package com.mst.matt.tradingplatformapp.service.price.api.finnhub;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Finnhub {@code GET /insider-sentiment} — Aggregated Insider Sentiment.
 * Free-tier endpoint.
 */
public record FinnhubInsiderSentiment(
        @SerializedName("data")   List<SentimentEntry> data,
        @SerializedName("symbol") String symbol
) {
    public record SentimentEntry(
            @SerializedName("change")  Integer change,
            @SerializedName("month")   Integer month,
            @SerializedName("mspr")    Double mspr,
            @SerializedName("symbol")  String symbol,
            @SerializedName("year")    Integer year
    ) {}
}
