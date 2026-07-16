package com.mst.matt.tradingplatformapp.service.price.api.twelvedata;

import com.google.gson.annotations.SerializedName;

/**
 * POJO for TwelveData {@code GET /price} response.
 * Returns only the latest traded price — the lightest-weight endpoint.
 *
 * <pre>{"price": "214.88"}</pre>
 */
public record TwelveDataPrice(
        @SerializedName("price") String price
) {}
