package com.mst.matt.tradingplatformapp.service.price.api.twelvedata;

import com.google.gson.annotations.SerializedName;

/**
 * POJO for TwelveData {@code GET /logo} response.
 * Returns a logo URL for a company, cryptocurrency, or forex pair.
 */
public record TwelveDataLogo(
        @SerializedName("url") String url
) {}
