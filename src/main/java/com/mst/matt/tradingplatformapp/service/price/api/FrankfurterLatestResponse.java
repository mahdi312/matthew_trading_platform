package com.mst.matt.tradingplatformapp.service.price.api;

import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import com.mst.matt.tradingplatformapp.service.price.JsonParseUtil;
import com.mst.matt.tradingplatformapp.service.price.PriceQuote;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Frankfurter {@code /latest?from=USD&to=EUR} — report.html "Latest Rates".
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

    public Optional<PriceQuote> toPriceQuote(String from, String to) {
        if (!rates.has(to)) return Optional.empty();
        BigDecimal rate = JsonParseUtil.asBigDecimal(rates, to);
        if (rate.compareTo(BigDecimal.ZERO) == 0) return Optional.empty();
        return Optional.of(PriceQuote.builder()
                .symbol(from + "/" + to)
                .assetName(from + "/" + to)
                .assetType(AssetType.FOREX)
                .price(rate)
                .change24h(BigDecimal.ZERO)
                .changePct24h(BigDecimal.ZERO)
                .currency(to)
                .exchange("Frankfurter (ECB)")
                .timestamp(LocalDateTime.now())
                .isUp(true)
                .build());
    }
}
