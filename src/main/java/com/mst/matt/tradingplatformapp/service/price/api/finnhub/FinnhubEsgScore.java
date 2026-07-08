package com.mst.matt.tradingplatformapp.service.price.api.finnhub;

import com.google.gson.annotations.SerializedName;

/**
 * Finnhub {@code GET /esg} — ESG Scores.
 * Free-tier endpoint.
 */
public record FinnhubEsgScore(
        @SerializedName("symbol")        String symbol,
        @SerializedName("totalESGScore") Double totalESGScore,
        @SerializedName("environmentScore") Double environmentScore,
        @SerializedName("socialScore")    Double socialScore,
        @SerializedName("governanceScore") Double governanceScore,
        @SerializedName("ratingMonth")    Integer ratingMonth,
        @SerializedName("ratingYear")     Integer ratingYear
) {}
