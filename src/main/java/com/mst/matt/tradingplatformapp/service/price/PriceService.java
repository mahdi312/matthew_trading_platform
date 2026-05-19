package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.model.OhlcvBar;

import java.util.List;
import java.util.Optional;

/**
 * Contract that every price data provider must implement.
 * Allows plugging in any source (Binance, Yahoo, CoinGecko, Frankfurter)
 * with a unified interface used across the entire application.
 */
public interface PriceService {

    /**
     * Fetch the latest quote for a symbol.
     */
    Optional<PriceQuote> getQuote(String symbol);

    /**
     * Fetch OHLCV candlestick bars.
     * @param symbol    e.g. "BTCUSDT", "AAPL", "EURUSD"
     * @param timeframe e.g. "1m","5m","15m","30m","1h","4h","1d","1w"
     * @param limit     number of bars (1–1000)
     */
    List<OhlcvBar> getOhlcv(String symbol, String timeframe, int limit);

    /**
     * Returns true if this service supports the given symbol.
     */
    boolean supports(String symbol);

    /**
     * Human-readable name for this provider.
     */
    String getProviderName();

    /** Stable provider id for routing and profile preferences. */
    MarketDataProvider getProviderId();

    /** False when API key is missing — registry skips these providers. */
    default boolean isEnabled() { return true; }
}