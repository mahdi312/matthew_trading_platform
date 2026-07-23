package com.mst.matt.marketservice.service;

import com.mst.matt.marketservice.model.OhlcvBar;
import com.mst.matt.marketservice.model.AssetType;
import com.mst.matt.marketservice.repository.OhlcvBarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregates lower-timeframe OHLCV bars into higher timeframes.
 * Ported from desktop monolith's {@code CandleAggregationService}.
 * Simplified: removed DynamicOhlcvTableService dependency; uses single ohlcv_bars table.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandleAggregationService {

    private final OhlcvBarRepository barRepository;

    public record TfSpec(String label, Duration duration) {}

    public static final List<TfSpec> ALL_TIMEFRAMES = List.of(
            new TfSpec("1m", Duration.ofMinutes(1)), new TfSpec("5m", Duration.ofMinutes(5)),
            new TfSpec("15m", Duration.ofMinutes(15)), new TfSpec("30m", Duration.ofMinutes(30)),
            new TfSpec("1h", Duration.ofHours(1)), new TfSpec("2h", Duration.ofHours(2)),
            new TfSpec("4h", Duration.ofHours(4)), new TfSpec("6h", Duration.ofHours(6)),
            new TfSpec("12h", Duration.ofHours(12)), new TfSpec("1d", Duration.ofDays(1)),
            new TfSpec("1w", Duration.ofDays(7)));

    private static final Map<String, Duration> TF_DURATION = ALL_TIMEFRAMES.stream()
            .collect(Collectors.toMap(TfSpec::label, TfSpec::duration));

    /**
     * Aggregate sourceBars into all valid higher timeframes and persist them.
     * @return map of target-timeframe → bars saved
     */
    @Transactional
    public Map<String, Integer> aggregateAndStore(String symbol, String sourceTf,
                                                   String provider, AssetType assetType,
                                                   List<OhlcvBar> sourceBars) {
        if (sourceBars == null || sourceBars.isEmpty()) return Map.of();
        Duration srcDur = TF_DURATION.get(sourceTf);
        if (srcDur == null) { log.warn("Unknown source timeframe: {}", sourceTf); return Map.of(); }

        Map<String, Integer> result = new LinkedHashMap<>();
        for (TfSpec target : ALL_TIMEFRAMES) {
            if (target.duration().compareTo(srcDur) <= 0) continue;
            if (target.duration().toSeconds() % srcDur.toSeconds() != 0) continue;
            List<OhlcvBar> agg = aggregate(sourceBars, target, symbol, assetType, provider);
            if (!agg.isEmpty()) {
                barRepository.saveAll(agg);
                result.put(target.label(), agg.size());
                log.debug("Aggregated {} bars for {}/{}", agg.size(), symbol, target.label());
            }
        }
        return result;
    }

    private List<OhlcvBar> aggregate(List<OhlcvBar> src, TfSpec target,
                                     String symbol, AssetType assetType, String provider) {
        Map<LocalDateTime, List<OhlcvBar>> buckets = new LinkedHashMap<>();
        for (OhlcvBar bar : src) {
            LocalDateTime bucket = alignToBucket(bar.getOpenTime(), target.duration());
            buckets.computeIfAbsent(bucket, k -> new ArrayList<>()).add(bar);
        }
        List<OhlcvBar> result = new ArrayList<>();
        for (Map.Entry<LocalDateTime, List<OhlcvBar>> entry : buckets.entrySet()) {
            List<OhlcvBar> group = entry.getValue();
            OhlcvBar merged = OhlcvBar.builder()
                    .symbol(symbol).timeframe(target.label())
                    .openTime(entry.getKey()).provider(provider).assetType(assetType)
                    .open(group.get(0).getOpen())
                    .high(group.stream().map(OhlcvBar::getHigh).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO))
                    .low(group.stream().map(OhlcvBar::getLow).min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO))
                    .close(group.get(group.size()-1).getClose())
                    .volume(group.stream().map(OhlcvBar::getVolume).reduce(BigDecimal.ZERO, BigDecimal::add))
                    .build();
            barRepository.findBySymbolAndTimeframeAndOpenTime(symbol, target.label(), entry.getKey())
                    .ifPresent(ex -> merged.setId(ex.getId()));
            result.add(merged);
        }
        return result;
    }

    private static LocalDateTime alignToBucket(LocalDateTime dt, Duration dur) {
        long epochSeconds = dt.toEpochSecond(java.time.ZoneOffset.UTC);
        long durSeconds = dur.toSeconds();
        long aligned = (epochSeconds / durSeconds) * durSeconds;
        return LocalDateTime.ofEpochSecond(aligned, 0, java.time.ZoneOffset.UTC);
    }

    // ── Query helpers (used by AggregatedCandleQueryService) ─────────────────

    /**
     * Reads pre-aggregated bars from the single {@code ohlcv_bars} JPA table,
     * keyed by symbol + targetTf. The provider segment is embedded in the
     * {@code provider} column of each bar.
     *
     * @param symbol          trading symbol
     * @param providerSegment provider name, or empty for any provider
     * @param targetTf        target timeframe label
     * @param assetType       asset class filter
     * @param limit           max bars to return
     * @return chronologically ordered bars; empty if none available
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<OhlcvBar> readAggregated(String symbol, String providerSegment,
                                         String targetTf, AssetType assetType, int limit) {
        List<OhlcvBar> bars;
        if (providerSegment != null && !providerSegment.isBlank()) {
            bars = barRepository.findTopBySymbolAndTimeframe(
                    symbol.toUpperCase(), targetTf,
                    org.springframework.data.domain.PageRequest.of(0, limit));
            bars = bars.stream()
                    .filter(b -> providerSegment.equalsIgnoreCase(b.getProvider()))
                    .toList();
        } else {
            bars = barRepository.findTopBySymbolAndTimeframe(
                    symbol.toUpperCase(), targetTf,
                    org.springframework.data.domain.PageRequest.of(0, limit));
        }
        List<OhlcvBar> ordered = new ArrayList<>(bars);
        ordered.sort(Comparator.comparing(OhlcvBar::getOpenTime));
        return ordered;
    }

    /**
     * Returns the list of higher timeframes derivable from {@code sourceTf}.
     * The set is every TF in {@link #ALL_TIMEFRAMES} whose duration is strictly
     * greater than sourceTf's duration AND evenly divisible.
     */
    public List<String> derivableTimeframes(String sourceTf) {
        Duration srcDur = TF_DURATION.get(sourceTf == null ? "" : sourceTf.toLowerCase());
        if (srcDur == null) return List.of();
        return ALL_TIMEFRAMES.stream()
                .filter(tf -> tf.duration().compareTo(srcDur) > 0
                        && tf.duration().toSeconds() % srcDur.toSeconds() == 0)
                .map(TfSpec::label)
                .toList();
    }

    /**
     * Builds the aggregated-table name following the convention:
     * {@code SYMBOL_PROVIDER_TF} (e.g. {@code BTCUSDT_BINANCE_4h}) or
     * {@code BTCUSDT_agg_4h} when the provider segment is empty.
     */
    public static String buildAggTableName(String symbol, String providerSegment, String targetTf) {
        String seg = (providerSegment == null || providerSegment.isBlank()) ? "agg" : providerSegment.toUpperCase();
        return symbol.toUpperCase() + "_" + seg + "_" + targetTf.toLowerCase();
    }

    /**
     * Triggers aggregation and returns a bar already in the registry for the
     * given chart session. Delegates to
     * {@link #aggregateAndStore(String, String, String, AssetType, List)}.
     */
    public Map<String, Integer> triggerForBars(String symbol, String sourceTf,
                                               String providerName, AssetType assetType,
                                               List<OhlcvBar> sourceBars) {
        return aggregateAndStore(symbol, sourceTf, providerName, assetType, sourceBars);
    }
}
