package com.mst.matt.tradingplatformapp.service.price.api.alphavantage;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * POJO for Alpha Vantage Economic Indicators responses (Section 12).
 * Covers: {@code REAL_GDP}, {@code REAL_GDP_PER_CAPITA}, {@code TREASURY_YIELD},
 * {@code FEDERAL_FUNDS_RATE}, {@code CPI}, {@code INFLATION}, {@code RETAIL_SALES},
 * {@code DURABLE_GOODS_ORDERS}, {@code UNEMPLOYMENT}, {@code NONFARM_PAYROLL}.
 *
 * <pre>
 * {
 *   "name": "Real Gross Domestic Product",
 *   "interval": "annual",
 *   "unit": "billions of dollars",
 *   "data": [
 *     { "date": "2025-01-01", "value": "23459.0" }
 *   ]
 * }
 * </pre>
 */
public record AlphaVantageEconomicIndicator(
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
