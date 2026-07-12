package com.mst.matt.tradingplatformapp.service.price.api.finnhub;

import com.google.gson.annotations.SerializedName;

/**
 * One entry from Finnhub {@code GET /stock/symbol}, {@code GET /forex/symbol},
 * or {@code GET /crypto/symbol}.
 * Free-tier endpoint.
 */
public record FinnhubSymbolEntry(
        @SerializedName("description")   String description,
        @SerializedName("displaySymbol") String displaySymbol,
        @SerializedName("symbol")        String symbol,
        @SerializedName("type")          String type,
        @SerializedName("currency")      String currency,
        @SerializedName("figi")          String figi,
        @SerializedName("mic")           String mic
) {}
