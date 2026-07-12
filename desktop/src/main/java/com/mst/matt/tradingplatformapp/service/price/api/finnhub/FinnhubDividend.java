package com.mst.matt.tradingplatformapp.service.price.api.finnhub;

import com.google.gson.annotations.SerializedName;

/**
 * Finnhub {@code GET /stock/dividend} — Dividend history entry.
 * Free-tier endpoint.
 */
public record FinnhubDividend(
        @SerializedName("symbol")          String symbol,
        @SerializedName("date")            String date,
        @SerializedName("amount")          Double amount,
        @SerializedName("adjustedAmount")  Double adjustedAmount,
        @SerializedName("payDate")         String payDate,
        @SerializedName("recordDate")      String recordDate,
        @SerializedName("declarationDate") String declarationDate,
        @SerializedName("currency")        String currency,
        @SerializedName("freq")            String freq
) {}
