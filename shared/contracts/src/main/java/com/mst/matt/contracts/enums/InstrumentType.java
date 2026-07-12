package com.mst.matt.contracts.enums;

/**
 * Broad classification of tradeable instruments supported across the platform.
 *
 * <p>Used in DTOs, routing logic, and capability checks so that services
 * can decide how to handle a given instrument without inspecting raw symbols.</p>
 */
public enum InstrumentType {

    /**
     * Cryptocurrency spot market pair (e.g., BTC/USDT on a centralised exchange).
     */
    CRYPTO_SPOT,

    /**
     * Cryptocurrency perpetual or dated futures contract
     * (e.g., BTCUSDT-PERP on BitUnix).
     */
    CRYPTO_FUTURES,

    /**
     * Traditional equity (stock) listed on a recognised exchange
     * (e.g., AAPL on NASDAQ via Alpaca).
     */
    EQUITY,

    /**
     * Forex / currency pair (e.g., EUR/USD).
     */
    FOREX,

    /**
     * Commodity or precious metal (e.g., XAU/USD, WTI crude).
     */
    COMMODITY,

    /**
     * Exchange-Traded Fund or Index instrument.
     */
    ETF_INDEX
}
