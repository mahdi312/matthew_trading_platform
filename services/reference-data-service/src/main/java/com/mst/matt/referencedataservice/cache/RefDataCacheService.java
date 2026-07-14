package com.mst.matt.referencedataservice.cache;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.calendar.EconomicCalendarProvider;
import com.mst.matt.contracts.provider.dto.*;
import com.mst.matt.contracts.provider.fundamentals.FundamentalsProvider;
import com.mst.matt.contracts.provider.news.NewsProvider;
import com.mst.matt.contracts.provider.nft.NftDataProvider;
import com.mst.matt.contracts.provider.registry.ProviderRegistry;
import com.mst.matt.contracts.provider.search.SymbolSearchProvider;
import com.mst.matt.contracts.provider.sentiment.SentimentProvider;
import com.mst.matt.referencedataservice.config.RefDataCacheConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Two-tier cache service for {@code reference-data-service}.
 *
 * <h3>Lookup order</h3>
 * <ol>
 *   <li><b>L1 — Caffeine</b>: sub-millisecond, local to this JVM instance only.</li>
 *   <li><b>L2 — Redis</b>: shared across all instances; survives restarts.</li>
 *   <li><b>Provider registry</b>: full miss — calls the fallback chain and writes
 *       back to both tiers on success.</li>
 * </ol>
 *
 * <p>This service is intentionally a cache-aside pattern written explicitly (not via
 * {@code @Cacheable}) so the two-tier flow is clear, testable, and Redis failures
 * degrade gracefully to provider calls without surfacing 500s.</p>
 *
 * <p>The controller ({@code ReferenceDataController}) currently calls provider
 * registries directly. Swap those calls for this service's methods to get the
 * two-tier cache benefit — that wiring is done in the same Step 8 pass.</p>
 */
@Slf4j
@Service
public class RefDataCacheService {

    private static final String REDIS_PREFIX = "refdata:";

    // ── Provider registries ───────────────────────────────────────────────────

    private final ProviderRegistry<FundamentalsProvider>     fundamentalsRegistry;
    private final ProviderRegistry<NewsProvider>             newsRegistry;
    private final ProviderRegistry<SentimentProvider>        sentimentRegistry;
    private final ProviderRegistry<EconomicCalendarProvider> calendarRegistry;
    private final ProviderRegistry<SymbolSearchProvider>     searchRegistry;
    private final ProviderRegistry<NftDataProvider>          nftRegistry;

    // ── L1 Caffeine caches ────────────────────────────────────────────────────

    private final com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineNews;
    private final com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineFundamentals;
    private final com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineSentiment;
    private final com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineCalendar;
    private final com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineSearch;
    private final com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineNft;

    // ── L2 Redis ──────────────────────────────────────────────────────────────

    private final RedisTemplate<String, Object> redisTemplate;

    // ── L2 TTLs ───────────────────────────────────────────────────────────────

    private final Duration newsTtl;
    private final Duration fundamentalsTtl;
    private final Duration sentimentTtl;
    private final Duration calendarTtl;
    private final Duration searchTtl;
    private final Duration nftTtl;

    public RefDataCacheService(
            ProviderRegistry<FundamentalsProvider>     fundamentalsRegistry,
            ProviderRegistry<NewsProvider>             newsRegistry,
            ProviderRegistry<SentimentProvider>        sentimentRegistry,
            ProviderRegistry<EconomicCalendarProvider> calendarRegistry,
            ProviderRegistry<SymbolSearchProvider>     searchRegistry,
            ProviderRegistry<NftDataProvider>          nftRegistry,
            @Qualifier("caffeineNewsCache")
            com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineNews,
            @Qualifier("caffeineFundamentalsCache")
            com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineFundamentals,
            @Qualifier("caffeineSentimentCache")
            com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineSentiment,
            @Qualifier("caffeineCalendarCache")
            com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineCalendar,
            @Qualifier("caffeineSearchCache")
            com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineSearch,
            @Qualifier("caffeineNftCache")
            com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineNft,
            RedisTemplate<String, Object> redisTemplate,
            @Value("${cache.redis.news-ttl-minutes:10}")          long newsTtlMin,
            @Value("${cache.redis.fundamentals-ttl-minutes:60}")  long fundamentalsTtlMin,
            @Value("${cache.redis.sentiment-ttl-minutes:5}")      long sentimentTtlMin,
            @Value("${cache.redis.calendar-ttl-minutes:60}")      long calendarTtlMin,
            @Value("${cache.redis.search-ttl-minutes:30}")        long searchTtlMin,
            @Value("${cache.redis.nft-ttl-minutes:15}")           long nftTtlMin) {

        this.fundamentalsRegistry = fundamentalsRegistry;
        this.newsRegistry         = newsRegistry;
        this.sentimentRegistry    = sentimentRegistry;
        this.calendarRegistry     = calendarRegistry;
        this.searchRegistry       = searchRegistry;
        this.nftRegistry          = nftRegistry;

        this.caffeineNews         = caffeineNews;
        this.caffeineFundamentals = caffeineFundamentals;
        this.caffeineSentiment    = caffeineSentiment;
        this.caffeineCalendar     = caffeineCalendar;
        this.caffeineSearch       = caffeineSearch;
        this.caffeineNft          = caffeineNft;

        this.redisTemplate = redisTemplate;

        this.newsTtl         = Duration.ofMinutes(newsTtlMin);
        this.fundamentalsTtl = Duration.ofMinutes(fundamentalsTtlMin);
        this.sentimentTtl    = Duration.ofMinutes(sentimentTtlMin);
        this.calendarTtl     = Duration.ofMinutes(calendarTtlMin);
        this.searchTtl       = Duration.ofMinutes(searchTtlMin);
        this.nftTtl          = Duration.ofMinutes(nftTtlMin);
    }

    // ── News ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public List<NewsArticleDto> getNews(String symbol, AssetClass assetClass, int limit) {
        String key = "news:" + symbol + ":" + assetClass + ":" + limit;
        Object hit = lookup(caffeineNews, key);
        if (hit instanceof List<?> list) return (List<NewsArticleDto>) list;

        List<NewsArticleDto> result = newsRegistry.executeWithFallback(
                p -> p.getNews(symbol, assetClass, limit));
        if (result != null && !result.isEmpty()) writeBack(caffeineNews, key, result, newsTtl);
        return result;
    }

    // ── Fundamentals ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public Optional<CompanyFundamentalsDto> getCompanyFundamentals(String symbol) {
        String key = "fundamentals:company:" + symbol;
        Object hit = lookup(caffeineFundamentals, key);
        if (hit instanceof CompanyFundamentalsDto dto) return Optional.of(dto);

        Optional<CompanyFundamentalsDto> result = fundamentalsRegistry.executeWithFallback(
                p -> p.getCompanyFundamentals(symbol));
        result.ifPresent(dto -> writeBack(caffeineFundamentals, key, dto, fundamentalsTtl));
        return result;
    }

    @SuppressWarnings("unchecked")
    public Optional<CryptoTokenomicsDto> getCryptoTokenomics(String coinId) {
        String key = "fundamentals:crypto:" + coinId;
        Object hit = lookup(caffeineFundamentals, key);
        if (hit instanceof CryptoTokenomicsDto dto) return Optional.of(dto);

        Optional<CryptoTokenomicsDto> result = fundamentalsRegistry.executeWithFallback(
                p -> p.getCryptoTokenomics(coinId));
        result.ifPresent(dto -> writeBack(caffeineFundamentals, key, dto, fundamentalsTtl));
        return result;
    }

    // ── Sentiment ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public Optional<SentimentSnapshotDto> getSentiment(String symbol, AssetClass assetClass) {
        String key = "sentiment:" + symbol + ":" + assetClass;
        Object hit = lookup(caffeineSentiment, key);
        if (hit instanceof SentimentSnapshotDto dto) return Optional.of(dto);

        Optional<SentimentSnapshotDto> result = sentimentRegistry.executeWithFallback(
                p -> p.getSentiment(symbol, assetClass));
        result.ifPresent(dto -> writeBack(caffeineSentiment, key, dto, sentimentTtl));
        return result;
    }

    // ── Economic Calendar ─────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public List<EconomicEventDto> getCalendar(LocalDate from, LocalDate to, String country) {
        String key = "calendar:" + from + ":" + to + ":" + (country != null ? country : "ALL");
        Object hit = lookup(caffeineCalendar, key);
        if (hit instanceof List<?> list) return (List<EconomicEventDto>) list;

        Instant fromInstant = from.atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
        Instant toInstant   = to.atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
        List<EconomicEventDto> result = calendarRegistry.executeWithFallback(
                p -> p.getUpcomingEvents(fromInstant, toInstant, country));
        if (result != null && !result.isEmpty()) writeBack(caffeineCalendar, key, result, calendarTtl);
        return result;
    }

    // ── Symbol Search ─────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public List<SymbolSearchResultDto> search(String query, AssetClass assetClass, int limit) {
        String key = "search:" + query + ":" + assetClass + ":" + limit;
        Object hit = lookup(caffeineSearch, key);
        if (hit instanceof List<?> list) return (List<SymbolSearchResultDto>) list;

        List<SymbolSearchResultDto> result = searchRegistry.executeWithFallback(
                p -> p.search(query, assetClass, limit));
        if (result != null && !result.isEmpty()) writeBack(caffeineSearch, key, result, searchTtl);
        return result;
    }

    // ── NFT ───────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public List<NftCollectionDto> getTopNftCollections(int limit) {
        String key = "nft:top:" + limit;
        Object hit = lookup(caffeineNft, key);
        if (hit instanceof List<?> list) return (List<NftCollectionDto>) list;

        List<NftCollectionDto> result = nftRegistry.executeWithFallback(
                p -> p.getTopCollections(limit));
        if (result != null && !result.isEmpty()) writeBack(caffeineNft, key, result, nftTtl);
        return result;
    }

    // ── Internal two-tier helpers ─────────────────────────────────────────────

    /**
     * L1 → L2 lookup. Returns the cached value or {@code null} on full miss.
     */
    private Object lookup(com.github.benmanes.caffeine.cache.Cache<String, Object> l1, String key) {
        // L1
        Object l1Hit = l1.getIfPresent(key);
        if (l1Hit != null) {
            log.debug("[RefDataCache] L1 hit — {}", key);
            return l1Hit;
        }
        // L2
        try {
            Object l2Hit = redisTemplate.opsForValue().get(REDIS_PREFIX + key);
            if (l2Hit != null) {
                log.debug("[RefDataCache] L2 (Redis) hit — {}", key);
                l1.put(key, l2Hit); // promote to L1
                return l2Hit;
            }
        } catch (Exception ex) {
            log.warn("[RefDataCache] Redis read failed for {} — falling through to provider: {}",
                    key, ex.getMessage());
        }
        log.debug("[RefDataCache] full miss — {}", key);
        return null;
    }

    /**
     * Write value to L1 (Caffeine) and L2 (Redis).
     * Redis failures are swallowed — L1 is still populated.
     */
    private void writeBack(com.github.benmanes.caffeine.cache.Cache<String, Object> l1,
                            String key, Object value, Duration redisTtl) {
        l1.put(key, value);
        try {
            redisTemplate.opsForValue().set(REDIS_PREFIX + key, value, redisTtl);
        } catch (Exception ex) {
            log.warn("[RefDataCache] Redis write failed for {} — L1 still populated: {}",
                    key, ex.getMessage());
        }
    }
}
