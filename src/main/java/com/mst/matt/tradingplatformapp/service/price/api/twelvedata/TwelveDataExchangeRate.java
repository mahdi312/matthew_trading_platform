package com.mst.matt.tradingplatformapp.service.price.api.twelvedata;

import com.google.gson.annotations.SerializedName;

/**
 * POJO for TwelveData {@code GET /exchange_rate} response.
 *
 * <pre>
 * {
 *   "symbol": "USD/JPY",
 *   "rate": 144.32,
 *   "timestamp": 1751500800
 * }
 * </pre>
 */
public record TwelveDataExchangeRate(
        @SerializedName("symbol")    String symbol,
        @SerializedName("rate")      Double rate,
        @SerializedName("timestamp") Long timestamp
) {}
