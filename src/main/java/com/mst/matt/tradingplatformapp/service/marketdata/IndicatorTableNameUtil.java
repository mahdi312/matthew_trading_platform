package com.mst.matt.tradingplatformapp.service.marketdata;

/**
 * Symbol/timeframe indicator tables — e.g. {@code BTCUSDT_EMA_1H}, {@code AUDJPY_RSI_1W}.
 * Not tied to API provider names.
 */
public final class IndicatorTableNameUtil {

    public enum SeriesType {
        VOL, EMA, MACD, RSI, BB, ICHIMOKU, STOCH, ATR, CCI, VWAP
    }

    private IndicatorTableNameUtil() {}

    public static String tableName(String symbol, SeriesType type, String timeframe) {
        return MarketDataTableNameUtil.sanitizeSymbol(symbol) + "_"
                + type.name() + "_"
                + MarketDataTableNameUtil.normalizeTimeframe(timeframe).toUpperCase();
    }
}
