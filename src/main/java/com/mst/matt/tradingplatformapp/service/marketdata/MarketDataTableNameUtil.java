package com.mst.matt.tradingplatformapp.service.marketdata;

import com.mst.matt.tradingplatformapp.service.price.MarketDataProvider;

/**
 * Builds safe PostgreSQL/SQLite table names such as {@code ETHUSDT_BINANCE_1h}.
 */
public final class MarketDataTableNameUtil {

    private MarketDataTableNameUtil() {}

    /** OHLCV table — symbol + timeframe only (e.g. {@code BTCUSDT_1H}), not API provider. */
    public static String buildTableName(String symbol, MarketDataProvider provider, String timeframe) {
        return buildOhlcvTableName(symbol, timeframe);
    }

    public static String buildOhlcvTableName(String symbol, String timeframe) {
        return sanitizeSymbol(symbol) + "_" + normalizeTimeframe(timeframe).toUpperCase();
    }

    static String sanitizeSymbol(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        return value.trim()
                .toUpperCase()
                .replaceAll("[^A-Z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    static String normalizeTimeframe(String timeframe) {
        if (timeframe == null || timeframe.isBlank()) {
            return "1h";
        }
        return timeframe.trim().toLowerCase();
    }
}
