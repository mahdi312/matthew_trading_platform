package com.mst.matt.tradingplatformapp.service.price;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.service.price.api.ApiErrorDetector;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Centralised JSON HTTP client.
 *
 * <p><b>P3 (LOG-FIX)</b>: every external call passes through a per-host circuit breaker.
 * After {@code api.circuit.failure-threshold} consecutive failures (timeouts, connect
 * resets, DNS failures, 5xx), the host is parked for {@code api.circuit.cooldown-sec}
 * seconds and immediately short-circuits with {@code Optional.empty()}. That stops the
 * UI from freezing for 30+ seconds at a time when every provider is unreachable (which
 * is what the supplied logs showed: Frankfurter / Fixer / Yahoo / Binance / Finnhub all
 * failing in lock-step).
 *
 * <p><b>T-23</b>: per-provider sliding-window throttle is still here.
 */
@Component
public class HttpJsonClient {

    private static final Logger log = LoggerFactory.getLogger(HttpJsonClient.class);
    private final OkHttpClient http;
    private final Gson gson = new Gson();

    /** T-23 sliding-window throttle. */
    private final Map<String, SlidingWindow> throttles = new ConcurrentHashMap<>();

    /** P3 circuit-breaker state, keyed by hostname. */
    private final Map<String, CircuitState> circuits = new ConcurrentHashMap<>();

    private final int failureThreshold;
    private final Duration cooldown;

    @Autowired
    public HttpJsonClient(
            @Qualifier("priceHttpClient") OkHttpClient http,
            @Value("${api.circuit.failure-threshold:3}") int failureThreshold,
            @Value("${api.circuit.cooldown-sec:300}") int cooldownSec) {
        this.http = http;
        this.failureThreshold = Math.max(1, failureThreshold);
        this.cooldown = Duration.ofSeconds(Math.max(1, cooldownSec));
    }

    /**
     * Convenience constructor used by unit tests only — the circuit breaker uses the
     * production defaults (3 failures → 5 min cooldown). Package-private + no Spring
     * annotation so the IoC container always picks the 3-arg constructor above.
     */
    HttpJsonClient(OkHttpClient http) {
        this(http, 3, 300);
    }

    public Optional<JsonObject> getJson(String url) {
        return getJson(url, null, null);
    }

    public Optional<JsonObject> getJson(String url, String userAgent) {
        return getJson(url, userAgent, null);
    }

    /**
     * T-23 throttled overload + P3 circuit-breaker gate.
     */
    public Optional<JsonObject> getJson(String url, String userAgent, String throttleKey) {
        String host = hostOf(url);

        // P3 (LOG-FIX): short-circuit dead providers instead of waiting for timeouts.
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
                // 5xx counts as a failure; 4xx is an application error, not a network problem.
                if (host != null && response.code() >= 500) {
                    recordFailure(host);
                }
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
            // P3 success path: reset the failure counter for this host.
            if (host != null) recordSuccess(host);
            return Optional.of(root);
        } catch (IOException e) {
            log.warn("Request failed for {}: {}", abbreviate(url), e.getMessage());
            if (host != null) recordFailure(host);
            return Optional.empty();
        }
    }

    /**
     * T-23: register a sliding-window rate limit for {@code key}.
     */
    public void throttle(String key, int maxCalls, Duration window) {
        throttles.put(key, new SlidingWindow(maxCalls, window.toMillis()));
    }

    // --- Circuit breaker helpers (P3 LOG-FIX) ----------------------------------

    private boolean isCircuitOpen(String host) {
        CircuitState s = circuits.get(host);
        if (s == null) return false;
        if (s.failures.get() < failureThreshold) return false;
        Instant openedAt = s.openedAt;
        if (openedAt != null && Instant.now().isBefore(openedAt.plus(cooldown))) {
            return true;
        }
        // half-open: let the next call probe the host.
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
            if (s.openedAt != null) {
                log.info("Circuit CLOSED for {} after successful probe", host);
            }
            s.failures.set(0);
            s.openedAt = null;
        }
    }

    private static String hostOf(String url) {
        try {
            return new URI(url).getHost();
        } catch (URISyntaxException e) {
            return null;
        }
    }

    private static String abbreviate(String url) {
        int q = url.indexOf('?');
        return q > 0 ? url.substring(0, Math.min(q, 80)) + "..." : url;
    }

    private static String abbreviateError(JsonObject root) {
        if (root.has("Note")) return root.get("Note").getAsString();
        if (root.has("Information")) return root.get("Information").getAsString();
        if (root.has("message")) return root.get("message").getAsString();
        if (root.has("error") && root.get("error").isJsonObject()) {
            return root.getAsJsonObject("error").toString();
        }
        if (root.has("error") && root.get("error").isJsonPrimitive()) {
            return root.get("error").getAsString();
        }
        return root.toString().substring(0, Math.min(120, root.toString().length()));
    }

    private static final class CircuitState {
        final AtomicInteger failures = new AtomicInteger();
        volatile Instant openedAt;
    }

    /**
     * Tiny sliding-window throttle. Tracks call timestamps in a deque, evicts the ones
     * older than {@code windowMs}, and blocks the calling thread until the next slot
     * becomes free.
     */
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
                    try {
                        wait(Math.min(sleep, windowMs));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
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
