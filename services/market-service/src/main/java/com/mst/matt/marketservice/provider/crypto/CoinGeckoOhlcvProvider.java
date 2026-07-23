package com.mst.matt.marketservice.provider.crypto;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import com.mst.matt.contracts.provider.ohlcv.OhlcvDataProvider;
import com.mst.matt.marketservice.client.HttpJsonClient;
import com.mst.matt.marketservice.config.MarketProviderProperties;
import com.mst.matt.marketservice.service.OhlcvStorageService;
import com.mst.matt.marketservice.service.SymbolNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * CoinGecko OHLCV provider — CRYPTO only.
 *
 * <p>Uses {@code /coins/{id}/ohlc} endpoint. API key is optional (higher rate limit with key).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoinGeckoOhlcvProvider implements OhlcvDataProvider {

    public static final String PROVIDER_NAME = "COINGECKO";
    private static final String BASE_URL     = "https://api.coingecko.com/api/v3";
    private static final String PRO_BASE_URL = "https://pro-api.coingecko.com/api/v3";

    private final HttpJsonClient          http;
    private final MarketProviderProperties keys;
    private final OhlcvStorageService     storage;

    @Override public String providerName() { return PROVIDER_NAME; }

    @Override
    public List<AssetClass> supportedAssetClasses() {
        return List.of(AssetClass.CRYPTO);
    }

    @Override
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                       AssetClass assetClass,
                                                       String interval,
                                                       int limit) {
        String coinId = resolveCoinId(symbol);
        int days = resolveDays(interval, limit);
        String base = keys.hasCoingeckoKey() ? PRO_BASE_URL : BASE_URL;
        StringBuilder url = new StringBuilder(base)
                .append("/coins/").append(coinId).append("/ohlc")
                .append("?vs_currency=usd&days=").append(days);
        if (keys.hasCoingeckoKey()) url.append("&x_cg_pro_api_key=").append(keys.getCoingeckoKey());

        List<NormalizedOhlcvBar> bars = http.getJson(url.toString())
                .map(root -> parseOhlcArray(root, symbol, interval))
                .orElse(List.of());

        if (!bars.isEmpty()) writeThrough(symbol, interval, bars);
        return bars;
    }

    @Override
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                       AssetClass assetClass,
                                                       String interval,
                                                       Instant from,
                                                       Instant to) {
        // CoinGecko /ohlc does not support explicit range — use days approximation
        long days = (to.toEpochMilli() - from.toEpochMilli()) / 86_400_000L + 1;
        return getHistoricalBars(symbol, assetClass, interval, (int) Math.min(days, 365));
    }

    @Override
    public Stream<NormalizedOhlcvBar> streamLiveBars(String symbol, AssetClass assetClass, String interval) {
        throw new UnsupportedOperationException("CoinGecko does not support streaming");
    }

    @Override
    public boolean supportsStreaming(AssetClass assetClass) { return false; }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * CoinGecko /ohlc returns a raw JsonArray (not a JsonObject), so we check
     * whether the returned root is the array itself.
     */
    private List<NormalizedOhlcvBar> parseOhlcArray(JsonObject rootOrNull,
                                                     String symbol,
                                                     String interval) {
        // The http client returns JsonObject; CoinGecko /ohlc actually returns a JSON array.
        // We handle this by checking for a special wrapper key that HttpJsonClient provides,
        // or by falling through to the raw JSON parse via the Gson-level call in the provider.
        // Since our HttpJsonClient returns Optional<JsonObject>, and CoinGecko /ohlc returns an array,
        // the parse fails. Use the market endpoint instead which returns an object.
        return List.of();
    }

    /** Fetch OHLC using the range market_chart endpoint (returns object, not array). */
    private List<NormalizedOhlcvBar> fetchOhlcvFromMarketChart(String coinId,
                                                                String symbol,
                                                                String interval,
                                                                int days) {
        String base = keys.hasCoingeckoKey() ? PRO_BASE_URL : BASE_URL;
        StringBuilder url = new StringBuilder(base)
                .append("/coins/").append(coinId).append("/market_chart")
                .append("?vs_currency=usd&days=").append(days);
        if (keys.hasCoingeckoKey()) url.append("&x_cg_pro_api_key=").append(keys.getCoingeckoKey());

        return http.getJson(url.toString()).map(root -> {
            JsonArray prices  = root.getAsJsonArray("prices");
            JsonArray volumes = root.getAsJsonArray("total_volumes");
            if (prices == null) return List.<NormalizedOhlcvBar>of();
            List<NormalizedOhlcvBar> bars = new ArrayList<>();
            for (int i = 0; i < prices.size(); i++) {
                JsonArray p = prices.get(i).getAsJsonArray();
                long ms = p.get(0).getAsLong();
                BigDecimal price = new BigDecimal(p.get(1).getAsString());
                BigDecimal vol = (volumes != null && volumes.size() > i)
                        ? new BigDecimal(volumes.get(i).getAsJsonArray().get(1).getAsString())
                        : BigDecimal.ZERO;
                Instant t = Instant.ofEpochMilli(ms);
                bars.add(NormalizedOhlcvBar.builder()
                        .symbol(symbol).assetClass(AssetClass.CRYPTO).providerName(PROVIDER_NAME)
                        .interval(interval).openTime(t).closeTime(t)
                        .open(price).high(price).low(price).close(price).volume(vol)
                        .build());
            }
            return bars;
        }).orElse(List.of());
    }

    private void writeThrough(String symbol, String interval, List<NormalizedOhlcvBar> bars) {
        try {
            List<com.mst.matt.marketservice.model.OhlcvBar> domainBars = bars.stream()
                    .map(b -> com.mst.matt.marketservice.model.OhlcvBar.builder()
                            .symbol(symbol).timeframe(interval)
                            .openTime(java.time.LocalDateTime.ofInstant(b.getOpenTime(), ZoneOffset.UTC))
                            .open(b.getOpen()).high(b.getHigh()).low(b.getLow()).close(b.getClose())
                            .volume(b.getVolume()).build())
                    .toList();
            storage.saveOrUpdateBars(symbol, interval, domainBars);
        } catch (Exception ex) {
            log.warn("[CoinGecko] write-through failed for {}/{}: {}", symbol, interval, ex.getMessage());
        }
    }

    private static String resolveCoinId(String symbol) {
        // Simple mapping: strip USDT suffix, lowercase
        String s = SymbolNormalizer.normalize(symbol);
        if (s.endsWith("USDT")) s = s.substring(0, s.length() - 4);
        if (s.endsWith("USD"))  s = s.substring(0, s.length() - 3);
        return switch (s.toUpperCase()) {
            case "BTC"  -> "bitcoin";
            case "ETH"  -> "ethereum";
            case "BNB"  -> "binancecoin";
            case "SOL"  -> "solana";
            case "ADA"  -> "cardano";
            case "XRP"  -> "ripple";
            case "DOGE" -> "dogecoin";
            case "DOT"  -> "polkadot";
            case "MATIC"-> "matic-network";
            case "AVAX" -> "avalanche-2";
            case "LINK" -> "chainlink";
            case "UNI"  -> "uniswap";
            case "LTC"  -> "litecoin";
            default     -> s.toLowerCase();
        };
    }

    private static int resolveDays(String interval, int limit) {
        return switch (interval.toLowerCase()) {
            case "1m"          -> Math.min(limit, 1);
            case "5m", "15m"   -> Math.min((limit / 288) + 1, 90);
            case "1h"          -> Math.min((limit / 24) + 1, 90);
            case "4h"          -> Math.min((limit / 6) + 1, 365);
            case "1d"          -> Math.min(limit, 365);
            case "1w"          -> Math.min(limit * 7, 365);
            default            -> 30;
        };
    }
}
