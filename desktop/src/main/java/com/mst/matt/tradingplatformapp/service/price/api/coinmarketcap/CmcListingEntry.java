package com.mst.matt.tradingplatformapp.service.price.api.coinmarketcap;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 * Single cryptocurrency entry returned by listings/latest and quotes/latest.
 */
public record CmcListingEntry(
        @SerializedName("id")                int                  id,
        @SerializedName("name")              String               name,
        @SerializedName("symbol")            String               symbol,
        @SerializedName("slug")              String               slug,
        @SerializedName("cmc_rank")          Integer              cmcRank,
        @SerializedName("num_market_pairs")  Integer              numMarketPairs,
        @SerializedName("circulating_supply") double              circulatingSupply,
        @SerializedName("total_supply")      double               totalSupply,
        @SerializedName("max_supply")        Double               maxSupply,
        @SerializedName("infinite_supply")   boolean              infiniteSupply,
        @SerializedName("last_updated")      String               lastUpdated,
        @SerializedName("date_added")        String               dateAdded,
        @SerializedName("tags")              java.util.List<String> tags,
        @SerializedName("quote")             Map<String, CmcQuote> quote
) {
    /** Convenience: returns USD quote or null. */
    public CmcQuote usdQuote() {
        return quote != null ? quote.get("USD") : null;
    }
}
