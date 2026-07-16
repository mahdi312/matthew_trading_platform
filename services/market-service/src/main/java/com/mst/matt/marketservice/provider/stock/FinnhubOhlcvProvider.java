package com.mst.matt.marketservice.provider.stock;

import com.google.gson.JsonArray;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import com.mst.matt.contracts.provider.ohlcv.OhlcvDataProvider;
import com.mst.matt.marketservice.client.HttpJsonClient;
import com.mst.matt.marketservice.client.JsonParseUtil;
import com.mst.matt.marketservice.config.MarketProviderProperties;
import com.mst.matt.marketservice.service.AssetClassDetector;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Finnhub OHLCV provider — supports STOCK, CRYPTO, FOREX.
 *
 * <p>Free tier: 60 requests/minute. Uses {@code /stock/candle}, {@code /crypto/candle},
 * {@code /forex/candle} depending on asset class.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FinnhubOhlcvProvider implements OhlcvDataProvider {

    public static final String PROVIDER_NAME = "FINNHUB";
    private static final String THROTTLE_KEY = "finnhub";
    private static final String BASE_URL     = "https://finnhub.io/api/v1";

    private final HttpJsonClient          http;
    private final MarketProviderProperties keys;
    private final OhlcvStorageService     storage;

    @PostConstruct
    void registerThrottle() {
        // Finnhub free tier: 60 requests/minute
        http.throttle(THROTTLE_KEY, 60, Duration.ofMinutes(1));
    }

    @Override public String providerName() { return PROVIDER_NAME; }

    @Override
    public List<AssetClass> supportedAssetClasses() {
        return List.of(AssetClass.STOCK, AssetClass.CRYPTO, AssetClass.FOREX);
    }

    @Override
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                       AssetClass assetClass,
                                                       String interval,
                                                       int limit) {
        if (!keys.hasFinnhubKey()) return List.of();
        String sym = SymbolNormalizer.normalize(symbol);
        long to   = Instant.now().getEpochSecond();
        long from = to - estimateSeconds(limit, interval);
        return fetchCandles(sym, assetClass, interval, from, to, limit);
    }

    @Override
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                       AssetClass assetClass,
                                                       String interval,
                                                       Instant from,
                                                       Instant to) {
        if (!keys.hasFinnhubKey()) return List.of();
        String sym = SymbolNormalizer.normalize(symbol);
        return fetchCandles(sym, assetClass, interval, from.getEpochSecond(), to.getEpochSecond(), 0);
    }

    @Override
    public Stream<NormalizedOhlcvBar> streamLiveBars(String symbol, AssetClass assetClass, String interval) {
        throw new UnsupportedOperationException("Finnhub REST does not support streaming");
    }

    @Override
    public boolean supportsStreaming(AssetClass assetClass) { return false; }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<NormalizedOhlcvBar> fetchCandles(String sym, AssetClass assetClass,
                                                   String interval, long from, long to,
                                                   int limit) {
        String path = assetClass == AssetClass.FOREX ? "/forex/candle"
                : assetClass == AssetClass.CRYPTO ? "/crypto/candle" : "/stock/candle";
        String url = BASE_URL + path
                + "?symbol=" + sym
                + "&resolution=" + mapResolution(interval)
                + "&from=" + from + "&to=" + to
                + "&token=" + keys.getFinnhubKey();

        List<NormalizedOhlcvBar> bars = http.getJson(url, null, THROTTLE_KEY).map(json -> {
            if (!"ok".equals(json.has("s") ? json.get("s").getAsString() : ""))
                return List.<NormalizedOhlcvBar>of();
            JsonArray t = json.getAsJsonArray("t");
            JsonArray o = json.getAsJsonArray("o");
            JsonArray h = json.getAsJsonArray("h");
            JsonArray l = json.getAsJsonArray("l");
            JsonArray c = json.getAsJsonArray("c");
            JsonArray v = json.getAsJsonArray("v");
            if (t == null) return List.<NormalizedOhlcvBar>of();
            List<NormalizedOhlcvBar> result = new ArrayList<>();
            int start = (limit > 0) ? Math.max(0, t.size() - limit) : 0;
            for (int i = start; i < t.size(); i++) {
                Instant openTime = Instant.ofEpochSecond(t.get(i).getAsLong());
                result.add(NormalizedOhlcvBar.builder()
                        .symbol(sym).assetClass(assetClass).providerName(PROVIDER_NAME)
                        .interval(interval).openTime(openTime).closeTime(openTime)
                        .open(JsonParseUtil.asBigDecimal(o.get(i)))
                        .high(JsonParseUtil.asBigDecimal(h.get(i)))
                        .low(JsonParseUtil.asBigDecimal(l.get(i)))
                        .close(JsonParseUtil.asBigDecimal(c.get(i)))
                        .volume(v != null ? JsonParseUtil.asBigDecimal(v.get(i)) : BigDecimal.ZERO)
                        .build());
            }
            return result;
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
            log.warn("[Finnhub] write-through failed for {}/{}: {}", symbol, interval, ex.getMessage());
        }
    }

    private static String mapResolution(String tf) {
        return switch (tf.toLowerCase()) {
            case "1m"  -> "1";
            case "5m"  -> "5";
            case "15m" -> "15";
            case "30m" -> "30";
            case "1h", "4h" -> "60";
            case "1w"  -> "W";
            default    -> "D";
        };
    }

    private static long estimateSeconds(int limit, String tf) {
        long perBar = switch (tf.toLowerCase()) {
            case "1m"  -> 60L;
            case "5m"  -> 300L;
            case "15m" -> 900L;
            case "30m" -> 1800L;
            case "1h"  -> 3600L;
            case "4h"  -> 14400L;
            case "1w"  -> 604800L;
            default    -> 86400L;
        };
        return perBar * limit;
    }
}
