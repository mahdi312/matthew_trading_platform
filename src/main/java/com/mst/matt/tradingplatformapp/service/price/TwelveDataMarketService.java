package com.mst.matt.tradingplatformapp.service.price;

import com.google.gson.*;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.service.price.api.twelvedata.*;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TwelveData market-data + analysis service — free-tier integration.
 *
 * <h3>Free-tier endpoints covered:</h3>
 * <ul>
 *   <li>{@code GET /quote}          (batch) — snapshots for multiple symbols</li>
 *   <li>{@code GET /market_movers}         — top gainers / losers</li>
 *   <li>{@code GET /exchange_status}       — open / closed status of exchanges</li>
 *   <li>{@code GET /press_releases}        — official company press releases (Basic+)</li>
 *   <li>{@code GET /ipos}                  — past, today, upcoming IPOs (Analysis)</li>
 *   <li>{@code GET /first_datetime}        — earliest data date for an instrument</li>
 * </ul>
 *
 * <h3>DB persistence:</h3>
 * Quote snapshots are optionally persisted to {@code twelvedata_quote_snapshot}
 * for offline fallback and historical comparison.
 *
 * <h3>In-memory caching:</h3>
 * All responses are cached at reasonable TTLs to conserve the 8 credits/min free-tier limit.
 */
@Service
public class TwelveDataMarketService {

    private static final Logger log = LoggerFactory.getLogger(TwelveDataMarketService.class);

    // Cache TTLs
    private static final long QUOTE_CACHE_TTL_MS     = 2  * 60_000L;  // 2 min
    private static final long MOVERS_CACHE_TTL_MS    = 5  * 60_000L;  // 5 min
    private static final long EXCH_STATUS_TTL_MS     = 3  * 60_000L;  // 3 min
    private static final long IPOS_CACHE_TTL_MS      = 30 * 60_000L;  // 30 min
    private static final long PRESS_CACHE_TTL_MS     = 15 * 60_000L;  // 15 min
    private static final long FIRST_DT_CACHE_TTL_MS  = 60 * 60_000L;  // 1 hour

    private final TwelveDataPriceService priceService;
    private final JdbcTemplate           jdbc;
    private final Gson                   gson;

    // ── In-memory caches ──────────────────────────────────────────────────────

    /** Key: "symbol|interval" → cached quote */
    private final Map<String, CacheEntry<TwelveDataQuote>> quoteCache =
            new ConcurrentHashMap<>();

    /** Key: "exchange|country" → movers raw JSON */
    private volatile CacheEntry<JsonObject> moversCache;

    /** Exchange status (global — refreshed every 3 min) */
    private volatile CacheEntry<TwelveDataExchangeStatus> exchangeStatusCache;

    /** Key: date string → ipos JSON */
    private final Map<String, CacheEntry<JsonObject>> iposCache = new ConcurrentHashMap<>();

    /** Key: "symbol" → press releases JSON */
    private final Map<String, CacheEntry<JsonObject>> pressCache = new ConcurrentHashMap<>();

    /** Key: "symbol|interval" → first datetime string */
    private final Map<String, CacheEntry<String>> firstDatetimeCache = new ConcurrentHashMap<>();

    private final Set<String> ensuredTables = ConcurrentHashMap.newKeySet();

    @Autowired
    public TwelveDataMarketService(TwelveDataPriceService priceService, JdbcTemplate jdbc) {
        this.priceService = priceService;
        this.jdbc         = jdbc;
        this.gson         = priceService.getGson();
    }

    // ─── Quote snapshot ────────────────────────────────────────────────────────

    /**
     * Returns a full quote for the given symbol using {@code GET /quote}.
     * Cached for {@link #QUOTE_CACHE_TTL_MS} ms.
     *
     * @param symbol   instrument ticker
     * @return typed quote, or empty if not available
     */
    public Optional<TwelveDataQuote> getQuote(String symbol) {
        String key = symbol.toUpperCase();
        CacheEntry<TwelveDataQuote> cached = quoteCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        Optional<TwelveDataQuote> result = priceService.fetchQuote(symbol);
        result.ifPresent(q -> {
            quoteCache.put(key, new CacheEntry<>(q, QUOTE_CACHE_TTL_MS));
            persistQuoteSnapshot(q);
        });
        return result;
    }

    /**
     * Returns quotes for a list of symbols (batch).
     * Each symbol consumes 1 credit on the free tier.
     *
     * @param symbols list of tickers (max ~120 in one batch call)
     * @return map of symbol → TwelveDataQuote for successfully resolved symbols
     */
    public Map<String, TwelveDataQuote> getBatchQuotes(List<String> symbols) {
        if (symbols == null || symbols.isEmpty()) return Collections.emptyMap();

        // Separate cached vs. uncached symbols
        Map<String, TwelveDataQuote> result      = new LinkedHashMap<>();
        List<String>                  toFetch    = new ArrayList<>();

        for (String sym : symbols) {
            String key   = sym.toUpperCase();
            CacheEntry<TwelveDataQuote> cached = quoteCache.get(key);
            if (cached != null && !cached.isExpired() && cached.value != null) {
                result.put(key, cached.value);
            } else {
                toFetch.add(TwelveDataPriceService.formatSymbol(sym));
            }
        }

        if (!toFetch.isEmpty()) {
            // Batch: comma-separated symbols in a single /quote request
            String symbolsCsv = String.join(",", toFetch);
            String url = priceService.buildUrl("/quote", "symbol=" + urlEnc(symbolsCsv));
            priceService.getRateLimiter().acquire();
            try (Response r = priceService.getHttpClient().newCall(
                    new okhttp3.Request.Builder().url(url)
                            .addHeader("Accept", "application/json")
                            .addHeader("User-Agent", "TradingPlatform/1.0")
                            .build()).execute()) {
                if (r.isSuccessful() && r.body() != null) {
                    String body = r.body().string();
                    JsonElement parsed = gson.fromJson(body, JsonElement.class);
                    if (parsed != null && parsed.isJsonObject()) {
                        // Multi-symbol: keyed by symbol
                        JsonObject root = parsed.getAsJsonObject();
                        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                            if (!entry.getValue().isJsonObject()) continue;
                            try {
                                TwelveDataQuote q = gson.fromJson(entry.getValue(), TwelveDataQuote.class);
                                if (q != null && q.symbol() != null) {
                                    result.put(q.symbol().toUpperCase(), q);
                                    quoteCache.put(q.symbol().toUpperCase(),
                                            new CacheEntry<>(q, QUOTE_CACHE_TTL_MS));
                                }
                            } catch (Exception ex) {
                                log.debug("Failed to parse batch quote entry: {}", ex.getMessage());
                            }
                        }
                    }
                }
            } catch (IOException e) {
                log.warn("TwelveData batch /quote error: {}", e.getMessage());
            }
        }
        return result;
    }

    // ─── Market Movers ─────────────────────────────────────────────────────────

    /**
     * {@code GET /market_movers} — top gaining and losing stocks.
     * Cached for {@link #MOVERS_CACHE_TTL_MS} ms.
     *
     * @param exchange exchange filter (null = all)
     * @param country  country filter (null = all)
     * @return typed market movers, or empty on error
     */
    public Optional<TwelveDataMarketMover> getMarketMovers(String exchange, String country) {
        String cacheKey = (exchange != null ? exchange : "") + "|" + (country != null ? country : "");
        if (moversCache != null && !moversCache.isExpired()) {
            return Optional.of(moversCache.value)
                    .map(root -> gson.fromJson(root, TwelveDataMarketMover.class));
        }
        Optional<TwelveDataMarketMover> result = priceService.fetchMarketMoversTyped(exchange, country);
        result.ifPresent(m -> {
            JsonObject root = gson.fromJson(gson.toJson(m), JsonObject.class);
            moversCache = new CacheEntry<>(root, MOVERS_CACHE_TTL_MS);
        });
        return result;
    }

    // ─── Exchange Status ────────────────────────────────────────────────────────

    /**
     * {@code GET /exchange_status} — which exchanges are currently open or closed.
     * Cached for {@link #EXCH_STATUS_TTL_MS} ms.
     *
     * @return typed exchange status, or empty on error
     */
    public Optional<TwelveDataExchangeStatus> getExchangeStatus() {
        if (exchangeStatusCache != null && !exchangeStatusCache.isExpired())
            return Optional.ofNullable(exchangeStatusCache.value);

        Optional<TwelveDataExchangeStatus> result = priceService.fetchExchangeStatusTyped();
        result.ifPresent(s -> exchangeStatusCache = new CacheEntry<>(s, EXCH_STATUS_TTL_MS));
        return result;
    }

    /**
     * Returns true if any major exchange (NYSE, NASDAQ) is currently open.
     */
    public boolean isMajorMarketOpen() {
        return getExchangeStatus()
                .map(TwelveDataExchangeStatus::data)
                .orElse(Collections.emptyList())
                .stream()
                .filter(e -> e.code() != null &&
                        (e.code().equalsIgnoreCase("NYSE") || e.code().equalsIgnoreCase("NASDAQ")))
                .anyMatch(e -> Boolean.TRUE.equals(e.isMarketOpen()));
    }

    // ─── IPOs ──────────────────────────────────────────────────────────────────

    /**
     * {@code GET /ipos} — returns IPO calendar (past, today, or upcoming).
     * Cached for {@link #IPOS_CACHE_TTL_MS} ms.
     *
     * @param date specific date in "YYYY-MM-DD" format, or null for all upcoming
     * @return raw JSON object with IPO data, or empty on error
     */
    public Optional<JsonObject> getIpos(String date) {
        String key = date != null ? date : "all";
        CacheEntry<JsonObject> cached = iposCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        StringBuilder params = new StringBuilder();
        if (date != null && !date.isBlank()) params.append("date=").append(date);
        String url = priceService.buildUrl("/ipos", params.toString());
        priceService.getRateLimiter().acquire();
        try (Response r = priceService.getHttpClient().newCall(
                new okhttp3.Request.Builder().url(url)
                        .addHeader("Accept", "application/json")
                        .addHeader("User-Agent", "TradingPlatform/1.0")
                        .build()).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject root = gson.fromJson(r.body().string(), JsonObject.class);
            if (root != null) iposCache.put(key, new CacheEntry<>(root, IPOS_CACHE_TTL_MS));
            return Optional.ofNullable(root);
        } catch (IOException e) {
            log.warn("TwelveData /ipos error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // ─── Press Releases ─────────────────────────────────────────────────────────

    /**
     * {@code GET /press_releases} — official company press releases (Basic/free plan).
     * Cached for {@link #PRESS_CACHE_TTL_MS} ms.
     *
     * @param symbol instrument ticker
     * @return raw JSON object with press release data, or empty on error
     */
    public Optional<JsonObject> getPressReleases(String symbol) {
        String key = symbol.toUpperCase();
        CacheEntry<JsonObject> cached = pressCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = priceService.buildUrl("/press_releases",
                "symbol=" + urlEnc(TwelveDataPriceService.formatSymbol(symbol)));
        priceService.getRateLimiter().acquire();
        try (Response r = priceService.getHttpClient().newCall(
                new okhttp3.Request.Builder().url(url)
                        .addHeader("Accept", "application/json")
                        .addHeader("User-Agent", "TradingPlatform/1.0")
                        .build()).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject root = gson.fromJson(r.body().string(), JsonObject.class);
            if (root != null) pressCache.put(key, new CacheEntry<>(root, PRESS_CACHE_TTL_MS));
            return Optional.ofNullable(root);
        } catch (IOException e) {
            log.warn("TwelveData /press_releases error for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    // ─── First Available DateTime ────────────────────────────────────────────────

    /**
     * {@code GET /first_datetime} — the earliest available data date for an instrument
     * at a given interval.
     * Cached for {@link #FIRST_DT_CACHE_TTL_MS} ms.
     *
     * @param symbol   instrument ticker
     * @param interval timeframe interval (e.g. "1day", "1h")
     * @return datetime string (ISO-8601), or empty on error
     */
    public Optional<String> getFirstDatetime(String symbol, String interval) {
        String key = symbol.toUpperCase() + "|" + interval;
        CacheEntry<String> cached = firstDatetimeCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        String url = priceService.buildUrl("/first_datetime",
                "symbol=" + urlEnc(TwelveDataPriceService.formatSymbol(symbol)) +
                "&interval=" + interval);
        priceService.getRateLimiter().acquire();
        try (Response r = priceService.getHttpClient().newCall(
                new okhttp3.Request.Builder().url(url)
                        .addHeader("Accept", "application/json")
                        .addHeader("User-Agent", "TradingPlatform/1.0")
                        .build()).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject root = gson.fromJson(r.body().string(), JsonObject.class);
            if (root == null) return Optional.empty();
            String dt = root.has("datetime") ? root.get("datetime").getAsString() : null;
            if (dt != null) firstDatetimeCache.put(key, new CacheEntry<>(dt, FIRST_DT_CACHE_TTL_MS));
            return Optional.ofNullable(dt);
        } catch (IOException e) {
            log.warn("TwelveData /first_datetime error for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    // ─── OHLCV with date-range ──────────────────────────────────────────────────

    /**
     * Fetches OHLCV bars for a date range using {@code GET /time_series} with
     * {@code start_date} and {@code end_date}.
     *
     * @param symbol    instrument ticker
     * @param timeframe e.g. "1day", "1h"
     * @param startDate start date in "YYYY-MM-DD" format
     * @param endDate   end date in "YYYY-MM-DD" format
     * @param limit     max bars (1–5000)
     * @return OHLCV bars in chronological order
     */
    public List<OhlcvBar> getOhlcvRange(String symbol, String timeframe,
                                         String startDate, String endDate, int limit) {
        String sym      = TwelveDataPriceService.formatSymbol(symbol);
        String interval = TwelveDataPriceService.mapInterval(timeframe);
        StringBuilder params = new StringBuilder();
        params.append("symbol=").append(urlEnc(sym));
        params.append("&interval=").append(interval);
        params.append("&outputsize=").append(Math.min(limit, 5000));
        if (startDate != null && !startDate.isBlank())
            params.append("&start_date=").append(startDate);
        if (endDate != null && !endDate.isBlank())
            params.append("&end_date=").append(endDate);
        String url = priceService.buildUrl("/time_series", params.toString());
        priceService.getRateLimiter().acquire();
        try (Response r = priceService.getHttpClient().newCall(
                new okhttp3.Request.Builder().url(url)
                        .addHeader("Accept", "application/json")
                        .addHeader("User-Agent", "TradingPlatform/1.0")
                        .build()).execute()) {
            if (!r.isSuccessful() || r.body() == null) return Collections.emptyList();
            String body = r.body().string();
            com.mst.matt.tradingplatformapp.service.price.api.twelvedata.TwelveDataTimeSeries ts =
                    gson.fromJson(body, com.mst.matt.tradingplatformapp.service.price.api.twelvedata.TwelveDataTimeSeries.class);
            if (ts == null || !ts.isOk() || ts.values() == null) return Collections.emptyList();
            return parseBars(ts, symbol, timeframe, limit);
        } catch (IOException e) {
            log.warn("TwelveData /time_series range error for {}: {}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ─── DB persistence ─────────────────────────────────────────────────────────

    private void persistQuoteSnapshot(TwelveDataQuote q) {
        try {
            ensureQuoteSnapshotTable();
            String upsert = isPostgres()
                    ? """
                      INSERT INTO twelvedata_quote_snapshot
                        (symbol, exchange, name, close, change_val, percent_change,
                         volume, is_market_open, snapshot_time)
                      VALUES (?,?,?,?,?,?,?,?,?)
                      ON CONFLICT (symbol) DO UPDATE SET
                        exchange       = EXCLUDED.exchange,
                        name           = EXCLUDED.name,
                        close          = EXCLUDED.close,
                        change_val     = EXCLUDED.change_val,
                        percent_change = EXCLUDED.percent_change,
                        volume         = EXCLUDED.volume,
                        is_market_open = EXCLUDED.is_market_open,
                        snapshot_time  = EXCLUDED.snapshot_time
                      """
                    : """
                      INSERT OR REPLACE INTO twelvedata_quote_snapshot
                        (symbol, exchange, name, close, change_val, percent_change,
                         volume, is_market_open, snapshot_time)
                      VALUES (?,?,?,?,?,?,?,?,?)
                      """;
            jdbc.update(upsert,
                    q.symbol(), q.exchange(), q.name(),
                    parseBD(q.close()), parseBD(q.change()), parseBD(q.percentChange()),
                    parseBD(q.volume()),
                    q.isMarketOpen() != null ? q.isMarketOpen() : false,
                    Timestamp.valueOf(LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("TwelveData quote snapshot persist error: {}", e.getMessage());
        }
    }

    private void ensureQuoteSnapshotTable() {
        if (!ensuredTables.add("twelvedata_quote_snapshot")) return;
        String ddl = isPostgres()
                ? """
                  CREATE TABLE IF NOT EXISTS twelvedata_quote_snapshot (
                    symbol         TEXT PRIMARY KEY,
                    exchange       TEXT,
                    name           TEXT,
                    close          NUMERIC,
                    change_val     NUMERIC,
                    percent_change NUMERIC,
                    volume         NUMERIC,
                    is_market_open BOOLEAN,
                    snapshot_time  TIMESTAMP
                  )
                  """
                : """
                  CREATE TABLE IF NOT EXISTS twelvedata_quote_snapshot (
                    symbol         TEXT PRIMARY KEY,
                    exchange       TEXT,
                    name           TEXT,
                    close          REAL,
                    change_val     REAL,
                    percent_change REAL,
                    volume         REAL,
                    is_market_open INTEGER,
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

    // ─── Parse helpers ────────────────────────────────────────────────────────

    private List<OhlcvBar> parseBars(
            com.mst.matt.tradingplatformapp.service.price.api.twelvedata.TwelveDataTimeSeries ts,
            String symbol, String timeframe, int limit) {
        if (ts.values() == null) return Collections.emptyList();
        String norm = SymbolNormalizer.normalize(symbol);
        List<OhlcvBar> bars = new ArrayList<>(ts.values().size());
        java.time.format.DateTimeFormatter dtFmt =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        java.time.format.DateTimeFormatter dFmt =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (var v : ts.values()) {
            java.time.LocalDateTime openTime;
            try {
                openTime = v.datetime().length() > 10
                        ? java.time.LocalDateTime.parse(v.datetime(), dtFmt)
                        : java.time.LocalDateTime.parse(v.datetime() + " 00:00:00", dtFmt);
            } catch (Exception e) {
                openTime = java.time.LocalDateTime.now();
            }
            bars.add(OhlcvBar.builder()
                    .symbol(norm).timeframe(timeframe)
                    .openTime(openTime)
                    .open(parseBD(v.open())).high(parseBD(v.high()))
                    .low(parseBD(v.low())).close(parseBD(v.close()))
                    .volume(parseBD(v.volume()))
                    .assetType(com.mst.matt.tradingplatformapp.model.Trade.AssetType.STOCK)
                    .build());
        }
        Collections.reverse(bars); // newest-first → chronological
        return bars.size() > limit ? bars.subList(bars.size() - limit, bars.size()) : bars;
    }

    private static BigDecimal parseBD(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(s); } catch (NumberFormatException e) { return BigDecimal.ZERO; }
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
