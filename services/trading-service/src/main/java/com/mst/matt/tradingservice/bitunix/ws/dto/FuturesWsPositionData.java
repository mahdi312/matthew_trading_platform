package com.mst.matt.tradingservice.bitunix.ws.dto;

import lombok.Data;

/**
 * Payload for the BitUnix Futures private WebSocket {@code position} channel
 * (Step 6 — Futures WS push model).
 *
 * <h3>Wire format (example)</h3>
 * <pre>{@code
 * {
 *   "ch":  "position",
 *   "ts":  1712345678901,
 *   "data": {
 *     "event":        "POSITION_OPEN",
 *     "positionId":   "pos_987654321",
 *     "marginMode":   "ISOLATION",
 *     "positionMode": "ONE_WAY",
 *     "side":         "LONG",
 *     "leverage":     "10",
 *     "margin":       "600.00",
 *     "qty":          "0.01",
 *     "realizedPNL":  "0",
 *     "unrealizedPNL":"15.50",
 *     "funding":      "-0.23",
 *     "fee":          "-0.12",
 *     "ctime":        1712345678000
 *   }
 * }
 * }</pre>
 *
 * <h3>Event types</h3>
 * <ul>
 *   <li>{@code POSITION_OPEN}   — new position opened</li>
 *   <li>{@code POSITION_UPDATE} — position size/PnL/margin updated</li>
 *   <li>{@code POSITION_CLOSE}  — position fully closed</li>
 * </ul>
 *
 * <p>All monetary and size fields are serialised as strings.</p>
 */
@Data
public class FuturesWsPositionData {

    /**
     * Position lifecycle event type ({@code "POSITION_OPEN"},
     * {@code "POSITION_UPDATE"}, {@code "POSITION_CLOSE"}).
     */
    private String event;

    /** BitUnix-assigned position identifier. */
    private String positionId;

    /** Margin mode: {@code "ISOLATION"} or {@code "CROSS"}. */
    private String marginMode;

    /** Position mode: {@code "ONE_WAY"} or {@code "HEDGE"}. */
    private String positionMode;

    /** Position direction: {@code "LONG"} or {@code "SHORT"}. */
    private String side;

    /** Leverage applied to this position. */
    private String leverage;

    /** Margin collateral locked for this position (quote asset). */
    private String margin;

    /** Position size in base-asset units. */
    private String qty;

    /** Cumulative realised PnL for this position (quote asset). */
    private String realizedPNL;

    /** Current unrealised PnL (mark-price based, quote asset). */
    private String unrealizedPNL;

    /** Cumulative funding fees paid/received (quote asset, can be negative). */
    private String funding;

    /** Cumulative trading fees charged (quote asset, negative). */
    private String fee;

    /** Position open timestamp, Unix milliseconds. */
    private Long ctime;
}
