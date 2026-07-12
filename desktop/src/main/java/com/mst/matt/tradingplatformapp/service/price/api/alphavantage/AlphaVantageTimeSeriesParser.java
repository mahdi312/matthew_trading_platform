package com.mst.matt.tradingplatformapp.service.price.api.alphavantage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import com.mst.matt.tradingplatformapp.service.price.JsonParseUtil;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Parses all Alpha Vantage TIME_SERIES_* responses including:
 * <ul>
 *   <li>TIME_SERIES_INTRADAY (intraday OHLCV)</li>
 *   <li>TIME_SERIES_DAILY / TIME_SERIES_DAILY_ADJUSTED</li>
 *   <li>TIME_SERIES_WEEKLY / TIME_SERIES_WEEKLY_ADJUSTED</li>
 *   <li>TIME_SERIES_MONTHLY / TIME_SERIES_MONTHLY_ADJUSTED</li>
 *   <li>FX_DAILY / FX_WEEKLY / FX_MONTHLY (forex series)</li>
 *   <li>DIGITAL_CURRENCY_DAILY / WEEKLY / MONTHLY (crypto series)</li>
 * </ul>
 *
 * <p>Key format mapping (field names differ per series type):
 * <ul>
 *   <li>Standard equity: "1. open", "2. high", "3. low", "4. close", "5. volume"</li>
 *   <li>Adjusted equity: "1. open", "2. high", "3. low", "4. close",
 *       "5. adjusted close", "6. volume"</li>
 *   <li>Forex:           "1. open", "2. high", "3. low", "4. close" (no volume)</li>
 *   <li>Crypto:          "1a. open (USD)", "2a. high (USD)", "3a. low (USD)",
 *       "4a. close (USD)", "5. volume"</li>
 * </ul>
 */
public final class AlphaVantageTimeSeriesParser {

    private AlphaVantageTimeSeriesParser() {}

    /**
     * Parses any Alpha Vantage time-series JSON root into a list of OHLCV bars.
     *
     * @param root      root JSON object from the API response
     * @param sym       normalized symbol string
     * @param tf        timeframe label (used as-is on OhlcvBar)
     * @param limit     max bars to return (most recent); 0 = unlimited
     * @param intraday  true if response contains datetime (yyyy-MM-dd HH:mm:ss) keys
     * @param assetType STOCK, FOREX, or CRYPTO
     * @return list of OhlcvBar in chronological order (oldest → newest)
     */
    public static List<OhlcvBar> parse(JsonObject root, String sym, String tf,
                                       int limit, boolean intraday, AssetType assetType) {
        if (root == null) return List.of();

        // Find the data series key — could be "Time Series (5min)", "Time Series (Daily)",
        // "Weekly Time Series", "Monthly Time Series", "Time Series FX (Daily)",
        // "Time Series Crypto (Daily)", etc.
        String seriesKey = root.keySet().stream()
                .filter(k -> k.contains("Time Series") || k.contains("time series"))
                .findFirst()
                .orElse(null);
        if (seriesKey == null) return List.of();

        JsonObject series = root.getAsJsonObject(seriesKey);
        if (series == null || series.isEmpty()) return List.of();

        List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(series.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey));  // chronological order

        int start = (limit > 0) ? Math.max(0, entries.size() - limit) : 0;

        DateTimeFormatter intraFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter dayFmt   = DateTimeFormatter.ISO_LOCAL_DATE;
        List<OhlcvBar> bars = new ArrayList<>(entries.size() - start);

        for (int i = start; i < entries.size(); i++) {
            Map.Entry<String, JsonElement> e = entries.get(i);
            if (!e.getValue().isJsonObject()) continue;
            JsonObject bar = e.getValue().getAsJsonObject();

            LocalDateTime openTime;
            try {
                openTime = intraday
                        ? LocalDateTime.parse(e.getKey(), intraFmt)
                        : LocalDate.parse(e.getKey(), dayFmt).atStartOfDay();
            } catch (Exception ex) {
                continue; // skip malformed date entry
            }

            bars.add(OhlcvBar.builder()
                    .symbol(sym)
                    .timeframe(tf)
                    .openTime(openTime)
                    .open(resolveOpen(bar, assetType))
                    .high(resolveHigh(bar, assetType))
                    .low(resolveLow(bar, assetType))
                    .close(resolveClose(bar, assetType))
                    .volume(resolveVolume(bar, assetType))
                    .assetType(assetType)
                    .build());
        }
        return bars;
    }

    // ─── Field resolution helpers ──────────────────────────────────────────────

    /**
     * Resolves the "open" field from a bar object, handling all series field name variants.
     */
    private static BigDecimal resolveOpen(JsonObject bar, AssetType type) {
        // Crypto uses "1a. open (USD)" or "1b. open (XXX)"
        if (type == AssetType.CRYPTO) {
            BigDecimal v = firstNonZero(bar, "1a. open (USD)", "1b. open (BTC)", "1. open");
            return v.compareTo(BigDecimal.ZERO) != 0 ? v : JsonParseUtil.asBigDecimal(bar, "1. open");
        }
        return JsonParseUtil.asBigDecimal(bar, "1. open");
    }

    private static BigDecimal resolveHigh(JsonObject bar, AssetType type) {
        if (type == AssetType.CRYPTO) {
            BigDecimal v = firstNonZero(bar, "2a. high (USD)", "2b. high (BTC)", "2. high");
            return v.compareTo(BigDecimal.ZERO) != 0 ? v : JsonParseUtil.asBigDecimal(bar, "2. high");
        }
        return JsonParseUtil.asBigDecimal(bar, "2. high");
    }

    private static BigDecimal resolveLow(JsonObject bar, AssetType type) {
        if (type == AssetType.CRYPTO) {
            BigDecimal v = firstNonZero(bar, "3a. low (USD)", "3b. low (BTC)", "3. low");
            return v.compareTo(BigDecimal.ZERO) != 0 ? v : JsonParseUtil.asBigDecimal(bar, "3. low");
        }
        return JsonParseUtil.asBigDecimal(bar, "3. low");
    }

    private static BigDecimal resolveClose(JsonObject bar, AssetType type) {
        if (type == AssetType.CRYPTO) {
            BigDecimal v = firstNonZero(bar, "4a. close (USD)", "4b. close (BTC)", "4. close");
            return v.compareTo(BigDecimal.ZERO) != 0 ? v : JsonParseUtil.asBigDecimal(bar, "4. close");
        }
        // Prefer adjusted close when available (TIME_SERIES_DAILY_ADJUSTED, etc.)
        if (bar.has("5. adjusted close")) {
            BigDecimal adj = JsonParseUtil.asBigDecimal(bar, "5. adjusted close");
            if (adj.compareTo(BigDecimal.ZERO) != 0) return adj;
        }
        return JsonParseUtil.asBigDecimal(bar, "4. close");
    }

    private static BigDecimal resolveVolume(JsonObject bar, AssetType type) {
        if (type == AssetType.CRYPTO) {
            // Crypto uses "5. volume" directly
            return JsonParseUtil.asBigDecimal(bar, "5. volume");
        }
        if (type == AssetType.FOREX) {
            // Forex series has no volume; return zero
            return BigDecimal.ZERO;
        }
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
