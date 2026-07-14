package com.mst.matt.marketservice.provider.stock;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * TwelveData OHLCV provider — supports STOCK, CRYPTO, FOREX.
 *
 * <p>Free tier: 8 credits/minute, 800 credits/day. Uses {@code /time_series}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TwelveDataOhlcvProvider implements OhlcvDataProvider {

    public static final String PROVIDER_NAME = "TWELVEDATA";
    private static final String BASE_URL     = "https://api.twelvedata.com";
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter D_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final HttpJsonClient          http;
    private final MarketProviderProperties keys;
    private final OhlcvStorageService     storage;

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
        if (!keys.hasTwelvedataKey()) return List.of();
        String sym = SymbolNormalizer.normalize(symbol);
        String url = BASE_URL + "/time_series"
                + "?symbol=" + sym
                + "&interval=" + mapInterval(interval)
                + "&outputsize=" + Math.min(limit, 5000)
                + "&apikey=" + keys.getTwelvedataKey();

        List<NormalizedOhlcvBar> bars = http.getJson(url)
                .map(root -> parseSeries(root, sym, interval, assetClass))
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
        if (!keys.hasTwelvedataKey()) return List.of();
        String sym = SymbolNormalizer.normalize(symbol);
        String startDate = LocalDateTime.ofInstant(from, ZoneOffset.UTC).format(D_FMT);
        String endDate   = LocalDateTime.ofInstant(to,   ZoneOffset.UTC).format(D_FMT);
        String url = BASE_URL + "/time_series"
                + "?symbol=" + sym
                + "&interval=" + mapInterval(interval)
                + "&start_date=" + startDate
                + "&end_date="   + endDate
                + "&outputsize=5000"
                + "&apikey=" + keys.getTwelvedataKey();

        List<NormalizedOhlcvBar> bars = http.getJson(url)
                .map(root -> parseSeries(root, sym, interval, assetClass))
                .orElse(List.of());

        if (!bars.isEmpty()) writeThrough(sym, interval, bars);
        return bars;
    }

    @Override
    public Stream<NormalizedOhlcvBar> streamLiveBars(String symbol, AssetClass assetClass, String interval) {
        throw new UnsupportedOperationException("TwelveData does not support streaming via this provider");
    }

    @Override
    public boolean supportsStreaming(AssetClass assetClass) { return false; }

    // ── helpers ───────────────────────────────────────────────────────────────

    private List<NormalizedOhlcvBar> parseSeries(JsonObject root, String symbol,
                                                  String interval, AssetClass assetClass) {
        if (root == null || root.has("code") || root.has("message")) return List.of();
        JsonArray values = root.getAsJsonArray("values");
        if (values == null || values.isEmpty()) return List.of();
        List<NormalizedOhlcvBar> bars = new ArrayList<>();
        for (JsonElement el : values) {
            JsonObject v = el.getAsJsonObject();
            String dtStr = v.has("datetime") ? v.get("datetime").getAsString() : null;
            if (dtStr == null) continue;
            Instant openTime;
            try {
                openTime = (dtStr.length() > 10)
                        ? LocalDateTime.parse(dtStr, DT_FMT).toInstant(ZoneOffset.UTC)
                        : LocalDateTime.parse(dtStr + " 00:00:00", DT_FMT).toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException ex) {
                continue;
            }
            bars.add(NormalizedOhlcvBar.builder()
                    .symbol(symbol).assetClass(assetClass).providerName(PROVIDER_NAME)
                    .interval(interval).openTime(openTime).closeTime(openTime)
                    .open(JsonParseUtil.asBigDecimal(v, "open"))
                    .high(JsonParseUtil.asBigDecimal(v, "high"))
                    .low(JsonParseUtil.asBigDecimal(v, "low"))
                    .close(JsonParseUtil.asBigDecimal(v, "close"))
                    .volume(JsonParseUtil.asBigDecimal(v, "volume"))
                    .build());
        }
        // TwelveData returns newest-first; reverse to chronological
        Collections.reverse(bars);
        return bars;
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
            log.warn("[TwelveData] write-through failed for {}/{}: {}", symbol, interval, ex.getMessage());
        }
    }

    private static String mapInterval(String tf) {
        return switch (tf.toLowerCase()) {
            case "1m"  -> "1min";
            case "5m"  -> "5min";
            case "15m" -> "15min";
            case "30m" -> "30min";
            case "1h"  -> "1h";
            case "2h"  -> "2h";
            case "4h"  -> "4h";
            case "1d"  -> "1day";
            case "1w"  -> "1week";
            case "1mo" -> "1month";
            default    -> "1day";
        };
    }
}
