package com.mst.matt.marketservice.service;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import com.mst.matt.marketservice.model.AssetType;
import com.mst.matt.marketservice.model.MarketDataTableRegistry;
import com.mst.matt.marketservice.model.OhlcvBar;
import com.mst.matt.marketservice.registry.MarketOhlcvProviderRegistry;
import com.mst.matt.marketservice.repository.MarketDataTableRegistryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
 *       {@link MarketOhlcvProviderRegistry} (Gap 1): sync now calls through
 *       the full fallback chain (Binance → CoinGecko → … for crypto, etc.)
 *       before falling back to serving from DB only when all providers fail.</li>
 *   <li>Dynamic-table writes route through {@link DynamicOhlcvTableService};
 *       JPA reads go through {@link OhlcvStorageService}.</li>
 * </ul>
 */
@Slf4j
@Service
public class MarketDataSyncService {

    private static final ConcurrentHashMap<String, Object> REGISTER_LOCKS = new ConcurrentHashMap<>();

    private final MarketDataTableRegistryRepository registryRepository;
    private final OhlcvStorageService               storageService;
    private final MarketReferenceDataService         referenceDataService;
    private final MarketOhlcvProviderRegistry        providerRegistry;

    /**
     * Lazy injection to avoid a circular-dependency issue:
     * Scheduler → SyncService → Scheduler.
     */
    private CandleAggregationScheduler aggregationScheduler;

    public MarketDataSyncService(MarketDataTableRegistryRepository registryRepository,
                                 OhlcvStorageService storageService,
                                 MarketReferenceDataService referenceDataService,
                                 MarketOhlcvProviderRegistry providerRegistry) {
        this.registryRepository = registryRepository;
        this.storageService      = storageService;
        this.referenceDataService = referenceDataService;
        this.providerRegistry    = providerRegistry;
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
     * Synchronises a registry entry by calling the external provider chain
     * when the DB is empty or the entry is due for a refresh.
     *
     * <p>Read path:</p>
     * <ol>
     *   <li>Read existing bars from storage.</li>
     *   <li>If storage is empty <em>or</em> the entry is due for sync,
     *       call {@link MarketOhlcvProviderRegistry} with the entry's asset class
     *       and interval — this triggers the full fallback chain
     *       (Binance → CoinGecko → … for crypto, etc.).</li>
     *   <li>On successful fetch, persist via
     *       {@link #persistAndUpdateRegistry(MarketDataTableRegistry, List)}.
     *       On failure, return whatever is already in DB.</li>
     *   <li>Trigger higher-TF aggregation via the scheduler.</li>
     * </ol>
     *
     * @param entry   registry entry to sync
     * @param limit   maximum bars to fetch/return
     * @return up-to-date bars for the entry
     */
    @Transactional
    public List<OhlcvBar> syncRegistryEntry(MarketDataTableRegistry entry, int limit) {
        List<OhlcvBar> current = readFromRegistry(entry, limit);

        // Attempt live fetch when storage empty or sync is overdue
        if (current.isEmpty() || isDue(entry)) {
            List<OhlcvBar> fresh = fetchFromProvider(entry, limit);
            if (!fresh.isEmpty()) {
                persistAndUpdateRegistry(entry, fresh);
                current = fresh;
            } else {
                log.debug("syncRegistryEntry: provider returned empty for {}/{} — serving from DB",
                        entry.getSymbol(), entry.getTimeframe());
            }
        }

        if (!current.isEmpty() && aggregationScheduler != null) {
            aggregationScheduler.triggerAggregationForBars(
                    entry.getSymbol(), entry.getTimeframe(),
                    entry.getAssetType(), current);
        }

        log.debug("syncRegistryEntry: {}/{} → {} bars",
                entry.getSymbol(), entry.getTimeframe(), current.size());
        return current;
    }

    /**
     * Fetches fresh bars from the {@link MarketOhlcvProviderRegistry} for the
     * given registry entry. Maps {@link NormalizedOhlcvBar} → {@link OhlcvBar}.
     * Returns empty list on provider failure (never throws).
     */
    private List<OhlcvBar> fetchFromProvider(MarketDataTableRegistry entry, int limit) {
        try {
            AssetClass assetClass = toContractsAssetClass(entry.getAssetType());
            List<NormalizedOhlcvBar> normalized = providerRegistry.getHistoricalBars(
                    entry.getSymbol(), assetClass, entry.getTimeframe(), limit);
            if (normalized == null || normalized.isEmpty()) return List.of();
            return normalized.stream()
                    .map(n -> toOhlcvBar(n, entry.getSymbol(), entry.getTimeframe(), entry.getAssetType()))
                    .toList();
        } catch (Exception e) {
            log.warn("Provider fetch failed for {}/{}: {}",
                    entry.getSymbol(), entry.getTimeframe(), e.getMessage());
            return List.of();
        }
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
            case CRYPTO    -> AssetType.CRYPTO;
            case FOREX     -> AssetType.FOREX;
            case STOCK     -> AssetType.STOCK;
            case COMMODITY -> AssetType.COMMODITY;
            case INDEX     -> AssetType.INDEX;
        };
    }

    /** Maps the local {@link AssetType} to the contracts {@link AssetClass} for provider routing. */
    private static AssetClass toContractsAssetClass(AssetType at) {
        return switch (at) {
            case CRYPTO    -> AssetClass.CRYPTO;
            case FOREX     -> AssetClass.FOREX;
            case STOCK,
                 COMMODITY,
                 INDEX      -> AssetClass.STOCK;
        };
    }

    /** Maps a {@link NormalizedOhlcvBar} to an {@link OhlcvBar} JPA entity. */
    private static OhlcvBar toOhlcvBar(NormalizedOhlcvBar n,
                                        String symbol, String timeframe, AssetType assetType) {
        java.time.LocalDateTime openTime = n.getOpenTime() != null
                ? java.time.LocalDateTime.ofInstant(n.getOpenTime(), ZoneOffset.UTC)
                : java.time.LocalDateTime.now(ZoneOffset.UTC);
        return OhlcvBar.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .openTime(openTime)
                .open(nvl(n.getOpen()))
                .high(nvl(n.getHigh()))
                .low(nvl(n.getLow()))
                .close(nvl(n.getClose()))
                .volume(nvl(n.getVolume()))
                .assetType(assetType)
                .provider(n.getProviderName())
                .build();
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
