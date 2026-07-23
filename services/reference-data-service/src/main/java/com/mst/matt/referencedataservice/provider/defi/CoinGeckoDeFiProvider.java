package com.mst.matt.referencedataservice.provider.defi;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.defi.DeFiDataProvider;
import com.mst.matt.contracts.provider.dto.DeFiPoolDto;
import com.mst.matt.referencedataservice.client.JsonUtil;
import com.mst.matt.referencedataservice.client.RefDataHttpClient;
import com.mst.matt.referencedataservice.config.RefDataProviderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CoinGecko / GeckoTerminal on-chain DeFi data provider.
 *
 * <p>Uses the GeckoTerminal public on-chain API (JSONAPI format) for pool data:
 * <ul>
 *   <li>{@code GET /onchain/networks/{n}/pools}      — pools for a network</li>
 *   <li>{@code GET /onchain/networks/trending_pools} — trending pools globally</li>
 *   <li>{@code GET /onchain/search/pools?query=}     — pool search</li>
 * </ul>
 *
 * <p>Throttle key: {@code "coingecko_defi_ref"} at 10 req/min (free-tier limit).
 * The GeckoTerminal on-chain endpoint uses a different base URL from the CoinGecko v3 API.
 *
 * <p>Registered ahead of {@code NoOpDeFiDataProvider} in the
 * {@code ProviderRegistry&lt;DeFiDataProvider&gt;} chain.</p>
 */
@Component
public class CoinGeckoDeFiProvider implements DeFiDataProvider {

    private static final Logger log = LoggerFactory.getLogger(CoinGeckoDeFiProvider.class);
    public static final String PROVIDER_NAME = "COINGECKO_DEFI";

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public List<AssetClass> supportedAssetClasses() {
        return List.of(AssetClass.CRYPTO);
    }

    // GeckoTerminal on-chain API base URL
    private static final String ONCHAIN_BASE = "https://api.geckoterminal.com/api/v2";
    private static final String THROTTLE_KEY = "coingecko_defi_ref";
    

    private final RefDataHttpClient http;
    private final RefDataProviderProperties props;

    public CoinGeckoDeFiProvider(RefDataHttpClient http, RefDataProviderProperties props) {
        this.http = http;
        this.props = props;
        http.throttle(THROTTLE_KEY, 10, java.time.Duration.ofMinutes(1));
    }

    // ── Trending pools (global) ────────────────────────────────────────────────

    @Override
    public List<DeFiPoolDto> getTrendingPools() {
        return fetchPools(ONCHAIN_BASE + "/networks/trending_pools?include=dex,network");
    }

    // ── Pools for a specific network ──────────────────────────────────────────

    @Override
    public List<DeFiPoolDto> getPoolsByNetwork(String networkId) {
        if (networkId == null || networkId.isBlank()) return List.of();
        String url = ONCHAIN_BASE + "/networks/" + networkId + "/pools?include=dex,network";
        return fetchPools(url);
    }

    // ── Pool search ───────────────────────────────────────────────────────────

    @Override
    public List<DeFiPoolDto> searchPools(String query) {
        if (query == null || query.isBlank()) return List.of();
        try {
            String encoded = java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
            String url = ONCHAIN_BASE + "/search/pools?query=" + encoded + "&include=dex,network";
            return fetchPools(url);
        } catch (Exception e) {
            log.warn("[{}] searchPools({}) failed: {}", PROVIDER_NAME, query, e.getMessage());
            return List.of();
        }
    }

    // ── Core fetch + JSONAPI parse ─────────────────────────────────────────────

    private List<DeFiPoolDto> fetchPools(String url) {
        try {
            Optional<JsonElement> resp = http.getJsonElement(url, null, THROTTLE_KEY);
            if (resp.isEmpty()) return List.of();

            JsonObject root = resp.get().getAsJsonObject();
            if (!root.has("data") || !root.get("data").isJsonArray()) return List.of();

            // Build lookup maps from included resources (dex + network)
            Map<String, String> dexNames = new java.util.HashMap<>();
            Map<String, String> networkNames = new java.util.HashMap<>();
            if (root.has("included") && root.get("included").isJsonArray()) {
                for (JsonElement inc : root.getAsJsonArray("included")) {
                    if (!inc.isJsonObject()) continue;
                    JsonObject incObj = inc.getAsJsonObject();
                    String type = JsonUtil.str(incObj, "type");
                    String id = JsonUtil.str(incObj, "id");
                    JsonObject attrs = incObj.has("attributes") ? incObj.getAsJsonObject("attributes") : null;
                    if ("dex".equals(type) && id != null && attrs != null) {
                        dexNames.put(id, JsonUtil.str(attrs, "name"));
                    } else if ("network".equals(type) && id != null && attrs != null) {
                        networkNames.put(id, JsonUtil.str(attrs, "name"));
                    }
                }
            }

            JsonArray data = root.getAsJsonArray("data");
            List<DeFiPoolDto> results = new ArrayList<>();
            for (JsonElement el : data) {
                if (!el.isJsonObject()) continue;
                JsonObject poolObj = el.getAsJsonObject();
                DeFiPoolDto dto = parsePool(poolObj, dexNames, networkNames);
                if (dto != null) results.add(dto);
            }
            return results;
        } catch (Exception e) {
            log.warn("[{}] fetchPools failed for {}: {}", PROVIDER_NAME, url, e.getMessage());
            return List.of();
        }
    }

    private DeFiPoolDto parsePool(JsonObject poolObj,
                                   Map<String, String> dexNames,
                                   Map<String, String> networkNames) {
        try {
            // GeckoTerminal JSONAPI: { id, type, attributes:{...}, relationships:{...} }
            String poolId = JsonUtil.str(poolObj, "id"); // e.g. "eth_0xabc..."
            JsonObject attrs = poolObj.has("attributes") ? poolObj.getAsJsonObject("attributes") : null;
            if (attrs == null) return null;

            // Extract network and dex from relationships
            String networkId = null;
            String dexId = null;
            if (poolObj.has("relationships") && poolObj.get("relationships").isJsonObject()) {
                JsonObject rels = poolObj.getAsJsonObject("relationships");
                if (rels.has("network") && rels.get("network").isJsonObject()) {
                    JsonObject netRel = rels.getAsJsonObject("network");
                    if (netRel.has("data") && netRel.get("data").isJsonObject()) {
                        networkId = JsonUtil.str(netRel.getAsJsonObject("data"), "id");
                    }
                }
                if (rels.has("dex") && rels.get("dex").isJsonObject()) {
                    JsonObject dexRel = rels.getAsJsonObject("dex");
                    if (dexRel.has("data") && dexRel.get("dex").isJsonObject()) {
                        dexId = JsonUtil.str(dexRel.getAsJsonObject("data"), "id");
                    } else if (dexRel.has("data") && dexRel.get("data").isJsonObject()) {
                        dexId = JsonUtil.str(dexRel.getAsJsonObject("data"), "id");
                    }
                }
            }

            // Pool address: last segment of the compound id (e.g. "eth_0xabc" → "0xabc")
            String poolAddress = poolId;
            if (poolId != null && poolId.contains("_")) {
                int idx = poolId.indexOf("_");
                if (networkId == null) networkId = poolId.substring(0, idx);
                poolAddress = poolId.substring(idx + 1);
            }

            // Token symbols from attributes
            String baseSymbol = null;
            String quoteSymbol = null;
            String baseAddr = null;
            String quoteAddr = null;
            if (attrs.has("base_token_price_usd")) {
                // attributes contain base_token_price_usd, name, address
                baseAddr = JsonUtil.str(attrs, "pool_created_at"); // not here, see below
            }
            // Name like "WETH / USDC 0.05%"
            String poolName = JsonUtil.str(attrs, "name");
            if (poolName != null && poolName.contains("/")) {
                String[] parts = poolName.split("/", 2);
                baseSymbol = parts[0].trim();
                quoteSymbol = parts[1].contains(" ") ? parts[1].trim().split("\\s+")[0].trim() : parts[1].trim();
            }

            // Price changes
            BigDecimal change5m = null, change1h = null, change24h = null;
            if (attrs.has("price_change_percentage") && attrs.get("price_change_percentage").isJsonObject()) {
                JsonObject ch = attrs.getAsJsonObject("price_change_percentage");
                change5m  = JsonUtil.bd(ch, "m5");
                change1h  = JsonUtil.bd(ch, "h1");
                change24h = JsonUtil.bd(ch, "h24");
            }

            // Volume
            BigDecimal vol1h = null, vol6h = null, vol24h = null;
            if (attrs.has("volume_usd") && attrs.get("volume_usd").isJsonObject()) {
                JsonObject vol = attrs.getAsJsonObject("volume_usd");
                vol1h  = JsonUtil.bd(vol, "h1");
                vol6h  = JsonUtil.bd(vol, "h6");
                vol24h = JsonUtil.bd(vol, "h24");
            }

            // Transaction counts
            Long buys24h = null, sells24h = null, txCount24h = null;
            if (attrs.has("transactions") && attrs.get("transactions").isJsonObject()) {
                JsonObject txns = attrs.getAsJsonObject("transactions");
                if (txns.has("h24") && txns.get("h24").isJsonObject()) {
                    JsonObject h24 = txns.getAsJsonObject("h24");
                    buys24h  = JsonUtil.longVal(h24, "buys");
                    sells24h = JsonUtil.longVal(h24, "sells");
                    if (buys24h != null && sells24h != null) txCount24h = buys24h + sells24h;
                }
            }

            // Created at
            Instant createdAt = null;
            String createdStr = JsonUtil.str(attrs, "pool_created_at");
            if (createdStr != null) {
                try { createdAt = OffsetDateTime.parse(createdStr).toInstant(); } catch (Exception ignored) {}
            }

            return DeFiPoolDto.builder()
                    .poolAddress(poolAddress)
                    .poolName(poolName)
                    .networkId(networkId)
                    .networkName(networkId != null ? networkNames.getOrDefault(networkId, networkId) : null)
                    .dexId(dexId)
                    .dexName(dexId != null ? dexNames.getOrDefault(dexId, dexId) : null)
                    .baseTokenSymbol(baseSymbol)
                    .quoteTokenSymbol(quoteSymbol)
                    .priceUsd(JsonUtil.bd(attrs, "base_token_price_usd"))
                    .priceChangePercent5m(change5m)
                    .priceChangePercent1h(change1h)
                    .priceChangePercent24h(change24h)
                    .volume1hUsd(vol1h)
                    .volume6hUsd(vol6h)
                    .volume24hUsd(vol24h)
                    .liquidityUsd(JsonUtil.bd(attrs, "reserve_in_usd"))
                    .marketCapUsd(JsonUtil.bd(attrs, "market_cap_usd"))
                    .txCount24h(txCount24h)
                    .buys24h(buys24h)
                    .sells24h(sells24h)
                    .createdAt(createdAt)
                    .providerName(PROVIDER_NAME)
                    .fetchedAt(Instant.now())
                    .build();
        } catch (Exception e) {
            log.trace("[{}] parsePool failed: {}", PROVIDER_NAME, e.getMessage());
            return null;
        }
    }
}
