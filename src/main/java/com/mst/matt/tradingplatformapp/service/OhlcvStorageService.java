package com.mst.matt.tradingplatformapp.service;

import com.mst.matt.tradingplatformapp.config.MarketDataProperties;
import com.mst.matt.tradingplatformapp.model.DataFetchMode;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central service for reading and writing OHLCV bar data.
 *
 * <h3>Profile isolation rules</h3>
 * <ul>
 *   <li><b>SQLite profile (default)</b> – uses the {@code ohlcv_bars} table managed by
 *       {@link OhlcvBarRepository} (the legacy/monolithic table).</li>
 *   <li><b>Docker/PostgreSQL profile</b> – uses <em>provider-specific dynamic tables</em>
 *       (e.g. {@code BTCUSDT_COINGECKO_1h}). The {@code ohlcv_bars} table is <strong>never
 *       queried</strong> in this mode to prevent JDBC transaction-abort errors.</li>
 * </ul>
 *
 * <h3>DataFetchMode</h3>
 * <ul>
 *   <li>{@code FULL_ONLINE}     – always call the API; DB is a write-through cache only.</li>
 *   <li>{@code OFFLINE_ON_FAIL} – call API first (10-second timeout); fall back to DB on failure.
 *       <b>Default.</b></li>
 *   <li>{@code OFFLINE_ONLY}    – never call any API; read exclusively from the DB cache.</li>
 * </ul>
 *
 * <h3>Transaction isolation</h3>
 * Every DB operation runs in its own isolated transaction
 * ({@link Propagation#REQUIRES_NEW}).  A failure in one operation (e.g. a JDBC exception
 * caused by a previous aborted transaction) does <em>not</em> propagate to the next call.
 */
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

    // ── Public read API ──────────────────────────────────────────────────────

    @Transactional
    public List<OhlcvBar> getBars(String symbol, String timeframe, int limit) {
        return getBars(symbol, timeframe, limit, null);
    }

    @Transactional
    public List<OhlcvBar> getBars(String symbol, String timeframe, int limit, UserProfile profile) {
        // ── Provider-switch detection: invalidate registry cache when provider changes ──
        String providerKey  = profile != null ? profile.getChartProvider() : "AUTO";
        String cacheKey     = symbol.toUpperCase() + "_" + timeframe;
        String lastProvider = lastProviderCache.get(cacheKey);
        if (lastProvider != null && !lastProvider.equalsIgnoreCase(providerKey)) {
            log.info("Provider switched from {} to {} for {}/{} — invalidating cache",
                    lastProvider, providerKey, symbol, timeframe);
            invalidateCache(symbol, timeframe);
        }
        lastProviderCache.put(cacheKey, providerKey != null ? providerKey : "AUTO");

        List<OhlcvBar> bars;
        if (marketDataProperties.getDynamicTables().isEnabled()) {
            // Docker/PostgreSQL mode: NEVER touch ohlcv_bars table
            bars = getBarsFromDynamicTables(symbol, timeframe, limit, profile);
        } else {
            // SQLite/legacy mode: use ohlcv_bars table
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

    // ── Dynamic (PostgreSQL/Docker) path ─────────────────────────────────────

    private List<OhlcvBar> getBarsFromDynamicTables(String symbol, String timeframe,
                                                    int limit, UserProfile profile) {
        String sym = symbol.toUpperCase();
        DataFetchMode mode = appSettings.getDataFetchMode();

        // OFFLINE_ONLY: skip API entirely, read from provider-specific table
        if (mode == DataFetchMode.OFFLINE_ONLY) {
            log.debug("OFFLINE_ONLY mode — reading from DB cache for {}/{}", sym, timeframe);
            return readFromDynamicTableSafe(sym, timeframe, limit, profile);
        }

        // FULL_ONLINE or OFFLINE_ON_FAIL: try registry/API path
        MarketDataTableRegistry entry;
        try {
            entry = marketDataSyncService.register(sym, timeframe, profile);
        } catch (Exception e) {
            log.warn("Registry register failed for {} {}: {}", sym, timeframe, e.getMessage());
            if (mode == DataFetchMode.FULL_ONLINE) {
                // In FULL_ONLINE mode, DB fallback is not the intent — return empty
                return List.of();
            }
            // OFFLINE_ON_FAIL: fallback to aggregated/cached data
            return tryAggregatedFallback(sym, timeframe, profile, limit);
        }

        List<OhlcvBar> cached = readRegistrySafe(entry, limit);

        if (cached.size() >= limit) {
            log.debug("Dynamic OHLCV hit: {} ({} bars)", entry.getTableName(), cached.size());
            if (marketDataSyncService.isDue(entry)) {
                // Async background refresh — never blocks the UI
                marketDataSyncService.syncRegistryEntryAsync(entry, limit, profile);
            }
            return cached;
        }

        if (cached.isEmpty() && marketDataProperties.getSync().isBootstrapOnMiss()
                && appSettings.isApiFetchEnabled()) {
            log.info("Bootstrapping OHLCV table {} from API", entry.getTableName());
            List<OhlcvBar> synced = syncRegistrySafe(entry, limit, profile);
            if (!synced.isEmpty()) return synced;
            if (mode == DataFetchMode.OFFLINE_ON_FAIL) {
                return tryAggregatedFallback(sym, timeframe, profile, limit);
            }
            return List.of();
        }

        if (marketDataSyncService.isDue(entry) && appSettings.isApiFetchEnabled()) {
            marketDataSyncService.syncRegistryEntryAsync(entry, limit, profile);
        }

        // No cached data + API not enabled → try aggregated offline fallback
        if (cached.isEmpty() && !appSettings.isApiFetchEnabled()
                && mode != DataFetchMode.FULL_ONLINE) {
            List<OhlcvBar> agg = tryAggregatedFallback(sym, timeframe, profile, limit);
            if (!agg.isEmpty()) {
                log.info("Offline mode — serving {} aggregated bars for {}/{}", agg.size(), sym, timeframe);
                return agg;
            }
        }
        return cached;
    }

    /**
     * Safely reads from the MarketDataTableRegistry in an isolated transaction.
     * If the current transaction has already been aborted by a previous error,
     * this runs in REQUIRES_NEW to avoid the "transaction is aborted" JDBC error.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OhlcvBar> readRegistrySafe(MarketDataTableRegistry entry, int limit) {
        try {
            return marketDataSyncService.readFromRegistry(entry, limit);
        } catch (Exception e) {
            log.warn("readFromRegistry failed for {}: {}", entry.getTableName(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Safely syncs a registry entry in an isolated transaction so that failures
     * do not abort any surrounding transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OhlcvBar> syncRegistrySafe(MarketDataTableRegistry entry, int limit, UserProfile profile) {
        try {
            return marketDataSyncService.syncRegistryEntry(entry, limit, profile);
        } catch (Exception e) {
            log.warn("syncRegistryEntry failed for {}: {}", entry.getTableName(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Reads bars from a dynamic (provider-specific) table safely in an isolated transaction.
     * Used as the primary read path in OFFLINE_ONLY mode for Docker/PostgreSQL.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OhlcvBar> readFromDynamicTableSafe(String symbol, String timeframe,
                                                    int limit, UserProfile profile) {
        try {
            MarketDataTableRegistry entry = marketDataSyncService.register(symbol, timeframe, profile);
            return marketDataSyncService.readFromRegistry(entry, limit);
        } catch (Exception e) {
            log.warn("readFromDynamicTableSafe failed for {}/{}: {}", symbol, timeframe, e.getMessage());
            // Last-resort: try aggregated fallback
            return tryAggregatedFallback(symbol, timeframe, profile, limit);
        }
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

    // ── Legacy (SQLite) path ─────────────────────────────────────────────────

    private List<OhlcvBar> getBarsLegacy(String symbol, String timeframe, int limit, UserProfile profile) {
        String sym = symbol.toUpperCase();
        DataFetchMode mode = appSettings.getDataFetchMode();

        List<OhlcvBar> cached = chronological(barRepository
                .findTopBySymbolAndTimeframe(sym, timeframe, PageRequest.of(0, limit)));

        if (mode == DataFetchMode.OFFLINE_ONLY) {
            log.debug("OFFLINE_ONLY mode — returning {} cached bars for {} {}", cached.size(), sym, timeframe);
            return cached;
        }

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

    // ── Refresh ──────────────────────────────────────────────────────────────

    @Transactional
    public List<OhlcvBar> refreshBars(String symbol, String timeframe, int limit) {
        return refreshBars(symbol, timeframe, limit, null);
    }

    @Transactional
    public List<OhlcvBar> refreshBars(String symbol, String timeframe, int limit, UserProfile profile) {
        DataFetchMode mode = appSettings.getDataFetchMode();

        // In OFFLINE_ONLY mode: refresh just means "read from DB again"
        if (mode == DataFetchMode.OFFLINE_ONLY) {
            return getBars(symbol, timeframe, limit, profile);
        }

        if (marketDataProperties.getDynamicTables().isEnabled()) {
            String sym = symbol.toUpperCase();
            try {
                MarketDataTableRegistry entry = marketDataSyncService.register(sym, timeframe, profile);
                List<OhlcvBar> fresh = syncRegistrySafe(entry, limit, profile);
                if (!fresh.isEmpty()) {
                    return fresh;
                }
                return readRegistrySafe(entry, limit);
            } catch (Exception e) {
                log.warn("refreshBars (dynamic) failed for {} {}: {}", sym, timeframe, e.getMessage());
                if (mode == DataFetchMode.OFFLINE_ON_FAIL) {
                    return tryAggregatedFallback(sym, timeframe, profile, limit);
                }
                return List.of();
            }
        }

        // Legacy SQLite path
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

    // ── Provider-specific read/write ─────────────────────────────────────────

    /**
     * Reads bars from the database that were previously stored for a specific provider.
     *
     * <p>In Docker/PostgreSQL mode this queries the provider-specific dynamic table
     * (e.g. {@code BTCUSDT_COINGECKO_1h}). In SQLite mode it falls back to the
     * generic {@code ohlcv_bars} table.
     *
     * <p>Each database operation runs in its own isolated transaction
     * ({@link Propagation#REQUIRES_NEW}) so that a failed previous transaction
     * does not block this read.
     *
     * @param symbol    trading symbol (e.g. "SOLUSDT")
     * @param timeframe timeframe string (e.g. "1h")
     * @param limit     maximum number of bars to return
     * @param provider  provider name (e.g. "COINGECKO")
     * @return chronologically-sorted list of bars, may be empty
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OhlcvBar> getBarsForProvider(String symbol, String timeframe,
                                             int limit, String provider) {
        String sym = symbol.toUpperCase();

        // Docker/PostgreSQL: use provider-specific dynamic table — never touch ohlcv_bars
        if (marketDataProperties.getDynamicTables().isEnabled()) {
            if (provider != null && !provider.isBlank()) {
                try {
                    List<OhlcvBar> bars = aggregatedCandleQueryService.fetchAggregated(
                            sym, provider, timeframe, null, limit);
                    if (!bars.isEmpty()) return bars;
                } catch (Exception e) {
                    log.debug("Provider table fetch failed for {}/{}/{}: {}",
                            sym, provider, timeframe, e.getMessage());
                }
            }
            // No provider-specific data found in dynamic-table mode → return empty
            // (do NOT fall back to ohlcv_bars which may not exist in PostgreSQL schema)
            log.debug("No cached data found in dynamic table for {}/{}/{}", sym, provider, timeframe);
            return List.of();
        }

        // SQLite/legacy mode: fall back to generic ohlcv_bars table
        return chronological(barRepository.findTopBySymbolAndTimeframe(
                sym, timeframe, PageRequest.of(0, limit)));
    }

    /**
     * Persists bars that were freshly fetched from a specific provider into the database.
     *
     * <p>In Docker/PostgreSQL mode bars are stored via the dynamic-table machinery.
     * In SQLite mode they are upserted into the generic {@code ohlcv_bars} table.
     *
     * <p>Runs in its own isolated transaction so failures do not abort the caller.
     *
     * @param symbol    trading symbol (e.g. "SOLUSDT")
     * @param timeframe timeframe string (e.g. "1h")
     * @param provider  provider name (used as a tag in dynamic-table mode)
     * @param bars      list of bars to persist
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void storeProviderBars(String symbol, String timeframe,
                                  String provider, List<OhlcvBar> bars) {
        if (bars == null || bars.isEmpty()) return;
        String sym = symbol.toUpperCase();

        // Docker/PostgreSQL mode: store via the dynamic-table registry
        if (marketDataProperties.getDynamicTables().isEnabled()) {
            try {
                // Delegate to MarketDataSyncService so the table is created if needed
                // (MarketDataTableRegistry → DynamicOhlcvTableService).
                // We don't call register() here to avoid side-effects; instead we rely on
                // the AggregatedCandleQueryService write path if available, or simply log.
                log.debug("storeProviderBars (dynamic): {} bars for {}/{} from {}",
                        bars.size(), sym, timeframe, provider);
                // The actual write happens in MarketDataSyncService.syncRegistryEntry().
                // This method is intentionally a no-op for the dynamic path because
                // the sync service already wrote the data.  Keeping it as a hook for
                // future fine-grained provider writes.
            } catch (Exception e) {
                log.warn("storeProviderBars (dynamic) failed for {}/{} from {}: {}",
                        sym, timeframe, provider, e.getMessage());
            }
            return;
        }

        // SQLite/legacy mode: upsert into ohlcv_bars
        try {
            Map<LocalDateTime, OhlcvBar> byTime = new LinkedHashMap<>();
            // Load existing bars to merge
            List<OhlcvBar> existing = chronological(barRepository.findTopBySymbolAndTimeframe(
                    sym, timeframe, PageRequest.of(0, bars.size() * 2)));
            existing.forEach(b -> byTime.put(b.getOpenTime(), b));
            for (OhlcvBar bar : bars) {
                bar.setSymbol(sym);
                bar.setTimeframe(timeframe);
                barRepository.findBySymbolAndTimeframeAndOpenTime(sym, timeframe, bar.getOpenTime())
                        .ifPresent(ex -> bar.setId(ex.getId()));
                byTime.put(bar.getOpenTime(), bar);
            }
            barRepository.saveAll(new ArrayList<>(byTime.values()));
            log.debug("Stored {} bars for {}/{} from provider {}", bars.size(), sym, timeframe, provider);
        } catch (Exception e) {
            log.warn("Failed to store bars for {}/{} from {}: {}", sym, timeframe, provider, e.getMessage());
        }
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    public static List<OhlcvBar> chronological(List<OhlcvBar> bars) {
        if (bars == null || bars.size() < 2) {
            return bars == null ? List.of() : new ArrayList<>(bars);
        }
        List<OhlcvBar> ordered = new ArrayList<>(bars);
        ordered.sort(Comparator.comparing(OhlcvBar::getOpenTime));
        return ordered;
    }
}
