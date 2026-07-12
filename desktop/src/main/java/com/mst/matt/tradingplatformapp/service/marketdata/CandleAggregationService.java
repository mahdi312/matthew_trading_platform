package com.mst.matt.tradingplatformapp.service.marketdata;

import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregates lower-timeframe OHLCV bars into higher timeframes.
 *
 * <p>Given source bars (e.g. 1H), the service can produce:
 * <ul>
 *   <li>From 1M  → 5M, 15M, 30M, 1H, 2H, 4H, 6H, 8H, 12H, 1D, 1W, 1Mo</li>
 *   <li>From 5M  → 15M, 30M, 1H, 2H, 4H, 6H, 8H, 12H, 1D, 1W, 1Mo</li>
 *   <li>From 15M → 30M, 1H, 2H, 4H, 6H, 8H, 12H, 1D, 1W, 1Mo</li>
 *   <li>From 1H  → 2H, 4H, 6H, 8H, 12H, 1D, 1W, 1Mo</li>
 *   <li>From 1D  → 1W, 1Mo</li>
 * </ul>
 *
 * <p>Aggregated results are stored in tables following the same naming convention
 * as live data: {@code SYMBOL_PROVIDER_TF} (e.g. {@code BTCUSDT_BINANCE_4h}).
 * Tables are prefixed with {@code agg_} to distinguish them from live-feed tables
 * when the provider segment is absent.
 */
@Service
public class CandleAggregationService {

    private static final Logger log = LoggerFactory.getLogger(CandleAggregationService.class);

    /**
     * Ordered list of all canonical timeframes with their Duration.
     * We use this to determine which higher TFs can be derived from a given source TF.
     */
    public static final List<TfSpec> ALL_TIMEFRAMES = List.of(
            new TfSpec("1m",  Duration.ofMinutes(1)),
            new TfSpec("3m",  Duration.ofMinutes(3)),
            new TfSpec("5m",  Duration.ofMinutes(5)),
            new TfSpec("15m", Duration.ofMinutes(15)),
            new TfSpec("30m", Duration.ofMinutes(30)),
            new TfSpec("1h",  Duration.ofHours(1)),
            new TfSpec("2h",  Duration.ofHours(2)),
            new TfSpec("4h",  Duration.ofHours(4)),
            new TfSpec("6h",  Duration.ofHours(6)),
            new TfSpec("8h",  Duration.ofHours(8)),
            new TfSpec("12h", Duration.ofHours(12)),
            new TfSpec("1d",  Duration.ofDays(1)),
            new TfSpec("3d",  Duration.ofDays(3)),
            new TfSpec("1w",  Duration.ofDays(7)),
            new TfSpec("1mo", Duration.ofDays(30))
    );

    private static final Map<String, Duration> TF_DURATION = ALL_TIMEFRAMES.stream()
            .collect(Collectors.toMap(TfSpec::label, TfSpec::duration));

    private final DynamicOhlcvTableService dynamicTableService;

    public CandleAggregationService(DynamicOhlcvTableService dynamicTableService) {
        this.dynamicTableService = dynamicTableService;
    }

    // ── Public API ────────────────────────────────────────────

    /**
     * Given a list of source bars for {@code symbol} / {@code sourceTf}, compute all valid
     * higher-timeframe aggregations and persist them in the database.
     *
     * @param symbol     e.g. "BTCUSDT"
     * @param sourceTf   source timeframe label, e.g. "1h"
     * @param providerSegment  e.g. "BINANCE" or empty string — used in the table name
     * @param assetType  asset class for metadata
     * @param sourceBars ordered (ascending open_time) source OHLCV bars
     * @return map of target timeframe → number of bars stored
     */
    public Map<String, Integer> aggregateAndStore(
            String symbol,
            String sourceTf,
            String providerSegment,
            Trade.AssetType assetType,
            List<OhlcvBar> sourceBars) {

        Map<String, Integer> results = new LinkedHashMap<>();
        if (sourceBars == null || sourceBars.isEmpty()) {
            log.debug("No source bars to aggregate for {} {}", symbol, sourceTf);
            return results;
        }

        Duration sourceDuration = TF_DURATION.getOrDefault(sourceTf.toLowerCase(), null);
        if (sourceDuration == null) {
            log.warn("Unknown source timeframe '{}' — skipping aggregation", sourceTf);
            return results;
        }

        List<TfSpec> higherTfs = ALL_TIMEFRAMES.stream()
                .filter(spec -> spec.duration().toMinutes() > sourceDuration.toMinutes())
                .filter(spec -> spec.duration().toMinutes() % sourceDuration.toMinutes() == 0)
                .collect(Collectors.toList());

        if (higherTfs.isEmpty()) {
            log.debug("No higher TFs can be derived from {} for {}", sourceTf, symbol);
            return results;
        }

        log.info("Aggregating {} {} bars for {} → {}",
                sourceBars.size(), sourceTf, symbol,
                higherTfs.stream().map(TfSpec::label).collect(Collectors.joining(", ")));

        for (TfSpec target : higherTfs) {
            try {
                List<OhlcvBar> aggregated = aggregate(sourceBars, sourceTf, target.label(),
                        target.duration(), symbol);
                if (!aggregated.isEmpty()) {
                    String tableName = buildAggTableName(symbol, providerSegment, target.label());
                    dynamicTableService.ensureTable(tableName);
                    dynamicTableService.replaceBars(tableName, symbol, target.label(),
                            assetType, aggregated);
                    results.put(target.label(), aggregated.size());
                    log.info("Aggregated {} bars {}/{} → table '{}'",
                            aggregated.size(), symbol, target.label(), tableName);
                }
            } catch (Exception ex) {
                log.warn("Aggregation failed for {} {} → {}: {}", symbol, sourceTf,
                        target.label(), ex.getMessage());
            }
        }
        return results;
    }

    /**
     * Reads pre-aggregated bars for a symbol / timeframe from the aggregated table.
     *
     * @param symbol          e.g. "BTCUSDT"
     * @param providerSegment e.g. "BINANCE" or empty
     * @param targetTf        target timeframe, e.g. "4h"
     * @param assetType       asset class for metadata
     * @param limit           max number of bars to return
     * @return list of bars, chronologically ordered; empty if no table exists yet
     */
    public List<OhlcvBar> readAggregated(String symbol, String providerSegment,
                                         String targetTf, Trade.AssetType assetType,
                                         int limit) {
        String tableName = buildAggTableName(symbol, providerSegment, targetTf);
        return dynamicTableService.findBars(tableName, symbol, targetTf, assetType, limit);
    }

    /**
     * Returns the list of higher timeframes that can be derived from the given source timeframe.
     *
     * @param sourceTf source timeframe label
     * @return ordered list of derivable target timeframe labels
     */
    public List<String> derivableTimeframes(String sourceTf) {
        Duration sourceDuration = TF_DURATION.getOrDefault(sourceTf.toLowerCase(), null);
        if (sourceDuration == null) return List.of();
        return ALL_TIMEFRAMES.stream()
                .filter(spec -> spec.duration().toMinutes() > sourceDuration.toMinutes()
                        && spec.duration().toMinutes() % sourceDuration.toMinutes() == 0)
                .map(TfSpec::label)
                .collect(Collectors.toList());
    }

    // ── Core aggregation logic ────────────────────────────────

    /**
     * Aggregates a list of lower-TF bars into higher-TF bars by bucketing them
     * into fixed time windows aligned to the Unix epoch.
     *
     * @param sourceBars    chronologically ordered source bars
     * @param sourceTfLabel label of the source TF (for logging)
     * @param targetTfLabel label of the target TF
     * @param targetDuration duration of each target bar
     * @param symbol        symbol name (set on aggregated bars)
     * @return list of aggregated bars, chronologically ordered; only includes
     *         complete periods (last in-progress bucket is excluded)
     */
    public List<OhlcvBar> aggregate(List<OhlcvBar> sourceBars,
                                    String sourceTfLabel,
                                    String targetTfLabel,
                                    Duration targetDuration,
                                    String symbol) {
        if (sourceBars == null || sourceBars.isEmpty()) return List.of();

        long targetMinutes = targetDuration.toMinutes();
        if (targetMinutes <= 0) return List.of();

        // Group source bars into target-TF buckets
        Map<LocalDateTime, List<OhlcvBar>> buckets = new TreeMap<>();
        for (OhlcvBar bar : sourceBars) {
            LocalDateTime bucket = snapToBucket(bar.getOpenTime(), targetMinutes);
            buckets.computeIfAbsent(bucket, k -> new ArrayList<>()).add(bar);
        }

        // Determine the last complete bucket: all bars should exist unless the last
        // bucket only partially has data (in-progress candle). We exclude it if it
        // contains fewer bars than the expected count.
        long expectedBarsPerBucket = targetMinutes
                / TF_DURATION.getOrDefault(sourceTfLabel.toLowerCase(), targetDuration).toMinutes();

        List<LocalDateTime> bucketKeys = new ArrayList<>(buckets.keySet());
        // Remove the last bucket if it is still building (< expected bars)
        if (!bucketKeys.isEmpty()) {
            LocalDateTime lastKey = bucketKeys.get(bucketKeys.size() - 1);
            int lastCount = buckets.get(lastKey).size();
            if (lastCount < expectedBarsPerBucket) {
                buckets.remove(lastKey);
            }
        }

        List<OhlcvBar> result = new ArrayList<>();
        for (Map.Entry<LocalDateTime, List<OhlcvBar>> entry : buckets.entrySet()) {
            List<OhlcvBar> group = entry.getValue();
            if (group.isEmpty()) continue;
            group.sort(Comparator.comparing(OhlcvBar::getOpenTime));

            BigDecimal open  = group.get(0).getOpen();
            BigDecimal close = group.get(group.size() - 1).getClose();
            BigDecimal high  = group.stream().map(OhlcvBar::getHigh)
                    .max(BigDecimal::compareTo).orElse(open);
            BigDecimal low   = group.stream().map(OhlcvBar::getLow)
                    .min(BigDecimal::compareTo).orElse(open);
            BigDecimal volume = group.stream().map(OhlcvBar::getVolume)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Trade.AssetType assetType = group.get(0).getAssetType() != null
                    ? group.get(0).getAssetType() : Trade.AssetType.CRYPTO;

            result.add(OhlcvBar.builder()
                    .symbol(symbol)
                    .timeframe(targetTfLabel)
                    .openTime(entry.getKey())
                    .open(open)
                    .high(high)
                    .low(low)
                    .close(close)
                    .volume(volume)
                    .assetType(assetType)
                    .build());
        }
        return result;
    }

    // ── Naming convention ─────────────────────────────────────

    /**
     * Builds an aggregated table name using the same convention as live-feed tables:
     * {@code SYMBOL_PROVIDER_TF} when a provider segment is present, otherwise
     * {@code SYMBOL_agg_TF} (with "agg" to distinguish from live tables).
     *
     * <p>Examples:
     * <ul>
     *   <li>({@code BTCUSDT}, {@code BINANCE}, {@code 4h}) → {@code BTCUSDT_BINANCE_4h}</li>
     *   <li>({@code BTCUSDT}, {@code ""}, {@code 4h}) → {@code BTCUSDT_agg_4h}</li>
     * </ul>
     */
    public static String buildAggTableName(String symbol, String providerSegment, String targetTf) {
        String sym = MarketDataTableNameUtil.sanitizeSymbol(symbol);
        String tf  = MarketDataTableNameUtil.normalizeTimeframe(targetTf);
        if (providerSegment != null && !providerSegment.isBlank()) {
            String prov = MarketDataTableNameUtil.sanitizeSymbol(providerSegment);
            return sym + "_" + prov + "_" + tf;
        }
        return sym + "_agg_" + tf;
    }

    // ── Helpers ───────────────────────────────────────────────

    /**
     * Snaps a timestamp to the start of the enclosing target-TF bucket.
     * Uses minutes-from-epoch arithmetic so all bars worldwide align to the same boundaries.
     */
    private static LocalDateTime snapToBucket(LocalDateTime time, long targetMinutes) {
        // Convert to total minutes from a reference epoch (2000-01-01T00:00)
        LocalDateTime EPOCH = LocalDateTime.of(2000, 1, 1, 0, 0);
        long minutesFromEpoch = EPOCH.until(time, ChronoUnit.MINUTES);
        long bucketStart = (minutesFromEpoch / targetMinutes) * targetMinutes;
        return EPOCH.plusMinutes(bucketStart);
    }

    // ── Data types ─────────────────────────────────────────────

    public record TfSpec(String label, Duration duration) {}
}
