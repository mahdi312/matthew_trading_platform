package com.mst.matt.marketservice.service;

import com.mst.matt.marketservice.model.MarketDataProvider;

/**
 * Builds safe PostgreSQL table names such as {@code ETHUSDT_BINANCE_1h}.
 * Ported from the desktop monolith's {@code MarketDataTableNameUtil}.
 */
public final class MarketDataTableNameUtil {

    private MarketDataTableNameUtil() {}

    public static String buildTableName(String symbol, MarketDataProvider provider, String timeframe) {
        String providerSeg = (provider == null || provider == MarketDataProvider.AUTO)
                ? "" : "_" + sanitize(provider.name());
        return sanitize(symbol) + providerSeg + "_" + normalize(timeframe);
    }

    public static String buildOhlcvTableName(String symbol, String timeframe) {
        return sanitize(symbol) + "_" + normalize(timeframe);
    }

    static String sanitize(String v) {
        if (v == null || v.isBlank()) return "UNKNOWN";
        return v.trim().toUpperCase().replaceAll("[^A-Z0-9]","_").replaceAll("_+","_").replaceAll("^_|_$","");
    }

    static String normalize(String tf) {
        return (tf == null || tf.isBlank()) ? "1h" : tf.trim().toLowerCase();
    }
}
