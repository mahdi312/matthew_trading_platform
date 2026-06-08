package com.mst.matt.tradingplatformapp.service;

import com.mst.matt.tradingplatformapp.config.MarketDataProperties;
import com.mst.matt.tradingplatformapp.model.MarketDataTableRegistry;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.UserProfile;
import com.mst.matt.tradingplatformapp.repository.OhlcvBarRepository;
import com.mst.matt.tradingplatformapp.service.marketdata.MarketDataSyncService;
import com.mst.matt.tradingplatformapp.service.price.PriceRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OhlcvStorageService {

    private static final Logger log = LoggerFactory.getLogger(OhlcvStorageService.class);

    @Autowired private OhlcvBarRepository barRepository;
    @Autowired private PriceRouter priceRouter;
    @Autowired private MarketDataProperties marketDataProperties;
    @Autowired private MarketDataSyncService marketDataSyncService;
    @Autowired private AppSettingsService appSettings;

    @Transactional
    public List<OhlcvBar> getBars(String symbol, String timeframe, int limit) {
        return getBars(symbol, timeframe, limit, null);
    }

    @Transactional
    public List<OhlcvBar> getBars(String symbol, String timeframe, int limit, UserProfile profile) {
        if (marketDataProperties.getDynamicTables().isEnabled()) {
            return getBarsFromDynamicTables(symbol, timeframe, limit, profile);
        }
        return getBarsLegacy(symbol, timeframe, limit, profile);
    }

    private List<OhlcvBar> getBarsFromDynamicTables(String symbol, String timeframe,
                                                    int limit, UserProfile profile) {
        String sym = symbol.toUpperCase();
        MarketDataTableRegistry entry;
        try {
            entry = marketDataSyncService.register(sym, timeframe, profile);
        } catch (Exception e) {
            log.warn("Registry register failed for {} {}: {}", sym, timeframe, e.getMessage());
            return List.of();
        }
        List<OhlcvBar> cached = marketDataSyncService.readFromRegistry(entry, limit);

        if (cached.size() >= limit) {
            log.debug("Dynamic OHLCV hit: {} ({} bars)", entry.getTableName(), cached.size());
            if (marketDataSyncService.isDue(entry)) {
                marketDataSyncService.syncRegistryEntryAsync(entry, limit, profile);
            }
            return cached;
        }

        if (cached.isEmpty() && marketDataProperties.getSync().isBootstrapOnMiss()
                && appSettings.isApiFetchEnabled()) {
            log.info("Bootstrapping OHLCV table {} from API", entry.getTableName());
            return marketDataSyncService.syncRegistryEntry(entry, limit, profile);
        }

        if (marketDataSyncService.isDue(entry) && appSettings.isApiFetchEnabled()) {
            marketDataSyncService.syncRegistryEntryAsync(entry, limit, profile);
        }
        return cached;
    }

    private List<OhlcvBar> getBarsLegacy(String symbol, String timeframe, int limit, UserProfile profile) {
        String sym = symbol.toUpperCase();

        List<OhlcvBar> cached = chronological(barRepository
                .findTopBySymbolAndTimeframe(sym, timeframe, PageRequest.of(0, limit)));

        if (cached.size() >= limit) {
            log.debug("OHLCV cache hit: {} {} ({} bars)", sym, timeframe, cached.size());
            if (appSettings.isApiFetchEnabled()) {
                refreshFromApiAsync(sym, timeframe, limit, profile);
            }
            return cached;
        }

        if (!appSettings.isApiFetchEnabled()) {
            log.info("Offline mode — serving {} cached bars for {} {}", cached.size(), sym, timeframe);
            return cached;
        }

        log.info("Fetching OHLCV from API: {} {} {} bars", sym, timeframe, limit);
        List<OhlcvBar> merged = mergeFromApi(sym, timeframe, limit, profile, cached);
        return merged.isEmpty() ? cached : merged;
    }

    private void refreshFromApiAsync(String sym, String timeframe, int limit, UserProfile profile) {
        Thread.ofVirtual().start(() -> mergeFromApi(sym, timeframe, limit, profile,
                chronological(barRepository.findTopBySymbolAndTimeframe(
                        sym, timeframe, PageRequest.of(0, limit)))));
    }

    /** Merge API bars into DB keyed by {@code open_time}, then return latest {@code limit} bars. */
    private List<OhlcvBar> mergeFromApi(String sym, String timeframe, int limit,
                                        UserProfile profile, List<OhlcvBar> cached) {
        List<OhlcvBar> fresh = chronological(
                priceRouter.getOhlcv(sym, timeframe, limit, profile));
        if (fresh.isEmpty()) {
            return cached;
        }
        Map<LocalDateTime, OhlcvBar> byTime = new LinkedHashMap<>();
        cached.forEach(b -> byTime.put(b.getOpenTime(), b));
        for (OhlcvBar bar : fresh) {
            bar.setSymbol(sym);
            bar.setTimeframe(timeframe);
            barRepository.findBySymbolAndTimeframeAndOpenTime(sym, timeframe, bar.getOpenTime())
                    .ifPresent(existing -> bar.setId(existing.getId()));
            byTime.put(bar.getOpenTime(), bar);
        }
        List<OhlcvBar> merged = byTime.values().stream()
                .sorted(Comparator.comparing(OhlcvBar::getOpenTime))
                .toList();
        int from = Math.max(0, merged.size() - limit);
        List<OhlcvBar> trimmed = new ArrayList<>(merged.subList(from, merged.size()));
        barRepository.saveAll(trimmed);
        return trimmed;
    }

    @Transactional
    public List<OhlcvBar> refreshBars(String symbol, String timeframe, int limit) {
        return refreshBars(symbol, timeframe, limit, null);
    }

    @Transactional
    public List<OhlcvBar> refreshBars(String symbol, String timeframe, int limit, UserProfile profile) {
        if (!appSettings.isApiFetchEnabled()) {
            return getBars(symbol, timeframe, limit, profile);
        }
        if (marketDataProperties.getDynamicTables().isEnabled()) {
            String sym = symbol.toUpperCase();
            MarketDataTableRegistry entry = marketDataSyncService.register(sym, timeframe, profile);
            List<OhlcvBar> fresh = marketDataSyncService.syncRegistryEntry(entry, limit, profile);
            if (!fresh.isEmpty()) {
                return fresh;
            }
            return marketDataSyncService.readFromRegistry(entry, limit);
        }

        String sym = symbol.toUpperCase();
        List<OhlcvBar> cached = chronological(barRepository
                .findTopBySymbolAndTimeframe(sym, timeframe, PageRequest.of(0, limit)));
        List<OhlcvBar> merged = mergeFromApi(sym, timeframe, limit, profile, cached);
        if (!merged.isEmpty()) {
            return merged;
        }
        log.warn("Refresh failed for {} {} — keeping previously cached bars", sym, timeframe);
        return cached;
    }

    public static List<OhlcvBar> chronological(List<OhlcvBar> bars) {
        if (bars == null || bars.size() < 2) {
            return bars == null ? List.of() : new ArrayList<>(bars);
        }
        List<OhlcvBar> ordered = new ArrayList<>(bars);
        ordered.sort(java.util.Comparator.comparing(OhlcvBar::getOpenTime));
        return ordered;
    }
}
