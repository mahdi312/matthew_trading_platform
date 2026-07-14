package com.mst.matt.marketservice.provider.crypto;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import com.mst.matt.contracts.provider.ohlcv.OhlcvDataProvider;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Binance OHLCV provider — CRYPTO only, no API key needed.
 *
 * <p>Uses the public klines endpoint: {@code GET /api/v3/klines}.
 */
@Slf4j
@Component
public class BinanceOhlcvProvider implements OhlcvDataProvider {

    public static final String PROVIDER_NAME = "BINANCE";

    @Value("${api.binance.base-url:https://api.binance.com}")
    private String baseUrl;

    @Value("${api.binance.fallback-url:https://data-api.binance.vision}")
    private String fallbackUrl;

    private final OkHttpClient    httpClient;
    private final OhlcvStorageService storage;
    private final Gson            gson = new Gson();

    public BinanceOhlcvProvider(
            @Qualifier("marketProviderHttpClient") OkHttpClient httpClient,
            OhlcvStorageService storage) {
        this.httpClient = httpClient;
        this.storage    = storage;
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
        String sym = SymbolNormalizer.forBinance(symbol);
        String binanceInterval = mapInterval(interval);
        for (String root : List.of(baseUrl, fallbackUrl)) {
            List<NormalizedOhlcvBar> bars = fetchKlines(root, sym, binanceInterval, limit, interval);
            if (!bars.isEmpty()) {
                writeThrough(sym, interval, bars);
                return bars;
            }
        }
        return List.of();
    }

    @Override
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                       AssetClass assetClass,
                                                       String interval,
                                                       Instant from,
                                                       Instant to) {
        String sym = SymbolNormalizer.forBinance(symbol);
        String binanceInterval = mapInterval(interval);
        long startMs = from.toEpochMilli();
        long endMs   = to.toEpochMilli();
        String url = String.format("%s/api/v3/klines?symbol=%s&interval=%s&startTime=%d&endTime=%d&limit=1000",
                baseUrl, sym, binanceInterval, startMs, endMs);
        List<NormalizedOhlcvBar> bars = fetchKlinesUrl(url, sym, interval);
        if (!bars.isEmpty()) writeThrough(sym, interval, bars);
        return bars;
    }

    @Override
    public Stream<NormalizedOhlcvBar> streamLiveBars(String symbol, AssetClass assetClass, String interval) {
        throw new UnsupportedOperationException("Use BinanceMarketDataProvider for streaming");
    }

    @Override
    public boolean supportsStreaming(AssetClass assetClass) { return false; }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<NormalizedOhlcvBar> fetchKlines(String rootUrl, String symbol,
                                                  String interval, int limit,
                                                  String originalInterval) {
        String url = String.format("%s/api/v3/klines?symbol=%s&interval=%s&limit=%d",
                rootUrl, symbol, interval, limit);
        return fetchKlinesUrl(url, symbol, originalInterval);
    }

    private List<NormalizedOhlcvBar> fetchKlinesUrl(String url, String symbol, String originalInterval) {
        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "MarketService/1.0")
                .build();

        List<NormalizedOhlcvBar> bars = new ArrayList<>();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return bars;
            // Binance returns array-of-arrays: [openTime, open, high, low, close, volume, closeTime, ...]
            JsonArray klines = gson.fromJson(response.body().string(), JsonArray.class);
            for (JsonElement el : klines) {
                JsonArray k = el.getAsJsonArray();
                long openTimeMs  = k.get(0).getAsLong();
                long closeTimeMs = k.get(6).getAsLong();
                bars.add(NormalizedOhlcvBar.builder()
                        .symbol(symbol)
                        .assetClass(AssetClass.CRYPTO)
                        .providerName(PROVIDER_NAME)
                        .interval(originalInterval)
                        .openTime(Instant.ofEpochMilli(openTimeMs))
                        .closeTime(Instant.ofEpochMilli(closeTimeMs))
                        .open(new BigDecimal(k.get(1).getAsString()))
                        .high(new BigDecimal(k.get(2).getAsString()))
                        .low(new BigDecimal(k.get(3).getAsString()))
                        .close(new BigDecimal(k.get(4).getAsString()))
                        .volume(new BigDecimal(k.get(5).getAsString()))
                        .quoteVolume(new BigDecimal(k.get(7).getAsString()))
                        .tradeCount(k.get(8).getAsLong())
                        .build());
            }
        } catch (IOException e) {
            log.error("[Binance] klines error for {}: {}", symbol, e.getMessage());
        }
        return bars;
    }

    private void writeThrough(String symbol, String interval, List<NormalizedOhlcvBar> bars) {
        try {
            List<com.mst.matt.marketservice.model.OhlcvBar> domainBars = bars.stream()
                    .map(b -> com.mst.matt.marketservice.model.OhlcvBar.builder()
                            .symbol(symbol)
                            .timeframe(interval)
                            .openTime(java.time.LocalDateTime.ofInstant(b.getOpenTime(), ZoneOffset.UTC))
                            .open(b.getOpen()).high(b.getHigh()).low(b.getLow()).close(b.getClose())
                            .volume(b.getVolume()).build())
                    .toList();
            storage.saveOrUpdateBars(symbol, interval, domainBars);
        } catch (Exception ex) {
            log.warn("[Binance] write-through failed for {}/{}: {}", symbol, interval, ex.getMessage());
        }
    }

    private static String mapInterval(String tf) {
        return switch (tf.toLowerCase()) {
            case "1m"  -> "1m";
            case "3m"  -> "3m";
            case "5m"  -> "5m";
            case "15m" -> "15m";
            case "30m" -> "30m";
            case "1h"  -> "1h";
            case "2h"  -> "2h";
            case "4h"  -> "4h";
            case "6h"  -> "6h";
            case "12h" -> "12h";
            case "1d"  -> "1d";
            case "3d"  -> "3d";
            case "1w"  -> "1w";
            case "1mo" -> "1M";
            default    -> "1h";
        };
    }
}
