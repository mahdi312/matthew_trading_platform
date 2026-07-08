package com.mst.matt.tradingplatformapp.service.price.api.coinmarketcap;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * Response models for category endpoints:
 * {@code GET /v1/cryptocurrency/categories},
 * {@code GET /v1/cryptocurrency/category}.
 */
public final class CmcCategories {

    private CmcCategories() {}

    public record CategoryEntry(
            @SerializedName("id")                  String id,
            @SerializedName("name")                String name,
            @SerializedName("title")               String title,
            @SerializedName("description")         String description,
            @SerializedName("num_tokens")          Integer numTokens,
            @SerializedName("avg_price_change")    Double  avgPriceChange,
            @SerializedName("market_cap")          Double  marketCap,
            @SerializedName("market_cap_change")   Double  marketCapChange,
            @SerializedName("volume")              Double  volume,
            @SerializedName("volume_change")       Double  volumeChange,
            @SerializedName("last_updated")        String  lastUpdated
    ) {}

    public record CategoriesListResponse(
            @SerializedName("data")   List<CategoryEntry> data,
            @SerializedName("status") CmcStatus           status
    ) {}

    public record CategoryCoinsEntry(
            @SerializedName("id")     int                   id,
            @SerializedName("name")   String                name,
            @SerializedName("symbol") String                symbol,
            @SerializedName("quote")  Map<String, CmcQuote> quote
    ) {
        public CmcQuote usdQuote() {
            return quote != null ? quote.get("USD") : null;
        }
    }

    public record CategoryDetailData(
            @SerializedName("id")          String               id,
            @SerializedName("name")        String               name,
            @SerializedName("description") String               description,
            @SerializedName("coins")       List<CategoryCoinsEntry> coins
    ) {}

    public record CategoryDetailResponse(
            @SerializedName("data")   CategoryDetailData data,
            @SerializedName("status") CmcStatus          status
    ) {}
}
