package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoCoinBasic;
import com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoSearchResult;
import com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoTrendingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;
import java.util.stream.Collectors;

/**
 * Search and discovery service using CoinGecko free endpoints.
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>{@code GET /search} — real-time coin/exchange/NFT search.</li>
 *   <li>{@code GET /search/trending} — trending coins and NFTs.</li>
 *   <li>{@code GET /coins/list} — full coin catalogue, synced to DB daily.</li>
 *   <li>In-memory coin-ID lookup table built from the daily DB sync.</li>
 * </ul>
 *
 * <h3>Daily sync:</h3>
 * The {@link #syncCoinsListToDb()} method is scheduled daily at 04:00 UTC.
 * It calls {@link CoinGeckoService#fetchCoinsList()}, writes all entries to
 * the {@code coingecko_coins_list} table, and refreshes the in-memory lookup map.
 *
 * <h3>Symbol resolution:</h3>
 * {@link #resolveCoinId(String)} checks the static map in {@link CoinGeckoService}
 * first (fast path), then falls back to the DB-backed lookup table.
 */
@Service
public class CoinGeckoSearchService {

    private static final Logger log = LoggerFactory.getLogger(CoinGeckoSearchService.class);

    private static final long TRENDING_CACHE_TTL_MS = 15 * 60_000L; // 15 minutes
    private static final long SEARCH_CACHE_TTL_MS   = 5  * 60_000L; // 5 minutes

    private final CoinGeckoService coinGeckoService;
    private final JdbcTemplate     jdbc;

    /** In-memory lookup: upper-case symbol → CoinGecko coin ID (e.g. "BTC" → "bitcoin") */
    private volatile Map<String, String> symbolToIdCache = Collections.emptyMap();

    /** Last time the in-memory cache was populated from the DB */
    private volatile long symbolCacheLoadedAt = 0L;

    /** Tables created this session */
    private final KeySetView<String, Boolean> ensuredTables = ConcurrentHashMap.newKeySet();

    /** Trending coins cache */
    private volatile CacheEntry<CoinGeckoTrendingResult> trendingCache;

    /** Search query cache */
    private final Map<String, CacheEntry<CoinGeckoSearchResult>> searchCache =
            new ConcurrentHashMap<>();

    public CoinGeckoSearchService(CoinGeckoService coinGeckoService, JdbcTemplate jdbc) {
        this.coinGeckoService = coinGeckoService;
        this.jdbc = jdbc;
    }

    // ── Symbol / coin-ID resolution ────────────────────────────────────────────

    /**
     * Resolves a trading symbol to a CoinGecko coin ID.
     * <ol>
     *   <li>Static map in {@link CoinGeckoService} (fast, ~20 entries).</li>
     *   <li>In-memory DB-backed lookup table (populated from daily sync).</li>
     * </ol>
     *
     * @param symbol raw trading symbol (e.g. "BTC", "BTCUSDT", "ethereum")
     * @return CoinGecko coin ID (e.g. "bitcoin"), or {@code null} if unresolved
     */
    public String resolveCoinId(String symbol) {
        String id = coinGeckoService.lookupStaticCoinId(symbol);
        if (id != null) return id;
        return lookupCoinIdFromDb(symbol);
    }

    /**
     * Resolves a symbol using only the DB-backed lookup table (no static map).
     * Called by {@link CoinGeckoService} after the static fast-path misses.
     */
    public String lookupCoinIdFromDb(String symbol) {
        if (symbol == null || symbol.isBlank()) return null;

        // Refresh in-memory cache from DB if stale (max once per hour)
        if (System.currentTimeMillis() - symbolCacheLoadedAt > 3_600_000L) {
            loadSymbolCacheFromDb();
        }

        String upper = symbol.toUpperCase();
        String id = symbolToIdCache.get(upper);
        if (id != null) return id;

        for (String suffix : List.of("USDT", "USD", "BTC", "ETH", "BNB", "BUSD")) {
            if (upper.endsWith(suffix) && upper.length() > suffix.length()) {
                String stripped = upper.substring(0, upper.length() - suffix.length());
                id = symbolToIdCache.get(stripped);
                if (id != null) return id;
            }
        }
        return null;
    }

    // ── /search ────────────────────────────────────────────────────────────────

    /**
     * Searches for coins, exchanges, and NFTs matching the given query.
     * Results are cached for {@link #SEARCH_CACHE_TTL_MS} ms.
     *
     * @param query search string
     * @return typed search result, or empty on error
     */
    public Optional<CoinGeckoSearchResult> search(String query) {
        if (query == null || query.isBlank()) return Optional.empty();
        String cacheKey = query.trim().toLowerCase();
        CacheEntry<CoinGeckoSearchResult> cached = searchCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        return coinGeckoService.search(query).map(json -> {
            CoinGeckoSearchResult result = coinGeckoService.getGson()
                    .fromJson(json, CoinGeckoSearchResult.class);
            searchCache.put(cacheKey, new CacheEntry<>(result, SEARCH_CACHE_TTL_MS));
            return result;
        });
    }

    /**
     * Returns coin ID suggestions matching the given prefix.
     * Uses the in-memory symbol→ID map for fast autocomplete.
     *
     * @param prefix symbol prefix (e.g. "BT")
     * @param limit  max results
     * @return list of matching coin IDs
     */
    public List<String> autocompleteCoinIds(String prefix, int limit) {
        if (prefix == null || prefix.isBlank()) return Collections.emptyList();
        if (System.currentTimeMillis() - symbolCacheLoadedAt > 3_600_000L) {
            loadSymbolCacheFromDb();
        }
        String upper = prefix.toUpperCase();
        return symbolToIdCache.entrySet().stream()
                .filter(e -> e.getKey().startsWith(upper))
                .limit(limit)
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    // ── /search/trending ───────────────────────────────────────────────────────

    /**
     * Returns trending coins and NFTs.
     * Cached for {@link #TRENDING_CACHE_TTL_MS} ms.
     *
     * @return typed trending result, or empty on error
     */
    public Optional<CoinGeckoTrendingResult> getTrending() {
        return getTrendingFull();
    }

    /** Fetches trending directly from /search/trending endpoint. */
    public Optional<CoinGeckoTrendingResult> getTrendingFull() {
        if (trendingCache != null && !trendingCache.isExpired())
            return Optional.ofNullable(trendingCache.value);

        return coinGeckoService.getSearchTrendingFull().map(json -> {
            CoinGeckoTrendingResult result = coinGeckoService.getGson()
                    .fromJson(json, CoinGeckoTrendingResult.class);
            trendingCache = new CacheEntry<>(result, TRENDING_CACHE_TTL_MS);
            return result;
        });
    }

    private Optional<com.google.gson.JsonObject> getTrendingRaw() {
        return coinGeckoService.getSearchTrendingFull();
    }

    // ── /coins/list — daily DB sync ────────────────────────────────────────────

    /**
     * Scheduled daily sync of the full CoinGecko coins list to the
     * {@code coingecko_coins_list} DB table.
     *
     * Runs at 04:00 UTC every day (configurable via {@code app.coingecko.coins-list-sync-cron}).
     * Also called once at startup (via {@link #initCoinsListSync()}) to warm the cache.
     */
    @Scheduled(cron = "${app.coingecko.coins-list-sync-cron:0 0 4 * * *}")
    public void syncCoinsListToDb() {
        log.info("CoinGecko /coins/list sync started");
        List<CoinGeckoCoinBasic> coins = coinGeckoService.fetchCoinsList();
        if (coins.isEmpty()) {
            log.warn("CoinGecko /coins/list returned empty — skipping DB write");
            return;
        }
        try {
            ensureCoinsListTable();
            // Truncate + re-insert for daily full refresh
            jdbc.execute("DELETE FROM coingecko_coins_list");

            String insert = isPostgres()
                    ? "INSERT INTO coingecko_coins_list (coin_id, symbol, name, updated_at) VALUES (?,?,?,?) ON CONFLICT (coin_id) DO UPDATE SET symbol=EXCLUDED.symbol, name=EXCLUDED.name, updated_at=EXCLUDED.updated_at"
                    : "INSERT OR REPLACE INTO coingecko_coins_list (coin_id, symbol, name, updated_at) VALUES (?,?,?,?)";

            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            // Batch in groups of 500 to avoid huge single transactions
            final int BATCH = 500;
            for (int i = 0; i < coins.size(); i += BATCH) {
                List<CoinGeckoCoinBasic> batch = coins.subList(i, Math.min(i + BATCH, coins.size()));
                jdbc.batchUpdate(insert, batch, batch.size(), (ps, coin) -> {
                    ps.setString(1, coin.getId());
                    ps.setString(2, coin.getSymbol() != null ? coin.getSymbol().toUpperCase() : "");
                    ps.setString(3, coin.getName() != null ? coin.getName() : "");
                    ps.setTimestamp(4, now);
                });
            }
            log.info("CoinGecko /coins/list synced {} entries to DB", coins.size());

            // Refresh in-memory lookup
            buildSymbolCache(coins);

        } catch (Exception e) {
            log.error("CoinGecko /coins/list DB sync failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Loads the symbol→coinId map from the DB into memory.
     * Called lazily when a resolution is needed and the cache is stale.
     */
    private synchronized void loadSymbolCacheFromDb() {
        try {
            ensureCoinsListTable();
            List<CoinGeckoCoinBasic> entries = jdbc.query(
                    "SELECT coin_id, symbol, name FROM coingecko_coins_list",
                    (rs, i) -> {
                        CoinGeckoCoinBasic b = new CoinGeckoCoinBasic();
                        b.setId(rs.getString("coin_id"));
                        b.setSymbol(rs.getString("symbol"));
                        b.setName(rs.getString("name"));
                        return b;
                    });
            buildSymbolCache(entries);
            log.debug("Loaded {} coin-ID mappings from DB", entries.size());
        } catch (Exception e) {
            log.warn("Could not load CoinGecko coins list from DB: {}", e.getMessage());
        }
    }

    private void buildSymbolCache(List<CoinGeckoCoinBasic> coins) {
        Map<String, String> map = new HashMap<>(coins.size() * 2);
        for (CoinGeckoCoinBasic c : coins) {
            if (c.getSymbol() != null && c.getId() != null) {
                // symbol → id (upper-case key; if collision, keep existing — first wins = higher rank)
                map.putIfAbsent(c.getSymbol().toUpperCase(), c.getId());
            }
        }
        symbolToIdCache    = Collections.unmodifiableMap(map);
        symbolCacheLoadedAt = System.currentTimeMillis();
    }

    // ── On-startup warm-up ─────────────────────────────────────────────────────

    /**
     * On application startup, loads the coins list from DB (if available) to
     * warm the in-memory symbol cache without calling the API.
     * If the DB table is empty or doesn't exist, triggers a fresh sync.
     */
    @org.springframework.context.event.EventListener(
            org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void initCoinsListSync() {
        Thread.ofVirtual().name("cg-coins-list-init").start(() -> {
            try {
                Thread.sleep(5_000L); // wait for app to fully start
            } catch (InterruptedException ignored) {}
            try {
                ensureCoinsListTable();
                Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM coingecko_coins_list",
                        Integer.class);
                if (count == null || count == 0) {
                    log.info("coingecko_coins_list empty — running initial sync");
                    syncCoinsListToDb();
                } else {
                    log.info("coingecko_coins_list has {} entries — loading into memory", count);
                    loadSymbolCacheFromDb();
                }
            } catch (Exception e) {
                log.warn("CoinGecko coins list init: {}", e.getMessage());
            }
        });
    }

    // ── DB DDL ─────────────────────────────────────────────────────────────────

    private void ensureCoinsListTable() {
        if (!ensuredTables.add("coingecko_coins_list")) return;
        String ddl = isPostgres()
                ? """
                  CREATE TABLE IF NOT EXISTS coingecko_coins_list (
                    coin_id    TEXT PRIMARY KEY,
                    symbol     TEXT NOT NULL,
                    name       TEXT NOT NULL,
                    updated_at TIMESTAMP
                  )
                  """
                : """
                  CREATE TABLE IF NOT EXISTS coingecko_coins_list (
                    coin_id    TEXT PRIMARY KEY,
                    symbol     TEXT NOT NULL,
                    name       TEXT NOT NULL,
                    updated_at TEXT
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

    // ── Convenience: symbol→ID lookup exposed for other services ──────────────

    /**
     * Returns the complete symbol→coinId lookup map (read-only view).
     * Built from the daily DB sync + static CoinGeckoService map.
     */
    public Map<String, String> getSymbolToIdMap() {
        if (System.currentTimeMillis() - symbolCacheLoadedAt > 3_600_000L) {
            loadSymbolCacheFromDb();
        }
        return symbolToIdCache;
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
