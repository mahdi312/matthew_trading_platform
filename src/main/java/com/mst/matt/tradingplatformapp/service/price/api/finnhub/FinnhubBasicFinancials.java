package com.mst.matt.tradingplatformapp.service.price.api.finnhub;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

/**
 * Finnhub {@code GET /stock/metric} — Basic Financial Metrics.
 * Free-tier endpoint.
 */
public record FinnhubBasicFinancials(
        @SerializedName("symbol") String symbol,
        @SerializedName("metric") JsonObject metric,
        @SerializedName("series") JsonObject series
) {}
