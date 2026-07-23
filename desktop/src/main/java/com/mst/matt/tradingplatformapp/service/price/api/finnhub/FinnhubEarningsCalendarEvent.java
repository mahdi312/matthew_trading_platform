package com.mst.matt.tradingplatformapp.service.price.api.finnhub;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Finnhub {@code GET /calendar/earnings} — Earnings Calendar.
 * Free-tier endpoint.
 */
public record FinnhubEarningsCalendarEvent(
        @SerializedName("earningsCalendar") List<EarningsEvent> earningsCalendar
) {
    public record EarningsEvent(
            @SerializedName("date")          String date,
            @SerializedName("epsActual")     Double epsActual,
            @SerializedName("epsEstimate")   Double epsEstimate,
            @SerializedName("hour")          String hour,
            @SerializedName("quarter")       Integer quarter,
            @SerializedName("revenueActual") Long revenueActual,
            @SerializedName("revenueEstimate") Long revenueEstimate,
            @SerializedName("symbol")        String symbol,
            @SerializedName("year")          Integer year
    ) {}
}
