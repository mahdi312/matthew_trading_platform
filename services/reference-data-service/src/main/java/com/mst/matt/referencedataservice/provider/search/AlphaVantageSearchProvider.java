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
 * Alpha Vantage implementation of {@link SymbolSearchProvider}.
 *
 * <h3>Endpoint</h3>
 * <pre>GET https://www.alphavantage.co/query?function=SYMBOL_SEARCH&keywords={query}&apikey={key}</pre>
 *
 * <p>Response shape:</p>
 * <pre>{@code
 * {
 *   "bestMatches": [
 *     {
 *       "1. symbol":      "AAPL",
 *       "2. name":        "Apple Inc",
 *       "3. type":        "Equity",
 *       "4. region":      "United States",
 *       "5. marketOpen":  "09:30",
 *       "6. marketClose": "16:00",
 *       "7. timezone":    "UTC-04",
 *       "8. currency":    "USD",
 *       "9. matchScore":  "1.0000"
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <p>Throttle key {@code "alphavantage"} (5 req/min) registered by
 * {@link com.mst.matt.referencedataservice.provider.fundamentals.AlphaVantageFundamentalsProvider}.</p>
 *
 * <p>Alpha Vantage SYMBOL_SEARCH covers equities + ETFs only.
 * Supported asset classes: {@link AssetClass#STOCK}.</p>
 */
@Component
public class AlphaVantageSearchProvider implements SymbolSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(AlphaVantageSearchProvider.class);

    public static final String PROVIDER_NAME = "ALPHA_VANTAGE";
    private static final String BASE_URL     = "https://www.alphavantage.co/query";
    private static final String THROTTLE_KEY = "alphavantage";
    private static final String UA           = "reference-data-service/1.0";

    private final RefDataHttpClient http;
    private final RefDataProviderProperties keys;

    public AlphaVantageSearchProvider(RefDataHttpClient http, RefDataProviderProperties keys) {
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
        return searchAv(query, limit);
    }

    @Override
    public List<SymbolSearchResultDto> search(String query, AssetClass assetClass, int limit) {
        if (assetClass != AssetClass.STOCK) return List.of();
        return searchAv(query, limit);
    }

    @Override
    public Optional<SymbolSearchResultDto> lookup(String symbol, AssetClass assetClass) {
        if (assetClass != null && assetClass != AssetClass.STOCK) return Optional.empty();
        return searchAv(symbol, 5).stream()
                .filter(r -> r.getSymbol().equalsIgnoreCase(symbol))
                .findFirst();
    }

    @Override
    public List<SymbolSearchResultDto> listAll(AssetClass assetClass, int limit, int offset) {
        // AV SYMBOL_SEARCH requires a query string; listing all is not supported.
        return List.of();
    }

    @Override
    public List<SymbolSearchResultDto> getTopSymbols(AssetClass assetClass, int limit) {
        // Search for broad terms as a proxy for popular symbols
        if (assetClass != AssetClass.STOCK) return List.of();
        return searchAv("a", limit);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<SymbolSearchResultDto> searchAv(String query, int limit) {
        if (!keys.hasAlphavantageKey() || query == null || query.isBlank()) return List.of();

        String url = BASE_URL
                + "?function=SYMBOL_SEARCH"
                + "&keywords=" + encode(query)
                + "&apikey=" + keys.getAlphavantageKey();

        return http.getJson(url, UA, THROTTLE_KEY).map(root -> {
            List<SymbolSearchResultDto> results = new ArrayList<>();
            JsonElement matchesEl = root.get("bestMatches");
            if (matchesEl == null || !matchesEl.isJsonArray()) return results;

            JsonArray matches = matchesEl.getAsJsonArray();
            for (JsonElement el : matches) {
                if (!el.isJsonObject()) continue;
                JsonObject item = el.getAsJsonObject();

                String symbol      = JsonUtil.str(item, "1. symbol");
                String name        = JsonUtil.str(item, "2. name");
                String type        = JsonUtil.str(item, "3. type");
                String region      = JsonUtil.str(item, "4. region");
                String currency    = JsonUtil.str(item, "8. currency");
                String matchScore  = JsonUtil.str(item, "9. matchScore");

                if (symbol == null) continue;

                Double relevance = null;
                if (matchScore != null) {
                    try { relevance = Double.parseDouble(matchScore); }
                    catch (NumberFormatException ignored) { /* keep null */ }
                }

                InstrumentType instrType = mapInstrumentType(type);
                AssetClass assetCls      = instrType != null
                        ? instrType.toAssetClass()
                        : AssetClass.STOCK;
                String country           = mapRegionToCountry(region);

                results.add(SymbolSearchResultDto.builder()
                        .symbol(symbol)
                        .displayName(name)
                        .assetClass(assetCls)
                        .instrumentType(instrType)
                        .quoteCurrency(currency)
                        .country(country)
                        .providerSymbol(symbol)
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

    // ── Mapping helpers ───────────────────────────────────────────────────────

    /**
     * Map AV "type" field to InstrumentType.
     * Known AV type values: "Equity", "ETF", "Mutual Fund", "Index", "Forex",
     * "Cryptocurrency", "Physical Currency", "Economic Indicator".
     */
    private static InstrumentType mapInstrumentType(String avType) {
        if (avType == null) return InstrumentType.EQUITY;
        return switch (avType.toLowerCase()) {
            case "equity"                  -> InstrumentType.EQUITY;
            case "etf", "index"            -> InstrumentType.ETF_INDEX;
            case "forex",
                 "physical currency"       -> InstrumentType.FOREX;
            case "cryptocurrency"          -> InstrumentType.CRYPTO_SPOT;
            default                        -> InstrumentType.EQUITY;
        };
    }

    /**
     * Derive a 2-letter ISO country code from AV's verbose region string.
     */
    private static String mapRegionToCountry(String region) {
        if (region == null) return null;
        return switch (region) {
            case "United States"    -> "US";
            case "United Kingdom"   -> "GB";
            case "Germany"          -> "DE";
            case "France"           -> "FR";
            case "Japan"            -> "JP";
            case "Canada"           -> "CA";
            case "Australia"        -> "AU";
            case "China"            -> "CN";
            case "Hong Kong"        -> "HK";
            case "India"            -> "IN";
            case "Brazil"           -> "BR";
            default                 -> null;
        };
    }

    /** URL-encode a query string (simple space → %20 only; AV accepts this). */
    private static String encode(String s) {
        return s.replace(" ", "%20");
    }
}
