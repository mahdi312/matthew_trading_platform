package com.mst.matt.marketservice.bitunix.ws;

import com.google.gson.JsonObject;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic envelope for every message BitUnix pushes over its public
 * WebSocket feed (Step 5.4) — kline pushes, ticker pushes, and the
 * ping/pong keepalive frame all share this shape at the top level:
 * {@code {"ch": "...", "symbol": "...", "ts": ..., "data": {...}}} for
 * channel pushes, or {@code {"op":"ping", ...}} for keepalive.
 *
 * <p>{@code data} is kept as a raw {@link JsonObject} rather than a typed
 * DTO because its shape differs per channel ({@code market_kline_*} vs
 * {@code ticker}) — {@link BitUnixWebSocketClient} inspects {@link #ch}
 * first, then parses {@code data} into the matching typed payload
 * ({@link BitUnixWsKlineData} or {@link BitUnixWsTickerData}).</p>
 */
@Data
@NoArgsConstructor
public class BitUnixWsPushMessage {

    /** Present on channel push messages (e.g. {@code "market_kline_1min"}, {@code "ticker"}). Null for ping/pong frames. */
    private String ch;

    /** Present on channel push messages: the symbol this push is for (e.g. {@code "BTCUSDT"}). */
    private String symbol;

    /** Server-side push timestamp, unix milliseconds. */
    private long ts;

    /** Channel-specific payload — see {@link BitUnixWsKlineData} / {@link BitUnixWsTickerData}. */
    private JsonObject data;

    /** Present on ping/pong frames only: {@code "ping"}. Null on channel pushes. */
    private String op;

    /** Present on our outgoing ping frames: unix ms we sent the ping at. */
    private Long ping;

    /** Present on BitUnix's pong replies: unix ms BitUnix received our ping. */
    private Long pong;
}
