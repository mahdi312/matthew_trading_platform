package com.mst.matt.referencedataservice.controller;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.calendar.EconomicCalendarProvider;
import com.mst.matt.contracts.provider.dto.*;
import com.mst.matt.contracts.provider.fundamentals.FundamentalsProvider;
import com.mst.matt.contracts.provider.news.NewsProvider;
import com.mst.matt.contracts.provider.nft.NftDataProvider;
import com.mst.matt.contracts.provider.registry.ProviderRegistry;
import com.mst.matt.contracts.provider.search.SymbolSearchProvider;
import com.mst.matt.contracts.provider.sentiment.SentimentProvider;
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
 * <h3>Base path: {@code /api/reference}</h3>
 *
 * <h3>Design rules</h3>
 * <ul>
 *   <li>All calls go through {@link ProviderRegistry#executeWithFallback} — never
 *       directly to any HTTP client or concrete provider.</li>
 *   <li>Asset class routing uses the {@code ?assetClass=STOCK|CRYPTO|FOREX} query param
 *       where applicable; defaults to {@link AssetClass#STOCK} for equity-biased endpoints.</li>
 *   <li>404 is returned when a provider returns {@link Optional#empty()}.</li>
 *   <li>No pagination on this initial pass — use {@code ?limit=} to cap results.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/reference")
public class ReferenceDataController {

    private final ProviderRegistry<FundamentalsProvider>     fundamentalsRegistry;
    private final ProviderRegistry<NewsProvider>             newsRegistry;
    private final ProviderRegistry<SentimentProvider>        sentimentRegistry;
    private final ProviderRegistry<EconomicCalendarProvider> calendarRegistry;
    private final ProviderRegistry<SymbolSearchProvider>     searchRegistry;
    private final ProviderRegistry<NftDataProvider>          nftRegistry;
    private final CoinGeckoNftDataProvider                   nftDataProvider;
    private final CoinGeckoDeFiProvider                      deFiProvider;

    public ReferenceDataController(
            ProviderRegistry<FundamentalsProvider>     fundamentalsRegistry,
            ProviderRegistry<NewsProvider>             newsRegistry,
            ProviderRegistry<SentimentProvider>        sentimentRegistry,
            ProviderRegistry<EconomicCalendarProvider> calendarRegistry,
            ProviderRegistry<SymbolSearchProvider>     searchRegistry,
            ProviderRegistry<NftDataProvider>          nftRegistry,
            CoinGeckoNftDataProvider                   nftDataProvider,
            CoinGeckoDeFiProvider                      deFiProvider) {

        this.fundamentalsRegistry = fundamentalsRegistry;
        this.newsRegistry         = newsRegistry;
        this.sentimentRegistry    = sentimentRegistry;
        this.calendarRegistry     = calendarRegistry;
        this.searchRegistry       = searchRegistry;
        this.nftRegistry          = nftRegistry;
        this.nftDataProvider      = nftDataProvider;
        this.deFiProvider         = deFiProvider;
    }

    // ── 1. Fundamentals ───────────────────────────────────────────────────────

    /**
     * Fetch fundamental data for a symbol.
     *
     * <p>Routes to the appropriate DTO based on asset class:</p>
     * <ul>
     *   <li>{@code STOCK}  → {@link CompanyFundamentalsDto}</li>
     *   <li>{@code CRYPTO} → {@link CryptoTokenomicsDto}</li>
     *   <li>{@code FOREX}  → {@link ForexMacroIndicatorsDto}</li>
     * </ul>
     *
     * @param symbol     e.g. "AAPL", "BTC", "EURUSD"
     * @param assetClass default STOCK
     */
    @GetMapping("/fundamentals/{symbol}")
    public ResponseEntity<?> getFundamentals(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "STOCK") AssetClass assetClass) {

        return switch (assetClass) {
            case STOCK -> {
                Optional<CompanyFundamentalsDto> result =
                        fundamentalsRegistry.executeWithFallback(
                                AssetClass.STOCK,
                                p -> p.getCompanyFundamentals(symbol));
                yield result.map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build());
            }
            case CRYPTO -> {
                Optional<CryptoTokenomicsDto> result =
                        fundamentalsRegistry.executeWithFallback(
                                AssetClass.CRYPTO,
                                p -> p.getCryptoTokenomics(symbol));
                yield result.map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build());
            }
            case FOREX -> {
                Optional<ForexMacroIndicatorsDto> result =
                        fundamentalsRegistry.executeWithFallback(
                                AssetClass.FOREX,
                                p -> p.getForexMacroIndicators(symbol));
                yield result.map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build());
            }
            default -> ResponseEntity.badRequest().build();
        };
    }

    // ── 2. News ───────────────────────────────────────────────────────────────

    /**
     * Fetch news articles.
     *
     * <p>Behaviour depends on {@code symbol} param presence:</p>
     * <ul>
     *   <li>Symbol present → {@link NewsProvider#getNewsBySymbol(String, AssetClass, int)}</li>
     *   <li>Symbol absent  → {@link NewsProvider#getNewsByAssetClass(AssetClass, int)}</li>
     * </ul>
     * Optional {@code from}/{@code to} date parameters (ISO-8601 date, e.g. 2025-01-15)
     * activate the ranged overload.
     *
     * @param symbol     optional symbol filter
     * @param assetClass default STOCK
     * @param limit      max articles, default 20
     * @param from       optional range start (ISO date)
     * @param to         optional range end   (ISO date)
     */
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
                // ranged overload
                Instant fromInstant = from != null
                        ? from.atStartOfDay(ZoneOffset.UTC).toInstant()
                        : Instant.now().minusSeconds(7L * 24 * 3600);
                Instant toInstant   = to != null
                        ? to.atStartOfDay(ZoneOffset.UTC).toInstant()
                        : Instant.now();
                final String sym = symbol;
                final Instant fi = fromInstant;
                final Instant ti = toInstant;
                articles = newsRegistry.executeWithFallback(
                        assetClass,
                        p -> p.getNewsBySymbol(sym, assetClass, fi, ti, limit));
            } else {
                final String sym = symbol;
                articles = newsRegistry.executeWithFallback(
                        assetClass,
                        p -> p.getNewsBySymbol(sym, assetClass, limit));
            }
        } else {
            articles = newsRegistry.executeWithFallback(
                    assetClass,
                    p -> p.getNewsByAssetClass(assetClass, limit));
        }

        return ResponseEntity.ok(articles);
    }

    // ── 3. Sentiment ──────────────────────────────────────────────────────────

    /**
     * Fetch sentiment data.
     *
     * <p>If {@code symbol} is provided, returns a per-symbol snapshot.
     * Otherwise returns the asset-class market-wide sentiment index.</p>
     *
     * @param symbol     optional — e.g. "BTC", "ETH"
     * @param assetClass default CRYPTO (sentiment providers currently support CRYPTO only)
     */
    @GetMapping("/sentiment")
    public ResponseEntity<SentimentSnapshotDto> getSentiment(
            @RequestParam(required = false) String symbol,
            @RequestParam(defaultValue = "CRYPTO") AssetClass assetClass) {

        if (symbol != null && !symbol.isBlank()) {
            final String sym = symbol;
            Optional<SentimentSnapshotDto> result =
                    sentimentRegistry.executeWithFallback(
                            assetClass,
                            p -> p.getSentiment(sym, assetClass));
            return result.map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } else {
            SentimentSnapshotDto index =
                    sentimentRegistry.executeWithFallback(
                            assetClass,
                            p -> p.getMarketSentimentIndex(assetClass));
            return ResponseEntity.ok(index);
        }
    }

    // ── 4. Economic Calendar ──────────────────────────────────────────────────

    /**
     * Fetch upcoming economic / earnings calendar events.
     *
     * <p>Optional date range parameters; defaults to today → +30 days.
     * Optional {@code impactLevel} filter: HIGH | MEDIUM | LOW.</p>
     *
     * @param assetClass  default STOCK
     * @param from        optional range start (ISO date)
     * @param to          optional range end   (ISO date)
     * @param impactLevel optional filter
     */
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

        final Instant fi = fromInstant;
        final Instant ti = toInstant;
        List<EconomicEventDto> events =
                calendarRegistry.executeWithFallback(
                        assetClass,
                        p -> p.getUpcomingEvents(fi, ti, impactLevel));

        return ResponseEntity.ok(events);
    }

    // ── 5. Symbol Search ──────────────────────────────────────────────────────

    /**
     * Search for symbols matching a free-text query.
     *
     * <p>Results are aggregated across providers in priority order.
     * Each provider contributes up to {@code limit} results; duplicates
     * are not de-duplicated at this layer.</p>
     *
     * @param query      free-text search term (required)
     * @param assetClass optional asset class filter; if absent, all asset classes are searched
     * @param limit      max results per provider, default 10
     */
    @GetMapping("/search")
    public ResponseEntity<List<SymbolSearchResultDto>> search(
            @RequestParam String query,
            @RequestParam(required = false) AssetClass assetClass,
            @RequestParam(defaultValue = "10") int limit) {

        AssetClass ac = assetClass != null ? assetClass : AssetClass.STOCK;

        List<SymbolSearchResultDto> results;
        if (assetClass != null) {
            results = searchRegistry.executeWithFallback(
                    ac,
                    p -> p.search(query, ac, limit));
        } else {
            // No filter — use STOCK chain as the default entry point;
            // cross-asset providers (TwelveData, CoinGecko) are still included
            // in the STOCK chain and will gracefully return empty for mismatched types.
            results = searchRegistry.executeWithFallback(
                    AssetClass.STOCK,
                    p -> p.search(query, limit));
        }

        return ResponseEntity.ok(results);
    }

    // ── 6. NFT Collections ────────────────────────────────────────────────────

    /**
     * Fetch NFT collections from CoinGecko.
     *
     * <p>Supports three modes:</p>
     * <ul>
     *   <li>{@code ?trending=true}       — trending collections</li>
     *   <li>{@code ?query=...}           — search by name</li>
     *   <li>default                      — paginated list ordered by market cap</li>
     * </ul>
     *
     * @param limit    max results, default 20
     * @param page     page number (1-based), default 1; used in list mode only
     * @param trending if {@code true}, return trending collections
     * @param query    optional text search query
     */
    @GetMapping("/nft/collections")
    public ResponseEntity<List<NftCollectionDto>> getNftCollections(
            @RequestParam(defaultValue = "20")  int     limit,
            @RequestParam(defaultValue = "1")   int     page,
            @RequestParam(defaultValue = "false") boolean trending,
            @RequestParam(required = false)     String  query) {

        List<NftCollectionDto> result;
        if (trending) {
            result = nftRegistry.executeWithFallback(
                    AssetClass.NFT,
                    p -> p.getTrendingCollections(limit));
        } else if (query != null && !query.isBlank()) {
            final String q = query;
            result = nftRegistry.executeWithFallback(
                    AssetClass.NFT,
                    p -> p.searchCollections(q, limit));
        } else {
            // List mode — delegate directly to the CoinGecko provider
            result = nftDataProvider.listCollections(limit, page);
        }
        return ResponseEntity.ok(result);
    }

    // ── 7. DeFi Pools ────────────────────────────────────────────────────────

    /**
     * Fetch DeFi pool data from GeckoTerminal (on-chain).
     *
     * <p>Supports three modes:</p>
     * <ul>
     *   <li>{@code ?trending=true}       — global trending pools</li>
     *   <li>{@code ?query=...}           — pool search by token/pair name</li>
     *   <li>{@code ?network=eth}         — pools for a specific network</li>
     * </ul>
     *
     * @param network  optional network id (e.g., "eth", "bsc")
     * @param query    optional text search query
     * @param trending if {@code true}, return global trending pools
     */
    @GetMapping("/defi/pools")
    public ResponseEntity<List<DeFiPoolDto>> getDefiPools(
            @RequestParam(required = false)       String  network,
            @RequestParam(required = false)       String  query,
            @RequestParam(defaultValue = "false") boolean trending) {

        List<DeFiPoolDto> result;
        if (trending) {
            result = deFiProvider.getTrendingPools();
        } else if (query != null && !query.isBlank()) {
            result = deFiProvider.searchPools(query);
        } else if (network != null && !network.isBlank()) {
            result = deFiProvider.getPoolsByNetwork(network);
        } else {
            result = deFiProvider.getTrendingPools();   // default: trending
        }
        return ResponseEntity.ok(result);
    }
}
