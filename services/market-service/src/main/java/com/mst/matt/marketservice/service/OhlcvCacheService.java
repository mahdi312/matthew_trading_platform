package com.mst.matt.marketservice.service;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import com.mst.matt.marketservice.registry.MarketOhlcvProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Caffeine-backed OHLCV cache layer.
 *
 * <p>Uses the existing {@code ohlcv} Caffeine cache region declared in
 * {@link com.mst.matt.marketservice.config.CacheConfig} (30 s TTL, 2000 entries).
 *
 * <p>Pattern: {@code getCached} checks the cache first; on miss it delegates to
 * {@link MarketOhlcvProviderRegistry} which applies the fallback chain.
 * Successful fetches are stored back via {@code @CachePut}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OhlcvCacheService {

    private final MarketOhlcvProviderRegistry registry;

    // ── Cached reads ─────────────────────────────────────────────────────────

    /**
     * Returns cached bars, or fetches and caches them on miss.
     *
     * @param symbol     canonical symbol
     * @param assetClass asset class
     * @param interval   timeframe label
     * @param limit      max bars
     * @return bars from cache or provider; empty list on total failure
     */
    @Cacheable(value = "ohlcv", key = "#symbol + ':' + #assetClass + ':' + #interval + ':' + #limit")
    public List<NormalizedOhlcvBar> getCached(String symbol,
                                               AssetClass assetClass,
                                               String interval,
                                               int limit) {
        log.debug("[OhlcvCache] miss — fetching {}/{}/{}/{}", symbol, assetClass, interval, limit);
        return registry.getHistoricalBars(symbol, assetClass, interval, limit);
    }

    /**
     * Force-refresh: fetches fresh bars from provider and updates the cache.
     *
     * @param symbol     canonical symbol
     * @param assetClass asset class
     * @param interval   timeframe label
     * @param limit      max bars
     * @return freshly fetched bars
     */
    @CachePut(value = "ohlcv", key = "#symbol + ':' + #assetClass + ':' + #interval + ':' + #limit")
    public List<NormalizedOhlcvBar> refresh(String symbol,
                                             AssetClass assetClass,
                                             String interval,
                                             int limit) {
        log.debug("[OhlcvCache] force-refresh {}/{}/{}/{}", symbol, assetClass, interval, limit);
        return registry.getHistoricalBars(symbol, assetClass, interval, limit);
    }

    // ── Range-based (not cached — too many possible ranges) ──────────────────

    /**
     * Fetches bars for a time range. Not cached (range space is unbounded).
     *
     * @param symbol     canonical symbol
     * @param assetClass asset class
     * @param interval   timeframe label
     * @param from       range start
     * @param to         range end
     * @return bars from provider; empty list on failure
     */
    public List<NormalizedOhlcvBar> getOrFetch(String symbol,
                                                AssetClass assetClass,
                                                String interval,
                                                Instant from,
                                                Instant to) {
        return registry.getHistoricalBars(symbol, assetClass, interval, from, to);
    }
}
