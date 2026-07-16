package com.mst.matt.tradingplatformapp.service.price.api.finnhub;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Finnhub {@code GET /stock/etf-holdings} — ETF Holdings.
 * Free-tier endpoint.
 */
public record FinnhubEtfHolding(
        @SerializedName("symbol")   String symbol,
        @SerializedName("holdings") List<Holding> holdings
) {
    public record Holding(
            @SerializedName("symbol")  String symbol,
            @SerializedName("name")    String name,
            @SerializedName("isin")    String isin,
            @SerializedName("cusip")   String cusip,
            @SerializedName("share")   Double share,
            @SerializedName("value")   Double value
    ) {}
}
