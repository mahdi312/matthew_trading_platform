package com.mst.matt.marketservice.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mst.matt.marketservice.client.api.ApiErrorDetector;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Centralised JSON HTTP client for all external market-data providers.
 *
 * <p>Ported from {@code desktop/.../service/price/HttpJsonClient} with the
 * following adaptations:</p>
 * <ul>
 *   <li>Qualifier changed from {@code priceHttpClient} to
 *       {@code marketProviderHttpClient} (declared in
 *       {@link com.mst.matt.marketservice.config.MarketProviderConfig}).</li>
 *   <li>Circuit-breaker parameters wired from
 *       {@code api.circuit.*} properties (same names as the monolith).</li>
 *   <li>Sliding-window per-provider throttle: call
 *       {@link #throttle(String, int, Duration)} from each provider's
 *       {@code @PostConstruct} method.</li>
 * </ul>
 *
 * <p>The Resilience4j circuit-breakers in
 * {@link com.mst.matt.marketservice.registry.MarketOhlcvProviderRegistry}
 * operate at the provider level; the host-level breaker here handles lower-level
 * network failures (DNS, TCP) that affect all providers sharing a host.</p>
 */
@Component
public class HttpJsonClient {

    private static final Logger log = LoggerFactory.getLogger(HttpJsonClient.class);

    private final OkHttpClient http;
    private final Gson gson = new Gson();

    /** Per-provider sliding-window throttles. */
    private final Map<String, SlidingWindow> throttles = new ConcurrentHashMap<>();

    /** Per-host circuit-breaker state. */
    private final Map<String, CircuitState> circuits = new ConcurrentHashMap<>();

    private final int failureThreshold;
    private final Duration cooldown;

    public HttpJsonClient(
            @Qualifier("marketProviderHttpClient") OkHttpClient http,
            @Value("${api.circuit.failure-threshold:3}") int failureThreshold,
            @Value("${api.circuit.cooldown-sec:300}") int cooldownSec) {
        this.http = http;
        this.failureThreshold = Math.max(1, failureThreshold);
        this.cooldown = Duration.ofSeconds(Math.max(1, cooldownSec));
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public Optional<JsonObject> getJson(String url) {
        return getJson(url, null, null);
    }

    public Optional<JsonObject> getJson(String url, String userAgent) {
        return getJson(url, userAgent, null);
    }

    /**
     * Issues a GET, returns the parsed JSON root or {@link Optional#empty()} on
     * any error (network failure, non-2xx, error payload, circuit open).
     *
     * @param url         full request URL
     * @param userAgent   optional User-Agent override (for Yahoo, CoinGecko)
     * @param throttleKey optional sliding-window throttle key (provider name)
     */
    public Optional<JsonObject> getJson(String url, String userAgent, String throttleKey) {
        String host = hostOf(url);

        if (host != null && isCircuitOpen(host)) {
            log.debug("Circuit open for {} — skipping {}", host, abbreviate(url));
            return Optional.empty();
        }

        if (throttleKey != null) {
            SlidingWindow w = throttles.get(throttleKey);
            if (w != null) w.acquireOrWait();
        }

        Request.Builder builder = new Request.Builder().url(url)
                .addHeader("Accept", "application/json");
        if (userAgent != null) {
            builder.addHeader("User-Agent", userAgent);
        }

        try (Response response = http.newCall(builder.build()).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                log.warn("HTTP {} for {}", response.code(), abbreviate(url));
                if (host != null && response.code() >= 500) recordFailure(host);
                return Optional.empty();
            }
            String body = response.body().string();
            if (body.isBlank() || body.stripLeading().startsWith("<")) {
                log.warn("Non-JSON body for {}", abbreviate(url));
                return Optional.empty();
            }
            JsonObject root = gson.fromJson(body, JsonObject.class);
            if (root == null) return Optional.empty();
            if (ApiErrorDetector.isErrorPayload(root)) {
                log.warn("API error payload for {}: {}", abbreviate(url), abbreviateError(root));
                return Optional.empty();
            }
            if (host != null) recordSuccess(host);
            return Optional.of(root);
        } catch (IOException e) {
            log.warn("Request failed for {}: {}", abbreviate(url), e.getMessage());
            if (host != null) recordFailure(host);
            return Optional.empty();
        }
    }

    /**
     * Registers a sliding-window rate limit for {@code key}.
     *
     * @param key      throttle key, typically the provider name
     * @param maxCalls maximum calls per window
     * @param window   window duration
     */
    public void throttle(String key, int maxCalls, Duration window) {
        throttles.put(key, new SlidingWindow(maxCalls, window.toMillis()));
    }

    // ── Circuit-breaker ───────────────────────────────────────────────────────

    private boolean isCircuitOpen(String host) {
        CircuitState s = circuits.get(host);
        if (s == null) return false;
        if (s.failures.get() < failureThreshold) return false;
        Instant openedAt = s.openedAt;
        if (openedAt != null && Instant.now().isBefore(openedAt.plus(cooldown))) {
            return true;
        }
        // half-open: allow next probe
        s.failures.set(failureThreshold - 1);
        s.openedAt = null;
        return false;
    }

    private void recordFailure(String host) {
        CircuitState s = circuits.computeIfAbsent(host, k -> new CircuitState());
        int now = s.failures.incrementAndGet();
        if (now >= failureThreshold && s.openedAt == null) {
            s.openedAt = Instant.now();
            log.warn("Circuit OPEN for {} after {} consecutive failures — cooling down for {}s",
                    host, now, cooldown.toSeconds());
        }
    }

    private void recordSuccess(String host) {
        CircuitState s = circuits.get(host);
        if (s != null) {
            if (s.openedAt != null) log.info("Circuit CLOSED for {} after successful probe", host);
            s.failures.set(0);
            s.openedAt = null;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String hostOf(String url) {
        try { return new URI(url).getHost(); } catch (URISyntaxException e) { return null; }
    }

    private static String abbreviate(String url) {
        int q = url.indexOf('?');
        return q > 0 ? url.substring(0, Math.min(q, 80)) + "..." : url;
    }

    private static String abbreviateError(JsonObject root) {
        if (root.has("Note")) return root.get("Note").getAsString();
        if (root.has("Information")) return root.get("Information").getAsString();
        if (root.has("message")) return root.get("message").getAsString();
        if (root.has("error") && root.get("error").isJsonObject())
            return root.getAsJsonObject("error").toString();
        if (root.has("error") && root.get("error").isJsonPrimitive())
            return root.get("error").getAsString();
        String s = root.toString();
        return s.substring(0, Math.min(120, s.length()));
    }

    // ── Inner types ───────────────────────────────────────────────────────────

    private static final class CircuitState {
        final AtomicInteger failures = new AtomicInteger();
        volatile Instant openedAt;
    }

    private static final class SlidingWindow {
        private final int maxCalls;
        private final long windowMs;
        private final Deque<Long> timestamps = new ArrayDeque<>();

        SlidingWindow(int maxCalls, long windowMs) {
            this.maxCalls = maxCalls;
            this.windowMs = windowMs;
        }

        synchronized void acquireOrWait() {
            long now = System.currentTimeMillis();
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMs) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxCalls) {
                long earliest = timestamps.peekFirst();
                long sleep = (earliest + windowMs) - now;
                if (sleep > 0) {
                    try { wait(Math.min(sleep, windowMs)); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }
                long t = System.currentTimeMillis();
                while (!timestamps.isEmpty() && t - timestamps.peekFirst() > windowMs) {
                    timestamps.pollFirst();
                }
            }
            timestamps.addLast(System.currentTimeMillis());
        }
    }
}
