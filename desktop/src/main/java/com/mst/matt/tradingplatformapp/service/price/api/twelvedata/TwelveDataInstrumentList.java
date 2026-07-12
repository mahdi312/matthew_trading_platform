package com.mst.matt.tradingplatformapp.service.price.api.twelvedata;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Generic wrapper for TwelveData list endpoints:
 * {@code /stocks}, {@code /etfs}, {@code /indices}, {@code /cryptocurrencies},
 * {@code /forex_pairs}, {@code /mutual_funds}.
 *
 * <pre>{"data": [...], "status": "ok", "count": 123}</pre>
 */
public record TwelveDataInstrumentList(
        @SerializedName("data")   List<TwelveDataInstrument> data,
        @SerializedName("status") String status,
        @SerializedName("count")  Integer count
) {
    public boolean isOk() {
        return "ok".equalsIgnoreCase(status);
    }
}
