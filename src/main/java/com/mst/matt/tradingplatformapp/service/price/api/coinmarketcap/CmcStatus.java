package com.mst.matt.tradingplatformapp.service.price.api.coinmarketcap;

import com.google.gson.annotations.SerializedName;

/**
 * CoinMarketCap API status object returned with every response.
 */
public record CmcStatus(
        @SerializedName("timestamp")      String timestamp,
        @SerializedName("error_code")     int    errorCode,
        @SerializedName("error_message")  String errorMessage,
        @SerializedName("elapsed")        int    elapsed,
        @SerializedName("credit_count")   int    creditCount
) {
    public boolean isSuccess() {
        return errorCode == 0;
    }
}
