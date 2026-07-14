package com.mst.matt.referencedataservice.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Thin OkHttpClient wrapper for reference-data provider HTTP calls.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>Per-provider sliding-window rate throttle (register via {@link #throttle}).</li>
 *   <li>Returns {@code Optional<JsonObject>} — callers never see raw HTTP.</li>
 *   <li>Silently returns {@code Optional.empty()} on HTTP errors, timeouts, or
 *       when the response is a JSON array (callers can use {@link #getJsonElement}
 *       for array responses).</li>
 * </ul>
 */
@Component
public class RefDataHttpClient {

    private static final Logger log = LoggerFactory.getLogger(RefDataHttpClient.class);

    private final OkHttpClient http;
    private final Gson gson = new Gson();

    /** Per-provider sliding-window throttles: throttleKey → SlidingWindow */
    private final Map<String, SlidingWindow> throttles = new ConcurrentHashMap<>();

    public RefDataHttpClient() {
        this.http = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    // ── Throttle registration ─────────────────────────────────────────────────

    /**
     * Register a sliding-window rate limit for a provider.
     *
     * @param key      provider key (e.g. "alphavantage", "finnhub")
     * @param maxCalls maximum calls allowed in the window
     * @param window   duration of the window
     */
    public void throttle(String key, int maxCalls, Duration window) {
        throttles.put(key, new SlidingWindow(maxCalls, window.toMillis()));
        log.debug("Throttle registered: {} → {}/{}", key, maxCalls, window);
    }

    // ── HTTP GET → JsonObject ─────────────────────────────────────────────────

    /**
     * Perform a GET request and return the response body as a parsed JsonObject.
     * Returns empty if the response is not a JSON object (e.g. array, error).
     *
     * @param url          full URL to GET
     * @param userAgent    optional User-Agent header; {@code null} uses default
     * @param throttleKey  optional throttle bucket key; {@code null} for no throttle
     * @return parsed JSON object or empty
     */
    public Optional<JsonObject> getJson(String url, String userAgent, String throttleKey) {
        return getJsonElement(url, userAgent, throttleKey)
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject);
    }

    /**
     * Perform a GET request and return the raw JsonElement (may be object or array).
     */
    public Optional<JsonElement> getJsonElement(String url, String userAgent, String throttleKey) {
        if (throttleKey != null) {
            SlidingWindow sw = throttles.get(throttleKey);
            if (sw != null && !sw.tryAcquire()) {
                log.warn("Throttle limit reached for '{}', skipping: {}", throttleKey, url);
                return Optional.empty();
            }
        }

        String host = extractHost(url);
        Request.Builder reqBuilder = new Request.Builder().url(url).get();
        if (userAgent != null && !userAgent.isBlank()) {
            reqBuilder.header("User-Agent", userAgent);
        }

        try (Response resp = http.newCall(reqBuilder.build()).execute()) {
            if (!resp.isSuccessful()) {
                log.warn("HTTP {} from {}", resp.code(), host);
                return Optional.empty();
            }
            if (resp.body() == null) return Optional.empty();
            String body = resp.body().string();
            if (body.isBlank()) return Optional.empty();
            JsonElement el = JsonParser.parseString(body);
            return Optional.of(el);
        } catch (IOException e) {
            log.warn("HTTP error for {}: {}", host, e.getMessage());
            return Optional.empty();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String extractHost(String url) {
        try {
            return new URI(url).getHost();
        } catch (URISyntaxException e) {
            return url;
        }
    }

    // ── Sliding-window throttle ────────────────────────────────────────────────

    private static final class SlidingWindow {
        private final int maxCalls;
        private final long windowMs;
        private final Deque<Long> timestamps = new ArrayDeque<>();

        SlidingWindow(int maxCalls, long windowMs) {
            this.maxCalls = maxCalls;
            this.windowMs = windowMs;
        }

        synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            long cutoff = now - windowMs;
            while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxCalls) return false;
            timestamps.addLast(now);
            return true;
        }
    }
}
