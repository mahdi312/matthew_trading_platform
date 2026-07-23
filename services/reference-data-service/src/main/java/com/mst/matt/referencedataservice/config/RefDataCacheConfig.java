package com.mst.matt.referencedataservice.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.mst.matt.referencedataservice.cache.RefDataCacheService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Two-tier cache configuration for {@code reference-data-service}.
 *
 * <h3>Architecture</h3>
 * <ul>
 *   <li><b>L1 — Caffeine</b> (in-JVM, per-region): short TTL, local only.
 *       Beans are named {@code caffeine<Region>Cache} and injected directly into
 *       {@link RefDataCacheService} for explicit two-tier logic.</li>
 *   <li><b>L2 — Redis</b> (shared across all instances): longer TTL, survives
 *       restarts. Registered as the primary Spring {@link CacheManager}.</li>
 * </ul>
 *
 * <h3>Cache regions</h3>
 * <table border="1">
 *   <tr><th>Region</th><th>L1 TTL (s)</th><th>L2 TTL (min)</th><th>Max L1 entries</th></tr>
 *   <tr><td>news</td><td>120</td><td>10</td><td>500</td></tr>
 *   <tr><td>fundamentals</td><td>300</td><td>60</td><td>200</td></tr>
 *   <tr><td>sentiment</td><td>60</td><td>5</td><td>200</td></tr>
 *   <tr><td>calendar</td><td>300</td><td>60</td><td>100</td></tr>
 *   <tr><td>search</td><td>60</td><td>30</td><td>1000</td></tr>
 *   <tr><td>nft</td><td>120</td><td>15</td><td>200</td></tr>
 * </table>
 *
 * <p>For local dev: {@code docker run -p 6379:6379 redis:7-alpine}.<br>
 * For production / Step 15: the {@code redis} service in {@code docker-compose.yml}.</p>
 */
@Configuration
@EnableCaching
public class RefDataCacheConfig {

    // ── Cache region names ───────────────────────────────────────────────────

    public static final String NEWS_CACHE         = "news";
    public static final String FUNDAMENTALS_CACHE = "fundamentals";
    public static final String SENTIMENT_CACHE    = "sentiment";
    public static final String CALENDAR_CACHE     = "calendar";
    public static final String SEARCH_CACHE       = "search";
    public static final String NFT_CACHE          = "nft";
    public static final String DEFI_CACHE         = "defi";

    // ── L1 TTLs (Caffeine) ───────────────────────────────────────────────────

    @Value("${cache.caffeine.news-ttl-seconds:120}")
    private long newsCaffeineTtl;

    @Value("${cache.caffeine.fundamentals-ttl-seconds:300}")
    private long fundamentalsCaffeineTtl;

    @Value("${cache.caffeine.sentiment-ttl-seconds:60}")
    private long sentimentCaffeineTtl;

    @Value("${cache.caffeine.calendar-ttl-seconds:300}")
    private long calendarCaffeineTtl;

    @Value("${cache.caffeine.search-ttl-seconds:60}")
    private long searchCaffeineTtl;

    @Value("${cache.caffeine.nft-ttl-seconds:120}")
    private long nftCaffeineTtl;

    @Value("${cache.caffeine.defi-ttl-seconds:60}")
    private long defiCaffeineTtl;

    // ── L2 TTLs (Redis) ──────────────────────────────────────────────────────

    @Value("${cache.redis.news-ttl-minutes:10}")
    private long newsRedisTtl;

    @Value("${cache.redis.fundamentals-ttl-minutes:60}")
    private long fundamentalsRedisTtl;

    @Value("${cache.redis.sentiment-ttl-minutes:5}")
    private long sentimentRedisTtl;

    @Value("${cache.redis.calendar-ttl-minutes:60}")
    private long calendarRedisTtl;

    @Value("${cache.redis.search-ttl-minutes:30}")
    private long searchRedisTtl;

    @Value("${cache.redis.nft-ttl-minutes:15}")
    private long nftRedisTtl;

    @Value("${cache.redis.defi-ttl-minutes:5}")
    private long defiRedisTtl;

    // ── L1 beans (Caffeine) ──────────────────────────────────────────────────

    @Bean(name = "caffeineNewsCache")
    public com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineNewsCache() {
        return buildCaffeine(newsCaffeineTtl, 500);
    }

    @Bean(name = "caffeineFundamentalsCache")
    public com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineFundamentalsCache() {
        return buildCaffeine(fundamentalsCaffeineTtl, 200);
    }

    @Bean(name = "caffeineSentimentCache")
    public com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineSentimentCache() {
        return buildCaffeine(sentimentCaffeineTtl, 200);
    }

    @Bean(name = "caffeineCalendarCache")
    public com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineCalendarCache() {
        return buildCaffeine(calendarCaffeineTtl, 100);
    }

    @Bean(name = "caffeineSearchCache")
    public com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineSearchCache() {
        return buildCaffeine(searchCaffeineTtl, 1000);
    }

    @Bean(name = "caffeineNftCache")
    public com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineNftCache() {
        return buildCaffeine(nftCaffeineTtl, 200);
    }

    @Bean(name = "caffeineDefiCache")
    public com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineDefiCache() {
        return buildCaffeine(defiCaffeineTtl, 200);
    }

    // ── L2: Redis CacheManager (primary Spring CacheManager) ─────────────────

    /**
     * Redis-backed {@link CacheManager} — primary; used by any {@code @Cacheable}
     * annotations (none currently in this service, but kept for Actuator
     * metrics and forward-compatibility).
     */
    @Bean
    @Primary
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        GenericJacksonJsonRedisSerializer valueSerializer = new GenericJacksonJsonRedisSerializer(JsonMapper.builder().build());

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(valueSerializer))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> regionConfigs = new HashMap<>();
        regionConfigs.put(NEWS_CACHE,         base.entryTtl(Duration.ofMinutes(newsRedisTtl)));
        regionConfigs.put(FUNDAMENTALS_CACHE, base.entryTtl(Duration.ofMinutes(fundamentalsRedisTtl)));
        regionConfigs.put(SENTIMENT_CACHE,    base.entryTtl(Duration.ofMinutes(sentimentRedisTtl)));
        regionConfigs.put(CALENDAR_CACHE,     base.entryTtl(Duration.ofMinutes(calendarRedisTtl)));
        regionConfigs.put(SEARCH_CACHE,       base.entryTtl(Duration.ofMinutes(searchRedisTtl)));
        regionConfigs.put(NFT_CACHE,          base.entryTtl(Duration.ofMinutes(nftRedisTtl)));
        regionConfigs.put(DEFI_CACHE,         base.entryTtl(Duration.ofMinutes(defiRedisTtl)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(30)))
                .withInitialCacheConfigurations(regionConfigs)
                .build();
    }

    // ── RedisTemplate ─────────────────────────────────────────────────────────

    /**
     * Typed {@link RedisTemplate} used by {@link RefDataCacheService}
     * for explicit L1→L2→provider cache logic.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer keySerializer = new StringRedisSerializer();
        GenericJacksonJsonRedisSerializer valueSerializer = new GenericJacksonJsonRedisSerializer(JsonMapper.builder().build());

        template.setKeySerializer(keySerializer);
        template.setHashKeySerializer(keySerializer);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);
        template.setDefaultSerializer(valueSerializer);
        template.afterPropertiesSet();
        return template;
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static com.github.benmanes.caffeine.cache.Cache<String, Object> buildCaffeine(
            long ttlSeconds, long maxSize) {
        return Caffeine.newBuilder()
                .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
                .maximumSize(maxSize)
                .recordStats()
                .build();
    }
}
