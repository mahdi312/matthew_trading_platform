package com.mst.matt.tradingplatformapp.service.price.api.twelvedata;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Generic POJO for TwelveData technical indicator responses.
 * All indicator endpoints (/rsi, /macd, /ema, /sma, /bbands, etc.)
 * follow the same envelope pattern with {@code meta}, {@code values}, and {@code status}.
 *
 * Individual value fields vary by indicator — they are exposed as raw JSON via the
 * {@code values} list which each entry having string key→value pairs.
 * Use {@link TwelveDataIndicatorValue} for typed single-value indicators (RSI, SMA, EMA, etc.)
 * and {@link TwelveDataMacdValue} for MACD.
 */
public record TwelveDataIndicator(
        @SerializedName("meta")   Meta meta,
        @SerializedName("values") List<TwelveDataIndicatorValue> values,
        @SerializedName("status") String status
) {

    public record Meta(
            @SerializedName("symbol")      String symbol,
            @SerializedName("interval")    String interval,
            @SerializedName("indicator")   IndicatorMeta indicator
    ) {}

    public record IndicatorMeta(
            @SerializedName("name")        String name,
            @SerializedName("series_type") String seriesType,
            @SerializedName("time_period") Integer timePeriod
    ) {}

    public boolean isOk() {
        return "ok".equalsIgnoreCase(status);
    }
}
