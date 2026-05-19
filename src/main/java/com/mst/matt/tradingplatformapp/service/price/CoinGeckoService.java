package com.mst.matt.tradingplatformapp.service.price;

import com.google.gson.*;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/**
 * CoinGecko service — broader crypto coverage than Binance alone.
 *
 * Free demo API (no credit card, just register for key).
 * Endpoints used:
 *   /simple/price           → current price + 24h change
 *   /coins/{id}/ohlc        → OHLCV data (limited history on free tier)
 *   /coins/markets          → full market data for multiple coins
 */
@Service
public class CoinGeckoService implements PriceService {

    private static final Logger log = LoggerFactory.getLogger(CoinGeckoService.class);

    @Value("${api.coingecko.base-url:https://api.coingecko.com/api/v3}")
    private String baseUrl;

    @Value("${api.coingecko-key:}")
    private String apiKey;

    private final OkHttpClient httpClient;
    private final Gson gson = new Gson();

    // Map from trading symbols to CoinGecko coin IDs
    private static final Map<String, String> SYMBOL_TO_ID = Map.ofEntries(
            Map.entry("BTC",  "bitcoin"),
            Map.entry("ETH",  "ethereum"),
            Map.entry("BNB",  "binancecoin"),
            Map.entry("SOL",  "solana"),
            Map.entry("ADA",  "cardano"),
            Map.entry("XRP",  "ripple"),
            Map.entry("DOT",  "polkadot"),
            Map.entry("DOGE", "dogecoin"),
            Map.entry("AVAX", "avalanche-2"),
            Map.entry("MATIC","matic-network"),
            Map.entry("LINK", "chainlink"),
            Map.entry("LTC",  "litecoin"),
            Map.entry("UNI",  "uniswap"),
            Map.entry("ATOM", "cosmos"),
            Map.entry("XLM",  "stellar"),
            Map.entry("NEAR", "near"),
            Map.entry("ALGO", "algorand"),
            Map.entry("VET",  "vechain"),
            Map.entry("FIL",  "filecoin"),
            Map.entry("TRX",  "tron")
    );

    public CoinGeckoService(@Autowired @Qualifier("priceHttpClient") OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public Optional<PriceQuote> getQuote(String symbol) {
        String coinId = toCoinId(symbol);
        if (coinId == null) return Optional.empty();

        String url = baseUrl + "/coins/markets"
                + "?vs_currency=usd"
                + "&ids=" + coinId
                + "&order=market_cap_desc"
                + "&sparkline=false"
                + "&price_change_percentage=24h";

        Request request = buildRequest(url);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return Optional.empty();

            JsonArray arr = gson.fromJson(response.body().string(), JsonArray.class);
            if (arr.size() == 0) return Optional.empty();

            JsonObject coin = arr.get(0).getAsJsonObject();

            BigDecimal price     = JsonParseUtil.asBigDecimal(coin, "current_price");
            BigDecimal changePct = JsonParseUtil.asBigDecimal(coin, "price_change_percentage_24h");
            BigDecimal change    = JsonParseUtil.asBigDecimal(coin, "price_change_24h");

            PriceQuote quote = PriceQuote.builder()
                    .symbol(symbol.toUpperCase())
                    .assetName(coin.has("name") ? coin.get("name").getAsString() : symbol)
                    .assetType(AssetType.CRYPTO)
                    .price(price)
                    .high24h(JsonParseUtil.asBigDecimal(coin, "high_24h"))
                    .low24h(JsonParseUtil.asBigDecimal(coin, "low_24h"))
                    .change24h(change)
                    .changePct24h(changePct)
                    .volume24h(JsonParseUtil.asBigDecimal(coin, "total_volume"))
                    .marketCap(JsonParseUtil.asBigDecimal(coin, "market_cap"))
                    .currency("USD")
                    .exchange("CoinGecko")
                    .timestamp(LocalDateTime.now())
                    .isUp(changePct.compareTo(BigDecimal.ZERO) >= 0)
                    .build();

            return Optional.of(quote);

        } catch (IOException e) {
            log.error("CoinGecko error for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<OhlcvBar> getOhlcv(String symbol, String timeframe, int limit) {
        String coinId = toCoinId(symbol);
        if (coinId == null) return Collections.emptyList();

        // CoinGecko OHLC endpoint: days param drives how much history
        int days = toDays(timeframe, limit);
        String url = baseUrl + "/coins/" + coinId
                + "/ohlc?vs_currency=usd&days=" + days;

        Request request = buildRequest(url);
        List<OhlcvBar> bars = new ArrayList<>();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return bars;

            JsonArray arr = gson.fromJson(response.body().string(), JsonArray.class);

            for (JsonElement el : arr) {
                JsonArray row = el.getAsJsonArray();
                long ts = row.get(0).getAsLong();

                OhlcvBar bar = OhlcvBar.builder()
                        .symbol(symbol.toUpperCase())
                        .timeframe(timeframe)
                        .openTime(LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(ts), ZoneOffset.UTC))
                        .open(new BigDecimal(row.get(1).getAsString()))
                        .high(new BigDecimal(row.get(2).getAsString()))
                        .low(new BigDecimal(row.get(3).getAsString()))
                        .close(new BigDecimal(row.get(4).getAsString()))
                        .volume(BigDecimal.ZERO) // CoinGecko OHLC has no volume column
                        .assetType(AssetType.CRYPTO)
                        .build();

                bars.add(bar);
            }

        } catch (IOException e) {
            log.error("CoinGecko OHLCV error for {}: {}", symbol, e.getMessage());
        }

        // Trim to requested limit
        if (bars.size() > limit)
            bars = bars.subList(bars.size() - limit, bars.size());

        return bars;
    }

    @Override
    public boolean supports(String symbol) {
        return toCoinId(symbol.toUpperCase()) != null
                || toCoinId(stripSuffix(symbol.toUpperCase())) != null;
    }

    @Override
    public String getProviderName() { return "CoinGecko"; }

    @Override
    public MarketDataProvider getProviderId() { return MarketDataProvider.COINGECKO; }

    // ── Helpers ─────────────────────────────────────────────

    private String toCoinId(String symbol) {
        // Strip common suffixes: BTCUSDT → BTC
        String base = stripSuffix(symbol.toUpperCase());
        return SYMBOL_TO_ID.get(base);
    }

    private String stripSuffix(String s) {
        for (String suffix : List.of("USDT","USD","BTC","ETH","BNB","BUSD"))
            if (s.endsWith(suffix) && s.length() > suffix.length())
                return s.substring(0, s.length() - suffix.length());
        return s;
    }

    private int toDays(String timeframe, int limit) {
        return switch (timeframe.toLowerCase()) {
            case "1m"  -> 1;
            case "5m"  -> 1;
            case "15m" -> 2;
            case "30m" -> 3;
            case "1h"  -> Math.max(1, limit / 24) + 1;
            case "4h"  -> Math.max(1, limit / 6)  + 1;
            case "1d"  -> Math.min(limit + 5, 365);
            case "1w"  -> Math.min(limit * 7 + 10, 1825);
            default    -> 30;
        };
    }

    private Request buildRequest(String url) {
        Request.Builder b = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "TradingPlatform/1.0")
                .addHeader("Accept", "application/json");
        if (apiKey != null && !apiKey.isBlank() && !"demo".equalsIgnoreCase(apiKey))
            b.addHeader("x-cg-demo-api-key", apiKey);
        return b.build();
    }
}