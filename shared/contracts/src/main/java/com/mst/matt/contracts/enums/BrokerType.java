package com.mst.matt.contracts.enums;

/**
 * Enumeration of all supported (or planned) broker integrations.
 *
 * <p>Each service that interacts with an external broker references this enum
 * to avoid string literals and remain open for new integrations without
 * touching every call-site.</p>
 *
 * <p>Value meaning:</p>
 * <ul>
 *   <li>{@code BITUNIX} – BitUnix crypto spot/futures exchange (first live integration).</li>
 *   <li>{@code BINANCE} – Binance global spot/futures exchange.</li>
 *   <li>{@code COINBASE} – Coinbase Advanced Trade.</li>
 *   <li>{@code KRAKEN}  – Kraken spot/futures exchange.</li>
 *   <li>{@code ALPACA}  – Alpaca Markets (US equities).</li>
 *   <li>{@code PAPER}   – Internal paper-trading simulator — no real API.</li>
 * </ul>
 */
public enum BrokerType {

    /** BitUnix crypto exchange — Phase 1 live implementation target. */
    BITUNIX,

    /** Binance global crypto exchange. */
    BINANCE,

    /** Coinbase Advanced Trade API. */
    COINBASE,

    /** Kraken spot and futures. */
    KRAKEN,

    /** Alpaca Markets — US equities and crypto. */
    ALPACA,

    /** Internal paper-trading simulator (no real external API calls). */
    PAPER
}
