package com.mst.matt.tradingplatformapp.service.price;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CoinGeckoRateLimiter}.
 *
 * Tests:
 * - remainingSlots() returns MAX_CALLS initially (28 available)
 * - acquire() records calls and reduces available slots
 * - recordRetryAfter() blocks subsequent acquire() calls
 * - Thread safety: concurrent acquires do not exceed limit
 */
class CoinGeckoRateLimiterTest {

    private CoinGeckoRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new CoinGeckoRateLimiter();
    }

    // ── Basic slot behaviour ──────────────────────────────────────────────────

    @Test
    void remainingSlots_startsAtMaximum() {
        int slots = rateLimiter.remainingSlots();
        // Max is 28 (MAX_CALLS_PER_WINDOW)
        assertEquals(28, slots, "Fresh limiter should have 28 remaining slots");
    }

    @Test
    void acquire_reducesRemainingSlots() {
        int before = rateLimiter.remainingSlots();
        rateLimiter.acquire();
        int after  = rateLimiter.remainingSlots();

        assertEquals(before - 1, after,
                "acquire() should reduce remaining slots by 1");
    }

    @Test
    void multipleAcquires_reduceSlotsAccordingly() {
        int initial = rateLimiter.remainingSlots();
        int calls   = 5;

        for (int i = 0; i < calls; i++) {
            rateLimiter.acquire();
        }

        int remaining = rateLimiter.remainingSlots();
        assertEquals(initial - calls, remaining,
                "Remaining slots should decrease by the number of acquire() calls");
    }

    @Test
    void remainingSlots_neverGoesNegative() {
        // Exhaust all slots
        for (int i = 0; i < 28; i++) {
            rateLimiter.acquire();
        }

        int remaining = rateLimiter.remainingSlots();
        assertTrue(remaining >= 0, "remainingSlots() should never return a negative value");
    }

    // ── Retry-After ───────────────────────────────────────────────────────────

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void recordRetryAfter_withZeroSeconds_doesNotBlockSubsequentAcquire() {
        // recordRetryAfter(0) sets retryAfterUntilMs to "now" → no real wait
        rateLimiter.recordRetryAfter(0L);

        long start = System.currentTimeMillis();
        rateLimiter.acquire();
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 2000L,
                "acquire() after recordRetryAfter(0) should not block for more than 2 seconds");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void recordRetryAfter_withOneSecond_delaysNextAcquire() throws InterruptedException {
        rateLimiter.recordRetryAfter(1L);  // 1 second delay

        long start = System.currentTimeMillis();
        // Run the acquire in a separate thread so we don't block the test thread
        Thread t = new Thread(() -> rateLimiter.acquire());
        t.start();
        t.join(3000L); // Wait up to 3 seconds for the thread to finish
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed >= 900L,
                "acquire() should have waited at least ~1 second for Retry-After");
    }

    // ── Thread safety ─────────────────────────────────────────────────────────

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void concurrentAcquires_doNotExceedMaxSlots() throws InterruptedException {
        int threadCount = 10;
        AtomicInteger successCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    rateLimiter.acquire();
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(8, TimeUnit.SECONDS),
                "All threads should complete within 8 seconds");
        executor.shutdown();

        assertEquals(threadCount, successCount.get(),
                "All concurrent acquire() calls should eventually succeed");
        assertTrue(rateLimiter.remainingSlots() >= 0,
                "remainingSlots() should be non-negative after concurrent calls");
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void concurrentAcquires_areThreadSafe() throws InterruptedException {
        int threadCount = 5;
        AtomicInteger errorCount = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    rateLimiter.acquire();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(4, TimeUnit.SECONDS),
                "All threads should complete within 4 seconds");
        executor.shutdown();

        assertEquals(0, errorCount.get(),
                "No exceptions should be thrown from concurrent acquire() calls");
    }

    // ── Slot sliding-window behaviour ─────────────────────────────────────────

    @Test
    void remainingSlots_returnsZeroWhenWindowFull() {
        // Fill the window to capacity
        for (int i = 0; i < 28; i++) {
            rateLimiter.acquire();
        }

        assertEquals(0, rateLimiter.remainingSlots(),
                "remainingSlots() should be 0 when window is full");
    }

    @Test
    void recordRetryAfter_withNegativeValue_doesNotThrow() {
        assertDoesNotThrow(() -> rateLimiter.recordRetryAfter(-1L),
                "recordRetryAfter() with negative value should not throw");
    }
}
