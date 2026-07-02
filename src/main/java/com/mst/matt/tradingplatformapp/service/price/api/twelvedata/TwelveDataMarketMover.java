package com.mst.matt.tradingplatformapp.service.price.api.twelvedata;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * POJO for TwelveData {@code GET /market_movers} response.
 * Returns the top gaining or losing stocks for a given exchange.
 */
public record TwelveDataMarketMover(
        @SerializedName("gainers") List<Mover> gainers,
        @SerializedName("losers")  List<Mover> losers
) {

    public record Mover(
            @SerializedName("symbol")         String symbol,
            @SerializedName("name")           String name,
            @SerializedName("exchange")       String exchange,
            @SerializedName("currency")       String currency,
            @SerializedName("close")          String close,
            @SerializedName("change")         String change,
            @SerializedName("percent_change") String percentChange
    ) {}
}
