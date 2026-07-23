package com.mst.matt.referencedataservice.provider.search;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.enums.InstrumentType;
import com.mst.matt.contracts.provider.dto.SymbolSearchResultDto;
import com.mst.matt.contracts.provider.search.SymbolSearchProvider;
import com.mst.matt.referencedataservice.client.JsonUtil;
import com.mst.matt.referencedataservice.client.RefDataHttpClient;
import com.mst.matt.referencedataservice.config.RefDataProviderProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CoinGecko implementation of {@link SymbolSearchProvider}.
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET /search?query={q}} — searches coins, exchanges, NFTs, categories.
 *       This provider maps only the {@code coins} array.</li>
 *   <li>{@code GET /coins/list} — full coin listing (used by {@link #listAll}).</li>
 * </ul>
 *
 * <p>Response shape for /search:</p>
 * <pre>{@code
 * {
 *   "coins": [
 *     {
 *       "id":           "bitcoin",
 *       "name":         "Bitcoin",
 *       "api_symbol":   "bitcoin",
 *       "symbol":       "BTC",
 *       "market_cap_rank": 1,
 *       "thumb":        "https://…",
 *       "large":        "https://…"
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <p>Throttle key {@code "coingecko_search_ref"} at 10 req/min (free tier ceiling).
 * Separate from the sentiment provider's throttle to avoid cross-pollination.</p>
 *
 * <p>Supported asset classes: {@link AssetClass#CRYPTO}.</p>
 */
@Component
public class CoinGeckoSearchProvider implements SymbolSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(CoinGeckoSearchProvider.class);

    public static final String PROVIDER_NAME  = "COINGECKO";
    private static final String BASE_URL      = "https://api.coingecko.com/api/v3";
    private static final String THROTTLE_KEY  = "coingecko_search_ref";
    private static final String UA            = "reference-data-service/1.0";

    private final RefDataHttpClient http;
    private final RefDataProviderProperties keys;

    public CoinGeckoSearchProvider(RefDataHttpClient http, RefDataProviderProperties keys) {
        this.http  = http;
        this.keys  = keys;
    }

    @PostConstruct
    public void registerThrottle() {
        http.throttle(THROTTLE_KEY, 10, Duration.ofMinutes(1));
    }

    @Override public String providerName() { return PROVIDER_NAME; }

    @Override
    public List<AssetClass> supportedAssetClasses() {
        return List.of(AssetClass.CRYPTO);
    }

    // ── SymbolSearchProvider ──────────────────────────────────────────────────

    @Override
    public List<SymbolSearchResultDto> search(String query, int limit) {
        return searchCg(query, limit);
    }

    @Override
    public List<SymbolSearchResultDto> search(String query, AssetClass assetClass, int limit) {
        if (assetClass != AssetClass.CRYPTO) return List.of();
        return searchCg(query, limit);
    }

    @Override
    public Optional<SymbolSearchResultDto> lookup(String symbol, AssetClass assetClass) {
        if (assetClass != null && assetClass != AssetClass.CRYPTO) return Optional.empty();
        return searchCg(symbol, 20).stream()
                .filter(r -> r.getSymbol().equalsIgnoreCase(symbol))
                .findFirst();
    }

    @Override
    public List<SymbolSearchResultDto> listAll(AssetClass assetClass, int limit, int offset) {
        if (assetClass != AssetClass.CRYPTO) return List.of();
        return fetchCoinList(limit, offset);
    }

    @Override
    public List<SymbolSearchResultDto> getTopSymbols(AssetClass assetClass, int limit) {
        if (assetClass != AssetClass.CRYPTO) return List.of();
        // /coins/markets gives top coins by market cap; use /search with "bitcoin" as proxy
        return searchCg("bitcoin", limit);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<SymbolSearchResultDto> searchCg(String query, int limit) {
        if (query == null || query.isBlank()) return List.of();

        String url = buildUrl("/search") + "?query=" + encode(query);

        return http.getJson(url, UA, THROTTLE_KEY).map(root -> {
            List<SymbolSearchResultDto> results = new ArrayList<>();
            JsonElement coinsEl = root.get("coins");
            if (coinsEl == null || !coinsEl.isJsonArray()) return results;

            JsonArray coins = coinsEl.getAsJsonArray();
            int rank = 1;
            for (JsonElement el : coins) {
                if (!el.isJsonObject()) continue;
                JsonObject item = el.getAsJsonObject();

                String id          = JsonUtil.str(item, "id");
                String name        = JsonUtil.str(item, "name");
                String tickerSym   = JsonUtil.str(item, "symbol");
                Integer marketRank = JsonUtil.integer(item, "market_cap_rank");

                if (tickerSym == null) continue;

                // CoinGecko symbols are lowercase (e.g. "btc") — normalise to uppercase
                String canonicalSymbol = tickerSym.toUpperCase();

                // Higher market_cap_rank = worse rank; convert to [0,1] relevance
                double relevance = marketRank != null
                        ? Math.max(0.0, 1.0 - (marketRank / 1000.0))
                        : 0.5;

                results.add(SymbolSearchResultDto.builder()
                        .symbol(canonicalSymbol)
                        .displayName(name)
                        .assetClass(AssetClass.CRYPTO)
                        .instrumentType(InstrumentType.CRYPTO_SPOT)
                        .quoteCurrency("USDT")          // default quote for crypto spot
                        .country(null)                   // crypto has no issuing country
                        .providerSymbol(id)              // CoinGecko ID (e.g. "bitcoin")
                        .providerName(PROVIDER_NAME)
                        .relevanceScore(relevance)
                        .active(true)
                        .platformSupported(true)
                        .build());

                if (results.size() >= limit) break;
            }
            return results;
        }).orElse(List.of());
    }

    /**
     * Fetch full coin list from {@code /coins/list} and apply offset/limit paging.
     * Response: [{id, symbol, name}, …]
     */
    private List<SymbolSearchResultDto> fetchCoinList(int limit, int offset) {
        String url = buildUrl("/coins/list");

        return http.getJsonElement(url, UA, THROTTLE_KEY).map(rootEl -> {
            List<SymbolSearchResultDto> results = new ArrayList<>();
            if (!rootEl.isJsonArray()) return results;

            JsonArray arr = rootEl.getAsJsonArray();
            int idx = 0;
            for (JsonElement el : arr) {
                if (idx < offset) { idx++; continue; }
                if (!el.isJsonObject()) continue;
                JsonObject item = el.getAsJsonObject();

                String id     = JsonUtil.str(item, "id");
                String ticker = JsonUtil.str(item, "symbol");
                String name   = JsonUtil.str(item, "name");

                if (ticker == null) continue;

                results.add(SymbolSearchResultDto.builder()
                        .symbol(ticker.toUpperCase())
                        .displayName(name)
                        .assetClass(AssetClass.CRYPTO)
                        .instrumentType(InstrumentType.CRYPTO_SPOT)
                        .quoteCurrency("USDT")
                        .country(null)
                        .providerSymbol(id)
                        .providerName(PROVIDER_NAME)
                        .relevanceScore(0.5)
                        .active(true)
                        .platformSupported(true)
                        .build());

                if (results.size() >= limit) break;
                idx++;
            }
            return results;
        }).orElse(List.of());
    }

    // ── URL builder ───────────────────────────────────────────────────────────

    private String buildUrl(String path) {
        StringBuilder sb = new StringBuilder(BASE_URL).append(path);
        if (keys.hasCoinGeckoKey()) {
            sb.append("?x_cg_pro_api_key=").append(keys.getCoingeckoKey());
        }
        return sb.toString();
    }

    private static String encode(String s) {
        return s.replace(" ", "%20");
    }
}
