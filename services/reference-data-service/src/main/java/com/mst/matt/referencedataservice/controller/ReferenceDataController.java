package com.mst.matt.referencedataservice.controller;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.*;
import com.mst.matt.referencedataservice.cache.RefDataCacheService;
import com.mst.matt.referencedataservice.provider.nft.CoinGeckoNftDataProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * REST controller for the Reference Data Service.
 *
 * <p>All calls go through {@link RefDataCacheService} (L1 Caffeine → L2 Redis →
 * provider registry fallback chain) — never directly to a concrete provider,
 * except the NFT paginated list bypass ({@code listCollections}) which is not
 * yet on the {@code NftDataProvider} contract.</p>
 */
@RestController
@Tag(name = "Reference Data", description = "Fundamentals, news, sentiment, calendar, NFT, and DeFi data")
@RequestMapping("/api/reference")
public class ReferenceDataController {

    private final RefDataCacheService      cacheService;
    private final CoinGeckoNftDataProvider nftDataProvider;

    public ReferenceDataController(
            RefDataCacheService      cacheService,
            CoinGeckoNftDataProvider nftDataProvider) {
        this.cacheService    = cacheService;
        this.nftDataProvider = nftDataProvider;
    }

    // ── 1. Fundamentals ───────────────────────────────────────────────────────

    @Operation(summary = "Get fundamentals for a symbol by asset class")
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

    @Operation(summary = "Get news articles by symbol or asset class")
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

    @Operation(summary = "Get sentiment snapshot for a symbol or market index")
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

    @Operation(summary = "Get upcoming economic calendar events")
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

    @Operation(summary = "Search symbols across asset classes")
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

    @Operation(summary = "List, search, or get trending NFT collections")
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
            result = nftDataProvider.listCollections(limit, page);
        }
        return ResponseEntity.ok(result);
    }

    // ── 7. DeFi Pools (via ProviderRegistry + cache) ───────────────────────────

    @Operation(summary = "List, search, or get trending DeFi pools")
    @GetMapping("/defi/pools")
    public ResponseEntity<List<DeFiPoolDto>> getDefiPools(
            @RequestParam(required = false)       String  network,
            @RequestParam(required = false)       String  query,
            @RequestParam(defaultValue = "false") boolean trending) {

        List<DeFiPoolDto> result;
        if (query != null && !query.isBlank()) {
            result = cacheService.searchDefiPools(query);
        } else if (network != null && !network.isBlank()) {
            result = cacheService.getDefiPoolsByNetwork(network);
        } else {
            result = cacheService.getTrendingDefiPools();
        }
        return ResponseEntity.ok(result);
    }
}
