package com.mst.matt.tradingplatformapp.service.price.api.twelvedata;

import com.google.gson.annotations.SerializedName;

/**
 * POJO for TwelveData {@code GET /currency_conversion} response.
 *
 * <pre>
 * {
 *   "symbol": "EUR/USD",
 *   "rate": 1.0862,
 *   "amount": 108.62
 * }
 * </pre>
 */
public record TwelveDataCurrencyConversion(
        @SerializedName("symbol") String symbol,
        @SerializedName("rate")   Double rate,
        @SerializedName("amount") Double amount
) {}
