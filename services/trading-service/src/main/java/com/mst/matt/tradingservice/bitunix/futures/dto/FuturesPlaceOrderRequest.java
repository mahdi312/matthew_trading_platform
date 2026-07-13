package com.mst.matt.tradingservice.bitunix.futures.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Wire-format request body for {@code POST /api/v1/futures/trade/place_order}.
 *
 * <p>Field names must match BitUnix's JSON key exactly (camelCase as documented).
 * All numeric fields are strings to preserve precision as BitUnix specifies.</p>
 */
@Data
@Builder
public class FuturesPlaceOrderRequest {

    /** Trading symbol, e.g. {@code "BTCUSDT"}. */
    private String symbol;

    /** Base coin quantity, e.g. {@code "0.5"}. */
    private String qty;

    /** Limit price — required for {@code LIMIT} orders; omit for {@code MARKET}. */
    private String price;

    /** {@code "BUY"} or {@code "SELL"}. */
    private String side;

    /**
     * Required in hedge-mode accounts: {@code "OPEN"} or {@code "CLOSE"}.
     * Omit (null) for one-way mode accounts.
     */
    private String tradeSide;

    /**
     * Required when {@code tradeSide="CLOSE"}: the position id being closed.
     */
    private String positionId;

    /** {@code "LIMIT"} or {@code "MARKET"}. */
    private String orderType;

    /**
     * Time-in-force for limit orders: {@code "GTC"} (default), {@code "IOC"},
     * {@code "FOK"}, or {@code "POST_ONLY"}.
     */
    private String effect;

    /** Optional client-generated idempotency key. */
    private String clientId;

    /** {@code true} to prevent opening a new position (close-only). */
    private Boolean reduceOnly;

    // ── Attached take-profit ──────────────────────────────────────────────────
    private String tpPrice;
    private String tpStopType;
    private String tpOrderType;
    private String tpOrderPrice;

    // ── Attached stop-loss ────────────────────────────────────────────────────
    private String slPrice;
    private String slStopType;
    private String slOrderType;
    private String slOrderPrice;
}
