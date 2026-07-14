package com.mst.matt.marketservice.service;

import java.time.Duration;
import java.util.Map;

/**
 * Maps all supported chart timeframe labels to their candle duration and
 * polling/refresh intervals.
 *
 * <p>Ported verbatim from the desktop monolith's {@code TimeframeInterval};
 * used by {@link ChartLiveSessionService} and {@link MarketDataSyncScheduler}
 * to decide when cached chart data is stale and how frequently to poll.</p>
 */
public final class TimeframeInterval {

    private static final Map<String, Duration> INTERVALS = Map.ofEntries(
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

    private TimeframeInterval() {}

    /** Returns the candle duration for the given timeframe label. Defaults to 1h. */
    public static Duration forTimeframe(String timeframe) {
        if (timeframe == null) return Duration.ofHours(1);
        return INTERVALS.getOrDefault(timeframe.toLowerCase(), Duration.ofHours(1));
    }

    /**
     * Returns the recommended UI/scheduler poll cadence for the given timeframe.
     * Shorter candles = shorter poll interval (15 s to 120 s range).
     */
    public static Duration refreshPollInterval(String timeframe) {
        Duration candle = forTimeframe(timeframe);
        long secs = Math.max(15, Math.min(120, candle.getSeconds() / 4));
        return Duration.ofSeconds(secs);
    }
}
