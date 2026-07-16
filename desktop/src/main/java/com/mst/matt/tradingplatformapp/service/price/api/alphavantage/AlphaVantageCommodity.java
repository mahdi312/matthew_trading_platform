package com.mst.matt.tradingplatformapp.service.price.api.alphavantage;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * POJO for Alpha Vantage Commodities responses (Section 11).
 * Covers: {@code WTI}, {@code BRENT}, {@code NATURAL_GAS}, {@code GOLD},
 * {@code SILVER}, {@code COPPER}, {@code WHEAT}, {@code CORN}, {@code COMMODITY_CHAIN}.
 *
 * <pre>
 * {
 *   "name": "Crude Oil Prices WTI",
 *   "interval": "weekly",
 *   "unit": "dollars per barrel",
 *   "data": [
 *     { "date": "2026-06-30", "value": "79.45" }
 *   ]
 * }
 * </pre>
 */
public record AlphaVantageCommodity(
        @SerializedName("name")     String name,
        @SerializedName("interval") String interval,
        @SerializedName("unit")     String unit,
        @SerializedName("data")     List<DataPoint> data
) {

    public record DataPoint(
            @SerializedName("date")  String date,
            @SerializedName("value") String value
    ) {}
}
