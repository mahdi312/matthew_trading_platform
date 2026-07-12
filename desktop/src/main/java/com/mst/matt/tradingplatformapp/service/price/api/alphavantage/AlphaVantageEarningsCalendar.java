package com.mst.matt.tradingplatformapp.service.price.api.alphavantage;

import java.util.List;

/**
 * POJO for Alpha Vantage {@code EARNINGS_CALENDAR} response (Fundamental Data).
 * The API returns CSV; this class holds the parsed entries.
 *
 * <p>Fields: symbol, name, reportDate, fiscalDateEnding, estimate, currency.
 */
public record AlphaVantageEarningsCalendar(
        List<EarningsEvent> events
) {

    public record EarningsEvent(
            String symbol,
            String name,
            String reportDate,
            String fiscalDateEnding,
            String estimate,
            String currency
    ) {}
}
