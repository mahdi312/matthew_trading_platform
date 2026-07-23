package com.mst.matt.marketservice.service;

import com.mst.matt.marketservice.model.OhlcvBar;
import com.mst.matt.marketservice.repository.OhlcvBarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Bar persistence service — stores and retrieves OHLCV candles from the database.
 * Ported from the desktop monolith's {@code OhlcvStorageService}.
 *
 * <p>Adapted for the microservice context:
 * <ul>
 *   <li>Removed JavaFX UI callback ({@code onStaleDataWarning})</li>
 *   <li>Removed live-API fetch dependencies (PriceRouter, AppSettingsService) —
 *       this service only does DB persistence; live data arrives via
 *       {@link com.mst.matt.marketservice.bitunix.BitUnixMarketDataProvider}.</li>
 *   <li>Dynamic-table path removed — single {@code ohlcv_bars} table per design decision
 *       documented in {@link OhlcvBar}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OhlcvStorageService {

    private final OhlcvBarRepository barRepository;

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<OhlcvBar> getBars(String symbol, String timeframe, int limit) {
        List<OhlcvBar> bars = barRepository.findTopBySymbolAndTimeframe(
                symbol.toUpperCase(), timeframe, PageRequest.of(0, limit));
        return chronological(bars);
    }

    @Transactional(readOnly = true)
    public List<OhlcvBar> getBars(String symbol, String timeframe,
                                   LocalDateTime from, LocalDateTime to) {
        return barRepository.findBySymbolAndTimeframeAndOpenTimeBetween(
                symbol.toUpperCase(), timeframe, from, to);
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Upsert a list of OHLCV bars (insert or update by symbol+timeframe+openTime).
     * Called by the market-data sync path when new bars arrive from a provider.
     */
    @Transactional
    public List<OhlcvBar> saveOrUpdateBars(String symbol, String timeframe,
                                            List<OhlcvBar> incoming) {
        String sym = symbol.toUpperCase();
        List<OhlcvBar> toSave = new ArrayList<>();
        for (OhlcvBar bar : incoming) {
            bar.setSymbol(sym);
            bar.setTimeframe(timeframe);
            barRepository.findBySymbolAndTimeframeAndOpenTime(sym, timeframe, bar.getOpenTime())
                    .ifPresent(existing -> bar.setId(existing.getId()));
            toSave.add(bar);
        }
        List<OhlcvBar> saved = barRepository.saveAll(toSave);
        log.debug("Upserted {} bars for {}/{}", saved.size(), sym, timeframe);
        return chronological(saved);
    }

    @Transactional
    public void deleteAllBars(String symbol, String timeframe) {
        barRepository.deleteBySymbolAndTimeframe(symbol.toUpperCase(), timeframe);
        log.info("Deleted all bars for {}/{}", symbol, timeframe);
    }

    // ── Staleness ─────────────────────────────────────────────────────────────

    public boolean isStale(List<OhlcvBar> bars, String timeframe) {
        if (bars == null || bars.isEmpty()) return false;
        OhlcvBar last = bars.get(bars.size() - 1);
        if (last.getOpenTime() == null) return false;
        Duration tfDuration = timeframeToDuration(timeframe);
        if (tfDuration == null) return false;
        long ageSeconds = Duration.between(last.getOpenTime(), LocalDateTime.now()).toSeconds();
        return ageSeconds > tfDuration.toSeconds() * 2;
    }

    private static Duration timeframeToDuration(String tf) {
        if (tf == null) return null;
        return switch (tf.toLowerCase()) {
            case "1m"  -> Duration.ofMinutes(1);
            case "5m"  -> Duration.ofMinutes(5);
            case "15m" -> Duration.ofMinutes(15);
            case "30m" -> Duration.ofMinutes(30);
            case "1h"  -> Duration.ofHours(1);
            case "4h"  -> Duration.ofHours(4);
            case "1d"  -> Duration.ofDays(1);
            case "1w"  -> Duration.ofDays(7);
            default    -> null;
        };
    }

    public static List<OhlcvBar> chronological(List<OhlcvBar> bars) {
        if (bars == null || bars.size() < 2) return bars == null ? List.of() : new ArrayList<>(bars);
        List<OhlcvBar> ordered = new ArrayList<>(bars);
        ordered.sort(Comparator.comparing(OhlcvBar::getOpenTime));
        return ordered;
    }
}
