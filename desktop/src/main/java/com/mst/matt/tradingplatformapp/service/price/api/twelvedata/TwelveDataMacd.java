package com.mst.matt.tradingplatformapp.service.price.api.twelvedata;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Full POJO for TwelveData {@code GET /macd} indicator response.
 */
public record TwelveDataMacd(
        @SerializedName("meta")   TwelveDataIndicator.Meta meta,
        @SerializedName("values") List<TwelveDataMacdValue> values,
        @SerializedName("status") String status
) {
    public boolean isOk() {
        return "ok".equalsIgnoreCase(status);
    }
}
