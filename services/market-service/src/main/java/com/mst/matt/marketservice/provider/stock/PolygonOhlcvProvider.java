package com.mst.matt.marketservice.provider.stock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import com.mst.matt.contracts.provider.ohlcv.OhlcvDataProvider;
import com.mst.matt.marketservice.client.HttpJsonClient;
import com.mst.matt.marketservice.client.JsonParseUtil;
import com.mst.matt.marketservice.config.MarketProviderProperties;
import com.mst.matt.marketservice.service.OhlcvStorageService;
import com.mst.matt.marketservice.service.SymbolNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Polygon.io OHLCV provider — STOCK only.
 *
 * <p>Free tier: unlimited requests but delayed data. Uses {@code /v2/aggs/ticker/.../range/...}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PolygonOhlcvProvider implements OhlcvDataProvider {

    public static final String PROVIDER_NAME = "POLYGON";
    private static final String BASE_URL     = "https://api.polygon.io";

    private final HttpJsonClient          http;
    private final MarketProviderProperties keys;
    private final OhlcvStorageService     storage;

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
        if (!keys.hasPolygonKey()) return List.of();
        String sym = SymbolNormalizer.normalize(symbol);
        long toMs   = Instant.now().toEpochMilli();
        long fromMs = Instant.now().minus(estimateDays(limit, interval), ChronoUnit.DAYS).toEpochMilli();
        return fetchAggs(sym, interval, fromMs, toMs, limit);
    }

    @Override
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                       AssetClass assetClass,
                                                       String interval,
                                                       Instant from,
                                                       Instant to) {
        if (!keys.hasPolygonKey()) return List.of();
        String sym = SymbolNormalizer.normalize(symbol);
        return fetchAggs(sym, interval, from.toEpochMilli(), to.toEpochMilli(), 50000);
    }

    @Override
    public Stream<NormalizedOhlcvBar> streamLiveBars(String symbol, AssetClass assetClass, String interval) {
        throw new UnsupportedOperationException("Polygon does not support REST streaming");
    }

    @Override
    public boolean supportsStreaming(AssetClass assetClass) { return false; }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<NormalizedOhlcvBar> fetchAggs(String sym, String interval,
                                                long fromMs, long toMs, int limit) {
        Span span = mapSpan(interval);
        String url = String.format(
                BASE_URL + "/v2/aggs/ticker/%s/range/%d/%s/%d/%d?adjusted=true&sort=asc&limit=%d&apiKey=%s",
                sym, span.multiplier(), span.timespan(), fromMs, toMs, limit, keys.getPolygonKey());

        List<NormalizedOhlcvBar> bars = http.getJson(url).map(root -> {
            JsonArray results = root.getAsJsonArray("results");
            if (results == null) return List.<NormalizedOhlcvBar>of();
            List<NormalizedOhlcvBar> list = new ArrayList<>();
            for (var el : results) {
                JsonObject r = el.getAsJsonObject();
                long ms = r.get("t").getAsLong();
                Instant openTime = Instant.ofEpochMilli(ms);
                list.add(NormalizedOhlcvBar.builder()
                        .symbol(sym).assetClass(AssetClass.STOCK).providerName(PROVIDER_NAME)
                        .interval(interval).openTime(openTime).closeTime(openTime)
                        .open(JsonParseUtil.asBigDecimal(r, "o"))
                        .high(JsonParseUtil.asBigDecimal(r, "h"))
                        .low(JsonParseUtil.asBigDecimal(r, "l"))
                        .close(JsonParseUtil.asBigDecimal(r, "c"))
                        .volume(JsonParseUtil.asBigDecimal(r, "v"))
                        .build());
            }
            return list;
        }).orElse(List.of());

        if (!bars.isEmpty()) writeThrough(sym, interval, bars);
        return bars;
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
            log.warn("[Polygon] write-through failed for {}/{}: {}", symbol, interval, ex.getMessage());
        }
    }

    private record Span(int multiplier, String timespan) {}

    private static Span mapSpan(String tf) {
        return switch (tf.toLowerCase()) {
            case "1m"  -> new Span(1,  "minute");
            case "5m"  -> new Span(5,  "minute");
            case "15m" -> new Span(15, "minute");
            case "30m" -> new Span(30, "minute");
            case "1h"  -> new Span(1,  "hour");
            case "4h"  -> new Span(4,  "hour");
            case "1d"  -> new Span(1,  "day");
            case "1w"  -> new Span(1,  "week");
            case "1mo" -> new Span(1,  "month");
            default    -> new Span(1,  "day");
        };
    }

    private static long estimateDays(int limit, String tf) {
        return switch (tf.toLowerCase()) {
            case "1m"  -> Math.max(1, limit / 390);
            case "5m"  -> Math.max(1, limit / 78);
            case "15m" -> Math.max(1, limit / 26);
            case "30m" -> Math.max(1, limit / 13);
            case "1h"  -> Math.max(1, limit / 7);
            case "4h"  -> Math.max(1, (long) limit);
            case "1w"  -> (long) limit * 7;
            case "1mo" -> (long) limit * 31;
            default    -> (long) limit;
        };
    }
}
