package com.mst.matt.tradingplatformapp.service.price.api.twelvedata;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TwelveData Reference / Symbol Catalog service — free-tier integration.
 *
 * <h3>Free-tier reference endpoints covered:</h3>
 * <ul>
 *   <li>{@code GET /symbol_search}     — search by name or ticker fragment</li>
 *   <li>{@code GET /stocks}            — full stock symbol catalog</li>
 *   <li>{@code GET /etfs}              — ETF catalog</li>
 *   <li>{@code GET /indices}           — indices catalog</li>
 *   <li>{@code GET /cryptocurrencies}  — crypto pairs catalog</li>
 *   <li>{@code GET /forex_pairs}       — forex pairs catalog</li>
 *   <li>{@code GET /exchanges}         — exchange details and trading hours</li>
 *   <li>{@code GET /crypto_exchanges}  — crypto exchange details</li>
 *   <li>{@code GET /instrument_type}   — list of all instrument types</li>
 * </ul>
 *
 * <h3>In-memory search cache:</h3>
 * Symbol search results are cached for 5 minutes to conserve free-tier credits.
 *
 * <h3>Daily catalog sync:</h3>
 * The {@link #syncStocksCatalogToDb()} method syncs a batch of stock symbols to the
 * {@code twelvedata_symbols} table daily (configurable cron).
 */
@Service
public class TwelveDataReferenceService {

    private static final Logger log = LoggerFactory.getLogger(TwelveDataReferenceService.class);

    private static final long SEARCH_CACHE_TTL_MS   = 5  * 60_000L;  // 5 min
    private static final long CATALOG_CACHE_TTL_MS  = 30 * 60_000L;  // 30 min

    private final TwelveDataPriceService  priceService;
    private final JdbcTemplate             jdbc;

    /** Key: query → typed search result */
    private final Map<String, CacheEntry<TwelveDataSymbolSearch>> searchCache =
            new ConcurrentHashMap<>();

    /** Simple per-type catalog caches (avoids repeated full-list fetches) */
    private volatile CacheEntry<TwelveDataInstrumentList> stocksCache;
    private volatile CacheEntry<TwelveDataInstrumentList> etfsCache;
    private volatile CacheEntry<TwelveDataInstrumentList> indicesCache;
    private volatile CacheEntry<TwelveDataInstrumentList> cryptoCache;
    private volatile CacheEntry<TwelveDataInstrumentList> forexCache;
    private volatile CacheEntry<TwelveDataExchangeList>   exchangesCache;
    private volatile CacheEntry<TwelveDataExchangeList>   cryptoExchangesCache;

    private final Set<String> ensuredTables = ConcurrentHashMap.newKeySet();
    private final Gson gson;

    @Autowired
    public TwelveDataReferenceService(TwelveDataPriceService priceService, JdbcTemplate jdbc) {
        this.priceService = priceService;
        this.jdbc         = jdbc;
        this.gson         = priceService.getGson();
    }

    // ─── /symbol_search ────────────────────────────────────────────────────────

    /**
     * {@code GET /symbol_search} — searches the full instrument catalog by name or ticker.
     * Results are cached for {@link #SEARCH_CACHE_TTL_MS} ms.
     *
     * @param query full or partial ticker / name (e.g. "apple", "AAPL", "BTC")
     * @return typed search result, or empty on error
     */
    public Optional<TwelveDataSymbolSearch> symbolSearch(String query) {
        if (query == null || query.isBlank()) return Optional.empty();
        String key = query.trim().toLowerCase();
        CacheEntry<TwelveDataSymbolSearch> cached = searchCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = priceService.buildUrl("/symbol_search", "symbol=" + urlEnc(query));
        priceService.getRateLimiter().acquire();
        try (Response r = priceService.getHttpClient().newCall(
                new okhttp3.Request.Builder().url(url)
                        .addHeader("Accept", "application/json")
                        .addHeader("User-Agent", "TradingPlatform/1.0")
                        .build()).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            TwelveDataSymbolSearch result = gson.fromJson(r.body().string(), TwelveDataSymbolSearch.class);
            if (result == null) return Optional.empty();
            searchCache.put(key, new CacheEntry<>(result, SEARCH_CACHE_TTL_MS));
            return Optional.of(result);
        } catch (IOException e) {
            log.warn("TwelveData /symbol_search error for '{}': {}", query, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Convenience: returns the top {@code limit} matching instruments for a query.
     *
     * @param query  search string
     * @param limit  max results (uses {@code &outputsize=})
     * @return list of matching instruments, or empty list on error
     */
    public List<TwelveDataSymbolSearch.Instrument> searchInstruments(String query, int limit) {
        if (query == null || query.isBlank()) return Collections.emptyList();
        String url = priceService.buildUrl("/symbol_search",
                "symbol=" + urlEnc(query) + "&outputsize=" + Math.min(limit, 120));
        priceService.getRateLimiter().acquire();
        try (Response r = priceService.getHttpClient().newCall(
                new okhttp3.Request.Builder().url(url)
                        .addHeader("Accept", "application/json")
                        .addHeader("User-Agent", "TradingPlatform/1.0")
                        .build()).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Collections.emptyList();
            TwelveDataSymbolSearch result = gson.fromJson(r.body().string(), TwelveDataSymbolSearch.class);
            if (result == null || result.data() == null) return Collections.emptyList();
            return result.data();
        } catch (IOException e) {
            log.warn("TwelveData /symbol_search instruments error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ─── Catalog endpoints ─────────────────────────────────────────────────────

    /**
     * {@code GET /stocks} — returns daily-updated master list of stocks.
     * Optionally filter by exchange or country.
     *
     * @param exchange exchange code, or null for all
     * @param country  country name, or null for all
     * @return typed instrument list, or empty on error
     */
    public Optional<TwelveDataInstrumentList> getStocks(String exchange, String country) {
        if (stocksCache != null && !stocksCache.isExpired()) return Optional.of(stocksCache.value);
        return fetchInstrumentList("/stocks", exchange, country, null).map(list -> {
            stocksCache = new CacheEntry<>(list, CATALOG_CACHE_TTL_MS);
            return list;
        });
    }

    /**
     * {@code GET /etfs} — returns ETF catalog.
     */
    public Optional<TwelveDataInstrumentList> getEtfs(String exchange, String country) {
        if (etfsCache != null && !etfsCache.isExpired()) return Optional.of(etfsCache.value);
        return fetchInstrumentList("/etfs", exchange, country, null).map(list -> {
            etfsCache = new CacheEntry<>(list, CATALOG_CACHE_TTL_MS);
            return list;
        });
    }

    /**
     * {@code GET /indices} — returns indices catalog.
     */
    public Optional<TwelveDataInstrumentList> getIndices(String exchange, String country) {
        if (indicesCache != null && !indicesCache.isExpired()) return Optional.of(indicesCache.value);
        return fetchInstrumentList("/indices", exchange, country, null).map(list -> {
            indicesCache = new CacheEntry<>(list, CATALOG_CACHE_TTL_MS);
            return list;
        });
    }

    /**
     * {@code GET /cryptocurrencies} — returns crypto pairs catalog.
     *
     * @param exchange crypto exchange name, or null for all
     * @param symbol   specific ticker filter, or null for all
     */
    public Optional<TwelveDataInstrumentList> getCryptocurrencies(String exchange, String symbol) {
        if (cryptoCache != null && !cryptoCache.isExpired()) return Optional.of(cryptoCache.value);
        return fetchInstrumentList("/cryptocurrencies", exchange, null, symbol).map(list -> {
            cryptoCache = new CacheEntry<>(list, CATALOG_CACHE_TTL_MS);
            return list;
        });
    }

    /**
     * {@code GET /forex_pairs} — returns forex pairs catalog.
     */
    public Optional<TwelveDataInstrumentList> getForexPairs(String symbol) {
        if (forexCache != null && !forexCache.isExpired()) return Optional.of(forexCache.value);
        return fetchInstrumentList("/forex_pairs", null, null, symbol).map(list -> {
            forexCache = new CacheEntry<>(list, CATALOG_CACHE_TTL_MS);
            return list;
        });
    }

    /**
     * {@code GET /exchanges} — returns exchange details and trading hours.
     */
    public Optional<TwelveDataExchangeList> getExchanges(String type, String country) {
        if (exchangesCache != null && !exchangesCache.isExpired()) return Optional.of(exchangesCache.value);
        return fetchExchangeList("/exchanges", type, country).map(list -> {
            exchangesCache = new CacheEntry<>(list, CATALOG_CACHE_TTL_MS);
            return list;
        });
    }

    /**
     * {@code GET /crypto_exchanges} — returns crypto exchange details.
     */
    public Optional<TwelveDataExchangeList> getCryptoExchanges() {
        if (cryptoExchangesCache != null && !cryptoExchangesCache.isExpired())
            return Optional.of(cryptoExchangesCache.value);
        return fetchExchangeList("/crypto_exchanges", null, null).map(list -> {
            cryptoExchangesCache = new CacheEntry<>(list, CATALOG_CACHE_TTL_MS);
            return list;
        });
    }

    /**
     * {@code GET /instrument_type} — returns all available instrument type names.
     *
     * @return raw JSON object, or empty on error
     */
    public Optional<JsonObject> getInstrumentTypes() {
        String url = priceService.buildUrl("/instrument_type", "");
        priceService.getRateLimiter().acquire();
        try (Response r = priceService.getHttpClient().newCall(
                new okhttp3.Request.Builder().url(url)
                        .addHeader("Accept", "application/json")
                        .addHeader("User-Agent", "TradingPlatform/1.0")
                        .build()).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject root = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(root);
        } catch (IOException e) {
            log.warn("TwelveData /instrument_type error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ─── Daily DB sync ─────────────────────────────────────────────────────────

    /**
     * Scheduled daily sync of TwelveData stock symbols to the {@code twelvedata_symbols} table.
     * Runs at 05:00 UTC to avoid overlap with CoinGecko sync at 04:00.
     */
    @Scheduled(cron = "${app.twelvedata.symbols-sync-cron:0 0 5 * * *}")
    public void syncStocksCatalogToDb() {
        if (!priceService.isEnabled()) {
            log.debug("TwelveData key not configured — skipping symbol sync");
            return;
        }
        log.info("TwelveData /stocks catalog sync started");
        Optional<TwelveDataInstrumentList> listOpt = fetchInstrumentList("/stocks", null, null, null);
        if (listOpt.isEmpty() || listOpt.get().data() == null) {
            log.warn("TwelveData /stocks returned empty — skipping DB write");
            return;
        }
        List<TwelveDataInstrument> instruments = listOpt.get().data();
        try {
            ensureSymbolsTable();
            String upsert = isPostgres()
                    ? """
                      INSERT INTO twelvedata_symbols
                        (symbol, name, exchange, country, type, currency, updated_at)
                      VALUES (?,?,?,?,?,?,?)
                      ON CONFLICT (symbol, exchange) DO UPDATE SET
                        name=EXCLUDED.name, country=EXCLUDED.country,
                        type=EXCLUDED.type, currency=EXCLUDED.currency,
                        updated_at=EXCLUDED.updated_at
                      """
                    : """
                      INSERT OR REPLACE INTO twelvedata_symbols
                        (symbol, name, exchange, country, type, currency, updated_at)
                      VALUES (?,?,?,?,?,?,?)
                      """;
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            final int BATCH = 500;
            for (int i = 0; i < instruments.size(); i += BATCH) {
                List<TwelveDataInstrument> batch =
                        instruments.subList(i, Math.min(i + BATCH, instruments.size()));
                jdbc.batchUpdate(upsert, batch, batch.size(), (ps, inst) -> {
                    ps.setString(1, inst.symbol() != null ? inst.symbol() : "");
                    ps.setString(2, inst.name() != null ? inst.name() : "");
                    ps.setString(3, inst.exchange() != null ? inst.exchange() : "");
                    ps.setString(4, inst.country() != null ? inst.country() : "");
                    ps.setString(5, inst.type() != null ? inst.type() : "");
                    ps.setString(6, inst.currency() != null ? inst.currency() : "");
                    ps.setTimestamp(7, now);
                });
            }
            log.info("TwelveData symbols synced {} entries to DB", instruments.size());
        } catch (Exception e) {
            log.error("TwelveData symbols DB sync failed: {}", e.getMessage(), e);
        }
    }

    /**
     * DB-backed symbol lookup: searches the {@code twelvedata_symbols} table.
     *
     * @param prefix symbol prefix for autocomplete (e.g. "AAP")
     * @param limit  max results
     * @return list of matching symbols
     */
    public List<String> autocompleteSymbols(String prefix, int limit) {
        if (prefix == null || prefix.isBlank()) return Collections.emptyList();
        try {
            ensureSymbolsTable();
            return jdbc.queryForList(
                    "SELECT symbol FROM twelvedata_symbols WHERE symbol LIKE ? ORDER BY symbol LIMIT ?",
                    String.class,
                    prefix.toUpperCase() + "%", limit);
        } catch (Exception e) {
            log.warn("TwelveData autocomplete error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ─── Private helpers ───────────────────────────────────────────────────────

    private Optional<TwelveDataInstrumentList> fetchInstrumentList(
            String endpoint, String exchange, String country, String symbol) {
        StringBuilder params = new StringBuilder();
        if (exchange != null && !exchange.isBlank()) {
            if (!params.isEmpty()) params.append("&");
            params.append("exchange=").append(urlEnc(exchange));
        }
        if (country != null && !country.isBlank()) {
            if (!params.isEmpty()) params.append("&");
            params.append("country=").append(urlEnc(country));
        }
        if (symbol != null && !symbol.isBlank()) {
            if (!params.isEmpty()) params.append("&");
            params.append("symbol=").append(urlEnc(symbol));
        }
        String url = priceService.buildUrl(endpoint, params.toString());
        priceService.getRateLimiter().acquire();
        try (Response r = priceService.getHttpClient().newCall(
                new okhttp3.Request.Builder().url(url)
                        .addHeader("Accept", "application/json")
                        .addHeader("User-Agent", "TradingPlatform/1.0")
                        .build()).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            String body = r.body().string();
            // Some endpoints return a bare array; wrap into {"data":[...]}
            JsonElement parsed = gson.fromJson(body, JsonElement.class);
            TwelveDataInstrumentList list;
            if (parsed != null && parsed.isJsonArray()) {
                JsonObject wrapper = new JsonObject();
                wrapper.add("data", parsed.getAsJsonArray());
                wrapper.addProperty("status", "ok");
                list = gson.fromJson(wrapper, TwelveDataInstrumentList.class);
            } else {
                list = gson.fromJson(body, TwelveDataInstrumentList.class);
            }
            if (list == null) return Optional.empty();
            return Optional.of(list);
        } catch (IOException e) {
            log.warn("TwelveData {} error: {}", endpoint, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<TwelveDataExchangeList> fetchExchangeList(
            String endpoint, String type, String country) {
        StringBuilder params = new StringBuilder();
        if (type != null && !type.isBlank()) params.append("type=").append(urlEnc(type));
        if (country != null && !country.isBlank()) {
            if (!params.isEmpty()) params.append("&");
            params.append("country=").append(urlEnc(country));
        }
        String url = priceService.buildUrl(endpoint, params.toString());
        priceService.getRateLimiter().acquire();
        try (Response r = priceService.getHttpClient().newCall(
                new okhttp3.Request.Builder().url(url)
                        .addHeader("Accept", "application/json")
                        .addHeader("User-Agent", "TradingPlatform/1.0")
                        .build()).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            String body = r.body().string();
            JsonElement parsed = gson.fromJson(body, JsonElement.class);
            TwelveDataExchangeList list;
            if (parsed != null && parsed.isJsonArray()) {
                JsonObject wrapper = new JsonObject();
                wrapper.add("data", parsed.getAsJsonArray());
                wrapper.addProperty("status", "ok");
                list = gson.fromJson(wrapper, TwelveDataExchangeList.class);
            } else {
                list = gson.fromJson(body, TwelveDataExchangeList.class);
            }
            if (list == null) return Optional.empty();
            return Optional.of(list);
        } catch (IOException e) {
            log.warn("TwelveData {} error: {}", endpoint, e.getMessage());
            return Optional.empty();
        }
    }

    private void ensureSymbolsTable() {
        if (!ensuredTables.add("twelvedata_symbols")) return;
        String ddl = isPostgres()
                ? """
                  CREATE TABLE IF NOT EXISTS twelvedata_symbols (
                    symbol     TEXT NOT NULL,
                    name       TEXT,
                    exchange   TEXT NOT NULL DEFAULT '',
                    country    TEXT,
                    type       TEXT,
                    currency   TEXT,
                    updated_at TIMESTAMP,
                    PRIMARY KEY (symbol, exchange)
                  )
                  """
                : """
                  CREATE TABLE IF NOT EXISTS twelvedata_symbols (
                    symbol     TEXT NOT NULL,
                    name       TEXT,
                    exchange   TEXT NOT NULL DEFAULT '',
                    country    TEXT,
                    type       TEXT,
                    currency   TEXT,
                    updated_at TEXT,
                    PRIMARY KEY (symbol, exchange)
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
