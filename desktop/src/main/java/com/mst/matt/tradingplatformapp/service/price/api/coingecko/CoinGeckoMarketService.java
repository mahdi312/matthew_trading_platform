package com.mst.matt.tradingplatformapp.service.price.api.coingecko;

import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;

/**
 * Typed service layer for CoinGecko market-data endpoints.
 *
 * Responsibilities:
 * <ul>
 *   <li>Fetching and caching {@code /coins/markets} (top-N coins list with price, cap, volume).</li>
 *   <li>Fetching detailed {@code /coins/{id}} data.</li>
 *   <li>Persisting snapshot rows to a {@code {SYMBOL}_COINGECKO_MARKET} table (PostgreSQL)
 *       or a shared {@code coingecko_market_snapshot} table (SQLite).</li>
 *   <li>Providing global market stats via {@code /global}.</li>
 * </ul>
 *
 * All HTTP calls go through the shared {@link CoinGeckoService} which applies rate limiting.
 */
@Service
public class CoinGeckoMarketService {

    private static final Logger log = LoggerFactory.getLogger(CoinGeckoMarketService.class);

    /** How long typed market-data stays fresh in memory (ms). */
    private static final long MARKET_CACHE_TTL_MS = 5 * 60_000L;   // 5 minutes
    private static final long GLOBAL_CACHE_TTL_MS = 3 * 60_000L;   // 3 minutes
    private static final long COIN_DETAIL_TTL_MS  = 10 * 60_000L;  // 10 minutes

    private final CoinGeckoService coinGeckoService;
    private final JdbcTemplate     jdbc;

    // ── In-memory cache ────────────────────────────────────────────────────────

    /** Key: "vs_currency|order|perPage|page|ids" → cached entry */
    private final Map<String, CacheEntry<List<CoinGeckoMarketCoin>>> marketsCache =
            new ConcurrentHashMap<>();

    /** Key: coin id → cached full coin detail */
    private final Map<String, CacheEntry<CoinGeckoFullCoin>> coinDetailCache =
            new ConcurrentHashMap<>();

    /** Global market data cache (single entry) */
    private volatile CacheEntry<JsonObject> globalCache;

    /** Tables already ensured this session */
    private final KeySetView<String, Boolean> ensuredTables = ConcurrentHashMap.newKeySet();

    public CoinGeckoMarketService(CoinGeckoService coinGeckoService, JdbcTemplate jdbc) {
        this.coinGeckoService = coinGeckoService;
        this.jdbc = jdbc;
    }

    // ── /coins/markets ─────────────────────────────────────────────────────────

    /**
     * Returns a list of coins with market data (price, cap, volume, change).
     * Results are cached for {@link #MARKET_CACHE_TTL_MS} milliseconds.
     *
     * @param vsCurrency          quote currency, e.g. "usd"
     * @param idsCsv              comma-separated coin IDs (null = top by market-cap)
     * @param order               "market_cap_desc", "volume_desc", etc.
     * @param perPage             1–250
     * @param page                page index (1-based)
     * @param priceChangePeriod   "24h", "7d", etc. (null = omit)
     * @return typed list of market-coin rows
     */
    public List<CoinGeckoMarketCoin> getCoinsMarkets(String vsCurrency, String idsCsv,
                                                      String order, int perPage, int page,
                                                      String priceChangePeriod) {
        String cacheKey = vsCurrency + "|" + order + "|" + perPage + "|" + page + "|" + idsCsv;
        CacheEntry<List<CoinGeckoMarketCoin>> cached = marketsCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) return cached.value;

        List<CoinGeckoMarketCoin> result = coinGeckoService.getCoinsMarketsTyped(
                vsCurrency, idsCsv, order, perPage, page, false, priceChangePeriod);

        marketsCache.put(cacheKey, new CacheEntry<>(result, MARKET_CACHE_TTL_MS));
        if (!result.isEmpty()) {
            persistMarketSnapshots(result);
        }
        return result;
    }

    /**
     * Shorthand: top 50 coins by market cap in USD.
     */
    public List<CoinGeckoMarketCoin> getTopCoinsMarkets(int limit) {
        int perPage = Math.min(limit, 250);
        return getCoinsMarkets("usd", null, "market_cap_desc", perPage, 1, "24h");
    }

    // ── /coins/{id} ────────────────────────────────────────────────────────────

    /**
     * Returns detailed coin information (market data, links, community stats).
     * Cached for {@link #COIN_DETAIL_TTL_MS} ms.
     *
     * @param coinId CoinGecko coin ID (e.g. "bitcoin")
     * @return optional typed coin detail
     */
    public Optional<CoinGeckoFullCoin> getCoinDetail(String coinId) {
        CacheEntry<CoinGeckoFullCoin> cached = coinDetailCache.get(coinId);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        Optional<CoinGeckoFullCoin> result = coinGeckoService.getCoinByIdTyped(
                coinId, false, true, false, false, false);

        result.ifPresent(c -> coinDetailCache.put(coinId, new CacheEntry<>(c, COIN_DETAIL_TTL_MS)));
        return result;
    }

    /**
     * Convenience: resolve symbol → coin ID, then fetch detail.
     *
     * @param symbol trading symbol (e.g. "BTC", "BTCUSDT")
     * @return optional typed coin detail
     */
    public Optional<CoinGeckoFullCoin> getCoinDetailBySymbol(String symbol) {
        String coinId = coinGeckoService.resolveCoinId(symbol);
        if (coinId == null) return Optional.empty();
        return getCoinDetail(coinId);
    }

    // ── /global ────────────────────────────────────────────────────────────────

    /**
     * Returns the raw CoinGecko global market stats JSON object.
     * Cached for {@link #GLOBAL_CACHE_TTL_MS} ms.
     *
     * @return optional JsonObject with "data" key
     */
    public Optional<JsonObject> getGlobalMarketData() {
        if (globalCache != null && !globalCache.isExpired()) {
            return Optional.ofNullable(globalCache.value);
        }
        Optional<JsonObject> result = coinGeckoService.getGlobalData();
        result.ifPresent(obj -> {
            globalCache = new CacheEntry<>(obj, GLOBAL_CACHE_TTL_MS);
            persistGlobalMarketData(obj);
        });
        return result;
    }

    // ── DB persistence ─────────────────────────────────────────────────────────

    /**
     * Persists a list of market-coin snapshots into the shared
     * {@code coingecko_market_snapshot} table.  This lightweight table is used
     * for offline fallback and historical comparison.
     *
     * Schema (created on first use):
     * <pre>
     *   coin_id          TEXT PRIMARY KEY
     *   symbol           TEXT
     *   name             TEXT
     *   current_price    NUMERIC
     *   market_cap       NUMERIC
     *   total_volume     NUMERIC
     *   price_change_pct NUMERIC
     *   high_24h         NUMERIC
     *   low_24h          NUMERIC
     *   snapshot_time    TIMESTAMP
     * </pre>
     */
    private void persistMarketSnapshots(List<CoinGeckoMarketCoin> coins) {
        try {
            ensureMarketSnapshotTable();
            LocalDateTime now = LocalDateTime.now();
            String upsert = isPostgres()
                    ? """
                      INSERT INTO coingecko_market_snapshot
                        (coin_id, symbol, name, current_price, market_cap, total_volume,
                         price_change_pct, high_24h, low_24h, snapshot_time)
                      VALUES (?,?,?,?,?,?,?,?,?,?)
                      ON CONFLICT (coin_id) DO UPDATE SET
                        current_price    = EXCLUDED.current_price,
                        market_cap       = EXCLUDED.market_cap,
                        total_volume     = EXCLUDED.total_volume,
                        price_change_pct = EXCLUDED.price_change_pct,
                        high_24h         = EXCLUDED.high_24h,
                        low_24h          = EXCLUDED.low_24h,
                        snapshot_time    = EXCLUDED.snapshot_time
                      """
                    : """
                      INSERT OR REPLACE INTO coingecko_market_snapshot
                        (coin_id, symbol, name, current_price, market_cap, total_volume,
                         price_change_pct, high_24h, low_24h, snapshot_time)
                      VALUES (?,?,?,?,?,?,?,?,?,?)
                      """;
            for (CoinGeckoMarketCoin c : coins) {
                jdbc.update(upsert,
                        c.id(), c.symbol(), c.name(),
                        c.currentPrice(), c.marketCap(), c.totalVolume(),
                        c.priceChangePct24h(), c.high24h(), c.low24h(),
                        Timestamp.valueOf(now));
            }
        } catch (Exception e) {
            log.warn("Failed to persist market snapshots: {}", e.getMessage());
        }
    }

    /**
     * Persists global market data into the {@code global_market_data} table.
     *
     * Schema (created on first use):
     * <pre>
     *   provider         TEXT
     *   total_market_cap NUMERIC
     *   btc_dominance    NUMERIC
     *   active_cryptos   INTEGER
     *   snapshot_time    TIMESTAMP
     * </pre>
     */
    private void persistGlobalMarketData(JsonObject root) {
        try {
            ensureGlobalMarketDataTable();
            if (!root.has("data")) return;
            JsonObject data = root.getAsJsonObject("data");

            BigDecimal totalMarketCap = BigDecimal.ZERO;
            BigDecimal btcDominance   = BigDecimal.ZERO;
            int        activeCryptos  = 0;

            if (data.has("total_market_cap") && data.getAsJsonObject("total_market_cap").has("usd")) {
                totalMarketCap = data.getAsJsonObject("total_market_cap").get("usd").getAsBigDecimal();
            }
            if (data.has("market_cap_percentage") && data.getAsJsonObject("market_cap_percentage").has("btc")) {
                btcDominance = data.getAsJsonObject("market_cap_percentage").get("btc").getAsBigDecimal();
            }
            if (data.has("active_cryptocurrencies")) {
                activeCryptos = data.get("active_cryptocurrencies").getAsInt();
            }

            String upsert = isPostgres()
                    ? """
                      INSERT INTO global_market_data
                        (provider, total_market_cap, btc_dominance, active_cryptos, snapshot_time)
                      VALUES ('COINGECKO',?,?,?,?)
                      ON CONFLICT (provider) DO UPDATE SET
                        total_market_cap = EXCLUDED.total_market_cap,
                        btc_dominance    = EXCLUDED.btc_dominance,
                        active_cryptos   = EXCLUDED.active_cryptos,
                        snapshot_time    = EXCLUDED.snapshot_time
                      """
                    : """
                      INSERT OR REPLACE INTO global_market_data
                        (provider, total_market_cap, btc_dominance, active_cryptos, snapshot_time)
                      VALUES ('COINGECKO',?,?,?,?)
                      """;
            jdbc.update(upsert, totalMarketCap, btcDominance, activeCryptos,
                    Timestamp.valueOf(LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("Failed to persist global market data: {}", e.getMessage());
        }
    }

    // ── Typed wrappers with coin-ID resolution ────────────────────────────────

    /**
     * Returns market data rows for a set of symbols (resolves symbol → coin ID).
     *
     * @param symbols list of trading symbols (e.g. "BTC", "BTCUSDT")
     * @return typed market-coin rows for recognised symbols
     */
    public List<CoinGeckoMarketCoin> getMarketDataForSymbols(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return Collections.emptyList();
        List<String> coinIds = new ArrayList<>();
        for (String sym : symbols) {
            String id = coinGeckoService.resolveCoinId(sym);
            if (id != null) coinIds.add(id);
        }
        if (coinIds.isEmpty()) return Collections.emptyList();
        return getCoinsMarkets("usd", String.join(",", coinIds),
                "market_cap_desc", coinIds.size(), 1, "24h");
    }

    /**
     * Returns the simple price for a single symbol using /simple/price.
     * Lighter than /coins/markets — useful for ticker updates.
     *
     * @param symbol trading symbol
     * @return optional price in USD, or empty if not found / error
     */
    public Optional<BigDecimal> getSimpleUsdPrice(String symbol) {
        String coinId = coinGeckoService.resolveCoinId(symbol);
        if (coinId == null) return Optional.empty();
        Map<String, ?> prices = coinGeckoService.getSimplePriceTyped(
                coinId, "usd", false, false, false);
        Object entry = prices.get(coinId);
        if (entry == null) return Optional.empty();
        // CoinGeckoSimplePrice has .usd() accessor
        if (entry instanceof com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoSimplePrice sp) {
            return Optional.of(sp.usd());
        }
        return Optional.empty();
    }

    // ── Table DDL helpers ─────────────────────────────────────────────────────

    private void ensureMarketSnapshotTable() {
        if (!ensuredTables.add("coingecko_market_snapshot")) return;
        String ddl = isPostgres()
                ? """
                  CREATE TABLE IF NOT EXISTS coingecko_market_snapshot (
                    coin_id          TEXT PRIMARY KEY,
                    symbol           TEXT,
                    name             TEXT,
                    current_price    NUMERIC,
                    market_cap       NUMERIC,
                    total_volume     NUMERIC,
                    price_change_pct NUMERIC,
                    high_24h         NUMERIC,
                    low_24h          NUMERIC,
                    snapshot_time    TIMESTAMP
                  )
                  """
                : """
                  CREATE TABLE IF NOT EXISTS coingecko_market_snapshot (
                    coin_id          TEXT PRIMARY KEY,
                    symbol           TEXT,
                    name             TEXT,
                    current_price    REAL,
                    market_cap       REAL,
                    total_volume     REAL,
                    price_change_pct REAL,
                    high_24h         REAL,
                    low_24h          REAL,
                    snapshot_time    TEXT
                  )
                  """;
        jdbc.execute(ddl);
    }

    private void ensureGlobalMarketDataTable() {
        if (!ensuredTables.add("global_market_data")) return;
        String ddl = isPostgres()
                ? """
                  CREATE TABLE IF NOT EXISTS global_market_data (
                    provider         TEXT PRIMARY KEY,
                    total_market_cap NUMERIC,
                    btc_dominance    NUMERIC,
                    active_cryptos   INTEGER,
                    snapshot_time    TIMESTAMP
                  )
                  """
                : """
                  CREATE TABLE IF NOT EXISTS global_market_data (
                    provider         TEXT PRIMARY KEY,
                    total_market_cap REAL,
                    btc_dominance    REAL,
                    active_cryptos   INTEGER,
                    snapshot_time    TEXT
                  )
                  """;
        jdbc.execute(ddl);
    }

    private boolean isPostgres() {
        try {
            String url = jdbc.getDataSource() != null
                    ? jdbc.getDataSource().getConnection().getMetaData().getURL()
                    : "";
            return url.startsWith("jdbc:postgresql");
        } catch (Exception e) {
            return false;
        }
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

    // ── Accessors used by controllers ─────────────────────────────────────────

    /**
     * Returns the underlying CoinGeckoService (for advanced callers).
     */
    public CoinGeckoService getCoinGeckoService() { return coinGeckoService; }

    /**
     * Fetches exchange rates ({@code /exchange_rates}) and returns the raw JSON.
     * Delegates to CoinGeckoService.
     */
    public Optional<JsonObject> getExchangeRates() {
        return coinGeckoService.getExchangeRates();
    }

    /**
     * Returns typed exchange rates (wraps the raw JSON).
     */
    public Optional<com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoExchangeRates>
    getExchangeRatesTyped() {
        Optional<JsonObject> raw = coinGeckoService.getExchangeRates();
        if (raw.isEmpty()) return Optional.empty();
        try {
            return Optional.of(coinGeckoService.getGson().fromJson(
                    raw.get(), com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoExchangeRates.class));
        } catch (Exception e) {
            log.warn("Failed to parse exchange rates: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
