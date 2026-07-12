package com.mst.matt.tradingplatformapp.service.price.api.twelvedata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Token-bucket / sliding-window rate limiter for the TwelveData Free (Basic) plan.
 *
 * <h3>Free-tier limits:</h3>
 * <ul>
 *   <li>8 API credits per minute</li>
 *   <li>800 credits per day</li>
 * </ul>
 *
 * <p>This limiter uses a 60-second sliding window allowing at most
 * {@link #MAX_CREDITS_PER_WINDOW} requests within that window.
 * It also tracks daily usage and honours {@code Retry-After} headers from 429 responses.
 *
 * <p>Thread-safe — all public methods are guarded by a {@link ReentrantLock}.
 *
 * <h3>Usage:</h3>
 * <pre>{@code
 *   rateLimiter.acquire();                   // blocks until a slot is available
 *   rateLimiter.recordRetryAfter(seconds);   // honour Retry-After from 429
 *   rateLimiter.recordCreditsUsed(n);        // track header: api-credits-used
 *   int remaining = rateLimiter.dailyCreditsRemaining();
 * }</pre>
 */
@Component
public class TwelveDataRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(TwelveDataRateLimiter.class);

    /** Free-tier: 8 credits per minute (leave 1 headroom below the limit). */
    private static final int  MAX_CREDITS_PER_WINDOW = 7;
    /** Free-tier: 800 credits per day. */
    private static final int  MAX_CREDITS_PER_DAY    = 800;
    private static final long WINDOW_MS              = 60_000L;
    /** Minimum inter-call gap to avoid micro-bursts. */
    private static final long MIN_DELAY_MS           = 200L;

    private final Deque<Long> callTimestamps = new ArrayDeque<>();
    private final ReentrantLock lock         = new ReentrantLock(true);

    /** Epoch-ms until which all requests are blocked due to a 429 Retry-After. */
    private volatile long retryAfterUntilMs = 0L;

    /** Rolling daily credit counter (reset when a new UTC day starts). */
    private volatile int  dailyCreditsUsed  = 0;
    private volatile long dailyResetEpochMs = todayMidnightMs();

    // ─── Public API ────────────────────────────────────────────────────────────

    /**
     * Acquires one rate-limit slot, blocking the calling thread as needed.
     * Must be called before every TwelveData HTTP request.
     *
     * @throws IllegalStateException if the daily credit quota is exhausted
     */
    public void acquire() {
        lock.lock();
        try {
            // 1. Reset daily counter at midnight UTC
            mayberesetDailyCounter();

            // 2. Check daily cap
            if (dailyCreditsUsed >= MAX_CREDITS_PER_DAY) {
                long msUntilReset = dailyResetEpochMs - System.currentTimeMillis();
                log.warn("TwelveData daily credit quota ({}) exhausted. Next reset in {}s",
                        MAX_CREDITS_PER_DAY, msUntilReset / 1_000);
                throw new IllegalStateException(
                        "TwelveData daily credit quota exhausted (" + MAX_CREDITS_PER_DAY + " credits)");
            }

            // 3. Honour Retry-After if set
            long now  = System.currentTimeMillis();
            long wait = retryAfterUntilMs - now;
            if (wait > 0) {
                log.info("TwelveData: honouring Retry-After, sleeping {}ms", wait);
                sleepUninterruptibly(wait);
            }

            // 4. Sliding-window: evict expired timestamps
            now = System.currentTimeMillis();
            while (!callTimestamps.isEmpty() && now - callTimestamps.peekFirst() > WINDOW_MS) {
                callTimestamps.pollFirst();
            }

            // 5. If window is full, sleep until the oldest slot expires
            if (callTimestamps.size() >= MAX_CREDITS_PER_WINDOW) {
                long oldest = callTimestamps.peekFirst();
                long sleepMs = WINDOW_MS - (now - oldest) + 50L;  // +50ms buffer
                log.info("TwelveData rate-limit window full ({}/{}), sleeping {}ms",
                        callTimestamps.size(), MAX_CREDITS_PER_WINDOW, sleepMs);
                sleepUninterruptibly(sleepMs);
                now = System.currentTimeMillis();
                while (!callTimestamps.isEmpty() && now - callTimestamps.peekFirst() > WINDOW_MS) {
                    callTimestamps.pollFirst();
                }
            }

            // 6. Enforce minimum inter-call gap
            if (!callTimestamps.isEmpty()) {
                long lastCall = callTimestamps.peekLast();
                long gap = System.currentTimeMillis() - lastCall;
                if (gap < MIN_DELAY_MS) {
                    sleepUninterruptibly(MIN_DELAY_MS - gap);
                }
            }

            callTimestamps.addLast(System.currentTimeMillis());
            dailyCreditsUsed++;

        } finally {
            lock.unlock();
        }
    }

    /**
     * Records a {@code Retry-After} delay in seconds received from a 429 response.
     * All subsequent {@link #acquire()} calls will block until the delay expires.
     *
     * @param retryAfterSeconds delay in seconds (from {@code Retry-After} header)
     */
    public void recordRetryAfter(long retryAfterSeconds) {
        long until = System.currentTimeMillis() + retryAfterSeconds * 1_000L;
        retryAfterUntilMs = until;
        log.warn("TwelveData 429 — honouring Retry-After: {}s", retryAfterSeconds);
    }

    /**
     * Parses the {@code api-credits-used} response header and adjusts the daily counter.
     * Call this after each successful response to keep the counter accurate.
     *
     * @param creditsUsed value from {@code api-credits-used} header
     */
    public void recordCreditsUsed(int creditsUsed) {
        lock.lock();
        try {
            mayberesetDailyCounter();
            // Use header value as an absolute watermark if larger than our counter
            if (creditsUsed > dailyCreditsUsed) {
                dailyCreditsUsed = creditsUsed;
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns approximate remaining daily credits (informational only).
     */
    public int dailyCreditsRemaining() {
        lock.lock();
        try {
            mayberesetDailyCounter();
            return Math.max(0, MAX_CREDITS_PER_DAY - dailyCreditsUsed);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns available slots in the current per-minute window (informational).
     */
    public int minuteWindowSlotsRemaining() {
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            long active = callTimestamps.stream()
                    .filter(t -> now - t <= WINDOW_MS)
                    .count();
            return (int) Math.max(0, MAX_CREDITS_PER_WINDOW - active);
        } finally {
            lock.unlock();
        }
    }

    // ─── Internals ─────────────────────────────────────────────────────────────

    private void mayberesetDailyCounter() {
        long now = System.currentTimeMillis();
        if (now >= dailyResetEpochMs) {
            log.info("TwelveData daily credit counter reset (was {})", dailyCreditsUsed);
            dailyCreditsUsed  = 0;
            dailyResetEpochMs = todayMidnightMs() + 86_400_000L; // next midnight
        }
    }

    private static long todayMidnightMs() {
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        return today.plusDays(1)
                .atStartOfDay(java.time.ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();
    }

    private static void sleepUninterruptibly(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
