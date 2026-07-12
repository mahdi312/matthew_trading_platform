package com.mst.matt.tradingplatformapp.service.price.api.finnhub;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 * Finnhub {@code GET /forex/rates?base=...} — Real-time FX rates.
 * Free-tier endpoint.
 */
public record FinnhubForexRate(
        @SerializedName("base")  String base,
        @SerializedName("quote") Map<String, Double> quote
) {}
