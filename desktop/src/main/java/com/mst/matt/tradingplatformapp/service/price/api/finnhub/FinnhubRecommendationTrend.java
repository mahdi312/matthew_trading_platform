package com.mst.matt.tradingplatformapp.service.price.api.finnhub;

import com.google.gson.annotations.SerializedName;

/**
 * Finnhub {@code GET /stock/recommendation} — Analyst Recommendation Trends.
 * Free-tier endpoint.
 */
public record FinnhubRecommendationTrend(
        @SerializedName("buy")        Integer buy,
        @SerializedName("hold")       Integer hold,
        @SerializedName("period")     String period,
        @SerializedName("sell")       Integer sell,
        @SerializedName("strongBuy")  Integer strongBuy,
        @SerializedName("strongSell") Integer strongSell,
        @SerializedName("symbol")     String symbol
) {}
