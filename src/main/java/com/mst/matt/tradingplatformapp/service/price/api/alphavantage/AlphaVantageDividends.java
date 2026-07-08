package com.mst.matt.tradingplatformapp.service.price.api.alphavantage;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * POJO for Alpha Vantage {@code DIVIDENDS} response (Fundamental Data).
 *
 * <pre>
 * {
 *   "symbol": "IBM",
 *   "data": [
 *     {
 *       "ex_dividend_date": "2026-05-09",
 *       "declaration_date": "2026-04-29",
 *       "record_date": "2026-05-10",
 *       "payment_date": "2026-06-10",
 *       "amount": "1.67"
 *     }
 *   ]
 * }
 * </pre>
 */
public record AlphaVantageDividends(
        @SerializedName("symbol") String symbol,
        @SerializedName("data")   List<DividendEntry> data
) {

    public record DividendEntry(
            @SerializedName("ex_dividend_date")  String exDividendDate,
            @SerializedName("declaration_date")  String declarationDate,
            @SerializedName("record_date")       String recordDate,
            @SerializedName("payment_date")      String paymentDate,
            @SerializedName("amount")            String amount
    ) {}
}
