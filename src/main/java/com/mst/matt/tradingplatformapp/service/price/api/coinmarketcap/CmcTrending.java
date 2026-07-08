package com.mst.matt.tradingplatformapp.service.price.api.coinmarketcap;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * Response models for trending endpoints:
 * {@code GET /v1/cryptocurrency/trending/latest},
 * {@code GET /v1/cryptocurrency/trending/gainers-losers},
 * {@code GET /v1/cryptocurrency/trending/most-visited}.
 */
public final class CmcTrending {

    private CmcTrending() {}

    public record TrendingEntry(
            @SerializedName("id")       int                   id,
            @SerializedName("name")     String                name,
            @SerializedName("symbol")   String                symbol,
            @SerializedName("slug")     String                slug,
            @SerializedName("cmc_rank") Integer               cmcRank,
            @SerializedName("quote")    Map<String, CmcQuote> quote
    ) {
        public CmcQuote usdQuote() {
            return quote != null ? quote.get("USD") : null;
        }
    }

    public record TrendingResponse(
            @SerializedName("data")   List<TrendingEntry> data,
            @SerializedName("status") CmcStatus           status
    ) {}

    /** For gainers-losers the data has two fields. */
    public record GainersLosersData(
            @SerializedName("gainers") List<TrendingEntry> gainers,
            @SerializedName("losers")  List<TrendingEntry> losers
    ) {}

    public record GainersLosersResponse(
            @SerializedName("data")   GainersLosersData data,
            @SerializedName("status") CmcStatus         status
    ) {}
}
