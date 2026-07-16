package com.mst.matt.marketservice.provider.stock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import com.mst.matt.contracts.provider.ohlcv.OhlcvDataProvider;
import com.mst.matt.marketservice.client.JsonParseUtil;
import com.mst.matt.marketservice.service.OhlcvStorageService;
import com.mst.matt.marketservice.service.SymbolNormalizer;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Yahoo Finance OHLCV provider — STOCK only, no API key required.
 *
 * <p>Uses the unofficial v8 chart endpoint:
 * {@code https://query1.finance.yahoo.com/v8/finance/chart/{symbol}}.
 */
@Slf4j
@Component
public class YahooFinanceOhlcvProvider implements OhlcvDataProvider {

    public static final String PROVIDER_NAME = "YAHOO_FINANCE";

    @Value("${api.yahoo.base-url:https://query1.finance.yahoo.com}")
    private String baseUrl;

    private final OkHttpClient    httpClient;
    private final OhlcvStorageService storage;
    private final Gson            gson = new GsonBuilder().serializeNulls().create();

    public YahooFinanceOhlcvProvider(
            @Qualifier("marketProviderHttpClient") OkHttpClient httpClient,
            OhlcvStorageService storage) {
        this.httpClient = httpClient;
        this.storage    = storage;
    }

    @Override public String providerName() { return PROVIDER_NAME; }

    @Override
    public List<AssetClass> supportedAssetClasses() {
        return List.of(AssetClass.STOCK);
    }

    @Override
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                       AssetClass assetClass,
                                                       String interval,
                                                       int limit) {
        String sym = SymbolNormalizer.forYahoo(symbol);
        String range      = mapRange(interval, limit);
        String yahooInt   = mapInterval(interval);
        String url = String.format(
                "%s/v8/finance/chart/%s?interval=%s&range=%s&includePrePost=false",
                baseUrl, sym, yahooInt, range);

        List<NormalizedOhlcvBar> bars = fetchBars(url, sym, symbol, interval, assetClass);
        if (!bars.isEmpty()) writeThrough(symbol, interval, bars);
        return bars;
    }

    @Override
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                       AssetClass assetClass,
                                                       String interval,
                                                       Instant from,
                                                       Instant to) {
        String sym = SymbolNormalizer.forYahoo(symbol);
        String yahooInt = mapInterval(interval);
        String url = String.format(
                "%s/v8/finance/chart/%s?interval=%s&period1=%d&period2=%d&includePrePost=false",
                baseUrl, sym, yahooInt, from.getEpochSecond(), to.getEpochSecond());

        List<NormalizedOhlcvBar> bars = fetchBars(url, sym, symbol, interval, assetClass);
        if (!bars.isEmpty()) writeThrough(symbol, interval, bars);
        return bars;
    }

    @Override
    public Stream<NormalizedOhlcvBar> streamLiveBars(String symbol, AssetClass assetClass, String interval) {
        throw new UnsupportedOperationException("Yahoo Finance does not support streaming");
    }

    @Override
    public boolean supportsStreaming(AssetClass assetClass) { return false; }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<NormalizedOhlcvBar> fetchBars(String url, String yahooSym, String originalSym,
                                                String interval, AssetClass assetClass) {
        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (compatible; MarketService/1.0)")
                .addHeader("Accept",     "application/json")
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("[Yahoo] HTTP {} for {}", response.code(), yahooSym);
                return List.of();
            }
            JsonObject root   = gson.fromJson(response.body().string(), JsonObject.class);
            JsonObject chart  = root.getAsJsonObject("chart");
            if (chart == null) return List.of();
            JsonArray results = chart.getAsJsonArray("result");
            if (results == null || results.isEmpty()) return List.of();

            JsonObject result = results.get(0).getAsJsonObject();
            JsonArray timestamps = result.getAsJsonArray("timestamp");
            JsonObject indicators = result.getAsJsonObject("indicators");
            if (timestamps == null || indicators == null) return List.of();

            JsonArray quote = indicators.getAsJsonArray("quote");
            if (quote == null || quote.isEmpty()) return List.of();
            JsonObject q = quote.get(0).getAsJsonObject();
            JsonArray opens  = q.getAsJsonArray("open");
            JsonArray highs  = q.getAsJsonArray("high");
            JsonArray lows   = q.getAsJsonArray("low");
            JsonArray closes = q.getAsJsonArray("close");
            JsonArray volumes= q.getAsJsonArray("volume");

            List<NormalizedOhlcvBar> bars = new ArrayList<>();
            for (int i = 0; i < timestamps.size(); i++) {
                if (closes == null || closes.get(i).isJsonNull()) continue;
                Instant t = Instant.ofEpochSecond(timestamps.get(i).getAsLong());
                bars.add(NormalizedOhlcvBar.builder()
                        .symbol(originalSym).assetClass(assetClass).providerName(PROVIDER_NAME)
                        .interval(interval).openTime(t).closeTime(t)
                        .open(safeDecimal(opens,   i))
                        .high(safeDecimal(highs,   i))
                        .low(safeDecimal(lows,     i))
                        .close(safeDecimal(closes, i))
                        .volume(volumes != null ? safeDecimal(volumes, i) : BigDecimal.ZERO)
                        .build());
            }
            return bars;
        } catch (IOException e) {
            log.error("[Yahoo] fetch error for {}: {}", yahooSym, e.getMessage());
            return List.of();
        }
    }

    private static BigDecimal safeDecimal(JsonArray arr, int i) {
        if (arr == null || i >= arr.size() || arr.get(i).isJsonNull()) return BigDecimal.ZERO;
        try { return arr.get(i).getAsBigDecimal(); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private void writeThrough(String symbol, String interval, List<NormalizedOhlcvBar> bars) {
        try {
            List<com.mst.matt.marketservice.model.OhlcvBar> domainBars = bars.stream()
                    .map(b -> com.mst.matt.marketservice.model.OhlcvBar.builder()
                            .symbol(symbol).timeframe(interval)
                            .openTime(LocalDateTime.ofInstant(b.getOpenTime(), ZoneOffset.UTC))
                            .open(b.getOpen()).high(b.getHigh()).low(b.getLow()).close(b.getClose())
                            .volume(b.getVolume()).build())
                    .toList();
            storage.saveOrUpdateBars(symbol, interval, domainBars);
        } catch (Exception ex) {
            log.warn("[Yahoo] write-through failed for {}/{}: {}", symbol, interval, ex.getMessage());
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
            case "1d"  -> "1d";
            case "1w"  -> "1wk";
            case "1mo" -> "1mo";
            default    -> "1d";
        };
    }

    private static String mapRange(String interval, int limit) {
        // Estimate the range string from limit + interval
        long totalDays = switch (interval.toLowerCase()) {
            case "1m"  -> Math.max(1, limit / 390);
            case "5m"  -> Math.max(1, limit / 78);
            case "15m" -> Math.max(1, limit / 26);
            case "30m" -> Math.max(1, limit / 13);
            case "1h"  -> Math.max(1, limit / 7);
            case "4h"  -> Math.max(1, limit / 2);
            case "1w"  -> (long) limit * 7;
            case "1mo" -> (long) limit * 31;
            default    -> limit;
        };
        if (totalDays <= 5)   return "5d";
        if (totalDays <= 30)  return "1mo";
        if (totalDays <= 90)  return "3mo";
        if (totalDays <= 180) return "6mo";
        if (totalDays <= 365) return "1y";
        if (totalDays <= 730) return "2y";
        if (totalDays <= 1825) return "5y";
        return "max";
    }
}
