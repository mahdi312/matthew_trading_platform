package com.mst.matt.tradingplatformapp.service.price.api.alphavantage;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import com.mst.matt.tradingplatformapp.service.price.HttpJsonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Alpha Vantage reference / symbol catalog service — free-tier integration.
 *
 * <h3>Free-tier reference endpoints covered:</h3>
 * <ul>
 *   <li>{@code SYMBOL_SEARCH} — search by name or ticker fragment</li>
 *   <li>{@code MARKET_STATUS} — current market open/closed status for major exchanges</li>
 * </ul>
 *
 * <h3>In-memory caching:</h3>
 * Search results are cached for 5 minutes to conserve free-tier request credits.
 * Market status is cached for 3 minutes.
 *
 * <h3>DB persistence:</h3>
 * Search results are optionally persisted to {@code av_symbol_search_cache}
 * for offline fallback and history.
 */
@Service
public class AlphaVantageReferenceService {

    private static final Logger log = LoggerFactory.getLogger(AlphaVantageReferenceService.class);

    private static final long SEARCH_CACHE_TTL_MS = 5  * 60_000L;   // 5 min
    private static final long STATUS_CACHE_TTL_MS = 3  * 60_000L;   // 3 min

    private static final String THROTTLE_KEY = "alphavantage";

    private final HttpJsonClient http;
    private final MarketApiProperties  keys;
    private final JdbcTemplate         jdbc;
    private final Gson                 gson = new Gson();

    /** Key: query → search result */
    private final Map<String, CacheEntry<AlphaVantageSearchResult>> searchCache =
            new ConcurrentHashMap<>();

    /** Market status — refreshed every 3 min */
    private volatile CacheEntry<JsonObject> marketStatusCache;

    private final Set<String> ensuredTables = ConcurrentHashMap.newKeySet();

    @Autowired
    public AlphaVantageReferenceService(HttpJsonClient http,
                                        MarketApiProperties keys,
                                        JdbcTemplate jdbc) {
        this.http = http;
        this.keys = keys;
        this.jdbc = jdbc;
    }

    public boolean isEnabled() {
        return keys.hasAlphavantageKey();
    }

    // ─── Symbol Search ─────────────────────────────────────────────────────────

    /**
     * {@code SYMBOL_SEARCH} — searches for the best-matching symbols and market information
     * based on keywords of your choice.
     *
     * <p>Cached for {@link #SEARCH_CACHE_TTL_MS} to conserve free-tier API credits.
     * Results are also persisted to the DB for offline fallback.
     *
     * @param query search query (ticker fragment or company name, e.g. "apple", "IBM")
     * @return search results with up to 10 best matches, or empty on error
     */
    public Optional<AlphaVantageSearchResult> search(String query) {
        if (query == null || query.isBlank()) return Optional.empty();
        String key = query.trim().toLowerCase();

        CacheEntry<AlphaVantageSearchResult> cached = searchCache.get(key);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        // Try DB fallback first if network is unavailable
        Optional<AlphaVantageSearchResult> dbResult = loadSearchFromDb(key);

        String url = buildUrl("SYMBOL_SEARCH", "keywords=" + urlEncode(query.trim()));
        Optional<AlphaVantageSearchResult> result = http.getJson(url, null, THROTTLE_KEY)
                .map(root -> gson.fromJson(root, AlphaVantageSearchResult.class))
                .filter(r -> r.bestMatches() != null && !r.bestMatches().isEmpty());

        if (result.isPresent()) {
            searchCache.put(key, new CacheEntry<>(result.get(), SEARCH_CACHE_TTL_MS));
            persistSearchResults(key, result.get());
            return result;
        }
        return dbResult;
    }

    /**
     * Returns the best single match for a symbol lookup (highest matchScore).
     *
     * @param query ticker or company name
     * @return best match instrument, or empty if not found
     */
    public Optional<AlphaVantageSearchResult.Match> bestMatch(String query) {
        return search(query)
                .flatMap(r -> r.bestMatches() == null || r.bestMatches().isEmpty()
                        ? Optional.empty()
                        : Optional.of(r.bestMatches().get(0)));
    }

    /**
     * Returns all matches filtered by asset type.
     *
     * @param query     ticker or company name
     * @param typeFilter type filter, e.g. "Equity", "ETF", "Mutual Fund" (case-insensitive)
     * @return filtered list of matches
     */
    public List<AlphaVantageSearchResult.Match> searchByType(String query, String typeFilter) {
        return search(query)
                .map(AlphaVantageSearchResult::bestMatches)
                .orElse(List.of())
                .stream()
                .filter(m -> m.type() != null && m.type().equalsIgnoreCase(typeFilter))
                .toList();
    }

    // ─── Market Status ─────────────────────────────────────────────────────────

    /**
     * {@code MARKET_STATUS} — returns the current market open/closed status for major
     * global trading venues (US, Canada, EU, Asia, etc.).
     *
     * <p>Cached for {@link #STATUS_CACHE_TTL_MS}.
     *
     * @return raw JSON with market status information, or empty on error
     */
    public Optional<JsonObject> getMarketStatus() {
        if (marketStatusCache != null && !marketStatusCache.isExpired())
            return Optional.ofNullable(marketStatusCache.value);

        String url = buildUrl("MARKET_STATUS", "");
        Optional<JsonObject> result = http.getJson(url, null, THROTTLE_KEY);
        result.ifPresent(root -> marketStatusCache = new CacheEntry<>(root, STATUS_CACHE_TTL_MS));
        return result;
    }

    /**
     * Returns true if the US stock market (NYSE / NASDAQ) is currently open,
     * based on {@code MARKET_STATUS}.
     *
     * @return true if market is open, false otherwise
     */
    public boolean isUsMarketOpen() {
        return getMarketStatus()
                .filter(root -> root.has("markets"))
                .map(root -> root.getAsJsonArray("markets"))
                .map(arr -> {
                    for (com.google.gson.JsonElement el : arr) {
                        if (!el.isJsonObject()) continue;
                        JsonObject market = el.getAsJsonObject();
                        String region = market.has("region") ? market.get("region").getAsString() : "";
                        String status = market.has("current_status") ? market.get("current_status").getAsString() : "";
                        if ("United States".equalsIgnoreCase(region) && "open".equalsIgnoreCase(status)) {
                            return true;
                        }
                    }
                    return false;
                })
                .orElse(false);
    }

    // ─── DB helpers ────────────────────────────────────────────────────────────

    private Optional<AlphaVantageSearchResult> loadSearchFromDb(String query) {
        try {
            ensureSearchTable();
            String sql = "SELECT result_json FROM av_symbol_search_cache WHERE query = ?";
            List<String> results = jdbc.queryForList(sql, String.class, query);
            if (results.isEmpty()) return Optional.empty();
            AlphaVantageSearchResult r = gson.fromJson(results.get(0), AlphaVantageSearchResult.class);
            return Optional.ofNullable(r);
        } catch (Exception e) {
            log.debug("AlphaVantage search DB load error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private void persistSearchResults(String query, AlphaVantageSearchResult result) {
        try {
            ensureSearchTable();
            String json = gson.toJson(result);
            String upsert = isPostgres()
                    ? """
                      INSERT INTO av_symbol_search_cache (query, result_json, cached_at)
                      VALUES (?, ?, ?)
                      ON CONFLICT (query) DO UPDATE SET
                        result_json = EXCLUDED.result_json,
                        cached_at   = EXCLUDED.cached_at
                      """
                    : """
                      INSERT OR REPLACE INTO av_symbol_search_cache (query, result_json, cached_at)
                      VALUES (?, ?, ?)
                      """;
            jdbc.update(upsert, query, json, Timestamp.valueOf(LocalDateTime.now()));
        } catch (Exception e) {
            log.debug("AlphaVantage search persist error: {}", e.getMessage());
        }
    }

    private void ensureSearchTable() {
        if (!ensuredTables.add("av_symbol_search_cache")) return;
        String ddl = isPostgres()
                ? """
                  CREATE TABLE IF NOT EXISTS av_symbol_search_cache (
                    query       TEXT PRIMARY KEY,
                    result_json TEXT,
                    cached_at   TIMESTAMP
                  )
                  """
                : """
                  CREATE TABLE IF NOT EXISTS av_symbol_search_cache (
                    query       TEXT PRIMARY KEY,
                    result_json TEXT,
                    cached_at   TEXT
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

    // ─── URL builder ──────────────────────────────────────────────────────────

    /**
     * Builds a complete Alpha Vantage API URL.
     *
     * @param function API function name
     * @param params   additional query params (may be empty)
     * @return complete URL string
     */
    public String buildUrl(String function, String params) {
        String key = keys.getAlphavantageKey();
        if (key == null || key.isBlank()) key = "demo";
        StringBuilder sb = new StringBuilder("https://www.alphavantage.co/query")
                .append("?function=").append(function);
        if (params != null && !params.isBlank()) {
            sb.append("&").append(params);
        }
        sb.append("&apikey=").append(key);
        return sb.toString();
    }

    private static String urlEncode(String s) {
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
