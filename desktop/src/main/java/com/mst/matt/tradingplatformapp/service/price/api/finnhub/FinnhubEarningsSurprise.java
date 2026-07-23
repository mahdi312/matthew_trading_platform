package com.mst.matt.tradingplatformapp.service.price.api.finnhub;

import com.google.gson.annotations.SerializedName;

/**
 * Finnhub {@code GET /stock/earnings} — Earnings Surprise.
 * Free-tier endpoint.
 */
public record FinnhubEarningsSurprise(
        @SerializedName("actual")          Double actual,
        @SerializedName("estimate")        Double estimate,
        @SerializedName("period")          String period,
        @SerializedName("quarter")         Integer quarter,
        @SerializedName("surprise")        Double surprise,
        @SerializedName("surprisePercent") Double surprisePercent,
        @SerializedName("symbol")          String symbol,
        @SerializedName("year")            Integer year
) {}
