package com.mst.matt.tradingplatformapp.service.price.api.twelvedata;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * POJO for TwelveData {@code GET /time_series} response.
 * <pre>
 * {
 *   "meta": { "symbol":"AAPL", "interval":"1day", ... },
 *   "values": [ { "datetime":"2026-06-30","open":"...", ...} ],
 *   "status": "ok"
 * }
 * </pre>
 */
public record TwelveDataTimeSeries(
        @SerializedName("meta")   Meta meta,
        @SerializedName("values") List<Bar> values,
        @SerializedName("status") String status
) {

    public record Meta(
            @SerializedName("symbol")            String symbol,
            @SerializedName("interval")          String interval,
            @SerializedName("currency")          String currency,
            @SerializedName("exchange_timezone") String exchangeTimezone,
            @SerializedName("exchange")          String exchange,
            @SerializedName("type")              String type
    ) {}

    public record Bar(
            @SerializedName("datetime") String datetime,
            @SerializedName("open")     String open,
            @SerializedName("high")     String high,
            @SerializedName("low")      String low,
            @SerializedName("close")    String close,
            @SerializedName("volume")   String volume
    ) {}

    public boolean isOk() {
        return "ok".equalsIgnoreCase(status);
    }
}
