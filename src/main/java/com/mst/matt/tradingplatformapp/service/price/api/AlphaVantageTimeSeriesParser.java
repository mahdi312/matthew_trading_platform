package com.mst.matt.tradingplatformapp.service.price.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import com.mst.matt.tradingplatformapp.service.price.JsonParseUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Parses Alpha Vantage daily/intraday series keys from report.html TIME_SERIES_* responses. */
public final class AlphaVantageTimeSeriesParser {

    private AlphaVantageTimeSeriesParser() {}

    public static List<OhlcvBar> parse(JsonObject root, String sym, String tf,
                                     int limit, boolean intraday, AssetType assetType) {
        String seriesKey = root.keySet().stream()
                .filter(k -> k.startsWith("Time Series"))
                .findFirst()
                .orElse(null);
        if (seriesKey == null) return List.of();
        JsonObject series = root.getAsJsonObject(seriesKey);
        if (series == null) return List.of();

        List<Map.Entry<String, JsonElement>> entries = new ArrayList<>(series.entrySet());
        entries.sort(Comparator.comparing(Map.Entry::getKey));
        int start = Math.max(0, entries.size() - limit);
        List<OhlcvBar> bars = new ArrayList<>();
        DateTimeFormatter intraFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        DateTimeFormatter dayFmt = DateTimeFormatter.ISO_LOCAL_DATE;

        for (int i = start; i < entries.size(); i++) {
            var e = entries.get(i);
            JsonObject bar = e.getValue().getAsJsonObject();
            LocalDateTime openTime = intraday
                    ? LocalDateTime.parse(e.getKey(), intraFmt)
                    : LocalDate.parse(e.getKey(), dayFmt).atStartOfDay();
            bars.add(OhlcvBar.builder()
                    .symbol(sym)
                    .timeframe(tf)
                    .openTime(openTime)
                    .open(JsonParseUtil.asBigDecimal(bar, "1. open"))
                    .high(JsonParseUtil.asBigDecimal(bar, "2. high"))
                    .low(JsonParseUtil.asBigDecimal(bar, "3. low"))
                    .close(JsonParseUtil.asBigDecimal(bar, "4. close"))
                    .volume(JsonParseUtil.asBigDecimal(bar, "5. volume"))
                    .assetType(assetType)
                    .build());
        }
        return bars;
    }
}
