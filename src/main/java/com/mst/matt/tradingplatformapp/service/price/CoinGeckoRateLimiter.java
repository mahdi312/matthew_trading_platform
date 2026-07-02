package com.mst.matt.tradingplatformapp.service.price;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Token-bucket rate limiter for CoinGecko Free / Demo plan.
 *
 * Free tier allows ~30 calls/minute.
 * This limiter uses a sliding window of 60 seconds and permits at most
 * {@code MAX_CALLS_PER_WINDOW} requests within that window.
 *
 * Thread-safe — all public methods are guarded by a ReentrantLock.
 *
 * Usage:
 * <pre>{@code
 *   rateLimiter.acquire();      // blocks until a slot is available
 *   rateLimiter.recordRetryAfter(seconds);  // honour Retry-After header
 * }</pre>
 */
@Component
public class CoinGeckoRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(CoinGeckoRateLimiter.class);

    /** Maximum calls allowed within WINDOW_MS milliseconds. */
    private static final int    MAX_CALLS_PER_WINDOW = 28;   // leave 2-call headroom below 30
    private static final long   WINDOW_MS            = 60_000L;
    private static final long   MIN_DELAY_MS         = 100L;  // minimum inter-call gap

    private final Deque<Long>    callTimestamps = new ArrayDeque<>();
    private final ReentrantLock  lock           = new ReentrantLock(true);

    /** Epoch-ms until which requests are blocked due to Retry-After. */
    private volatile long retryAfterUntilMs = 0L;

    /**
     * Acquires a rate-limit slot, blocking the calling thread if the
     * window is full.  Must be called before every CoinGecko HTTP request.
     */
    public void acquire() {
        lock.lock();
        try {
            // 1. Honour Retry-After if set
            long now = System.currentTimeMillis();
            long retryDelay = retryAfterUntilMs - now;
            if (retryDelay > 0) {
                log.info("CoinGecko rate limiter: honouring Retry-After, sleeping {}ms", retryDelay);
                sleepUninterruptibly(retryDelay);
            }

            // 2. Sliding-window: drop timestamps older than WINDOW_MS
            now = System.currentTimeMillis();
            while (!callTimestamps.isEmpty() && now - callTimestamps.peekFirst() > WINDOW_MS) {
                callTimestamps.pollFirst();
            }

            // 3. If window is full, sleep until oldest slot expires
            if (callTimestamps.size() >= MAX_CALLS_PER_WINDOW) {
                long oldest = callTimestamps.peekFirst();
                long waitMs = WINDOW_MS - (now - oldest) + 50L;  // +50ms buffer
                log.info("CoinGecko rate limit window full ({}/{}), sleeping {}ms",
                        callTimestamps.size(), MAX_CALLS_PER_WINDOW, waitMs);
                sleepUninterruptibly(waitMs);
                // Re-purge expired entries
                now = System.currentTimeMillis();
                while (!callTimestamps.isEmpty() && now - callTimestamps.peekFirst() > WINDOW_MS)
                    callTimestamps.pollFirst();
            }

            // 4. Enforce minimum inter-call gap to avoid micro-burst
            if (!callTimestamps.isEmpty()) {
                long lastCall = callTimestamps.peekLast();
                long gap = System.currentTimeMillis() - lastCall;
                if (gap < MIN_DELAY_MS) {
                    sleepUninterruptibly(MIN_DELAY_MS - gap);
                }
            }

            callTimestamps.addLast(System.currentTimeMillis());
        } finally {
            lock.unlock();
        }
    }

    /**
     * Records a Retry-After delay (in seconds) received from a 429 response.
     * All subsequent {@link #acquire()} calls will block until the delay expires.
     *
     * @param retryAfterSeconds seconds to wait (from Retry-After header)
     */
    public void recordRetryAfter(long retryAfterSeconds) {
        long until = System.currentTimeMillis() + retryAfterSeconds * 1_000L;
        retryAfterUntilMs = until;
        log.warn("CoinGecko 429 received — honouring Retry-After: {}s", retryAfterSeconds);
    }

    /** Remaining call slots available in the current window (informational). */
    public int remainingSlots() {
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            long count = callTimestamps.stream()
                    .filter(t -> now - t <= WINDOW_MS)
                    .count();
            return (int) Math.max(0, MAX_CALLS_PER_WINDOW - count);
        } finally {
            lock.unlock();
        }
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
