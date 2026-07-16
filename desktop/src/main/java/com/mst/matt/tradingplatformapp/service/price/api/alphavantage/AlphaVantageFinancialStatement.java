package com.mst.matt.tradingplatformapp.service.price.api.alphavantage;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Generic POJO for Alpha Vantage financial statement responses:
 * {@code INCOME_STATEMENT}, {@code BALANCE_SHEET}, {@code CASH_FLOW}.
 *
 * <pre>
 * {
 *   "symbol": "IBM",
 *   "annualReports": [ { "fiscalDateEnding": "2025-12-31", "reportedCurrency": "USD", ... } ],
 *   "quarterlyReports": [ { ... } ]
 * }
 * </pre>
 *
 * Individual report fields are kept as raw {@link JsonObject} because each
 * statement has dozens of distinct numeric fields. Callers can extract
 * specific fields using {@link com.mst.matt.tradingplatformapp.service.price.JsonParseUtil}.
 */
public record AlphaVantageFinancialStatement(
        @SerializedName("symbol")           String symbol,
        @SerializedName("annualReports")    List<JsonObject> annualReports,
        @SerializedName("quarterlyReports") List<JsonObject> quarterlyReports
) {}
