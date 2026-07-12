package com.mst.matt.tradingplatformapp.service.price.api.finnhub;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Finnhub {@code GET /search?q=...} — Symbol Search result.
 * Free-tier endpoint.
 */
public record FinnhubSearchResult(
        @SerializedName("count")  Integer count,
        @SerializedName("result") List<Match> result
) {
    public record Match(
            @SerializedName("description")   String description,
            @SerializedName("displaySymbol") String displaySymbol,
            @SerializedName("symbol")        String symbol,
            @SerializedName("type")          String type
    ) {}
}
