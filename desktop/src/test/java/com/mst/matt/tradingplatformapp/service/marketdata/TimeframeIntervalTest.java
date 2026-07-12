package com.mst.matt.tradingplatformapp.service.marketdata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TimeframeInterval}.
 *
 * Verifies that all 15 supported timeframes map to the correct {@link Duration}
 * and that unknown / null inputs fall back to 1 hour.
 */
class TimeframeIntervalTest {

    // ── Known timeframes ──────────────────────────────────────

    @ParameterizedTest(name = "timeframe \"{0}\" → {1} minutes")
    @CsvSource({
            "1m,   1",
            "3m,   3",
            "5m,   5",
            "15m,  15",
            "30m,  30",
    })
    void minuteTimeframes_returnCorrectMinutes(String tf, long expectedMinutes) {
        Duration d = TimeframeInterval.forTimeframe(tf);
        assertEquals(Duration.ofMinutes(expectedMinutes), d,
                tf + " should be " + expectedMinutes + " min");
    }

    @ParameterizedTest(name = "timeframe \"{0}\" → {1} hours")
    @CsvSource({
            "1h,  1",
            "2h,  2",
            "4h,  4",
            "6h,  6",
            "8h,  8",
            "12h, 12",
    })
    void hourTimeframes_returnCorrectHours(String tf, long expectedHours) {
        Duration d = TimeframeInterval.forTimeframe(tf);
        assertEquals(Duration.ofHours(expectedHours), d,
                tf + " should be " + expectedHours + " hr");
    }

    @ParameterizedTest(name = "timeframe \"{0}\" → {1} days")
    @CsvSource({
            "1d,   1",
            "3d,   3",
            "1w,   7",
            "1mo, 30",
    })
    void dayTimeframes_returnCorrectDays(String tf, long expectedDays) {
        Duration d = TimeframeInterval.forTimeframe(tf);
        assertEquals(Duration.ofDays(expectedDays), d,
                tf + " should be " + expectedDays + " days");
    }

    // ── All 15 timeframes exist ───────────────────────────────

    @Test
    void all15TimeframesAreSupported() {
        // Verify that all 15 timeframes are explicitly registered by checking
        // their Duration is the one we expect (not just any fallback match).
        // Each entry: (timeframe, expectedDuration)
        Map<String, Duration> expected = Map.ofEntries(
                Map.entry("1m",  Duration.ofMinutes(1)),
                Map.entry("3m",  Duration.ofMinutes(3)),
                Map.entry("5m",  Duration.ofMinutes(5)),
                Map.entry("15m", Duration.ofMinutes(15)),
                Map.entry("30m", Duration.ofMinutes(30)),
                Map.entry("1h",  Duration.ofHours(1)),
                Map.entry("2h",  Duration.ofHours(2)),
                Map.entry("4h",  Duration.ofHours(4)),
                Map.entry("6h",  Duration.ofHours(6)),
                Map.entry("8h",  Duration.ofHours(8)),
                Map.entry("12h", Duration.ofHours(12)),
                Map.entry("1d",  Duration.ofDays(1)),
                Map.entry("3d",  Duration.ofDays(3)),
                Map.entry("1w",  Duration.ofDays(7)),
                Map.entry("1mo", Duration.ofDays(30))
        );
        assertEquals(15, expected.size(), "Must have exactly 15 timeframes");
        for (var entry : expected.entrySet()) {
            Duration actual = TimeframeInterval.forTimeframe(entry.getKey());
            assertEquals(entry.getValue(), actual,
                    "Timeframe '" + entry.getKey() + "' returned wrong Duration");
        }
    }

    // Edge: 1h IS exactly 1 hour — the expected duration matches the fallback by coincidence
    @Test
    void oneHour_isExactlyOneHour() {
        assertEquals(Duration.ofHours(1), TimeframeInterval.forTimeframe("1h"));
    }

    // ── Fallback / edge cases ─────────────────────────────────

    @Test
    void nullInput_returnsOneHourFallback() {
        assertEquals(Duration.ofHours(1), TimeframeInterval.forTimeframe(null));
    }

    @ParameterizedTest(name = "unknown timeframe \"{0}\" falls back to 1h")
    @ValueSource(strings = {"", "2m", "10m", "1year", "monthly", "bogus"})
    void unknownTimeframe_returnsOneHourFallback(String tf) {
        // Unknown keys should return the default 1-hour Duration
        assertEquals(Duration.ofHours(1), TimeframeInterval.forTimeframe(tf),
                "'" + tf + "' should fall back to 1 hour");
    }

    // ── Case sensitivity ──────────────────────────────────────

    @Test
    void lookupIsCaseInsensitive_uppercase() {
        // TimeframeInterval.forTimeframe calls .toLowerCase() internally
        // Using upper-case variants of the registered keys should still work
        assertEquals(Duration.ofMinutes(15), TimeframeInterval.forTimeframe("15M"),  "15M");
        assertEquals(Duration.ofHours(4),    TimeframeInterval.forTimeframe("4H"),   "4H");
        assertEquals(Duration.ofDays(1),     TimeframeInterval.forTimeframe("1D"),   "1D");
        assertEquals(Duration.ofDays(30),    TimeframeInterval.forTimeframe("1MO"),  "1MO");
        assertEquals(Duration.ofMinutes(30), TimeframeInterval.forTimeframe("30M"),  "30M");
    }

    // ── Ordering / magnitude sanity ───────────────────────────

    @Test
    void longerTimeframesHaveLargerDuration() {
        Duration m1  = TimeframeInterval.forTimeframe("1m");
        Duration m5  = TimeframeInterval.forTimeframe("5m");
        Duration h1  = TimeframeInterval.forTimeframe("1h");
        Duration h4  = TimeframeInterval.forTimeframe("4h");
        Duration d1  = TimeframeInterval.forTimeframe("1d");
        Duration w1  = TimeframeInterval.forTimeframe("1w");
        Duration mo1 = TimeframeInterval.forTimeframe("1mo");

        assertTrue(m1.compareTo(m5)  < 0);
        assertTrue(m5.compareTo(h1)  < 0);
        assertTrue(h1.compareTo(h4)  < 0);
        assertTrue(h4.compareTo(d1)  < 0);
        assertTrue(d1.compareTo(w1)  < 0);
        assertTrue(w1.compareTo(mo1) < 0);
    }
}
