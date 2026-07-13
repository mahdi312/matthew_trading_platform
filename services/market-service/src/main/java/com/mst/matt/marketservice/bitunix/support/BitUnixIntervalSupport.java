package com.mst.matt.marketservice.bitunix.support;

import com.mst.matt.contracts.broker.market.UnsupportedIntervalException;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Interval whitelist + duration lookup for BitUnix's REST Kline endpoint
 * ({@code GET /api/v1/futures/market/kline}).
 *
 * <p>Per the migration guide's Step 5.2 spec, the REST {@code interval}
 * query parameter accepts exactly these values — this is also the set
 * published in {@code market-service}'s {@code BrokerCapabilities} bean
 * for {@code BrokerType.BITUNIX} (Step 5.5).</p>
 *
 * <p>Durations are needed to derive {@link com.mst.matt.contracts.dto.OhlcvBarDto#getCloseTime()}
 * — BitUnix's Kline response only gives a single {@code time} (candle open)
 * timestamp per bar, not an explicit close time.</p>
 */
public final class BitUnixIntervalSupport {

    /** Ordered (smallest-to-largest) set of intervals BitUnix's Kline REST endpoint accepts. */
    public static final Set<String> SUPPORTED_INTERVALS = buildSupportedIntervals();

    private BitUnixIntervalSupport() {
    }

    private static Set<String> buildSupportedIntervals() {
        Set<String> intervals = new LinkedHashSet<>();
        intervals.add("1m");
        intervals.add("5m");
        intervals.add("15m");
        intervals.add("30m");
        intervals.add("1h");
        intervals.add("2h");
        intervals.add("4h");
        intervals.add("6h");
        intervals.add("8h");
        intervals.add("12h");
        intervals.add("1d");
        intervals.add("3d");
        intervals.add("1w");
        intervals.add("1M");
        return intervals;
    }

    /**
     * Validates the interval is one BitUnix's REST Kline endpoint accepts.
     *
     * @throws UnsupportedIntervalException if not in {@link #SUPPORTED_INTERVALS}
     */
    public static void validate(String interval) {
        if (!SUPPORTED_INTERVALS.contains(interval)) {
            throw new UnsupportedIntervalException(interval, "BITUNIX");
        }
    }

    /**
     * Candle duration for a given interval, used to derive a bar's close
     * time from its open time. {@code 1M} (calendar month) is approximated
     * as 30 days since BitUnix's Kline payload gives no explicit end time.
     */
    public static Duration durationOf(String interval) {
        return switch (interval) {
            case "1m"  -> Duration.ofMinutes(1);
            case "5m"  -> Duration.ofMinutes(5);
            case "15m" -> Duration.ofMinutes(15);
            case "30m" -> Duration.ofMinutes(30);
            case "1h"  -> Duration.ofHours(1);
            case "2h"  -> Duration.ofHours(2);
            case "4h"  -> Duration.ofHours(4);
            case "6h"  -> Duration.ofHours(6);
            case "8h"  -> Duration.ofHours(8);
            case "12h" -> Duration.ofHours(12);
            case "1d"  -> Duration.ofDays(1);
            case "3d"  -> Duration.ofDays(3);
            case "1w"  -> Duration.ofDays(7);
            case "1M"  -> Duration.ofDays(30);
            default    -> throw new UnsupportedIntervalException(interval, "BITUNIX");
        };
    }
}
