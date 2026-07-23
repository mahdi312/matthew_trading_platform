package com.mst.matt.tradingplatformapp.service.price.api.coinmarketcap;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Response model for {@code GET /v1/cryptocurrency/map}.
 */
public record CmcCryptocurrencyMap(
        @SerializedName("data")   List<CmcCoinEntry> data,
        @SerializedName("status") CmcStatus          status
) {
    public record CmcCoinEntry(
            @SerializedName("id")                     int     id,
            @SerializedName("name")                   String  name,
            @SerializedName("symbol")                 String  symbol,
            @SerializedName("slug")                   String  slug,
            @SerializedName("rank")                   Integer rank,
            @SerializedName("is_active")              int     isActive,
            @SerializedName("first_historical_data")  String  firstHistoricalData,
            @SerializedName("last_historical_data")   String  lastHistoricalData,
            @SerializedName("platform")               Object  platform
    ) {}
}
