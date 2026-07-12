package com.mst.matt.tradingplatformapp.service.price.api.alphavantage;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Parsed result for Alpha Vantage Technical Indicator responses (Section 13).
 *
 * <p>Covers all 60+ indicator functions. The response format is:
 * <pre>
 * {
 *   "Meta Data": { "1: Symbol": "IBM", "2: Indicator": "SMA", ... },
 *   "Technical Analysis: SMA": {
 *     "2026-07-02": { "SMA": "144.85" },
 *     "2026-07-01": { "SMA": "144.32" }
 *   }
 * }
 * </pre>
 *
 * <p>For multi-value indicators (MACD, BBANDS, STOCH, etc.) the value map
 * contains multiple keys per date entry.
 */
public record AlphaVantageTechnicalIndicator(
        String symbol,
        String indicator,
        String interval,
        String timePeriod,
        String seriesType,
        List<DataPoint> data
) {

    public record DataPoint(
            String date,
            Map<String, String> values
    ) {}

    /**
     * Parses a raw JSON root returned by any Alpha Vantage technical indicator endpoint.
     *
     * @param root      root JsonObject from the API
     * @param symbol    symbol being queried
     * @param limit     max data points to return (most-recent first)
     * @return parsed indicator result, or empty list on failure
     */
    public static AlphaVantageTechnicalIndicator fromRoot(
            JsonObject root, String symbol, int limit) {

        if (root == null) {
            return new AlphaVantageTechnicalIndicator(symbol, "", "", "", "", List.of());
        }

        // Extract meta data
        String indicatorName = "";
        String interval      = "";
        String timePeriod    = "";
        String seriesType    = "";

        if (root.has("Meta Data")) {
            JsonObject meta = root.getAsJsonObject("Meta Data");
            // Common meta keys: "2: Indicator", "3: Last Refreshed", "4: Interval"
            if (meta.has("2: Indicator")) indicatorName = meta.get("2: Indicator").getAsString();
            if (meta.has("4: Interval"))  interval      = meta.get("4: Interval").getAsString();
            if (meta.has("5: Time Period")) timePeriod  = meta.get("5: Time Period").getAsString();
            if (meta.has("6: Series Type")) seriesType  = meta.get("6: Series Type").getAsString();
        }

        // Find the "Technical Analysis: XXX" key
        String analysisKey = root.keySet().stream()
                .filter(k -> k.startsWith("Technical Analysis:"))
                .findFirst()
                .orElse(null);

        if (analysisKey == null) {
            return new AlphaVantageTechnicalIndicator(symbol, indicatorName, interval, timePeriod, seriesType, List.of());
        }

        JsonObject series = root.getAsJsonObject(analysisKey);
        List<Map.Entry<String, com.google.gson.JsonElement>> entries =
                new ArrayList<>(series.entrySet());

        // Sort descending (newest first), then apply limit
        entries.sort(Comparator.comparing(Map.Entry<String, com.google.gson.JsonElement>::getKey).reversed());
        if (limit > 0 && entries.size() > limit) {
            entries = entries.subList(0, limit);
        }

        List<DataPoint> points = new ArrayList<>(entries.size());
        for (Map.Entry<String, com.google.gson.JsonElement> e : entries) {
            JsonObject val = e.getValue().getAsJsonObject();
            Map<String, String> valMap = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, com.google.gson.JsonElement> v : val.entrySet()) {
                valMap.put(v.getKey(), v.getValue().getAsString());
            }
            points.add(new DataPoint(e.getKey(), valMap));
        }

        return new AlphaVantageTechnicalIndicator(symbol, indicatorName, interval, timePeriod, seriesType, points);
    }
}
