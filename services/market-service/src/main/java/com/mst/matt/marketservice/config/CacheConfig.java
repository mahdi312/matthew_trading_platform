package com.mst.matt.marketservice.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Two-tier cache configuration for {@code market-service}.
 *
 * <h3>Architecture</h3>
 * <ul>
 *   <li><b>L1 — Caffeine</b> (in-JVM): sub-millisecond reads, local to this
 *       instance only. Configured as a plain {@link com.github.benmanes.caffeine.cache.Cache}
 *       used directly by {@link com.mst.matt.marketservice.service.OhlcvCacheService};
 *       NOT wired through Spring's {@code CacheManager} to avoid accidental
 *       double-caching via {@code @Cacheable}.</li>
 *   <li><b>L2 — Redis</b> (shared across instances): longer TTL, survives
 *       restarts, shared across all JVM instances. Wired as the primary Spring
 *       {@link CacheManager} so actuator/metrics work normally.</li>
 * </ul>
 *
 * <h3>Cache regions and TTLs</h3>
 * <ul>
 *   <li><b>{@code tickerSnapshot}</b> — L1: 5s, L2: 10s. Live-ish ticker;
 *       BitUnix pushes updates via WebSocket so REST callers should see data
 *       no more than a few seconds stale.</li>
 *   <li><b>{@code ohlcv}</b> — L1: 30s, L2: 5 min. Historical candles change
 *       slowly; L2 TTL is longer because sharing saves re-hitting rate-limited
 *       provider APIs on cold instances.</li>
 * </ul>
 *
 * <p>For local dev: {@code docker run -p 6379:6379 redis:7-alpine}.<br>
 * For production / Step 15: the {@code redis} service in {@code docker-compose.yml}.</p>
 *
 * <p>Metrics: Caffeine stats are tracked via the native Caffeine builder
 * ({@link #caffeineOhlcvCache()} exposes {@code recordStats()}), not through
 * Micrometer's Spring cache binding. Redis stats come from Actuator's
 * {@code /actuator/metrics} automatically via Spring Boot's Redis auto-config.</p>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    // ── Cache region names (constants shared with OhlcvCacheService) ─────────

    public static final String TICKER_CACHE = "tickerSnapshot";
    public static final String OHLCV_CACHE  = "ohlcv";

    // ── L2 TTLs (Redis) ───────────────────────────────────────────────────────

    @Value("${cache.redis.ohlcv-ttl-minutes:5}")
    private long ohlcvRedisTtlMinutes;

    @Value("${cache.redis.ticker-ttl-seconds:10}")
    private long tickerRedisTtlSeconds;

    // ── L1 TTLs (Caffeine, in seconds) ───────────────────────────────────────

    @Value("${cache.caffeine.ohlcv-ttl-seconds:30}")
    private long ohlcvCaffeineTtlSeconds;

    @Value("${cache.caffeine.ticker-ttl-seconds:5}")
    private long tickerCaffeineTtlSeconds;

    // ── L1: Caffeine caches (used directly by OhlcvCacheService) ─────────────

    /**
     * L1 Caffeine cache for OHLCV bars.
     * <p>Intentionally NOT a Spring-managed cache region — used directly via the
     * native Caffeine API in {@link com.mst.matt.marketservice.service.OhlcvCacheService}
     * so the two-tier logic (L1 → L2 → provider) is explicit and testable.</p>
     */
    @Bean(name = "caffeineOhlcvCache")
    public com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineOhlcvCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(ohlcvCaffeineTtlSeconds, TimeUnit.SECONDS)
                .maximumSize(2000)
                .recordStats()
                .build();
    }

    /**
     * L1 Caffeine cache for ticker snapshots.
     */
    @Bean(name = "caffeineTickerCache")
    public com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineTickerCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(tickerCaffeineTtlSeconds, TimeUnit.SECONDS)
                .maximumSize(500)
                .recordStats()
                .build();
    }

    // ── L2: Redis CacheManager (primary Spring CacheManager) ─────────────────

    /**
     * Redis-backed Spring {@link CacheManager} — the primary cache manager.
     *
     * <p>Uses {@link GenericJackson2JsonRedisSerializer} so cached objects can be
     * inspected with {@code redis-cli} and survive class evolution without manual
     * serialisation config (Jackson handles polymorphism via {@code @class} field).</p>
     */
    @Bean
    @Primary
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(ohlcvRedisTtlMinutes))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        RedisCacheConfiguration tickerConfig = defaultConfig
                .entryTtl(Duration.ofSeconds(tickerRedisTtlSeconds));

        RedisCacheConfiguration ohlcvConfig = defaultConfig
                .entryTtl(Duration.ofMinutes(ohlcvRedisTtlMinutes));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration(TICKER_CACHE, tickerConfig)
                .withCacheConfiguration(OHLCV_CACHE,  ohlcvConfig)
                .build();
    }
}
