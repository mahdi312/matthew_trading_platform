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
 * Polls external market APIs for the <em>active chart only</em> (symbol + timeframe).
 * Pauses entirely when the user navigates away from the Live Chart view.
 */
@Component
@ConditionalOnProperty(name = "app.market-data.sync.enabled", havingValue = "true", matchIfMissing = true)
public class MarketDataSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(MarketDataSyncScheduler.class);

    private final MarketDataTableRegistryRepository registryRepository;
    private final MarketDataSyncService syncService;
    private final MarketDataProperties properties;
    private final AppSettingsService appSettings;
    private final ChartLiveSessionService chartSession;

    private volatile UserProfile activeProfile;

    public MarketDataSyncScheduler(MarketDataTableRegistryRepository registryRepository,
                                   MarketDataSyncService syncService,
                                   MarketDataProperties properties,
                                   AppSettingsService appSettings,
                                   ChartLiveSessionService chartSession) {
        this.registryRepository = registryRepository;
        this.syncService = syncService;
        this.properties = properties;
        this.appSettings = appSettings;
        this.chartSession = chartSession;
    }

    public void setActiveProfile(UserProfile profile) {
        this.activeProfile = profile;
    }

    @Scheduled(fixedDelay = 30_000)
    public void syncDueTables() {
        if (!properties.getDynamicTables().isEnabled() || !appSettings.isApiFetchEnabled()) {
            return;
        }
        if (!chartSession.isActive()) {
            log.trace("Market data sync skipped — chart view not active");
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
                syncService.syncRegistryEntry(entry, syncService.defaultBarLimit(), activeProfile);
            } catch (Exception e) {
                log.error("Scheduled sync failed for {}: {}", entry.getTableName(), e.getMessage());
            }
        }
    }

    /** Called when the user changes symbol or timeframe on the open chart. */
    public void notifyChartContextChanged(String symbol, String timeframe) {
        chartSession.updateContext(symbol, timeframe);
        if (activeProfile != null && symbol != null) {
            activeProfile.setDefaultSymbol(symbol.toUpperCase());
        }
    }

    /** @deprecated use {@link #notifyChartContextChanged(String, String)} */
    @Deprecated
    public void notifyActiveSymbolChanged(String symbol) {
        notifyChartContextChanged(symbol, chartSession.getTimeframe());
    }
}
