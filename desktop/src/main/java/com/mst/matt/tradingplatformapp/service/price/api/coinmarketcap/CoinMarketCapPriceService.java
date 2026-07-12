package com.mst.matt.tradingplatformapp.service.price.api.coinmarketcap;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import com.mst.matt.tradingplatformapp.service.price.HttpJsonClient;
import com.mst.matt.tradingplatformapp.service.price.MarketDataProvider;
import com.mst.matt.tradingplatformapp.service.price.PriceQuote;
import com.mst.matt.tradingplatformapp.service.price.PriceService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CoinMarketCap price service implementing {@link PriceService}.
 *
 * <p>Handles quote and OHLCV fetching via the free-tier CoinMarketCap Pro API.
 *
 * <h3>Free-tier endpoints used:</h3>
 * <ul>
 *   <li>{@code GET /v3/cryptocurrency/quotes/latest} — real-time quotes</li>
 *   <li>{@code GET /v1/cryptocurrency/map}           — symbol-to-ID mapping</li>
 *   <li>{@code GET /v2/cryptocurrency/ohlcv/historical} — OHLCV candles</li>
 *   <li>{@code GET /v2/cryptocurrency/ohlcv/latest}     — latest OHLCV</li>
 *   <li>{@code GET /v1/k-line/candles}               — DEX K-line candles (keyless)</li>
 * </ul>
 *
 * <h3>Authentication:</h3>
 * API key is passed via {@code X-CMC_PRO_API_KEY} header (preferred method).
 *
 * <h3>Rate limiting:</h3>
 * Free tier: 30 requests/minute. Shared throttle key {@code "coinmarketcap"}.
 */
@Service
public class CoinMarketCapPriceService implements PriceService {

    private static final Logger log = LoggerFactory.getLogger(CoinMarketCapPriceService.class);

    static final String BASE_URL        = "https://pro-api.coinmarketcap.com";
    static final String PUBLIC_BASE_URL = "https://pro-api.coinmarketcap.com/public-api";
    static final String THROTTLE_KEY    = "coinmarketcap";
    static final String CMC_KEY_HEADER  = "X-CMC_PRO_API_KEY";

    // Cache TTLs
    private static final long QUOTE_TTL_MS   = 2  * 60_000L;    // 2 min
    private static final long MAP_TTL_MS     = 24 * 3600_000L;  // 24 hours
    private static final long OHLCV_TTL_MS   = 5  * 60_000L;    // 5 min

    private final HttpJsonClient http;
    private final MarketApiProperties  keys;
    private final Gson                 gson = new Gson();

    // In-memory caches
    private final Map<String, CacheEntry<PriceQuote>>       quoteCache  = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<Integer>>           symbolIdMap = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<OhlcvBar>>>    ohlcvCache  = new ConcurrentHashMap<>();

    public CoinMarketCapPriceService(HttpJsonClient http, MarketApiProperties keys) {
        this.http = http;
        this.keys = keys;
    }

    @PostConstruct
    void registerThrottle() {
        // CMC free tier: 30 requests/minute
        http.throttle(THROTTLE_KEY, 30, Duration.ofMinutes(1));
    }

    @Override
    public boolean isEnabled() {
        return keys.hasCoinmarketcapKey();
    }

    @Override
    public boolean supports(String symbol) {
        // CoinMarketCap primarily handles crypto; support anything that looks crypto-ish
        return isEnabled() && symbol != null && !symbol.isBlank();
    }

    @Override
    public String getProviderName() { return "CoinMarketCap"; }

    @Override
    public MarketDataProvider getProviderId() { return MarketDataProvider.COINMARKETCAP; }

    // ─── Quote ────────────────────────────────────────────────────────────────

    @Override
    public Optional<PriceQuote> getQuote(String symbol) {
        if (!isEnabled()) return Optional.empty();
        String sym = cleanSymbol(symbol);

        CacheEntry<PriceQuote> cached = quoteCache.get(sym);
        if (cached != null && !cached.isExpired()) return Optional.ofNullable(cached.value);

        // Use quotes/latest endpoint with symbol
        String url = BASE_URL + "/v3/cryptocurrency/quotes/latest?symbol=" + sym + "&convert=USD";
        return http.getJson(url, null, THROTTLE_KEY, cmcHeaders())
                .flatMap(root -> parseQuoteFromJson(root, sym))
                .map(quote -> {
                    quoteCache.put(sym, new CacheEntry<>(quote, QUOTE_TTL_MS));
                    return quote;
                });
    }

    // ─── OHLCV ────────────────────────────────────────────────────────────────

    @Override
    public List<OhlcvBar> getOhlcv(String symbol, String timeframe, int limit) {
        if (!isEnabled()) return List.of();
        String sym = cleanSymbol(symbol);
        String cacheKey = sym + "|" + timeframe + "|" + limit;

        CacheEntry<List<OhlcvBar>> cached = ohlcvCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) return cached.value;

        // Get CMC coin ID for the symbol
        Integer coinId = resolveCoinId(sym);
        if (coinId == null) {
            log.debug("CMC: no ID found for symbol {}", sym);
            return List.of();
        }

        List<OhlcvBar> bars = fetchOhlcvHistorical(sym, coinId, timeframe, limit);
        if (!bars.isEmpty()) {
            ohlcvCache.put(cacheKey, new CacheEntry<>(bars, OHLCV_TTL_MS));
        }
        return bars;
    }

    // ─── Internal: resolve symbol → CMC ID ───────────────────────────────────

    /**
     * Resolves a crypto symbol (e.g. "BTC") to a CoinMarketCap numeric ID.
     * Uses /v1/cryptocurrency/map, cached for 24 hours.
     */
    public Integer resolveCoinId(String symbol) {
        String sym = cleanSymbol(symbol);
        CacheEntry<Integer> cached = symbolIdMap.get(sym);
        if (cached != null && !cached.isExpired()) return cached.value;

        String url = BASE_URL + "/v1/cryptocurrency/map?symbol=" + sym + "&listing_status=active";
        Optional<Integer> idOpt = http.getJson(url, null, THROTTLE_KEY, cmcHeaders())
                .flatMap(root -> {
                    JsonArray data = root.has("data") && root.get("data").isJsonArray()
                            ? root.getAsJsonArray("data") : null;
                    if (data == null || data.isEmpty()) return Optional.empty();
                    // Prefer rank=1 or first active entry
                    for (JsonElement el : data) {
                        if (!el.isJsonObject()) continue;
                        JsonObject obj = el.getAsJsonObject();
                        if (obj.has("is_active") && obj.get("is_active").getAsInt() == 1) {
                            return Optional.of(obj.get("id").getAsInt());
                        }
                    }
                    // Fallback: take first entry
                    JsonObject first = data.get(0).getAsJsonObject();
                    return first.has("id")
                            ? Optional.of(first.get("id").getAsInt())
                            : Optional.empty();
                });

        idOpt.ifPresent(id -> symbolIdMap.put(sym, new CacheEntry<>(id, MAP_TTL_MS)));
        return idOpt.orElse(null);
    }

    // ─── Internal: fetch historical OHLCV ────────────────────────────────────

    private List<OhlcvBar> fetchOhlcvHistorical(String symbol, int coinId,
                                                  String timeframe, int limit) {
        String interval = mapTimeframe(timeframe);
        LocalDateTime endDt = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime startDt = endDt.minus(estimateDuration(limit, timeframe));

        DateTimeFormatter fmt = DateTimeFormatter.ISO_DATE_TIME;
        String timeStart = startDt.format(fmt);
        String timeEnd   = endDt.format(fmt);

        String url = BASE_URL + "/v2/cryptocurrency/ohlcv/historical"
                + "?id=" + coinId
                + "&convert=USD"
                + "&time_start=" + urlEnc(timeStart)
                + "&time_end="   + urlEnc(timeEnd)
                + "&interval="   + interval
                + "&count="      + Math.min(limit + 5, 500);

        return http.getJson(url, null, THROTTLE_KEY, cmcHeaders())
                .map(root -> parseOhlcvHistorical(root, symbol, timeframe, limit))
                .orElse(List.of());
    }

    private List<OhlcvBar> parseOhlcvHistorical(JsonObject root, String symbol,
                                                  String timeframe, int limit) {
        if (!root.has("data") || root.get("data").isJsonNull()) return List.of();
        JsonObject dataObj = root.getAsJsonObject("data");
        if (!dataObj.has("quotes") || !dataObj.get("quotes").isJsonArray()) return List.of();

        JsonArray quotes = dataObj.getAsJsonArray("quotes");
        List<OhlcvBar> bars = new ArrayList<>();
        int start = Math.max(0, quotes.size() - limit);

        for (int i = start; i < quotes.size(); i++) {
            JsonElement el = quotes.get(i);
            if (!el.isJsonObject()) continue;
            JsonObject entry = el.getAsJsonObject();

            String timeOpen = entry.has("time_open") ? entry.get("time_open").getAsString() : null;
            if (timeOpen == null) continue;

            JsonObject usdQuote = getNestedQuote(entry);
            if (usdQuote == null) continue;

            OhlcvBar bar = buildOhlcvBar(symbol, timeframe, timeOpen, usdQuote);
            if (bar != null) bars.add(bar);
        }
        return bars;
    }

    private OhlcvBar buildOhlcvBar(String symbol, String timeframe,
                                    String timeOpen, JsonObject usdQuote) {
        try {
            LocalDateTime openTime = parseDateTime(timeOpen);
            return OhlcvBar.builder()
                    .symbol(symbol)
                    .timeframe(timeframe)
                    .openTime(openTime)
                    .open(getDecimal(usdQuote, "open"))
                    .high(getDecimal(usdQuote, "high"))
                    .low(getDecimal(usdQuote, "low"))
                    .close(getDecimal(usdQuote, "close"))
                    .volume(getDecimal(usdQuote, "volume"))
                    .assetType(AssetType.CRYPTO)
                    .build();
        } catch (Exception e) {
            log.debug("CMC: failed to build OhlcvBar: {}", e.getMessage());
            return null;
        }
    }

    // ─── Internal: parse quote from /quotes/latest ───────────────────────────

    private Optional<PriceQuote> parseQuoteFromJson(JsonObject root, String symbol) {
        if (!root.has("data")) return Optional.empty();
        JsonObject data = root.getAsJsonObject("data");

        // Data is keyed by symbol
        String upperSym = symbol.toUpperCase();
        if (!data.has(upperSym)) {
            // Try first key in data
            if (data.size() == 0) return Optional.empty();
            String firstKey = data.keySet().iterator().next();
            if (firstKey == null) return Optional.empty();
            upperSym = firstKey;
        }

        JsonElement coinEl = data.get(upperSym);
        // CMC quotes/latest v3 returns an array per symbol
        JsonObject coinObj = null;
        if (coinEl.isJsonArray()) {
            JsonArray arr = coinEl.getAsJsonArray();
            if (arr.isEmpty()) return Optional.empty();
            coinObj = arr.get(0).getAsJsonObject();
        } else if (coinEl.isJsonObject()) {
            coinObj = coinEl.getAsJsonObject();
        } else {
            return Optional.empty();
        }

        JsonObject usdQuote = getNestedQuote(coinObj);
        if (usdQuote == null) return Optional.empty();

        try {
            String name = coinObj.has("name") ? coinObj.get("name").getAsString() : symbol;
            double price = usdQuote.has("price") ? usdQuote.get("price").getAsDouble() : 0;
            if (price <= 0) return Optional.empty();

            double pctChange = usdQuote.has("percent_change_24h")
                    ? usdQuote.get("percent_change_24h").getAsDouble() : 0;
            double volume = usdQuote.has("volume_24h")
                    ? usdQuote.get("volume_24h").getAsDouble() : 0;
            double marketCap = usdQuote.has("market_cap")
                    ? usdQuote.get("market_cap").getAsDouble() : 0;

            PriceQuote quote = PriceQuote.builder()
                    .symbol(symbol)
                    .assetName(name)
                    .assetType(AssetType.CRYPTO)
                    .price(BigDecimal.valueOf(price))
                    .changePct24h(BigDecimal.valueOf(pctChange))
                    .volume24h(BigDecimal.valueOf(volume))
                    .marketCap(BigDecimal.valueOf(marketCap))
                    .currency("USD")
                    .timestamp(LocalDateTime.now(ZoneOffset.UTC))
                    .isUp(pctChange >= 0)
                    .build();
            return Optional.of(quote);
        } catch (Exception e) {
            log.debug("CMC: quote parse error for {}: {}", symbol, e.getMessage());
            return Optional.empty();
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Returns the CMC API key as the required header map. */
    public Map<String, String> cmcHeaders() {
        return Map.of(CMC_KEY_HEADER, keys.getCoinmarketcapKey());
    }

    /** Returns headers for keyless endpoints (public-api prefix, no API key needed). */
    public Map<String, String> noAuthHeaders() {
        return Map.of();
    }

    private static String cleanSymbol(String symbol) {
        if (symbol == null) return "";
        // Strip common quote currencies (BTCUSDT → BTC)
        String s = symbol.toUpperCase().trim();
        for (String suffix : new String[]{"USDT", "USDC", "BUSD", "USD", "BTC", "ETH", "BNB"}) {
            if (s.endsWith(suffix) && s.length() > suffix.length()) {
                return s.substring(0, s.length() - suffix.length());
            }
        }
        return s;
    }

    private static String mapTimeframe(String tf) {
        return switch (tf.toLowerCase()) {
            case "1m"  -> "1min";
            case "5m"  -> "5min";
            case "15m" -> "15min";
            case "30m" -> "30min";
            case "1h"  -> "1h";
            case "2h"  -> "2h";
            case "4h"  -> "4h";
            case "6h"  -> "6h";
            case "8h"  -> "8h";
            case "12h" -> "12h";
            case "1d"  -> "daily";
            case "3d"  -> "3d";
            case "1w"  -> "weekly";
            case "1mo" -> "monthly";
            default    -> "daily";
        };
    }

    private static Duration estimateDuration(int limit, String tf) {
        long barsToSeconds = switch (tf.toLowerCase()) {
            case "1m"  -> 60L;
            case "5m"  -> 300L;
            case "15m" -> 900L;
            case "30m" -> 1800L;
            case "1h"  -> 3600L;
            case "2h"  -> 7200L;
            case "4h"  -> 14400L;
            case "6h"  -> 21600L;
            case "12h" -> 43200L;
            case "1w"  -> 604800L;
            case "1mo" -> 2592000L;
            default    -> 86400L; // 1d
        };
        return Duration.ofSeconds(barsToSeconds * (limit + 5));
    }

    private static JsonObject getNestedQuote(JsonObject entry) {
        if (!entry.has("quote") || entry.get("quote").isJsonNull()) return null;
        JsonObject q = entry.getAsJsonObject("quote");
        return q.has("USD") && q.get("USD").isJsonObject() ? q.getAsJsonObject("USD") : null;
    }

    private static BigDecimal getDecimal(JsonObject obj, String field) {
        if (!obj.has(field) || obj.get(field).isJsonNull()) return BigDecimal.ZERO;
        try { return BigDecimal.valueOf(obj.get(field).getAsDouble()); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    private static LocalDateTime parseDateTime(String iso) {
        try {
            return LocalDateTime.parse(iso, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            try {
                return LocalDateTime.ofInstant(Instant.parse(iso), ZoneOffset.UTC);
            } catch (Exception e2) {
                return LocalDateTime.now(ZoneOffset.UTC);
            }
        }
    }

    private static String urlEnc(String s) {
        if (s == null) return "";
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    // ─── Cache helper ──────────────────────────────────────────────────────────

    static final class CacheEntry<T> {
        final T    value;
        final long expiresAt;

        CacheEntry(T value, long ttlMs) {
            this.value     = value;
            this.expiresAt = System.currentTimeMillis() + ttlMs;
        }

        boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
    }
}
