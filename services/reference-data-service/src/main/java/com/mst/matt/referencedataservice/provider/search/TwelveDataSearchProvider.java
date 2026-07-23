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
 * Twelve Data implementation of {@link SymbolSearchProvider}.
 *
 * <h3>Endpoint</h3>
 * <pre>GET https://api.twelvedata.com/symbol_search?symbol={query}&apikey={key}</pre>
 *
 * <p>Response shape:</p>
 * <pre>{@code
 * {
 *   "data": [
 *     {
 *       "symbol":         "AAPL",
 *       "instrument_name": "Apple Inc",
 *       "exchange":       "NASDAQ",
 *       "mic_code":       "XNAS",
 *       "exchange_timezone": "America/New_York",
 *       "instrument_type": "Common Stock",
 *       "country":        "United States",
 *       "currency":       "USD"
 *     }
 *   ],
 *   "status": "ok"
 * }
 * }</pre>
 *
 * <p>Twelve Data free tier allows ~8 requests/min; we register at 7/min to stay safe.
 * Covers stocks, forex, crypto, ETFs — all AssetClasses.</p>
 */
@Component
public class TwelveDataSearchProvider implements SymbolSearchProvider {

    private static final Logger log = LoggerFactory.getLogger(TwelveDataSearchProvider.class);

    public static final String PROVIDER_NAME = "TWELVE_DATA";
    private static final String BASE_URL     = "https://api.twelvedata.com";
    private static final String THROTTLE_KEY = "twelvedata_ref";
    private static final String UA           = "reference-data-service/1.0";

    private final RefDataHttpClient http;
    private final RefDataProviderProperties keys;

    public TwelveDataSearchProvider(RefDataHttpClient http, RefDataProviderProperties keys) {
        this.http  = http;
        this.keys  = keys;
    }

    @PostConstruct
    public void registerThrottle() {
        http.throttle(THROTTLE_KEY, 7, Duration.ofMinutes(1));
    }

    @Override public String providerName() { return PROVIDER_NAME; }

    @Override
    public List<AssetClass> supportedAssetClasses() {
        return List.of(AssetClass.STOCK, AssetClass.FOREX, AssetClass.CRYPTO);
    }

    // ── SymbolSearchProvider ──────────────────────────────────────────────────

    @Override
    public List<SymbolSearchResultDto> search(String query, int limit) {
        return searchTwelveData(query, null, limit);
    }

    @Override
    public List<SymbolSearchResultDto> search(String query, AssetClass assetClass, int limit) {
        return searchTwelveData(query, assetClass, limit);
    }

    @Override
    public Optional<SymbolSearchResultDto> lookup(String symbol, AssetClass assetClass) {
        return searchTwelveData(symbol, assetClass, 10).stream()
                .filter(r -> r.getSymbol().equalsIgnoreCase(symbol))
                .findFirst();
    }

    @Override
    public List<SymbolSearchResultDto> listAll(AssetClass assetClass, int limit, int offset) {
        // TwelveData /symbol_search requires a keyword — bulk listing not supported.
        return List.of();
    }

    @Override
    public List<SymbolSearchResultDto> getTopSymbols(AssetClass assetClass, int limit) {
        // Use a popular symbol prefix as a proxy
        return searchTwelveData("a", assetClass, limit);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<SymbolSearchResultDto> searchTwelveData(String query, AssetClass assetClass,
                                                          int limit) {
        if (!keys.hasTwelvedataKey() || query == null || query.isBlank()) return List.of();

        StringBuilder url = new StringBuilder(BASE_URL)
                .append("/symbol_search")
                .append("?symbol=").append(encode(query))
                .append("&outputsize=").append(Math.min(limit * 2, 120))   // request 2× for filtering
                .append("&apikey=").append(keys.getTwelvedataKey());

        // Twelve Data supports type-based filtering via &type=
        if (assetClass != null) {
            String tdType = assetClassToTdType(assetClass);
            if (tdType != null) url.append("&type=").append(tdType);
        }

        return http.getJson(url.toString(), UA, THROTTLE_KEY).map(root -> {
            List<SymbolSearchResultDto> results = new ArrayList<>();
            JsonElement dataEl = root.get("data");
            if (dataEl == null || !dataEl.isJsonArray()) return results;

            JsonArray arr = dataEl.getAsJsonArray();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject item = el.getAsJsonObject();

                String symbol       = JsonUtil.str(item, "symbol");
                String name         = JsonUtil.str(item, "instrument_name");
                String exchange     = JsonUtil.str(item, "exchange");
                String instrTypeStr = JsonUtil.str(item, "instrument_type");
                String country      = JsonUtil.str(item, "country");
                String currency     = JsonUtil.str(item, "currency");

                if (symbol == null) continue;

                InstrumentType instrType = mapInstrumentType(instrTypeStr);
                AssetClass assetCls      = instrType != null
                        ? instrType.toAssetClass()
                        : AssetClass.STOCK;

                // Skip if asset class filter doesn't match
                if (assetClass != null && assetCls != assetClass) continue;

                results.add(SymbolSearchResultDto.builder()
                        .symbol(symbol)
                        .displayName(name)
                        .assetClass(assetCls)
                        .instrumentType(instrType)
                        .exchange(exchange)
                        .quoteCurrency(currency)
                        .country(mapCountry(country))
                        .providerSymbol(symbol)
                        .providerName(PROVIDER_NAME)
                        .relevanceScore(null)   // TwelveData does not return relevance scores
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
     * Map TwelveData instrument_type → InstrumentType.
     * Known TD types: "Common Stock", "ETF", "Index", "Forex Pair",
     * "Digital Currency", "Mutual Fund", "Bond/Note", "Commodity".
     */
    private static InstrumentType mapInstrumentType(String tdType) {
        if (tdType == null) return InstrumentType.EQUITY;
        String t = tdType.toLowerCase();
        if (t.contains("etf") || t.contains("index") || t.contains("fund")) return InstrumentType.ETF_INDEX;
        if (t.contains("forex") || t.contains("currency pair"))              return InstrumentType.FOREX;
        if (t.contains("digital") || t.contains("crypto"))                  return InstrumentType.CRYPTO_SPOT;
        if (t.contains("commodity"))                                         return InstrumentType.COMMODITY;
        return InstrumentType.EQUITY;
    }

    /** Map platform AssetClass → TwelveData type query param. */
    private static String assetClassToTdType(AssetClass ac) {
        return switch (ac) {
            case STOCK  -> "Common Stock";
            case FOREX  -> "Forex Pair";
            case CRYPTO -> "Digital Currency";
            default     -> null;
        };
    }

    /** Normalize TwelveData verbose country → ISO-3166 alpha-2. */
    private static String mapCountry(String country) {
        if (country == null) return null;
        return switch (country) {
            case "United States" -> "US";
            case "United Kingdom" -> "GB";
            case "Germany" -> "DE";
            case "France" -> "FR";
            case "Japan" -> "JP";
            case "Canada" -> "CA";
            case "Australia" -> "AU";
            case "China" -> "CN";
            default -> null;
        };
    }

    private static String encode(String s) {
        return s.replace(" ", "%20");
    }
}
