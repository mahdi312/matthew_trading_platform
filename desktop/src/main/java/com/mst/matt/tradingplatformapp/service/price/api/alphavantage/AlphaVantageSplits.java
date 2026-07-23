package com.mst.matt.tradingplatformapp.service.price.api.alphavantage;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * POJO for Alpha Vantage {@code SPLITS} response (Fundamental Data).
 *
 * <pre>
 * {
 *   "symbol": "AAPL",
 *   "data": [
 *     {
 *       "effective_date": "2020-08-31",
 *       "split_factor": "4"
 *     }
 *   ]
 * }
 * </pre>
 */
public record AlphaVantageSplits(
        @SerializedName("symbol") String symbol,
        @SerializedName("data")   List<SplitEntry> data
) {

    public record SplitEntry(
            @SerializedName("effective_date") String effectiveDate,
            @SerializedName("split_factor")   String splitFactor
    ) {}
}
