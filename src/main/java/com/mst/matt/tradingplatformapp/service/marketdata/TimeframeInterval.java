package com.mst.matt.tradingplatformapp.service.marketdata;

import java.time.Duration;
import java.util.Map;

/**
 * Maps ALL supported chart timeframes to polling intervals for background API sync.
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

    public static Duration forTimeframe(String timeframe) {
        if (timeframe == null) return Duration.ofHours(1);
        return INTERVALS.getOrDefault(timeframe.toLowerCase(), Duration.ofHours(1));
    }

    /** UI / scheduler poll cadence — shorter TFs checked more often (15s–120s). */
    public static Duration refreshPollInterval(String timeframe) {
        Duration candle = forTimeframe(timeframe);
        long secs = Math.max(15, Math.min(120, candle.getSeconds() / 4));
        return Duration.ofSeconds(secs);
    }
}
