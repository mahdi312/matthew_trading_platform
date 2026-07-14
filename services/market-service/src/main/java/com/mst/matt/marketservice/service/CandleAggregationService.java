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
}
