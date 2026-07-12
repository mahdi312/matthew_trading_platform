package com.mst.matt.tradingplatformapp.service.price.api.alphavantage;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * POJO for Alpha Vantage {@code EARNINGS} response (Fundamental Data).
 *
 * <pre>
 * {
 *   "symbol": "IBM",
 *   "annualEarnings": [
 *     { "fiscalDateEnding": "2025-12-31", "reportedEPS": "6.45" }
 *   ],
 *   "quarterlyEarnings": [
 *     {
 *       "fiscalDateEnding": "2026-03-31",
 *       "reportedDate": "2026-04-22",
 *       "reportedEPS": "1.65",
 *       "estimatedEPS": "1.58",
 *       "surprise": "0.07",
 *       "surprisePercentage": "4.43"
 *     }
 *   ]
 * }
 * </pre>
 */
public record AlphaVantageEarnings(
        @SerializedName("symbol")            String symbol,
        @SerializedName("annualEarnings")    List<AnnualEarning> annualEarnings,
        @SerializedName("quarterlyEarnings") List<QuarterlyEarning> quarterlyEarnings
) {

    public record AnnualEarning(
            @SerializedName("fiscalDateEnding") String fiscalDateEnding,
            @SerializedName("reportedEPS")      String reportedEPS
    ) {}

    public record QuarterlyEarning(
            @SerializedName("fiscalDateEnding")   String fiscalDateEnding,
            @SerializedName("reportedDate")       String reportedDate,
            @SerializedName("reportedEPS")        String reportedEPS,
            @SerializedName("estimatedEPS")       String estimatedEPS,
            @SerializedName("surprise")           String surprise,
            @SerializedName("surprisePercentage") String surprisePercentage
    ) {}
}
