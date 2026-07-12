package com.mst.matt.tradingplatformapp.service.price.api.alphavantage;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * POJO for Alpha Vantage {@code INSIDER_TRANSACTIONS} response (Alpha Intelligence™).
 *
 * <pre>
 * {
 *   "data": [
 *     {
 *       "symbol": "IBM",
 *       "transaction_date": "2026-06-15",
 *       "filing_date": "2026-06-17",
 *       "executive": "John Doe",
 *       "executive_title": "CEO",
 *       "security_type": "Common Stock",
 *       "acquisition_or_disposal": "A",
 *       "shares": "1500",
 *       "share_price": "144.50",
 *       "transaction_type": "S-1"
 *     }
 *   ]
 * }
 * </pre>
 */
public record AlphaVantageInsiderTransaction(
        @SerializedName("data") List<Transaction> data
) {

    public record Transaction(
            @SerializedName("symbol")                    String symbol,
            @SerializedName("transaction_date")          String transactionDate,
            @SerializedName("filing_date")               String filingDate,
            @SerializedName("executive")                 String executive,
            @SerializedName("executive_title")           String executiveTitle,
            @SerializedName("security_type")             String securityType,
            @SerializedName("acquisition_or_disposal")   String acquisitionOrDisposal,
            @SerializedName("shares")                    String shares,
            @SerializedName("share_price")               String sharePrice,
            @SerializedName("transaction_type")          String transactionType
    ) {}
}
