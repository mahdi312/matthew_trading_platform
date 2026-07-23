package com.mst.matt.tradingservice.bitunix.ws.dto;

import lombok.Data;

/**
 * Payload for the BitUnix Futures private WebSocket {@code order} channel
 * (Step 6 — Futures WS push model).
 *
 * <h3>Wire format (example)</h3>
 * <pre>{@code
 * {
 *   "ch":  "order",
 *   "ts":  1712345678901,
 *   "data": {
 *     "event":        "ORDER_NEW",
 *     "orderId":      "1234567890",
 *     "clientId":     "myClient001",
 *     "symbol":       "BTCUSDT",
 *     "side":         "BUY",
 *     "type":         "LIMIT",
 *     "qty":          "0.01",
 *     "price":        "60000",
 *     "orderStatus":  "OPEN",
 *     "fee":          "0.0001",
 *     "averagePrice": "0",
 *     "dealAmount":   "0",
 *     "leverage":     "10",
 *     "ctime":        1712345678000,
 *     "mtime":        1712345678900
 *   }
 * }
 * }</pre>
 *
 * <h3>Event types</h3>
 * <ul>
 *   <li>{@code ORDER_NEW}      — order accepted by the matching engine</li>
 *   <li>{@code ORDER_PARTIAL}  — partial fill</li>
 *   <li>{@code ORDER_FILLED}   — fully filled</li>
 *   <li>{@code ORDER_CANCELLED}— cancelled (HTTP cancel or self-cancel)</li>
 *   <li>{@code ORDER_REJECTED} — rejected by matching engine</li>
 * </ul>
 *
 * <h3>Numeric fields as strings</h3>
 * <p>BitUnix serialises all numeric fields ({@code qty}, {@code price}, etc.)
 * as JSON strings to preserve precision.  Use {@code new BigDecimal(qty)} for
 * arithmetic.</p>
 */
@Data
public class FuturesWsOrderData {

    /**
     * Order lifecycle event type (e.g., {@code "ORDER_NEW"}, {@code "ORDER_FILLED"},
     * {@code "ORDER_CANCELLED"}).
     */
    private String event;

    /** BitUnix-assigned order identifier. */
    private String orderId;

    /** Client-assigned order identifier (echoed from the place-order request). */
    private String clientId;

    /** Trading symbol (e.g., {@code "BTCUSDT"}). */
    private String symbol;

    /** Order direction: {@code "BUY"} or {@code "SELL"}. */
    private String side;

    /** Order type: {@code "LIMIT"} or {@code "MARKET"}. */
    private String type;

    /** Order quantity (base asset units), serialised as a string. */
    private String qty;

    /** Limit price; {@code "0"} for MARKET orders. */
    private String price;

    /**
     * Current order status string:
     * {@code "OPEN"}, {@code "PARTIALLY_FILLED"}, {@code "FILLED"},
     * {@code "CANCELLED"}, {@code "REJECTED"}.
     */
    private String orderStatus;

    /** Total fee charged so far (quote asset units), as a string. */
    private String fee;

    /** Volume-weighted average fill price; {@code "0"} if not yet filled. */
    private String averagePrice;

    /** Cumulative filled quantity (base asset units), as a string. */
    private String dealAmount;

    /** Leverage applied to this order (futures only). */
    private String leverage;

    /** Order creation timestamp, Unix milliseconds. */
    private Long ctime;

    /** Order last-update timestamp, Unix milliseconds. */
    private Long mtime;
}
