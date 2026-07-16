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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Finnhub implementation of {@link SymbolSearchProvider}.
 *
 * <h3>Endpoint</h3>
 * <pre>GET https://finnhub.io/api/v1/search?q={query}&token={key}</pre>
 *
 * <p>Response shape:</p>
 * <pre>{@code
 * {
 *   "count": 4,
 *   "result": [
 *     {
 *       "description": "APPLE INC",
 *       "displaySymbol": "AAPL",
 *       "symbol":        "AAPL",
 *       "type":          "Common Stock"
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <p>Throttle key {@code "finnhub"} (60 req/min) registered by
 * {@link com.mst.matt.referencedataservice.provider.fundamentals.FinnhubFundamentalsProvider}.</p>
 *
 * <p>Supported asset classes: {@link AssetClass#STOCK}.</p>
 */
@Component
public class FinnhubSearchProvider implements SymbolSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(FinnhubSearchProvider.class);

    public static final String PROVIDER_NAME = "FINNHUB";
    private static final String BASE_URL     = "https://finnhub.io/api/v1";
    private static final String THROTTLE_KEY = "finnhub";
    private static final String UA           = "reference-data-service/1.0";

    private final RefDataHttpClient http;
    private final RefDataProviderProperties keys;

    public FinnhubSearchProvider(RefDataHttpClient http, RefDataProviderProperties keys) {
        this.http  = http;
        this.keys  = keys;
    }

    @Override public String providerName() { return PROVIDER_NAME; }

    @Override
    public List<AssetClass> supportedAssetClasses() {
        return List.of(AssetClass.STOCK);
    }

    // ── SymbolSearchProvider ──────────────────────────────────────────────────

    @Override
    public List<SymbolSearchResultDto> search(String query, int limit) {
        return searchFinnhub(query, limit);
    }

    @Override
    public List<SymbolSearchResultDto> search(String query, AssetClass assetClass, int limit) {
        if (assetClass != AssetClass.STOCK) return List.of();
        return searchFinnhub(query, limit);
    }

    @Override
    public Optional<SymbolSearchResultDto> lookup(String symbol, AssetClass assetClass) {
        if (assetClass != null && assetClass != AssetClass.STOCK) return Optional.empty();
        return searchFinnhub(symbol, 10).stream()
                .filter(r -> r.getSymbol().equalsIgnoreCase(symbol))
                .findFirst();
    }

    @Override
    public List<SymbolSearchResultDto> listAll(AssetClass assetClass, int limit, int offset) {
        // Finnhub /search requires a query term; bulk listing not supported here.
        return List.of();
    }

    @Override
    public List<SymbolSearchResultDto> getTopSymbols(AssetClass assetClass, int limit) {
        if (assetClass != AssetClass.STOCK) return List.of();
        // Use a broad single-char query as a proxy for popular symbols.
        return searchFinnhub("a", limit);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<SymbolSearchResultDto> searchFinnhub(String query, int limit) {
        if (!keys.hasFinnhubKey() || query == null || query.isBlank()) return List.of();

        String url = BASE_URL + "/search"
                + "?q=" + encode(query)
                + "&token=" + keys.getFinnhubKey();

        return http.getJson(url, UA, THROTTLE_KEY).map(root -> {
            List<SymbolSearchResultDto> results = new ArrayList<>();
            JsonElement resultEl = root.get("result");
            if (resultEl == null || !resultEl.isJsonArray()) return results;

            JsonArray arr = resultEl.getAsJsonArray();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject item = el.getAsJsonObject();

                String symbol        = JsonUtil.str(item, "symbol");
                String displaySymbol = JsonUtil.str(item, "displaySymbol");
                String description   = JsonUtil.str(item, "description");
                String type          = JsonUtil.str(item, "type");

                if (symbol == null) continue;

                InstrumentType instrType = mapInstrumentType(type);
                AssetClass assetCls      = instrType != null
                        ? instrType.toAssetClass()
                        : AssetClass.STOCK;

                results.add(SymbolSearchResultDto.builder()
                        .symbol(displaySymbol != null ? displaySymbol : symbol)
                        .displayName(description)
                        .assetClass(assetCls)
                        .instrumentType(instrType)
                        .quoteCurrency("USD")      // Finnhub does not return currency in search
                        .country("US")             // Finnhub /search is predominantly US equities
                        .providerSymbol(symbol)
                        .providerName(PROVIDER_NAME)
                        .relevanceScore(null)      // Finnhub does not return a relevance score
                        .active(true)
                        .platformSupported(true)
                        .build());

                if (results.size() >= limit) break;
            }
            return results;
        }).orElse(List.of());
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    /**
     * Map Finnhub "type" strings to InstrumentType.
     * Known Finnhub type values include: "Common Stock", "ETP" (ETF), "ADR",
     * "Warrant", "Right", "Closed-End Fund", "Open-End Fund", "Unit", etc.
     */
    private static InstrumentType mapInstrumentType(String type) {
        if (type == null) return InstrumentType.EQUITY;
        String t = type.toLowerCase();
        if (t.contains("etf") || t.contains("etp") || t.contains("fund")
                || t.contains("index"))                    return InstrumentType.ETF_INDEX;
        if (t.contains("forex") || t.contains("fx"))      return InstrumentType.FOREX;
        if (t.contains("crypto"))                          return InstrumentType.CRYPTO_SPOT;
        // Common Stock, ADR, Warrant, Right, Unit → EQUITY
        return InstrumentType.EQUITY;
    }

    private static String encode(String s) {
        return s.replace(" ", "%20");
    }
}
