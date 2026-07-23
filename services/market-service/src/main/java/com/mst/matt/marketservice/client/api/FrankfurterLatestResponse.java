package com.mst.matt.marketservice.client.api;

import com.google.gson.JsonObject;
import com.mst.matt.marketservice.client.JsonParseUtil;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Frankfurter {@code /latest?from=USD&to=EUR} response shape.
 * {@code {"amount":1.0,"base":"USD","date":"2026-06-04","rates":{"EUR":0.85911,...}}}
 */
public record FrankfurterLatestResponse(
        String base,
        String date,
        JsonObject rates) {

    public static Optional<FrankfurterLatestResponse> fromJson(JsonObject json) {
        if (json == null || !json.has("rates")) return Optional.empty();
        return Optional.of(new FrankfurterLatestResponse(
                json.has("base") ? json.get("base").getAsString() : "",
                json.has("date") ? json.get("date").getAsString() : "",
                json.getAsJsonObject("rates")));
    }

    /**
     * Returns the rate for the given target currency, or empty if not present.
     */
    public Optional<BigDecimal> rateFor(String toCurrency) {
        if (rates == null || !rates.has(toCurrency)) return Optional.empty();
        BigDecimal rate = JsonParseUtil.asBigDecimal(rates, toCurrency);
        return rate.compareTo(BigDecimal.ZERO) == 0 ? Optional.empty() : Optional.of(rate);
    }
}
