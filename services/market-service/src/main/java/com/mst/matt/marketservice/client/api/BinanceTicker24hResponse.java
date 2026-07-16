package com.mst.matt.marketservice.client.api;

import com.google.gson.JsonObject;
import com.mst.matt.marketservice.client.JsonParseUtil;

import java.math.BigDecimal;
import java.util.Optional;

/** Binance {@code GET /api/v3/ticker/24hr} response shape. */
public record BinanceTicker24hResponse(
        String symbol,
        BigDecimal lastPrice,
        BigDecimal openPrice,
        BigDecimal highPrice,
        BigDecimal lowPrice,
        BigDecimal priceChange,
        BigDecimal priceChangePercent,
        BigDecimal volume) {

    public static Optional<BinanceTicker24hResponse> fromJson(JsonObject json) {
        if (json == null || !json.has("lastPrice")) return Optional.empty();
        BigDecimal last = JsonParseUtil.asBigDecimal(json, "lastPrice");
        if (last.compareTo(BigDecimal.ZERO) == 0) return Optional.empty();
        return Optional.of(new BinanceTicker24hResponse(
                json.has("symbol") ? json.get("symbol").getAsString() : "",
                last,
                JsonParseUtil.asBigDecimal(json, "openPrice"),
                JsonParseUtil.asBigDecimal(json, "highPrice"),
                JsonParseUtil.asBigDecimal(json, "lowPrice"),
                JsonParseUtil.asBigDecimal(json, "priceChange"),
                JsonParseUtil.asBigDecimal(json, "priceChangePercent"),
                JsonParseUtil.asBigDecimal(json, "volume")));
    }
}
