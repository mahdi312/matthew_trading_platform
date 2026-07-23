package com.mst.matt.tradingplatformapp.service.price.api.coinmarketcap;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;

/**
 * Response models for OHLCV endpoints:
 * {@code GET /v2/cryptocurrency/ohlcv/latest} and
 * {@code GET /v2/cryptocurrency/ohlcv/historical}.
 */
public final class CmcOhlcv {

    private CmcOhlcv() {}

    /** A single OHLCV candle with quote data. */
    public record OhlcvQuote(
            @SerializedName("open")       double open,
            @SerializedName("high")       double high,
            @SerializedName("low")        double low,
            @SerializedName("close")      double close,
            @SerializedName("volume")     double volume,
            @SerializedName("market_cap") double marketCap,
            @SerializedName("timestamp")  String timestamp
    ) {}

    /** Entry per time_open / time_close in historical OHLCV. */
    public record OhlcvHistoricalEntry(
            @SerializedName("time_open")  String               timeOpen,
            @SerializedName("time_close") String               timeClose,
            @SerializedName("time_high")  String               timeHigh,
            @SerializedName("time_low")   String               timeLow,
            @SerializedName("quote")      Map<String, OhlcvQuote> quote
    ) {
        public OhlcvQuote usdQuote() {
            return quote != null ? quote.get("USD") : null;
        }
    }

    /** Per-symbol data in historical OHLCV response. */
    public record OhlcvHistoricalData(
            @SerializedName("id")     int    id,
            @SerializedName("name")   String name,
            @SerializedName("symbol") String symbol,
            @SerializedName("quotes") List<OhlcvHistoricalEntry> quotes
    ) {}

    /** Response wrapper for historical OHLCV. */
    public record OhlcvHistoricalResponse(
            @SerializedName("data")   OhlcvHistoricalData data,
            @SerializedName("status") CmcStatus           status
    ) {}

    /** Latest OHLCV quote entry. */
    public record OhlcvLatestEntry(
            @SerializedName("id")     int    id,
            @SerializedName("name")   String name,
            @SerializedName("symbol") String symbol,
            @SerializedName("quote")  Map<String, OhlcvQuote> quote
    ) {
        public OhlcvQuote usdQuote() {
            return quote != null ? quote.get("USD") : null;
        }
    }
}
