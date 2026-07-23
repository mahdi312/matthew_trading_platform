package com.mst.matt.marketservice.bitunix.support;

import com.mst.matt.contracts.broker.market.UnsupportedIntervalException;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps between {@code BitUnixMarketDataProvider}'s REST-style interval
 * strings (Step 5.2's {@link BitUnixIntervalSupport#SUPPORTED_INTERVALS},
 * e.g. {@code "1h"}) and BitUnix's WebSocket Kline channel suffixes
 * (Step 5.4, e.g. {@code "60min"}), per the migration guide's documented
 * channel format:
 * <pre>{market|mark}_kline_{1min|3min|5min|15min|30min|60min|2h|4h|6h|8h|12h|1day|3day|1week|1month}</pre>
 *
 * <p>The two naming schemes disagree for four intervals (minutes vs "min"
 * suffix, and hour/day/week/month spelled out below 1h) — this class is the
 * single source of truth for translating one to the other so the REST and
 * WebSocket sides of {@code BitUnixMarketDataProvider} never drift apart.</p>
 */
public final class BitUnixWsChannelSupport {

    /** BitUnix WS kline channel prefix for last-traded-price candles (matches REST's default {@code type=LAST_PRICE}). */
    public static final String MARKET_KLINE_PREFIX = "market_kline_";

    /** BitUnix WS ticker channel name (fixed, no symbol/interval suffix variants). */
    public static final String TICKER_CHANNEL = "ticker";

    private static final Map<String, String> REST_TO_WS = buildRestToWs();
    private static final Map<String, String> WS_TO_REST = buildWsToRest();

    private BitUnixWsChannelSupport() {
    }

    private static Map<String, String> buildRestToWs() {
        Map<String, String> m = new HashMap<>();
        m.put("1m", "1min");
        m.put("5m", "5min");
        m.put("15m", "15min");
        m.put("30m", "30min");
        m.put("1h", "60min");
        m.put("2h", "2h");
        m.put("4h", "4h");
        m.put("6h", "6h");
        m.put("8h", "8h");
        m.put("12h", "12h");
        m.put("1d", "1day");
        m.put("3d", "3day");
        m.put("1w", "1week");
        m.put("1M", "1month");
        return Map.copyOf(m);
    }

    private static Map<String, String> buildWsToRest() {
        Map<String, String> m = new HashMap<>();
        REST_TO_WS.forEach((rest, ws) -> m.put(ws, rest));
        return Map.copyOf(m);
    }

    /**
     * Converts a REST-style interval (e.g. {@code "1h"}) to its BitUnix WS
     * channel suffix (e.g. {@code "60min"}).
     *
     * @throws UnsupportedIntervalException if {@code interval} is not one of
     *         {@link BitUnixIntervalSupport#SUPPORTED_INTERVALS}
     */
    public static String toWsSuffix(String interval) {
        String suffix = REST_TO_WS.get(interval);
        if (suffix == null) {
            throw new UnsupportedIntervalException(interval, "BITUNIX");
        }
        return suffix;
    }

    /**
     * Converts a BitUnix WS channel suffix (e.g. {@code "60min"}) back to
     * the REST-style interval string (e.g. {@code "1h"}) used throughout
     * {@code MarketDataProvider} and the STOMP relay's destination topics.
     *
     * @return the REST-style interval, or {@code null} if {@code wsSuffix}
     *         is not a recognised BitUnix WS kline suffix (defensive —
     *         incoming push data should always match a subscribed channel,
     *         but callers must handle unknown values gracefully rather than
     *         throwing on a live data feed)
     */
    public static String toRestInterval(String wsSuffix) {
        return WS_TO_REST.get(wsSuffix);
    }

    /**
     * Builds the full BitUnix WS kline channel name for a REST-style
     * interval, e.g. {@code "1h"} → {@code "market_kline_60min"}.
     */
    public static String klineChannel(String interval) {
        return MARKET_KLINE_PREFIX + toWsSuffix(interval);
    }

    /**
     * Extracts the REST-style interval from a full kline channel name
     * pushed by BitUnix (e.g. {@code "market_kline_60min"} → {@code "1h"}),
     * or {@code null} if {@code channel} is not a recognised market-kline
     * channel.
     */
    public static String intervalFromKlineChannel(String channel) {
        if (channel == null || !channel.startsWith(MARKET_KLINE_PREFIX)) {
            return null;
        }
        return toRestInterval(channel.substring(MARKET_KLINE_PREFIX.length()));
    }
}
