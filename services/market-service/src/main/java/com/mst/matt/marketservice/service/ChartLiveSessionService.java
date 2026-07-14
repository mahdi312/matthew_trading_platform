package com.mst.matt.marketservice.service;

import com.mst.matt.marketservice.model.MarketDataTableRegistry;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks whether a live chart session is active and which symbol/timeframe is
 * currently being viewed.
 *
 * <p>Ported from the desktop monolith's {@code ChartLiveSessionService};
 * adapted for the REST/microservice context:</p>
 * <ul>
 *   <li>JavaFX UI-event hooks removed — activation is now driven by REST
 *       endpoint calls (e.g. {@code GET /api/market/ohlcv/{symbol}?live=true}).</li>
 *   <li>{@link MarketDataSyncScheduler} consults {@link #isActive()} and
 *       {@link #matches(MarketDataTableRegistry)} before syncing, exactly as in
 *       the monolith: syncs are paused when no live session is running.</li>
 * </ul>
 */
@Service
public class ChartLiveSessionService {

    private final AtomicBoolean active = new AtomicBoolean(false);
    private volatile String symbol;
    private volatile String timeframe;
    private volatile LocalDateTime lastLoadedAt;

    /** Marks a live session open for the given symbol + timeframe. */
    public void activate(String symbol, String timeframe) {
        this.symbol = symbol != null ? symbol.trim().toUpperCase() : null;
        this.timeframe = timeframe != null ? timeframe.trim().toLowerCase() : null;
        active.set(true);
    }

    /** Marks the live session as closed. */
    public void deactivate() {
        active.set(false);
    }

    public boolean isActive() {
        return active.get();
    }

    public String getSymbol() { return symbol; }

    public String getTimeframe() { return timeframe; }

    /**
     * Updates the active symbol/timeframe without changing the session's
     * active state. Called when the user changes the chart instrument
     * without navigating away from the chart view.
     */
    public void updateContext(String symbol, String timeframe) {
        if (symbol != null && !symbol.isBlank())
            this.symbol = symbol.trim().toUpperCase();
        if (timeframe != null && !timeframe.isBlank())
            this.timeframe = timeframe.trim().toLowerCase();
    }

    /**
     * Returns {@code true} if the given registry entry matches the active session's
     * symbol + timeframe. Used by the sync scheduler to skip entries that are not
     * currently being viewed.
     */
    public boolean matches(MarketDataTableRegistry entry) {
        if (!active.get() || entry == null || symbol == null || timeframe == null) return false;
        return entry.getSymbol().equalsIgnoreCase(symbol)
                && entry.getTimeframe().equalsIgnoreCase(timeframe);
    }

    /** Records the time when chart data was last fetched. */
    public void recordLoaded() {
        lastLoadedAt = LocalDateTime.now();
    }

    /**
     * Returns {@code true} when the cached chart data should be refreshed
     * from the network (i.e., it is older than one candle period).
     */
    public boolean isCacheStale() {
        if (lastLoadedAt == null || timeframe == null) return true;
        return lastLoadedAt.plus(TimeframeInterval.forTimeframe(timeframe))
                .isBefore(LocalDateTime.now());
    }

    /** Returns the recommended polling interval for the current timeframe. */
    public Duration pollInterval() {
        return TimeframeInterval.refreshPollInterval(timeframe);
    }
}
