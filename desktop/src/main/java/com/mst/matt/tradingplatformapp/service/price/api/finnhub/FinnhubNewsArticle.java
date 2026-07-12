package com.mst.matt.tradingplatformapp.service.price.api.finnhub;

import com.google.gson.annotations.SerializedName;

/**
 * Finnhub news article — used by both {@code GET /news} (market news) and
 * {@code GET /company-news} (company-specific news).
 * Free-tier endpoint.
 */
public record FinnhubNewsArticle(
        @SerializedName("category") String category,
        @SerializedName("datetime") Long datetime,
        @SerializedName("headline") String headline,
        @SerializedName("id")       Long id,
        @SerializedName("image")    String image,
        @SerializedName("related")  String related,
        @SerializedName("source")   String source,
        @SerializedName("summary")  String summary,
        @SerializedName("url")      String url
) {}
