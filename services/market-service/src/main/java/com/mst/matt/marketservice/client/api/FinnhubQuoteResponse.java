package com.mst.matt.marketservice.client.api;

import com.google.gson.JsonObject;
import com.mst.matt.marketservice.client.JsonParseUtil;

import java.math.BigDecimal;
import java.util.Optional;

/** Finnhub {@code /quote} — fields {@code c,h,l,o,pc} per API docs. */
public record FinnhubQuoteResponse(
        BigDecimal current,
        BigDecimal high,
        BigDecimal low,
        BigDecimal open,
        BigDecimal previousClose) {

    public static Optional<FinnhubQuoteResponse> fromJson(JsonObject json) {
        if (json == null) return Optional.empty();
        BigDecimal c = JsonParseUtil.asBigDecimal(json, "c");
        if (c.compareTo(BigDecimal.ZERO) == 0) return Optional.empty();
        return Optional.of(new FinnhubQuoteResponse(
                c,
                JsonParseUtil.asBigDecimal(json, "h"),
                JsonParseUtil.asBigDecimal(json, "l"),
                JsonParseUtil.asBigDecimal(json, "o"),
                JsonParseUtil.asBigDecimal(json, "pc")));
    }
}
