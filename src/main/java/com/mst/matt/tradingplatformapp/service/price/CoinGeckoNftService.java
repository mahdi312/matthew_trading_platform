package com.mst.matt.tradingplatformapp.service.price;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoNftCollection;
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
 * NFT data service using CoinGecko free endpoints.
 *
 * <h3>Endpoints covered (🆓 Free tier):</h3>
 * <ul>
 *   <li>{@code GET /nfts/list} — list of tracked NFT collections.</li>
 *   <li>{@code GET /nfts/{id}} — detailed collection data (floor price, market cap, volume).</li>
 *   <li>{@code GET /nfts/{platform}/contract/{address}} — collection by contract address.</li>
 * </ul>
 *
 * <h3>DB persistence:</h3>
 * Collection snapshots (floor price, market cap, volume) are persisted to a
 * {@code nft_collection_snapshot} table for offline access.
 *
 * <h3>Caching:</h3>
 * Results are cached in memory for 10–15 minutes to stay within free-tier limits.
 */
@Service
public class CoinGeckoNftService {

    private static final Logger log = LoggerFactory.getLogger(CoinGeckoNftService.class);

    private static final long LIST_CACHE_TTL_MS       = 15 * 60_000L; // 15 min
    private static final long COLLECTION_CACHE_TTL_MS = 10 * 60_000L; // 10 min

    private final CoinGeckoService coinGeckoService;
    private final JdbcTemplate     jdbc;

    /** Cache for the NFT collections list (paginated). Key = "perPage|page|order" */
    private final Map<String, CacheEntry<List<CoinGeckoNftCollection>>> listCache =
            new ConcurrentHashMap<>();

    /** Cache for individual collection details. Key = collection id */
    private final Map<String, CacheEntry<CoinGeckoNftCollection>> collectionCache =
            new ConcurrentHashMap<>();

    private final KeySetView<String, Boolean> ensuredTables = ConcurrentHashMap.newKeySet();

    public CoinGeckoNftService(CoinGeckoService coinGeckoService, JdbcTemplate jdbc) {
        this.coinGeckoService = coinGeckoService;
        this.jdbc = jdbc;
    }

    // ── /nfts/list ────────────────────────────────────────────────────────────

    /**
     * Returns a paginated list of NFT collections.
     * Uses {@code GET /nfts/list}.
     *
     * @param perPage items per page (1–250)
     * @param page    page index (1-based)
     * @param order   sort order (null = default)
     * @return typed list of NFT collection summaries
     */
    public List<CoinGeckoNftCollection> listCollections(int perPage, int page, String order) {
        String cacheKey = perPage + "|" + page + "|" + order;
        CacheEntry<List<CoinGeckoNftCollection>> cached = listCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) return cached.value;

        Optional<JsonArray> raw = coinGeckoService.listNfts(perPage, page, order);
        if (raw.isEmpty()) return Collections.emptyList();

        List<CoinGeckoNftCollection> result = parseCollectionList(raw.get());
        listCache.put(cacheKey, new CacheEntry<>(result, LIST_CACHE_TTL_MS));
        persistCollectionSnapshots(result);
        return result;
    }

    /**
     * Returns the top 50 NFT collections by default ordering.
     */
    public List<CoinGeckoNftCollection> getTopCollections() {
        return listCollections(50, 1, null);
    }

    // ── /nfts/{id} ────────────────────────────────────────────────────────────

    /**
     * Returns detailed data for a single NFT collection.
     * Uses {@code GET /nfts/{id}}.
     *
     * @param collectionId CoinGecko NFT collection ID (e.g. "bored-ape-yacht-club")
     * @return optional typed collection detail
     */
    public Optional<CoinGeckoNftCollection> getCollectionDetail(String collectionId) {
        CacheEntry<CoinGeckoNftCollection> cached = collectionCache.get(collectionId);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        Optional<JsonObject> raw = coinGeckoService.getNftCollection(collectionId, false);
        return raw.map(json -> {
            CoinGeckoNftCollection result = coinGeckoService.getGson()
                    .fromJson(json, CoinGeckoNftCollection.class);
            collectionCache.put(collectionId, new CacheEntry<>(result, COLLECTION_CACHE_TTL_MS));
            persistCollectionSnapshot(result);
            return result;
        });
    }

    // ── /nfts/{platform}/contract/{address} ───────────────────────────────────

    /**
     * Returns NFT collection data by contract address.
     * Uses {@code GET /nfts/{platform}/contract/{address}}.
     *
     * @param platform         asset platform ID (e.g. "ethereum")
     * @param contractAddress  NFT contract address
     * @return optional typed collection detail
     */
    public Optional<CoinGeckoNftCollection> getCollectionByContract(String platform,
                                                                      String contractAddress) {
        // Delegate to the raw endpoint in CoinGeckoService (via getNftCollection for contract form)
        String url = coinGeckoService.getBaseUrl()
                + "/nfts/" + platform + "/contract/" + contractAddress;
        // Use the service's raw GET via direct call
        return coinGeckoService.getNftCollection(platform + "/contract/" + contractAddress, false)
                .map(json -> coinGeckoService.getGson().fromJson(json, CoinGeckoNftCollection.class));
    }

    // ── DB persistence ─────────────────────────────────────────────────────────

    private void persistCollectionSnapshots(List<CoinGeckoNftCollection> collections) {
        for (CoinGeckoNftCollection c : collections) {
            persistCollectionSnapshot(c);
        }
    }

    /**
     * Persists a collection snapshot to the {@code nft_collection_snapshot} table.
     *
     * Schema:
     * <pre>
     *   collection_id    TEXT PRIMARY KEY
     *   name             TEXT
     *   symbol           TEXT
     *   platform         TEXT
     *   floor_price_usd  NUMERIC
     *   market_cap_usd   NUMERIC
     *   volume_24h_usd   NUMERIC
     *   snapshot_time    TIMESTAMP
     * </pre>
     */
    private void persistCollectionSnapshot(CoinGeckoNftCollection c) {
        if (c == null || c.getId() == null) return;
        try {
            ensureNftSnapshotTable();
            BigDecimal floorPriceUsd = c.getFloorPrice() != null
                    ? c.getFloorPrice().getOrDefault("usd", BigDecimal.ZERO) : BigDecimal.ZERO;
            BigDecimal marketCapUsd  = c.getMarketCap()  != null
                    ? c.getMarketCap().getOrDefault("usd", BigDecimal.ZERO) : BigDecimal.ZERO;
            BigDecimal volume24hUsd  = c.getVolume24h()  != null
                    ? c.getVolume24h().getOrDefault("usd", BigDecimal.ZERO) : BigDecimal.ZERO;

            String upsert = isPostgres()
                    ? """
                      INSERT INTO nft_collection_snapshot
                        (collection_id, name, symbol, platform, floor_price_usd,
                         market_cap_usd, volume_24h_usd, snapshot_time)
                      VALUES (?,?,?,?,?,?,?,?)
                      ON CONFLICT (collection_id) DO UPDATE SET
                        name           = EXCLUDED.name,
                        floor_price_usd = EXCLUDED.floor_price_usd,
                        market_cap_usd  = EXCLUDED.market_cap_usd,
                        volume_24h_usd  = EXCLUDED.volume_24h_usd,
                        snapshot_time   = EXCLUDED.snapshot_time
                      """
                    : """
                      INSERT OR REPLACE INTO nft_collection_snapshot
                        (collection_id, name, symbol, platform, floor_price_usd,
                         market_cap_usd, volume_24h_usd, snapshot_time)
                      VALUES (?,?,?,?,?,?,?,?)
                      """;
            jdbc.update(upsert,
                    c.getId(), c.getName(), c.getSymbol(), c.getAssetPlatformId(),
                    floorPriceUsd, marketCapUsd, volume24hUsd,
                    Timestamp.valueOf(LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("Failed to persist NFT snapshot for {}: {}", c.getId(), e.getMessage());
        }
    }

    // ── Parsing helpers ───────────────────────────────────────────────────────

    private List<CoinGeckoNftCollection> parseCollectionList(JsonArray arr) {
        List<CoinGeckoNftCollection> out = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            try {
                CoinGeckoNftCollection c = coinGeckoService.getGson()
                        .fromJson(el, CoinGeckoNftCollection.class);
                if (c != null) out.add(c);
            } catch (Exception e) {
                log.debug("Failed to parse NFT collection entry: {}", e.getMessage());
            }
        }
        return out;
    }

    // ── Table DDL ─────────────────────────────────────────────────────────────

    private void ensureNftSnapshotTable() {
        if (!ensuredTables.add("nft_collection_snapshot")) return;
        String ddl = isPostgres()
                ? """
                  CREATE TABLE IF NOT EXISTS nft_collection_snapshot (
                    collection_id   TEXT PRIMARY KEY,
                    name            TEXT,
                    symbol          TEXT,
                    platform        TEXT,
                    floor_price_usd NUMERIC,
                    market_cap_usd  NUMERIC,
                    volume_24h_usd  NUMERIC,
                    snapshot_time   TIMESTAMP
                  )
                  """
                : """
                  CREATE TABLE IF NOT EXISTS nft_collection_snapshot (
                    collection_id   TEXT PRIMARY KEY,
                    name            TEXT,
                    symbol          TEXT,
                    platform        TEXT,
                    floor_price_usd REAL,
                    market_cap_usd  REAL,
                    volume_24h_usd  REAL,
                    snapshot_time   TEXT
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
