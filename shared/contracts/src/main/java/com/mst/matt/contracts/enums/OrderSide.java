package com.mst.matt.contracts.enums;

/**
 * Direction of a trade order.
 *
 * <p>Intentionally kept to the two fundamental directions so that
 * broker implementations can map these to their own terminology
 * (e.g., "Buy"/"Sell", "Long"/"Short", "bid"/"ask") internally.</p>
 */
public enum OrderSide {

    /**
     * Buy / Long — the order acquires the base asset or opens a long position.
     */
    BUY,

    /**
     * Sell / Short — the order disposes of the base asset or opens a short position.
     */
    SELL
}
