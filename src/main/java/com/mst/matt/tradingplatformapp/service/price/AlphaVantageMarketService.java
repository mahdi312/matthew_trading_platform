package com.mst.matt.tradingplatformapp.service.price;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import com.mst.matt.tradingplatformapp.service.price.api.AlphaVantageTimeSeriesParser;
import com.mst.matt.tradingplatformapp.service.price.api.alphavantage.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Alpha Vantage market-data + analysis service — complete free-tier integration.
 *
 * <h3>Free-tier endpoints covered:</h3>
 * <ul>
 *   <li>{@code GLOBAL_QUOTE}              — real-time quote (delegated to AlphaVantagePriceService)</li>
 *   <li>{@code TIME_SERIES_DAILY}         — daily OHLCV (delegated to AlphaVantagePriceService)</li>
 *   <li>{@code TIME_SERIES_DAILY_ADJUSTED}— daily adjusted OHLCV</li>
 *   <li>{@code TIME_SERIES_WEEKLY}        — weekly OHLCV</li>
 *   <li>{@code TIME_SERIES_WEEKLY_ADJUSTED}— weekly adjusted OHLCV</li>
 *   <li>{@code TIME_SERIES_MONTHLY}       — monthly OHLCV</li>
 *   <li>{@code TIME_SERIES_MONTHLY_ADJUSTED}— monthly adjusted OHLCV</li>
 *   <li>{@code NEWS_SENTIMENT}            — news articles with sentiment scores</li>
 *   <li>{@code TOP_GAINERS_LOSERS}        — top market movers</li>
 *   <li>{@code INSIDER_TRANSACTIONS}      — insider trading data</li>
 *   <li>{@code OVERVIEW}                  — company overview and fundamentals</li>
 *   <li>{@code INCOME_STATEMENT}          — annual/quarterly income statements</li>
 *   <li>{@code BALANCE_SHEET}             — annual/quarterly balance sheets</li>
 *   <li>{@code CASH_FLOW}                 — annual/quarterly cash flow</li>
 *   <li>{@code EARNINGS}                  — historical EPS data</li>
 *   <li>{@code EARNINGS_ESTIMATES}        — analyst EPS estimates</li>
 *   <li>{@code DIVIDENDS}                 — dividend history</li>
 *   <li>{@code SPLITS}                    — stock split history</li>
 *   <li>{@code LISTING_STATUS}            — active/delisted securities (CSV)</li>
 *   <li>{@code EARNINGS_CALENDAR}         — upcoming earnings (CSV)</li>
 *   <li>{@code IPO_CALENDAR}              — upcoming IPOs (CSV)</li>
 *   <li>{@code ETF_PROFILE}               — ETF holdings and profile</li>
 *   <li>{@code CURRENCY_EXCHANGE_RATE}    — real-time forex rate</li>
 *   <li>{@code FX_DAILY}                  — daily forex OHLCV</li>
 *   <li>{@code FX_WEEKLY}                 — weekly forex OHLCV</li>
 *   <li>{@code FX_MONTHLY}                — monthly forex OHLCV</li>
 *   <li>{@code DIGITAL_CURRENCY_DAILY}    — daily crypto OHLCV</li>
 *   <li>{@code DIGITAL_CURRENCY_WEEKLY}   — weekly crypto OHLCV</li>
 *   <li>{@code DIGITAL_CURRENCY_MONTHLY}  — monthly crypto OHLCV</li>
 *   <li>{@code CRYPTO_RATING}             — FCAS crypto rating</li>
 *   <li>{@code WTI}, {@code BRENT}, {@code NATURAL_GAS}, {@code GOLD},
 *       {@code SILVER}, {@code COPPER}, {@code WHEAT}, {@code CORN} — commodity prices</li>
 *   <li>{@code REAL_GDP}, {@code REAL_GDP_PER_CAPITA}, {@code TREASURY_YIELD},
 *       {@code FEDERAL_FUNDS_RATE}, {@code CPI}, {@code INFLATION},
 *       {@code RETAIL_SALES}, {@code DURABLE_GOODS_ORDERS},
 *       {@code UNEMPLOYMENT}, {@code NONFARM_PAYROLL} — economic indicators</li>
 *   <li>60+ Technical Indicators (SMA, EMA, RSI, MACD, BBANDS, etc.)</li>
 * </ul>
 *
 * <h3>Rate limiting:</h3>
 * All calls share the {@code "alphavantage"} throttle bucket registered by
 * {@link AlphaVantagePriceService}: 5 requests/minute (free tier).
 *
 * <h3>Caching:</h3>
 * All responses are in-memory cached at appropriate TTLs to conserve the
 * 500 requests/day free-tier limit.
 */
@Service
public class AlphaVantageMarketService {

    private static final Logger log = LoggerFactory.getLogger(AlphaVantageMarketService.class);

    // ── Cache TTLs ─────────────────────────────────────────────────────────────
    private static final long QUOTE_CACHE_TTL_MS       = 2  * 60_000L;   // 2 min
    private static final long NEWS_CACHE_TTL_MS        = 10 * 60_000L;   // 10 min
    private static final long MOVERS_CACHE_TTL_MS      = 5  * 60_000L;   // 5 min
    private static final long INSIDER_CACHE_TTL_MS     = 30 * 60_000L;   // 30 min
    private static final long OVERVIEW_CACHE_TTL_MS    = 60 * 60_000L;   // 1 hour
    private static final long FINANCIAL_CACHE_TTL_MS   = 60 * 60_000L;   // 1 hour
    private static final long EARNINGS_CACHE_TTL_MS    = 60 * 60_000L;   // 1 hour
    private static final long DIVIDEND_CACHE_TTL_MS    = 60 * 60_000L;   // 1 hour
    private static final long SPLITS_CACHE_TTL_MS      = 60 * 60_000L;   // 1 hour
    private static final long CALENDAR_CACHE_TTL_MS    = 30 * 60_000L;   // 30 min
    private static final long ETF_CACHE_TTL_MS         = 60 * 60_000L;   // 1 hour
    private static final long FOREX_RATE_CACHE_TTL_MS  = 2  * 60_000L;   // 2 min
    private static final long OHLCV_CACHE_TTL_MS       = 5  * 60_000L;   // 5 min
    private static final long CRYPTO_RATING_CACHE_TTL  = 60 * 60_000L;   // 1 hour
    private static final long COMMODITY_CACHE_TTL_MS   = 15 * 60_000L;   // 15 min
    private static final long ECONOMIC_CACHE_TTL_MS    = 60 * 60_000L;   // 1 hour
    private static final long INDICATOR_CACHE_TTL_MS   = 5  * 60_000L;   // 5 min

    private static final String THROTTLE_KEY = "alphavantage";
    private static final String BASE_URL     = "https://www.alphavantage.co/query";

    private final HttpJsonClient       http;
    private final MarketApiProperties  keys;
    private final JdbcTemplate         jdbc;
    private final Gson                 gson = new Gson();

    // ── In-memory caches ───────────────────────────────────────────────────────
    private final Map<String, CacheEntry<AlphaVantageCompanyOverview>>   overviewCache     = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<AlphaVantageNewsSentiment>>     newsCache         = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<AlphaVantageFinancialStatement>> incomeCache      = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<AlphaVantageFinancialStatement>> balanceCache     = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<AlphaVantageFinancialStatement>> cashFlowCache    = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<AlphaVantageEarnings>>           earningsCache    = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<AlphaVantageDividends>>          dividendsCache   = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<AlphaVantageSplits>>             splitsCache      = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<AlphaVantageEtfProfile>>         etfCache         = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<AlphaVantageForexRate>>          forexRateCache   = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<AlphaVantageCryptoRating>>       cryptoRatingCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<AlphaVantageCommodity>>          commodityCache   = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<AlphaVantageEconomicIndicator>>  economicCache    = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<AlphaVantageTechnicalIndicator>> indicatorCache   = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<OhlcvBar>>>                 ohlcvCache       = new ConcurrentHashMap<>();
    private volatile CacheEntry<AlphaVantageTopMovers>                    moversCache;
    private volatile CacheEntry<AlphaVantageInsiderTransaction>           insiderCache;
    private volatile CacheEntry<AlphaVantageEarningsCalendar>             earningsCalCache;
    private volatile CacheEntry<AlphaVantageIpoCalendar>                  ipoCalCache;

    private final Set<String> ensuredTables = ConcurrentHashMap.newKeySet();

    @Autowired
    public AlphaVantageMarketService(HttpJsonClient http,
                                     MarketApiProperties keys,
                                     JdbcTemplate jdbc) {
        this.http = http;
        this.keys = keys;
        this.jdbc = jdbc;
    }

    public boolean isEnabled() {
        return keys.hasAlphavantageKey();
    }

    // ─── Time Series (OHLCV) ────────────────────────────────────────────────────

    /**
     * Daily Adjusted OHLCV — {@code TIME_SERIES_DAILY_ADJUSTED}.
     * Includes split/dividend-adjusted close prices.
     */
    public List<OhlcvBar> getDailyAdjusted(String symbol, int limit) {
        return getTimeSeries(symbol, "TIME_SERIES_DAILY_ADJUSTED", "1d_adj", null, limit);
    }

    /**
     * Weekly OHLCV — {@code TIME_SERIES_WEEKLY}.
     */
    public List<OhlcvBar> getWeekly(String symbol, int limit) {
        return getTimeSeries(symbol, "TIME_SERIES_WEEKLY", "1w", null, limit);
    }

    /**
     * Weekly Adjusted OHLCV — {@code TIME_SERIES_WEEKLY_ADJUSTED}.
     */
    public List<OhlcvBar> getWeeklyAdjusted(String symbol, int limit) {
        return getTimeSeries(symbol, "TIME_SERIES_WEEKLY_ADJUSTED", "1w_adj", null, limit);
    }

    /**
     * Monthly OHLCV — {@code TIME_SERIES_MONTHLY}.
     */
    public List<OhlcvBar> getMonthly(String symbol, int limit) {
        return getTimeSeries(symbol, "TIME_SERIES_MONTHLY", "1mo", null, limit);
    }

    /**
     * Monthly Adjusted OHLCV — {@code TIME_SERIES_MONTHLY_ADJUSTED}.
     */
    public List<OhlcvBar> getMonthlyAdjusted(String symbol, int limit) {
        return getTimeSeries(symbol, "TIME_SERIES_MONTHLY_ADJUSTED", "1mo_adj", null, limit);
    }

    /**
     * Forex Daily OHLCV — {@code FX_DAILY}.
     *
     * @param fromSymbol source currency, e.g. "USD"
     * @param toSymbol   target currency, e.g. "EUR"
     */
    public List<OhlcvBar> getForexDaily(String fromSymbol, String toSymbol, int limit) {
        String cacheKey = "FX_DAILY|" + fromSymbol + "|" + toSymbol + "|" + limit;
        CacheEntry<List<OhlcvBar>> cached = ohlcvCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) return cached.value;

        String url = buildUrl("FX_DAILY",
                "from_symbol=" + fromSymbol + "&to_symbol=" + toSymbol + "&outputsize=compact");
        String pairSym = fromSymbol + toSymbol;
        List<OhlcvBar> bars = fetchOhlcv(url, pairSym, "1d", limit, false, AssetType.FOREX);
        ohlcvCache.put(cacheKey, new CacheEntry<>(bars, OHLCV_CACHE_TTL_MS));
        return bars;
    }

    /**
     * Forex Weekly OHLCV — {@code FX_WEEKLY}.
     */
    public List<OhlcvBar> getForexWeekly(String fromSymbol, String toSymbol, int limit) {
        String cacheKey = "FX_WEEKLY|" + fromSymbol + "|" + toSymbol + "|" + limit;
        CacheEntry<List<OhlcvBar>> cached = ohlcvCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) return cached.value;

        String url = buildUrl("FX_WEEKLY",
                "from_symbol=" + fromSymbol + "&to_symbol=" + toSymbol);
        String pairSym = fromSymbol + toSymbol;
        List<OhlcvBar> bars = fetchOhlcv(url, pairSym, "1w", limit, false, AssetType.FOREX);
        ohlcvCache.put(cacheKey, new CacheEntry<>(bars, OHLCV_CACHE_TTL_MS));
        return bars;
    }

    /**
     * Forex Monthly OHLCV — {@code FX_MONTHLY}.
     */
    public List<OhlcvBar> getForexMonthly(String fromSymbol, String toSymbol, int limit) {
        String cacheKey = "FX_MONTHLY|" + fromSymbol + "|" + toSymbol + "|" + limit;
        CacheEntry<List<OhlcvBar>> cached = ohlcvCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) return cached.value;

        String url = buildUrl("FX_MONTHLY",
                "from_symbol=" + fromSymbol + "&to_symbol=" + toSymbol);
        String pairSym = fromSymbol + toSymbol;
        List<OhlcvBar> bars = fetchOhlcv(url, pairSym, "1mo", limit, false, AssetType.FOREX);
        ohlcvCache.put(cacheKey, new CacheEntry<>(bars, OHLCV_CACHE_TTL_MS));
        return bars;
    }

    /**
     * Digital Currency Daily — {@code DIGITAL_CURRENCY_DAILY}.
     *
     * @param symbol crypto symbol, e.g. "BTC"
     * @param market quote market, e.g. "USD"
     */
    public List<OhlcvBar> getCryptoDaily(String symbol, String market, int limit) {
        String cacheKey = "DIGITAL_CURRENCY_DAILY|" + symbol + "|" + market + "|" + limit;
        CacheEntry<List<OhlcvBar>> cached = ohlcvCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) return cached.value;

        String url = buildUrl("DIGITAL_CURRENCY_DAILY",
                "symbol=" + symbol + "&market=" + market);
        List<OhlcvBar> bars = fetchOhlcv(url, symbol + market, "1d", limit, false, AssetType.CRYPTO);
        ohlcvCache.put(cacheKey, new CacheEntry<>(bars, OHLCV_CACHE_TTL_MS));
        return bars;
    }

    /**
     * Digital Currency Weekly — {@code DIGITAL_CURRENCY_WEEKLY}.
     */
    public List<OhlcvBar> getCryptoWeekly(String symbol, String market, int limit) {
        String cacheKey = "DIGITAL_CURRENCY_WEEKLY|" + symbol + "|" + market + "|" + limit;
        CacheEntry<List<OhlcvBar>> cached = ohlcvCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) return cached.value;

        String url = buildUrl("DIGITAL_CURRENCY_WEEKLY",
                "symbol=" + symbol + "&market=" + market);
        List<OhlcvBar> bars = fetchOhlcv(url, symbol + market, "1w", limit, false, AssetType.CRYPTO);
        ohlcvCache.put(cacheKey, new CacheEntry<>(bars, OHLCV_CACHE_TTL_MS));
        return bars;
    }

    /**
     * Digital Currency Monthly — {@code DIGITAL_CURRENCY_MONTHLY}.
     */
    public List<OhlcvBar> getCryptoMonthly(String symbol, String market, int limit) {
        String cacheKey = "DIGITAL_CURRENCY_MONTHLY|" + symbol + "|" + market + "|" + limit;
        CacheEntry<List<OhlcvBar>> cached = ohlcvCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) return cached.value;

        String url = buildUrl("DIGITAL_CURRENCY_MONTHLY",
                "symbol=" + symbol + "&market=" + market);
        List<OhlcvBar> bars = fetchOhlcv(url, symbol + market, "1mo", limit, false, AssetType.CRYPTO);
        ohlcvCache.put(cacheKey, new CacheEntry<>(bars, OHLCV_CACHE_TTL_MS));
        return bars;
    }

    // ─── Alpha Intelligence™ ──────────────────────────────────────────────────

    /**
     * {@code NEWS_SENTIMENT} — news articles with sentiment scores for a symbol or topic.
     *
     * @param symbol    optional ticker symbol filter (can be null for broad market news)
     * @param topics    optional comma-separated topics (e.g. "technology,ipo")
     * @param timeFrom  optional start time "YYYYMMDDTHHMM" format
     * @param timeTo    optional end time "YYYYMMDDTHHMM" format
     * @param limit     max articles to return (1–1000, default 50)
     * @return news sentiment result, or empty on error
     */
    public Optional<AlphaVantageNewsSentiment> getNewsSentiment(String symbol, String topics,
                                                                  String timeFrom, String timeTo,
                                                                  int limit) {
        String cacheKey = symbol + "|" + topics + "|" + timeFrom + "|" + timeTo + "|" + limit;
        CacheEntry<AlphaVantageNewsSentiment> cached = newsCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        StringBuilder params = new StringBuilder();
        if (symbol  != null && !symbol.isBlank())  appendParam(params, "tickers", symbol);
        if (topics  != null && !topics.isBlank())  appendParam(params, "topics",  topics);
        if (timeFrom != null && !timeFrom.isBlank()) appendParam(params, "time_from", timeFrom);
        if (timeTo   != null && !timeTo.isBlank())   appendParam(params, "time_to",   timeTo);
        if (limit > 0) appendParam(params, "limit", String.valueOf(Math.min(limit, 1000)));

        return http.getJson(buildUrl("NEWS_SENTIMENT", params.toString()), null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, AlphaVantageNewsSentiment.class))
                .map(result -> {
                    newsCache.put(cacheKey, new CacheEntry<>(result, NEWS_CACHE_TTL_MS));
                    return result;
                });
    }

    /**
     * {@code TOP_GAINERS_LOSERS} — top gaining, losing, and most actively traded US tickers.
     * Cached for {@link #MOVERS_CACHE_TTL_MS}.
     *
     * @return top movers, or empty on error
     */
    public Optional<AlphaVantageTopMovers> getTopMovers() {
        if (moversCache != null && !moversCache.isExpired())
            return Optional.ofNullable(moversCache.value);

        return http.getJson(buildUrl("TOP_GAINERS_LOSERS", ""), null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, AlphaVantageTopMovers.class))
                .map(result -> {
                    moversCache = new CacheEntry<>(result, MOVERS_CACHE_TTL_MS);
                    return result;
                });
    }

    /**
     * {@code INSIDER_TRANSACTIONS} — insider trading data for a symbol.
     * Cached for {@link #INSIDER_CACHE_TTL_MS}.
     *
     * @param symbol equity ticker
     * @return insider transactions, or empty on error
     */
    public Optional<AlphaVantageInsiderTransaction> getInsiderTransactions(String symbol) {
        if (insiderCache != null && !insiderCache.isExpired())
            return Optional.ofNullable(insiderCache.value);

        String url = buildUrl("INSIDER_TRANSACTIONS", "symbol=" + SymbolNormalizer.normalize(symbol));
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, AlphaVantageInsiderTransaction.class))
                .map(result -> {
                    insiderCache = new CacheEntry<>(result, INSIDER_CACHE_TTL_MS);
                    return result;
                });
    }

    // ─── Fundamental Data ─────────────────────────────────────────────────────

    /**
     * {@code OVERVIEW} — company overview with sector, P/E, EPS, market cap, etc.
     * Cached for {@link #OVERVIEW_CACHE_TTL_MS}.
     *
     * @param symbol equity ticker
     * @return company overview, or empty on error
     */
    public Optional<AlphaVantageCompanyOverview> getCompanyOverview(String symbol) {
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<AlphaVantageCompanyOverview> cached = overviewCache.get(sym);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("OVERVIEW", "symbol=" + sym);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, AlphaVantageCompanyOverview.class))
                .filter(o -> o.symbol() != null && !o.symbol().isBlank())
                .map(result -> {
                    overviewCache.put(sym, new CacheEntry<>(result, OVERVIEW_CACHE_TTL_MS));
                    persistOverviewSnapshot(result);
                    return result;
                });
    }

    /**
     * {@code INCOME_STATEMENT} — annual and quarterly income statement.
     * Cached for {@link #FINANCIAL_CACHE_TTL_MS}.
     *
     * @param symbol equity ticker
     * @return financial statement, or empty on error
     */
    public Optional<AlphaVantageFinancialStatement> getIncomeStatement(String symbol) {
        return fetchFinancialStatement(symbol, "INCOME_STATEMENT", incomeCache);
    }

    /**
     * {@code BALANCE_SHEET} — annual and quarterly balance sheet.
     * Cached for {@link #FINANCIAL_CACHE_TTL_MS}.
     *
     * @param symbol equity ticker
     * @return financial statement, or empty on error
     */
    public Optional<AlphaVantageFinancialStatement> getBalanceSheet(String symbol) {
        return fetchFinancialStatement(symbol, "BALANCE_SHEET", balanceCache);
    }

    /**
     * {@code CASH_FLOW} — annual and quarterly cash flow statement.
     * Cached for {@link #FINANCIAL_CACHE_TTL_MS}.
     *
     * @param symbol equity ticker
     * @return financial statement, or empty on error
     */
    public Optional<AlphaVantageFinancialStatement> getCashFlow(String symbol) {
        return fetchFinancialStatement(symbol, "CASH_FLOW", cashFlowCache);
    }

    /**
     * {@code EARNINGS} — historical annual and quarterly EPS data.
     * Cached for {@link #EARNINGS_CACHE_TTL_MS}.
     *
     * @param symbol equity ticker
     * @return earnings data, or empty on error
     */
    public Optional<AlphaVantageEarnings> getEarnings(String symbol) {
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<AlphaVantageEarnings> cached = earningsCache.get(sym);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("EARNINGS", "symbol=" + sym);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, AlphaVantageEarnings.class))
                .filter(e -> e.symbol() != null)
                .map(result -> {
                    earningsCache.put(sym, new CacheEntry<>(result, EARNINGS_CACHE_TTL_MS));
                    return result;
                });
    }

    /**
     * {@code EARNINGS_ESTIMATES} — analyst EPS estimates for future quarters.
     * Cached for {@link #EARNINGS_CACHE_TTL_MS}.
     *
     * @param symbol equity ticker
     * @return raw JSON with estimates, or empty on error
     */
    public Optional<JsonObject> getEarningsEstimates(String symbol) {
        String sym = SymbolNormalizer.normalize(symbol);
        String url = buildUrl("EARNINGS_ESTIMATES", "symbol=" + sym);
        return http.getJson(url, null, THROTTLE_KEY);
    }

    /**
     * {@code DIVIDENDS} — historical dividend data.
     * Cached for {@link #DIVIDEND_CACHE_TTL_MS}.
     *
     * @param symbol equity ticker
     * @return dividend history, or empty on error
     */
    public Optional<AlphaVantageDividends> getDividends(String symbol) {
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<AlphaVantageDividends> cached = dividendsCache.get(sym);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("DIVIDENDS", "symbol=" + sym);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, AlphaVantageDividends.class))
                .map(result -> {
                    dividendsCache.put(sym, new CacheEntry<>(result, DIVIDEND_CACHE_TTL_MS));
                    return result;
                });
    }

    /**
     * {@code SPLITS} — stock split history.
     * Cached for {@link #SPLITS_CACHE_TTL_MS}.
     *
     * @param symbol equity ticker
     * @return splits data, or empty on error
     */
    public Optional<AlphaVantageSplits> getSplits(String symbol) {
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<AlphaVantageSplits> cached = splitsCache.get(sym);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("SPLITS", "symbol=" + sym);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, AlphaVantageSplits.class))
                .map(result -> {
                    splitsCache.put(sym, new CacheEntry<>(result, SPLITS_CACHE_TTL_MS));
                    return result;
                });
    }

    /**
     * {@code LISTING_STATUS} — list of active and delisted US securities.
     * Returns CSV parsed into {@link AlphaVantageListingStatus}.
     * Cached for 1 hour.
     *
     * @param state "active" (default) or "delisted"
     * @return listing status, or empty on error
     */
    public Optional<AlphaVantageListingStatus> getListingStatus(String state) {
        String stateParam = (state != null && !state.isBlank()) ? state : "active";
        // This endpoint returns CSV — use raw HTTP
        String url = buildUrl("LISTING_STATUS", "state=" + stateParam + "&datatype=csv");
        return fetchCsvListingStatus(url);
    }

    /**
     * {@code EARNINGS_CALENDAR} — upcoming earnings announcements.
     * Returns CSV parsed into {@link AlphaVantageEarningsCalendar}.
     * Cached for 30 minutes.
     *
     * @param symbol optional symbol filter (can be null for all upcoming)
     * @param horizon optional horizon: "3month" (default), "6month", "12month"
     * @return earnings calendar, or empty on error
     */
    public Optional<AlphaVantageEarningsCalendar> getEarningsCalendar(String symbol, String horizon) {
        if (earningsCalCache != null && !earningsCalCache.isExpired())
            return Optional.ofNullable(earningsCalCache.value);

        StringBuilder params = new StringBuilder("datatype=csv");
        if (symbol  != null && !symbol.isBlank())  params.append("&symbol=").append(symbol);
        if (horizon != null && !horizon.isBlank()) params.append("&horizon=").append(horizon);
        String url = buildUrl("EARNINGS_CALENDAR", params.toString());
        return fetchCsvEarningsCalendar(url).map(result -> {
            earningsCalCache = new CacheEntry<>(result, CALENDAR_CACHE_TTL_MS);
            return result;
        });
    }

    /**
     * {@code IPO_CALENDAR} — upcoming IPOs.
     * Returns CSV parsed into {@link AlphaVantageIpoCalendar}.
     * Cached for 30 minutes.
     *
     * @return IPO calendar, or empty on error
     */
    public Optional<AlphaVantageIpoCalendar> getIpoCalendar() {
        if (ipoCalCache != null && !ipoCalCache.isExpired())
            return Optional.ofNullable(ipoCalCache.value);

        String url = buildUrl("IPO_CALENDAR", "datatype=csv");
        return fetchCsvIpoCalendar(url).map(result -> {
            ipoCalCache = new CacheEntry<>(result, CALENDAR_CACHE_TTL_MS);
            return result;
        });
    }

    /**
     * {@code ETF_PROFILE} — ETF holdings and profile information.
     * Cached for {@link #ETF_CACHE_TTL_MS}.
     *
     * @param symbol ETF ticker (e.g. "SPY")
     * @return ETF profile, or empty on error
     */
    public Optional<AlphaVantageEtfProfile> getEtfProfile(String symbol) {
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<AlphaVantageEtfProfile> cached = etfCache.get(sym);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("ETF_PROFILE", "symbol=" + sym);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, AlphaVantageEtfProfile.class))
                .map(result -> {
                    etfCache.put(sym, new CacheEntry<>(result, ETF_CACHE_TTL_MS));
                    return result;
                });
    }

    // ─── Forex ────────────────────────────────────────────────────────────────

    /**
     * {@code CURRENCY_EXCHANGE_RATE} — real-time exchange rate between two currencies.
     * Cached for {@link #FOREX_RATE_CACHE_TTL_MS}.
     *
     * @param fromCurrency source currency (e.g. "USD")
     * @param toCurrency   target currency (e.g. "EUR")
     * @return exchange rate, or empty on error
     */
    public Optional<AlphaVantageForexRate> getForexRate(String fromCurrency, String toCurrency) {
        String cacheKey = fromCurrency + "|" + toCurrency;
        CacheEntry<AlphaVantageForexRate> cached = forexRateCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("CURRENCY_EXCHANGE_RATE",
                "from_currency=" + fromCurrency + "&to_currency=" + toCurrency);
        return http.getJson(url, null, THROTTLE_KEY)
                .flatMap(AlphaVantageForexRate::fromRoot)
                .map(result -> {
                    forexRateCache.put(cacheKey, new CacheEntry<>(result, FOREX_RATE_CACHE_TTL_MS));
                    return result;
                });
    }

    // ─── Crypto ────────────────────────────────────────────────────────────────

    /**
     * {@code CRYPTO_RATING} — FCAS health index for a cryptocurrency.
     * Cached for {@link #CRYPTO_RATING_CACHE_TTL}.
     *
     * @param symbol crypto symbol (e.g. "BTC")
     * @return crypto rating, or empty on error
     */
    public Optional<AlphaVantageCryptoRating> getCryptoRating(String symbol) {
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<AlphaVantageCryptoRating> cached = cryptoRatingCache.get(sym);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl("CRYPTO_RATING", "symbol=" + sym);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> {
                    // Response is wrapped: { "Crypto Rating (FCAS)": { ... } }
                    if (root.has("Crypto Rating (FCAS)")) {
                        return gson.fromJson(root.getAsJsonObject("Crypto Rating (FCAS)"),
                                AlphaVantageCryptoRating.class);
                    }
                    return gson.fromJson(root, AlphaVantageCryptoRating.class);
                })
                .map(result -> {
                    cryptoRatingCache.put(sym, new CacheEntry<>(result, CRYPTO_RATING_CACHE_TTL));
                    return result;
                });
    }

    // ─── Commodities ──────────────────────────────────────────────────────────

    /**
     * Fetches commodity price data.
     * Supports: {@code WTI}, {@code BRENT}, {@code NATURAL_GAS}, {@code GOLD},
     * {@code SILVER}, {@code COPPER}, {@code WHEAT}, {@code CORN}.
     * Cached for {@link #COMMODITY_CACHE_TTL_MS}.
     *
     * @param commodity commodity function name (e.g. "WTI", "GOLD")
     * @param interval  "daily", "weekly" (default), "monthly"
     * @return commodity data, or empty on error
     */
    public Optional<AlphaVantageCommodity> getCommodity(String commodity, String interval) {
        String ivl = (interval != null && !interval.isBlank()) ? interval : "weekly";
        String cacheKey = commodity + "|" + ivl;
        CacheEntry<AlphaVantageCommodity> cached = commodityCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl(commodity.toUpperCase(), "interval=" + ivl);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, AlphaVantageCommodity.class))
                .filter(c -> c.data() != null && !c.data().isEmpty())
                .map(result -> {
                    commodityCache.put(cacheKey, new CacheEntry<>(result, COMMODITY_CACHE_TTL_MS));
                    return result;
                });
    }

    // ─── Economic Indicators ──────────────────────────────────────────────────

    /**
     * Fetches an economic indicator.
     * Supports: {@code REAL_GDP}, {@code REAL_GDP_PER_CAPITA}, {@code TREASURY_YIELD},
     * {@code FEDERAL_FUNDS_RATE}, {@code CPI}, {@code INFLATION}, {@code RETAIL_SALES},
     * {@code DURABLE_GOODS_ORDERS}, {@code UNEMPLOYMENT}, {@code NONFARM_PAYROLL}.
     * Cached for {@link #ECONOMIC_CACHE_TTL_MS}.
     *
     * @param indicatorFunction the function name (e.g. "REAL_GDP", "CPI")
     * @param interval          "annual", "quarterly", "monthly" (varies by indicator)
     * @param maturity          for TREASURY_YIELD: "3month", "2year", "5year", "7year",
     *                          "10year" (default), "30year"
     * @return economic indicator data, or empty on error
     */
    public Optional<AlphaVantageEconomicIndicator> getEconomicIndicator(
            String indicatorFunction, String interval, String maturity) {
        String cacheKey = indicatorFunction + "|" + interval + "|" + maturity;
        CacheEntry<AlphaVantageEconomicIndicator> cached = economicCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        StringBuilder params = new StringBuilder();
        if (interval != null && !interval.isBlank()) appendParam(params, "interval", interval);
        if (maturity != null && !maturity.isBlank()) appendParam(params, "maturity", maturity);

        String url = buildUrl(indicatorFunction.toUpperCase(), params.toString());
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, AlphaVantageEconomicIndicator.class))
                .filter(e -> e.data() != null && !e.data().isEmpty())
                .map(result -> {
                    economicCache.put(cacheKey, new CacheEntry<>(result, ECONOMIC_CACHE_TTL_MS));
                    return result;
                });
    }

    // ─── Technical Indicators ─────────────────────────────────────────────────

    /**
     * Fetches any technical indicator supported by Alpha Vantage (60+ functions).
     * Examples: {@code SMA}, {@code EMA}, {@code RSI}, {@code MACD}, {@code BBANDS},
     * {@code STOCH}, {@code ADX}, {@code ATR}, {@code CCI}, {@code OBV}, etc.
     * Cached for {@link #INDICATOR_CACHE_TTL_MS}.
     *
     * @param function    indicator function name (e.g. "SMA", "RSI")
     * @param symbol      equity/forex/crypto ticker
     * @param interval    "1min","5min","15min","30min","60min","daily","weekly","monthly"
     * @param timePeriod  lookback period (e.g. 14 for RSI); 0 = use API default
     * @param seriesType  "close","open","high","low" (null = use API default "close")
     * @param extraParams additional query params as key=value pairs (can be null)
     * @param limit       max data points to return (0 = all)
     * @return technical indicator result
     */
    public AlphaVantageTechnicalIndicator getTechnicalIndicator(
            String function, String symbol, String interval,
            int timePeriod, String seriesType,
            Map<String, String> extraParams, int limit) {

        String sym = SymbolNormalizer.normalize(symbol);
        String cacheKey = function + "|" + sym + "|" + interval + "|" + timePeriod
                + "|" + seriesType + "|" + limit;
        CacheEntry<AlphaVantageTechnicalIndicator> cached = indicatorCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) return cached.value;

        StringBuilder params = new StringBuilder("symbol=").append(sym)
                .append("&interval=").append(interval);
        if (timePeriod > 0) params.append("&time_period=").append(timePeriod);
        if (seriesType != null && !seriesType.isBlank()) params.append("&series_type=").append(seriesType);
        if (extraParams != null) {
            extraParams.forEach((k, v) -> params.append("&").append(k).append("=").append(v));
        }

        AlphaVantageTechnicalIndicator result = http.getJson(buildUrl(function.toUpperCase(), params.toString()),
                        null, THROTTLE_KEY)
                .map(root -> AlphaVantageTechnicalIndicator.fromRoot(root, sym, limit))
                .orElse(new AlphaVantageTechnicalIndicator(sym, function, interval,
                        String.valueOf(timePeriod), seriesType != null ? seriesType : "close",
                        List.of()));

        indicatorCache.put(cacheKey, new CacheEntry<>(result, INDICATOR_CACHE_TTL_MS));
        return result;
    }

    // ─── Convenience Technical Indicator Wrappers ─────────────────────────────

    /** Simple Moving Average (SMA). */
    public AlphaVantageTechnicalIndicator getSma(String symbol, String interval, int period, int limit) {
        return getTechnicalIndicator("SMA", symbol, interval, period, "close", null, limit);
    }

    /** Exponential Moving Average (EMA). */
    public AlphaVantageTechnicalIndicator getEma(String symbol, String interval, int period, int limit) {
        return getTechnicalIndicator("EMA", symbol, interval, period, "close", null, limit);
    }

    /** Weighted Moving Average (WMA). */
    public AlphaVantageTechnicalIndicator getWma(String symbol, String interval, int period, int limit) {
        return getTechnicalIndicator("WMA", symbol, interval, period, "close", null, limit);
    }

    /** Relative Strength Index (RSI). */
    public AlphaVantageTechnicalIndicator getRsi(String symbol, String interval, int period, int limit) {
        return getTechnicalIndicator("RSI", symbol, interval, period, "close", null, limit);
    }

    /** Moving Average Convergence/Divergence (MACD). */
    public AlphaVantageTechnicalIndicator getMacd(String symbol, String interval, int limit) {
        return getTechnicalIndicator("MACD", symbol, interval, 0, "close", null, limit);
    }

    /** Bollinger Bands (BBANDS). */
    public AlphaVantageTechnicalIndicator getBbands(String symbol, String interval, int period, int limit) {
        return getTechnicalIndicator("BBANDS", symbol, interval, period, "close", null, limit);
    }

    /** Stochastic Oscillator (STOCH). */
    public AlphaVantageTechnicalIndicator getStoch(String symbol, String interval, int limit) {
        return getTechnicalIndicator("STOCH", symbol, interval, 0, null, null, limit);
    }

    /** Average Directional Index (ADX). */
    public AlphaVantageTechnicalIndicator getAdx(String symbol, String interval, int period, int limit) {
        return getTechnicalIndicator("ADX", symbol, interval, period, null, null, limit);
    }

    /** Average True Range (ATR). */
    public AlphaVantageTechnicalIndicator getAtr(String symbol, String interval, int period, int limit) {
        return getTechnicalIndicator("ATR", symbol, interval, period, null, null, limit);
    }

    /** Commodity Channel Index (CCI). */
    public AlphaVantageTechnicalIndicator getCci(String symbol, String interval, int period, int limit) {
        return getTechnicalIndicator("CCI", symbol, interval, period, null, null, limit);
    }

    /** On Balance Volume (OBV). */
    public AlphaVantageTechnicalIndicator getObv(String symbol, String interval, int limit) {
        return getTechnicalIndicator("OBV", symbol, interval, 0, null, null, limit);
    }

    /** Williams %R (WILLR). */
    public AlphaVantageTechnicalIndicator getWillr(String symbol, String interval, int period, int limit) {
        return getTechnicalIndicator("WILLR", symbol, interval, period, null, null, limit);
    }

    /** Momentum (MOM). */
    public AlphaVantageTechnicalIndicator getMom(String symbol, String interval, int period, int limit) {
        return getTechnicalIndicator("MOM", symbol, interval, period, "close", null, limit);
    }

    /** Rate of Change (ROC). */
    public AlphaVantageTechnicalIndicator getRoc(String symbol, String interval, int period, int limit) {
        return getTechnicalIndicator("ROC", symbol, interval, period, "close", null, limit);
    }

    /** Parabolic SAR (SAR). */
    public AlphaVantageTechnicalIndicator getSar(String symbol, String interval, int limit) {
        return getTechnicalIndicator("SAR", symbol, interval, 0, null, null, limit);
    }

    /** Money Flow Index (MFI). */
    public AlphaVantageTechnicalIndicator getMfi(String symbol, String interval, int period, int limit) {
        return getTechnicalIndicator("MFI", symbol, interval, period, null, null, limit);
    }

    /** Chaikin A/D Line (AD). */
    public AlphaVantageTechnicalIndicator getAd(String symbol, String interval, int limit) {
        return getTechnicalIndicator("AD", symbol, interval, 0, null, null, limit);
    }

    /** Standard Deviation (STDDEV). */
    public AlphaVantageTechnicalIndicator getStddev(String symbol, String interval, int period, int limit) {
        return getTechnicalIndicator("STDDEV", symbol, interval, period, "close", null, limit);
    }

    // ─── URL builder ──────────────────────────────────────────────────────────

    /**
     * Builds a complete Alpha Vantage API URL.
     *
     * @param function  the API function name (e.g. "TIME_SERIES_DAILY")
     * @param params    additional query params without leading "?" (may be empty)
     * @return complete URL string
     */
    public String buildUrl(String function, String params) {
        String key = keys.getAlphavantageKey();
        if (key == null || key.isBlank()) key = "demo";
        StringBuilder sb = new StringBuilder(BASE_URL)
                .append("?function=").append(function);
        if (params != null && !params.isBlank()) {
            sb.append("&").append(params);
        }
        sb.append("&apikey=").append(key);
        return sb.toString();
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private List<OhlcvBar> getTimeSeries(String symbol, String function,
                                          String timeframe, String interval,
                                          int limit) {
        String sym = SymbolNormalizer.normalize(symbol);
        String cacheKey = function + "|" + sym + "|" + timeframe + "|" + limit;
        CacheEntry<List<OhlcvBar>> cached = ohlcvCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) return cached.value;

        StringBuilder params = new StringBuilder("symbol=").append(sym).append("&outputsize=compact");
        if (interval != null && !interval.isBlank()) params.append("&interval=").append(interval);
        String url = buildUrl(function, params.toString());

        boolean intraday = interval != null && !interval.isBlank();
        AssetType type = assetType(sym);
        List<OhlcvBar> bars = fetchOhlcv(url, sym, timeframe, limit, intraday, type);
        ohlcvCache.put(cacheKey, new CacheEntry<>(bars, OHLCV_CACHE_TTL_MS));
        return bars;
    }

    private List<OhlcvBar> fetchOhlcv(String url, String sym, String timeframe,
                                       int limit, boolean intraday, AssetType type) {
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> AlphaVantageTimeSeriesParser.parse(root, sym, timeframe, limit, intraday, type))
                .orElse(List.of());
    }

    private Optional<AlphaVantageFinancialStatement> fetchFinancialStatement(
            String symbol, String function,
            Map<String, CacheEntry<AlphaVantageFinancialStatement>> cache) {
        String sym = SymbolNormalizer.normalize(symbol);
        CacheEntry<AlphaVantageFinancialStatement> cached = cache.get(sym);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = buildUrl(function, "symbol=" + sym);
        return http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, AlphaVantageFinancialStatement.class))
                .filter(s -> s.symbol() != null)
                .map(result -> {
                    cache.put(sym, new CacheEntry<>(result, FINANCIAL_CACHE_TTL_MS));
                    return result;
                });
    }

    // ─── CSV helpers (LISTING_STATUS, EARNINGS_CALENDAR, IPO_CALENDAR) ────────

    private Optional<AlphaVantageListingStatus> fetchCsvListingStatus(String url) {
        try {
            String body = fetchRawCsv(url);
            if (body == null || body.isBlank()) return Optional.empty();
            List<AlphaVantageListingStatus.ListingEntry> entries = new ArrayList<>();
            String[] lines = body.split("\n");
            for (int i = 1; i < lines.length && i < 5001; i++) { // max 5000 entries
                String[] cols = lines[i].split(",", -1);
                if (cols.length < 7) continue;
                entries.add(new AlphaVantageListingStatus.ListingEntry(
                        cols[0].trim(), cols[1].trim(), cols[2].trim(),
                        cols[3].trim(), cols[4].trim(), cols[5].trim(), cols[6].trim()
                ));
            }
            return Optional.of(new AlphaVantageListingStatus(entries));
        } catch (Exception e) {
            log.warn("AlphaVantage LISTING_STATUS CSV parse error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<AlphaVantageEarningsCalendar> fetchCsvEarningsCalendar(String url) {
        try {
            String body = fetchRawCsv(url);
            if (body == null || body.isBlank()) return Optional.empty();
            List<AlphaVantageEarningsCalendar.EarningsEvent> events = new ArrayList<>();
            String[] lines = body.split("\n");
            for (int i = 1; i < lines.length; i++) {
                String[] cols = lines[i].split(",", -1);
                if (cols.length < 6) continue;
                events.add(new AlphaVantageEarningsCalendar.EarningsEvent(
                        cols[0].trim(), cols[1].trim(), cols[2].trim(),
                        cols[3].trim(), cols[4].trim(), cols[5].trim()
                ));
            }
            return Optional.of(new AlphaVantageEarningsCalendar(events));
        } catch (Exception e) {
            log.warn("AlphaVantage EARNINGS_CALENDAR CSV parse error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<AlphaVantageIpoCalendar> fetchCsvIpoCalendar(String url) {
        try {
            String body = fetchRawCsv(url);
            if (body == null || body.isBlank()) return Optional.empty();
            List<AlphaVantageIpoCalendar.IpoEvent> events = new ArrayList<>();
            String[] lines = body.split("\n");
            for (int i = 1; i < lines.length; i++) {
                String[] cols = lines[i].split(",", -1);
                if (cols.length < 7) continue;
                events.add(new AlphaVantageIpoCalendar.IpoEvent(
                        cols[0].trim(), cols[1].trim(), cols[2].trim(),
                        cols[3].trim(), cols[4].trim(), cols[5].trim(), cols[6].trim()
                ));
            }
            return Optional.of(new AlphaVantageIpoCalendar(events));
        } catch (Exception e) {
            log.warn("AlphaVantage IPO_CALENDAR CSV parse error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String fetchRawCsv(String url) {
        try {
            // Use OkHttp directly for CSV endpoints (HttpJsonClient handles only JSON)
            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                    .build();
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(url)
                    .addHeader("Accept", "text/csv")
                    .addHeader("User-Agent", "TradingPlatform/1.0")
                    .build();
            try (okhttp3.Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return null;
                return response.body().string();
            }
        } catch (Exception e) {
            log.warn("AlphaVantage CSV fetch error for {}: {}", url, e.getMessage());
            return null;
        }
    }

    // ─── DB persistence (overview snapshot) ────────────────────────────────────

    private void persistOverviewSnapshot(AlphaVantageCompanyOverview o) {
        try {
            ensureOverviewTable();
            String upsert = isPostgres()
                    ? """
                      INSERT INTO av_company_overview
                        (symbol, name, sector, industry, market_cap, pe_ratio,
                         dividend_yield, eps, exchange, currency, snapshot_time)
                      VALUES (?,?,?,?,?,?,?,?,?,?,?)
                      ON CONFLICT (symbol) DO UPDATE SET
                        name           = EXCLUDED.name,
                        sector         = EXCLUDED.sector,
                        industry       = EXCLUDED.industry,
                        market_cap     = EXCLUDED.market_cap,
                        pe_ratio       = EXCLUDED.pe_ratio,
                        dividend_yield = EXCLUDED.dividend_yield,
                        eps            = EXCLUDED.eps,
                        exchange       = EXCLUDED.exchange,
                        currency       = EXCLUDED.currency,
                        snapshot_time  = EXCLUDED.snapshot_time
                      """
                    : """
                      INSERT OR REPLACE INTO av_company_overview
                        (symbol, name, sector, industry, market_cap, pe_ratio,
                         dividend_yield, eps, exchange, currency, snapshot_time)
                      VALUES (?,?,?,?,?,?,?,?,?,?,?)
                      """;
            jdbc.update(upsert,
                    o.symbol(), o.name(), o.sector(), o.industry(),
                    parseBD(o.marketCapitalization()),
                    parseBD(o.peRatio()),
                    parseBD(o.dividendYield()),
                    parseBD(o.eps()),
                    o.exchange(), o.currency(),
                    Timestamp.valueOf(LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("AlphaVantage overview persist error: {}", e.getMessage());
        }
    }

    private void ensureOverviewTable() {
        if (!ensuredTables.add("av_company_overview")) return;
        String ddl = isPostgres()
                ? """
                  CREATE TABLE IF NOT EXISTS av_company_overview (
                    symbol         TEXT PRIMARY KEY,
                    name           TEXT,
                    sector         TEXT,
                    industry       TEXT,
                    market_cap     NUMERIC,
                    pe_ratio       NUMERIC,
                    dividend_yield NUMERIC,
                    eps            NUMERIC,
                    exchange       TEXT,
                    currency       TEXT,
                    snapshot_time  TIMESTAMP
                  )
                  """
                : """
                  CREATE TABLE IF NOT EXISTS av_company_overview (
                    symbol         TEXT PRIMARY KEY,
                    name           TEXT,
                    sector         TEXT,
                    industry       TEXT,
                    market_cap     REAL,
                    pe_ratio       REAL,
                    dividend_yield REAL,
                    eps            REAL,
                    exchange       TEXT,
                    currency       TEXT,
                    snapshot_time  TEXT
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

    // ─── Util ─────────────────────────────────────────────────────────────────

    private static AssetType assetType(String s) {
        if (AssetClassDetector.isCrypto(s)) return AssetType.CRYPTO;
        if (AssetClassDetector.isForex(s))  return AssetType.FOREX;
        return AssetType.STOCK;
    }

    private static BigDecimal parseBD(String s) {
        if (s == null || s.isBlank() || "None".equalsIgnoreCase(s)) return BigDecimal.ZERO;
        try { return new BigDecimal(s); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private static void appendParam(StringBuilder sb, String key, String value) {
        if (!sb.isEmpty()) sb.append("&");
        sb.append(key).append("=").append(value);
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
