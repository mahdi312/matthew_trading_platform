package com.mst.matt.tradingplatformapp.service.price.api.twelvedata;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Wrapper for TwelveData {@code GET /exchanges} and {@code GET /crypto_exchanges} responses.
 */
public record TwelveDataExchangeList(
        @SerializedName("data")   List<TwelveDataExchange> data,
        @SerializedName("status") String status
) {
    public boolean isOk() {
        return "ok".equalsIgnoreCase(status);
    }
}
