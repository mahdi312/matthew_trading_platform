package com.mst.matt.tradingplatformapp.service.price;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import com.mst.matt.tradingplatformapp.service.price.api.finnhub.*;
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
 * Finnhub market-data + analytics service — complete free-tier REST API integration.
 *
 * <h3>Free-tier endpoints covered:</h3>
 * <ul>
 *   <li>{@code GET /quote}                       — real-time quote (delegates to FinnhubPriceService)</li>
 *   <li>{@code GET /stock/candle}                — OHLCV candles (delegates to FinnhubPriceService)</li>
 *   <li>{@code GET /search}                      — symbol search</li>
 *   <li>{@code GET /stock/profile2}              — company profile</li>
 *   <li>{@code GET /stock/peers}                 — peer companies</li>
 *   <li>{@code GET /stock/symbol}                — list all symbols on an exchange</li>
 *   <li>{@code GET /stock/dividend}              — dividend history</li>
 *   <li>{@code GET /stock/split}                 — stock split history</li>
 *   <li>{@code GET /stock/earnings}              — earnings surprises</li>
 *   <li>{@code GET /stock/recommendation}        — analyst recommendation trends</li>
 *   <li>{@code GET /stock/price-target}          — analyst price targets</li>
 *   <li>{@code GET /stock/insider-transactions}  — insider trades</li>
 *   <li>{@code GET /stock/ownership}             — institutional holdings</li>
 *   <li>{@code GET /stock/senate-trading}        — US senator trade disclosures</li>
 *   <li>{@code GET /stock/fund-ownership}        — mutual fund / ETF holders</li>
 *   <li>{@code GET /etf/profile}                 — ETF profile</li>
 *   <li>{@code GET /stock/etf-holdings}          — ETF holdings</li>
 *   <li>{@code GET /stock/metric}                — basic financial metrics (P/E, EPS, Beta, etc.)</li>
 *   <li>{@code GET /forex/symbol}               — list forex pairs</li>
 *   <li>{@code GET /forex/candle}               — forex candles (delegates to FinnhubPriceService)</li>
 *   <li>{@code GET /forex/rates}                — real-time FX rates</li>
 *   <li>{@code GET /crypto/symbol}              — list crypto pairs</li>
 *   <li>{@code GET /crypto/candle}              — crypto candles (delegates to FinnhubPriceService)</li>
 *   <li>{@code GET /news}                       — market news by category</li>
 *   <li>{@code GET /company-news}              — company-specific news</li>
 *   <li>{@code GET /news-sentiment}            — news sentiment score</li>
 *   <li>{@code GET /press-releases}            — official press releases</li>
 *   <li>{@code GET /calendar/ipo}              — IPO calendar</li>
 *   <li>{@code GET /calendar/earnings}         — earnings calendar</li>
 *   <li>{@code GET /calendar/economic}         — economic calendar</li>
 *   <li>{@code GET /calendar/dividend}         — dividend calendar</li>
 *   <li>{@code GET /calendar/split}            — split calendar</li>
 *   <li>{@code GET /indicator/sma|ema|rsi|macd|stoch|bb} — technical indicators</li>
 *   <li>{@code GET /scan/pattern}              — candlestick pattern scan</li>
 *   <li>{@code GET /economic/code}             — list economic codes</li>
 *   <li>{@code GET /economic}                  — economic data by code</li>
 *   <li>{@code GET /insider-sentiment}         — aggregated insider sentiment</li>
 *   <li>{@code GET /esg}                       — ESG scores</li>
 *   <li>{@code GET /stock/supply-chain}        — supply chain (customers/suppliers)</li>
 *   <li>{@code GET /index/constituents}        — index component symbols</li>
 *   <li>{@code GET /index/candle}              — index candles</li>
 * </ul>
 *
 * <h3>Rate limiting:</h3>
 * Free-tier: 60 requests/minute. All calls share the {@code "finnhub"} throttle bucket
 * registered by {@link FinnhubPriceService}.
 *
 * <h3>Caching:</h3>
 * All responses are in-memory cached at appropriate TTLs to stay within the free-tier limit.
 *
 * <h3>DB persistence:</h3>
 * Company profiles and quote snapshots are persisted to {@code finnhub_company_profile}
 * for offline fallback.
 */
@Service
public class FinnhubMarketService {

    private static final Logger log = LoggerFactory.getLogger(FinnhubMarketService.class);

    // ── Base URL ──────────────────────────────────────────────────────────────
    /** Package-private and non-final so tests can redirect to MockWebServer. */
    String baseUrl = "https://finnhub.io/api/v1";

    private static final String THROTTLE_KEY = "finnhub";

    // ── Cache TTLs ─────────────────────────────────────────────────────────────
    private static final long QUOTE_CACHE_TTL_MS       = 2  * 60_000L;   // 2 min
    private static final long PROFILE_CACHE_TTL_MS     = 60 * 60_000L;   // 1 hour
    private static final long PEERS_CACHE_TTL_MS       = 60 * 60_000L;   // 1 hour
    private static final long SEARCH_CACHE_TTL_MS      = 5  * 60_000L;   // 5 min
    private static final long SYMBOLS_CACHE_TTL_MS     = 24 * 60_000L * 60; // 24 hours
    private static final long DIVIDEND_CACHE_TTL_MS    = 60 * 60_000L;   // 1 hour
    private static final long SPLIT_CACHE_TTL_MS       = 60 * 60_000L;   // 1 hour
    private static final long EARNINGS_CACHE_TTL_MS    = 60 * 60_000L;   // 1 hour
    private static final long RECOMMEND_CACHE_TTL_MS   = 60 * 60_000L;   // 1 hour
    private static final long PRICE_TARGET_TTL_MS      = 60 * 60_000L;   // 1 hour
    private static final long INSIDER_CACHE_TTL_MS     = 30 * 60_000L;   // 30 min
    private static final long OWNERSHIP_CACHE_TTL_MS   = 30 * 60_000L;   // 30 min
    private static final long SENATE_CACHE_TTL_MS      = 60 * 60_000L;   // 1 hour
    private static final long FUND_OWN_CACHE_TTL_MS    = 30 * 60_000L;   // 30 min
    private static final long ETF_PROFILE_TTL_MS       = 60 * 60_000L;   // 1 hour
    private static final long ETF_HOLDINGS_TTL_MS      = 60 * 60_000L;   // 1 hour
    private static final long METRICS_CACHE_TTL_MS     = 15 * 60_000L;   // 15 min
    private static final long FOREX_RATES_TTL_MS       = 2  * 60_000L;   // 2 min
    private static final long NEWS_CACHE_TTL_MS        = 5  * 60_000L;   // 5 min
    private static final long SENTIMENT_CACHE_TTL_MS   = 10 * 60_000L;   // 10 min
    private static final long CALENDAR_CACHE_TTL_MS    = 30 * 60_000L;   // 30 min
    private static final long INDICATOR_CACHE_TTL_MS   = 5  * 60_000L;   // 5 min
    private static final long ECONOMIC_CACHE_TTL_MS    = 60 * 60_000L;   // 1 hour
    private static final long ESG_CACHE_TTL_MS         = 60 * 60_000L;   // 1 hour
    private static final long INDEX_CACHE_TTL_MS       = 60 * 60_000L;   // 1 hour
    private static final long SUPPLY_CHAIN_TTL_MS      = 60 * 60_000L;   // 1 hour
    private static final long INSIDER_SENT_TTL_MS      = 30 * 60_000L;   // 30 min
    private static final long PRESS_CACHE_TTL_MS       = 15 * 60_000L;   // 15 min
    private static final long PATTERN_CACHE_TTL_MS     = 5  * 60_000L;   // 5 min

    private final HttpJsonClient       http;
    private final MarketApiProperties  keys;
    private final JdbcTemplate         jdbc;
    private final Gson                 gson = new Gson();

    // ── In-memory caches ───────────────────────────────────────────────────────
    private final Map<String, CacheEntry<FinnhubCompanyProfile>>        profileCache      = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<String>>>                 peersCache        = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<FinnhubSearchResult>>          searchCache       = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<FinnhubSymbolEntry>>>     symbolsCache      = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<FinnhubDividend>>>        dividendCache     = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<FinnhubSplit>>>           splitCache        = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<FinnhubEarningsSurprise>>> earningsCache    = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<FinnhubRecommendationTrend>>> recCache      = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<FinnhubPriceTarget>>           priceTargetCache  = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<FinnhubInsiderTransaction>>    insiderCache      = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<FinnhubInstitutionalOwnership>> ownershipCache   = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<FinnhubSenateTrade>>           senateCache       = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<FinnhubFundOwnership>>         fundOwnCache      = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<FinnhubEtfProfile>>            etfProfileCache   = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<FinnhubEtfHolding>>            etfHoldingsCache  = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<FinnhubBasicFinancials>>       metricsCache      = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<FinnhubForexRate>>             forexRatesCache   = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<FinnhubNewsArticle>>>     newsCache         = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<FinnhubNewsSentiment>>         sentimentCache    = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<FinnhubEarningsCalendarEvent>> earningsCalCache  = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<FinnhubIpoEvent>>              ipoCalCache       = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<JsonObject>>                   econCalCache      = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<JsonObject>>                   dividCalCache     = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<JsonObject>>                   splitCalCache     = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<FinnhubTechnicalIndicator>>    indicatorCache    = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<JsonObject>>                   patternCache      = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<FinnhubEconomicData>>>    economicCache     = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<FinnhubEsgScore>>              esgCache          = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<FinnhubIndexConstituents>>     indexCache        = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<JsonObject>>                   supplyChainCache  = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<FinnhubInsiderSentiment>>      insiderSentCache  = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<JsonObject>>                   pressCache        = new ConcurrentHashMap<>();
    private volatile CacheEntry<JsonArray>                               econCodesCache;

    private final Set<String> ensuredTables = ConcurrentHashMap.newKeySet();

    @Autowired
    public FinnhubMarketService(HttpJsonClient http,
                                MarketApiProperties keys,
                                JdbcTemplate jdbc) {
        this.http = http;
        this.keys = keys;
        this.jdbc = jdbc;
    }

    public boolean isEnabled() {
        return keys.hasFinnhubKey();
    }

    // ─── Symbol Search ────────────────────────────────────────────────────────

    /**
     * {@code GET /search?q=...} — Search for symbols by keyword.
     * Cached for {@link #SEARCH_CACHE_TTL_MS}.
     *
     * @param query search term (ticker or company name)
     * @return search result with matches, or empty on error
     */
    public Optional<FinnhubSearchResult> search(String query) {
        if (!isEnabled() || query == null || query.isBlank()) return Optional.empty();
        String key = query.trim().toLowerCase();
        CacheEntry<FinnhubSearchResult> cached = searchCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("/search", "q=" + urlEnc(query.trim()));
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, FinnhubSearchResult.class))
                .filter(r -> r.result() != null && !r.result().isEmpty())
                .map(result -> {
                    searchCache.put(key, new CacheEntry<>(result, SEARCH_CACHE_TTL_MS));
                    return result;
                });
    }

    /**
     * Returns the best single match for a symbol lookup.
     */
    public Optional<FinnhubSearchResult.Match> bestMatch(String query) {
        return search(query)
                .flatMap(r -> r.result() == null || r.result().isEmpty()
                        ? Optional.empty()
                        : Optional.of(r.result().get(0)));
    }

    // ─── Stock Symbols ─────────────────────────────────────────────────────────

    /**
     * {@code GET /stock/symbol?exchange=...} — List all stock symbols on an exchange.
     * Cached for 24 hours.
     *
     * @param exchange exchange code e.g. "US", "NASDAQ", "NYSE"
     * @return list of symbol entries, or empty list on error
     */
    public List<FinnhubSymbolEntry> getStockSymbols(String exchange) {
        if (!isEnabled()) return Collections.emptyList();
        String key = exchange.toUpperCase();
        CacheEntry<List<FinnhubSymbolEntry>> cached = symbolsCache.get(key);
        if (cached != null && !cached.isExpired()) return cached.value;

        String url = buildUrl("/stock/symbol", "exchange=" + urlEnc(exchange));
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> {
                    if (!root.isJsonArray()) return Collections.<FinnhubSymbolEntry>emptyList();
                    List<FinnhubSymbolEntry> list = new ArrayList<>();
                    root.getAsJsonArray().forEach(el -> {
                        if (el.isJsonObject())
                            list.add(gson.fromJson(el, FinnhubSymbolEntry.class));
                    });
                    return list;
                })
                .map(list -> {
                    symbolsCache.put(key, new CacheEntry<>(list, SYMBOLS_CACHE_TTL_MS));
                    return list;
                })
                .orElse(Collections.emptyList());
    }

    /**
     * {@code GET /forex/symbol?exchange=...} — List forex pairs on an exchange.
     * Cached for 24 hours.
     *
     * @param exchange e.g. "OANDA" (default if null)
     * @return list of forex symbol entries
     */
    public List<FinnhubSymbolEntry> getForexSymbols(String exchange) {
        if (!isEnabled()) return Collections.emptyList();
        String exc = (exchange != null && !exchange.isBlank()) ? exchange : "OANDA";
        String key = "forex|" + exc.toUpperCase();
        CacheEntry<List<FinnhubSymbolEntry>> cached = symbolsCache.get(key);
        if (cached != null && !cached.isExpired()) return cached.value;

        String url = buildUrl("/forex/symbol", "exchange=" + urlEnc(exc));
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> parseSymbolArray(root))
                .map(list -> {
                    symbolsCache.put(key, new CacheEntry<>(list, SYMBOLS_CACHE_TTL_MS));
                    return list;
                })
                .orElse(Collections.emptyList());
    }

    /**
     * {@code GET /crypto/symbol?exchange=...} — List crypto pairs on an exchange.
     * Cached for 24 hours.
     *
     * @param exchange e.g. "BINANCE", "COINBASE", "KRAKEN"
     * @return list of crypto symbol entries
     */
    public List<FinnhubSymbolEntry> getCryptoSymbols(String exchange) {
        if (!isEnabled()) return Collections.emptyList();
        String exc = (exchange != null && !exchange.isBlank()) ? exchange : "BINANCE";
        String key = "crypto|" + exc.toUpperCase();
        CacheEntry<List<FinnhubSymbolEntry>> cached = symbolsCache.get(key);
        if (cached != null && !cached.isExpired()) return cached.value;

        String url = buildUrl("/crypto/symbol", "exchange=" + urlEnc(exc));
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> parseSymbolArray(root))
                .map(list -> {
                    symbolsCache.put(key, new CacheEntry<>(list, SYMBOLS_CACHE_TTL_MS));
                    return list;
                })
                .orElse(Collections.emptyList());
    }

    // ─── Company Profile ──────────────────────────────────────────────────────

    /**
     * {@code GET /stock/profile2?symbol=...} — Company Profile.
     * Cached for {@link #PROFILE_CACHE_TTL_MS}.
     * Also persisted to DB for offline fallback.
     *
     * @param symbol stock ticker
     * @return company profile, or empty on error
     */
    public Optional<FinnhubCompanyProfile> getCompanyProfile(String symbol) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<FinnhubCompanyProfile> cached = profileCache.get(sym);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        // Try DB fallback
        Optional<FinnhubCompanyProfile> dbResult = loadProfileFromDb(sym);

        String url = buildUrl("/stock/profile2", "symbol=" + sym);
        Optional<FinnhubCompanyProfile> result = http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, FinnhubCompanyProfile.class))
                .filter(p -> p.ticker() != null && !p.ticker().isBlank());

        if (result.isPresent()) {
            profileCache.put(sym, new CacheEntry<>(result.get(), PROFILE_CACHE_TTL_MS));
            persistProfile(result.get());
            return result;
        }
        return dbResult;
    }

    // ─── Peer Companies ────────────────────────────────────────────────────────

    /**
     * {@code GET /stock/peers?symbol=...} — Peer company symbols.
     * Cached for {@link #PEERS_CACHE_TTL_MS}.
     *
     * @param symbol stock ticker
     * @return list of peer symbols, or empty list on error
     */
    public List<String> getPeers(String symbol) {
        if (!isEnabled()) return Collections.emptyList();
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<List<String>> cached = peersCache.get(sym);
        if (cached != null && !cached.isExpired()) return cached.value;

        String url = buildUrl("/stock/peers", "symbol=" + sym);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> {
                    if (!root.isJsonArray()) return Collections.<String>emptyList();
                    List<String> peers = new ArrayList<>();
                    root.getAsJsonArray().forEach(el -> {
                        if (el.isJsonPrimitive()) peers.add(el.getAsString());
                    });
                    return peers;
                })
                .map(list -> {
                    peersCache.put(sym, new CacheEntry<>(list, PEERS_CACHE_TTL_MS));
                    return list;
                })
                .orElse(Collections.emptyList());
    }

    // ─── Dividends ────────────────────────────────────────────────────────────

    /**
     * {@code GET /stock/dividend?symbol=...&from=...&to=...} — Dividend history.
     * Cached for {@link #DIVIDEND_CACHE_TTL_MS}.
     *
     * @param symbol stock ticker
     * @param from   start date "YYYY-MM-DD"
     * @param to     end date "YYYY-MM-DD"
     * @return list of dividend events
     */
    public List<FinnhubDividend> getDividends(String symbol, String from, String to) {
        if (!isEnabled()) return Collections.emptyList();
        String sym = SymbolNormalizer.normalize(symbol);
        String key = sym + "|" + from + "|" + to;
        CacheEntry<List<FinnhubDividend>> cached = dividendCache.get(key);
        if (cached != null && !cached.isExpired()) return cached.value;

        String params = "symbol=" + sym + "&from=" + from + "&to=" + to;
        String url = buildUrl("/stock/dividend", params);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> parseArray(root, FinnhubDividend.class))
                .map(list -> {
                    dividendCache.put(key, new CacheEntry<>(list, DIVIDEND_CACHE_TTL_MS));
                    return list;
                })
                .orElse(Collections.emptyList());
    }

    // ─── Stock Splits ─────────────────────────────────────────────────────────

    /**
     * {@code GET /stock/split?symbol=...&from=...&to=...} — Stock split history.
     * Cached for {@link #SPLIT_CACHE_TTL_MS}.
     *
     * @param symbol stock ticker
     * @param from   start date "YYYY-MM-DD"
     * @param to     end date "YYYY-MM-DD"
     * @return list of split events
     */
    public List<FinnhubSplit> getSplits(String symbol, String from, String to) {
        if (!isEnabled()) return Collections.emptyList();
        String sym = SymbolNormalizer.normalize(symbol);
        String key = sym + "|" + from + "|" + to;
        CacheEntry<List<FinnhubSplit>> cached = splitCache.get(key);
        if (cached != null && !cached.isExpired()) return cached.value;

        String params = "symbol=" + sym + "&from=" + from + "&to=" + to;
        String url = buildUrl("/stock/split", params);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> parseArray(root, FinnhubSplit.class))
                .map(list -> {
                    splitCache.put(key, new CacheEntry<>(list, SPLIT_CACHE_TTL_MS));
                    return list;
                })
                .orElse(Collections.emptyList());
    }

    // ─── Earnings Surprises ───────────────────────────────────────────────────

    /**
     * {@code GET /stock/earnings?symbol=...&limit=...} — Earnings surprises.
     * Cached for {@link #EARNINGS_CACHE_TTL_MS}.
     *
     * @param symbol stock ticker
     * @param limit  max quarters (default 4)
     * @return list of earnings surprise entries
     */
    public List<FinnhubEarningsSurprise> getEarningsSurprises(String symbol, int limit) {
        if (!isEnabled()) return Collections.emptyList();
        String sym = SymbolNormalizer.normalize(symbol);
        String key = sym + "|" + limit;
        CacheEntry<List<FinnhubEarningsSurprise>> cached = earningsCache.get(key);
        if (cached != null && !cached.isExpired()) return cached.value;

        int lim = limit > 0 ? limit : 4;
        String url = buildUrl("/stock/earnings", "symbol=" + sym + "&limit=" + lim);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> parseArray(root, FinnhubEarningsSurprise.class))
                .map(list -> {
                    earningsCache.put(key, new CacheEntry<>(list, EARNINGS_CACHE_TTL_MS));
                    return list;
                })
                .orElse(Collections.emptyList());
    }

    // ─── Recommendation Trends ────────────────────────────────────────────────

    /**
     * {@code GET /stock/recommendation?symbol=...} — Analyst recommendation trends.
     * Cached for {@link #RECOMMEND_CACHE_TTL_MS}.
     *
     * @param symbol stock ticker
     * @return list of recommendation trend entries
     */
    public List<FinnhubRecommendationTrend> getRecommendationTrends(String symbol) {
        if (!isEnabled()) return Collections.emptyList();
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<List<FinnhubRecommendationTrend>> cached = recCache.get(sym);
        if (cached != null && !cached.isExpired()) return cached.value;

        String url = buildUrl("/stock/recommendation", "symbol=" + sym);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> parseArray(root, FinnhubRecommendationTrend.class))
                .map(list -> {
                    recCache.put(sym, new CacheEntry<>(list, RECOMMEND_CACHE_TTL_MS));
                    return list;
                })
                .orElse(Collections.emptyList());
    }

    // ─── Price Target ─────────────────────────────────────────────────────────

    /**
     * {@code GET /stock/price-target?symbol=...} — Analyst consensus price target.
     * Cached for {@link #PRICE_TARGET_TTL_MS}.
     *
     * @param symbol stock ticker
     * @return price target, or empty on error
     */
    public Optional<FinnhubPriceTarget> getPriceTarget(String symbol) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<FinnhubPriceTarget> cached = priceTargetCache.get(sym);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("/stock/price-target", "symbol=" + sym);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, FinnhubPriceTarget.class))
                .filter(pt -> pt.symbol() != null)
                .map(result -> {
                    priceTargetCache.put(sym, new CacheEntry<>(result, PRICE_TARGET_TTL_MS));
                    return result;
                });
    }

    // ─── Insider Transactions ─────────────────────────────────────────────────

    /**
     * {@code GET /stock/insider-transactions?symbol=...} — Insider buy/sell transactions.
     * Cached for {@link #INSIDER_CACHE_TTL_MS}.
     *
     * @param symbol stock ticker
     * @param from   optional start date "YYYY-MM-DD" (can be null)
     * @param to     optional end date "YYYY-MM-DD" (can be null)
     * @return insider transactions, or empty on error
     */
    public Optional<FinnhubInsiderTransaction> getInsiderTransactions(String symbol,
                                                                       String from, String to) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);
        String key = sym + "|" + from + "|" + to;
        CacheEntry<FinnhubInsiderTransaction> cached = insiderCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        StringBuilder params = new StringBuilder("symbol=").append(sym);
        if (from != null && !from.isBlank()) params.append("&from=").append(from);
        if (to   != null && !to.isBlank())   params.append("&to=").append(to);
        String url = buildUrl("/stock/insider-transactions", params.toString());
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, FinnhubInsiderTransaction.class))
                .map(result -> {
                    insiderCache.put(key, new CacheEntry<>(result, INSIDER_CACHE_TTL_MS));
                    return result;
                });
    }

    // ─── Institutional Ownership ──────────────────────────────────────────────

    /**
     * {@code GET /stock/ownership?symbol=...&limit=...} — Top institutional holders.
     * Cached for {@link #OWNERSHIP_CACHE_TTL_MS}.
     *
     * @param symbol stock ticker
     * @param limit  max results (0 = default)
     * @return ownership data, or empty on error
     */
    public Optional<FinnhubInstitutionalOwnership> getInstitutionalOwnership(String symbol, int limit) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);
        String key = sym + "|" + limit;
        CacheEntry<FinnhubInstitutionalOwnership> cached = ownershipCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String params = "symbol=" + sym + (limit > 0 ? "&limit=" + limit : "");
        String url = buildUrl("/stock/ownership", params);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, FinnhubInstitutionalOwnership.class))
                .map(result -> {
                    ownershipCache.put(key, new CacheEntry<>(result, OWNERSHIP_CACHE_TTL_MS));
                    return result;
                });
    }

    // ─── Senate Trading ───────────────────────────────────────────────────────

    /**
     * {@code GET /stock/senate-trading?symbol=...} — US Senator stock trade disclosures.
     * Cached for {@link #SENATE_CACHE_TTL_MS}.
     *
     * @param symbol stock ticker
     * @return senate trading data, or empty on error
     */
    public Optional<FinnhubSenateTrade> getSenateTrades(String symbol) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<FinnhubSenateTrade> cached = senateCache.get(sym);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("/stock/senate-trading", "symbol=" + sym);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, FinnhubSenateTrade.class))
                .map(result -> {
                    senateCache.put(sym, new CacheEntry<>(result, SENATE_CACHE_TTL_MS));
                    return result;
                });
    }

    // ─── Fund Ownership ───────────────────────────────────────────────────────

    /**
     * {@code GET /stock/fund-ownership?symbol=...} — Mutual fund / ETF holders.
     * Cached for {@link #FUND_OWN_CACHE_TTL_MS}.
     *
     * @param symbol stock ticker
     * @return fund ownership data, or empty on error
     */
    public Optional<FinnhubFundOwnership> getFundOwnership(String symbol) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<FinnhubFundOwnership> cached = fundOwnCache.get(sym);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("/stock/fund-ownership", "symbol=" + sym);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, FinnhubFundOwnership.class))
                .map(result -> {
                    fundOwnCache.put(sym, new CacheEntry<>(result, FUND_OWN_CACHE_TTL_MS));
                    return result;
                });
    }

    // ─── ETF ──────────────────────────────────────────────────────────────────

    /**
     * {@code GET /etf/profile?symbol=...} — ETF profile.
     * Cached for {@link #ETF_PROFILE_TTL_MS}.
     *
     * @param symbol ETF ticker
     * @return ETF profile, or empty on error
     */
    public Optional<FinnhubEtfProfile> getEtfProfile(String symbol) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<FinnhubEtfProfile> cached = etfProfileCache.get(sym);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("/etf/profile", "symbol=" + sym);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, FinnhubEtfProfile.class))
                .filter(p -> p.symbol() != null)
                .map(result -> {
                    etfProfileCache.put(sym, new CacheEntry<>(result, ETF_PROFILE_TTL_MS));
                    return result;
                });
    }

    /**
     * {@code GET /stock/etf-holdings?symbol=...} — ETF top holdings.
     * Cached for {@link #ETF_HOLDINGS_TTL_MS}.
     *
     * @param symbol ETF ticker
     * @return ETF holdings, or empty on error
     */
    public Optional<FinnhubEtfHolding> getEtfHoldings(String symbol) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<FinnhubEtfHolding> cached = etfHoldingsCache.get(sym);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("/stock/etf-holdings", "symbol=" + sym);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, FinnhubEtfHolding.class))
                .map(result -> {
                    etfHoldingsCache.put(sym, new CacheEntry<>(result, ETF_HOLDINGS_TTL_MS));
                    return result;
                });
    }

    // ─── Basic Financials (Metrics) ───────────────────────────────────────────

    /**
     * {@code GET /stock/metric?symbol=...} — Key financial metrics (P/E, EPS, Beta, etc.).
     * Cached for {@link #METRICS_CACHE_TTL_MS}.
     *
     * @param symbol stock ticker
     * @return basic financials, or empty on error
     */
    public Optional<FinnhubBasicFinancials> getBasicFinancials(String symbol) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<FinnhubBasicFinancials> cached = metricsCache.get(sym);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("/stock/metric", "symbol=" + sym + "&metric=all");
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, FinnhubBasicFinancials.class))
                .filter(bf -> bf.symbol() != null)
                .map(result -> {
                    metricsCache.put(sym, new CacheEntry<>(result, METRICS_CACHE_TTL_MS));
                    return result;
                });
    }

    // ─── Forex Rates ──────────────────────────────────────────────────────────

    /**
     * {@code GET /forex/rates?base=...} — Real-time FX rates for all currencies.
     * Cached for {@link #FOREX_RATES_TTL_MS}.
     *
     * @param baseCurrency base currency code e.g. "EUR", "USD"
     * @return FX rates, or empty on error
     */
    public Optional<FinnhubForexRate> getForexRates(String baseCurrency) {
        if (!isEnabled()) return Optional.empty();
        String base = baseCurrency != null ? baseCurrency.toUpperCase() : "USD";
        CacheEntry<FinnhubForexRate> cached = forexRatesCache.get(base);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("/forex/rates", "base=" + base);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, FinnhubForexRate.class))
                .filter(r -> r.base() != null)
                .map(result -> {
                    forexRatesCache.put(base, new CacheEntry<>(result, FOREX_RATES_TTL_MS));
                    return result;
                });
    }

    // ─── Market News ──────────────────────────────────────────────────────────

    /**
     * {@code GET /news?category=...} — Market news by category.
     * Cached for {@link #NEWS_CACHE_TTL_MS}.
     *
     * @param category "general", "forex", "crypto", or "merger"
     * @return list of news articles
     */
    public List<FinnhubNewsArticle> getMarketNews(String category) {
        if (!isEnabled()) return Collections.emptyList();
        String cat = (category != null && !category.isBlank()) ? category : "general";
        CacheEntry<List<FinnhubNewsArticle>> cached = newsCache.get(cat);
        if (cached != null && !cached.isExpired()) return cached.value;

        String url = buildUrl("/news", "category=" + cat);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> parseArticleArray(root))
                .map(list -> {
                    newsCache.put(cat, new CacheEntry<>(list, NEWS_CACHE_TTL_MS));
                    return list;
                })
                .orElse(Collections.emptyList());
    }

    /**
     * {@code GET /company-news?symbol=...&from=...&to=...} — Company-specific news.
     * Cached for {@link #NEWS_CACHE_TTL_MS}.
     *
     * @param symbol stock ticker
     * @param from   start date "YYYY-MM-DD"
     * @param to     end date "YYYY-MM-DD"
     * @return list of news articles
     */
    public List<FinnhubNewsArticle> getCompanyNews(String symbol, String from, String to) {
        if (!isEnabled()) return Collections.emptyList();
        String sym = SymbolNormalizer.normalize(symbol);
        String key = "company|" + sym + "|" + from + "|" + to;
        CacheEntry<List<FinnhubNewsArticle>> cached = newsCache.get(key);
        if (cached != null && !cached.isExpired()) return cached.value;

        String params = "symbol=" + sym + "&from=" + from + "&to=" + to;
        String url = buildUrl("/company-news", params);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> parseArticleArray(root))
                .map(list -> {
                    newsCache.put(key, new CacheEntry<>(list, NEWS_CACHE_TTL_MS));
                    return list;
                })
                .orElse(Collections.emptyList());
    }

    /**
     * {@code GET /news-sentiment?symbol=...} — Aggregated news sentiment.
     * Cached for {@link #SENTIMENT_CACHE_TTL_MS}.
     *
     * @param symbol stock ticker
     * @return news sentiment, or empty on error
     */
    public Optional<FinnhubNewsSentiment> getNewsSentiment(String symbol) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<FinnhubNewsSentiment> cached = sentimentCache.get(sym);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("/news-sentiment", "symbol=" + sym);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, FinnhubNewsSentiment.class))
                .filter(ns -> ns.symbol() != null)
                .map(result -> {
                    sentimentCache.put(sym, new CacheEntry<>(result, SENTIMENT_CACHE_TTL_MS));
                    return result;
                });
    }

    /**
     * {@code GET /press-releases?symbol=...&from=...&to=...} — Official press releases.
     * Cached for {@link #PRESS_CACHE_TTL_MS}.
     *
     * @param symbol stock ticker
     * @param from   start date "YYYY-MM-DD" (optional)
     * @param to     end date "YYYY-MM-DD" (optional)
     * @return raw JSON with press releases, or empty on error
     */
    public Optional<JsonObject> getPressReleases(String symbol, String from, String to) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);
        String key = sym + "|" + from + "|" + to;
        CacheEntry<JsonObject> cached = pressCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        StringBuilder params = new StringBuilder("symbol=").append(sym);
        if (from != null && !from.isBlank()) params.append("&from=").append(from);
        if (to   != null && !to.isBlank())   params.append("&to=").append(to);
        String url = buildUrl("/press-releases", params.toString());
        return http.getJson(url, null, THROTTLE_KEY)
                .map(result -> {
                    pressCache.put(key, new CacheEntry<>(result, PRESS_CACHE_TTL_MS));
                    return result;
                });
    }

    // ─── Calendar Endpoints ───────────────────────────────────────────────────

    /**
     * {@code GET /calendar/ipo?from=...&to=...} — IPO Calendar.
     * Cached for {@link #CALENDAR_CACHE_TTL_MS}.
     *
     * @param from start date "YYYY-MM-DD"
     * @param to   end date "YYYY-MM-DD"
     * @return IPO calendar, or empty on error
     */
    public Optional<FinnhubIpoEvent> getIpoCalendar(String from, String to) {
        if (!isEnabled()) return Optional.empty();
        String key = "ipo|" + from + "|" + to;
        CacheEntry<FinnhubIpoEvent> cached = ipoCalCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("/calendar/ipo", "from=" + from + "&to=" + to);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, FinnhubIpoEvent.class))
                .map(result -> {
                    ipoCalCache.put(key, new CacheEntry<>(result, CALENDAR_CACHE_TTL_MS));
                    return result;
                });
    }

    /**
     * {@code GET /calendar/earnings?from=...&to=...} — Earnings Calendar.
     * Cached for {@link #CALENDAR_CACHE_TTL_MS}.
     *
     * @param from start date "YYYY-MM-DD"
     * @param to   end date "YYYY-MM-DD"
     * @return earnings calendar, or empty on error
     */
    public Optional<FinnhubEarningsCalendarEvent> getEarningsCalendar(String from, String to) {
        if (!isEnabled()) return Optional.empty();
        String key = "earnings|" + from + "|" + to;
        CacheEntry<FinnhubEarningsCalendarEvent> cached = earningsCalCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("/calendar/earnings", "from=" + from + "&to=" + to);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, FinnhubEarningsCalendarEvent.class))
                .map(result -> {
                    earningsCalCache.put(key, new CacheEntry<>(result, CALENDAR_CACHE_TTL_MS));
                    return result;
                });
    }

    /**
     * {@code GET /calendar/economic?from=...&to=...} — Economic Calendar.
     * Cached for {@link #CALENDAR_CACHE_TTL_MS}.
     *
     * @param from start date "YYYY-MM-DD"
     * @param to   end date "YYYY-MM-DD"
     * @return raw JSON with economic calendar events, or empty on error
     */
    public Optional<JsonObject> getEconomicCalendar(String from, String to) {
        if (!isEnabled()) return Optional.empty();
        String key = "econ|" + from + "|" + to;
        CacheEntry<JsonObject> cached = econCalCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("/calendar/economic", "from=" + from + "&to=" + to);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(result -> {
                    econCalCache.put(key, new CacheEntry<>(result, CALENDAR_CACHE_TTL_MS));
                    return result;
                });
    }

    /**
     * {@code GET /calendar/dividend?from=...&to=...} — Dividend Calendar.
     * Cached for {@link #CALENDAR_CACHE_TTL_MS}.
     *
     * @param from start date "YYYY-MM-DD"
     * @param to   end date "YYYY-MM-DD"
     * @return raw JSON with dividend calendar events, or empty on error
     */
    public Optional<JsonObject> getDividendCalendar(String from, String to) {
        if (!isEnabled()) return Optional.empty();
        String key = "div|" + from + "|" + to;
        CacheEntry<JsonObject> cached = dividCalCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("/calendar/dividend", "from=" + from + "&to=" + to);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(result -> {
                    dividCalCache.put(key, new CacheEntry<>(result, CALENDAR_CACHE_TTL_MS));
                    return result;
                });
    }

    /**
     * {@code GET /calendar/split?from=...&to=...} — Split Calendar.
     * Cached for {@link #CALENDAR_CACHE_TTL_MS}.
     *
     * @param from start date "YYYY-MM-DD"
     * @param to   end date "YYYY-MM-DD"
     * @return raw JSON with split calendar events, or empty on error
     */
    public Optional<JsonObject> getSplitCalendar(String from, String to) {
        if (!isEnabled()) return Optional.empty();
        String key = "split|" + from + "|" + to;
        CacheEntry<JsonObject> cached = splitCalCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("/calendar/split", "from=" + from + "&to=" + to);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(result -> {
                    splitCalCache.put(key, new CacheEntry<>(result, CALENDAR_CACHE_TTL_MS));
                    return result;
                });
    }

    // ─── Technical Indicators ─────────────────────────────────────────────────

    /**
     * Fetches a technical indicator from Finnhub.
     * Supports: {@code sma}, {@code ema}, {@code rsi}, {@code macd}, {@code stoch}, {@code bb}.
     * Cached for {@link #INDICATOR_CACHE_TTL_MS}.
     *
     * @param indicatorType indicator type: "sma", "ema", "rsi", "macd", "stoch", or "bb"
     * @param symbol        stock/forex/crypto ticker
     * @param resolution    timeframe: "1","5","15","30","60","D","W","M"
     * @param from          start UNIX timestamp
     * @param to            end UNIX timestamp
     * @param timePeriod    lookback period (e.g. 14 for RSI); 0 = skip parameter
     * @return technical indicator result, or empty on error
     */
    public Optional<FinnhubTechnicalIndicator> getTechnicalIndicator(
            String indicatorType, String symbol, String resolution,
            long from, long to, int timePeriod) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);
        String key = indicatorType + "|" + sym + "|" + resolution + "|" + from + "|" + to + "|" + timePeriod;
        CacheEntry<FinnhubTechnicalIndicator> cached = indicatorCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        StringBuilder params = new StringBuilder()
                .append("symbol=").append(sym)
                .append("&resolution=").append(resolution)
                .append("&from=").append(from)
                .append("&to=").append(to);
        if (timePeriod > 0) params.append("&timeperiod=").append(timePeriod);

        String url = buildUrl("/indicator/" + indicatorType.toLowerCase(), params.toString());
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, FinnhubTechnicalIndicator.class))
                .filter(FinnhubTechnicalIndicator::isOk)
                .map(result -> {
                    indicatorCache.put(key, new CacheEntry<>(result, INDICATOR_CACHE_TTL_MS));
                    return result;
                });
    }

    /** Convenience: Simple Moving Average (SMA). */
    public Optional<FinnhubTechnicalIndicator> getSma(String symbol, String resolution,
                                                      long from, long to, int period) {
        return getTechnicalIndicator("sma", symbol, resolution, from, to, period);
    }

    /** Convenience: Exponential Moving Average (EMA). */
    public Optional<FinnhubTechnicalIndicator> getEma(String symbol, String resolution,
                                                      long from, long to, int period) {
        return getTechnicalIndicator("ema", symbol, resolution, from, to, period);
    }

    /** Convenience: Relative Strength Index (RSI). */
    public Optional<FinnhubTechnicalIndicator> getRsi(String symbol, String resolution,
                                                      long from, long to, int period) {
        return getTechnicalIndicator("rsi", symbol, resolution, from, to, period);
    }

    /** Convenience: MACD. */
    public Optional<FinnhubTechnicalIndicator> getMacd(String symbol, String resolution,
                                                       long from, long to) {
        return getTechnicalIndicator("macd", symbol, resolution, from, to, 0);
    }

    /** Convenience: Stochastic Oscillator. */
    public Optional<FinnhubTechnicalIndicator> getStoch(String symbol, String resolution,
                                                        long from, long to) {
        return getTechnicalIndicator("stoch", symbol, resolution, from, to, 0);
    }

    /** Convenience: Bollinger Bands. */
    public Optional<FinnhubTechnicalIndicator> getBollingerBands(String symbol, String resolution,
                                                                 long from, long to, int period) {
        return getTechnicalIndicator("bb", symbol, resolution, from, to, period);
    }

    // ─── Pattern Recognition ──────────────────────────────────────────────────

    /**
     * {@code GET /scan/pattern?symbol=...&resolution=...} — Candlestick pattern scan.
     * Cached for {@link #PATTERN_CACHE_TTL_MS}.
     *
     * @param symbol     stock ticker
     * @param resolution timeframe resolution
     * @return raw JSON with detected patterns, or empty on error
     */
    public Optional<JsonObject> scanPatterns(String symbol, String resolution) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);
        String key = sym + "|" + resolution;
        CacheEntry<JsonObject> cached = patternCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("/scan/pattern", "symbol=" + sym + "&resolution=" + resolution);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(result -> {
                    patternCache.put(key, new CacheEntry<>(result, PATTERN_CACHE_TTL_MS));
                    return result;
                });
    }

    // ─── Economic Data ────────────────────────────────────────────────────────

    /**
     * {@code GET /economic/code} — List all available economic indicator codes.
     * Cached indefinitely (codes change rarely).
     *
     * @return raw JSON array of economic codes, or empty on error
     */
    public Optional<JsonArray> getEconomicCodes() {
        if (!isEnabled()) return Optional.empty();
        if (econCodesCache != null && !econCodesCache.isExpired())
            return Optional.ofNullable(econCodesCache.value);

        String url = buildUrl("/economic/code", "");
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> {
                    if (root.isJsonArray()) {
                        JsonArray arr = root.getAsJsonArray();
                        econCodesCache = new CacheEntry<>(arr, ECONOMIC_CACHE_TTL_MS);
                        return arr;
                    }
                    if (root.has("data") && root.get("data").isJsonArray()) {
                        JsonArray arr = root.getAsJsonArray("data");
                        econCodesCache = new CacheEntry<>(arr, ECONOMIC_CACHE_TTL_MS);
                        return arr;
                    }
                    return null;
                });
    }

    /**
     * {@code GET /economic?code=...} — Economic data series for a code (e.g. GDP.US).
     * Cached for {@link #ECONOMIC_CACHE_TTL_MS}.
     *
     * @param code economic indicator code e.g. "GDP.US", "CPI.US"
     * @return list of economic data points
     */
    public List<FinnhubEconomicData> getEconomicData(String code) {
        if (!isEnabled() || code == null || code.isBlank()) return Collections.emptyList();
        CacheEntry<List<FinnhubEconomicData>> cached = economicCache.get(code);
        if (cached != null && !cached.isExpired()) return cached.value;

        String url = buildUrl("/economic", "code=" + urlEnc(code));
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> {
                    // Response can be an array at root or wrapped in a field
                    if (root.isJsonArray()) {
                        return parseEconomicArray(root.getAsJsonArray());
                    }
                    return Collections.<FinnhubEconomicData>emptyList();
                })
                .map(list -> {
                    economicCache.put(code, new CacheEntry<>(list, ECONOMIC_CACHE_TTL_MS));
                    return list;
                })
                .orElse(Collections.emptyList());
    }

    // ─── Alternative Data ─────────────────────────────────────────────────────

    /**
     * {@code GET /insider-sentiment?symbol=...&from=...&to=...} — Aggregated insider sentiment.
     * Cached for {@link #INSIDER_SENT_TTL_MS}.
     *
     * @param symbol stock ticker
     * @param from   start date "YYYY-MM-DD" (optional)
     * @param to     end date "YYYY-MM-DD" (optional)
     * @return insider sentiment, or empty on error
     */
    public Optional<FinnhubInsiderSentiment> getInsiderSentiment(String symbol,
                                                                  String from, String to) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);
        String key = sym + "|" + from + "|" + to;
        CacheEntry<FinnhubInsiderSentiment> cached = insiderSentCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        StringBuilder params = new StringBuilder("symbol=").append(sym);
        if (from != null && !from.isBlank()) params.append("&from=").append(from);
        if (to   != null && !to.isBlank())   params.append("&to=").append(to);
        String url = buildUrl("/insider-sentiment", params.toString());
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, FinnhubInsiderSentiment.class))
                .map(result -> {
                    insiderSentCache.put(key, new CacheEntry<>(result, INSIDER_SENT_TTL_MS));
                    return result;
                });
    }

    /**
     * {@code GET /esg?symbol=...} — ESG Scores.
     * Cached for {@link #ESG_CACHE_TTL_MS}.
     *
     * @param symbol stock ticker
     * @return ESG scores, or empty on error
     */
    public Optional<FinnhubEsgScore> getEsgScores(String symbol) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<FinnhubEsgScore> cached = esgCache.get(sym);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("/esg", "symbol=" + sym);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, FinnhubEsgScore.class))
                .filter(e -> e.symbol() != null)
                .map(result -> {
                    esgCache.put(sym, new CacheEntry<>(result, ESG_CACHE_TTL_MS));
                    return result;
                });
    }

    /**
     * {@code GET /stock/supply-chain?symbol=...} — Supply chain (customers & suppliers).
     * Cached for {@link #SUPPLY_CHAIN_TTL_MS}.
     *
     * @param symbol stock ticker
     * @return raw JSON with supply chain data, or empty on error
     */
    public Optional<JsonObject> getSupplyChain(String symbol) {
        if (!isEnabled()) return Optional.empty();
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<JsonObject> cached = supplyChainCache.get(sym);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("/stock/supply-chain", "symbol=" + sym);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(result -> {
                    supplyChainCache.put(sym, new CacheEntry<>(result, SUPPLY_CHAIN_TTL_MS));
                    return result;
                });
    }

    // ─── Index Data ───────────────────────────────────────────────────────────

    /**
     * {@code GET /index/constituents?symbol=...} — Index component symbols.
     * Cached for {@link #INDEX_CACHE_TTL_MS}.
     *
     * @param indexSymbol index symbol e.g. "^GSPC" (S&P500), "^NDX" (Nasdaq100)
     * @return index constituents, or empty on error
     */
    public Optional<FinnhubIndexConstituents> getIndexConstituents(String indexSymbol) {
        if (!isEnabled()) return Optional.empty();
        CacheEntry<FinnhubIndexConstituents> cached = indexCache.get(indexSymbol);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("/index/constituents", "symbol=" + urlEnc(indexSymbol));
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, FinnhubIndexConstituents.class))
                .map(result -> {
                    indexCache.put(indexSymbol, new CacheEntry<>(result, INDEX_CACHE_TTL_MS));
                    return result;
                });
    }

    // ─── URL builder ──────────────────────────────────────────────────────────

    /**
     * Builds a complete Finnhub API URL including authentication token.
     *
     * @param path   API path (e.g. "/quote", "/stock/candle")
     * @param params additional query parameters (without leading "?")
     * @return complete URL string
     */
    public String buildUrl(String path, String params) {
        String token = keys.getFinnhubKey();
        StringBuilder sb = new StringBuilder(baseUrl).append(path).append("?");
        if (params != null && !params.isBlank()) {
            sb.append(params).append("&");
        }
        sb.append("token=").append(token);
        return sb.toString();
    }

    // ─── DB Persistence ───────────────────────────────────────────────────────

    private void persistProfile(FinnhubCompanyProfile p) {
        try {
            ensureProfileTable();
            String upsert = isPostgres()
                    ? """
                      INSERT INTO finnhub_company_profile
                        (ticker, name, exchange, industry, country, currency,
                         market_cap, logo, weburl, snapshot_time)
                      VALUES (?,?,?,?,?,?,?,?,?,?)
                      ON CONFLICT (ticker) DO UPDATE SET
                        name          = EXCLUDED.name,
                        exchange      = EXCLUDED.exchange,
                        industry      = EXCLUDED.industry,
                        country       = EXCLUDED.country,
                        currency      = EXCLUDED.currency,
                        market_cap    = EXCLUDED.market_cap,
                        logo          = EXCLUDED.logo,
                        weburl        = EXCLUDED.weburl,
                        snapshot_time = EXCLUDED.snapshot_time
                      """
                    : """
                      INSERT OR REPLACE INTO finnhub_company_profile
                        (ticker, name, exchange, industry, country, currency,
                         market_cap, logo, weburl, snapshot_time)
                      VALUES (?,?,?,?,?,?,?,?,?,?)
                      """;
            jdbc.update(upsert,
                    p.ticker(), p.name(), p.exchange(), p.finnhubIndustry(),
                    p.country(), p.currency(),
                    p.marketCapitalization() != null ? p.marketCapitalization() : 0.0,
                    p.logo(), p.weburl(),
                    Timestamp.valueOf(LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("Finnhub profile persist error: {}", e.getMessage());
        }
    }

    private Optional<FinnhubCompanyProfile> loadProfileFromDb(String symbol) {
        try {
            ensureProfileTable();
            String sql = "SELECT ticker, name, exchange, industry, country, currency, " +
                         "market_cap, logo, weburl FROM finnhub_company_profile WHERE ticker = ?";
            List<FinnhubCompanyProfile> results = jdbc.query(sql, (rs, i) ->
                    new FinnhubCompanyProfile(
                            rs.getString("country"),
                            rs.getString("currency"),
                            rs.getString("exchange"),
                            null,
                            rs.getDouble("market_cap"),
                            rs.getString("name"),
                            null,
                            null,
                            rs.getString("ticker"),
                            rs.getString("weburl"),
                            rs.getString("logo"),
                            rs.getString("industry")
                    ), symbol);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.debug("Finnhub profile DB load error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void ensureProfileTable() {
        if (!ensuredTables.add("finnhub_company_profile")) return;
        String ddl = isPostgres()
                ? """
                  CREATE TABLE IF NOT EXISTS finnhub_company_profile (
                    ticker        TEXT PRIMARY KEY,
                    name          TEXT,
                    exchange      TEXT,
                    industry      TEXT,
                    country       TEXT,
                    currency      TEXT,
                    market_cap    NUMERIC,
                    logo          TEXT,
                    weburl        TEXT,
                    snapshot_time TIMESTAMP
                  )
                  """
                : """
                  CREATE TABLE IF NOT EXISTS finnhub_company_profile (
                    ticker        TEXT PRIMARY KEY,
                    name          TEXT,
                    exchange      TEXT,
                    industry      TEXT,
                    country       TEXT,
                    currency      TEXT,
                    market_cap    REAL,
                    logo          TEXT,
                    weburl        TEXT,
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

    // ─── Parse helpers ────────────────────────────────────────────────────────

    private List<FinnhubSymbolEntry> parseSymbolArray(JsonObject root) {
        if (!root.isJsonArray()) {
            // Sometimes the response is wrapped in a field
            if (root.has("data") && root.get("data").isJsonArray()) {
                return parseSymbolEntries(root.getAsJsonArray("data"));
            }
            return Collections.emptyList();
        }
        return parseSymbolEntries(root.getAsJsonArray());
    }

    private List<FinnhubSymbolEntry> parseSymbolEntries(JsonArray arr) {
        List<FinnhubSymbolEntry> list = new ArrayList<>();
        arr.forEach(el -> {
            if (el.isJsonObject())
                list.add(gson.fromJson(el, FinnhubSymbolEntry.class));
        });
        return list;
    }

    private <T> List<T> parseArray(JsonObject root, Class<T> type) {
        List<T> list = new ArrayList<>();
        JsonArray arr = null;
        if (root.isJsonArray()) {
            arr = root.getAsJsonArray();
        } else if (root.has("data") && root.get("data").isJsonArray()) {
            arr = root.getAsJsonArray("data");
        }
        if (arr != null) {
            arr.forEach(el -> {
                if (el.isJsonObject()) {
                    try { list.add(gson.fromJson(el, type)); }
                    catch (Exception ex) { log.debug("Parse error for {}: {}", type.getSimpleName(), ex.getMessage()); }
                }
            });
        }
        return list;
    }

    private List<FinnhubNewsArticle> parseArticleArray(JsonObject root) {
        if (!root.isJsonArray()) return Collections.emptyList();
        return parseArray(root, FinnhubNewsArticle.class);
    }

    private List<FinnhubEconomicData> parseEconomicArray(JsonArray arr) {
        List<FinnhubEconomicData> list = new ArrayList<>();
        arr.forEach(el -> {
            if (el.isJsonObject())
                list.add(gson.fromJson(el, FinnhubEconomicData.class));
        });
        return list;
    }

    private static String urlEnc(String s) {
        if (s == null) return "";
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    // ─── Cache helper ──────────────────────────────────────────────────────────

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
