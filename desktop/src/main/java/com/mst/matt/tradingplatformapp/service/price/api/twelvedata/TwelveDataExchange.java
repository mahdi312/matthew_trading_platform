package com.mst.matt.tradingplatformapp.service.price.api.twelvedata;

import com.google.gson.annotations.SerializedName;

/**
 * POJO representing a single exchange entry from TwelveData
 * {@code GET /exchanges} and {@code GET /crypto_exchanges}.
 */
public record TwelveDataExchange(
        @SerializedName("name")       String name,
        @SerializedName("code")       String code,
        @SerializedName("country")    String country,
        @SerializedName("timezone")   String timezone,
        @SerializedName("open")       String open,
        @SerializedName("close")      String close,
        // Crypto exchanges may include these additional fields
        @SerializedName("url")        String url
) {}
