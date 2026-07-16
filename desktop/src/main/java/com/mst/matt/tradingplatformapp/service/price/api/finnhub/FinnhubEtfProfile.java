package com.mst.matt.tradingplatformapp.service.price.api.finnhub;

import com.google.gson.annotations.SerializedName;

/**
 * Finnhub {@code GET /etf/profile} — ETF Profile.
 * Free-tier endpoint.
 */
public record FinnhubEtfProfile(
        @SerializedName("symbol")          String symbol,
        @SerializedName("name")            String name,
        @SerializedName("description")     String description,
        @SerializedName("assetClass")      String assetClass,
        @SerializedName("aum")             Double aum,
        @SerializedName("expenseRatio")    Double expenseRatio,
        @SerializedName("nav")             Double nav,
        @SerializedName("navCurrency")     String navCurrency,
        @SerializedName("domicile")        String domicile,
        @SerializedName("primaryExchange") String primaryExchange,
        @SerializedName("inceptionDate")   String inceptionDate,
        @SerializedName("website")         String website
) {}
