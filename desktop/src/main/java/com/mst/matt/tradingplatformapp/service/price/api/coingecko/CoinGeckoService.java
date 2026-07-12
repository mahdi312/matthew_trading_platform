package com.mst.matt.tradingplatformapp.service.price.api.coingecko;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade.AssetType;
import com.mst.matt.tradingplatformapp.service.price.MarketDataProvider;
import com.mst.matt.tradingplatformapp.service.price.PriceQuote;
import com.mst.matt.tradingplatformapp.service.price.PriceService;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;

/**
 * CoinGecko service — broader crypto coverage than Binance alone.
 *
 * Free demo API (no credit card, just register for key).
 * Endpoints used (Free / Demo tier):
 *   /simple/price                    → current price + 24h change
 *   /coins/{id}/ohlc                 → OHLCV with day-based granularity
 *   /coins/{id}/market_chart         → price/volume time-series → synthesised OHLCV
 *   /coins/{id}/market_chart/range   → same but with unix-sec from/to
 *   /coins/markets                   → full market data for multiple coins
 *   /coins/list                      → full coin catalogue (daily sync to DB via CoinGeckoSearchService)
 *   /global                          → global market stats
 *   /global/decentralized_finance_defi → DeFi market stats
 *   /search, /search/trending        → coin search & trending
 *   /exchange_rates                  → fiat/crypto exchange rates
 *   /onchain/networks/{n}/pools/{a}/ohlcv/{tf} → DEX pool OHLCV
 *   /onchain/networks/{n}/pools      → top pools
 *   /onchain/networks/{n}/tokens/{a}/pools → pools for a token
 *   /nfts/list, /nfts/{id}           → NFT collections
 *
 * Rate limiting: all HTTP calls go through {@link CoinGeckoRateLimiter} which
 * enforces the 30 calls/min free-tier limit and honours Retry-After headers.
 */
@Service
public class CoinGeckoService implements PriceService {

    private static final Logger log = LoggerFactory.getLogger(CoinGeckoService.class);

    @Value("${api.coingecko.base-url:https://api.coingecko.com/api/v3}")
    private String baseUrl;

    @Value("${api.coingecko-key:}")
    private String apiKey;

    private final OkHttpClient httpClient;
    private final CoinGeckoRateLimiter rateLimiter;
    private final Gson gson = new Gson();

    /** DB-backed symbol→coinId lookup (injected lazily to avoid circular dependency). */
    private Function<String, String> coinIdLookup = s -> null;

    // Map from trading symbols to CoinGecko coin IDs
    private static final Map<String, String> SYMBOL_TO_ID = Map.ofEntries(
            Map.entry("BTC",  "bitcoin"),
            Map.entry("ETH",  "ethereum"),
            Map.entry("BNB",  "binancecoin"),
            Map.entry("SOL",  "solana"),
            Map.entry("ADA",  "cardano"),
            Map.entry("XRP",  "ripple"),
            Map.entry("DOT",  "polkadot"),
            Map.entry("DOGE", "dogecoin"),
            Map.entry("AVAX", "avalanche-2"),
            Map.entry("MATIC","matic-network"),
            Map.entry("LINK", "chainlink"),
            Map.entry("LTC",  "litecoin"),
            Map.entry("UNI",  "uniswap"),
            Map.entry("ATOM", "cosmos"),
            Map.entry("XLM",  "stellar"),
            Map.entry("NEAR", "near"),
            Map.entry("ALGO", "algorand"),
            Map.entry("VET",  "vechain"),
            Map.entry("FIL",  "filecoin"),
            Map.entry("TRX",  "tron")
    );

    public CoinGeckoService(@Autowired @Qualifier("priceHttpClient") OkHttpClient httpClient,
                             @Autowired CoinGeckoRateLimiter rateLimiter) {
        this.httpClient = httpClient;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Wires the DB-backed coin-ID resolver from {@link CoinGeckoSearchService}.
     * Lazy injection breaks the CoinGeckoService ↔ CoinGeckoSearchService cycle.
     */
    @Autowired
    void setCoinIdLookup(@Lazy CoinGeckoSearchService searchService) {
        this.coinIdLookup = searchService::lookupCoinIdFromDb;
    }

    @Override
    public Optional<PriceQuote> getQuote(String symbol) {
        String coinId = toCoinId(symbol);
        if (coinId == null) return Optional.empty();
        String display = symbol.toUpperCase();

        Optional<PriceQuote> fromMarkets = fetchFromMarkets(coinId, display);
        if (fromMarkets.isPresent()) return fromMarkets;
        return fetchFromSimplePrice(coinId, display);
    }

    private Optional<PriceQuote> fetchFromMarkets(String coinId, String display) {
        String url = baseUrl + "/coins/markets"
                + "?vs_currency=usd"
                + "&ids=" + coinId
                + "&order=market_cap_desc"
                + "&sparkline=false"
                + "&price_change_percentage=24h";
        rateLimiter.acquire();
        try (Response response = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(response);
            if (!response.isSuccessful() || response.body() == null) return Optional.empty();
            JsonArray arr = gson.fromJson(response.body().string(), JsonArray.class);
            if (arr == null || arr.isEmpty()) return Optional.empty();
            return CoinGeckoMarketCoin.fromJson(arr.get(0).getAsJsonObject())
                    .map(c -> c.toPriceQuote(display));
        } catch (IOException e) {
            log.warn("CoinGecko markets error for {}: {}", display, e.getMessage());
            return Optional.empty();
        }
    }

    /** Lighter endpoint from report.html when markets is rate-limited. */
    private Optional<PriceQuote> fetchFromSimplePrice(String coinId, String display) {
        String url = baseUrl + "/simple/price?ids=" + coinId
                + "&vs_currencies=usd&include_24hr_change=true&include_market_cap=true";
        rateLimiter.acquire();
        try (Response response = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(response);
            if (!response.isSuccessful() || response.body() == null) return Optional.empty();
            JsonObject root = gson.fromJson(response.body().string(), JsonObject.class);
            if (root == null || !root.has(coinId)) return Optional.empty();
            return CoinGeckoSimplePrice.fromCoinNode(root.getAsJsonObject(coinId))
                    .map(p -> p.toPriceQuote(display, display));
        } catch (IOException e) {
            log.error("CoinGecko error for {}: {}", display, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<OhlcvBar> getOhlcv(String symbol, String timeframe, int limit) {
        String coinId = toCoinId(symbol);
        if (coinId == null) return Collections.emptyList();

        // Sub-daily timeframes: synthesise candles from /market_chart (finer granularity).
        String tf = timeframe == null ? "" : timeframe.toLowerCase();
        if (List.of("1m", "5m", "15m", "30m", "1h", "4h").contains(tf)) {
            List<OhlcvBar> fromChart = getOhlcvFromMarketChart(symbol, timeframe, limit);
            if (!fromChart.isEmpty()) return fromChart;
        }

        // Daily+ timeframes: use /ohlc (supported days: 1,7,14,30,90,180,365,max)
        int days = toDays(timeframe, limit);
        String url = baseUrl + "/coins/" + coinId
                + "/ohlc?vs_currency=usd&days=" + days;

        Request request = buildRequest(url);
        List<OhlcvBar> bars = new ArrayList<>();

        rateLimiter.acquire();
        try (Response response = httpClient.newCall(request).execute()) {
            handleRateLimitHeaders(response);
            if (!response.isSuccessful() || response.body() == null) return bars;

            JsonArray arr = gson.fromJson(response.body().string(), JsonArray.class);

            for (JsonElement el : arr) {
                JsonArray row = el.getAsJsonArray();
                long ts = row.get(0).getAsLong();

                OhlcvBar bar = OhlcvBar.builder()
                        .symbol(symbol.toUpperCase())
                        .timeframe(timeframe)
                        .openTime(LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(ts), ZoneOffset.UTC))
                        .open(new BigDecimal(row.get(1).getAsString()))
                        .high(new BigDecimal(row.get(2).getAsString()))
                        .low(new BigDecimal(row.get(3).getAsString()))
                        .close(new BigDecimal(row.get(4).getAsString()))
                        .volume(BigDecimal.ZERO) // CoinGecko OHLC has no volume column
                        .assetType(AssetType.CRYPTO)
                        .build();

                bars.add(bar);
            }

        } catch (IOException e) {
            log.error("CoinGecko OHLCV error for {}: {}", symbol, e.getMessage());
        }

        // Trim to requested limit
        if (bars.size() > limit)
            bars = bars.subList(bars.size() - limit, bars.size());

        return bars;
    }

    @Override
    public boolean supports(String symbol) {
        return toCoinId(symbol.toUpperCase()) != null
                || toCoinId(stripSuffix(symbol.toUpperCase())) != null;
    }

    @Override
    public String getProviderName() { return "CoinGecko"; }

    @Override
    public MarketDataProvider getProviderId() { return MarketDataProvider.COINGECKO; }

    // ── OHLCV helpers ─────────────────────────────────────────────────────

    /**
     * Fetches OHLCV bars using the {@code /coins/{id}/market_chart} endpoint.
     * CoinGecko's market_chart returns prices, market_caps, and total_volumes
     * as parallel time-series.  We synthesise OHLCV bars by grouping price
     * ticks into candles matching the requested {@code timeframe}.
     *
     * @param symbol    trading symbol (e.g. "BTCUSDT", "BTC")
     * @param timeframe e.g. "1h", "4h", "1d"
     * @param limit     maximum number of bars to return
     * @return list of synthesised OHLCV bars (may be empty on error)
     */
    public List<OhlcvBar> getOhlcvFromMarketChart(String symbol, String timeframe, int limit) {
        String coinId = toCoinId(symbol);
        if (coinId == null) return Collections.emptyList();

        int days  = toDays(timeframe, limit);
        String dayStr = String.valueOf(Math.min(days, 365));
        // Choose interval hint: CoinGecko auto-selects hourly for <=90 days, daily for >90
        String interval = days <= 1 ? "minutely" : (days <= 90 ? "hourly" : "daily");

        Optional<JsonObject> chartOpt = getCoinMarketChart(coinId, "usd", dayStr, interval);
        if (chartOpt.isEmpty()) return Collections.emptyList();

        return parseMarketChartToOhlcv(chartOpt.get(), symbol.toUpperCase(), timeframe, limit);
    }

    /**
     * Fetches OHLCV bars for a custom date range using
     * {@code /coins/{id}/market_chart/range}.
     *
     * @param symbol    trading symbol
     * @param timeframe target timeframe for synthesised candles
     * @param fromEpoch unix epoch seconds (start)
     * @param toEpoch   unix epoch seconds (end)
     * @return synthesised OHLCV bars
     */
    public List<OhlcvBar> getOhlcvRange(String symbol, String timeframe, long fromEpoch, long toEpoch) {
        String coinId = toCoinId(symbol);
        if (coinId == null) return Collections.emptyList();

        Optional<JsonObject> chartOpt = getCoinMarketChartRange(coinId, "usd", fromEpoch, toEpoch);
        if (chartOpt.isEmpty()) return Collections.emptyList();

        return parseMarketChartToOhlcv(chartOpt.get(), symbol.toUpperCase(), timeframe, Integer.MAX_VALUE);
    }

    /**
     * Parses a CoinGecko market_chart JSON response into {@link OhlcvBar} list.
     * Price ticks are grouped into OHLCV candles based on the timeframe bucket.
     */
    private List<OhlcvBar> parseMarketChartToOhlcv(JsonObject chart, String symbol,
                                                    String timeframe, int limit) {
        if (!chart.has("prices")) return Collections.emptyList();

        JsonArray prices  = chart.getAsJsonArray("prices");
        JsonArray volumes = chart.has("total_volumes")
                ? chart.getAsJsonArray("total_volumes") : new JsonArray();

        long bucketMs = timeframeToBucketMs(timeframe);
        // Build volume lookup by timestamp bucket
        Map<Long, BigDecimal> volByBucket = new TreeMap<>();
        for (JsonElement ve : volumes) {
            JsonArray row = ve.getAsJsonArray();
            long ts   = row.get(0).getAsLong();
            long bucket = (ts / bucketMs) * bucketMs;
            BigDecimal vol = new BigDecimal(row.get(1).getAsString());
            volByBucket.merge(bucket, vol, BigDecimal::add);
        }

        // Group price ticks into candles
        Map<Long, List<BigDecimal>> pricesByBucket = new TreeMap<>();
        for (JsonElement pe : prices) {
            JsonArray row = pe.getAsJsonArray();
            long ts   = row.get(0).getAsLong();
            long bucket = (ts / bucketMs) * bucketMs;
            BigDecimal price = new BigDecimal(row.get(1).getAsString());
            pricesByBucket.computeIfAbsent(bucket, k -> new ArrayList<>()).add(price);
        }

        List<OhlcvBar> bars = new ArrayList<>(pricesByBucket.size());
        for (Map.Entry<Long, List<BigDecimal>> entry : pricesByBucket.entrySet()) {
            List<BigDecimal> ticks = entry.getValue();
            if (ticks.isEmpty()) continue;
            BigDecimal open  = ticks.getFirst();
            BigDecimal close = ticks.getLast();
            BigDecimal high  = ticks.stream().max(BigDecimal::compareTo).orElse(open);
            BigDecimal low   = ticks.stream().min(BigDecimal::compareTo).orElse(open);
            BigDecimal vol   = volByBucket.getOrDefault(entry.getKey(), BigDecimal.ZERO);

            bars.add(OhlcvBar.builder()
                    .symbol(symbol)
                    .timeframe(timeframe)
                    .openTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(entry.getKey()), ZoneOffset.UTC))
                    .open(open).high(high).low(low).close(close)
                    .volume(vol)
                    .assetType(AssetType.CRYPTO)
                    .build());
        }

        if (bars.size() > limit)
            bars = bars.subList(bars.size() - limit, bars.size());

        return bars;
    }

    /**
     * Returns the millisecond duration of one candle for the given timeframe.
     * Used to bucket market_chart ticks into OHLCV candles.
     */
    private long timeframeToBucketMs(String tf) {
        return switch (tf.toLowerCase()) {
            case "1m"  -> 60_000L;
            case "5m"  -> 5   * 60_000L;
            case "15m" -> 15  * 60_000L;
            case "30m" -> 30  * 60_000L;
            case "1h"  -> 3_600_000L;
            case "4h"  -> 4   * 3_600_000L;
            case "1d"  -> 86_400_000L;
            case "1w"  -> 7   * 86_400_000L;
            default    -> 3_600_000L;
        };
    }

    /**
     * Fetches the complete CoinGecko coins list ({@code /coins/list}).
     * Returns a list of {@link CoinGeckoCoinBasic} POJOs (id, symbol, name).
     * Callers (e.g. {@link CoinGeckoSearchService}) are responsible for caching
     * and daily DB persistence.
     */
    public List<CoinGeckoCoinBasic> fetchCoinsList() {
        String url = baseUrl + "/coins/list?include_platform=false";
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Collections.emptyList();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            if (arr == null) return Collections.emptyList();
            List<CoinGeckoCoinBasic> out = new ArrayList<>(arr.size());
            for (JsonElement el : arr) {
                try {
                    out.add(gson.fromJson(el, CoinGeckoCoinBasic.class));
                } catch (Exception ex) {
                    log.debug("parse coins/list entry failed: {}", ex.getMessage());
                }
            }
            log.info("CoinGecko /coins/list fetched: {} coins", out.size());
            return out;
        } catch (IOException e) {
            log.error("CoinGecko /coins/list error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Resolves a trading symbol (e.g. "BTCUSDT", "BTC", "bitcoin") to a CoinGecko
     * coin ID string (e.g. "bitcoin").  Checks the static map first, then the
     * stripped suffix form.
     *
     * @param symbol raw trading symbol
     * @return CoinGecko coin ID, or {@code null} if not found
     */
    public String resolveCoinId(String symbol) {
        return toCoinId(symbol);
    }

    /**
     * Fast-path resolver using only the static map (~20 major coins).
     * Used by {@link CoinGeckoSearchService} to avoid recursive lookup.
     */
    public String lookupStaticCoinId(String symbol) {
        if (symbol == null || symbol.isBlank()) return null;
        String base = stripSuffix(symbol.toUpperCase());
        return SYMBOL_TO_ID.get(base);
    }

    // ── CoinGecko endpoint wrappers (raw JSON helpers for free endpoints) ──

    // ─── INTERNAL: apply rate limiter before every raw HTTP call ───────────
    private Optional<Response> executeRequest(String url) {
        rateLimiter.acquire();
        try {
            Response r = httpClient.newCall(buildRequest(url)).execute();
            handleRateLimitHeaders(r);
            return Optional.of(r);
        } catch (IOException e) {
            log.warn("CoinGecko HTTP error [{}]: {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    /** Parses Retry-After header from a 429 response and forwards to the rate limiter. */
    private void handleRateLimitHeaders(Response response) {
        if (response.code() == 429) {
            String retryAfter = response.header("Retry-After");
            if (retryAfter != null) {
                try {
                    rateLimiter.recordRetryAfter(Long.parseLong(retryAfter.trim()));
                } catch (NumberFormatException ex) {
                    rateLimiter.recordRetryAfter(60L); // default 60s
                }
            } else {
                rateLimiter.recordRetryAfter(60L);
            }
        }
    }

    // --- Simple ---
    public Optional<JsonObject> getSimplePrice(String idsCsv, String vsCurrency,
                                               boolean includeMarketCap, boolean include24hChange,
                                               boolean includeLastUpdatedAt) {
        String url = baseUrl + "/simple/price?ids=" + urlEncode(idsCsv)
                + "&vs_currencies=" + urlEncode(vsCurrency)
                + "&include_market_cap=" + includeMarketCap
                + "&include_24hr_vol=" + include24hChange
                + "&include_24hr_change=" + include24hChange
                + "&include_last_updated_at=" + includeLastUpdatedAt;
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko getSimplePrice error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public List<String> getSupportedVsCurrencies() {
        String url = baseUrl + "/simple/supported_vs_currencies";
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Collections.emptyList();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            List<String> res = new ArrayList<>();
            if (arr != null) for (JsonElement e : arr) res.add(e.getAsString());
            return res;
        } catch (IOException e) {
            log.error("CoinGecko supported currencies error: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public Optional<JsonObject> getSimpleTokenPrice(String platform, String contractAddressesCsv, String vsCurrencies,
                                                    boolean includeMarketCap, boolean include24hChange,
                                                    boolean includeLastUpdatedAt) {
        String url = baseUrl + "/simple/token_price/" + urlEncode(platform)
                + "?contract_addresses=" + urlEncode(contractAddressesCsv)
                + "&vs_currencies=" + urlEncode(vsCurrencies)
                + "&include_market_cap=" + includeMarketCap
                + "&include_24hr_vol=" + include24hChange
                + "&include_24hr_change=" + include24hChange
                + "&include_last_updated_at=" + includeLastUpdatedAt;
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko token price error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // --- Coins ---
    public Optional<JsonArray> getCoinsMarkets(String vsCurrency, String idsCsv, String order, int perPage, int page,
                                               boolean sparkline, String priceChangePercentage) {
        String url = baseUrl + "/coins/markets?vs_currency=" + urlEncode(vsCurrency)
                + (idsCsv == null || idsCsv.isBlank() ? "" : "&ids=" + urlEncode(idsCsv))
                + "&order=" + urlEncode(order)
                + "&per_page=" + perPage
                + "&page=" + page
                + "&sparkline=" + sparkline
                + (priceChangePercentage == null ? "" : "&price_change_percentage=" + urlEncode(priceChangePercentage));
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            return Optional.ofNullable(arr);
        } catch (IOException e) {
            log.error("CoinGecko coins/markets error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getCoinById(String id, boolean localization, boolean marketData,
                                            boolean communityData, boolean developerData, boolean sparkline) {
        String url = baseUrl + "/coins/" + urlEncode(id)
                + "?localization=" + localization
                + "&market_data=" + marketData
                + "&community_data=" + communityData
                + "&developer_data=" + developerData
                + "&sparkline=" + sparkline;
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko coin by id error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getCoinHistory(String id, String date, boolean localization) {
        String url = baseUrl + "/coins/" + urlEncode(id) + "/history?date=" + urlEncode(date)
                + "&localization=" + localization;
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko coin history error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getCoinMarketChart(String id, String vsCurrency, String days, String interval) {
        String url = baseUrl + "/coins/" + urlEncode(id) + "/market_chart?vs_currency=" + urlEncode(vsCurrency)
                + "&days=" + urlEncode(days)
                + (interval == null ? "" : "&interval=" + urlEncode(interval));
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko market chart error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getCoinMarketChartRange(String id, String vsCurrency, long from, long to) {
        String url = baseUrl + "/coins/" + urlEncode(id) + "/market_chart/range?vs_currency=" + urlEncode(vsCurrency)
                + "&from=" + from + "&to=" + to;
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko market chart range error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getCoinTickers(String id, String exchangeIds, boolean includeExchangeLogo, String order, int page) {
        String url = baseUrl + "/coins/" + urlEncode(id) + "/tickers"
                + (exchangeIds == null || exchangeIds.isBlank() ? "" : "?exchange_ids=" + urlEncode(exchangeIds))
                + "&include_exchange_logo=" + includeExchangeLogo
                + "&page=" + page
                + (order == null ? "" : "&order=" + urlEncode(order));
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko coin tickers error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonArray> getCoinOhlcRange(String id, String vsCurrency, long from, long to) {
        String url = baseUrl + "/coins/" + urlEncode(id) + "/ohlc?vs_currency=" + urlEncode(vsCurrency)
                + "&days=1"; // CoinGecko OHLC uses days param; range OHLC not universally available
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            return Optional.ofNullable(arr);
        } catch (IOException e) {
            log.error("CoinGecko coin ohlc error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // --- Contract ---
    public Optional<JsonObject> getTokenByContract(String platform, String contractAddress,
                                                   boolean localization, boolean marketData,
                                                   boolean communityData, boolean developerData,
                                                   boolean sparkline) {
        String url = baseUrl + "/coins/" + urlEncode(platform) + "/contract/" + urlEncode(contractAddress)
                + "?localization=" + localization
                + "&market_data=" + marketData
                + "&community_data=" + communityData
                + "&developer_data=" + developerData
                + "&sparkline=" + sparkline;
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko contract token error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getTokenMarketChartByContract(String platform, String contractAddress,
                                                              String vsCurrency, String days, String interval) {
        String url = baseUrl + "/coins/" + urlEncode(platform) + "/contract/" + urlEncode(contractAddress) + "/market_chart?vs_currency="
                + urlEncode(vsCurrency) + "&days=" + urlEncode(days) + (interval == null ? "" : "&interval=" + urlEncode(interval));
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko contract market chart error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getTokenMarketChartRangeByContract(String platform, String contractAddress,
                                                                   String vsCurrency, long from, long to) {
        String url = baseUrl + "/coins/" + urlEncode(platform) + "/contract/" + urlEncode(contractAddress) + "/market_chart/range?vs_currency="
                + urlEncode(vsCurrency) + "&from=" + from + "&to=" + to;
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko contract market chart range error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // --- NFT ---
    public Optional<JsonObject> listNftCollections(int perPage, int page, String order) {
        String url = baseUrl + "/nfts/list?per_page=" + perPage + "&page=" + page + (order == null ? "" : "&order=" + urlEncode(order));
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko nfts list error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getNftCollection(String id, boolean localization) {
        String url = baseUrl + "/nfts/" + urlEncode(id) + "?localization=" + localization;
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko nft collection error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getNftMarketChart(String id, String days, String order) {
        String url = baseUrl + "/nfts/" + urlEncode(id) + "/market_chart?days=" + urlEncode(days) + (order == null ? "" : "&order=" + urlEncode(order));
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko nft market chart error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // --- Exchanges ---
    public Optional<JsonArray> listExchanges(int perPage, int page) {
        String url = baseUrl + "/exchanges?per_page=" + perPage + "&page=" + page;
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            return Optional.ofNullable(arr);
        } catch (IOException e) {
            log.error("CoinGecko exchanges list error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getExchangeById(String id) {
        String url = baseUrl + "/exchanges/" + urlEncode(id);
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko exchange error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getExchangeTickers(String id, String coinIds, boolean includeExchangeLogo, String order, int page) {
        String url = baseUrl + "/exchanges/" + urlEncode(id) + "/tickers"
                + (coinIds == null || coinIds.isBlank() ? "" : "?coin_ids=" + urlEncode(coinIds))
                + "&include_exchange_logo=" + includeExchangeLogo
                + "&page=" + page
                + (order == null ? "" : "&order=" + urlEncode(order));
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko exchange tickers error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonArray> getExchangeVolumeChart(String id, String days) {
        String url = baseUrl + "/exchanges/" + urlEncode(id) + "/volume_chart?days=" + urlEncode(days);
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            return Optional.ofNullable(arr);
        } catch (IOException e) {
            log.error("CoinGecko exchange volume error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonArray> getExchangeBtcVolumeChart(String id, String days) {
        String url = baseUrl + "/exchanges/" + urlEncode(id) + "/btc_volume_chart?days=" + urlEncode(days);
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            return Optional.ofNullable(arr);
        } catch (IOException e) {
            log.error("CoinGecko exchange btc volume error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // --- Derivatives ---
    public Optional<JsonArray> listDerivatives(int perPage, int page, String order) {
        String url = baseUrl + "/derivatives?per_page=" + perPage + "&page=" + page + (order == null ? "" : "&order=" + urlEncode(order));
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            return Optional.ofNullable(arr);
        } catch (IOException e) {
            log.error("CoinGecko derivatives list error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonArray> getDerivativeExchanges(int perPage, int page, String order) {
        String url = baseUrl + "/derivatives/exchanges?per_page=" + perPage + "&page=" + page + (order == null ? "" : "&order=" + urlEncode(order));
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            return Optional.ofNullable(arr);
        } catch (IOException e) {
            log.error("CoinGecko derivatives exchanges error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getDerivativeExchangeById(String id) {
        String url = baseUrl + "/derivatives/exchanges/" + urlEncode(id);
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko derivative exchange error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonArray> getDerivativeExchangeVolumeChart(String id, String days) {
        String url = baseUrl + "/derivatives/exchanges/" + urlEncode(id) + "/volume_chart?days=" + urlEncode(days);
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            return Optional.ofNullable(arr);
        } catch (IOException e) {
            log.error("CoinGecko derivative volume error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // --- Treasury & Market-wide ---
    public Optional<JsonObject> getGlobalData() {
        String url = baseUrl + "/global";
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko global error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getGlobalDeFiData() {
        String url = baseUrl + "/global/decentralized_finance_defi";
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko global defi error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonArray> getSearchTrending() {
        return getSearchTrendingFull()
                .filter(obj -> obj.has("coins"))
                .map(obj -> obj.getAsJsonArray("coins"));
    }

    /** Returns the full /search/trending response (coins, nfts, categories). */
    public Optional<JsonObject> getSearchTrendingFull() {
        String url = baseUrl + "/search/trending";
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko trending error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> search(String query) {
        String url = baseUrl + "/search?query=" + urlEncode(query);
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko search error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // --- Onchain / DEX ---
    public Optional<JsonArray> listNetworks() {
        String url = baseUrl + "/onchain/networks";
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            return Optional.ofNullable(arr);
        } catch (IOException e) {
            log.error("CoinGecko onchain networks error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getPoolsByNetwork(String network, int perPage, int page, String order) {
        String url = baseUrl + "/onchain/networks/" + urlEncode(network)
                + "/pools?per_page=" + perPage + "&page=" + page
                + (order == null ? "" : "&order=" + urlEncode(order));
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            com.google.gson.JsonElement parsed = gson.fromJson(r.body().string(), com.google.gson.JsonElement.class);
            if (parsed == null) return Optional.empty();
            if (parsed.isJsonObject()) return Optional.of(parsed.getAsJsonObject());
            if (parsed.isJsonArray()) {
                JsonObject wrapper = new JsonObject();
                wrapper.add("data", parsed.getAsJsonArray());
                return Optional.of(wrapper);
            }
            return Optional.empty();
        } catch (IOException e) {
            log.error("CoinGecko onchain pools error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getPoolData(String network, String poolAddress) {
        String url = baseUrl + "/onchain/" + urlEncode(network) + "/pools/" + urlEncode(poolAddress);
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko pool data error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonArray> getTokensByNetwork(String network, int perPage, int page, String order) {
        String url = baseUrl + "/onchain/" + urlEncode(network) + "/tokens?per_page=" + perPage + "&page=" + page
                + (order == null ? "" : "&order=" + urlEncode(order));
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            return Optional.ofNullable(arr);
        } catch (IOException e) {
            log.error("CoinGecko onchain tokens error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getTokenData(String network, String contractAddress) {
        String url = baseUrl + "/onchain/" + urlEncode(network) + "/tokens/" + urlEncode(contractAddress);
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko onchain token error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonArray> getDexesByNetwork(String network, int perPage, int page, String order) {
        String url = baseUrl + "/onchain/" + urlEncode(network) + "/dexes?per_page=" + perPage + "&page=" + page
                + (order == null ? "" : "&order=" + urlEncode(order));
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonArray arr = gson.fromJson(r.body().string(), JsonArray.class);
            return Optional.ofNullable(arr);
        } catch (IOException e) {
            log.error("CoinGecko onchain dexes error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getDexData(String network, String dexName) {
        String url = baseUrl + "/onchain/" + urlEncode(network) + "/dexes/" + urlEncode(dexName);
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko onchain dex error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // --- Exchange rates ---
    public Optional<JsonObject> getExchangeRates() {
        String url = baseUrl + "/exchange_rates";
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko exchange_rates error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // --- Onchain pool OHLCV ---
    /**
     * Fetches OHLCV candlestick data for a specific DEX pool via GeckoTerminal.
     *
     * @param network    network ID (e.g. "eth")
     * @param poolAddr   pool contract address
     * @param timeframe  "day", "hour", or "minute"
     * @param aggregate  aggregation multiplier (e.g. 4 for 4h candles when timeframe=hour)
     * @param limit      max number of candles (up to 1000)
     * @return raw JSON object with "data.attributes.ohlcv_list"
     */
    public Optional<JsonObject> getPoolOhlcv(String network, String poolAddr,
                                             String timeframe, int aggregate, int limit) {
        String url = baseUrl + "/onchain/networks/" + urlEncode(network)
                + "/pools/" + urlEncode(poolAddr)
                + "/ohlcv/" + urlEncode(timeframe)
                + "?aggregate=" + aggregate
                + "&limit=" + Math.min(limit, 1000);
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            JsonObject obj = gson.fromJson(r.body().string(), JsonObject.class);
            return Optional.ofNullable(obj);
        } catch (IOException e) {
            log.error("CoinGecko pool OHLCV error for {}/{}: {}", network, poolAddr, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Converts a raw pool OHLCV JSON response into {@link OhlcvBar} list.
     * The array format is: [timestamp_sec, open, high, low, close, volume].
     */
    public List<OhlcvBar> parsePoolOhlcvToOhlcvBars(Optional<JsonObject> rawOpt,
                                                     String symbol, String timeframe) {
        if (rawOpt.isEmpty()) return Collections.emptyList();
        try {
            JsonObject root = rawOpt.get();
            if (!root.has("data")) return Collections.emptyList();
            JsonObject data = root.getAsJsonObject("data");
            if (!data.has("attributes")) return Collections.emptyList();
            JsonObject attrs = data.getAsJsonObject("attributes");
            if (!attrs.has("ohlcv_list")) return Collections.emptyList();
            JsonArray ohlcvList = attrs.getAsJsonArray("ohlcv_list");

            List<OhlcvBar> bars = new ArrayList<>(ohlcvList.size());
            for (JsonElement el : ohlcvList) {
                JsonArray row = el.getAsJsonArray();
                long   ts    = row.get(0).getAsLong() * 1000L; // sec → ms
                BigDecimal o = new BigDecimal(row.get(1).getAsString());
                BigDecimal h = new BigDecimal(row.get(2).getAsString());
                BigDecimal l = new BigDecimal(row.get(3).getAsString());
                BigDecimal c = new BigDecimal(row.get(4).getAsString());
                BigDecimal v = new BigDecimal(row.get(5).getAsString());
                bars.add(OhlcvBar.builder()
                        .symbol(symbol.toUpperCase())
                        .timeframe(timeframe)
                        .openTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneOffset.UTC))
                        .open(o).high(h).low(l).close(c).volume(v)
                        .assetType(AssetType.CRYPTO)
                        .build());
            }
            return bars;
        } catch (Exception e) {
            log.warn("Failed to parse pool OHLCV: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // --- Onchain trending pools ---
    public Optional<JsonObject> getTrendingPoolsGlobal() {
        String url = baseUrl + "/onchain/networks/trending_pools";
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            return Optional.ofNullable(gson.fromJson(r.body().string(), JsonObject.class));
        } catch (IOException e) {
            log.error("CoinGecko trending pools error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getTrendingPoolsByNetwork(String network) {
        String url = baseUrl + "/onchain/networks/" + urlEncode(network) + "/trending_pools";
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            return Optional.ofNullable(gson.fromJson(r.body().string(), JsonObject.class));
        } catch (IOException e) {
            log.error("CoinGecko trending pools by network error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getPoolsByToken(String network, String tokenAddress, int page) {
        String url = baseUrl + "/onchain/networks/" + urlEncode(network)
                + "/tokens/" + urlEncode(tokenAddress)
                + "/pools?page=" + page;
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            return Optional.ofNullable(gson.fromJson(r.body().string(), JsonObject.class));
        } catch (IOException e) {
            log.error("CoinGecko pools by token error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> getPoolTrades(String network, String poolAddress) {
        String url = baseUrl + "/onchain/networks/" + urlEncode(network)
                + "/pools/" + urlEncode(poolAddress) + "/trades";
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            return Optional.ofNullable(gson.fromJson(r.body().string(), JsonObject.class));
        } catch (IOException e) {
            log.error("CoinGecko pool trades error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Optional<JsonObject> searchPools(String query) {
        String url = baseUrl + "/onchain/search/pools?query=" + urlEncode(query);
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            return Optional.ofNullable(gson.fromJson(r.body().string(), JsonObject.class));
        } catch (IOException e) {
            log.error("CoinGecko search pools error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // --- NFT list (free tier) ---
    public Optional<JsonArray> listNfts(int perPage, int page, String order) {
        String url = baseUrl + "/nfts/list?per_page=" + perPage + "&page=" + page
                + (order != null ? "&order=" + urlEncode(order) : "");
        rateLimiter.acquire();
        try (Response r = httpClient.newCall(buildRequest(url)).execute()) {
            handleRateLimitHeaders(r);
            if (!r.isSuccessful() || r.body() == null) return Optional.empty();
            return Optional.ofNullable(gson.fromJson(r.body().string(), JsonArray.class));
        } catch (IOException e) {
            log.error("CoinGecko NFT list error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // --- Typed POJO wrappers ---
    public Map<String, CoinGeckoSimplePrice> getSimplePriceTyped(String idsCsv, String vsCurrency,
                                                                 boolean includeMarketCap, boolean include24hChange,
                                                                 boolean includeLastUpdatedAt) {
        Optional<JsonObject> rootOpt = getSimplePrice(idsCsv, vsCurrency, includeMarketCap, include24hChange, includeLastUpdatedAt);
        if (rootOpt.isEmpty()) return Collections.emptyMap();
        JsonObject root = rootOpt.get();
        Map<String, CoinGeckoSimplePrice> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : root.entrySet()) {
            try {
                JsonObject coinNode = e.getValue().getAsJsonObject();
                CoinGeckoSimplePrice.fromCoinNode(coinNode).ifPresent(p -> out.put(e.getKey(), p));
            } catch (Exception ex) {
                log.debug("parse simple price entry {} failed: {}", e.getKey(), ex.getMessage());
            }
        }
        return out;
    }

    public List<CoinGeckoMarketCoin> getCoinsMarketsTyped(String vsCurrency, String idsCsv, String order, int perPage, int page,
                                                          boolean sparkline, String priceChangePercentage) {
        Optional<JsonArray> arrOpt = getCoinsMarkets(vsCurrency, idsCsv, order, perPage, page, sparkline, priceChangePercentage);
        if (arrOpt.isEmpty()) return Collections.emptyList();
        JsonArray arr = arrOpt.get();
        List<CoinGeckoMarketCoin> out = new ArrayList<>();
        for (JsonElement el : arr) {
            try {
                JsonObject node = el.getAsJsonObject();
                CoinGeckoMarketCoin.fromJson(node).ifPresent(out::add);
            } catch (Exception ex) {
                log.debug("parse markets row failed: {}", ex.getMessage());
            }
        }
        return out;
    }

    public Optional<CoinGeckoFullCoin> getCoinByIdTyped(String id, boolean localization, boolean marketData,
                                                        boolean communityData, boolean developerData, boolean sparkline) {
        Optional<JsonObject> obj = getCoinById(id, localization, marketData, communityData, developerData, sparkline);
        return obj.map(j -> gson.fromJson(j, CoinGeckoFullCoin.class));
    }

    public Optional<CoinGeckoMarketChart> getCoinMarketChartTyped(String id, String vsCurrency, String days, String interval) {
        Optional<JsonObject> obj = getCoinMarketChart(id, vsCurrency, days, interval);
        return obj.map(j -> gson.fromJson(j, CoinGeckoMarketChart.class));
    }

    // --- Utilities ---
    private String urlEncode(String s) {
        if (s == null) return "";
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // ── Helpers ─────────────────────────────────────────────

    private String toCoinId(String symbol) {
        if (symbol == null || symbol.isBlank()) return null;
        String id = lookupStaticCoinId(symbol);
        if (id != null) return id;
        return coinIdLookup.apply(symbol);
    }

    private String stripSuffix(String s) {
        for (String suffix : List.of("USDT","USD","BTC","ETH","BNB","BUSD"))
            if (s.endsWith(suffix) && s.length() > suffix.length())
                return s.substring(0, s.length() - suffix.length());
        return s;
    }

    private int toDays(String timeframe, int limit) {
        return switch (timeframe.toLowerCase()) {
            case "1m"  -> 1;
            case "5m"  -> 1;
            case "15m" -> 2;
            case "30m" -> 3;
            case "1h"  -> Math.max(1, limit / 24) + 1;
            case "4h"  -> Math.max(1, limit / 6)  + 1;
            case "1d"  -> Math.min(limit + 5, 365);
            case "1w"  -> Math.min(limit * 7 + 10, 1825);
            default    -> 30;
        };
    }

    private Request buildRequest(String url) {
        Request.Builder b = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "TradingPlatform/1.0")
                .addHeader("Accept", "application/json");
        if (apiKey != null && !apiKey.isBlank() && !"demo".equalsIgnoreCase(apiKey))
            b.addHeader("x-cg-demo-api-key", apiKey);
        return b.build();
    }

    // ─── Package-private accessors used by sub-services ──────────────────

    /** @return the configured base URL (injected via @Value) */
    String getBaseUrl()   { return baseUrl; }

    /** @return the configured API key (may be blank) */
    String getApiKey()    { return apiKey; }

    /** @return the shared OkHttpClient */
    OkHttpClient getHttpClient() { return httpClient; }

    /** @return the shared rate limiter */
    CoinGeckoRateLimiter getRateLimiter() { return rateLimiter; }

    /** Gson instance shared with sub-services */
    Gson getGson() { return gson; }
}