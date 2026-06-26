package com.mst.matt.tradingplatformapp.service;

import com.mst.matt.tradingplatformapp.config.MarketDataProperties;
import com.mst.matt.tradingplatformapp.model.MarketDataTableRegistry;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade;
import com.mst.matt.tradingplatformapp.model.UserProfile;
import com.mst.matt.tradingplatformapp.repository.OhlcvBarRepository;
import com.mst.matt.tradingplatformapp.service.marketdata.AggregatedCandleQueryService;
import com.mst.matt.tradingplatformapp.service.marketdata.MarketDataSyncService;
import com.mst.matt.tradingplatformapp.service.price.AssetClassDetector;
import com.mst.matt.tradingplatformapp.service.price.MarketDataProvider;
import com.mst.matt.tradingplatformapp.service.price.PriceRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OhlcvStorageService {

    private static final Logger log = LoggerFactory.getLogger(OhlcvStorageService.class);

    @Autowired private OhlcvBarRepository barRepository;
    @Autowired private PriceRouter priceRouter;
    @Autowired private MarketDataProperties marketDataProperties;
    @Autowired private MarketDataSyncService marketDataSyncService;
    @Autowired private AppSettingsService appSettings;
    @Autowired private AggregatedCandleQueryService aggregatedCandleQueryService;

    /**
     * In-memory cache: key = symbol + "_" + timeframe → last provider used.
     * Used to detect provider switches and force cache invalidation.
     */
    private final ConcurrentHashMap<String, String> lastProviderCache = new ConcurrentHashMap<>();

    /**
     * Staleness warning callback — injected by ChartController so we can show
     * a status-bar warning without creating a circular Spring dependency.
     */
    private java.util.function.BiConsumer<String, String> onStaleDataWarning;

    /** Allow the UI layer to register a staleness-warning callback. */
    public void setOnStaleDataWarning(java.util.function.BiConsumer<String, String> callback) {
        this.onStaleDataWarning = callback;
    }

    /**
     * Clears the in-memory staleness/provider cache entry for a given symbol+timeframe.
     * Call this when the user explicitly switches data providers so the next
     * {@link #getBars} call fetches fresh data from the new provider.
     */
    public void invalidateCache(String symbol, String timeframe) {
        String key = symbol.toUpperCase() + "_" + timeframe;
        lastProviderCache.remove(key);
        log.info("Provider cache invalidated for {}/{}", symbol, timeframe);
    }

    /**
     * Returns {@code true} if the last candle in {@code bars} is older than
     * {@code 2 × timeframe-duration}.  Used to detect stale/offline datasets.
     */
    public boolean isStale(List<OhlcvBar> bars, String timeframe) {
        if (bars == null || bars.isEmpty()) return false;
        OhlcvBar last = bars.get(bars.size() - 1);
        if (last.getOpenTime() == null) return false;
        Duration tfDuration = timeframeToDuration(timeframe);
        if (tfDuration == null) return false;
        long ageSeconds = Duration.between(last.getOpenTime(), LocalDateTime.now()).toSeconds();
        return ageSeconds > tfDuration.toSeconds() * 2;
    }

    /** Maps a timeframe string to its expected candle interval {@link Duration}. */
    private static Duration timeframeToDuration(String tf) {
        if (tf == null) return null;
        return switch (tf.toLowerCase()) {
            case "1m"  -> Duration.ofMinutes(1);
            case "3m"  -> Duration.ofMinutes(3);
            case "5m"  -> Duration.ofMinutes(5);
            case "15m" -> Duration.ofMinutes(15);
            case "30m" -> Duration.ofMinutes(30);
            case "1h"  -> Duration.ofHours(1);
            case "2h"  -> Duration.ofHours(2);
            case "4h"  -> Duration.ofHours(4);
            case "6h"  -> Duration.ofHours(6);
            case "8h"  -> Duration.ofHours(8);
            case "12h" -> Duration.ofHours(12);
            case "1d"  -> Duration.ofDays(1);
            case "3d"  -> Duration.ofDays(3);
            case "1w"  -> Duration.ofDays(7);
            case "1mo" -> Duration.ofDays(30);
            default    -> null;
        };
    }

    @Transactional
    public List<OhlcvBar> getBars(String symbol, String timeframe, int limit) {
        return getBars(symbol, timeframe, limit, null);
    }

    @Transactional
    public List<OhlcvBar> getBars(String symbol, String timeframe, int limit, UserProfile profile) {
        // ── Provider-switch detection: invalidate registry cache when provider changes ──
        String providerKey = profile != null ? profile.getChartProvider() : "AUTO";
        String cacheKey    = symbol.toUpperCase() + "_" + timeframe;
        String lastProvider = lastProviderCache.get(cacheKey);
        if (lastProvider != null && !lastProvider.equalsIgnoreCase(providerKey)) {
            log.info("Provider switched from {} to {} for {}/{} — invalidating cache",
                    lastProvider, providerKey, symbol, timeframe);
            invalidateCache(symbol, timeframe);
        }
        lastProviderCache.put(cacheKey, providerKey != null ? providerKey : "AUTO");

        List<OhlcvBar> bars;
        if (marketDataProperties.getDynamicTables().isEnabled()) {
            bars = getBarsFromDynamicTables(symbol, timeframe, limit, profile);
        } else {
            bars = getBarsLegacy(symbol, timeframe, limit, profile);
        }

        // ── Staleness check: warn if last candle is older than 2× timeframe duration ──
        if (isStale(bars, timeframe) && onStaleDataWarning != null) {
            OhlcvBar lastBar = bars.get(bars.size() - 1);
            String providerLabel = providerKey != null ? providerKey : "Unknown";
            String warningMsg = "Data from " + providerLabel + " may be stale — last candle: "
                    + (lastBar.getOpenTime() != null
                        ? lastBar.getOpenTime().toString().replace("T", " ")
                        : "unknown");
            log.warn("Stale OHLCV data for {}/{}: {}", symbol, timeframe, warningMsg);
            onStaleDataWarning.accept(symbol + "/" + timeframe, warningMsg);
        }

        return bars;
    }

    private List<OhlcvBar> getBarsFromDynamicTables(String symbol, String timeframe,
                                                    int limit, UserProfile profile) {
        String sym = symbol.toUpperCase();
        MarketDataTableRegistry entry;
        try {
            entry = marketDataSyncService.register(sym, timeframe, profile);
        } catch (Exception e) {
            log.warn("Registry register failed for {} {}: {}", sym, timeframe, e.getMessage());
            // Try offline aggregated data before giving up
            return tryAggregatedFallback(sym, timeframe, profile, limit);
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
            List<OhlcvBar> synced = marketDataSyncService.syncRegistryEntry(entry, limit, profile);
            if (!synced.isEmpty()) return synced;
            // Last resort: aggregated offline data
            return tryAggregatedFallback(sym, timeframe, profile, limit);
        }

        if (marketDataSyncService.isDue(entry) && appSettings.isApiFetchEnabled()) {
            marketDataSyncService.syncRegistryEntryAsync(entry, limit, profile);
        }

        // If cached is empty and API fetch is disabled (offline), try pre-aggregated data
        if (cached.isEmpty() && !appSettings.isApiFetchEnabled()) {
            List<OhlcvBar> agg = tryAggregatedFallback(sym, timeframe, profile, limit);
            if (!agg.isEmpty()) {
                log.info("Offline mode — serving {} aggregated bars for {}/{}", agg.size(), sym, timeframe);
                return agg;
            }
        }
        return cached;
    }

    /**
     * Tries to serve bars from the pre-aggregated offline tables.
     * Detects the provider from the registry or falls back to an empty provider segment.
     */
    private List<OhlcvBar> tryAggregatedFallback(String symbol, String timeframe,
                                                  UserProfile profile, int limit) {
        try {
            // Determine asset type
            AssetClassDetector.AssetClass ac = profile != null
                    ? AssetClassDetector.fromProfileFocus(profile.getAssetFocus(), symbol)
                    : AssetClassDetector.detect(symbol);
            Trade.AssetType assetType = switch (ac) {
                case CRYPTO    -> Trade.AssetType.CRYPTO;
                case FOREX     -> Trade.AssetType.FOREX;
                case COMMODITY -> Trade.AssetType.COMMODITY;
                case INDEX     -> Trade.AssetType.INDEX;
                default        -> Trade.AssetType.STOCK;
            };

            // Try with known providers first (BINANCE is common for crypto)
            for (MarketDataProvider provider : MarketDataProvider.values()) {
                if (provider == MarketDataProvider.AUTO) continue;
                List<OhlcvBar> bars = aggregatedCandleQueryService.fetchAggregated(
                        symbol, provider.name(), timeframe, assetType, limit);
                if (!bars.isEmpty()) return bars;
            }
            // Generic agg table
            return aggregatedCandleQueryService.fetchAggregated(
                    symbol, "", timeframe, assetType, limit);
        } catch (Exception ex) {
            log.debug("Aggregated fallback failed for {}/{}: {}", symbol, timeframe, ex.getMessage());
            return List.of();
        }
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
        ordered.sort(Comparator.comparing(OhlcvBar::getOpenTime));
        return ordered;
    }
}
