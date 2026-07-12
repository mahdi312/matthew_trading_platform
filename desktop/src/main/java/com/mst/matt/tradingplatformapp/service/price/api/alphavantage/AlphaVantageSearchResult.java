package com.mst.matt.tradingplatformapp.service.price.api.alphavantage;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * POJO for Alpha Vantage {@code SYMBOL_SEARCH} response.
 *
 * <pre>
 * {
 *   "bestMatches": [
 *     {
 *       "1. symbol": "IBM",
 *       "2. name": "International Business Machines Corporation",
 *       "3. type": "Equity",
 *       "4. region": "United States",
 *       "5. marketOpen": "09:30",
 *       "6. marketClose": "16:00",
 *       "7. timezone": "UTC-04",
 *       "8. currency": "USD",
 *       "9. matchScore": "1.0000"
 *     }
 *   ]
 * }
 * </pre>
 */
public record AlphaVantageSearchResult(
        @SerializedName("bestMatches") List<Match> bestMatches
) {

    public record Match(
            @SerializedName("1. symbol")      String symbol,
            @SerializedName("2. name")        String name,
            @SerializedName("3. type")        String type,
            @SerializedName("4. region")      String region,
            @SerializedName("5. marketOpen")  String marketOpen,
            @SerializedName("6. marketClose") String marketClose,
            @SerializedName("7. timezone")    String timezone,
            @SerializedName("8. currency")    String currency,
            @SerializedName("9. matchScore")  String matchScore
    ) {}
}
