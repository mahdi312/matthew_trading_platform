package com.mst.matt.tradingplatformapp.service.price.api.finnhub;

import com.google.gson.annotations.SerializedName;

/**
 * Finnhub {@code GET /stock/profile2} — Company Profile.
 * Free-tier endpoint.
 */
public record FinnhubCompanyProfile(
        @SerializedName("country")              String country,
        @SerializedName("currency")             String currency,
        @SerializedName("exchange")             String exchange,
        @SerializedName("ipo")                  String ipo,
        @SerializedName("marketCapitalization") Double marketCapitalization,
        @SerializedName("name")                 String name,
        @SerializedName("phone")                String phone,
        @SerializedName("shareOutstanding")     Double shareOutstanding,
        @SerializedName("ticker")               String ticker,
        @SerializedName("weburl")               String weburl,
        @SerializedName("logo")                 String logo,
        @SerializedName("finnhubIndustry")      String finnhubIndustry
) {}
