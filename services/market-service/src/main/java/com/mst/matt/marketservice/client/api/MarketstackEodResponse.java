package com.mst.matt.marketservice.client.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import com.mst.matt.marketservice.client.JsonParseUtil;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Marketstack {@code /v1/eod} — data array shape. */
public record MarketstackEodResponse(List<NormalizedOhlcvBar> bars) {

    public static Optional<MarketstackEodResponse> fromRoot(JsonObject root,
                                                             String sym,
                                                             String interval,
                                                             int limit,
                                                             String providerName) {
        if (root == null || root.has("error")) return Optional.empty();
        JsonArray data = root.getAsJsonArray("data");
        if (data == null || data.isEmpty()) return Optional.empty();

        List<NormalizedOhlcvBar> bars = new ArrayList<>();
        for (var el : data) {
            JsonObject d = el.getAsJsonObject();
            if (!d.has("date")) continue;
            LocalDate date = LocalDate.parse(d.get("date").getAsString().substring(0, 10));
            bars.add(NormalizedOhlcvBar.builder()
                    .symbol(sym)
                    .assetClass(AssetClass.STOCK)
                    .providerName(providerName)
                    .interval(interval)
                    .openTime(date.atStartOfDay().toInstant(ZoneOffset.UTC))
                    .closeTime(date.atStartOfDay().toInstant(ZoneOffset.UTC))
                    .open(JsonParseUtil.asBigDecimal(d, "open"))
                    .high(JsonParseUtil.asBigDecimal(d, "high"))
                    .low(JsonParseUtil.asBigDecimal(d, "low"))
                    .close(JsonParseUtil.asBigDecimal(d, "close"))
                    .volume(JsonParseUtil.asBigDecimal(d, "volume"))
                    .build());
        }
        bars.sort(Comparator.comparing(NormalizedOhlcvBar::getOpenTime));
        if (bars.size() > limit) {
            bars = bars.subList(bars.size() - limit, bars.size());
        }
        return bars.isEmpty() ? Optional.empty() : Optional.of(new MarketstackEodResponse(bars));
    }
}
