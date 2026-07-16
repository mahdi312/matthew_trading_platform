package com.mst.matt.marketservice.client.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import com.mst.matt.marketservice.client.JsonParseUtil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Parses all Alpha Vantage TIME_SERIES_* responses into {@link NormalizedOhlcvBar} lists.
 *
 * <p>Supported series types:
 * <ul>
 *   <li>TIME_SERIES_INTRADAY (intraday OHLCV)</li>
 *   <li>TIME_SERIES_DAILY / TIME_SERIES_DAILY_ADJUSTED</li>
 *   <li>TIME_SERIES_WEEKLY / TIME_SERIES_WEEKLY_ADJUSTED</li>
 *   <li>TIME_SERIES_MONTHLY / TIME_SERIES_MONTHLY_ADJUSTED</li>
 *   <li>FX_DAILY / FX_WEEKLY / FX_MONTHLY (forex)</li>
 *   <li>DIGITAL_CURRENCY_DAILY / WEEKLY / MONTHLY (crypto)</li>
 * </ul>
 */
public final class AlphaVantageTimeSeriesParser {

    private AlphaVantageTimeSeriesParser() {}

    /**
     * Parses any Alpha Vantage time-series JSON root into NormalizedOhlcvBar list.
     *
     * @param root       root JSON object from the API response
     * @param symbol     normalized symbol string
     * @param interval   timeframe label (e.g. "1d", "1h")
     * @param limit      max bars to return (most recent); 0 = unlimited
     * @param intraday   true if response uses datetime keys (yyyy-MM-dd HH:mm:ss)
     * @param assetClass STOCK, FOREX, or CRYPTO
     * @param provider   provider name for the bar
     * @return list of NormalizedOhlcvBar in chronological order (oldest → newest)
     */
    public static List<NormalizedOhlcvBar> parse(JsonObject root,
                                                  String symbol,
                                                  String interval,
                                                  int limit,
                                                  boolean intraday,
                                                  AssetClass assetClass,
                                                  String provider) {
        if (root == null) return List.of();

        // Find the data series key
        String seriesKey = root.keySet().stream()
                .filter(k -> k.contains("Time Series") || k.contains("time series"))
                .findFirst()
                .orElse(null);
        if (seriesKey == null) return List.of();

        JsonObject series = root.getAsJsonObject(seriesKey);
        if (series == null || series.isEmpty()) return List.of();

        List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(series.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey)); // chronological

        int start = (limit > 0) ? Math.max(0, entries.size() - limit) : 0;

        DateTimeFormatter intraFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter dayFmt   = DateTimeFormatter.ISO_LOCAL_DATE;
        List<NormalizedOhlcvBar> bars = new ArrayList<>(entries.size() - start);

        for (int i = start; i < entries.size(); i++) {
            Map.Entry<String, JsonElement> e = entries.get(i);
            if (!e.getValue().isJsonObject()) continue;
            JsonObject bar = e.getValue().getAsJsonObject();

            LocalDateTime openLdt;
            try {
                openLdt = intraday
                        ? LocalDateTime.parse(e.getKey(), intraFmt)
                        : LocalDate.parse(e.getKey(), dayFmt).atStartOfDay();
            } catch (Exception ex) {
                continue;
            }

            bars.add(NormalizedOhlcvBar.builder()
                    .symbol(symbol)
                    .assetClass(assetClass)
                    .providerName(provider)
                    .interval(interval)
                    .openTime(openLdt.toInstant(ZoneOffset.UTC))
                    .closeTime(openLdt.toInstant(ZoneOffset.UTC))
                    .open(resolveOpen(bar, assetClass))
                    .high(resolveHigh(bar, assetClass))
                    .low(resolveLow(bar, assetClass))
                    .close(resolveClose(bar, assetClass))
                    .volume(resolveVolume(bar, assetClass))
                    .build());
        }
        return bars;
    }

    // ── Field resolution helpers ───────────────────────────────────────────────

    private static BigDecimal resolveOpen(JsonObject bar, AssetClass type) {
        if (type == AssetClass.CRYPTO) {
            BigDecimal v = firstNonZero(bar, "1a. open (USD)", "1b. open (BTC)", "1. open");
            return v.compareTo(BigDecimal.ZERO) != 0 ? v : JsonParseUtil.asBigDecimal(bar, "1. open");
        }
        return JsonParseUtil.asBigDecimal(bar, "1. open");
    }

    private static BigDecimal resolveHigh(JsonObject bar, AssetClass type) {
        if (type == AssetClass.CRYPTO) {
            BigDecimal v = firstNonZero(bar, "2a. high (USD)", "2b. high (BTC)", "2. high");
            return v.compareTo(BigDecimal.ZERO) != 0 ? v : JsonParseUtil.asBigDecimal(bar, "2. high");
        }
        return JsonParseUtil.asBigDecimal(bar, "2. high");
    }

    private static BigDecimal resolveLow(JsonObject bar, AssetClass type) {
        if (type == AssetClass.CRYPTO) {
            BigDecimal v = firstNonZero(bar, "3a. low (USD)", "3b. low (BTC)", "3. low");
            return v.compareTo(BigDecimal.ZERO) != 0 ? v : JsonParseUtil.asBigDecimal(bar, "3. low");
        }
        return JsonParseUtil.asBigDecimal(bar, "3. low");
    }

    private static BigDecimal resolveClose(JsonObject bar, AssetClass type) {
        if (type == AssetClass.CRYPTO) {
            BigDecimal v = firstNonZero(bar, "4a. close (USD)", "4b. close (BTC)", "4. close");
            return v.compareTo(BigDecimal.ZERO) != 0 ? v : JsonParseUtil.asBigDecimal(bar, "4. close");
        }
        // Prefer adjusted close when available
        if (bar.has("5. adjusted close")) {
            BigDecimal adj = JsonParseUtil.asBigDecimal(bar, "5. adjusted close");
            if (adj.compareTo(BigDecimal.ZERO) != 0) return adj;
        }
        return JsonParseUtil.asBigDecimal(bar, "4. close");
    }

    private static BigDecimal resolveVolume(JsonObject bar, AssetClass type) {
        if (type == AssetClass.FOREX) return BigDecimal.ZERO;
        if (type == AssetClass.CRYPTO) return JsonParseUtil.asBigDecimal(bar, "5. volume");
        // Adjusted daily uses "6. volume" when "5. adjusted close" is present
        if (bar.has("5. adjusted close") && bar.has("6. volume")) {
            return JsonParseUtil.asBigDecimal(bar, "6. volume");
        }
        return JsonParseUtil.asBigDecimal(bar, "5. volume");
    }

    private static BigDecimal firstNonZero(JsonObject bar, String... keys) {
        for (String key : keys) {
            if (bar.has(key)) {
                BigDecimal v = JsonParseUtil.asBigDecimal(bar, key);
                if (v.compareTo(BigDecimal.ZERO) != 0) return v;
            }
        }
        return BigDecimal.ZERO;
    }
}
