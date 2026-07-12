package com.mst.matt.tradingplatformapp.service.price.api.coinmarketcap;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Response models for CMC Index endpoints:
 * {@code GET /v3/index/cmc100-latest},
 * {@code GET /v3/index/cmc100-historical},
 * {@code GET /v3/index/cmc20-latest},
 * {@code GET /v3/index/cmc20-historical}.
 *
 * All are available keyless via {@code /public-api} prefix.
 */
public final class CmcIndex {

    private CmcIndex() {}

    public record IndexConstituent(
            @SerializedName("id")     int    id,
            @SerializedName("symbol") String symbol,
            @SerializedName("weight") Double weight
    ) {}

    public record IndexLatestData(
            @SerializedName("value")           double               value,
            @SerializedName("name")            String               name,
            @SerializedName("timestamp")       String               timestamp,
            @SerializedName("constituents")    List<IndexConstituent> constituents
    ) {}

    public record IndexLatestResponse(
            @SerializedName("data")   IndexLatestData data,
            @SerializedName("status") CmcStatus       status
    ) {}

    public record IndexHistoricalPoint(
            @SerializedName("value")     double value,
            @SerializedName("timestamp") String timestamp
    ) {}

    public record IndexHistoricalData(
            @SerializedName("name")   String                    name,
            @SerializedName("points") List<IndexHistoricalPoint> points
    ) {}

    public record IndexHistoricalResponse(
            @SerializedName("data")   IndexHistoricalData data,
            @SerializedName("status") CmcStatus           status
    ) {}

    public record AltcoinSeasonData(
            @SerializedName("score")             int    score,
            @SerializedName("classification")    String classification,
            @SerializedName("timestamp")         String timestamp
    ) {}

    public record AltcoinSeasonResponse(
            @SerializedName("data")   AltcoinSeasonData data,
            @SerializedName("status") CmcStatus         status
    ) {}
}
