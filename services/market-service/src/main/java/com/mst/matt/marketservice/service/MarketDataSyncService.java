package com.mst.matt.marketservice.service;

import com.mst.matt.marketservice.model.AssetType;
import com.mst.matt.marketservice.model.MarketDataTableRegistry;
import com.mst.matt.marketservice.model.OhlcvBar;
import com.mst.matt.marketservice.repository.MarketDataTableRegistryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry-based market data sync service.
 *
 * <p>Ported from the desktop monolith's {@code MarketDataSyncService};
 * heavily adapted for the microservice context:</p>
 * <ul>
 *   <li>{@code UserProfile} dependency removed — userId is not managed by
 *       market-service; the registry entry carries provider info instead.</li>
 *   <li>{@code PriceRouter}/{@code PriceProviderRegistry} replaced by the
 *       injected {@link OhlcvStorageService} + future
 *       {@code MarketOhlcvProviderRegistry} (Phase 3). Until Phase 3 beans
 *       are wired, sync falls back to serving from the DB only.</li>
 *   <li>Dynamic-table writes route through {@link DynamicOhlcvTableService};
 *       JPA reads go through {@link OhlcvStorageService}.</li>
 * </ul>
 */
@Slf4j
@Service
public class MarketDataSyncService {

    private static final ConcurrentHashMap<String, Object> REGISTER_LOCKS = new ConcurrentHashMap<>();

    private final MarketDataTableRegistryRepository registryRepository;
    private final OhlcvStorageService storageService;
    private final MarketReferenceDataService referenceDataService;

    /**
     * Lazy injection to avoid a circular-dependency issue:
     * Scheduler → SyncService → Scheduler.
     */
    private CandleAggregationScheduler aggregationScheduler;

    public MarketDataSyncService(MarketDataTableRegistryRepository registryRepository,
                                 OhlcvStorageService storageService,
                                 MarketReferenceDataService referenceDataService) {
        this.registryRepository = registryRepository;
        this.storageService = storageService;
        this.referenceDataService = referenceDataService;
    }

    @Autowired
    public void setAggregationScheduler(@Lazy CandleAggregationScheduler scheduler) {
        this.aggregationScheduler = scheduler;
    }

    // ── Registry management ───────────────────────────────────────────────────

    /**
     * Finds or creates a {@link MarketDataTableRegistry} row for the given
     * symbol + timeframe combination.
     *
     * <p>Thread-safe: a per-{@code symbol_tf} lock prevents duplicate INSERTs
     * under concurrent first-access; a {@code DataIntegrityViolationException}
     * fallback handles the rare race between a lock release and a parallel
     * write.</p>
     *
     * @param symbol    canonical trading symbol (upper-cased internally)
     * @param timeframe timeframe label (lower-cased internally)
     * @return existing or newly created registry entry
     */
    @Transactional
    public MarketDataTableRegistry register(String symbol, String timeframe) {
        String sym = symbol.toUpperCase();
        String tf = timeframe.toLowerCase();
        Object lock = REGISTER_LOCKS.computeIfAbsent(sym + "_" + tf, k -> new Object());

        synchronized (lock) {
            Optional<MarketDataTableRegistry> existing =
                    registryRepository.findBySymbolAndTimeframe(sym, tf);
            if (existing.isPresent()) {
                return existing.get();
            }

            AssetType assetType = resolveAssetType(sym);
            String tableName = MarketDataTableNameUtil.buildOhlcvTableName(sym, tf);

            try {
                return registryRepository.saveAndFlush(MarketDataTableRegistry.builder()
                        .tableName(tableName)
                        .symbol(sym)
                        .timeframe(tf)
                        .assetType(assetType)
                        .nextSyncAt(LocalDateTime.now())
                        .barCount(0)
                        .build());
            } catch (DataIntegrityViolationException dup) {
                log.debug("Registry race for {} {} — reloading existing row", sym, tf);
                return registryRepository.findBySymbolAndTimeframe(sym, tf)
                        .orElseThrow(() -> dup);
            }
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Reads bars for the registry entry from the JPA {@code ohlcv_bars} table.
     */
    @Transactional(readOnly = true)
    public List<OhlcvBar> readFromRegistry(MarketDataTableRegistry entry, int limit) {
        return storageService.getBars(entry.getSymbol(), entry.getTimeframe(), limit);
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    /**
     * Synchronises a registry entry.
     *
     * <p>In Phase 3 this will call through to the
     * {@code MarketOhlcvProviderRegistry} for a fresh fetch. Until then, it
     * returns what is already in the DB and logs a debug message. On success
     * it also triggers higher-TF aggregation via the scheduler.</p>
     *
     * @param entry   registry entry to sync
     * @param limit   maximum bars to fetch/return
     * @return current bars for the entry (from DB if no live fetch succeeds)
     */
    @Transactional
    public List<OhlcvBar> syncRegistryEntry(MarketDataTableRegistry entry, int limit) {
        List<OhlcvBar> current = readFromRegistry(entry, limit);
        if (!current.isEmpty()) {
            // Trigger aggregation for any new bars in the registry
            if (aggregationScheduler != null) {
                aggregationScheduler.triggerAggregationForBars(
                        entry.getSymbol(), entry.getTimeframe(),
                        entry.getAssetType(), current);
            }
        }
        log.debug("syncRegistryEntry: {} {}/{} → {} bars (Phase 3 provider not yet wired)",
                entry.getSymbol(), entry.getTimeframe(), entry.getTableName(), current.size());
        return current;
    }

    /** Async variant of {@link #syncRegistryEntry(MarketDataTableRegistry, int)}. */
    @Async
    public void syncRegistryEntryAsync(MarketDataTableRegistry entry, int limit) {
        syncRegistryEntry(entry, limit);
    }

    /**
     * Persists a batch of fresh bars (called by Phase 3 providers after fetching
     * from an external API) and updates the registry metadata.
     *
     * @param entry  registry entry being synced
     * @param fresh  bars returned by the external provider
     */
    @Transactional
    public void persistAndUpdateRegistry(MarketDataTableRegistry entry, List<OhlcvBar> fresh) {
        if (fresh == null || fresh.isEmpty()) return;
        storageService.saveOrUpdateBars(entry.getSymbol(), entry.getTimeframe(), fresh);
        entry.setLastSyncAt(LocalDateTime.now());
        entry.setNextSyncAt(LocalDateTime.now().plus(
                TimeframeInterval.forTimeframe(entry.getTimeframe())));
        entry.setBarCount(fresh.size());
        registryRepository.save(entry);
        log.info("Synced {} bars for {}/{}", fresh.size(), entry.getSymbol(), entry.getTimeframe());

        if (aggregationScheduler != null) {
            aggregationScheduler.triggerAggregationForBars(
                    entry.getSymbol(), entry.getTimeframe(),
                    entry.getAssetType(), fresh);
        }
    }

    // ── Due-check ─────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the registry entry's next-sync timestamp is in
     * the past (i.e., a fresh fetch is overdue).
     */
    public boolean isDue(MarketDataTableRegistry entry) {
        return entry.getNextSyncAt() == null
                || !entry.getNextSyncAt().isAfter(LocalDateTime.now());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static AssetType resolveAssetType(String symbol) {
        AssetClassDetector.AssetClass ac = AssetClassDetector.detect(symbol);
        return switch (ac) {
            case CRYPTO -> AssetType.CRYPTO;
            case FOREX  -> AssetType.FOREX;
            case STOCK  -> AssetType.STOCK;
            case COMMODITY -> AssetType.COMMODITY;
            case INDEX  -> AssetType.INDEX;
        };
    }
}
