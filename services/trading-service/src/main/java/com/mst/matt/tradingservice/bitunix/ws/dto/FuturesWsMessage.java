package com.mst.matt.tradingservice.bitunix.ws.dto;

import lombok.Data;

/**
 * Generic envelope for all BitUnix <em>Futures</em> private WebSocket push messages
 * (Step 6 — Futures WS push model).
 *
 * <h3>Wire format</h3>
 * <p>BitUnix futures private channels push JSON in this shape:</p>
 * <pre>{@code
 * {
 *   "ch":   "order",          // channel name: "balance" | "order" | "position"
 *   "ts":   1712345678901,    // server-side millisecond timestamp
 *   "data": { ... }           // channel-specific payload (generic JsonObject here)
 * }
 * }</pre>
 *
 * <h3>Usage</h3>
 * <p>The WS client ({@link com.mst.matt.tradingservice.bitunix.ws.BitUnixFuturesWsClient})
 * first deserialises incoming frames into this envelope, then dispatches to
 * the appropriate typed deserialiser based on {@link #ch}.</p>
 *
 * <h3>Control frames</h3>
 * <p>Heartbeat pong responses have {@code op = "pong"} at the top level rather
 * than a {@code ch} field.  Login acknowledgements have {@code op = "login"}
 * with {@code code = 0} on success.  The WS client handles those separately
 * before attempting channel dispatch.</p>
 *
 * @param <T> the typed data payload for a specific channel
 */
@Data
public class FuturesWsMessage<T> {

    /**
     * Channel identifier.  One of:
     * <ul>
     *   <li>{@code "balance"}  — account balance updates</li>
     *   <li>{@code "order"}    — order lifecycle events</li>
     *   <li>{@code "position"} — position opens/closes/updates</li>
     * </ul>
     * May also be {@code null} for control frames (login ack, pong).
     */
    private String ch;

    /**
     * Server-side timestamp in Unix milliseconds.
     */
    private Long ts;

    /**
     * Typed payload.  Null for control frames.
     * The WS client casts this to the correct DTO based on {@link #ch}.
     */
    private T data;

    // ── Control-frame fields (login ack, pong) ────────────────────────────────

    /**
     * Operation type for control frames: {@code "login"}, {@code "pong"},
     * {@code "subscribe"}.  Absent ({@code null}) on regular push messages.
     */
    private String op;

    /**
     * Response code for {@code login} / {@code subscribe} ack frames.
     * {@code 0} = success.  {@code null} for regular push messages.
     */
    private Integer code;

    /**
     * Human-readable message accompanying {@link #code}.
     */
    private String msg;
}
