package com.mst.matt.tradingplatformapp.service.price.api.finnhub;

import com.google.gson.annotations.SerializedName;

/**
 * Finnhub {@code GET /news-sentiment} — Aggregated news sentiment for a stock.
 * Free-tier endpoint.
 */
public record FinnhubNewsSentiment(
        @SerializedName("symbol")        String symbol,
        @SerializedName("buzz")          Integer buzz,
        @SerializedName("newsScore")     Double newsScore,
        @SerializedName("sentiment")     String sentiment,
        @SerializedName("sectorAverage") Double sectorAverage
) {}
