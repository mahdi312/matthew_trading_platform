package com.mst.matt.marketservice.client.api;

import com.google.gson.JsonObject;
import com.mst.matt.marketservice.client.JsonParseUtil;

import java.math.BigDecimal;
import java.util.Optional;

/** Alpha Vantage {@code GLOBAL_QUOTE} response shape. */
public record AlphaVantageGlobalQuote(
        String symbol,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal price,
        BigDecimal volume,
        BigDecimal previousClose,
        BigDecimal change,
        BigDecimal changePercent) {

    public static Optional<AlphaVantageGlobalQuote> fromRoot(JsonObject root) {
        if (root == null || !root.has("Global Quote")) return Optional.empty();
        JsonObject q = root.getAsJsonObject("Global Quote");
        BigDecimal price = JsonParseUtil.asBigDecimal(q, "05. price");
        if (price.compareTo(BigDecimal.ZERO) == 0) return Optional.empty();
        return Optional.of(new AlphaVantageGlobalQuote(
                text(q, "01. symbol"),
                JsonParseUtil.asBigDecimal(q, "02. open"),
                JsonParseUtil.asBigDecimal(q, "03. high"),
                JsonParseUtil.asBigDecimal(q, "04. low"),
                price,
                JsonParseUtil.asBigDecimal(q, "06. volume"),
                JsonParseUtil.asBigDecimal(q, "08. previous close"),
                JsonParseUtil.asBigDecimal(q, "09. change"),
                JsonParseUtil.asPercent(q, "10. change percent")));
    }

    private static String text(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsString() : "";
    }
}
