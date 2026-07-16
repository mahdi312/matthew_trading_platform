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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Two-tier cache service for {@code reference-data-service}.
 *
 * <h3>Lookup order</h3>
 * L1 (Caffeine, in-JVM) → L2 (Redis, shared) → provider registry (full miss,
 * writes back to both tiers on success).
 *
 * <p>Every method signature here matches a real call {@link
 * com.mst.matt.referencedataservice.controller.ReferenceDataController} makes —
 * checked directly against {@link NewsProvider}, {@link EconomicCalendarProvider},
 * {@link SymbolSearchProvider}, {@link NftDataProvider}. {@link
 * ProviderRegistry#executeWithFallback} always takes the {@link AssetClass} as
 * its first argument — there is no zero-arg-routing overload.</p>
 */
@Slf4j
@Service
public class RefDataCacheService {

    private static final String REDIS_PREFIX = "refdata:";

    private final ProviderRegistry<FundamentalsProvider>     fundamentalsRegistry;
    private final ProviderRegistry<NewsProvider>             newsRegistry;
    private final ProviderRegistry<SentimentProvider>        sentimentRegistry;
    private final ProviderRegistry<EconomicCalendarProvider> calendarRegistry;
    private final ProviderRegistry<SymbolSearchProvider>     searchRegistry;
    private final ProviderRegistry<NftDataProvider>          nftRegistry;

    private final com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineNews;
    private final com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineFundamentals;
    private final com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineSentiment;
    private final com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineCalendar;
    private final com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineSearch;
    private final com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineNft;

    private final RedisTemplate<String, Object> redisTemplate;

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
            @Qualifier("caffeineNewsCache")         com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineNews,
            @Qualifier("caffeineFundamentalsCache") com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineFundamentals,
            @Qualifier("caffeineSentimentCache")    com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineSentiment,
            @Qualifier("caffeineCalendarCache")     com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineCalendar,
            @Qualifier("caffeineSearchCache")       com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineSearch,
            @Qualifier("caffeineNftCache")          com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineNft,
            RedisTemplate<String, Object> redisTemplate,
            @Value("${cache.redis.news-ttl-minutes:10}")         long newsTtlMin,
            @Value("${cache.redis.fundamentals-ttl-minutes:60}") long fundamentalsTtlMin,
            @Value("${cache.redis.sentiment-ttl-minutes:5}")     long sentimentTtlMin,
            @Value("${cache.redis.calendar-ttl-minutes:60}")     long calendarTtlMin,
            @Value("${cache.redis.search-ttl-minutes:30}")       long searchTtlMin,
            @Value("${cache.redis.nft-ttl-minutes:15}")          long nftTtlMin) {

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
    public List<NewsArticleDto> getNewsBySymbol(String symbol, AssetClass assetClass, int limit) {
        String key = "news:symbol:" + symbol + ":" + assetClass + ":" + limit;
        Object hit = lookup(caffeineNews, key);
        if (hit instanceof List<?> list) return (List<NewsArticleDto>) list;

        List<NewsArticleDto> result = newsRegistry.executeWithFallback(
                assetClass, p -> p.getNewsBySymbol(symbol, assetClass, limit));
        if (result != null && !result.isEmpty()) writeBack(caffeineNews, key, result, newsTtl);
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<NewsArticleDto> getNewsBySymbolRanged(
            String symbol, AssetClass assetClass, Instant from, Instant to, int limit) {
        String key = "news:symbol-range:" + symbol + ":" + assetClass + ":" + from + ":" + to + ":" + limit;
        Object hit = lookup(caffeineNews, key);
        if (hit instanceof List<?> list) return (List<NewsArticleDto>) list;

        List<NewsArticleDto> result = newsRegistry.executeWithFallback(
                assetClass, p -> p.getNewsBySymbol(symbol, assetClass, from, to, limit));
        if (result != null && !result.isEmpty()) writeBack(caffeineNews, key, result, newsTtl);
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<NewsArticleDto> getNewsByAssetClass(AssetClass assetClass, int limit) {
        String key = "news:class:" + assetClass + ":" + limit;
        Object hit = lookup(caffeineNews, key);
        if (hit instanceof List<?> list) return (List<NewsArticleDto>) list;

        List<NewsArticleDto> result = newsRegistry.executeWithFallback(
                assetClass, p -> p.getNewsByAssetClass(assetClass, limit));
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
                AssetClass.STOCK, p -> p.getCompanyFundamentals(symbol));
        result.ifPresent(dto -> writeBack(caffeineFundamentals, key, dto, fundamentalsTtl));
        return result;
    }

    @SuppressWarnings("unchecked")
    public Optional<CryptoTokenomicsDto> getCryptoTokenomics(String symbol) {
        String key = "fundamentals:crypto:" + symbol;
        Object hit = lookup(caffeineFundamentals, key);
        if (hit instanceof CryptoTokenomicsDto dto) return Optional.of(dto);

        Optional<CryptoTokenomicsDto> result = fundamentalsRegistry.executeWithFallback(
                AssetClass.CRYPTO, p -> p.getCryptoTokenomics(symbol));
        result.ifPresent(dto -> writeBack(caffeineFundamentals, key, dto, fundamentalsTtl));
        return result;
    }

    @SuppressWarnings("unchecked")
    public Optional<ForexMacroIndicatorsDto> getForexMacroIndicators(String symbol) {
        String key = "fundamentals:forex:" + symbol;
        Object hit = lookup(caffeineFundamentals, key);
        if (hit instanceof ForexMacroIndicatorsDto dto) return Optional.of(dto);

        Optional<ForexMacroIndicatorsDto> result = fundamentalsRegistry.executeWithFallback(
                AssetClass.FOREX, p -> p.getForexMacroIndicators(symbol));
        result.ifPresent(dto -> writeBack(caffeineFundamentals, key, dto, fundamentalsTtl));
        return result;
    }

    // ── Sentiment ─────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public Optional<SentimentSnapshotDto> getSentiment(String symbol, AssetClass assetClass) {
        String key = "sentiment:symbol:" + symbol + ":" + assetClass;
        Object hit = lookup(caffeineSentiment, key);
        if (hit instanceof SentimentSnapshotDto dto) return Optional.of(dto);

        Optional<SentimentSnapshotDto> result = sentimentRegistry.executeWithFallback(
                assetClass, p -> p.getSentiment(symbol, assetClass));
        result.ifPresent(dto -> writeBack(caffeineSentiment, key, dto, sentimentTtl));
        return result;
    }

    @SuppressWarnings("unchecked")
    public SentimentSnapshotDto getMarketSentimentIndex(AssetClass assetClass) {
        String key = "sentiment:index:" + assetClass;
        Object hit = lookup(caffeineSentiment, key);
        if (hit instanceof SentimentSnapshotDto dto) return dto;

        SentimentSnapshotDto result = sentimentRegistry.executeWithFallback(
                assetClass, p -> p.getMarketSentimentIndex(assetClass));
        if (result != null) writeBack(caffeineSentiment, key, result, sentimentTtl);
        return result;
    }

    // ── Economic Calendar ─────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public List<EconomicEventDto> getUpcomingEvents(
            AssetClass assetClass, Instant from, Instant to, String impactLevel) {
        String key = "calendar:" + assetClass + ":" + from + ":" + to + ":" + (impactLevel != null ? impactLevel : "ALL");
        Object hit = lookup(caffeineCalendar, key);
        if (hit instanceof List<?> list) return (List<EconomicEventDto>) list;

        List<EconomicEventDto> result = calendarRegistry.executeWithFallback(
                assetClass, p -> p.getUpcomingEvents(from, to, impactLevel));
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
                assetClass, p -> p.search(query, assetClass, limit));
        if (result != null && !result.isEmpty()) writeBack(caffeineSearch, key, result, searchTtl);
        return result;
    }

    /** No-assetClass variant — matches the controller's default-to-STOCK-chain behaviour. */
    @SuppressWarnings("unchecked")
    public List<SymbolSearchResultDto> searchAllClasses(String query, int limit) {
        String key = "search:all:" + query + ":" + limit;
        Object hit = lookup(caffeineSearch, key);
        if (hit instanceof List<?> list) return (List<SymbolSearchResultDto>) list;

        List<SymbolSearchResultDto> result = searchRegistry.executeWithFallback(
                AssetClass.STOCK, p -> p.search(query, limit));
        if (result != null && !result.isEmpty()) writeBack(caffeineSearch, key, result, searchTtl);
        return result;
    }

    // ── NFT ───────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public List<NftCollectionDto> getTrendingNftCollections(int limit) {
        String key = "nft:trending:" + limit;
        Object hit = lookup(caffeineNft, key);
        if (hit instanceof List<?> list) return (List<NftCollectionDto>) list;

        List<NftCollectionDto> result = nftRegistry.executeWithFallback(
                AssetClass.NFT, p -> p.getTrendingCollections(limit));
        if (result != null && !result.isEmpty()) writeBack(caffeineNft, key, result, nftTtl);
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<NftCollectionDto> searchNftCollections(String query, int limit) {
        String key = "nft:search:" + query + ":" + limit;
        Object hit = lookup(caffeineNft, key);
        if (hit instanceof List<?> list) return (List<NftCollectionDto>) list;

        List<NftCollectionDto> result = nftRegistry.executeWithFallback(
                AssetClass.NFT, p -> p.searchCollections(query, limit));
        if (result != null && !result.isEmpty()) writeBack(caffeineNft, key, result, nftTtl);
        return result;
    }

    // ── Internal two-tier helpers (unchanged logic, kept as-is) ──────────────

    private Object lookup(com.github.benmanes.caffeine.cache.Cache<String, Object> l1, String key) {
        Object l1Hit = l1.getIfPresent(key);
        if (l1Hit != null) {
            log.debug("[RefDataCache] L1 hit — {}", key);
            return l1Hit;
        }
        try {
            Object l2Hit = redisTemplate.opsForValue().get(REDIS_PREFIX + key);
            if (l2Hit != null) {
                log.debug("[RefDataCache] L2 (Redis) hit — {}", key);
                l1.put(key, l2Hit);
                return l2Hit;
            }
        } catch (Exception ex) {
            log.warn("[RefDataCache] Redis read failed for {} — falling through to provider: {}",
                    key, ex.getMessage());
        }
        log.debug("[RefDataCache] full miss — {}", key);
        return null;
    }

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