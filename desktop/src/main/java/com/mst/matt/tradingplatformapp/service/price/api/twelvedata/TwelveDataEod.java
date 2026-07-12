package com.mst.matt.tradingplatformapp.service.price.api.twelvedata;

import com.google.gson.annotations.SerializedName;

/**
 * POJO for TwelveData {@code GET /eod} (End of Day) response.
 *
 * <pre>
 * {
 *   "symbol": "AAPL",
 *   "exchange": "NASDAQ",
 *   "datetime": "2026-06-30",
 *   "close": "214.88"
 * }
 * </pre>
 */
public record TwelveDataEod(
        @SerializedName("symbol")   String symbol,
        @SerializedName("exchange") String exchange,
        @SerializedName("datetime") String datetime,
        @SerializedName("close")    String close
) {}
