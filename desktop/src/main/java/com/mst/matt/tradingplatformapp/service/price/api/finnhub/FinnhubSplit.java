package com.mst.matt.tradingplatformapp.service.price.api.finnhub;

import com.google.gson.annotations.SerializedName;

/**
 * Finnhub {@code GET /stock/split} — Stock Split history entry.
 * Free-tier endpoint.
 */
public record FinnhubSplit(
        @SerializedName("symbol")     String symbol,
        @SerializedName("date")       String date,
        @SerializedName("fromFactor") Double fromFactor,
        @SerializedName("toFactor")   Double toFactor
) {}
