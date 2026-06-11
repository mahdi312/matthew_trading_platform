package com.mst.matt.tradingplatformapp.service.marketdata;

import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade;
import com.mst.matt.tradingplatformapp.service.price.AssetClassDetector;
import com.mst.matt.tradingplatformapp.service.price.MarketDataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * API service for querying pre-aggregated candle data stored offline.
 *
 * <p>This service acts as the single access point for the "offline candle aggregation"
 * feature. It reads higher-TF bars that were pre-computed from lower-TF source data
 * and stored in {@code agg_} tables (or provider-qualified tables).
 *
 * <p>Usage example:
 * <pre>
 *   // Fetch pre-aggregated 4H bars for BTCUSDT (derived from 1H Binance data)
 *   List&lt;OhlcvBar&gt; bars = queryService.fetchAggregated(
 *       "BTCUSDT", "BINANCE", "4h", Trade.AssetType.CRYPTO, 200);
 * </pre>
 */
@Service
public class AggregatedCandleQueryService {

    private static final Logger log = LoggerFactory.getLogger(AggregatedCandleQueryService.class);

    private final DynamicOhlcvTableService dynamicTableService;
    private final CandleAggregationService aggregationService;

    public AggregatedCandleQueryService(DynamicOhlcvTableService dynamicTableService,
                                        CandleAggregationService aggregationService) {
        this.dynamicTableService = dynamicTableService;
        this.aggregationService = aggregationService;
    }

    // ── Public API ────────────────────────────────────────────

    /**
     * Fetches pre-aggregated OHLCV bars for the given symbol and target timeframe.
     *
     * <p>The method first checks for a provider-qualified table (e.g. {@code BTCUSDT_BINANCE_4h}).
     * If no bars are found there, it falls back to the generic aggregated table
     * (e.g. {@code BTCUSDT_agg_4h}).
     *
     * @param symbol          trading symbol, e.g. "BTCUSDT"
     * @param providerSegment provider name used in table naming, e.g. "BINANCE" (or empty/null)
     * @param targetTf        target timeframe label, e.g. "4h", "1d"
     * @param assetType       asset class for metadata
     * @param limit           max number of bars to return (most recent)
     * @return pre-aggregated bars in chronological order; empty if no data available
     */
    public List<OhlcvBar> fetchAggregated(String symbol,
                                          String providerSegment,
                                          String targetTf,
                                          Trade.AssetType assetType,
                                          int limit) {
        // Try provider-qualified table first
        if (providerSegment != null && !providerSegment.isBlank()) {
            List<OhlcvBar> bars = aggregationService.readAggregated(
                    symbol, providerSegment, targetTf, assetType, limit);
            if (!bars.isEmpty()) {
                log.debug("Served {} aggregated bars for {}/{} from provider table",
                        bars.size(), symbol, targetTf);
                return bars;
            }
        }
        // Fallback: generic agg table (no provider segment)
        List<OhlcvBar> bars = aggregationService.readAggregated(
                symbol, "", targetTf, assetType, limit);
        log.debug("Served {} aggregated bars for {}/{} from generic agg table",
                bars.size(), symbol, targetTf);
        return bars;
    }

    /**
     * Returns the list of higher timeframes that have pre-aggregated data available
     * for the given symbol and provider.
     *
     * @param symbol          trading symbol
     * @param providerSegment provider name (may be empty)
     * @param sourceTf        the base timeframe that was used to generate aggregations
     * @return list of target timeframe labels that have data in the DB
     */
    public List<String> availableAggregations(String symbol, String providerSegment,
                                              String sourceTf) {
        return aggregationService.derivableTimeframes(sourceTf).stream()
                .filter(tf -> {
                    String tableName = CandleAggregationService.buildAggTableName(
                            symbol, providerSegment, tf);
                    return dynamicTableService.countBars(tableName) > 0;
                })
                .toList();
    }

    /**
     * Triggers an immediate on-demand aggregation from source bars.
     *
     * <p>Use this when you already have source bars in memory and want to rebuild
     * all higher-TF aggregations right away (e.g. after a manual refresh).
     *
     * @param symbol          trading symbol
     * @param sourceTf        source timeframe label
     * @param providerSegment provider name (may be empty)
     * @param assetType       asset class
     * @param sourceBars      chronologically ordered source bars
     * @return summary map: target TF → number of bars stored
     */
    public java.util.Map<String, Integer> rebuildAggregations(
            String symbol, String sourceTf, String providerSegment,
            Trade.AssetType assetType, List<OhlcvBar> sourceBars) {
        return aggregationService.aggregateAndStore(
                symbol, sourceTf, providerSegment, assetType, sourceBars);
    }
}
