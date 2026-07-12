package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.config.PriceHttpConfig;
import com.mst.matt.tradingplatformapp.service.price.api.coingecko.*;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CoinGeckoMarketService}.
 *
 * All HTTP calls are intercepted by MockWebServer — no live API is hit.
 * JdbcTemplate is mocked so that DB persistence does not require a real database.
 *
 * Endpoints tested:
 * - GET /coins/markets        → getCoinsMarkets / getTopCoinsMarkets
 * - GET /coins/{id}           → getCoinDetail / getCoinDetailBySymbol
 * - GET /global               → getGlobalMarketData
 * - GET /simple/price         → getSimpleUsdPrice
 * - GET /exchange_rates       → getExchangeRates
 */
class CoinGeckoMarketServiceTest {

    private MockWebServer       server;
    private CoinGeckoService coinGeckoService;
    private CoinGeckoMarketService marketService;
    private JdbcTemplate        mockJdbc;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        OkHttpClient client = new PriceHttpConfig().priceHttpClient(5, 5, 0);
        coinGeckoService = new CoinGeckoService(client, new CoinGeckoRateLimiter());
        PriceServiceTestSupport.setBaseUrl(coinGeckoService, "baseUrl",
                server.url("").toString().replaceAll("/$", ""));
        PriceServiceTestSupport.setBaseUrl(coinGeckoService, "apiKey", "test-key");

        mockJdbc = mock(JdbcTemplate.class);
        // Stub execute() so DDL table creation doesn't throw
        doNothing().when(mockJdbc).execute(anyString());
        // Stub update() for upsert calls
        when(mockJdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        // Stub getDataSource to return null (triggers SQLite path for isPostgres)
        when(mockJdbc.getDataSource()).thenReturn(null);

        marketService = new CoinGeckoMarketService(coinGeckoService, mockJdbc);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    // ── /coins/markets ────────────────────────────────────────────────────────

    @Test
    void getCoinsMarkets_parsesMarketListWithCorrectFields() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-markets.json"))
                .addHeader("Content-Type", "application/json"));

        List<CoinGeckoMarketCoin> result = marketService.getCoinsMarkets(
                "usd", "bitcoin", "market_cap_desc", 50, 1, "24h");

        assertFalse(result.isEmpty(), "Should parse non-empty market list");
        CoinGeckoMarketCoin coin = result.get(0);
        assertEquals("bitcoin", coin.id());
        assertEquals("BTC", coin.symbol().toUpperCase());
        assertEquals("Bitcoin", coin.name());
        assertTrue(coin.currentPrice().compareTo(BigDecimal.ZERO) > 0,
                "Current price should be positive");
        assertTrue(coin.marketCap().compareTo(BigDecimal.ZERO) > 0,
                "Market cap should be positive");
    }

    @Test
    void getTopCoinsMarkets_returnsDefaultTop50() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-markets.json"))
                .addHeader("Content-Type", "application/json"));

        List<CoinGeckoMarketCoin> result = marketService.getTopCoinsMarkets(50);

        assertFalse(result.isEmpty(), "getTopCoinsMarkets should return results");
    }

    @Test
    void getCoinsMarkets_returnsEmptyOnHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));

        List<CoinGeckoMarketCoin> result = marketService.getCoinsMarkets(
                "usd", null, "market_cap_desc", 50, 1, "24h");

        assertTrue(result.isEmpty(), "Should return empty list on HTTP error");
    }

    @Test
    void getCoinsMarkets_returnsEmptyOn429RateLimitError() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(429)
                .addHeader("Retry-After", "1"));

        List<CoinGeckoMarketCoin> result = marketService.getCoinsMarkets(
                "usd", null, "market_cap_desc", 50, 1, "24h");

        assertTrue(result.isEmpty(), "Should return empty list on 429 rate limit");
    }

    @Test
    void getCoinsMarkets_cachesPreviousResult() throws Exception {
        // Enqueue only one response — second call should hit cache
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-markets.json"))
                .addHeader("Content-Type", "application/json"));

        List<CoinGeckoMarketCoin> first  = marketService.getCoinsMarkets("usd", "bitcoin", "market_cap_desc", 50, 1, "24h");
        List<CoinGeckoMarketCoin> second = marketService.getCoinsMarkets("usd", "bitcoin", "market_cap_desc", 50, 1, "24h");

        assertFalse(first.isEmpty());
        assertEquals(first.size(), second.size(), "Cached result should match first call");
        assertEquals(1, server.getRequestCount(), "Second call should use cache, not hit the server again");
    }

    // ── /coins/{id} ───────────────────────────────────────────────────────────

    @Test
    void getCoinDetail_parsesFullCoinData() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-coin-detail.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<CoinGeckoFullCoin> result = marketService.getCoinDetail("bitcoin");

        assertTrue(result.isPresent(), "getCoinDetail should return data for 'bitcoin'");
        CoinGeckoFullCoin coin = result.get();
        assertEquals("bitcoin", coin.getId());
        assertEquals("btc", coin.getSymbol().toLowerCase());
        assertEquals("Bitcoin", coin.getName());
    }

    @Test
    void getCoinDetail_returnsEmptyOnNotFound() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404));

        Optional<CoinGeckoFullCoin> result = marketService.getCoinDetail("nonexistent-coin-xyz");

        assertTrue(result.isEmpty(), "Should return empty Optional for 404");
    }

    @Test
    void getCoinDetailBySymbol_resolvesKnownSymbol() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-coin-detail.json"))
                .addHeader("Content-Type", "application/json"));

        // "BTC" maps to "bitcoin" in the static map of CoinGeckoService
        Optional<CoinGeckoFullCoin> result = marketService.getCoinDetailBySymbol("BTC");

        assertTrue(result.isPresent(), "getCoinDetailBySymbol should resolve BTC to bitcoin");
    }

    @Test
    void getCoinDetailBySymbol_returnsEmptyForUnknownSymbol() {
        // No server call needed — unknown symbol can't resolve to a coin ID
        Optional<CoinGeckoFullCoin> result = marketService.getCoinDetailBySymbol("XXXXXXXXX");

        assertTrue(result.isEmpty(), "Unknown symbol should return empty Optional");
        assertEquals(0, server.getRequestCount(), "Should not hit the server for unknown symbols");
    }

    @Test
    void getCoinDetail_cachesPreviousResult() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-coin-detail.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<CoinGeckoFullCoin> first  = marketService.getCoinDetail("bitcoin");
        Optional<CoinGeckoFullCoin> second = marketService.getCoinDetail("bitcoin");

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertEquals(first.get().getId(), second.get().getId());
        assertEquals(1, server.getRequestCount(), "Second call should use cache");
    }

    // ── /global ───────────────────────────────────────────────────────────────

    @Test
    void getGlobalMarketData_returnsDataObject() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-global.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<com.google.gson.JsonObject> result = marketService.getGlobalMarketData();

        assertTrue(result.isPresent(), "getGlobalMarketData should return data");
        assertTrue(result.get().has("data"), "Response should contain 'data' key");

        com.google.gson.JsonObject data = result.get().getAsJsonObject("data");
        assertTrue(data.has("active_cryptocurrencies"), "Should have active_cryptocurrencies");
        assertTrue(data.has("total_market_cap"), "Should have total_market_cap");
        assertTrue(data.has("market_cap_percentage"), "Should have market_cap_percentage");
    }

    @Test
    void getGlobalMarketData_returnsEmptyOn500() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));

        Optional<com.google.gson.JsonObject> result = marketService.getGlobalMarketData();

        assertTrue(result.isEmpty(), "Should return empty on HTTP 500");
    }

    // ── /simple/price ─────────────────────────────────────────────────────────

    @Test
    void getSimpleUsdPrice_returnsPositivePriceForBtc() throws Exception {
        // getSimpleUsdPrice calls /coins/markets first as primary, then /simple/price
        // The CoinGeckoService.getQuote uses markets first; getSimpleUsdPrice uses
        // getSimplePriceTyped → /simple/price endpoint
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-simple-price.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<BigDecimal> price = marketService.getSimpleUsdPrice("BTC");

        assertTrue(price.isPresent(), "getSimpleUsdPrice should return a value for BTC");
        assertTrue(price.get().compareTo(BigDecimal.ZERO) > 0, "Price should be positive");
    }

    @Test
    void getSimpleUsdPrice_returnsEmptyForUnknownSymbol() {
        Optional<BigDecimal> price = marketService.getSimpleUsdPrice("UNKNOWN_XYZ_TOKEN");

        assertTrue(price.isEmpty(), "Should return empty for unresolvable symbol");
        assertEquals(0, server.getRequestCount(), "Should not hit server for unknown symbol");
    }

    // ── /exchange_rates ───────────────────────────────────────────────────────

    @Test
    void getExchangeRates_returnsRatesObject() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-exchange-rates.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<com.google.gson.JsonObject> result = marketService.getExchangeRates();

        assertTrue(result.isPresent(), "getExchangeRates should return data");
        assertTrue(result.get().has("rates"), "Response should contain 'rates' key");
    }

    @Test
    void getExchangeRatesTyped_parsesRateEntries() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-exchange-rates.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoExchangeRates>
                result = marketService.getExchangeRatesTyped();

        assertTrue(result.isPresent(), "getExchangeRatesTyped should parse data");
    }

    // ── Market data for multiple symbols ──────────────────────────────────────

    @Test
    void getMarketDataForSymbols_returnsDataForRecognisedSymbols() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-markets.json"))
                .addHeader("Content-Type", "application/json"));

        List<CoinGeckoMarketCoin> result = marketService.getMarketDataForSymbols(
                List.of("BTC", "ETH"));

        // At least BTC resolves — fixture only has bitcoin but service should return non-empty
        assertFalse(result.isEmpty(), "Should return market data for known symbols");
    }

    @Test
    void getMarketDataForSymbols_returnsEmptyForNoKnownSymbols() {
        List<CoinGeckoMarketCoin> result = marketService.getMarketDataForSymbols(
                List.of("UNKNOWN_AAA", "UNKNOWN_BBB"));

        assertTrue(result.isEmpty(), "Should return empty for unresolvable symbols");
        assertEquals(0, server.getRequestCount(), "Should not make HTTP calls for unknown symbols");
    }

    @Test
    void getMarketDataForSymbols_handlesNullInput() {
        List<CoinGeckoMarketCoin> result = marketService.getMarketDataForSymbols(null);

        assertTrue(result.isEmpty(), "Should handle null input gracefully");
    }
}
