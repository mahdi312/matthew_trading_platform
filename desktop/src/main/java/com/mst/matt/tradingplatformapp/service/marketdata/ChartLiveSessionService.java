package com.mst.matt.tradingplatformapp.service.marketdata;

import com.mst.matt.tradingplatformapp.model.MarketDataTableRegistry;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tracks whether the Live Chart view is open and which symbol/timeframe is active.
 * Background sync and aggregation consult this before doing any work.
 */
@Service
public class ChartLiveSessionService {

    private final AtomicBoolean active = new AtomicBoolean(false);
    private volatile String symbol;
    private volatile String timeframe;
    private volatile LocalDateTime lastLoadedAt;

    public void activate(String symbol, String timeframe) {
        this.symbol = symbol != null ? symbol.trim().toUpperCase() : null;
        this.timeframe = timeframe != null ? timeframe.trim().toLowerCase() : null;
        active.set(true);
    }

    public void deactivate() {
        active.set(false);
    }

    public boolean isActive() {
        return active.get();
    }

    public String getSymbol() { return symbol; }

    public String getTimeframe() { return timeframe; }

    public void updateContext(String symbol, String timeframe) {
        if (symbol != null && !symbol.isBlank())
            this.symbol = symbol.trim().toUpperCase();
        if (timeframe != null && !timeframe.isBlank())
            this.timeframe = timeframe.trim().toLowerCase();
    }

    public boolean matches(MarketDataTableRegistry entry) {
        if (!active.get() || entry == null || symbol == null || timeframe == null) return false;
        return entry.getSymbol().equalsIgnoreCase(symbol)
                && entry.getTimeframe().equalsIgnoreCase(timeframe);
    }

    public void recordLoaded() {
        lastLoadedAt = LocalDateTime.now();
    }

    /** True when cached chart data should be refreshed from the network. */
    public boolean isCacheStale() {
        if (lastLoadedAt == null || timeframe == null) return true;
        return lastLoadedAt.plus(TimeframeInterval.forTimeframe(timeframe))
                .isBefore(LocalDateTime.now());
    }

    public Duration pollInterval() {
        return TimeframeInterval.refreshPollInterval(timeframe);
    }
}
