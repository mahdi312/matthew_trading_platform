package com.mst.matt.tradingplatformapp.service.price.api.finnhub;

import com.google.gson.annotations.SerializedName;

/**
 * Finnhub {@code GET /economic?code=...} — Economic Data point.
 * Free-tier endpoint.
 */
public record FinnhubEconomicData(
        @SerializedName("timestamp") Long timestamp,
        @SerializedName("value")     Double value
) {}
