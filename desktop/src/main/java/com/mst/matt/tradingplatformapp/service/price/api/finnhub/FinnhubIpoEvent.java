package com.mst.matt.tradingplatformapp.service.price.api.finnhub;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Finnhub {@code GET /calendar/ipo} — IPO Calendar.
 * Free-tier endpoint.
 */
public record FinnhubIpoEvent(
        @SerializedName("ipoCalendar") List<IpoItem> ipoCalendar
) {
    public record IpoItem(
            @SerializedName("date")          String date,
            @SerializedName("exchange")      String exchange,
            @SerializedName("name")          String name,
            @SerializedName("numberOfShares") Long numberOfShares,
            @SerializedName("price")         String price,
            @SerializedName("status")        String status,
            @SerializedName("symbol")        String symbol,
            @SerializedName("totalSharesValue") Double totalSharesValue
    ) {}
}
