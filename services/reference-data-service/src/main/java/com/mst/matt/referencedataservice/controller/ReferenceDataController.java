package com.mst.matt.referencedataservice.controller;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.*;
import com.mst.matt.referencedataservice.cache.RefDataCacheService;
import com.mst.matt.referencedataservice.provider.defi.CoinGeckoDeFiProvider;
import com.mst.matt.referencedataservice.provider.nft.CoinGeckoNftDataProvider;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * REST controller for the Reference Data Service.
 *
 * <p>All calls go through {@link RefDataCacheService} (L1 Caffeine → L2 Redis →
 * provider registry fallback chain) — never directly to a {@code ProviderRegistry}
 * or concrete provider, except the two direct-provider bypasses that were already
 * here before caching existed ({@code nftDataProvider.listCollections},
 * {@code deFiProvider.*}) — those remain direct calls to the single concrete
 * CoinGecko-backed bean since there is only one implementation registered for
 * either today; wrap them in the cache service too if/when a second provider is
 * added for either.</p>
 */
@RestController
@RequestMapping("/api/reference")
public class ReferenceDataController {

    private final RefDataCacheService     cacheService;
    private final CoinGeckoNftDataProvider nftDataProvider;
    private final CoinGeckoDeFiProvider    deFiProvider;

    public ReferenceDataController(
            RefDataCacheService     cacheService,
            CoinGeckoNftDataProvider nftDataProvider,
            CoinGeckoDeFiProvider    deFiProvider) {
        this.cacheService    = cacheService;
        this.nftDataProvider = nftDataProvider;
        this.deFiProvider    = deFiProvider;
    }

    // ── 1. Fundamentals ───────────────────────────────────────────────────────

    @GetMapping("/fundamentals/{symbol}")
    public ResponseEntity<?> getFundamentals(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "STOCK") AssetClass assetClass) {

        return switch (assetClass) {
            case STOCK -> cacheService.getCompanyFundamentals(symbol)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
            case CRYPTO -> cacheService.getCryptoTokenomics(symbol)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
            case FOREX -> cacheService.getForexMacroIndicators(symbol)
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
            default -> ResponseEntity.badRequest().build();
        };
    }

    // ── 2. News ───────────────────────────────────────────────────────────────

    @GetMapping("/news")
    public ResponseEntity<List<NewsArticleDto>> getNews(
            @RequestParam(required = false) String symbol,
            @RequestParam(defaultValue = "STOCK") AssetClass assetClass,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        List<NewsArticleDto> articles;

        if (symbol != null && !symbol.isBlank()) {
            if (from != null || to != null) {
                Instant fromInstant = from != null
                        ? from.atStartOfDay(ZoneOffset.UTC).toInstant()
                        : Instant.now().minusSeconds(7L * 24 * 3600);
                Instant toInstant = to != null
                        ? to.atStartOfDay(ZoneOffset.UTC).toInstant()
                        : Instant.now();
                articles = cacheService.getNewsBySymbolRanged(symbol, assetClass, fromInstant, toInstant, limit);
            } else {
                articles = cacheService.getNewsBySymbol(symbol, assetClass, limit);
            }
        } else {
            articles = cacheService.getNewsByAssetClass(assetClass, limit);
        }

        return ResponseEntity.ok(articles);
    }

    // ── 3. Sentiment ──────────────────────────────────────────────────────────

    @GetMapping("/sentiment")
    public ResponseEntity<SentimentSnapshotDto> getSentiment(
            @RequestParam(required = false) String symbol,
            @RequestParam(defaultValue = "CRYPTO") AssetClass assetClass) {

        if (symbol != null && !symbol.isBlank()) {
            return cacheService.getSentiment(symbol, assetClass)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        }
        return ResponseEntity.ok(cacheService.getMarketSentimentIndex(assetClass));
    }

    // ── 4. Economic Calendar ──────────────────────────────────────────────────

    @GetMapping("/calendar")
    public ResponseEntity<List<EconomicEventDto>> getCalendar(
            @RequestParam(defaultValue = "STOCK") AssetClass assetClass,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String impactLevel) {

        Instant fromInstant = from != null
                ? from.atStartOfDay(ZoneOffset.UTC).toInstant()
                : Instant.now();
        Instant toInstant = to != null
                ? to.atStartOfDay(ZoneOffset.UTC).toInstant()
                : Instant.now().plus(java.time.Duration.ofDays(30));

        List<EconomicEventDto> events =
                cacheService.getUpcomingEvents(assetClass, fromInstant, toInstant, impactLevel);
        return ResponseEntity.ok(events);
    }

    // ── 5. Symbol Search ──────────────────────────────────────────────────────

    @GetMapping("/search")
    public ResponseEntity<List<SymbolSearchResultDto>> search(
            @RequestParam String query,
            @RequestParam(required = false) AssetClass assetClass,
            @RequestParam(defaultValue = "10") int limit) {

        List<SymbolSearchResultDto> results = assetClass != null
                ? cacheService.search(query, assetClass, limit)
                : cacheService.searchAllClasses(query, limit);
        return ResponseEntity.ok(results);
    }

    // ── 6. NFT Collections ────────────────────────────────────────────────────

    @GetMapping("/nft/collections")
    public ResponseEntity<List<NftCollectionDto>> getNftCollections(
            @RequestParam(defaultValue = "20")  int     limit,
            @RequestParam(defaultValue = "1")   int     page,
            @RequestParam(defaultValue = "false") boolean trending,
            @RequestParam(required = false)     String  query) {

        List<NftCollectionDto> result;
        if (trending) {
            result = cacheService.getTrendingNftCollections(limit);
        } else if (query != null && !query.isBlank()) {
            result = cacheService.searchNftCollections(query, limit);
        } else {
            result = nftDataProvider.listCollections(limit, page); // direct bypass — see class Javadoc
        }
        return ResponseEntity.ok(result);
    }

    // ── 7. DeFi Pools ────────────────────────────────────────────────────────

    @GetMapping("/defi/pools")
    public ResponseEntity<List<DeFiPoolDto>> getDefiPools(
            @RequestParam(required = false)       String  network,
            @RequestParam(required = false)       String  query,
            @RequestParam(defaultValue = "false") boolean trending) {

        List<DeFiPoolDto> result;
        if (trending)                              result = deFiProvider.getTrendingPools();
        else if (query != null && !query.isBlank()) result = deFiProvider.searchPools(query);
        else if (network != null && !network.isBlank()) result = deFiProvider.getPoolsByNetwork(network);
        else                                        result = deFiProvider.getTrendingPools();
        return ResponseEntity.ok(result);
    }
}