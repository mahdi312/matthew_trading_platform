package com.mst.matt.marketservice.service;

import com.mst.matt.marketservice.model.MarketDataTableRegistry;
import com.mst.matt.marketservice.repository.MarketDataTableRegistryRepository;
import com.mst.matt.marketservice.repository.OhlcvBarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Schedules periodic candle aggregation for all registered symbol+timeframe entries.
 * Ported from desktop monolith's {@code CandleAggregationScheduler}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CandleAggregationScheduler {

    private final MarketDataTableRegistryRepository registryRepository;
    private final OhlcvBarRepository barRepository;
    private final CandleAggregationService aggregationService;

    /** Runs every 5 minutes; aggregates from base timeframe for each registry entry. */
    @Scheduled(fixedDelayString = "${market.aggregation.interval-ms:300000}")
    public void runAggregation() {
        List<MarketDataTableRegistry> entries = registryRepository.findAll();
        log.debug("Aggregation scheduler: {} registry entries", entries.size());
        for (MarketDataTableRegistry entry : entries) {
            try {
                List<com.mst.matt.marketservice.model.OhlcvBar> sourceBars =
                        barRepository.findTopBySymbolAndTimeframe(
                                entry.getSymbol(), entry.getTimeframe(), PageRequest.of(0, 500));
                if (!sourceBars.isEmpty()) {
                    aggregationService.aggregateAndStore(
                            entry.getSymbol(), entry.getTimeframe(),
                            entry.getProvider() != null ? entry.getProvider().name() : "",
                            entry.getAssetType(),
                            OhlcvStorageService.chronological(sourceBars));
                }
            } catch (Exception e) {
                log.warn("Aggregation failed for {}/{}: {}", entry.getSymbol(), entry.getTimeframe(), e.getMessage());
            }
        }
    }

    /**
     * On-demand aggregation trigger — called by {@link MarketDataSyncService}
     * immediately after a fresh batch of source bars is persisted.
     *
     * @param symbol     trading symbol
     * @param sourceTf   timeframe of the source bars
     * @param assetType  asset class for metadata
     * @param sourceBars freshly persisted source bars (chronological)
     */
    public void triggerAggregationForBars(String symbol, String sourceTf,
                                          com.mst.matt.marketservice.model.AssetType assetType,
                                          List<com.mst.matt.marketservice.model.OhlcvBar> sourceBars) {
        try {
            aggregationService.aggregateAndStore(
                    symbol, sourceTf, "", assetType,
                    OhlcvStorageService.chronological(sourceBars));
        } catch (Exception e) {
            log.warn("On-demand aggregation failed for {}/{}: {}", symbol, sourceTf, e.getMessage());
        }
    }
}
