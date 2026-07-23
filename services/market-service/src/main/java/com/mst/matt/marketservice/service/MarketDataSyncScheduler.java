package com.mst.matt.marketservice.service;

import com.mst.matt.marketservice.model.MarketDataTableRegistry;
import com.mst.matt.marketservice.repository.MarketDataTableRegistryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled trigger for market-data sync.
 *
 * <p>Ported from the desktop monolith's {@code MarketDataSyncScheduler};
 * adapted for the microservice context:</p>
 * <ul>
 *   <li>{@code AppSettingsService} removed — the enable/disable toggle is
 *       now the {@code app.market-data.sync.enabled} property.</li>
 *   <li>{@code UserProfile} removed — no per-user provider preference here;
 *       registry entries carry their own provider info.</li>
 *   <li>The scheduler only runs when a {@link ChartLiveSessionService} session
 *       is active (same pattern as the monolith: sync is paused when the chart
 *       view is not open).</li>
 * </ul>
 *
 * <p>The scheduler is disabled by default ({@code app.market-data.sync.enabled=false})
 * because market-service does not yet have live external-provider wiring (Phase 3);
 * enable via the property once Phase 3 beans are registered.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.market-data.sync.enabled",
        havingValue = "true",
        matchIfMissing = false)
public class MarketDataSyncScheduler {

    private final MarketDataTableRegistryRepository registryRepository;
    private final MarketDataSyncService syncService;
    private final ChartLiveSessionService chartSession;

    private static final int DEFAULT_BAR_LIMIT = 200;

    /**
     * Polls every 30 seconds; only syncs registry entries that match the
     * currently active chart session (symbol + timeframe).
     */
    @Scheduled(fixedDelay = 30_000)
    public void syncDueTables() {
        if (!chartSession.isActive()) {
            log.trace("Market data sync skipped — chart session not active");
            return;
        }

        List<MarketDataTableRegistry> due = registryRepository.findDueForSync(LocalDateTime.now());
        if (due.isEmpty()) return;

        log.debug("Chart sync tick — {} due table(s) for {}/{}",
                due.size(), chartSession.getSymbol(), chartSession.getTimeframe());

        for (MarketDataTableRegistry entry : due) {
            if (!chartSession.matches(entry)) {
                continue;
            }
            try {
                syncService.syncRegistryEntry(entry, DEFAULT_BAR_LIMIT);
            } catch (Exception e) {
                log.error("Scheduled sync failed for {}/{}: {}",
                        entry.getSymbol(), entry.getTimeframe(), e.getMessage());
            }
        }
    }

    /**
     * Called when the user switches symbol or timeframe on the live chart view.
     * Updates the session context so the next scheduler tick targets the new
     * symbol/timeframe.
     *
     * @param symbol    new symbol (upper-cased internally)
     * @param timeframe new timeframe (lower-cased internally)
     */
    public void notifyChartContextChanged(String symbol, String timeframe) {
        chartSession.updateContext(symbol, timeframe);
        log.debug("Chart context updated → {}/{}", symbol, timeframe);
    }
}
