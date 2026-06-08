package com.mst.matt.tradingplatformapp.service.marketdata;

import java.time.Duration;
import java.util.Map;

/**
 * Maps chart timeframes to polling intervals for background API sync.
 */
public final class TimeframeInterval {

    private static final Map<String, Duration> INTERVALS = Map.of(
            "1m", Duration.ofMinutes(1),
            "5m", Duration.ofMinutes(5),
            "15m", Duration.ofMinutes(15),
            "1h", Duration.ofHours(1),
            "4h", Duration.ofHours(4),
            "1d", Duration.ofDays(1),
            "1w", Duration.ofDays(7)
    );

    private TimeframeInterval() {}

    public static Duration forTimeframe(String timeframe) {
        return INTERVALS.getOrDefault(timeframe, Duration.ofHours(1));
    }
}
