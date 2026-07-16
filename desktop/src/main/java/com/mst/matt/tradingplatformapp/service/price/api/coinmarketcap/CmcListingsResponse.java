package com.mst.matt.tradingplatformapp.service.price.api.coinmarketcap;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Response for {@code GET /v3/cryptocurrency/listings/latest} and
 * {@code GET /v1/cryptocurrency/listings/new}.
 */
public record CmcListingsResponse(
        @SerializedName("data")   List<CmcListingEntry> data,
        @SerializedName("status") CmcStatus             status
) {}
