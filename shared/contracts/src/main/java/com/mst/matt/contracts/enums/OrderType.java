package com.mst.matt.contracts.enums;

/**
 * Standard order execution types supported by the platform.
 *
 * <p>Not every broker supports every type; each broker's
 * {@code BrokerCapabilities} declares which types it accepts.
 * Controllers must validate against capabilities before forwarding
 * an order to a {@code TradingProvider}.</p>
 */
public enum OrderType {

    /**
     * Market order — execute immediately at the best available price.
     * Universally supported by all brokers.
     */
    MARKET,

    /**
     * Limit order — execute only at the specified price or better.
     */
    LIMIT,

    /**
     * Stop (stop-market) order — becomes a market order once the stop
     * price is reached.
     */
    STOP,

    /**
     * Stop-limit order — becomes a limit order once the stop price is reached.
     */
    STOP_LIMIT,

    /**
     * Trailing-stop order — stop price follows the market by a fixed
     * offset or percentage.
     */
    TRAILING_STOP
}
