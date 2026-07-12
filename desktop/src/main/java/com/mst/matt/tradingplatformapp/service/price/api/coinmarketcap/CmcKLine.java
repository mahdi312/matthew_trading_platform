package com.mst.matt.tradingplatformapp.service.price.api.coinmarketcap;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Response models for DEX K-line endpoints:
 * {@code GET /v1/k-line/points}  — [price, volume, timestamp]
 * {@code GET /v1/k-line/candles} — [open, high, low, close, volume, timestamp, traders]
 *
 * Both are available keyless via {@code /public-api} prefix.
 * CMC returns data as arrays of arrays; this class provides helpers to parse them.
 */
public final class CmcKLine {

    private CmcKLine() {}

    /**
     * A single K-line candle.
     * Raw format from API: {@code [open, high, low, close, volume, timestamp, traders]}
     */
    public record Candle(
            double open,
            double high,
            double low,
            double close,
            double volume,
            long   timestamp,
            int    traders
    ) {
        /**
         * Parse from a JSON array element (List of JsonElement).
         * Expected order: open, high, low, close, volume, timestamp, traders
         */
        public static Candle fromArray(List<Number> arr) {
            if (arr == null || arr.size() < 6) return null;
            return new Candle(
                    arr.get(0).doubleValue(),
                    arr.get(1).doubleValue(),
                    arr.get(2).doubleValue(),
                    arr.get(3).doubleValue(),
                    arr.get(4).doubleValue(),
                    arr.get(5).longValue(),
                    arr.size() > 6 ? arr.get(6).intValue() : 0
            );
        }
    }

    /**
     * A single price point.
     * Raw format from API: {@code [price, volume, timestamp]}
     */
    public record PricePoint(double price, double volume, long timestamp) {
        public static PricePoint fromArray(List<Number> arr) {
            if (arr == null || arr.size() < 3) return null;
            return new PricePoint(
                    arr.get(0).doubleValue(),
                    arr.get(1).doubleValue(),
                    arr.get(2).longValue()
            );
        }
    }

    /** Wrapper for the /v1/k-line/candles response. Data field contains list-of-arrays. */
    public record KLineCandlesResponse(
            @SerializedName("data")   List<List<Object>> data,
            @SerializedName("status") CmcStatus          status
    ) {}

    /** Wrapper for the /v1/k-line/points response. */
    public record KLinePointsResponse(
            @SerializedName("data")   List<List<Object>> data,
            @SerializedName("status") CmcStatus          status
    ) {}
}
