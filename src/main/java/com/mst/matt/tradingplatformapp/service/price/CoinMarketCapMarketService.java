package com.mst.matt.tradingplatformapp.service.price;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import com.mst.matt.tradingplatformapp.service.price.api.coinmarketcap.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CoinMarketCap market-data service — complete free-tier REST API integration.
 *
 * <h3>Free-tier endpoints covered:</h3>
 *
 * <b>Cryptocurrency (Authenticated):</b>
 * <ul>
 *   <li>{@code GET /v1/cryptocurrency/map}                            — symbol → ID mapping</li>
 *   <li>{@code GET /v3/cryptocurrency/listings/latest}                — top coins by market cap</li>
 *   <li>{@code GET /v1/cryptocurrency/listings/new}                   — newly listed coins</li>
 *   <li>{@code GET /v3/cryptocurrency/quotes/latest}                  — real-time quotes (delegated)</li>
 *   <li>{@code GET /v2/cryptocurrency/ohlcv/latest}                   — latest OHLCV</li>
 *   <li>{@code GET /v2/cryptocurrency/ohlcv/historical}               — historical OHLCV (delegated)</li>
 *   <li>{@code GET /v2/cryptocurrency/market-pairs/latest}            — trading pairs</li>
 *   <li>{@code GET /v2/cryptocurrency/price-performance-stats/latest} — price performance</li>
 *   <li>{@code GET /v2/cryptocurrency/info}                           — metadata (logo, description)</li>
 *   <li>{@code GET /v1/cryptocurrency/trending/latest}                — currently trending</li>
 *   <li>{@code GET /v1/cryptocurrency/trending/gainers-losers}        — top gainers / losers</li>
 *   <li>{@code GET /v1/cryptocurrency/trending/most-visited}          — most visited</li>
 *   <li>{@code GET /v1/cryptocurrency/categories}                     — all categories</li>
 *   <li>{@code GET /v1/cryptocurrency/category}                       — single category detail</li>
 *   <li>{@code GET /v1/cryptocurrency/airdrops}                       — airdrops list</li>
 *   <li>{@code GET /v1/cryptocurrency/airdrop}                        — single airdrop detail</li>
 * </ul>
 *
 * <b>Exchange:</b>
 * <ul>
 *   <li>{@code GET /v1/exchange/map}                 — exchange ID map</li>
 *   <li>{@code GET /v1/exchange/listings/latest}     — exchange listings</li>
 *   <li>{@code GET /v1/exchange/info}                — exchange metadata</li>
 * </ul>
 *
 * <b>DEX / Token (public-api keyless or authenticated):</b>
 * <ul>
 *   <li>{@code GET /v4/dex/spot-pairs/latest}        — DEX spot pairs</li>
 *   <li>{@code GET /v1/dex/search}                   — DEX token/pair search</li>
 *   <li>{@code POST /v1/dex/tokens/trending/list}    — trending DEX tokens</li>
 *   <li>{@code GET /v1/dex/new/list}                 — new DEX tokens</li>
 *   <li>{@code GET /v1/dex/meme/list}                — meme tokens</li>
 *   <li>{@code GET /v1/dex/gainer-loser/list}        — DEX gainers/losers</li>
 *   <li>{@code GET /v1/dex/security/detail}          — token security detail</li>
 *   <li>{@code GET /v1/dex/platform/list}            — DEX platforms (keyless)</li>
 * </ul>
 *
 * <b>K-Line / OHLCV (keyless):</b>
 * <ul>
 *   <li>{@code GET /v1/k-line/candles}               — DEX pair K-line candles</li>
 *   <li>{@code GET /v1/k-line/points}                — DEX price points</li>
 * </ul>
 *
 * <b>Global Metrics:</b>
 * <ul>
 *   <li>{@code GET /v1/global-metrics/quotes/latest} — total market cap, BTC dominance</li>
 *   <li>{@code GET /v3/fear-and-greed/latest}        — Fear &amp; Greed index (keyless)</li>
 *   <li>{@code GET /v3/fear-and-greed/historical}    — historical Fear &amp; Greed (keyless)</li>
 *   <li>{@code GET /v1/altcoin-season-index/latest}  — Altcoin Season Index</li>
 * </ul>
 *
 * <b>CMC Index (keyless):</b>
 * <ul>
 *   <li>{@code GET /v3/index/cmc100-latest}          — CMC100 current value</li>
 *   <li>{@code GET /v3/index/cmc100-historical}      — CMC100 history</li>
 *   <li>{@code GET /v3/index/cmc20-latest}           — CMC20 current value</li>
 *   <li>{@code GET /v3/index/cmc20-historical}       — CMC20 history</li>
 * </ul>
 *
 * <b>Tools / Utilities:</b>
 * <ul>
 *   <li>{@code GET /v2/tools/price-conversion}       — currency conversion</li>
 *   <li>{@code GET /v1/fiat/map}                     — fiat currency map</li>
 *   <li>{@code GET /v1/key/info}                     — API key usage stats</li>
 * </ul>
 *
 * <h3>Rate limiting:</h3>
 * Free tier: 30 requests/minute. All calls share the {@code "coinmarketcap"} throttle bucket
 * registered by {@link CoinMarketCapPriceService}.
 *
 * <h3>Caching:</h3>
 * All responses are in-memory cached at appropriate TTLs to stay within the free-tier limit.
 *
 * <h3>DB persistence:</h3>
 * Cryptocurrency metadata is persisted to {@code cmc_crypto_info} for offline fallback.
 */
@Service
public class CoinMarketCapMarketService {

    private static final Logger log = LoggerFactory.getLogger(CoinMarketCapMarketService.class);

    // ── Base URLs ─────────────────────────────────────────────────────────────
    private static final String BASE_URL        = CoinMarketCapPriceService.BASE_URL;
    private static final String PUBLIC_BASE_URL = CoinMarketCapPriceService.PUBLIC_BASE_URL;
    private static final String THROTTLE_KEY    = CoinMarketCapPriceService.THROTTLE_KEY;

    // ── Cache TTLs ────────────────────────────────────────────────────────────
    private static final long LISTINGS_TTL_MS        = 5  * 60_000L;   // 5 min
    private static final long QUOTES_TTL_MS          = 2  * 60_000L;   // 2 min
    private static final long MAP_TTL_MS             = 24 * 3600_000L; // 24 hours
    private static final long INFO_TTL_MS            = 60 * 60_000L;   // 1 hour
    private static final long TRENDING_TTL_MS        = 5  * 60_000L;   // 5 min
    private static final long GLOBAL_TTL_MS          = 2  * 60_000L;   // 2 min
    private static final long FEAR_GREED_TTL_MS      = 5  * 60_000L;   // 5 min
    private static final long EXCHANGE_TTL_MS        = 30 * 60_000L;   // 30 min
    private static final long DEX_TTL_MS             = 2  * 60_000L;   // 2 min
    private static final long KLINE_TTL_MS           = 1  * 60_000L;   // 1 min
    private static final long INDEX_TTL_MS           = 5  * 60_000L;   // 5 min
    private static final long CONVERSION_TTL_MS      = 2  * 60_000L;   // 2 min
    private static final long FIAT_MAP_TTL_MS        = 24 * 3600_000L; // 24 hours
    private static final long CATEGORIES_TTL_MS      = 30 * 60_000L;   // 30 min
    private static final long MARKET_PAIRS_TTL_MS    = 10 * 60_000L;   // 10 min
    private static final long PERF_STATS_TTL_MS      = 10 * 60_000L;   // 10 min
    private static final long ALTCOIN_IDX_TTL_MS     = 60 * 60_000L;   // 1 hour
    private static final long AIRDROPS_TTL_MS        = 60 * 60_000L;   // 1 hour

    private final HttpJsonClient        http;
    private final MarketApiProperties   keys;
    private final CoinMarketCapPriceService priceService;
    private final JdbcTemplate          jdbc;
    private final Gson                  gson = new Gson();

    // ── In-memory caches ──────────────────────────────────────────────────────
    private final Map<String, CacheEntry<JsonObject>>  listingsCache      = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<JsonObject>>  quotesCache        = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<JsonObject>>  infoCache          = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<JsonObject>>  trendingCache      = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<JsonObject>>  globalCache        = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<JsonObject>>  fearGreedCache     = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<JsonObject>>  exchangeCache      = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<JsonObject>>  dexCache           = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<JsonObject>>  klineCache         = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<JsonObject>>  indexCache         = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<JsonObject>>  conversionCache    = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<JsonObject>>  categoriesCache    = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<JsonObject>>  marketPairsCache   = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<JsonObject>>  perfStatsCache     = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<JsonObject>>  altcoinIdxCache    = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<JsonObject>>  airdropCache       = new ConcurrentHashMap<>();
    private volatile CacheEntry<JsonObject>             fiatMapCache;
    private volatile CacheEntry<JsonObject>             keyInfoCache;
    private volatile CacheEntry<JsonObject>             platformListCache;

    private final Set<String> ensuredTables = ConcurrentHashMap.newKeySet();

    @Autowired
    public CoinMarketCapMarketService(HttpJsonClient http,
                                       MarketApiProperties keys,
                                       CoinMarketCapPriceService priceService,
                                       JdbcTemplate jdbc) {
        this.http         = http;
        this.keys         = keys;
        this.priceService = priceService;
        this.jdbc         = jdbc;
    }

    public boolean isEnabled() {
        return keys.hasCoinmarketcapKey();
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 1. CRYPTOCURRENCY ENDPOINTS
    // ═════════════════════════════════════════════════════════════════════════

    // ─── 1a. Cryptocurrency ID Map ────────────────────────────────────────────

    /**
     * {@code GET /v1/cryptocurrency/map} — Map symbols/names to CoinMarketCap IDs.
     * Cached for 24 hours.
     *
     * @param symbol optional comma-separated symbols to look up (null = return first 5000)
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getCryptocurrencyMap(String symbol) {
        if (!isEnabled()) return Optional.empty();
        String key = "map|" + (symbol != null ? symbol.toUpperCase() : "all");
        CacheEntry<JsonObject> cached = listingsCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        StringBuilder url = new StringBuilder(BASE_URL).append("/v1/cryptocurrency/map?listing_status=active");
        if (symbol != null && !symbol.isBlank()) {
            url.append("&symbol=").append(urlEnc(symbol));
        }
        return cachedGet(url.toString(), listingsCache, key, MAP_TTL_MS);
    }

    // ─── 1b. Listings Latest ──────────────────────────────────────────────────

    /**
     * {@code GET /v3/cryptocurrency/listings/latest} — Top coins by market cap.
     * Cached for {@link #LISTINGS_TTL_MS}.
     *
     * @param limit  max results (1–5000, default 100)
     * @param sort   sort field (e.g. "market_cap", "volume_24h", "percent_change_24h")
     * @param convert convert currency (default "USD")
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getListingsLatest(int limit, String sort, String convert) {
        if (!isEnabled()) return Optional.empty();
        int lim = limit > 0 ? Math.min(limit, 5000) : 100;
        String srt = sort != null && !sort.isBlank() ? sort : "market_cap";
        String conv = convert != null && !convert.isBlank() ? convert : "USD";
        String key = "listings|" + lim + "|" + srt + "|" + conv;
        CacheEntry<JsonObject> cached = listingsCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = BASE_URL + "/v3/cryptocurrency/listings/latest"
                + "?limit=" + lim + "&sort=" + srt + "&convert=" + conv;
        return cachedGet(url, listingsCache, key, LISTINGS_TTL_MS);
    }

    // ─── 1c. Listings New ─────────────────────────────────────────────────────

    /**
     * {@code GET /v1/cryptocurrency/listings/new} — Newly listed cryptocurrencies.
     * Cached for {@link #LISTINGS_TTL_MS}.
     *
     * @param limit max results (default 20)
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getListingsNew(int limit) {
        if (!isEnabled()) return Optional.empty();
        int lim = limit > 0 ? limit : 20;
        String key = "new|" + lim;
        CacheEntry<JsonObject> cached = listingsCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = BASE_URL + "/v1/cryptocurrency/listings/new?limit=" + lim + "&convert=USD";
        return cachedGet(url, listingsCache, key, LISTINGS_TTL_MS);
    }

    // ─── 1d. Quotes Latest ────────────────────────────────────────────────────

    /**
     * {@code GET /v3/cryptocurrency/quotes/latest} — Real-time quotes for one or more coins.
     * Cached for {@link #QUOTES_TTL_MS}.
     *
     * @param symbol comma-separated symbols (e.g. "BTC,ETH") or IDs
     * @param convert convert currency (default "USD")
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getQuotesLatest(String symbol, String convert) {
        if (!isEnabled() || symbol == null || symbol.isBlank()) return Optional.empty();
        String conv = convert != null && !convert.isBlank() ? convert : "USD";
        String key = "ql|" + symbol.toUpperCase() + "|" + conv;
        CacheEntry<JsonObject> cached = quotesCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = BASE_URL + "/v3/cryptocurrency/quotes/latest"
                + "?symbol=" + urlEnc(symbol) + "&convert=" + conv;
        return cachedGet(url, quotesCache, key, QUOTES_TTL_MS);
    }

    // ─── 1e. OHLCV Latest ─────────────────────────────────────────────────────

    /**
     * {@code GET /v2/cryptocurrency/ohlcv/latest} — Latest OHLCV data.
     * Cached for {@link #QUOTES_TTL_MS}.
     *
     * @param symbol coin symbol (e.g. "BTC")
     * @param convert convert currency (default "USD")
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getOhlcvLatest(String symbol, String convert) {
        if (!isEnabled() || symbol == null || symbol.isBlank()) return Optional.empty();
        String conv = convert != null && !convert.isBlank() ? convert : "USD";
        String key = "ohlcv_latest|" + symbol.toUpperCase() + "|" + conv;
        CacheEntry<JsonObject> cached = quotesCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = BASE_URL + "/v2/cryptocurrency/ohlcv/latest"
                + "?symbol=" + urlEnc(symbol) + "&convert=" + conv;
        return cachedGet(url, quotesCache, key, QUOTES_TTL_MS);
    }

    // ─── 1f. OHLCV Historical ─────────────────────────────────────────────────

    /**
     * {@code GET /v2/cryptocurrency/ohlcv/historical} — Historical OHLCV candles.
     * Cached for {@link #QUOTES_TTL_MS}.
     *
     * @param symbol    coin symbol (e.g. "BTC") or ID
     * @param timeStart ISO 8601 start time
     * @param timeEnd   ISO 8601 end time
     * @param interval  candle interval (e.g. "daily", "1h", "1d")
     * @param count     max candles (default 10)
     * @param convert   convert currency (default "USD")
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getOhlcvHistorical(String symbol, String timeStart, String timeEnd,
                                                     String interval, int count, String convert) {
        if (!isEnabled() || symbol == null || symbol.isBlank()) return Optional.empty();
        String key = "ohlcv_hist|" + symbol + "|" + interval + "|" + count;
        CacheEntry<JsonObject> cached = quotesCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        StringBuilder url = new StringBuilder(BASE_URL)
                .append("/v2/cryptocurrency/ohlcv/historical")
                .append("?symbol=").append(urlEnc(symbol))
                .append("&convert=").append(convert != null ? convert : "USD");
        if (interval != null && !interval.isBlank()) url.append("&interval=").append(interval);
        if (timeStart != null && !timeStart.isBlank()) url.append("&time_start=").append(urlEnc(timeStart));
        if (timeEnd   != null && !timeEnd.isBlank())   url.append("&time_end=").append(urlEnc(timeEnd));
        if (count     > 0) url.append("&count=").append(Math.min(count, 500));

        return cachedGet(url.toString(), quotesCache, key, QUOTES_TTL_MS);
    }

    // ─── 1g. Market Pairs ─────────────────────────────────────────────────────

    /**
     * {@code GET /v2/cryptocurrency/market-pairs/latest} — Trading pairs for a coin.
     * Cached for {@link #MARKET_PAIRS_TTL_MS}.
     *
     * @param symbol coin symbol or ID
     * @param limit  max pairs (default 100)
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getMarketPairs(String symbol, int limit) {
        if (!isEnabled() || symbol == null || symbol.isBlank()) return Optional.empty();
        int lim = limit > 0 ? limit : 100;
        String key = "mpairs|" + symbol.toUpperCase() + "|" + lim;
        CacheEntry<JsonObject> cached = marketPairsCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = BASE_URL + "/v2/cryptocurrency/market-pairs/latest"
                + "?symbol=" + urlEnc(symbol) + "&limit=" + lim + "&convert=USD";
        return cachedGet(url, marketPairsCache, key, MARKET_PAIRS_TTL_MS);
    }

    // ─── 1h. Price Performance Stats ──────────────────────────────────────────

    /**
     * {@code GET /v2/cryptocurrency/price-performance-stats/latest} — Price performance.
     * Cached for {@link #PERF_STATS_TTL_MS}.
     *
     * @param symbol  coin symbol (e.g. "BTC,ETH")
     * @param timePeriod period: "all_time", "yesterday", "24h", "7d", "30d", "90d", "365d"
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getPricePerformanceStats(String symbol, String timePeriod) {
        if (!isEnabled() || symbol == null || symbol.isBlank()) return Optional.empty();
        String period = timePeriod != null && !timePeriod.isBlank() ? timePeriod : "24h";
        String key = "perf|" + symbol.toUpperCase() + "|" + period;
        CacheEntry<JsonObject> cached = perfStatsCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = BASE_URL + "/v2/cryptocurrency/price-performance-stats/latest"
                + "?symbol=" + urlEnc(symbol) + "&time_period=" + period + "&convert=USD";
        return cachedGet(url, perfStatsCache, key, PERF_STATS_TTL_MS);
    }

    // ─── 1i. Cryptocurrency Info (metadata) ───────────────────────────────────

    /**
     * {@code GET /v2/cryptocurrency/info} — Static metadata: logo, description, URLs.
     * Cached for {@link #INFO_TTL_MS}.
     * Also persisted to DB for offline fallback.
     *
     * @param symbol comma-separated symbols (e.g. "BTC,ETH")
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getCryptocurrencyInfo(String symbol) {
        if (!isEnabled() || symbol == null || symbol.isBlank()) return Optional.empty();
        String key = "info|" + symbol.toUpperCase();
        CacheEntry<JsonObject> cached = infoCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = BASE_URL + "/v2/cryptocurrency/info?symbol=" + urlEnc(symbol);
        Optional<JsonObject> result = http.getJson(url, null, THROTTLE_KEY, priceService.cmcHeaders());
        result.ifPresent(json -> {
            infoCache.put(key, new CacheEntry<>(json, INFO_TTL_MS));
            persistCryptoInfo(symbol, json);
        });
        return result;
    }

    // ─── 1j. Trending ────────────────────────────────────────────────────────

    /**
     * {@code GET /v1/cryptocurrency/trending/latest} — Currently trending coins.
     * Cached for {@link #TRENDING_TTL_MS}.
     *
     * @param limit max results (default 10)
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getTrendingLatest(int limit) {
        if (!isEnabled()) return Optional.empty();
        int lim = limit > 0 ? limit : 10;
        String key = "trending_latest|" + lim;
        CacheEntry<JsonObject> cached = trendingCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = BASE_URL + "/v1/cryptocurrency/trending/latest?limit=" + lim + "&convert=USD";
        return cachedGet(url, trendingCache, key, TRENDING_TTL_MS);
    }

    /**
     * {@code GET /v1/cryptocurrency/trending/gainers-losers} — Top gainers and losers.
     * Cached for {@link #TRENDING_TTL_MS}.
     *
     * @param limit  max per list (default 10)
     * @param timePeriod "1h", "24h", "7d" or "30d"
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getTrendingGainersLosers(int limit, String timePeriod) {
        if (!isEnabled()) return Optional.empty();
        int lim = limit > 0 ? limit : 10;
        String period = timePeriod != null && !timePeriod.isBlank() ? timePeriod : "24h";
        String key = "gainers_losers|" + lim + "|" + period;
        CacheEntry<JsonObject> cached = trendingCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = BASE_URL + "/v1/cryptocurrency/trending/gainers-losers"
                + "?limit=" + lim + "&time_period=" + period + "&convert=USD";
        return cachedGet(url, trendingCache, key, TRENDING_TTL_MS);
    }

    /**
     * {@code GET /v1/cryptocurrency/trending/most-visited} — Most visited on CMC.
     * Cached for {@link #TRENDING_TTL_MS}.
     *
     * @param limit max results (default 10)
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getTrendingMostVisited(int limit) {
        if (!isEnabled()) return Optional.empty();
        int lim = limit > 0 ? limit : 10;
        String key = "most_visited|" + lim;
        CacheEntry<JsonObject> cached = trendingCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = BASE_URL + "/v1/cryptocurrency/trending/most-visited?limit=" + lim + "&convert=USD";
        return cachedGet(url, trendingCache, key, TRENDING_TTL_MS);
    }

    // ─── 1k. Categories ──────────────────────────────────────────────────────

    /**
     * {@code GET /v1/cryptocurrency/categories} — All categories with market metrics.
     * Cached for {@link #CATEGORIES_TTL_MS}.
     *
     * @param limit max categories (default 100)
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getCategories(int limit) {
        if (!isEnabled()) return Optional.empty();
        int lim = limit > 0 ? limit : 100;
        String key = "categories|" + lim;
        CacheEntry<JsonObject> cached = categoriesCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = BASE_URL + "/v1/cryptocurrency/categories?limit=" + lim;
        return cachedGet(url, categoriesCache, key, CATEGORIES_TTL_MS);
    }

    /**
     * {@code GET /v1/cryptocurrency/category} — Single category details with coins.
     * Cached for {@link #CATEGORIES_TTL_MS}.
     *
     * @param id category ID (from /v1/cryptocurrency/categories)
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getCategoryDetail(String id) {
        if (!isEnabled() || id == null || id.isBlank()) return Optional.empty();
        String key = "category|" + id;
        CacheEntry<JsonObject> cached = categoriesCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = BASE_URL + "/v1/cryptocurrency/category?id=" + urlEnc(id) + "&convert=USD";
        return cachedGet(url, categoriesCache, key, CATEGORIES_TTL_MS);
    }

    // ─── 1l. Airdrops ─────────────────────────────────────────────────────────

    /**
     * {@code GET /v1/cryptocurrency/airdrops} — List current and upcoming airdrops.
     * Cached for {@link #AIRDROPS_TTL_MS}.
     *
     * @param status "UPCOMING", "ONGOING", or "ENDED"
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getAirdrops(String status) {
        if (!isEnabled()) return Optional.empty();
        String st = status != null && !status.isBlank() ? status : "ONGOING";
        String key = "airdrops|" + st;
        CacheEntry<JsonObject> cached = airdropCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = BASE_URL + "/v1/cryptocurrency/airdrops?status=" + st;
        return cachedGet(url, airdropCache, key, AIRDROPS_TTL_MS);
    }

    /**
     * {@code GET /v1/cryptocurrency/airdrop} — Single airdrop details.
     * Cached for {@link #AIRDROPS_TTL_MS}.
     *
     * @param id airdrop ID
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getAirdropDetail(String id) {
        if (!isEnabled() || id == null || id.isBlank()) return Optional.empty();
        String key = "airdrop|" + id;
        CacheEntry<JsonObject> cached = airdropCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = BASE_URL + "/v1/cryptocurrency/airdrop?id=" + urlEnc(id);
        return cachedGet(url, airdropCache, key, AIRDROPS_TTL_MS);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 2. EXCHANGE ENDPOINTS
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * {@code GET /v1/exchange/map} — Map exchange names/slugs to CMC IDs.
     * Cached for 24 hours. Also available keyless via public-api prefix.
     *
     * @param slug optional exchange slug (e.g. "binance"); null = return all
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getExchangeMap(String slug) {
        if (!isEnabled()) return Optional.empty();
        String key = "exmap|" + (slug != null ? slug : "all");
        CacheEntry<JsonObject> cached = exchangeCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        StringBuilder url = new StringBuilder(BASE_URL).append("/v1/exchange/map?listing_status=active");
        if (slug != null && !slug.isBlank()) url.append("&slug=").append(urlEnc(slug));
        return cachedGet(url.toString(), exchangeCache, key, MAP_TTL_MS);
    }

    /**
     * {@code GET /v1/exchange/listings/latest} — Top exchanges by volume.
     * Cached for {@link #EXCHANGE_TTL_MS}.
     *
     * @param limit  max results (default 100)
     * @param sort   sort field (default "volume_24h")
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getExchangeListings(int limit, String sort) {
        if (!isEnabled()) return Optional.empty();
        int lim = limit > 0 ? limit : 100;
        String srt = sort != null && !sort.isBlank() ? sort : "volume_24h";
        String key = "exlist|" + lim + "|" + srt;
        CacheEntry<JsonObject> cached = exchangeCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = BASE_URL + "/v1/exchange/listings/latest?limit=" + lim
                + "&sort=" + srt + "&convert=USD";
        return cachedGet(url, exchangeCache, key, EXCHANGE_TTL_MS);
    }

    /**
     * {@code GET /v1/exchange/info} — Exchange metadata (name, logo, description).
     * Cached for 1 hour.
     *
     * @param slug exchange slug (e.g. "binance")
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getExchangeInfo(String slug) {
        if (!isEnabled() || slug == null || slug.isBlank()) return Optional.empty();
        String key = "exinfo|" + slug;
        CacheEntry<JsonObject> cached = exchangeCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = BASE_URL + "/v1/exchange/info?slug=" + urlEnc(slug);
        return cachedGet(url, exchangeCache, key, INFO_TTL_MS);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 3. DEX / TOKEN ENDPOINTS
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * {@code GET /v4/dex/spot-pairs/latest} — DEX spot trading pairs.
     * Cached for {@link #DEX_TTL_MS}.
     *
     * @param platformId DEX platform ID (e.g. "ethereum"); null = all
     * @param limit      max pairs (default 100)
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getDexSpotPairs(String platformId, int limit) {
        if (!isEnabled()) return Optional.empty();
        int lim = limit > 0 ? limit : 100;
        String key = "dexpairs|" + (platformId != null ? platformId : "all") + "|" + lim;
        CacheEntry<JsonObject> cached = dexCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        StringBuilder url = new StringBuilder(BASE_URL)
                .append("/v4/dex/spot-pairs/latest?limit=").append(lim);
        if (platformId != null && !platformId.isBlank())
            url.append("&platform_id=").append(urlEnc(platformId));
        return cachedGet(url.toString(), dexCache, key, DEX_TTL_MS);
    }

    /**
     * {@code GET /v1/dex/search} — Search DEX tokens and pairs by name, symbol, or address.
     * Cached for {@link #DEX_TTL_MS}.
     *
     * @param keyword search keyword
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> searchDex(String keyword) {
        if (!isEnabled() || keyword == null || keyword.isBlank()) return Optional.empty();
        String key = "dexsearch|" + keyword.toLowerCase();
        CacheEntry<JsonObject> cached = dexCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = BASE_URL + "/v1/dex/search?keyword=" + urlEnc(keyword);
        return cachedGet(url, dexCache, key, DEX_TTL_MS);
    }

    /**
     * {@code GET /v1/dex/new/list} — Recently launched DEX tokens.
     * Cached for {@link #DEX_TTL_MS}.
     *
     * @param platformId DEX platform ID (null = all)
     * @param limit      max results (default 50)
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getDexNewTokens(String platformId, int limit) {
        if (!isEnabled()) return Optional.empty();
        int lim = limit > 0 ? limit : 50;
        String key = "dexnew|" + (platformId != null ? platformId : "all") + "|" + lim;
        CacheEntry<JsonObject> cached = dexCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        StringBuilder url = new StringBuilder(BASE_URL)
                .append("/v1/dex/new/list?pageSize=").append(lim);
        if (platformId != null && !platformId.isBlank())
            url.append("&platformId=").append(urlEnc(platformId));
        return cachedGet(url.toString(), dexCache, key, DEX_TTL_MS);
    }

    /**
     * {@code GET /v1/dex/meme/list} — Meme tokens on DEX.
     * Cached for {@link #DEX_TTL_MS}.
     *
     * @param platformId DEX platform ID (null = all)
     * @param limit      max results (default 50)
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getDexMemeTokens(String platformId, int limit) {
        if (!isEnabled()) return Optional.empty();
        int lim = limit > 0 ? limit : 50;
        String key = "dexmeme|" + (platformId != null ? platformId : "all") + "|" + lim;
        CacheEntry<JsonObject> cached = dexCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        StringBuilder url = new StringBuilder(BASE_URL)
                .append("/v1/dex/meme/list?pageSize=").append(lim);
        if (platformId != null && !platformId.isBlank())
            url.append("&platformId=").append(urlEnc(platformId));
        return cachedGet(url.toString(), dexCache, key, DEX_TTL_MS);
    }

    /**
     * {@code GET /v1/dex/gainer-loser/list} — Top gainers and losers on DEX.
     * Cached for {@link #DEX_TTL_MS}.
     *
     * @param platformId DEX platform ID (null = all)
     * @param interval   time interval: "1h", "4h", "12h", "24h" (default "24h")
     * @param type       "gainers" or "losers" (default "gainers")
     * @param limit      max results (default 20)
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getDexGainersLosers(String platformId, String interval,
                                                      String type, int limit) {
        if (!isEnabled()) return Optional.empty();
        int lim = limit > 0 ? limit : 20;
        String intv = interval != null && !interval.isBlank() ? interval : "24h";
        String tp   = type    != null && !type.isBlank()     ? type     : "gainers";
        String key  = "dexgl|" + (platformId != null ? platformId : "all")
                + "|" + intv + "|" + tp + "|" + lim;
        CacheEntry<JsonObject> cached = dexCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        StringBuilder url = new StringBuilder(BASE_URL)
                .append("/v1/dex/gainer-loser/list")
                .append("?interval=").append(intv)
                .append("&type=").append(tp)
                .append("&pageSize=").append(lim);
        if (platformId != null && !platformId.isBlank())
            url.append("&platformId=").append(urlEnc(platformId));
        return cachedGet(url.toString(), dexCache, key, DEX_TTL_MS);
    }

    /**
     * {@code GET /v1/dex/security/detail} — Token security analysis (honeypot, tax, etc.).
     * Cached for 10 minutes.
     *
     * @param address    token contract address
     * @param platformId DEX platform (e.g. "ethereum")
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getDexSecurityDetail(String address, String platformId) {
        if (!isEnabled() || address == null || address.isBlank()) return Optional.empty();
        String key = "dexsec|" + address.toLowerCase() + "|" + (platformId != null ? platformId : "");
        CacheEntry<JsonObject> cached = dexCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        StringBuilder url = new StringBuilder(BASE_URL)
                .append("/v1/dex/security/detail?address=").append(urlEnc(address));
        if (platformId != null && !platformId.isBlank())
            url.append("&platformId=").append(urlEnc(platformId));
        return cachedGet(url.toString(), dexCache, key, 10 * 60_000L);
    }

    /**
     * {@code GET /v1/dex/platform/list} — List all DEX platforms (chains).
     * Keyless endpoint — no API key required.
     * Cached for 24 hours.
     *
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getDexPlatformList() {
        if (platformListCache != null && !platformListCache.isExpired())
            return Optional.ofNullable(platformListCache.value);

        String url = PUBLIC_BASE_URL + "/v1/dex/platform/list";
        Optional<JsonObject> result = http.getJson(url, null, THROTTLE_KEY, priceService.noAuthHeaders());
        result.ifPresent(json -> platformListCache = new CacheEntry<>(json, MAP_TTL_MS));
        return result;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 4. K-LINE / OHLCV (KEYLESS)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * {@code GET /v1/k-line/candles} — DEX K-line candles for a trading pair.
     * Keyless endpoint — no API key required.
     * Cached for {@link #KLINE_TTL_MS}.
     *
     * Response: each candle is an array {@code [open, high, low, close, volume, timestamp, traders]}.
     *
     * @param platform DEX platform (e.g. "ethereum")
     * @param address  pair/token contract address
     * @param interval candle interval: "1min","5min","15min","30min","1h","2h","4h","6h","8h",
     *                 "12h","1d","3d","1w","1m"
     * @param from     start UNIX timestamp (seconds)
     * @param to       end UNIX timestamp (seconds)
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getKLineCandles(String platform, String address,
                                                  String interval, long from, long to) {
        if (platform == null || address == null) return Optional.empty();
        String key = "kcandles|" + platform + "|" + address + "|" + interval + "|" + from + "|" + to;
        CacheEntry<JsonObject> cached = klineCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = PUBLIC_BASE_URL + "/v1/k-line/candles"
                + "?platform=" + urlEnc(platform)
                + "&address=" + urlEnc(address)
                + "&interval=" + urlEnc(interval)
                + "&from=" + from
                + "&to=" + to;
        Optional<JsonObject> result = http.getJson(url, null, THROTTLE_KEY, priceService.noAuthHeaders());
        result.ifPresent(json -> klineCache.put(key, new CacheEntry<>(json, KLINE_TTL_MS)));
        return result;
    }

    /**
     * {@code GET /v1/k-line/points} — DEX price points for a trading pair.
     * Keyless endpoint — no API key required.
     * Cached for {@link #KLINE_TTL_MS}.
     *
     * Response: each point is an array {@code [price, volume, timestamp]}.
     *
     * @param platform DEX platform (e.g. "ethereum")
     * @param address  pair/token contract address
     * @param interval time interval
     * @param from     start UNIX timestamp (seconds)
     * @param to       end UNIX timestamp (seconds)
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getKLinePoints(String platform, String address,
                                                String interval, long from, long to) {
        if (platform == null || address == null) return Optional.empty();
        String key = "kpoints|" + platform + "|" + address + "|" + interval + "|" + from + "|" + to;
        CacheEntry<JsonObject> cached = klineCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = PUBLIC_BASE_URL + "/v1/k-line/points"
                + "?platform=" + urlEnc(platform)
                + "&address=" + urlEnc(address)
                + "&interval=" + urlEnc(interval)
                + "&from=" + from
                + "&to=" + to;
        Optional<JsonObject> result = http.getJson(url, null, THROTTLE_KEY, priceService.noAuthHeaders());
        result.ifPresent(json -> klineCache.put(key, new CacheEntry<>(json, KLINE_TTL_MS)));
        return result;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 5. GLOBAL METRICS
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * {@code GET /v1/global-metrics/quotes/latest} — Total market cap, BTC dominance.
     * Cached for {@link #GLOBAL_TTL_MS}.
     *
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getGlobalMetrics() {
        if (!isEnabled()) return Optional.empty();
        String key = "global";
        CacheEntry<JsonObject> cached = globalCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = BASE_URL + "/v1/global-metrics/quotes/latest?convert=USD";
        return cachedGet(url, globalCache, key, GLOBAL_TTL_MS);
    }

    /**
     * {@code GET /v3/fear-and-greed/latest} — Current Fear &amp; Greed Index.
     * Keyless endpoint — no API key required.
     * Cached for {@link #FEAR_GREED_TTL_MS}.
     *
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getFearAndGreedLatest() {
        String key = "fg_latest";
        CacheEntry<JsonObject> cached = fearGreedCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = PUBLIC_BASE_URL + "/v3/fear-and-greed/latest";
        Optional<JsonObject> result = http.getJson(url, null, THROTTLE_KEY, priceService.noAuthHeaders());
        result.ifPresent(json -> fearGreedCache.put(key, new CacheEntry<>(json, FEAR_GREED_TTL_MS)));
        return result;
    }

    /**
     * {@code GET /v3/fear-and-greed/historical} — Historical Fear &amp; Greed values.
     * Keyless endpoint — no API key required.
     * Cached for {@link #FEAR_GREED_TTL_MS}.
     *
     * @param limit  number of historical data points (default 30)
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getFearAndGreedHistorical(int limit) {
        int lim = limit > 0 ? limit : 30;
        String key = "fg_hist|" + lim;
        CacheEntry<JsonObject> cached = fearGreedCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = PUBLIC_BASE_URL + "/v3/fear-and-greed/historical?limit=" + lim;
        Optional<JsonObject> result = http.getJson(url, null, THROTTLE_KEY, priceService.noAuthHeaders());
        result.ifPresent(json -> fearGreedCache.put(key, new CacheEntry<>(json, FEAR_GREED_TTL_MS)));
        return result;
    }

    /**
     * {@code GET /v1/altcoin-season-index/latest} — Altcoin Season Index.
     * Cached for {@link #ALTCOIN_IDX_TTL_MS}.
     *
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getAltcoinSeasonIndex() {
        if (!isEnabled()) return Optional.empty();
        String key = "altcoin_season";
        CacheEntry<JsonObject> cached = altcoinIdxCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = BASE_URL + "/v1/altcoin-season-index/latest";
        return cachedGet(url, altcoinIdxCache, key, ALTCOIN_IDX_TTL_MS);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 6. CMC INDEX (KEYLESS)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * {@code GET /v3/index/cmc100-latest} — CMC100 current value and constituents.
     * Keyless endpoint.
     * Cached for {@link #INDEX_TTL_MS}.
     *
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getCmc100Latest() {
        String key = "cmc100_latest";
        CacheEntry<JsonObject> cached = indexCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = PUBLIC_BASE_URL + "/v3/index/cmc100-latest";
        Optional<JsonObject> result = http.getJson(url, null, THROTTLE_KEY, priceService.noAuthHeaders());
        result.ifPresent(json -> indexCache.put(key, new CacheEntry<>(json, INDEX_TTL_MS)));
        return result;
    }

    /**
     * {@code GET /v3/index/cmc100-historical} — CMC100 index history.
     * Keyless endpoint.
     * Cached for {@link #INDEX_TTL_MS}.
     *
     * @param interval "5m", "15m", or "daily"
     * @param count    number of data points (default 30)
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getCmc100Historical(String interval, int count) {
        String intv = interval != null && !interval.isBlank() ? interval : "daily";
        int cnt = count > 0 ? count : 30;
        String key = "cmc100_hist|" + intv + "|" + cnt;
        CacheEntry<JsonObject> cached = indexCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = PUBLIC_BASE_URL + "/v3/index/cmc100-historical?interval=" + intv + "&count=" + cnt;
        Optional<JsonObject> result = http.getJson(url, null, THROTTLE_KEY, priceService.noAuthHeaders());
        result.ifPresent(json -> indexCache.put(key, new CacheEntry<>(json, INDEX_TTL_MS)));
        return result;
    }

    /**
     * {@code GET /v3/index/cmc20-latest} — CMC20 current value.
     * Keyless endpoint.
     * Cached for {@link #INDEX_TTL_MS}.
     *
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getCmc20Latest() {
        String key = "cmc20_latest";
        CacheEntry<JsonObject> cached = indexCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = PUBLIC_BASE_URL + "/v3/index/cmc20-latest";
        Optional<JsonObject> result = http.getJson(url, null, THROTTLE_KEY, priceService.noAuthHeaders());
        result.ifPresent(json -> indexCache.put(key, new CacheEntry<>(json, INDEX_TTL_MS)));
        return result;
    }

    /**
     * {@code GET /v3/index/cmc20-historical} — CMC20 index history.
     * Keyless endpoint.
     * Cached for {@link #INDEX_TTL_MS}.
     *
     * @param interval "5m", "15m", or "daily"
     * @param count    number of data points (default 30)
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getCmc20Historical(String interval, int count) {
        String intv = interval != null && !interval.isBlank() ? interval : "daily";
        int cnt = count > 0 ? count : 30;
        String key = "cmc20_hist|" + intv + "|" + cnt;
        CacheEntry<JsonObject> cached = indexCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = PUBLIC_BASE_URL + "/v3/index/cmc20-historical?interval=" + intv + "&count=" + cnt;
        Optional<JsonObject> result = http.getJson(url, null, THROTTLE_KEY, priceService.noAuthHeaders());
        result.ifPresent(json -> indexCache.put(key, new CacheEntry<>(json, INDEX_TTL_MS)));
        return result;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // 7. TOOLS / UTILITIES
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * {@code GET /v2/tools/price-conversion} — Convert between any two currencies.
     * Cached for {@link #CONVERSION_TTL_MS}.
     *
     * @param amount  amount to convert
     * @param symbol  source currency symbol (e.g. "BTC")
     * @param convert target currency (e.g. "USD", "ETH")
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> convertPrice(double amount, String symbol, String convert) {
        if (!isEnabled() || symbol == null || symbol.isBlank()) return Optional.empty();
        String conv = convert != null && !convert.isBlank() ? convert : "USD";
        String key = "conv|" + amount + "|" + symbol + "|" + conv;
        CacheEntry<JsonObject> cached = conversionCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = BASE_URL + "/v2/tools/price-conversion"
                + "?amount=" + amount + "&symbol=" + urlEnc(symbol) + "&convert=" + urlEnc(conv);
        return cachedGet(url, conversionCache, key, CONVERSION_TTL_MS);
    }

    /**
     * {@code GET /v1/fiat/map} — Map fiat currencies to CMC IDs.
     * Cached for 24 hours.
     *
     * @param includeMetals whether to include precious metals (default false)
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getFiatMap(boolean includeMetals) {
        if (fiatMapCache != null && !fiatMapCache.isExpired())
            return Optional.ofNullable(fiatMapCache.value);

        String url = BASE_URL + "/v1/fiat/map?include_metals=" + includeMetals;
        Optional<JsonObject> result = http.getJson(url, null, THROTTLE_KEY, priceService.cmcHeaders());
        result.ifPresent(json -> fiatMapCache = new CacheEntry<>(json, FIAT_MAP_TTL_MS));
        return result;
    }

    /**
     * {@code GET /v1/key/info} — API key plan details and current usage statistics.
     * Not cached (or short TTL) to give real-time credit usage.
     * Does not count toward credit limit.
     *
     * @return raw JSON response, or empty
     */
    public Optional<JsonObject> getKeyInfo() {
        if (!isEnabled()) return Optional.empty();
        if (keyInfoCache != null && !keyInfoCache.isExpired())
            return Optional.ofNullable(keyInfoCache.value);

        String url = BASE_URL + "/v1/key/info";
        Optional<JsonObject> result = http.getJson(url, null, THROTTLE_KEY, priceService.cmcHeaders());
        result.ifPresent(json -> keyInfoCache = new CacheEntry<>(json, 30_000L)); // 30 sec
        return result;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SYMBOL RESOLUTION CONVENIENCE
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Convenience: resolve a symbol to its CMC numeric ID.
     * Delegates to {@link CoinMarketCapPriceService#resolveCoinId(String)}.
     */
    public Integer resolveCoinId(String symbol) {
        return priceService.resolveCoinId(symbol);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // DB PERSISTENCE
    // ═════════════════════════════════════════════════════════════════════════

    private void persistCryptoInfo(String symbol, JsonObject json) {
        try {
            ensureCryptoInfoTable();
            if (!json.has("data")) return;
            JsonObject data = json.getAsJsonObject("data");
            String upperSym = symbol.toUpperCase();
            if (!data.has(upperSym)) {
                // Iterate keys to find first match
                for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
                    if (entry.getValue().isJsonObject()) {
                        saveCryptoInfoRow(entry.getValue().getAsJsonObject());
                        break;
                    }
                }
            } else {
                JsonElement el = data.get(upperSym);
                if (el.isJsonObject()) saveCryptoInfoRow(el.getAsJsonObject());
            }
        } catch (Exception e) {
            log.warn("CMC crypto info persist error: {}", e.getMessage());
        }
    }

    private void saveCryptoInfoRow(JsonObject info) {
        String upsert = isPostgres()
                ? """
                  INSERT INTO cmc_crypto_info (cmc_id, symbol, name, slug, category, description, logo, snapshot_time)
                  VALUES (?,?,?,?,?,?,?,?)
                  ON CONFLICT (cmc_id) DO UPDATE SET
                    symbol        = EXCLUDED.symbol,
                    name          = EXCLUDED.name,
                    slug          = EXCLUDED.slug,
                    category      = EXCLUDED.category,
                    description   = EXCLUDED.description,
                    logo          = EXCLUDED.logo,
                    snapshot_time = EXCLUDED.snapshot_time
                  """
                : """
                  INSERT OR REPLACE INTO cmc_crypto_info
                    (cmc_id, symbol, name, slug, category, description, logo, snapshot_time)
                  VALUES (?,?,?,?,?,?,?,?)
                  """;
        jdbc.update(upsert,
                info.has("id")          ? info.get("id").getAsInt()         : 0,
                info.has("symbol")      ? info.get("symbol").getAsString()  : "",
                info.has("name")        ? info.get("name").getAsString()    : "",
                info.has("slug")        ? info.get("slug").getAsString()    : "",
                info.has("category")    ? info.get("category").getAsString(): "",
                info.has("description") ? info.get("description").getAsString() : "",
                info.has("logo")        ? info.get("logo").getAsString()    : "",
                Timestamp.valueOf(LocalDateTime.now())
        );
    }

    private void ensureCryptoInfoTable() {
        if (!ensuredTables.add("cmc_crypto_info")) return;
        String ddl = isPostgres()
                ? """
                  CREATE TABLE IF NOT EXISTS cmc_crypto_info (
                    cmc_id        INTEGER PRIMARY KEY,
                    symbol        TEXT,
                    name          TEXT,
                    slug          TEXT,
                    category      TEXT,
                    description   TEXT,
                    logo          TEXT,
                    snapshot_time TIMESTAMP
                  )
                  """
                : """
                  CREATE TABLE IF NOT EXISTS cmc_crypto_info (
                    cmc_id        INTEGER PRIMARY KEY,
                    symbol        TEXT,
                    name          TEXT,
                    slug          TEXT,
                    category      TEXT,
                    description   TEXT,
                    logo          TEXT,
                    snapshot_time TEXT
                  )
                  """;
        jdbc.execute(ddl);
    }

    private boolean isPostgres() {
        try {
            String url = jdbc.getDataSource() != null
                    ? jdbc.getDataSource().getConnection().getMetaData().getURL() : "";
            return url.startsWith("jdbc:postgresql");
        } catch (Exception e) { return false; }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═════════════════════════════════════════════════════════════════════════

    /** Generic GET → cache helper. */
    private Optional<JsonObject> cachedGet(String url, Map<String, CacheEntry<JsonObject>> cache,
                                             String key, long ttlMs) {
        Optional<JsonObject> result = http.getJson(url, null, THROTTLE_KEY, priceService.cmcHeaders());
        result.ifPresent(json -> cache.put(key, new CacheEntry<>(json, ttlMs)));
        return result;
    }

    private static String urlEnc(String s) {
        if (s == null) return "";
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    // ── Cache helper ──────────────────────────────────────────────────────────

    private static final class CacheEntry<T> {
        final T    value;
        final long expiresAt;

        CacheEntry(T value, long ttlMs) {
            this.value     = value;
            this.expiresAt = System.currentTimeMillis() + ttlMs;
        }

        boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
    }
}
