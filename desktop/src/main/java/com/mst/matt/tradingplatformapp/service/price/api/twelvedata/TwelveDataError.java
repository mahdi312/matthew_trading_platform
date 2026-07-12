package com.mst.matt.tradingplatformapp.service.price.api.twelvedata;

import com.google.gson.annotations.SerializedName;

/**
 * POJO for TwelveData error envelope.
 *
 * <pre>
 * {
 *   "code": 400,
 *   "message": "Invalid interval provided: 0.99min. ...",
 *   "status": "error"
 * }
 * </pre>
 */
public record TwelveDataError(
        @SerializedName("code")    Integer code,
        @SerializedName("message") String message,
        @SerializedName("status")  String status
) {
    public boolean isError() {
        return "error".equalsIgnoreCase(status);
    }
}
