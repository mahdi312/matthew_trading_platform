package com.mst.matt.tradingplatformapp.service.price.api.coingecko;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade;
import com.mst.matt.tradingplatformapp.service.marketdata.DynamicOhlcvTableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Onchain DEX service using CoinGecko's GeckoTerminal (free endpoints).
 *
 * <h3>Endpoints covered:</h3>
 * <ul>
 *   <li>{@code GET /onchain/networks} — list all supported networks.</li>
 *   <li>{@code GET /onchain/networks/{n}/pools} — top pools on a network.</li>
 *   <li>{@code GET /onchain/networks/trending_pools} — globally trending pools.</li>
 *   <li>{@code GET /onchain/networks/{n}/trending_pools} — trending on a network.</li>
 *   <li>{@code GET /onchain/networks/{n}/pools/{a}/ohlcv/{tf}} — pool OHLCV.</li>
 *   <li>{@code GET /onchain/networks/{n}/tokens/{addr}/pools} — pools for a token.</li>
 *   <li>{@code GET /onchain/networks/{n}/pools/{a}/trades} — recent pool trades.</li>
 *   <li>{@code GET /onchain/search/pools?query=...} — search pools.</li>
 * </ul>
 *
 * <h3>OHLCV storage:</h3>
 * Pool OHLCV data is stored in dynamically created tables named
 * {@code POOL_{NETWORK}_{ADDRESS_SHORT}_COINGECKO_{TIMEFRAME}}.
 */
@Service
public class CoinGeckoDexService {

    private static final Logger log = LoggerFactory.getLogger(CoinGeckoDexService.class);

    private static final long NETWORKS_CACHE_TTL_MS  = 60 * 60_000L; // 1 hour
    private static final long POOLS_CACHE_TTL_MS     = 5  * 60_000L; // 5 min
    private static final long TRENDING_CACHE_TTL_MS  = 5  * 60_000L; // 5 min

    private final CoinGeckoService          coinGeckoService;
    private final DynamicOhlcvTableService  dynamicTableService;

    // ── In-memory caches ─────────────────────────────────────────────────────

    private volatile CacheEntry<JsonObject> networksCache;
    private final Map<String, CacheEntry<JsonObject>> poolsCache    = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<JsonObject>> trendingCache = new ConcurrentHashMap<>();

    public CoinGeckoDexService(CoinGeckoService coinGeckoService,
                               DynamicOhlcvTableService dynamicTableService) {
        this.coinGeckoService   = coinGeckoService;
        this.dynamicTableService = dynamicTableService;
    }

    // ── Networks ──────────────────────────────────────────────────────────────

    /**
     * Returns all supported networks from {@code /onchain/networks}.
     * Cached for 1 hour (list changes rarely).
     */
    public Optional<JsonObject> getNetworks() {
        if (networksCache != null && !networksCache.isExpired())
            return Optional.ofNullable(networksCache.value);

        return coinGeckoService.listNetworks().map(arr -> {
            JsonObject wrapper = new JsonObject();
            wrapper.add("data", arr);
            networksCache = new CacheEntry<>(wrapper, NETWORKS_CACHE_TTL_MS);
            return wrapper;
        });
    }

    /**
     * Returns a list of network IDs as strings (e.g. ["eth", "bsc", "polygon_pos"]).
     */
    public List<String> getNetworkIds() {
        Optional<JsonObject> raw = getNetworks();
        if (raw.isEmpty() || !raw.get().has("data")) return Collections.emptyList();
        JsonArray arr = raw.get().getAsJsonArray("data");
        List<String> ids = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            if (el.isJsonObject() && el.getAsJsonObject().has("id")) {
                ids.add(el.getAsJsonObject().get("id").getAsString());
            }
        }
        return ids;
    }

    // ── Top pools ─────────────────────────────────────────────────────────────

    /**
     * Returns top pools on a given network.
     * Uses {@code GET /onchain/networks/{network}/pools}.
     *
     * @param network network ID (e.g. "eth")
     * @param page    page index (1-based)
     * @return JsonObject with "data" array of pool items
     */
    public Optional<JsonObject> getTopPools(String network, int page) {
        String cacheKey = network + "|" + page;
        CacheEntry<JsonObject> cached = poolsCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        // CoinGeckoService.getPoolsByNetwork returns JsonObject with "data" array
        Optional<JsonObject> raw = coinGeckoService.getPoolsByNetwork(network, 20, page, null);
        return raw.map(obj -> {
            poolsCache.put(cacheKey, new CacheEntry<>(obj, POOLS_CACHE_TTL_MS));
            return obj;
        });
    }

    // ── Trending pools ────────────────────────────────────────────────────────

    /**
     * Returns globally trending pools across all networks.
     */
    public Optional<JsonObject> getTrendingPoolsGlobal() {
        CacheEntry<JsonObject> cached = trendingCache.get("global");
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        Optional<JsonObject> result = coinGeckoService.getTrendingPoolsGlobal();
        result.ifPresent(obj -> trendingCache.put("global",
                new CacheEntry<>(obj, TRENDING_CACHE_TTL_MS)));
        return result;
    }

    /**
     * Returns trending pools on a specific network.
     */
    public Optional<JsonObject> getTrendingPoolsByNetwork(String network) {
        CacheEntry<JsonObject> cached = trendingCache.get(network);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        Optional<JsonObject> result = coinGeckoService.getTrendingPoolsByNetwork(network);
        result.ifPresent(obj -> trendingCache.put(network,
                new CacheEntry<>(obj, TRENDING_CACHE_TTL_MS)));
        return result;
    }

    // ── Pool OHLCV ────────────────────────────────────────────────────────────

    /**
     * Fetches OHLCV bars for a DEX pool and stores them in a dynamic table.
     *
     * <p>Table name format: {@code POOL_{NETWORK}_{ADDR_SHORT}_COINGECKO_{TIMEFRAME_UPPER}}
     * e.g. {@code POOL_ETH_0X88E6_COINGECKO_1H}
     *
     * @param network   network ID (e.g. "eth")
     * @param poolAddr  pool contract address (full hex)
     * @param timeframe "minute", "hour", or "day" (GeckoTerminal timeframe unit)
     * @param aggregate aggregation multiplier (e.g. 4 for 4h when timeframe="hour")
     * @param limit     max candles (up to 1000)
     * @return list of OHLCV bars; also persisted to DB
     */
    public List<OhlcvBar> getPoolOhlcv(String network, String poolAddr,
                                       String timeframe, int aggregate, int limit) {
        Optional<JsonObject> raw = coinGeckoService.getPoolOhlcv(
                network, poolAddr, timeframe, aggregate, limit);

        String symbol     = poolSymbol(network, poolAddr);
        String tfDisplay  = aggregate + timeframe.substring(0, 1); // e.g. "4h"
        List<OhlcvBar> bars = coinGeckoService.parsePoolOhlcvToOhlcvBars(raw, symbol, tfDisplay);

        if (!bars.isEmpty()) {
            String tableName = buildPoolTableName(network, poolAddr, tfDisplay);
            try {
                dynamicTableService.upsertBars(tableName, Trade.AssetType.CRYPTO, bars);
                log.debug("Stored {} pool OHLCV bars to {}", bars.size(), tableName);
            } catch (Exception e) {
                log.warn("Failed to store pool OHLCV bars for {}: {}", tableName, e.getMessage());
            }
        }
        return bars;
    }

    /**
     * Reads pool OHLCV bars from the DB (without making an API call).
     * Returns empty list if the table doesn't exist or has no data.
     */
    public List<OhlcvBar> getPoolOhlcvFromDb(String network, String poolAddr,
                                              String timeframe, int aggregate, int limit) {
        String tfDisplay = aggregate + timeframe.substring(0, 1);
        String symbol    = poolSymbol(network, poolAddr);
        String tableName = buildPoolTableName(network, poolAddr, tfDisplay);
        try {
            return dynamicTableService.findBars(tableName, symbol, tfDisplay,
                    Trade.AssetType.CRYPTO, limit);
        } catch (Exception e) {
            log.debug("No DB data for pool {}: {}", tableName, e.getMessage());
            return Collections.emptyList();
        }
    }

    // ── Pools by token ────────────────────────────────────────────────────────

    /**
     * Returns pools associated with a specific token address.
     * Uses {@code GET /onchain/networks/{network}/tokens/{address}/pools}.
     *
     * @param network      network ID
     * @param tokenAddress token contract address
     * @param page         page index
     * @return JsonObject with "data" array
     */
    public Optional<JsonObject> getPoolsByToken(String network, String tokenAddress, int page) {
        return coinGeckoService.getPoolsByToken(network, tokenAddress, page);
    }

    // ── Pool trades ───────────────────────────────────────────────────────────

    /**
     * Returns recent trades for a specific pool.
     * Uses {@code GET /onchain/networks/{network}/pools/{address}/trades}.
     */
    public Optional<JsonObject> getPoolTrades(String network, String poolAddress) {
        return coinGeckoService.getPoolTrades(network, poolAddress);
    }

    // ── Pool search ───────────────────────────────────────────────────────────

    /**
     * Searches DEX pools by name or token.
     * Uses {@code GET /onchain/search/pools?query=...}.
     */
    public Optional<JsonObject> searchPools(String query) {
        return coinGeckoService.searchPools(query);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    /**
     * Builds a display symbol from the network and pool address.
     * e.g. "POOL_ETH_0x88e6..."
     */
    private String poolSymbol(String network, String poolAddr) {
        String addrShort = poolAddr.length() > 10
                ? poolAddr.substring(0, 10).toUpperCase()
                : poolAddr.toUpperCase();
        return "POOL_" + network.toUpperCase() + "_" + addrShort;
    }

    /**
     * Builds the dynamic DB table name for a pool's OHLCV data.
     * Format: {@code POOL_{NETWORK}_{ADDR8}_COINGECKO_{TF}}
     * e.g. {@code POOL_ETH_0X88E6A0C2_COINGECKO_4H}
     */
    private String buildPoolTableName(String network, String poolAddr, String timeframe) {
        String addrPart = poolAddr.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (addrPart.length() > 8) addrPart = addrPart.substring(0, 8);
        return ("POOL_" + network + "_" + addrPart + "_COINGECKO_" + timeframe)
                .toUpperCase().replaceAll("[^A-Z0-9_]", "_");
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
