package com.mst.matt.marketservice.provider.crypto;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import com.mst.matt.contracts.provider.ohlcv.OhlcvDataProvider;
import com.mst.matt.marketservice.client.HttpJsonClient;
import com.mst.matt.marketservice.client.JsonParseUtil;
import com.mst.matt.marketservice.config.MarketProviderProperties;
import com.mst.matt.marketservice.service.OhlcvStorageService;
import com.mst.matt.marketservice.service.SymbolNormalizer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * CoinMarketCap OHLCV provider — CRYPTO only, API key required.
 *
 * <p>Free tier: 30 requests/minute. Uses {@code /v2/cryptocurrency/ohlcv/historical}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoinMarketCapOhlcvProvider implements OhlcvDataProvider {

    public static final String PROVIDER_NAME  = "COINMARKETCAP";
    private static final String THROTTLE_KEY  = "coinmarketcap";
    private static final String BASE_URL      = "https://pro-api.coinmarketcap.com";
    private static final String CMC_KEY_HDR   = "X-CMC_PRO_API_KEY";

    private final HttpJsonClient          http;
    private final MarketProviderProperties keys;
    private final OhlcvStorageService     storage;

    @PostConstruct
    void registerThrottle() {
        // CoinMarketCap free tier: 30 requests/minute
        http.throttle(THROTTLE_KEY, 30, Duration.ofMinutes(1));
    }

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
        if (!keys.hasCoinmarketcapKey()) return List.of();
        String symRaw = SymbolNormalizer.normalize(symbol);
        if (symRaw.endsWith("USDT")) symRaw = symRaw.substring(0, symRaw.length() - 4);
        final String sym = symRaw;
        String cmcInterval = mapInterval(interval);
        Instant to   = Instant.now();
        Instant from = to.minus(Duration.ofDays(estimateDays(interval, limit)));

        String url = BASE_URL + "/v2/cryptocurrency/ohlcv/historical"
                + "?symbol=" + sym
                + "&time_start=" + from.getEpochSecond()
                + "&time_end="   + to.getEpochSecond()
                + "&interval="   + cmcInterval
                + "&count="      + Math.min(limit, 10000)
                + "&convert=USD";

        List<NormalizedOhlcvBar> bars = http.getJson(url, null, THROTTLE_KEY)
                .map(root -> parseOhlcv(root, sym, interval))
                .orElse(List.of());

        if (!bars.isEmpty()) writeThrough(sym, interval, bars);
        return bars;
    }

    @Override
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                       AssetClass assetClass,
                                                       String interval,
                                                       Instant from,
                                                       Instant to) {
        if (!keys.hasCoinmarketcapKey()) return List.of();
        String sym = SymbolNormalizer.normalize(symbol);
        if (sym.endsWith("USDT")) sym = sym.substring(0, sym.length() - 4);
        String url = BASE_URL + "/v2/cryptocurrency/ohlcv/historical"
                + "?symbol=" + sym
                + "&time_start=" + from.getEpochSecond()
                + "&time_end="   + to.getEpochSecond()
                + "&interval="   + mapInterval(interval)
                + "&convert=USD";

        final String finalSym = sym;
        List<NormalizedOhlcvBar> bars = http.getJson(url, null, THROTTLE_KEY)
                .map(root -> parseOhlcv(root, finalSym, interval))
                .orElse(List.of());
        if (!bars.isEmpty()) writeThrough(finalSym, interval, bars);
        return bars;
    }

    @Override
    public Stream<NormalizedOhlcvBar> streamLiveBars(String symbol, AssetClass assetClass, String interval) {
        throw new UnsupportedOperationException("CoinMarketCap does not support streaming");
    }

    @Override
    public boolean supportsStreaming(AssetClass assetClass) { return false; }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<NormalizedOhlcvBar> parseOhlcv(JsonObject root, String symbol, String interval) {
        try {
            JsonObject data = root.getAsJsonObject("data");
            if (data == null) return List.of();
            // data is a map of symbol -> {quotes: [...]}
            for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
                JsonObject coinData = entry.getValue().getAsJsonObject();
                JsonArray quotes = coinData.getAsJsonArray("quotes");
                if (quotes == null) continue;
                List<NormalizedOhlcvBar> bars = new ArrayList<>();
                for (JsonElement qEl : quotes) {
                    JsonObject q = qEl.getAsJsonObject();
                    String timeOpen  = q.has("time_open")  ? q.get("time_open").getAsString()  : null;
                    String timeClose = q.has("time_close") ? q.get("time_close").getAsString() : null;
                    if (timeOpen == null) continue;
                    JsonObject usd = q.getAsJsonObject("quote") != null
                            ? q.getAsJsonObject("quote").getAsJsonObject("USD") : null;
                    if (usd == null) continue;
                    Instant openTime  = Instant.parse(timeOpen);
                    Instant closeTime = timeClose != null ? Instant.parse(timeClose) : openTime;
                    bars.add(NormalizedOhlcvBar.builder()
                            .symbol(symbol).assetClass(AssetClass.CRYPTO).providerName(PROVIDER_NAME)
                            .interval(interval).openTime(openTime).closeTime(closeTime)
                            .open(JsonParseUtil.asBigDecimal(usd, "open"))
                            .high(JsonParseUtil.asBigDecimal(usd, "high"))
                            .low(JsonParseUtil.asBigDecimal(usd, "low"))
                            .close(JsonParseUtil.asBigDecimal(usd, "close"))
                            .volume(JsonParseUtil.asBigDecimal(usd, "volume"))
                            .build());
                }
                return bars;
            }
        } catch (Exception ex) {
            log.warn("[CMC] parse error for {}: {}", symbol, ex.getMessage());
        }
        return List.of();
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
            log.warn("[CMC] write-through failed: {}", ex.getMessage());
        }
    }

    private static String mapInterval(String tf) {
        return switch (tf.toLowerCase()) {
            case "1m"  -> "1m";
            case "5m"  -> "5m";
            case "15m" -> "15m";
            case "30m" -> "30m";
            case "1h"  -> "1h";
            case "4h"  -> "4h";
            case "1d"  -> "daily";
            case "1w"  -> "weekly";
            case "1mo" -> "monthly";
            default    -> "daily";
        };
    }

    private static long estimateDays(String interval, int limit) {
        return switch (interval.toLowerCase()) {
            case "1m"  -> Math.max(1, limit / 1440);
            case "5m"  -> Math.max(1, limit / 288);
            case "15m" -> Math.max(1, limit / 96);
            case "30m" -> Math.max(1, limit / 48);
            case "1h"  -> Math.max(1, limit / 24);
            case "4h"  -> Math.max(1, limit / 6);
            case "1d"  -> limit;
            case "1w"  -> (long) limit * 7;
            default    -> 30L;
        };
    }
}
