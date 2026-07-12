package com.mst.matt.tradingplatformapp.service.price.api.finnhub;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Finnhub {@code GET /index/constituents} — Index Constituents.
 * Free-tier endpoint.
 */
public record FinnhubIndexConstituents(
        @SerializedName("symbol")      String symbol,
        @SerializedName("constituents") List<String> constituents
) {}
