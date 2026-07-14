package com.mst.matt.marketservice.service;

import com.mst.matt.marketservice.model.AssetType;
import com.mst.matt.marketservice.model.OhlcvBar;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * API service for querying pre-aggregated candle data.
 *
 * <p>Ported from the desktop monolith's {@code AggregatedCandleQueryService};
 * adapted for the market-service single-table approach:</p>
 * <ul>
 *   <li>Dynamic-table reads replaced by JPA queries on {@code ohlcv_bars}
 *       filtered by symbol + timeframe + (optionally) provider.</li>
 *   <li>On-demand rebuild delegates to {@link CandleAggregationService}.</li>
 * </ul>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 *   // Fetch pre-aggregated 4H bars for BTCUSDT (derived from 1H Binance data)
 *   List<OhlcvBar> bars = queryService.fetchAggregated(
 *       "BTCUSDT", "BINANCE", "4h", AssetType.CRYPTO, 200);
 * }</pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AggregatedCandleQueryService {

    private final CandleAggregationService aggregationService;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Fetches pre-aggregated OHLCV bars for the given symbol and target timeframe.
     *
     * <p>First tries the provider-qualified slice; falls back to any-provider
     * (empty segment) if no bars are found.</p>
     *
     * @param symbol          trading symbol, e.g. "BTCUSDT"
     * @param providerSegment provider name, e.g. "BINANCE" (or empty/null)
     * @param targetTf        target timeframe label, e.g. "4h", "1d"
     * @param assetType       asset class for metadata
     * @param limit           max bars to return (most recent)
     * @return pre-aggregated bars in chronological order; empty if no data
     */
    public List<OhlcvBar> fetchAggregated(String symbol,
                                          String providerSegment,
                                          String targetTf,
                                          AssetType assetType,
                                          int limit) {
        // Try provider-qualified slice first
        if (providerSegment != null && !providerSegment.isBlank()) {
            List<OhlcvBar> bars = aggregationService.readAggregated(
                    symbol, providerSegment, targetTf, assetType, limit);
            if (!bars.isEmpty()) {
                log.debug("Served {} aggregated bars for {}/{} from provider slice '{}'",
                        bars.size(), symbol, targetTf, providerSegment);
                return bars;
            }
        }
        // Fallback: any-provider slice
        List<OhlcvBar> bars = aggregationService.readAggregated(
                symbol, "", targetTf, assetType, limit);
        log.debug("Served {} aggregated bars for {}/{} from generic slice",
                bars.size(), symbol, targetTf);
        return bars;
    }

    /**
     * Returns the list of higher timeframes for which pre-aggregated data
     * exists in the database.
     *
     * @param symbol          trading symbol
     * @param providerSegment provider name (may be empty)
     * @param sourceTf        base timeframe used to generate aggregations
     * @return list of target timeframe labels that have data stored
     */
    public List<String> availableAggregations(String symbol, String providerSegment,
                                              String sourceTf) {
        return aggregationService.derivableTimeframes(sourceTf).stream()
                .filter(tf -> !aggregationService.readAggregated(
                        symbol, providerSegment, tf, null, 1).isEmpty())
                .toList();
    }

    /**
     * Triggers an immediate on-demand aggregation from source bars in memory.
     *
     * <p>Use this after a manual refresh when you have fresh source bars and
     * want to rebuild all higher-TF aggregations immediately.</p>
     *
     * @param symbol          trading symbol
     * @param sourceTf        source timeframe label
     * @param providerSegment provider name (may be empty)
     * @param assetType       asset class
     * @param sourceBars      chronologically ordered source bars
     * @return map: target TF → number of bars stored
     */
    public Map<String, Integer> rebuildAggregations(String symbol, String sourceTf,
                                                    String providerSegment,
                                                    AssetType assetType,
                                                    List<OhlcvBar> sourceBars) {
        return aggregationService.aggregateAndStore(
                symbol, sourceTf, providerSegment, assetType, sourceBars);
    }
}
