package com.mst.matt.tradingplatformapp.service.marketdata;

import com.mst.matt.tradingplatformapp.config.MarketDataProperties;
import com.mst.matt.tradingplatformapp.service.AppSettingsService;
import com.mst.matt.tradingplatformapp.model.MarketDataTableRegistry;
import com.mst.matt.tradingplatformapp.model.UserProfile;
import com.mst.matt.tradingplatformapp.repository.MarketDataTableRegistryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Polls external market APIs on each registered table's timeframe interval
 * and persists results into per-symbol/provider/timeframe tables.
 */
@Component
@ConditionalOnProperty(name = "app.market-data.sync.enabled", havingValue = "true", matchIfMissing = true)
public class MarketDataSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketDataSyncScheduler.class);

    private final MarketDataTableRegistryRepository registryRepository;
    private final MarketDataSyncService syncService;
    private final MarketDataProperties properties;
    private final AppSettingsService appSettings;

    private volatile UserProfile activeProfile;

    public MarketDataSyncScheduler(MarketDataTableRegistryRepository registryRepository,
                                   MarketDataSyncService syncService,
                                   MarketDataProperties properties,
                                   AppSettingsService appSettings) {
        this.registryRepository = registryRepository;
        this.syncService = syncService;
        this.properties = properties;
        this.appSettings = appSettings;
    }

    public void setActiveProfile(UserProfile profile) {
        this.activeProfile = profile;
    }

    /**
     * Sync only tables that belong to the currently viewed symbol (or the active profile's
     * default symbol). This prevents background bulk-loading of OHLCV data for every
     * symbol ever registered — only the chart the user is actually looking at gets refreshed.
     */
    @Scheduled(fixedDelay = 30_000)
    public void syncDueTables() {
        if (!properties.getDynamicTables().isEnabled() || !appSettings.isApiFetchEnabled()) {
            return;
        }
        List<MarketDataTableRegistry> due = registryRepository.findDueForSync(LocalDateTime.now());
        if (due.isEmpty()) {
            return;
        }

        // Lazy load: only sync tables for the currently active symbol
        // (if we know which symbol is active via the profile's default symbol)
        String activeSymbol = (activeProfile != null && activeProfile.getDefaultSymbol() != null)
                ? activeProfile.getDefaultSymbol().toUpperCase() : null;

        log.debug("Market data sync tick — {} table(s) due, active symbol: {}",
                due.size(), activeSymbol);

        for (MarketDataTableRegistry entry : due) {
            // Skip entries for symbols not matching the currently viewed symbol
            if (activeSymbol != null && !entry.getSymbol().equalsIgnoreCase(activeSymbol)) {
                log.trace("Skipping background sync for {} (not the active chart symbol {})",
                        entry.getSymbol(), activeSymbol);
                continue;
            }
            try {
                syncService.syncRegistryEntry(entry, syncService.defaultBarLimit(), activeProfile);
            } catch (Exception e) {
                log.error("Scheduled sync failed for {}: {}", entry.getTableName(), e.getMessage());
            }
        }
    }

    /** Called by MainDashboardController / ChartController when the user switches symbol. */
    public void notifyActiveSymbolChanged(String symbol) {
        if (activeProfile != null && symbol != null) {
            activeProfile.setDefaultSymbol(symbol.toUpperCase());
        }
    }
}
