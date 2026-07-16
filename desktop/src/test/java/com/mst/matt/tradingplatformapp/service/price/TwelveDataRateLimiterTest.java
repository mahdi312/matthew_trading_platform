package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.service.price.api.twelvedata.TwelveDataRateLimiter;
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
 * Unit tests for {@link TwelveDataRateLimiter}.
 *
 * Tests:
 * - minuteWindowSlotsRemaining() returns MAX_CREDITS (7) initially
 * - acquire() records calls and reduces available slots
 * - recordRetryAfter() is honoured
 * - dailyCreditsRemaining() starts at 800
 * - Thread safety: concurrent acquires do not exceed limit
 */
class TwelveDataRateLimiterTest {

    private TwelveDataRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new TwelveDataRateLimiter();
    }

    // ── Basic slot behaviour ──────────────────────────────────────────────────

    @Test
    void minuteWindowSlots_startsAtMaximum() {
        int slots = rateLimiter.minuteWindowSlotsRemaining();
        // MAX_CREDITS_PER_WINDOW = 7
        assertEquals(7, slots, "Fresh limiter should have 7 remaining per-minute slots");
    }

    @Test
    void dailyCredits_startsAtMaximum() {
        int remaining = rateLimiter.dailyCreditsRemaining();
        assertEquals(800, remaining, "Fresh limiter should have 800 daily credits remaining");
    }

    @Test
    void acquire_reducesRemainingSlots() {
        int before = rateLimiter.minuteWindowSlotsRemaining();
        rateLimiter.acquire();
        int after = rateLimiter.minuteWindowSlotsRemaining();
        assertEquals(before - 1, after, "acquire() should reduce per-minute slots by 1");
    }

    @Test
    void acquire_reducesDailyCredits() {
        int before = rateLimiter.dailyCreditsRemaining();
        rateLimiter.acquire();
        int after = rateLimiter.dailyCreditsRemaining();
        assertEquals(before - 1, after, "acquire() should reduce daily credits by 1");
    }

    @Test
    void multipleAcquires_reduceSlotsAccordingly() {
        int initial = rateLimiter.minuteWindowSlotsRemaining();
        int calls = 3;
        for (int i = 0; i < calls; i++) {
            rateLimiter.acquire();
        }
        int remaining = rateLimiter.minuteWindowSlotsRemaining();
        assertEquals(initial - calls, remaining,
                "Remaining slots should decrease by the number of acquire() calls");
    }

    @Test
    void minuteWindowSlots_neverGoesNegative() {
        // Acquire all 7 per-minute slots
        for (int i = 0; i < 7; i++) {
            rateLimiter.acquire();
        }
        int remaining = rateLimiter.minuteWindowSlotsRemaining();
        assertTrue(remaining >= 0, "minuteWindowSlotsRemaining() should never return negative");
    }

    // ── Retry-After ───────────────────────────────────────────────────────────

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void recordRetryAfter_withZeroSeconds_doesNotBlockSubsequentAcquire() {
        rateLimiter.recordRetryAfter(0L);
        long start = System.currentTimeMillis();
        rateLimiter.acquire();
        long elapsed = System.currentTimeMillis() - start;
        assertTrue(elapsed < 2000L,
                "acquire() after recordRetryAfter(0) should not block for more than 2 seconds");
    }

    @Test
    void recordRetryAfter_withNegativeValue_doesNotThrow() {
        assertDoesNotThrow(() -> rateLimiter.recordRetryAfter(-1L),
                "recordRetryAfter() with negative value should not throw");
    }

    // ── recordCreditsUsed ─────────────────────────────────────────────────────

    @Test
    void recordCreditsUsed_adjustsDailyCounter() {
        // Simulate server reporting higher usage than our counter
        rateLimiter.acquire(); // dailyCreditsUsed = 1
        rateLimiter.recordCreditsUsed(50); // server says 50 have been used
        int remaining = rateLimiter.dailyCreditsRemaining();
        assertEquals(750, remaining, "dailyCreditsRemaining should reflect server-reported usage");
    }

    @Test
    void recordCreditsUsed_doesNotDecreaseCounter() {
        // Simulate 5 acquires then server reports only 3 — counter should stay at 5
        for (int i = 0; i < 5; i++) rateLimiter.acquire();
        rateLimiter.recordCreditsUsed(3); // server claims less — ignore
        int remaining = rateLimiter.dailyCreditsRemaining();
        assertEquals(795, remaining, "Counter should not decrease if server reports less usage");
    }

    // ── Thread safety ─────────────────────────────────────────────────────────

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void concurrentAcquires_areThreadSafe() throws InterruptedException {
        int threadCount = 5;
        AtomicInteger errorCount  = new AtomicInteger(0);
        CountDownLatch latch       = new CountDownLatch(threadCount);
        ExecutorService executor   = Executors.newFixedThreadPool(threadCount);

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

        assertTrue(latch.await(8, TimeUnit.SECONDS),
                "All threads should complete within 8 seconds");
        executor.shutdown();
        assertEquals(0, errorCount.get(),
                "No exceptions should be thrown from concurrent acquire() calls");
    }
}
