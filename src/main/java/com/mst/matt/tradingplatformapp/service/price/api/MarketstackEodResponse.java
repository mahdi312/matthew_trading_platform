package com.mst.matt.tradingplatformapp.service.price.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import com.mst.matt.tradingplatformapp.service.price.JsonParseUtil;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Marketstack {@code /v1/eod} — report fixture shape in test resources. */
public record MarketstackEodResponse(List<OhlcvBar> bars) {

    public static Optional<MarketstackEodResponse> fromRoot(JsonObject root, String sym,
                                                            String timeframe, int limit) {
        if (root == null || root.has("error")) return Optional.empty();
        JsonArray data = root.getAsJsonArray("data");
        if (data == null || data.isEmpty()) return Optional.empty();

        List<OhlcvBar> bars = new ArrayList<>();
        for (var el : data) {
            JsonObject d = el.getAsJsonObject();
            if (!d.has("date")) continue;
            LocalDate date = LocalDate.parse(d.get("date").getAsString().substring(0, 10));
            bars.add(OhlcvBar.builder()
                    .symbol(sym)
                    .timeframe(timeframe)
                    .openTime(date.atStartOfDay())
                    .open(JsonParseUtil.asBigDecimal(d, "open"))
                    .high(JsonParseUtil.asBigDecimal(d, "high"))
                    .low(JsonParseUtil.asBigDecimal(d, "low"))
                    .close(JsonParseUtil.asBigDecimal(d, "close"))
                    .volume(JsonParseUtil.asBigDecimal(d, "volume"))
                    .assetType(AssetType.STOCK)
                    .build());
        }
        bars.sort(Comparator.comparing(OhlcvBar::getOpenTime));
        if (bars.size() > limit) {
            bars = bars.subList(bars.size() - limit, bars.size());
        }
        return bars.isEmpty() ? Optional.empty() : Optional.of(new MarketstackEodResponse(bars));
    }
}
