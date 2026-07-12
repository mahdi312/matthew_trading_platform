package com.mst.matt.tradingplatformapp.service.price.api.finnhub;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Finnhub {@code GET /stock/ownership} — Institutional Ownership.
 * Free-tier endpoint.
 */
public record FinnhubInstitutionalOwnership(
        @SerializedName("ownership") List<Institution> ownership,
        @SerializedName("symbol")    String symbol
) {
    public record Institution(
            @SerializedName("change")          Long change,
            @SerializedName("filingDate")      String filingDate,
            @SerializedName("name")            String name,
            @SerializedName("reportDate")      String reportDate,
            @SerializedName("share")           Long share,
            @SerializedName("symbol")          String symbol
    ) {}
}
