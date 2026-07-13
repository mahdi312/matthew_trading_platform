package com.mst.matt.marketservice.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.boot.cache.autoconfigure.CacheManagerCustomizer;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine cache layer for {@code BitUnixMarketDataProvider} (Step 5.3).
 *
 * <p>{@code spring.cache.caffeine.spec} (a single shared spec string) can't
 * express two different TTLs for two different cache regions, so this class
 * builds the {@link CaffeineCacheManager} directly and registers each named
 * cache with its own {@link Caffeine} builder:</p>
 * <ul>
 *   <li><b>{@code tickerSnapshot}</b> — 5s TTL. A live-ish 24h ticker
 *       snapshot; BitUnix pushes ticker updates over WebSocket roughly
 *       continuously (Step 5.4), so REST callers should not see data more
 *       than a few seconds stale.</li>
 *   <li><b>{@code ohlcv}</b> — 30s TTL. Historical candles only change when
 *       the current (still-forming) bar updates or a new one opens; a
 *       longer TTL avoids hammering BitUnix's Kline endpoint (10 req/s/ip
 *       rate limit) for chart data that barely moves within a few seconds.</li>
 * </ul>
 *
 * <p>{@code recordStats()} is enabled on both builders so Actuator's
 * {@code /actuator/caches} (region metadata) and {@code /actuator/metrics}
 * (via Micrometer's {@code cache.*} meters, auto-bound by Spring Boot for
 * every named Caffeine cache) expose hit/miss counters — the guide's
 * "expose cache stats via Actuator" requirement.</p>
 */
@Configuration
public class CacheConfig {

    private static final String TICKER_CACHE = "tickerSnapshot";
    private static final String OHLCV_CACHE = "ohlcv";

    @Bean
    public CacheManagerCustomizer<CaffeineCacheManager> caffeineCacheManagerCustomizer() {
        return cacheManager -> {
            cacheManager.registerCustomCache(TICKER_CACHE, buildCache(5, TimeUnit.SECONDS, 500));
            cacheManager.registerCustomCache(OHLCV_CACHE, buildCache(30, TimeUnit.SECONDS, 2000));
        };
    }

    private static Cache<Object, Object> buildCache(
            long ttl, TimeUnit unit, long maximumSize) {
        return Caffeine.newBuilder()
                .expireAfterWrite(ttl, unit)
                .maximumSize(maximumSize)
                .recordStats()
                .build();
    }
}
