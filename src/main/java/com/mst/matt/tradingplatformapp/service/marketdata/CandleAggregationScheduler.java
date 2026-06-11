package com.mst.matt.tradingplatformapp.service.marketdata;

import com.mst.matt.tradingplatformapp.model.MarketDataTableRegistry;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade;
import com.mst.matt.tradingplatformapp.repository.MarketDataTableRegistryRepository;
import com.mst.matt.tradingplatformapp.service.price.MarketDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Background scheduler that automatically aggregates lower-TF candles into higher TFs
 * for every registered symbol/timeframe in the {@code market_data_table_registry}.
 *
 * <p>Runs every 15 minutes by default (configurable via
 * {@code app.market-data.aggregation.cron}).
 *
 * <p>For each registered base table (e.g. {@code BTCUSDT_BINANCE_1h}), the service
 * loads the stored bars and computes all derivable higher-TF aggregations, storing
 * the results in tables with the same naming convention
 * (e.g. {@code BTCUSDT_BINANCE_4h}, {@code BTCUSDT_BINANCE_1d}, …).
 */
@Component
public class CandleAggregationScheduler {

    private static final Logger log = LoggerFactory.getLogger(CandleAggregationScheduler.class);

    private final MarketDataTableRegistryRepository registryRepository;
    private final DynamicOhlcvTableService           dynamicTableService;
    private final CandleAggregationService           aggregationService;

    public CandleAggregationScheduler(MarketDataTableRegistryRepository registryRepository,
                                      DynamicOhlcvTableService dynamicTableService,
                                      CandleAggregationService aggregationService) {
        this.registryRepository = registryRepository;
        this.dynamicTableService = dynamicTableService;
        this.aggregationService = aggregationService;
    }

    /**
     * Runs every 15 minutes.  For each registered base timeframe, loads all stored bars
     * and writes higher-TF aggregations into their respective tables.
     */
    @Scheduled(fixedDelayString = "${app.market-data.aggregation.delay-ms:900000}")
    public void runAggregation() {
        log.info("Starting offline candle aggregation pass…");
        int total = 0;
        try {
            List<MarketDataTableRegistry> entries = registryRepository.findAll();
            for (MarketDataTableRegistry entry : entries) {
                try {
                    total += processEntry(entry);
                } catch (Exception ex) {
                    log.warn("Aggregation skipped for {}/{}: {}", entry.getSymbol(),
                            entry.getTimeframe(), ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.error("Aggregation pass failed: {}", ex.getMessage(), ex);
        }
        log.info("Offline candle aggregation done — {} higher-TF table(s) updated.", total);
    }

    /**
     * Triggers an immediate (on-demand) aggregation for a specific symbol and timeframe.
     * Call this right after new bars are stored (e.g. from {@link MarketDataSyncService}).
     *
     * @param symbol    e.g. "BTCUSDT"
     * @param sourceTf  source timeframe that just received new data, e.g. "1h"
     * @param provider  data provider that sourced the bars (used for table naming)
     * @param assetType asset class
     * @param bars      freshly stored bars (in chronological order)
     */
    public void triggerAggregationForBars(String symbol, String sourceTf,
                                          MarketDataProvider provider,
                                          Trade.AssetType assetType,
                                          List<OhlcvBar> bars) {
        if (bars == null || bars.isEmpty()) return;
        String providerSegment = (provider == null || provider == MarketDataProvider.AUTO)
                ? "" : provider.name();
        Thread.ofVirtual().start(() -> {
            try {
                Map<String, Integer> results = aggregationService.aggregateAndStore(
                        symbol.toUpperCase(), sourceTf, providerSegment, assetType, bars);
                if (!results.isEmpty()) {
                    log.info("On-demand aggregation for {}/{}: {}", symbol, sourceTf, results);
                }
            } catch (Exception ex) {
                log.warn("On-demand aggregation failed for {}/{}: {}", symbol, sourceTf,
                        ex.getMessage());
            }
        });
    }

    // ── Private helpers ───────────────────────────────────────

    private int processEntry(MarketDataTableRegistry entry) {
        List<String> derivable = aggregationService.derivableTimeframes(entry.getTimeframe());
        if (derivable.isEmpty()) return 0;

        // Read all available bars from the source table
        List<OhlcvBar> sourceBars = dynamicTableService.findBars(
                entry.getTableName(), entry.getSymbol(), entry.getTimeframe(),
                entry.getAssetType(), 10_000 /* large limit to get all stored bars */);
        if (sourceBars.isEmpty()) return 0;

        String providerSegment = (entry.getProvider() == null
                || entry.getProvider() == MarketDataProvider.AUTO)
                ? "" : entry.getProvider().name();

        Map<String, Integer> results = aggregationService.aggregateAndStore(
                entry.getSymbol(), entry.getTimeframe(),
                providerSegment, entry.getAssetType(), sourceBars);
        return results.size();
    }
}
