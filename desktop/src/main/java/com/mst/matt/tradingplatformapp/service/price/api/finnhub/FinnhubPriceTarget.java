package com.mst.matt.tradingplatformapp.service.price.api.finnhub;

import com.google.gson.annotations.SerializedName;

/**
 * Finnhub {@code GET /stock/price-target} — Analyst Price Target.
 * Free-tier endpoint.
 */
public record FinnhubPriceTarget(
        @SerializedName("symbol")       String symbol,
        @SerializedName("targetHigh")   Double targetHigh,
        @SerializedName("targetLow")    Double targetLow,
        @SerializedName("targetMean")   Double targetMean,
        @SerializedName("targetMedian") Double targetMedian,
        @SerializedName("lastUpdated")  String lastUpdated
) {}
