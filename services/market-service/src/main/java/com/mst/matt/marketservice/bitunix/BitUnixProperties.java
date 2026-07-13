package com.mst.matt.marketservice.bitunix;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * BitUnix API configuration (Step 5.1).
 *
 * <p>Base URLs and rate-limit constants per BitUnix's official futures
 * OpenAPI docs (see {@code docs/MTP_Microservices_Migration_Guide.md},
 * Step 5.1/5.2/5.4):</p>
 * <ul>
 *   <li>REST: {@code https://fapi.bitunix.com}</li>
 *   <li>WebSocket public: {@code wss://fapi.bitunix.com/public/}</li>
 *   <li>WebSocket private: {@code wss://fapi.bitunix.com/private/}</li>
 * </ul>
 *
 * <p>{@link #apiKey} / {@link #secretKey} are only required for BitUnix's
 * <em>private</em> endpoints (order placement, balances, etc.) — Kline and
 * Tickers (Step 5.2) are public and unauthenticated. They are wired here
 * now, unused by {@code BitUnixMarketDataProvider}, purely so
 * {@code trading-service} (Step 6+) can reuse {@link BitUnixSigner}
 * against the same configuration without a second properties class.</p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "bitunix")
public class BitUnixProperties {

    /** REST base URL for all BitUnix futures endpoints. */
    private String restBaseUrl = "https://fapi.bitunix.com";

    /** Public WebSocket feed base URL (no auth required). */
    private String wsPublicUrl = "wss://fapi.bitunix.com/public/";

    /** Private WebSocket feed base URL (requires signed auth) — unused until Step 6+. */
    private String wsPrivateUrl = "wss://fapi.bitunix.com/private/";

    /** API key for private/authenticated endpoints; unused by public market data. */
    private String apiKey;

    /** Secret key used to compute request signatures; unused by public market data. */
    private String secretKey;

    /** BitUnix's documented public REST rate limit: 10 requests/sec/ip. */
    private int restRateLimitPerSecond = 10;

    /** BitUnix's documented WebSocket send rate limit: 5 messages/sec/connection. */
    private int wsRateLimitPerSecond = 5;

    /** BitUnix's documented max channel subscriptions per WebSocket connection. */
    private int wsMaxSubscriptions = 300;

    /** Ping/pong keepalive interval, per BitUnix's "send every ~20-30s" guidance. */
    private int wsPingIntervalSeconds = 25;
}
