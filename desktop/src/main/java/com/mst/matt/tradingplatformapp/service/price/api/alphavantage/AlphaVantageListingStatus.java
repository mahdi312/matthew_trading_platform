package com.mst.matt.tradingplatformapp.service.price.api.alphavantage;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * POJO for Alpha Vantage {@code LISTING_STATUS} response (Fundamental Data).
 * The API returns CSV; this class represents a parsed entry.
 *
 * <p>Fields: symbol, name, exchange, assetType, ipoDate, delistingDate, status.
 */
public record AlphaVantageListingStatus(
        List<ListingEntry> entries
) {

    public record ListingEntry(
            String symbol,
            String name,
            String exchange,
            String assetType,
            String ipoDate,
            String delistingDate,
            String status
    ) {}
}
