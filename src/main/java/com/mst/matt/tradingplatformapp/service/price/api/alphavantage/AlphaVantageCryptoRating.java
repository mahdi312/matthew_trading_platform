package com.mst.matt.tradingplatformapp.service.price.api.alphavantage;

import com.google.gson.annotations.SerializedName;

/**
 * POJO for Alpha Vantage {@code CRYPTO_RATING} response.
 *
 * <pre>
 * {
 *   "Crypto Rating (FCAS)": {
 *     "1. symbol": "BTC",
 *     "2. name": "Bitcoin",
 *     "3. fcas rating": "Superb",
 *     "4. fcas score": "935",
 *     "5. developer score": "981",
 *     "6. market maturity score": "843",
 *     "7. utility score": "800",
 *     "8. last refreshed": "2026-07-02",
 *     "9. timezone": "UTC"
 *   }
 * }
 * </pre>
 */
public record AlphaVantageCryptoRating(
        @SerializedName("1. symbol")                 String symbol,
        @SerializedName("2. name")                   String name,
        @SerializedName("3. fcas rating")            String fcasRating,
        @SerializedName("4. fcas score")             String fcasScore,
        @SerializedName("5. developer score")        String developerScore,
        @SerializedName("6. market maturity score")  String marketMaturityScore,
        @SerializedName("7. utility score")          String utilityScore,
        @SerializedName("8. last refreshed")         String lastRefreshed,
        @SerializedName("9. timezone")               String timezone
) {}
