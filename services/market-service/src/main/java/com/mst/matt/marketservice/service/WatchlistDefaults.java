package com.mst.matt.marketservice.service;

import java.util.List;

/**
 * Default watchlist symbols for each asset class.
 * Ported from desktop monolith's {@code WatchlistDefaults}.
 */
public final class WatchlistDefaults {

    private WatchlistDefaults() {}

    public static final List<String> CRYPTO_DEFAULTS = List.of(
            "BTCUSDT","ETHUSDT","BNBUSDT","SOLUSDT","XRPUSDT",
            "ADAUSDT","DOGEUSDT","LTCUSDT","DOTUSDT","LINKUSDT");

    public static final List<String> STOCK_DEFAULTS = List.of(
            "AAPL","MSFT","GOOGL","AMZN","META","TSLA","NVDA","AMD","NFLX","JPM");

    public static final List<String> FOREX_DEFAULTS = List.of(
            "EURUSD","GBPUSD","USDJPY","AUDUSD","USDCAD","USDCHF","NZDUSD","EURJPY","GBPJPY");
}
