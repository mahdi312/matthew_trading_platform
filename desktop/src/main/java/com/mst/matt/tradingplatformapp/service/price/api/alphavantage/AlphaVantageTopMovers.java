package com.mst.matt.tradingplatformapp.service.price.api.alphavantage;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * POJO for Alpha Vantage {@code TOP_GAINERS_LOSERS} response (Alpha Intelligence™).
 *
 * <pre>
 * {
 *   "metadata": "Top gainers, losers, and most actively traded US tickers",
 *   "last_updated": "2026-07-02 16:15:59 US/Eastern",
 *   "top_gainers": [
 *     { "ticker": "ABCD", "price": "10.50", "change_amount": "3.25", "change_percentage": "44.83%", "volume": "123456" }
 *   ],
 *   "top_losers": [...],
 *   "most_actively_traded": [...]
 * }
 * </pre>
 */
public record AlphaVantageTopMovers(
        @SerializedName("metadata")              String metadata,
        @SerializedName("last_updated")          String lastUpdated,
        @SerializedName("top_gainers")           List<Mover> topGainers,
        @SerializedName("top_losers")            List<Mover> topLosers,
        @SerializedName("most_actively_traded")  List<Mover> mostActivelyTraded
) {

    public record Mover(
            @SerializedName("ticker")            String ticker,
            @SerializedName("price")             String price,
            @SerializedName("change_amount")     String changeAmount,
            @SerializedName("change_percentage") String changePercentage,
            @SerializedName("volume")            String volume
    ) {}
}
