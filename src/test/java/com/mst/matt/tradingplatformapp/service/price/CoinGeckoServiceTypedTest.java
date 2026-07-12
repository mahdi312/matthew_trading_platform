package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.config.PriceHttpConfig;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoRateLimiter;
import com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoService;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Typed unit tests for {@link CoinGeckoService} core endpoints.
 *
 * Tests all primary free-tier endpoints:
 * - GET /simple/price                → getSimplePriceTyped / getQuote (via simplePrice)
 * - GET /coins/markets               → getCoinsMarketsTyped / getQuote (via markets)
 * - GET /coins/{id}/ohlc             → getOhlcv()
 * - GET /coins/{id}/market_chart     → getOhlcvFromMarketChart()
 * - GET /coins/{id}/market_chart/range → getOhlcvRange()
 * - GET /coins/{id}                  → getCoinByIdTyped()
 * - GET /global                      → getGlobalData()
 * - GET /exchange_rates              → getExchangeRates()
 * - GET /search                      → search()
 * - GET /search/trending             → getSearchTrending()
 * - GET /coins/list                  → fetchCoinsList()
 * - provider metadata                → getProviderId(), getProviderName(), supports()
 */
class CoinGeckoServiceTypedTest {

    private MockWebServer server;
    private CoinGeckoService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        OkHttpClient client = new PriceHttpConfig().priceHttpClient(5, 5, 0);
        service = new CoinGeckoService(client, new CoinGeckoRateLimiter());
        PriceServiceTestSupport.setBaseUrl(service, "baseUrl",
                server.url("").toString().replaceAll("/$", ""));
        PriceServiceTestSupport.setBaseUrl(service, "apiKey", "test-key");
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    // ── /coins/markets (primary quote path) ───────────────────────────────────

    @Test
    void getCoinsMarketsTyped_parsesMarketArray() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-markets.json"))
                .addHeader("Content-Type", "application/json"));

        List<?> list = service.getCoinsMarketsTyped("usd", "bitcoin", "market_cap_desc", 50, 1, false, "24h");
        assertFalse(list.isEmpty(), "Markets response should produce non-empty typed list");
    }

    @Test
    void getQuote_viaCoinMarkets_returnsValidPriceQuote() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-markets.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<com.mst.matt.tradingplatformapp.service.price.PriceQuote> quote =
                service.getQuote("BTC");

        assertTrue(quote.isPresent(), "getQuote(BTC) should return a present Optional");
        assertTrue(quote.get().getPrice().compareTo(BigDecimal.ZERO) > 0,
                "Price should be positive");
        assertEquals("BTC", quote.get().getSymbol(),
                "Symbol should match requested symbol");
    }

    @Test
    void getQuote_fallsBackToSimplePrice_whenMarketsReturnsEmpty() throws Exception {
        // First call (/coins/markets) returns empty array
        server.enqueue(new MockResponse()
                .setBody("[]")
                .addHeader("Content-Type", "application/json"));
        // Second call (/simple/price) returns valid data
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-simple-price.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<PriceQuote> quote = service.getQuote("BTC");

        assertTrue(quote.isPresent(), "Fallback to /simple/price should succeed");
    }

    @Test
    void getCoinsMarketsTyped_returnsEmptyOnHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));

        List<?> list = service.getCoinsMarketsTyped("usd", "bitcoin", "market_cap_desc", 50, 1, false, "24h");
        assertTrue(list.isEmpty(), "Should return empty list on HTTP 500");
    }

    // ── /simple/price ─────────────────────────────────────────────────────────

    @Test
    void getSimplePriceTyped_parsesSimpleObject() throws Exception {
        String body = "{"
                + "\"bitcoin\":{\"usd\":12345.67,\"usd_24h_change\":1.23,\"usd_market_cap\":999999999}"
                + "}";
        server.enqueue(new MockResponse().setBody(body).addHeader("Content-Type", "application/json"));

        Map<String, ?> map = service.getSimplePriceTyped("bitcoin", "usd", true, true, false);
        assertTrue(map.containsKey("bitcoin"), "Map should contain 'bitcoin' key");
    }

    @Test
    void getSimplePrice_returnsEmptyOnHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(429).addHeader("Retry-After", "1"));

        Optional<com.google.gson.JsonObject> result =
                service.getSimplePrice("bitcoin", "usd", false, false, false);
        assertTrue(result.isEmpty(), "Should return empty on rate limit 429");
    }

    // ── /coins/{id}/ohlc ──────────────────────────────────────────────────────

    @Test
    void getOhlcv_parsesOhlcTupleArray() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-ohlc.json"))
                .addHeader("Content-Type", "application/json"));

        List<OhlcvBar> bars = service.getOhlcv("BTC", "1d", 10);

        assertFalse(bars.isEmpty(), "OHLCV bars should not be empty");
        OhlcvBar bar = bars.get(0);
        assertNotNull(bar.getOpenTime(), "Bar should have open time");
        assertNotNull(bar.getOpen(),     "Bar should have open price");
        assertNotNull(bar.getHigh(),     "Bar should have high price");
        assertNotNull(bar.getLow(),      "Bar should have low price");
        assertNotNull(bar.getClose(),    "Bar should have close price");
    }

    @Test
    void getOhlcv_returnsEmptyForUnknownSymbol() {
        List<OhlcvBar> bars = service.getOhlcv("UNKNOWNTOKENABC", "1d", 10);
        assertTrue(bars.isEmpty(), "Unknown symbol should return empty list without HTTP call");
        assertEquals(0, server.getRequestCount(),
                "Should not make HTTP request for unresolvable symbol");
    }

    @Test
    void getOhlcv_returnsEmptyOnHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));

        List<OhlcvBar> bars = service.getOhlcv("BTC", "1d", 10);

        assertTrue(bars.isEmpty(), "Should return empty list on HTTP 500");
    }

    @Test
    void getOhlcv_trimsResultToRequestedLimit() throws Exception {
        // Fixture has 1 bar — limit 1 should return 1 bar
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-ohlc.json"))
                .addHeader("Content-Type", "application/json"));

        List<OhlcvBar> bars = service.getOhlcv("BTC", "1d", 1);

        assertTrue(bars.size() <= 1, "Result should be trimmed to requested limit");
    }

    // ── /coins/{id}/market_chart ───────────────────────────────────────────────

    @Test
    void getOhlcvFromMarketChart_synthesisesOhlcvCandles() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-market-chart.json"))
                .addHeader("Content-Type", "application/json"));

        List<OhlcvBar> bars = service.getOhlcvFromMarketChart("BTC", "1h", 10);

        assertFalse(bars.isEmpty(), "market_chart should synthesise non-empty OHLCV bars");
        OhlcvBar bar = bars.get(0);
        assertNotNull(bar.getOpenTime(), "Synthesised bar should have open time");
        assertNotNull(bar.getVolume(),   "Synthesised bar should have volume from total_volumes");
    }

    @Test
    void getOhlcvFromMarketChart_returnsEmptyOnHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));

        List<OhlcvBar> bars = service.getOhlcvFromMarketChart("BTC", "1h", 10);

        assertTrue(bars.isEmpty(), "Should return empty on HTTP error");
    }

    @Test
    void getOhlcvFromMarketChart_returnsEmptyForUnknownSymbol() {
        List<OhlcvBar> bars = service.getOhlcvFromMarketChart("UNKNOWNXYZ", "1h", 10);
        assertTrue(bars.isEmpty(), "Unknown symbol should return empty");
        assertEquals(0, server.getRequestCount(), "Should not call API for unknown symbol");
    }

    // ── /coins/{id}/market_chart/range ────────────────────────────────────────

    @Test
    void getOhlcvRange_synthesisesOhlcvCandlesForDateRange() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-market-chart.json"))
                .addHeader("Content-Type", "application/json"));

        long from = 1716000000L; // epoch seconds
        long to   = 1716100000L;
        List<OhlcvBar> bars = service.getOhlcvRange("BTC", "1h", from, to);

        assertFalse(bars.isEmpty(), "Range OHLCV should return bars for date range");
    }

    @Test
    void getOhlcvRange_returnsEmptyForUnknownSymbol() {
        List<OhlcvBar> bars = service.getOhlcvRange("FAKETOKEN", "1h", 0L, 1L);
        assertTrue(bars.isEmpty(), "Unknown symbol should produce empty range result");
        assertEquals(0, server.getRequestCount(), "No HTTP call for unknown symbol");
    }

    // ── /coins/{id} ───────────────────────────────────────────────────────────

    @Test
    void getCoinByIdTyped_parsesFullCoinObject() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-coin-detail.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoFullCoin>
                result = service.getCoinByIdTyped("bitcoin", false, true, false, false, false);

        assertTrue(result.isPresent(), "getCoinByIdTyped() should return data for bitcoin");
        assertEquals("bitcoin", result.get().getId(),
                "Parsed coin ID should be 'bitcoin'");
    }

    @Test
    void getCoinByIdTyped_returnsEmptyOnHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404));

        Optional<com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoFullCoin>
                result = service.getCoinByIdTyped("nonexistent", false, false, false, false, false);

        assertTrue(result.isEmpty(), "Should return empty Optional for 404");
    }

    // ── /global ───────────────────────────────────────────────────────────────

    @Test
    void getGlobalData_returnsDataWithActiveCryptosField() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-global.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<com.google.gson.JsonObject> result = service.getGlobalData();

        assertTrue(result.isPresent(), "getGlobalData() should return data");
        assertTrue(result.get().has("data"), "Global response must have 'data' key");
        assertTrue(result.get().getAsJsonObject("data").has("active_cryptocurrencies"),
                "Global data should have active_cryptocurrencies");
    }

    @Test
    void getGlobalData_returnsEmptyOnHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));

        Optional<com.google.gson.JsonObject> result = service.getGlobalData();

        assertTrue(result.isEmpty(), "Should return empty on HTTP 500");
    }

    // ── /exchange_rates ───────────────────────────────────────────────────────

    @Test
    void getExchangeRates_returnsRatesMap() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-exchange-rates.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<com.google.gson.JsonObject> result = service.getExchangeRates();

        assertTrue(result.isPresent(), "getExchangeRates() should return data");
        assertTrue(result.get().has("rates"), "Exchange rates response must have 'rates'");
    }

    // ── /search ───────────────────────────────────────────────────────────────

    @Test
    void search_returnsParsedSearchResultObject() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-search.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<com.google.gson.JsonObject> result = service.search("bitcoin");

        assertTrue(result.isPresent(), "search() should return JSON result");
        assertTrue(result.get().has("coins"), "Search result should have 'coins' array");
    }

    @Test
    void search_returnsEmptyOnHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));

        Optional<com.google.gson.JsonObject> result = service.search("bitcoin");

        assertTrue(result.isEmpty(), "Should return empty on HTTP error");
    }

    // ── /search/trending ──────────────────────────────────────────────────────

    @Test
    void getSearchTrending_returnsCoinsArray() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-trending.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<com.google.gson.JsonArray> result = service.getSearchTrending();

        assertTrue(result.isPresent(), "getSearchTrending() should return coins array");
        assertFalse(result.get().isEmpty(), "Trending coins array should not be empty");
    }

    @Test
    void getSearchTrending_returnsEmptyOnHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503));

        Optional<com.google.gson.JsonArray> result = service.getSearchTrending();

        assertTrue(result.isEmpty(), "Should return empty on HTTP 503");
    }

    // ── /coins/list ───────────────────────────────────────────────────────────

    @Test
    void fetchCoinsList_parsesCoinsArray() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-coins-list.json"))
                .addHeader("Content-Type", "application/json"));

        List<com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoCoinBasic>
                coins = service.fetchCoinsList();

        assertFalse(coins.isEmpty(), "fetchCoinsList() should return non-empty list");
        assertEquals(4, coins.size(), "Fixture has 4 coins");

        // Verify first entry
        com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoCoinBasic bitcoin =
                coins.get(0);
        assertEquals("bitcoin", bitcoin.getId(),    "First coin ID should be 'bitcoin'");
        assertEquals("btc",     bitcoin.getSymbol().toLowerCase(), "First coin symbol should be 'btc'");
        assertEquals("Bitcoin", bitcoin.getName(),  "First coin name should be 'Bitcoin'");
    }

    @Test
    void fetchCoinsList_returnsEmptyOnHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));

        List<?> coins = service.fetchCoinsList();

        assertTrue(coins.isEmpty(), "Should return empty list on HTTP error");
    }

    // ── Provider metadata ─────────────────────────────────────────────────────

    @Test
    void getProviderName_returnsCoinGecko() {
        assertEquals("CoinGecko", service.getProviderName(),
                "Provider name should be 'CoinGecko'");
    }

    @Test
    void getProviderId_returnsCoinGeckoEnum() {
        assertEquals(MarketDataProvider.COINGECKO, service.getProviderId(),
                "Provider ID should be COINGECKO");
    }

    @Test
    void supports_acceptsKnownCryptoSymbols() {
        assertTrue(service.supports("BTC"),     "Should support BTC");
        assertTrue(service.supports("ETH"),     "Should support ETH");
        assertTrue(service.supports("BTCUSDT"), "Should support BTCUSDT");
        assertTrue(service.supports("SOLUSDT"), "Should support SOLUSDT");
    }

    @Test
    void supports_rejectsStockAndForexSymbols() {
        assertFalse(service.supports("AAPL"),   "Should not support stock AAPL");
        assertFalse(service.supports("EURUSD"), "Should not support forex EURUSD");
        assertFalse(service.supports("MSFT"),   "Should not support stock MSFT");
    }

    // ── resolveCoinId ─────────────────────────────────────────────────────────

    @Test
    void resolveCoinId_mapsBtcToBitcoin() {
        assertEquals("bitcoin", service.resolveCoinId("BTC"),
                "BTC should resolve to 'bitcoin'");
    }

    @Test
    void resolveCoinId_stripsUsdtSuffix() {
        assertEquals("bitcoin", service.resolveCoinId("BTCUSDT"),
                "BTCUSDT should strip USDT and resolve to 'bitcoin'");
    }

    @Test
    void resolveCoinId_returnsNullForUnknownSymbol() {
        assertNull(service.resolveCoinId("UNKNOWNTOKEN_XYZ"),
                "Unknown symbol should return null");
    }

    // ── Package-private accessors ─────────────────────────────────────────────

    @Test
    void getBaseUrl_returnsConfiguredUrl() {
        String baseUrl = service.getBaseUrl();
        assertNotNull(baseUrl, "getBaseUrl() should not return null");
        assertFalse(baseUrl.isBlank(), "Base URL should not be blank");
    }

    @Test
    void getGson_returnsNonNullGsonInstance() {
        assertNotNull(service.getGson(), "getGson() should return a non-null Gson instance");
    }

    @Test
    void getRateLimiter_returnsNonNullRateLimiter() {
        assertNotNull(service.getRateLimiter(),
                "getRateLimiter() should return a non-null CoinGeckoRateLimiter");
    }
}
