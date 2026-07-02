package com.mst.matt.tradingplatformapp.service.price.api.twelvedata;

import com.google.gson.annotations.SerializedName;

/**
 * POJO for TwelveData {@code GET /quote} response.
 * Returns a full real-time quote snapshot including 52-week range.
 */
public record TwelveDataQuote(
        @SerializedName("symbol")           String symbol,
        @SerializedName("name")             String name,
        @SerializedName("exchange")         String exchange,
        @SerializedName("mic_code")         String micCode,
        @SerializedName("currency")         String currency,
        @SerializedName("datetime")         String datetime,
        @SerializedName("timestamp")        Long timestamp,
        @SerializedName("open")             String open,
        @SerializedName("high")             String high,
        @SerializedName("low")              String low,
        @SerializedName("close")            String close,
        @SerializedName("volume")           String volume,
        @SerializedName("previous_close")   String previousClose,
        @SerializedName("change")           String change,
        @SerializedName("percent_change")   String percentChange,
        @SerializedName("is_market_open")   Boolean isMarketOpen,
        @SerializedName("fifty_two_week")   FiftyTwoWeek fiftyTwoWeek,
        @SerializedName("extended_change")          String extendedChange,
        @SerializedName("extended_percent_change")  String extendedPercentChange,
        @SerializedName("extended_price")           String extendedPrice,
        @SerializedName("extended_timestamp")       Long extendedTimestamp,
        @SerializedName("status")           String status
) {

    public record FiftyTwoWeek(
            @SerializedName("low")                  String low,
            @SerializedName("high")                 String high,
            @SerializedName("low_change")           String lowChange,
            @SerializedName("high_change")          String highChange,
            @SerializedName("low_change_percent")   String lowChangePercent,
            @SerializedName("high_change_percent")  String highChangePercent,
            @SerializedName("range")                String range
    ) {}

    public boolean isOk() {
        return "ok".equalsIgnoreCase(status);
    }
}
