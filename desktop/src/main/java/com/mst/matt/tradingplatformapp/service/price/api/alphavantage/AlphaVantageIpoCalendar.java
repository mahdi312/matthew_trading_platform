package com.mst.matt.tradingplatformapp.service.price.api.alphavantage;

import java.util.List;

/**
 * POJO for Alpha Vantage {@code IPO_CALENDAR} response (Fundamental Data).
 * The API returns CSV; this class holds the parsed entries.
 *
 * <p>Fields: symbol, name, ipoDate, priceRangeLow, priceRangeHigh, currency, exchange.
 */
public record AlphaVantageIpoCalendar(
        List<IpoEvent> events
) {

    public record IpoEvent(
            String symbol,
            String name,
            String ipoDate,
            String priceRangeLow,
            String priceRangeHigh,
            String currency,
            String exchange
    ) {}
}
