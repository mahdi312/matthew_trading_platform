package com.mst.matt.tradingplatformapp.service.price;

import com.google.gson.*;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade;
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
 * Yahoo Finance service — stocks, ETFs, indices.
 *
 * Uses the unofficial chart/v8 endpoint (no API key needed).
 * Endpoint: https://query1.finance.yahoo.com/v8/finance/chart/{symbol}
 *
 * Includes proper User-Agent and headers to avoid 401 responses.
 */
@Service
public class YahooFinanceService implements PriceService {

    private static final Logger log = LoggerFactory.getLogger(YahooFinanceService.class);

    @Value("${api.yahoo.base-url}")
    private String baseUrl;

    private final OkHttpClient httpClient;
    private final Gson gson = new GsonBuilder().serializeNulls().create();

    public YahooFinanceService(@Autowired @Qualifier("priceHttpClient") OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    // ── PriceService Interface ──────────────────────────────

    @Override
    public Optional<PriceQuote> getQuote(String symbol) {
        return fetchYahooQuote(SymbolNormalizer.forYahoo(symbol));
    }

    @Override
    public List<OhlcvBar> getOhlcv(String symbol, String timeframe, int limit) {
        return fetchYahooHistory(SymbolNormalizer.forYahoo(symbol), timeframe, limit);
    }

    @Override
    public boolean supports(String symbol) {
        String s = SymbolNormalizer.normalize(symbol);
        if (forexLooksLike(s)) return false;
        return !s.endsWith("USDT") && !s.endsWith("BTC") && !s.endsWith("ETH");
    }

    private static boolean forexLooksLike(String s) {
        return s.length() == 6 && s.chars().allMatch(Character::isLetter);
    }

    @Override
    public String getProviderName() { return "Yahoo Finance"; }

    @Override
    public MarketDataProvider getProviderId() { return MarketDataProvider.YAHOO; }

    // ── REST: Quote ─────────────────────────────────────────

    private Optional<PriceQuote> fetchYahooQuote(String symbol) {
        // Use the v8 chart endpoint with period=1d to get latest price
        String url = String.format(
                "%s/v8/finance/chart/%s?interval=1d&range=5d&includePrePost=false",
                baseUrl, symbol);

        Request request = buildRequest(url);

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("Yahoo Finance HTTP {} for {}", response.code(), symbol);
                return Optional.empty();
            }

            String body = response.body().string();
            JsonObject root = gson.fromJson(body, JsonObject.class);
            JsonObject chart = root.getAsJsonObject("chart");
            JsonArray results = chart.getAsJsonArray("result");

            if (results == null || results.size() == 0) return Optional.empty();

            JsonObject result = results.get(0).getAsJsonObject();
            JsonObject meta   = result.getAsJsonObject("meta");

            BigDecimal price         = JsonParseUtil.asBigDecimal(meta, "regularMarketPrice");
            BigDecimal previousClose = JsonParseUtil.asBigDecimal(meta, "chartPreviousClose");
            BigDecimal change        = price.subtract(previousClose);
            BigDecimal changePct     = previousClose.compareTo(BigDecimal.ZERO) != 0
                    ? change.divide(previousClose, 6, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            PriceQuote quote = PriceQuote.builder()
                    .symbol(symbol)
                    .assetName(meta.has("longName")
                            ? meta.get("longName").getAsString() : symbol)
                    .assetType(Trade.AssetType.STOCK)
                    .price(price)
                    .open24h(JsonParseUtil.asBigDecimal(meta, "regularMarketOpen"))
                    .high24h(JsonParseUtil.asBigDecimal(meta, "regularMarketDayHigh"))
                    .low24h(JsonParseUtil.asBigDecimal(meta, "regularMarketDayLow"))
                    .change24h(change)
                    .changePct24h(changePct)
                    .volume24h(JsonParseUtil.asBigDecimal(meta, "regularMarketVolume"))
                    .currency(meta.has("currency")
                            ? meta.get("currency").getAsString() : "USD")
                    .exchange(meta.has("exchangeName")
                            ? meta.get("exchangeName").getAsString() : "")
                    .timestamp(LocalDateTime.now())
                    .isUp(changePct.compareTo(BigDecimal.ZERO) >= 0)
                    .build();

            return Optional.of(quote);

        } catch (IOException e) {
            log.error("Yahoo Finance error for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    // ── REST: OHLCV History ─────────────────────────────────

    private List<OhlcvBar> fetchYahooHistory(String symbol, String timeframe, int limit) {
        String interval = mapTimeframe(timeframe);
        String range    = limitToRange(limit, timeframe);

        String url = String.format(
                "%s/v8/finance/chart/%s?interval=%s&range=%s&includePrePost=false",
                baseUrl, symbol, interval, range);

        Request request = buildRequest(url);
        List<OhlcvBar> bars = new ArrayList<>();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return bars;

            JsonObject root    = gson.fromJson(response.body().string(), JsonObject.class);
            JsonObject chart   = root.getAsJsonObject("chart");
            JsonArray  results = chart.getAsJsonArray("result");

            if (results == null || results.size() == 0) return bars;

            JsonObject result    = results.get(0).getAsJsonObject();
            JsonArray  timestamps = result.getAsJsonArray("timestamp");
            JsonObject indicators = result.getAsJsonObject("indicators");
            JsonArray  quotes     = indicators.getAsJsonArray("quote");
            JsonObject q          = quotes.get(0).getAsJsonObject();

            JsonArray opens   = q.getAsJsonArray("open");
            JsonArray highs   = q.getAsJsonArray("high");
            JsonArray lows    = q.getAsJsonArray("low");
            JsonArray closes  = q.getAsJsonArray("close");
            JsonArray volumes = q.getAsJsonArray("volume");

            int count = Math.min(timestamps.size(), limit);

            for (int i = Math.max(0, timestamps.size() - count);
                 i < timestamps.size(); i++) {

                if (closes.get(i).isJsonNull()) continue;

                long epochSec = timestamps.get(i).getAsLong();
                LocalDateTime openTime = LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(epochSec), ZoneOffset.UTC);

                OhlcvBar bar = OhlcvBar.builder()
                        .symbol(symbol)
                        .timeframe(timeframe)
                        .openTime(openTime)
                        .open(safeGetBD(opens, i))
                        .high(safeGetBD(highs, i))
                        .low(safeGetBD(lows, i))
                        .close(safeGetBD(closes, i))
                        .volume(safeGetBD(volumes, i))
                        .assetType(Trade.AssetType.STOCK)
                        .build();

                bars.add(bar);
            }

        } catch (IOException e) {
            log.error("Yahoo Finance history error for {}: {}", symbol, e.getMessage());
        }

        return bars;
    }

    // ── Helpers ─────────────────────────────────────────────

    private Request buildRequest(String url) {
        return new Request.Builder()
                .url(url)
                .addHeader("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                                + "AppleWebKit/537.36 (KHTML, like Gecko) "
                                + "Chrome/120.0.0.0 Safari/537.36")
                .addHeader("Accept", "application/json")
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .build();
    }

    private BigDecimal safeGetBD(JsonArray arr, int idx) {
        try {
            return JsonParseUtil.asBigDecimal(arr.get(idx));
        } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private String mapTimeframe(String tf) {
        return switch (tf.toLowerCase()) {
            case "1m"  -> "1m";
            case "5m"  -> "5m";
            case "15m" -> "15m";
            case "30m" -> "30m";
            case "1h"  -> "60m";
            case "4h"  -> "1h"; // Yahoo doesn't have 4h; use 1h + more bars
            case "1d"  -> "1d";
            case "1w"  -> "1wk";
            case "1mo" -> "1mo";
            default    -> "1h";
        };
    }

    private String limitToRange(int limit, String tf) {
        // Estimate range string from limit + timeframe
        return switch (tf.toLowerCase()) {
            case "1m"         -> "1d";
            case "5m", "15m"  -> "5d";
            case "30m", "1h"  -> "1mo";
            case "4h"         -> "3mo";
            case "1d"         -> limit > 100 ? "1y" : "6mo";
            case "1w"         -> "5y";
            default           -> "3mo";
        };
    }
}