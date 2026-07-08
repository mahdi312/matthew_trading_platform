package com.mst.matt.tradingplatformapp.service.price.api.finnhub;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Finnhub {@code GET /stock/insider-transactions} — Insider Transactions.
 * Free-tier endpoint.
 */
public record FinnhubInsiderTransaction(
        @SerializedName("data")   List<Transaction> data,
        @SerializedName("symbol") String symbol
) {
    public record Transaction(
            @SerializedName("change")          Long change,
            @SerializedName("filingDate")      String filingDate,
            @SerializedName("name")            String name,
            @SerializedName("share")           Long share,
            @SerializedName("symbol")          String symbol,
            @SerializedName("transactionDate") String transactionDate,
            @SerializedName("transactionCode") String transactionCode,
            @SerializedName("transactionPrice") Double transactionPrice
    ) {}
}
