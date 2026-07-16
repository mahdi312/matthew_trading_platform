package com.mst.matt.tradingservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * BitUnix API configuration for trading-service (Step 6.6).
 *
 * <p>Mirrors the structure of market-service's {@code BitUnixProperties} but
 * adds the private WebSocket URL and exposes both futures and spot base URLs
 * separately, since trading-service uses two distinct BitUnix REST endpoints:</p>
 * <ul>
 *   <li>Futures REST: {@code https://fapi.bitunix.com}</li>
 *   <li>Spot REST: {@code https://openapi.bitunix.com}</li>
 *   <li>Private WS (futures): {@code wss://openapi.bitunix.com:443/ws-api/v1}</li>
 * </ul>
 *
 * <p>{@link #bitunixApiKey} and {@link #bitunixSecretKey} are the <em>default</em>
 * (global/single-user) credentials used by the current stub implementation of
 * {@code BitUnixTradingProvider.resolveCredential()}. In the full multi-user
 * implementation, per-user encrypted credentials will be fetched from
 * {@code identity-service} at order time — these fields act as a fallback or
 * service-level override.</p>
 *
 * <h3>Rate-limit constants</h3>
 * <ul>
 *   <li>{@link #restRateLimitPerSecond} — 10 req/s/ip (BitUnix public docs)</li>
 *   <li>{@link #wsRateLimitPerSecond} — 5 msg/s/connection</li>
 *   <li>{@link #wsMaxSubscriptions} — 300 channels per connection</li>
 *   <li>{@link #wsPingIntervalSeconds} — 25 s (within the 20-30 s window)</li>
 * </ul>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "bitunix")
public class TradingProperties {

    // ── REST base URLs ────────────────────────────────────────────────────────

    /** REST base URL for all BitUnix <em>futures</em> private endpoints. */
    private String futuresBaseUrl = "https://fapi.bitunix.com";

    /** REST base URL for all BitUnix <em>spot</em> private endpoints. */
    private String spotBaseUrl = "https://openapi.bitunix.com";

    // ── WebSocket URLs ────────────────────────────────────────────────────────

    /** Private WebSocket for futures balance/order/position push channels. */
    private String wsPrivateUrl = "wss://openapi.bitunix.com:443/ws-api/v1";

    // ── Credentials (global / single-user stub) ───────────────────────────────

    /**
     * API key for authenticated endpoints.
     *
     * <p><b>Stub use</b>: read directly from config for the current single-user
     * mode. The multi-user implementation will fetch per-user keys from
     * identity-service.</p>
     */
    private String bitunixApiKey;

    /**
     * Secret key used to compute double-SHA256 request signatures.
     *
     * <p><b>Stub use</b>: same caveat as {@link #bitunixApiKey}.</p>
     */
    private String bitunixSecretKey;

    // ── Rate-limit constants ──────────────────────────────────────────────────

    /** BitUnix documented REST rate limit: 10 requests/sec/ip. */
    private int restRateLimitPerSecond = 10;

    /** BitUnix documented WS send rate limit: 5 messages/sec/connection. */
    private int wsRateLimitPerSecond = 5;

    /** BitUnix documented max channel subscriptions per WS connection. */
    private int wsMaxSubscriptions = 300;

    /** Ping/pong keepalive interval — within BitUnix's 20-30 s guidance. */
    private int wsPingIntervalSeconds = 25;

    // ── Order/position constraints ────────────────────────────────────────────

    /** BitUnix futures max leverage (e.g., 125x). Override from config if needed. */
    private int maxLeverage = 125;
}
