package com.mst.matt.tradingplatformapp.service.price.api.twelvedata;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * POJO for TwelveData {@code GET /symbol_search} response.
 *
 * <pre>
 * {
 *   "data": [
 *     {
 *       "symbol": "AAPL",
 *       "instrument_name": "Apple Inc",
 *       "exchange": "NASDAQ",
 *       "country": "United States",
 *       "type": "Common Stock"
 *     }
 *   ]
 * }
 * </pre>
 */
public record TwelveDataSymbolSearch(
        @SerializedName("data") List<Instrument> data
) {

    public record Instrument(
            @SerializedName("symbol")          String symbol,
            @SerializedName("instrument_name") String instrumentName,
            @SerializedName("exchange")        String exchange,
            @SerializedName("mic_code")        String micCode,
            @SerializedName("country")         String country,
            @SerializedName("type")            String type,
            @SerializedName("currency")        String currency
    ) {}
}
