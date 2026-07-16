package com.mst.matt.tradingplatformapp.service.price.api.alphavantage;

import com.google.gson.annotations.SerializedName;

/**
 * POJO for Alpha Vantage {@code OVERVIEW} response (Fundamental Data).
 *
 * <pre>
 * {
 *   "Symbol": "IBM",
 *   "Name": "International Business Machines Corporation",
 *   "Sector": "Technology",
 *   "Industry": "Information Technology Services",
 *   "MarketCapitalization": "145678901234",
 *   "PERatio": "22.5",
 *   "DividendYield": "3.2",
 *   "EPS": "6.45",
 *   "Description": "..."
 * }
 * </pre>
 */
public record AlphaVantageCompanyOverview(
        @SerializedName("Symbol")                  String symbol,
        @SerializedName("AssetType")               String assetType,
        @SerializedName("Name")                    String name,
        @SerializedName("Description")             String description,
        @SerializedName("CIK")                     String cik,
        @SerializedName("Exchange")                String exchange,
        @SerializedName("Currency")                String currency,
        @SerializedName("Country")                 String country,
        @SerializedName("Sector")                  String sector,
        @SerializedName("Industry")                String industry,
        @SerializedName("Address")                 String address,
        @SerializedName("OfficialSite")            String officialSite,
        @SerializedName("FiscalYearEnd")           String fiscalYearEnd,
        @SerializedName("LatestQuarter")           String latestQuarter,
        @SerializedName("MarketCapitalization")    String marketCapitalization,
        @SerializedName("EBITDA")                  String ebitda,
        @SerializedName("PERatio")                 String peRatio,
        @SerializedName("PEGRatio")                String pegRatio,
        @SerializedName("BookValue")               String bookValue,
        @SerializedName("DividendPerShare")        String dividendPerShare,
        @SerializedName("DividendYield")           String dividendYield,
        @SerializedName("EPS")                     String eps,
        @SerializedName("RevenuePerShareTTM")      String revenuePerShareTTM,
        @SerializedName("ProfitMargin")            String profitMargin,
        @SerializedName("OperatingMarginTTM")      String operatingMarginTTM,
        @SerializedName("ReturnOnAssetsTTM")       String returnOnAssetsTTM,
        @SerializedName("ReturnOnEquityTTM")       String returnOnEquityTTM,
        @SerializedName("RevenueTTM")              String revenueTTM,
        @SerializedName("GrossProfitTTM")          String grossProfitTTM,
        @SerializedName("DilutedEPSTTM")           String dilutedEPSTTM,
        @SerializedName("QuarterlyEarningsGrowthYOY") String quarterlyEarningsGrowthYOY,
        @SerializedName("QuarterlyRevenueGrowthYOY")  String quarterlyRevenueGrowthYOY,
        @SerializedName("AnalystTargetPrice")      String analystTargetPrice,
        @SerializedName("AnalystRatingStrongBuy")  String analystRatingStrongBuy,
        @SerializedName("AnalystRatingBuy")        String analystRatingBuy,
        @SerializedName("AnalystRatingHold")       String analystRatingHold,
        @SerializedName("AnalystRatingSell")       String analystRatingSell,
        @SerializedName("AnalystRatingStrongSell") String analystRatingStrongSell,
        @SerializedName("TrailingPE")              String trailingPE,
        @SerializedName("ForwardPE")               String forwardPE,
        @SerializedName("PriceToSalesRatioTTM")   String priceToSalesRatioTTM,
        @SerializedName("PriceToBookRatio")        String priceToBookRatio,
        @SerializedName("EVToRevenue")             String evToRevenue,
        @SerializedName("EVToEBITDA")              String evToEBITDA,
        @SerializedName("Beta")                    String beta,
        @SerializedName("52WeekHigh")              String weekHigh52,
        @SerializedName("52WeekLow")               String weekLow52,
        @SerializedName("50DayMovingAverage")      String movingAverage50day,
        @SerializedName("200DayMovingAverage")     String movingAverage200day,
        @SerializedName("SharesOutstanding")       String sharesOutstanding,
        @SerializedName("DividendDate")            String dividendDate,
        @SerializedName("ExDividendDate")          String exDividendDate
) {}
