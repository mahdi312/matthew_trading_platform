package com.mst.matt.marketservice.controller;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Inbound STOMP message body for {@code /app/market/subscribe} and
 * {@code /app/market/unsubscribe} (Step 5.4) — the frontend chart/ticker
 * components send this to tell {@code MarketStreamController} which
 * BitUnix channel(s) to (un)subscribe on their behalf.
 *
 * <p>{@link #interval} is required for kline subscriptions and ignored for
 * ticker-only subscriptions (when {@code null}, only the ticker channel is
 * (un)subscribed).</p>
 */
@Data
@NoArgsConstructor
public class MarketStreamSubscribeRequest {

    /** Trading symbol, e.g. {@code "BTCUSDT"}. */
    private String symbol;

    /** REST-style OHLCV interval, e.g. {@code "1h"}; {@code null} for ticker-only. */
    private String interval;
}
