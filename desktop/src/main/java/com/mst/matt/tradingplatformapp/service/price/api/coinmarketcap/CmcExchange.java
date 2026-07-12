package com.mst.matt.tradingplatformapp.service.price.api.coinmarketcap;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * Response models for exchange endpoints:
 * {@code GET /v1/exchange/map},
 * {@code GET /v1/exchange/listings/latest},
 * {@code GET /v1/exchange/info}.
 */
public final class CmcExchange {

    private CmcExchange() {}

    public record ExchangeMapEntry(
            @SerializedName("id")           int    id,
            @SerializedName("name")         String name,
            @SerializedName("slug")         String slug,
            @SerializedName("is_active")    int    isActive,
            @SerializedName("first_historical_data") String firstHistoricalData,
            @SerializedName("last_historical_data")  String lastHistoricalData
    ) {}

    public record ExchangeMapResponse(
            @SerializedName("data")   List<ExchangeMapEntry> data,
            @SerializedName("status") CmcStatus              status
    ) {}

    public record ExchangeQuote(
            @SerializedName("volume_24h")               double volume24h,
            @SerializedName("volume_24h_adjusted")      double volume24hAdjusted,
            @SerializedName("volume_7d")                double volume7d,
            @SerializedName("volume_30d")               double volume30d,
            @SerializedName("percent_change_volume_24h") double percentChangeVolume24h,
            @SerializedName("percent_change_volume_7d")  double percentChangeVolume7d,
            @SerializedName("percent_change_volume_30d") double percentChangeVolume30d,
            @SerializedName("last_updated")             String lastUpdated
    ) {}

    public record ExchangeListingEntry(
            @SerializedName("id")               int    id,
            @SerializedName("name")             String name,
            @SerializedName("slug")             String slug,
            @SerializedName("num_market_pairs") int    numMarketPairs,
            @SerializedName("last_updated")     String lastUpdated,
            @SerializedName("quote")            Map<String, ExchangeQuote> quote
    ) {
        public ExchangeQuote usdQuote() {
            return quote != null ? quote.get("USD") : null;
        }
    }

    public record ExchangeListingsResponse(
            @SerializedName("data")   List<ExchangeListingEntry> data,
            @SerializedName("status") CmcStatus                  status
    ) {}

    public record ExchangeInfoEntry(
            @SerializedName("id")          int    id,
            @SerializedName("name")        String name,
            @SerializedName("slug")        String slug,
            @SerializedName("logo")        String logo,
            @SerializedName("description") String description,
            @SerializedName("date_launched") String dateLaunched,
            @SerializedName("urls")        Object urls
    ) {}

    public record ExchangeInfoResponse(
            @SerializedName("data")   Map<String, ExchangeInfoEntry> data,
            @SerializedName("status") CmcStatus                      status
    ) {}
}
