package com.mst.matt.tradingplatformapp.service.price.api.finnhub;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Finnhub {@code GET /stock/senate-trading} — Senate Trading disclosures.
 * Free-tier endpoint.
 */
public record FinnhubSenateTrade(
        @SerializedName("data")   List<SenateTransaction> data,
        @SerializedName("symbol") String symbol
) {
    public record SenateTransaction(
            @SerializedName("amount")           String amount,
            @SerializedName("assetDescription") String assetDescription,
            @SerializedName("assetType")        String assetType,
            @SerializedName("comment")          String comment,
            @SerializedName("firstName")        String firstName,
            @SerializedName("lastName")         String lastName,
            @SerializedName("owner")            String owner,
            @SerializedName("reportDate")       String reportDate,
            @SerializedName("symbol")           String symbol,
            @SerializedName("transactionDate")  String transactionDate,
            @SerializedName("transactionType")  String transactionType
    ) {}
}
