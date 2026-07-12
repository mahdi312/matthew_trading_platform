package com.mst.matt.tradingplatformapp.service.price.api.coinmarketcap;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Response for {@code GET /v3/fear-and-greed/latest} and
 * {@code GET /v3/fear-and-greed/historical}.
 * Both endpoints are available keyless via {@code /public-api} prefix.
 */
public final class CmcFearAndGreed {

    private CmcFearAndGreed() {}

    public record FearAndGreedEntry(
            @SerializedName("value")                int    value,
            @SerializedName("value_classification") String valueClassification,
            @SerializedName("timestamp")            String timestamp,
            @SerializedName("time_until_update")    String timeUntilUpdate
    ) {}

    public record FearAndGreedResponse(
            @SerializedName("data")   List<FearAndGreedEntry> data,
            @SerializedName("status") CmcStatus               status
    ) {
        /** Convenience: returns the most recent entry. */
        public FearAndGreedEntry latest() {
            return data != null && !data.isEmpty() ? data.get(0) : null;
        }
    }
}
