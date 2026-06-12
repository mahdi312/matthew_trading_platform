package com.mst.matt.tradingplatformapp.service.marketdata;

import com.mst.matt.tradingplatformapp.config.MarketDataProperties;
import com.mst.matt.tradingplatformapp.model.MarketDataTableRegistry;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade;
import com.mst.matt.tradingplatformapp.model.UserProfile;
import com.mst.matt.tradingplatformapp.repository.MarketDataTableRegistryRepository;
import com.mst.matt.tradingplatformapp.service.price.*;
import com.mst.matt.tradingplatformapp.service.price.AssetClassDetector.AssetClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MarketDataSyncService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataSyncService.class);
    private static final ConcurrentHashMap<String, Object> REGISTER_LOCKS = new ConcurrentHashMap<>();

    private final MarketDataTableRegistryRepository registryRepository;
    private final DynamicOhlcvTableService dynamicTableService;
    private final PriceProviderRegistry providerRegistry;
    private final MarketReferenceDataService referenceDataService;
    private final PriceRouter priceRouter;
    private final MarketDataProperties properties;
    // Lazy injection to avoid circular dependency (Scheduler → SyncService → Scheduler)
    private CandleAggregationScheduler aggregationScheduler;

    public MarketDataSyncService(MarketDataTableRegistryRepository registryRepository,
                                 DynamicOhlcvTableService dynamicTableService,
                                 PriceProviderRegistry providerRegistry,
                                 MarketReferenceDataService referenceDataService,
                                 PriceRouter priceRouter,
                                 MarketDataProperties properties) {
        this.registryRepository = registryRepository;
        this.dynamicTableService = dynamicTableService;
        this.providerRegistry = providerRegistry;
        this.referenceDataService = referenceDataService;
        this.priceRouter = priceRouter;
        this.properties = properties;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setAggregationScheduler(
            @org.springframework.context.annotation.Lazy CandleAggregationScheduler scheduler) {
        this.aggregationScheduler = scheduler;
    }

    @Transactional
    public MarketDataTableRegistry register(String symbol, String timeframe, UserProfile profile) {
        String sym = symbol.toUpperCase();
        String tf = timeframe.toLowerCase();
        Object lock = REGISTER_LOCKS.computeIfAbsent(sym + "_" + tf, k -> new Object());

        synchronized (lock) {
            AssetClass assetClass = profile != null
                    ? AssetClassDetector.fromProfileFocus(profile.getAssetFocus(), sym)
                    : AssetClassDetector.detect(sym);
            Trade.AssetType assetType = toAssetType(assetClass);

            List<PriceService> chain = providerRegistry.chainFor(sym, profile);
            if (chain.isEmpty()) {
                throw new IllegalStateException("No enabled provider for " + sym);
            }
            PriceService primary = chain.getFirst();
            MarketDataProvider provider = primary.getProviderId();

            Optional<MarketDataTableRegistry> existing =
                    registryRepository.findBySymbolAndTimeframeAndProvider(sym, tf, provider);
            if (existing.isPresent()) {
                return existing.get();
            }
            // Fallback: check any existing row for this symbol+timeframe (different provider)
            List<MarketDataTableRegistry> allForPair =
                    registryRepository.findAllBySymbolAndTimeframe(sym, tf);
            if (!allForPair.isEmpty()) {
                return allForPair.get(0);
            }

            // Use provider-qualified table name to avoid collisions
            String tableName = MarketDataTableNameUtil.buildTableName(sym, provider, tf);

            dynamicTableService.ensureTable(tableName);
            try {
                return registryRepository.saveAndFlush(MarketDataTableRegistry.builder()
                        .tableName(tableName)
                        .symbol(sym)
                        .provider(provider)
                        .timeframe(tf)
                        .assetType(assetType)
                        .nextSyncAt(LocalDateTime.now())
                        .barCount(0)
                        .build());
            } catch (DataIntegrityViolationException dup) {
                log.debug("Registry race for {} {} — reloading existing row", sym, tf);
                return registryRepository.findBySymbolAndTimeframe(sym, tf)
                        .or(() -> registryRepository.findBySymbolAndTimeframeAndProvider(sym, tf, provider))
                        .orElseThrow(() -> dup);
            }
        }
    }

    public List<OhlcvBar> readFromRegistry(MarketDataTableRegistry entry, int limit) {
        return dynamicTableService.findBars(
                entry.getTableName(), entry.getSymbol(), entry.getTimeframe(),
                entry.getAssetType(), limit);
    }

    @Transactional
    public List<OhlcvBar> syncRegistryEntry(MarketDataTableRegistry entry, int limit, UserProfile profile) {
        String sym = entry.getSymbol();
        Optional<PriceService> providerOpt = providerRegistry.get(entry.getProvider());
        if (providerOpt.isEmpty()) {
            log.warn("Provider {} not available for {}", entry.getProvider(), sym);
            return readFromRegistry(entry, limit);
        }

        PriceService provider = providerOpt.get();
        List<OhlcvBar> fresh;
        try {
            fresh = provider.getOhlcv(sym, entry.getTimeframe(), limit);
        } catch (Exception e) {
            log.warn("Sync failed for {} {} via {}: {}", sym, entry.getTimeframe(),
                    entry.getProvider(), e.getMessage());
            return readFromRegistry(entry, limit);
        }

        if (!fresh.isEmpty()) {
            // Full replacement for live-fetched data (the API always returns a complete window)
            dynamicTableService.replaceAllBars(
                    entry.getTableName(), sym, entry.getTimeframe(), entry.getAssetType(), fresh);
            entry.setLastSyncAt(LocalDateTime.now());
            entry.setNextSyncAt(LocalDateTime.now().plus(TimeframeInterval.forTimeframe(entry.getTimeframe())));
            entry.setBarCount(fresh.size());
            registryRepository.save(entry);
            referenceDataService.ensureShare(sym, entry.getProvider(),
                    priceRouter.getQuote(sym, profile), profile);
            log.info("Synced {} bars for {} {} ({})", fresh.size(), sym,
                    entry.getTimeframe(), entry.getTableName());
            // ── Trigger offline aggregation for higher timeframes ──
            if (aggregationScheduler != null) {
                aggregationScheduler.triggerAggregationForBars(
                        sym, entry.getTimeframe(), entry.getProvider(),
                        entry.getAssetType(), fresh);
            }
            return fresh;
        }

        return readFromRegistry(entry, limit);
    }

    @Async
    public void syncRegistryEntryAsync(MarketDataTableRegistry entry, int limit, UserProfile profile) {
        syncRegistryEntry(entry, limit, profile);
    }

    public boolean isDue(MarketDataTableRegistry entry) {
        return entry.getNextSyncAt() == null || !entry.getNextSyncAt().isAfter(LocalDateTime.now());
    }

    public int defaultBarLimit() {
        return properties.getSync().getDefaultBars();
    }

    private static Trade.AssetType toAssetType(AssetClass assetClass) {
        return switch (assetClass) {
            case CRYPTO -> Trade.AssetType.CRYPTO;
            case FOREX -> Trade.AssetType.FOREX;
            case STOCK -> Trade.AssetType.STOCK;
            case COMMODITY -> Trade.AssetType.COMMODITY;
            case INDEX -> Trade.AssetType.INDEX;
        };
    }
}
