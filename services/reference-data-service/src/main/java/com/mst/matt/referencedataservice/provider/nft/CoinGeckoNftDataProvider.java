package com.mst.matt.referencedataservice.provider.nft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NftAssetDto;
import com.mst.matt.contracts.provider.dto.NftCollectionDto;
import com.mst.matt.contracts.provider.dto.NftEventDto;
import com.mst.matt.contracts.provider.nft.NftDataProvider;
import com.mst.matt.referencedataservice.client.JsonUtil;
import com.mst.matt.referencedataservice.client.RefDataHttpClient;
import com.mst.matt.referencedataservice.config.RefDataProviderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CoinGecko implementation of {@link NftDataProvider}.
 *
 * <p>Uses the CoinGecko free-tier NFT endpoints:
 * <ul>
 *   <li>{@code GET /nfts/list}          — paginated list of all NFT collections</li>
 *   <li>{@code GET /nfts/{id}}          — detailed collection metadata + market stats</li>
 *   <li>{@code GET /search/trending}    — trending coins/NFTs (nfts array)</li>
 * </ul>
 *
 * <p>Throttle key: {@code "coingecko_nft_ref"} at 10 req/min (free tier limit).
 * Asset-level endpoints (per-token traits, events) are not available on the
 * free tier — those methods return empty results gracefully.
 */
@Component
public class CoinGeckoNftDataProvider implements NftDataProvider {

    private static final Logger log = LoggerFactory.getLogger(CoinGeckoNftDataProvider.class);
    public static final String PROVIDER_NAME = "COINGECKO_NFT";

    private static final String BASE_URL = "https://api.coingecko.com/api/v3";
    private static final String THROTTLE_KEY = "coingecko_nft_ref";
    

    private final RefDataHttpClient http;
    private final RefDataProviderProperties props;

    public CoinGeckoNftDataProvider(RefDataHttpClient http, RefDataProviderProperties props) {
        this.http = http;
        this.props = props;
        http.throttle(THROTTLE_KEY, 10, java.time.Duration.ofMinutes(1));
    }

    @Override
    public String providerName() { return PROVIDER_NAME; }

    @Override
    public List<AssetClass> supportedAssetClasses() { return List.of(AssetClass.NFT); }

    // ── Collection ────────────────────────────────────────────────────────────

    @Override
    public Optional<NftCollectionDto> getCollection(String collectionSlug) {
        if (collectionSlug == null || collectionSlug.isBlank()) return Optional.empty();
        try {
            String url = buildUrl("/nfts/" + collectionSlug);
            return http.getJsonElement(url, null, THROTTLE_KEY)
                    .map(JsonElement::getAsJsonObject)
                    .map(this::parseCollectionDetail);
        } catch (Exception e) {
            log.warn("[{}] getCollection({}) failed: {}", PROVIDER_NAME, collectionSlug, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<NftCollectionDto> searchCollections(String query, int limit) {
        // CoinGecko /search endpoint returns coins+NFTs mixed; use /nfts/list with no filter
        // as a fallback — proper text search isn't available on free tier
        try {
            String url = buildUrl("/search?query=" + encode(query));
            Optional<JsonElement> resp = http.getJsonElement(url, null, THROTTLE_KEY);
            if (resp.isEmpty()) return List.of();

            JsonObject root = resp.get().getAsJsonObject();
            List<NftCollectionDto> results = new ArrayList<>();

            if (root.has("nfts") && root.get("nfts").isJsonArray()) {
                JsonArray arr = root.getAsJsonArray("nfts");
                for (int i = 0; i < arr.size() && results.size() < limit; i++) {
                    JsonObject item = arr.get(i).getAsJsonObject();
                    NftCollectionDto dto = NftCollectionDto.builder()
                            .collectionSlug(JsonUtil.str(item, "id"))
                            .name(JsonUtil.str(item, "name"))
                            .blockchain(JsonUtil.str(item, "asset_platform_id"))
                            .contractAddress(JsonUtil.str(item, "contract_address"))
                            .providerName(PROVIDER_NAME)
                            .fetchedAt(Instant.now())
                            .build();
                    results.add(dto);
                }
            }
            return results;
        } catch (Exception e) {
            log.warn("[{}] searchCollections({}) failed: {}", PROVIDER_NAME, query, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<NftCollectionDto> getTrendingCollections(int limit) {
        try {
            String url = buildUrl("/search/trending");
            Optional<JsonElement> resp = http.getJsonElement(url, null, THROTTLE_KEY);
            if (resp.isEmpty()) return List.of();

            JsonObject root = resp.get().getAsJsonObject();
            List<NftCollectionDto> results = new ArrayList<>();

            if (root.has("nfts") && root.get("nfts").isJsonArray()) {
                JsonArray arr = root.getAsJsonArray("nfts");
                for (int i = 0; i < arr.size() && results.size() < limit; i++) {
                    JsonObject item = arr.get(i).getAsJsonObject();
                    // Trending NFT items wrap the nft data under "item" key
                    JsonObject nft = item.has("item") ? item.getAsJsonObject("item") : item;
                    NftCollectionDto dto = NftCollectionDto.builder()
                            .collectionSlug(JsonUtil.str(nft, "id"))
                            .name(JsonUtil.str(nft, "name"))
                            .blockchain(JsonUtil.str(nft, "asset_platform_id"))
                            .nativeCurrency(JsonUtil.str(nft, "native_currency_symbol"))
                            .floorPrice(JsonUtil.bd(nft, "floor_price_in_native_currency"))
                            .volume24h(JsonUtil.bd(nft, "h24_volume_in_native_currency"))
                            .providerName(PROVIDER_NAME)
                            .fetchedAt(Instant.now())
                            .build();
                    results.add(dto);
                }
            }
            return results;
        } catch (Exception e) {
            log.warn("[{}] getTrendingCollections failed: {}", PROVIDER_NAME, e.getMessage());
            return List.of();
        }
    }

    /** Fetch the first page of the NFT list (used as "list collections"). */
    public List<NftCollectionDto> listCollections(int limit, int page) {
        try {
            String url = buildUrl("/nfts/list?order=market_cap_usd_desc&per_page=" + Math.min(limit, 250) + "&page=" + page);
            Optional<JsonElement> resp = http.getJsonElement(url, null, THROTTLE_KEY);
            if (resp.isEmpty()) return List.of();

            JsonArray arr = resp.get().getAsJsonArray();
            List<NftCollectionDto> results = new ArrayList<>();
            for (JsonElement el : arr) {
                JsonObject item = el.getAsJsonObject();
                results.add(NftCollectionDto.builder()
                        .collectionSlug(JsonUtil.str(item, "id"))
                        .name(JsonUtil.str(item, "name"))
                        .contractAddress(JsonUtil.str(item, "contract_address"))
                        .blockchain(JsonUtil.str(item, "asset_platform_id"))
                        .providerName(PROVIDER_NAME)
                        .fetchedAt(Instant.now())
                        .build());
            }
            return results;
        } catch (Exception e) {
            log.warn("[{}] listCollections failed: {}", PROVIDER_NAME, e.getMessage());
            return List.of();
        }
    }

    // ── Floor price ───────────────────────────────────────────────────────────

    @Override
    public NftCollectionDto getFloorPrice(String collectionSlug) {
        return getCollection(collectionSlug)
                .orElse(NftCollectionDto.builder()
                        .collectionSlug(collectionSlug)
                        .providerName(PROVIDER_NAME)
                        .build());
    }

    // ── Asset endpoints (not supported on CG free tier) ───────────────────────

    @Override
    public Optional<NftAssetDto> getAsset(String collectionSlug, String tokenId) {
        return Optional.empty();   // per-token metadata not in CoinGecko free tier
    }

    @Override
    public List<NftAssetDto> getCollectionAssets(String collectionSlug, int limit, int offset) {
        return List.of();
    }

    @Override
    public List<NftAssetDto> getRarestAssets(String collectionSlug, int limit) {
        return List.of();
    }

    // ── Activity / events (not available on CG free tier) ────────────────────

    @Override
    public List<NftEventDto> getCollectionActivity(String collectionSlug, int limit) {
        return List.of();
    }

    @Override
    public List<NftEventDto> getAssetActivity(String collectionSlug, String tokenId, int limit) {
        return List.of();
    }

    // ── Parsing helpers ───────────────────────────────────────────────────────

    private NftCollectionDto parseCollectionDetail(JsonObject obj) {
        // Floor price is a nested object: { "native_currency": val, "usd": val }
        BigDecimal floorPriceNative = null;
        BigDecimal floorPriceUsd = null;
        BigDecimal volume24hNative = null;
        BigDecimal volume24hUsd = null;
        BigDecimal marketCapUsd = null;
        String nativeCurrency = null;

        if (obj.has("floor_price") && obj.get("floor_price").isJsonObject()) {
            JsonObject fp = obj.getAsJsonObject("floor_price");
            floorPriceUsd = JsonUtil.bd(fp, "usd");
            // first non-"usd" key is the native currency value
            for (String key : fp.keySet()) {
                if (!"usd".equals(key)) {
                    nativeCurrency = key;
                    floorPriceNative = JsonUtil.bd(fp, key);
                    break;
                }
            }
        }

        if (obj.has("volume_24h") && obj.get("volume_24h").isJsonObject()) {
            JsonObject v = obj.getAsJsonObject("volume_24h");
            volume24hUsd = JsonUtil.bd(v, "usd");
            if (nativeCurrency != null) volume24hNative = JsonUtil.bd(v, nativeCurrency);
        }

        if (obj.has("market_cap") && obj.get("market_cap").isJsonObject()) {
            JsonObject mc = obj.getAsJsonObject("market_cap");
            marketCapUsd = JsonUtil.bd(mc, "usd");
        }

        // 24h floor price change
        BigDecimal floorChange24h = null;
        if (obj.has("floor_price_24h_percentage_change")
                && obj.get("floor_price_24h_percentage_change").isJsonObject()) {
            JsonObject ch = obj.getAsJsonObject("floor_price_24h_percentage_change");
            floorChange24h = JsonUtil.bd(ch, "usd");
        }

        // Number of unique owners
        Long ownerCount = null;
        if (obj.has("number_of_unique_addresses")) {
            ownerCount = JsonUtil.longVal(obj, "number_of_unique_addresses");
        }

        // Total supply
        Long totalSupply = null;
        if (obj.has("total_supply")) {
            totalSupply = JsonUtil.longVal(obj, "total_supply");
        }

        return NftCollectionDto.builder()
                .collectionSlug(JsonUtil.str(obj, "id"))
                .name(JsonUtil.str(obj, "name"))
                .description(extractDescription(obj))
                .blockchain(JsonUtil.str(obj, "asset_platform_id"))
                .contractAddress(JsonUtil.str(obj, "contract_address"))
                .nativeCurrency(nativeCurrency)
                .floorPrice(floorPriceNative)
                .floorPriceUsd(floorPriceUsd)
                .floorPriceChange24hPct(floorChange24h)
                .volume24h(volume24hNative)
                .volume24hUsd(volume24hUsd)
                .ownerCount(ownerCount)
                .totalSupply(totalSupply)
                .providerName(PROVIDER_NAME)
                .fetchedAt(Instant.now())
                .build();
    }

    private String extractDescription(JsonObject obj) {
        if (!obj.has("description")) return null;
        JsonElement desc = obj.get("description");
        if (desc.isJsonPrimitive()) return desc.getAsString();
        if (desc.isJsonObject()) {
            JsonObject descObj = desc.getAsJsonObject();
            if (descObj.has("en")) return JsonUtil.str(descObj, "en");
        }
        return null;
    }

    private String buildUrl(String path) {
        String key = props.getCoingeckoKey();
        String base = BASE_URL + path;
        if (key != null && !key.isBlank()) {
            String sep = base.contains("?") ? "&" : "?";
            return base + sep + "x_cg_demo_api_key=" + key;
        }
        return base;
    }

    private String encode(String s) {
        try {
            return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) { return s; }
    }
}
