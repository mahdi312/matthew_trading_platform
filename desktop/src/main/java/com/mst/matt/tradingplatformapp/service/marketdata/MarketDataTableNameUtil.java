package com.mst.matt.tradingplatformapp.service.marketdata;

import com.mst.matt.tradingplatformapp.service.price.MarketDataProvider;

/**
 * Builds safe PostgreSQL/SQLite table names such as {@code ETHUSDT_BINANCE_1h}.
 */
public final class MarketDataTableNameUtil {

    private MarketDataTableNameUtil() {}

    /**
     * OHLCV table including provider — e.g. {@code ETHUSDT_BINANCE_1h}.
     * The provider segment lets different data sources coexist without collision.
     */
    public static String buildTableName(String symbol, MarketDataProvider provider, String timeframe) {
        String providerSeg = (provider == null || provider == MarketDataProvider.AUTO)
                ? ""
                : "_" + sanitizeSymbol(provider.name());
        return sanitizeSymbol(symbol) + providerSeg + "_" + normalizeTimeframe(timeframe);
    }

    /**
     * OHLCV table — symbol + timeframe only (legacy / fallback, no provider segment).
     * e.g. {@code BTCUSDT_1h}
     */
    public static String buildOhlcvTableName(String symbol, String timeframe) {
        return sanitizeSymbol(symbol) + "_" + normalizeTimeframe(timeframe);
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
