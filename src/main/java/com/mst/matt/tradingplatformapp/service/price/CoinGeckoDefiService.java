package com.mst.matt.tradingplatformapp.service.price;

import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoDefiData;
import com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoExchangeRates;
import com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoGlobalData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;

/**
 * DeFi and global market data service using CoinGecko free endpoints.
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *   <li>{@code GET /global} — total market cap, BTC dominance, active cryptocurrencies.</li>
 *   <li>{@code GET /global/decentralized_finance_defi} — DeFi-specific market stats.</li>
 *   <li>{@code GET /exchange_rates} — fiat/crypto exchange rate matrix.</li>
 * </ul>
 *
 * <h3>Caching:</h3>
 * All results are cached in memory for 3–5 minutes to stay within the free-tier
 * rate limit of 30 calls/min.
 *
 * <h3>DB persistence:</h3>
 * Global and DeFi snapshots are persisted to the {@code global_market_data} table
 * so the UI can show data even when offline.
 */
@Service
public class CoinGeckoDefiService {

    private static final Logger log = LoggerFactory.getLogger(CoinGeckoDefiService.class);

    private static final long GLOBAL_CACHE_TTL_MS       = 3 * 60_000L;  // 3 min
    private static final long DEFI_CACHE_TTL_MS         = 5 * 60_000L;  // 5 min
    private static final long EXCHANGE_RATE_CACHE_TTL_MS = 10 * 60_000L; // 10 min

    private final CoinGeckoService coinGeckoService;
    private final JdbcTemplate     jdbc;

    private volatile CacheEntry<CoinGeckoGlobalData> globalCache;
    private volatile CacheEntry<CoinGeckoDefiData>   defiCache;
    private volatile CacheEntry<CoinGeckoExchangeRates> ratesCache;

    private final KeySetView<String, Boolean> ensuredTables = ConcurrentHashMap.newKeySet();

    public CoinGeckoDefiService(CoinGeckoService coinGeckoService, JdbcTemplate jdbc) {
        this.coinGeckoService = coinGeckoService;
        this.jdbc = jdbc;
    }

    // ── Global market data ─────────────────────────────────────────────────────

    /**
     * Returns typed global market data (total cap, BTC dominance, etc.).
     * Uses {@code GET /global}.
     */
    public Optional<CoinGeckoGlobalData> getGlobalData() {
        if (globalCache != null && !globalCache.isExpired())
            return Optional.ofNullable(globalCache.value);

        Optional<JsonObject> raw = coinGeckoService.getGlobalData();
        return raw.map(json -> {
            JsonObject data = json.has("data") ? json.getAsJsonObject("data") : json;
            CoinGeckoGlobalData result = coinGeckoService.getGson()
                    .fromJson(data, CoinGeckoGlobalData.class);
            globalCache = new CacheEntry<>(result, GLOBAL_CACHE_TTL_MS);
            persistGlobalSnapshot(result);
            return result;
        });
    }

    /**
     * Returns the raw global JSON (full {@code /global} response with "data" wrapper).
     */
    public Optional<JsonObject> getGlobalRaw() {
        return coinGeckoService.getGlobalData();
    }

    // ── DeFi stats ─────────────────────────────────────────────────────────────

    /**
     * Returns typed DeFi market statistics.
     * Uses {@code GET /global/decentralized_finance_defi}.
     */
    public Optional<CoinGeckoDefiData> getDefiData() {
        if (defiCache != null && !defiCache.isExpired())
            return Optional.ofNullable(defiCache.value);

        Optional<JsonObject> raw = coinGeckoService.getGlobalDeFiData();
        return raw.map(json -> {
            JsonObject data = json.has("data") ? json.getAsJsonObject("data") : json;
            CoinGeckoDefiData result = coinGeckoService.getGson()
                    .fromJson(data, CoinGeckoDefiData.class);
            defiCache = new CacheEntry<>(result, DEFI_CACHE_TTL_MS);
            persistDefiSnapshot(result);
            return result;
        });
    }

    /**
     * Returns the raw DeFi JSON (full {@code /global/decentralized_finance_defi} response).
     */
    public Optional<JsonObject> getDefiRaw() {
        return coinGeckoService.getGlobalDeFiData();
    }

    // ── Exchange rates ─────────────────────────────────────────────────────────

    /**
     * Returns typed exchange rates (fiat and crypto relative to BTC).
     * Uses {@code GET /exchange_rates}.
     */
    public Optional<CoinGeckoExchangeRates> getExchangeRates() {
        if (ratesCache != null && !ratesCache.isExpired())
            return Optional.ofNullable(ratesCache.value);

        Optional<JsonObject> raw = coinGeckoService.getExchangeRates();
        return raw.map(json -> {
            CoinGeckoExchangeRates result = coinGeckoService.getGson()
                    .fromJson(json, CoinGeckoExchangeRates.class);
            ratesCache = new CacheEntry<>(result, EXCHANGE_RATE_CACHE_TTL_MS);
            return result;
        });
    }

    /**
     * Returns the raw exchange-rates JSON object.
     */
    public Optional<JsonObject> getExchangeRatesRaw() {
        return coinGeckoService.getExchangeRates();
    }

    // ── DB persistence ─────────────────────────────────────────────────────────

    /**
     * Persists a global market data snapshot into the {@code global_market_data} table.
     *
     * Row schema:
     * <pre>
     *   provider         TEXT PRIMARY KEY  (= 'COINGECKO')
     *   total_market_cap NUMERIC
     *   btc_dominance    NUMERIC
     *   active_cryptos   INTEGER
     *   snapshot_time    TIMESTAMP
     * </pre>
     */
    private void persistGlobalSnapshot(CoinGeckoGlobalData data) {
        try {
            ensureGlobalMarketDataTable();
            BigDecimal totalMarketCap = BigDecimal.ZERO;
            BigDecimal btcDominance   = BigDecimal.ZERO;
            int activeCryptos         = data.getActiveCryptocurrencies() != null
                    ? data.getActiveCryptocurrencies() : 0;

            if (data.getTotalMarketCap() != null && data.getTotalMarketCap().containsKey("usd")) {
                totalMarketCap = data.getTotalMarketCap().get("usd");
            }
            if (data.getMarketCapPercentage() != null && data.getMarketCapPercentage().containsKey("btc")) {
                btcDominance = data.getMarketCapPercentage().get("btc");
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
            log.warn("Failed to persist global market data snapshot: {}", e.getMessage());
        }
    }

    /**
     * Persists a DeFi market data snapshot into the {@code defi_market_data} table.
     *
     * Row schema:
     * <pre>
     *   id                TEXT PRIMARY KEY (= 'COINGECKO')
     *   defi_market_cap   NUMERIC
     *   trading_volume_24h NUMERIC
     *   defi_dominance    NUMERIC
     *   top_coin_name     TEXT
     *   snapshot_time     TIMESTAMP
     * </pre>
     */
    private void persistDefiSnapshot(CoinGeckoDefiData data) {
        try {
            ensureDefiMarketDataTable();
            String upsert = isPostgres()
                    ? """
                      INSERT INTO defi_market_data
                        (id, defi_market_cap, trading_volume_24h, defi_dominance, top_coin_name, snapshot_time)
                      VALUES ('COINGECKO',?,?,?,?,?)
                      ON CONFLICT (id) DO UPDATE SET
                        defi_market_cap    = EXCLUDED.defi_market_cap,
                        trading_volume_24h = EXCLUDED.trading_volume_24h,
                        defi_dominance     = EXCLUDED.defi_dominance,
                        top_coin_name      = EXCLUDED.top_coin_name,
                        snapshot_time      = EXCLUDED.snapshot_time
                      """
                    : """
                      INSERT OR REPLACE INTO defi_market_data
                        (id, defi_market_cap, trading_volume_24h, defi_dominance, top_coin_name, snapshot_time)
                      VALUES ('COINGECKO',?,?,?,?,?)
                      """;
            jdbc.update(upsert,
                    data.getDefiMarketCap(),
                    data.getTradingVolume24h(),
                    data.getDefiDominance(),
                    data.getTopCoinName(),
                    Timestamp.valueOf(LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("Failed to persist DeFi market data snapshot: {}", e.getMessage());
        }
    }

    // ── Table DDL helpers ─────────────────────────────────────────────────────

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

    private void ensureDefiMarketDataTable() {
        if (!ensuredTables.add("defi_market_data")) return;
        String ddl = isPostgres()
                ? """
                  CREATE TABLE IF NOT EXISTS defi_market_data (
                    id                 TEXT PRIMARY KEY,
                    defi_market_cap    NUMERIC,
                    trading_volume_24h NUMERIC,
                    defi_dominance     NUMERIC,
                    top_coin_name      TEXT,
                    snapshot_time      TIMESTAMP
                  )
                  """
                : """
                  CREATE TABLE IF NOT EXISTS defi_market_data (
                    id                 TEXT PRIMARY KEY,
                    defi_market_cap    REAL,
                    trading_volume_24h REAL,
                    defi_dominance     REAL,
                    top_coin_name      TEXT,
                    snapshot_time      TEXT
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
}
