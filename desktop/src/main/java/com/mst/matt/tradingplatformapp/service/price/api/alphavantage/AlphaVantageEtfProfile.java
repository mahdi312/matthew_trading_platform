package com.mst.matt.tradingplatformapp.service.price.api.alphavantage;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * POJO for Alpha Vantage {@code ETF_PROFILE} response (Fundamental Data).
 *
 * <pre>
 * {
 *   "symbol": "SPY",
 *   "name": "SPDR S&P 500 ETF Trust",
 *   "asset_class": "Equity",
 *   "expense_ratio": "0.0945",
 *   "holdings": [
 *     { "symbol": "AAPL", "description": "Apple Inc", "weight": "7.23" }
 *   ]
 * }
 * </pre>
 */
public record AlphaVantageEtfProfile(
        @SerializedName("symbol")        String symbol,
        @SerializedName("name")          String name,
        @SerializedName("asset_class")   String assetClass,
        @SerializedName("expense_ratio") String expenseRatio,
        @SerializedName("net_assets")    String netAssets,
        @SerializedName("nav")           String nav,
        @SerializedName("inception_date") String inceptionDate,
        @SerializedName("holdings")      List<Holding> holdings
) {

    public record Holding(
            @SerializedName("symbol")      String symbol,
            @SerializedName("description") String description,
            @SerializedName("weight")      String weight
    ) {}
}
