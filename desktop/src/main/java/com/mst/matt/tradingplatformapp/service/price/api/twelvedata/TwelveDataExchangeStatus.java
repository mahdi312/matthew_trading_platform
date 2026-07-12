package com.mst.matt.tradingplatformapp.service.price.api.twelvedata;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * POJO for TwelveData {@code GET /exchange_status} response.
 * Shows which exchanges are currently open or closed.
 */
public record TwelveDataExchangeStatus(
        @SerializedName("data") List<ExchangeInfo> data
) {

    public record ExchangeInfo(
            @SerializedName("name")           String name,
            @SerializedName("code")           String code,
            @SerializedName("country")        String country,
            @SerializedName("is_market_open") Boolean isMarketOpen,
            @SerializedName("time_after_open")  String timeAfterOpen,
            @SerializedName("time_to_open")     String timeToOpen,
            @SerializedName("time_to_close")    String timeToClose
    ) {}
}
