package com.mst.matt.marketservice.service;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import com.mst.matt.marketservice.config.CacheConfig;
import com.mst.matt.marketservice.registry.MarketOhlcvProviderRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Two-tier OHLCV cache service: L1 (Caffeine, in-JVM) → L2 (Redis, shared) → provider registry.
 *
 * <h3>Lookup order</h3>
 * <ol>
 *   <li><b>L1 — Caffeine</b>: sub-millisecond, local to this JVM instance.
 *       TTL: {@value CacheConfig#OHLCV_CACHE} region config (default 30 s).</li>
 *   <li><b>L2 — Redis</b>: shared across all instances, survives restarts.
 *       TTL: {@code cache.redis.ohlcv-ttl-minutes} (default 5 min).</li>
 *   <li><b>Provider registry</b>: full miss — calls
 *       {@link MarketOhlcvProviderRegistry#getHistoricalBars} which applies the
 *       Resilience4j-guarded fallback chain (BitUnix → AlphaVantage → … → NoOp).
 *       Result is written back to <em>both</em> L1 and L2.</li>
 * </ol>
 *
 * <p>The L1 Caffeine cache is injected directly (not via {@code @Cacheable}) so
 * the two-tier logic is explicit, testable, and not subject to Spring proxy
 * limitations (e.g., {@code @Cacheable} calling another {@code @Cacheable}
 * in the same bean is a well-known no-op).</p>
 *
 * <p>Range-based lookups ({@link #getOrFetch}) bypass the cache entirely because
 * the key-space (symbol × interval × from × to) is unbounded.</p>
 */
@Slf4j
@Service
public class OhlcvCacheService {

    private static final String REDIS_KEY_PREFIX = "ohlcv:";

    private final MarketOhlcvProviderRegistry registry;
    private final com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineL1;
    private final RedisTemplate<String, Object> redisTemplate;

    /** L2 Redis TTL — matches {@code cache.redis.ohlcv-ttl-minutes}. */
    private final Duration redisOhlcvTtl;

    public OhlcvCacheService(
            MarketOhlcvProviderRegistry registry,
            @Qualifier("caffeineOhlcvCache")
            com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineL1,
            RedisTemplate<String, Object> redisTemplate,
            @org.springframework.beans.factory.annotation.Value("${cache.redis.ohlcv-ttl-minutes:5}")
            long redisOhlcvTtlMinutes) {
        this.registry = registry;
        this.caffeineL1 = caffeineL1;
        this.redisTemplate = redisTemplate;
        this.redisOhlcvTtl = Duration.ofMinutes(redisOhlcvTtlMinutes);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns OHLCV bars, checking L1 → L2 → provider in that order.
     * Writes back to both tiers on a full miss.
     *
     * @param symbol     canonical symbol (e.g. "BTCUSDT")
     * @param assetClass asset class for registry routing
     * @param interval   timeframe label (e.g. "1h", "1d")
     * @param limit      maximum number of bars to fetch on a miss
     * @return bars from the highest-priority cache tier that has them,
     *         or from the provider on a full miss; empty list on total failure
     */
    @SuppressWarnings("unchecked")
    public List<NormalizedOhlcvBar> getCached(String symbol,
                                               AssetClass assetClass,
                                               String interval,
                                               int limit) {
        String cacheKey = buildKey(symbol, assetClass, interval, limit);

        // ── L1: Caffeine ──────────────────────────────────────────────────────
        Object l1Hit = caffeineL1.getIfPresent(cacheKey);
        if (l1Hit instanceof List<?> list && !list.isEmpty()) {
            log.debug("[OhlcvCache] L1 hit — {}", cacheKey);
            return (List<NormalizedOhlcvBar>) list;
        }

        // ── L2: Redis ─────────────────────────────────────────────────────────
        try {
            Object l2Hit = redisTemplate.opsForValue().get(REDIS_KEY_PREFIX + cacheKey);
            if (l2Hit instanceof List<?> list && !list.isEmpty()) {
                log.debug("[OhlcvCache] L2 (Redis) hit — {}", cacheKey);
                // Promote to L1 so subsequent calls from this instance are fast
                caffeineL1.put(cacheKey, l2Hit);
                return (List<NormalizedOhlcvBar>) list;
            }
        } catch (Exception ex) {
            // Redis unavailable — degrade gracefully, continue to provider
            log.warn("[OhlcvCache] Redis read failed for {} — falling through to provider: {}",
                    cacheKey, ex.getMessage());
        }

        // ── Full miss: provider registry ─────────────────────────────────────
        log.debug("[OhlcvCache] full miss — fetching from provider: {}", cacheKey);
        List<NormalizedOhlcvBar> bars = registry.getHistoricalBars(symbol, assetClass, interval, limit);

        if (bars != null && !bars.isEmpty()) {
            writeBack(cacheKey, bars);
        }
        return bars;
    }

    /**
     * Force-refresh: bypasses both cache tiers, fetches fresh bars from the
     * provider registry, and writes the result back to both L1 and L2.
     *
     * @param symbol     canonical symbol
     * @param assetClass asset class
     * @param interval   timeframe label
     * @param limit      max bars
     * @return freshly fetched bars
     */
    public List<NormalizedOhlcvBar> refresh(String symbol,
                                             AssetClass assetClass,
                                             String interval,
                                             int limit) {
        String cacheKey = buildKey(symbol, assetClass, interval, limit);
        log.debug("[OhlcvCache] force-refresh — {}", cacheKey);
        List<NormalizedOhlcvBar> bars = registry.getHistoricalBars(symbol, assetClass, interval, limit);
        if (bars != null && !bars.isEmpty()) {
            writeBack(cacheKey, bars);
        }
        return bars;
    }

    /**
     * Range-based fetch — not cached (key-space is unbounded).
     *
     * @param symbol     canonical symbol
     * @param assetClass asset class
     * @param interval   timeframe label
     * @param from       range start (inclusive)
     * @param to         range end (inclusive)
     * @return bars from the provider; empty list on failure
     */
    public List<NormalizedOhlcvBar> getOrFetch(String symbol,
                                                AssetClass assetClass,
                                                String interval,
                                                Instant from,
                                                Instant to) {
        return registry.getHistoricalBars(symbol, assetClass, interval, from, to);
    }

    /**
     * Evict a specific key from both cache tiers (e.g., on data-integrity events).
     */
    public void evict(String symbol, AssetClass assetClass, String interval, int limit) {
        String cacheKey = buildKey(symbol, assetClass, interval, limit);
        caffeineL1.invalidate(cacheKey);
        try {
            redisTemplate.delete(REDIS_KEY_PREFIX + cacheKey);
        } catch (Exception ex) {
            log.warn("[OhlcvCache] Redis evict failed for {}: {}", cacheKey, ex.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String buildKey(String symbol, AssetClass assetClass, String interval, int limit) {
        return symbol + ":" + assetClass + ":" + interval + ":" + limit;
    }

    private void writeBack(String cacheKey, List<NormalizedOhlcvBar> bars) {
        // L1
        caffeineL1.put(cacheKey, bars);
        // L2
        try {
            redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + cacheKey, bars, redisOhlcvTtl);
        } catch (Exception ex) {
            log.warn("[OhlcvCache] Redis write failed for {} — L1 still populated: {}",
                    cacheKey, ex.getMessage());
        }
    }
}
