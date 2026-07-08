package com.mst.matt.tradingplatformapp.service.price.api.coinmarketcap;

import com.google.gson.annotations.SerializedName;

/**
 * Price quote object inside {@code quote} maps for a given currency.
 * Used in listings, quotes/latest, and other pricing endpoints.
 */
public record CmcQuote(
        @SerializedName("price")                    double  price,
        @SerializedName("volume_24h")               double  volume24h,
        @SerializedName("volume_change_24h")        double  volumeChange24h,
        @SerializedName("percent_change_1h")        double  percentChange1h,
        @SerializedName("percent_change_24h")       double  percentChange24h,
        @SerializedName("percent_change_7d")        double  percentChange7d,
        @SerializedName("percent_change_30d")       double  percentChange30d,
        @SerializedName("percent_change_60d")       double  percentChange60d,
        @SerializedName("percent_change_90d")       double  percentChange90d,
        @SerializedName("market_cap")               double  marketCap,
        @SerializedName("market_cap_dominance")     double  marketCapDominance,
        @SerializedName("fully_diluted_market_cap") double  fullyDilutedMarketCap,
        @SerializedName("last_updated")             String  lastUpdated
) {}
