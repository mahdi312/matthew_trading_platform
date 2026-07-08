package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.config.PriceHttpConfig;
import com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoDefiData;
import com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoExchangeRates;
import com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoGlobalData;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CoinGeckoDefiService}.
 *
 * Endpoints tested:
 * - GET /global                             → getGlobalData() / getGlobalRaw()
 * - GET /global/decentralized_finance_defi  → getDefiData() / getDefiRaw()
 * - GET /exchange_rates                     → getExchangeRates()
 *
 * All HTTP calls are intercepted by MockWebServer.
 * JdbcTemplate is mocked so no actual database is required.
 */
class CoinGeckoDefiServiceTest {

    private MockWebServer      server;
    private CoinGeckoService   coinGeckoService;
    private CoinGeckoDefiService defiService;
    private JdbcTemplate       mockJdbc;

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
        doNothing().when(mockJdbc).execute(anyString());
        when(mockJdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(mockJdbc.getDataSource()).thenReturn(null);

        defiService = new CoinGeckoDefiService(coinGeckoService, mockJdbc);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    // ── /global ───────────────────────────────────────────────────────────────

    @Test
    void getGlobalData_parsesActiveCryptosAndMarketCap() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-global.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<CoinGeckoGlobalData> result = defiService.getGlobalData();

        assertTrue(result.isPresent(), "getGlobalData() should return data");
        CoinGeckoGlobalData data = result.get();
        assertTrue(data.getActiveCryptocurrencies() > 0,
                "Active cryptocurrencies should be positive");
    }

    @Test
    void getGlobalData_returnsEmptyOnHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));

        Optional<CoinGeckoGlobalData> result = defiService.getGlobalData();

        assertTrue(result.isEmpty(), "Should return empty on HTTP 500");
    }

    @Test
    void getGlobalData_cachesPreviousResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-global.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<CoinGeckoGlobalData> first  = defiService.getGlobalData();
        Optional<CoinGeckoGlobalData> second = defiService.getGlobalData();

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertEquals(1, server.getRequestCount(), "Second call should use in-memory cache");
    }

    @Test
    void getGlobalRaw_returnsJsonWithDataKey() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-global.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<com.google.gson.JsonObject> result = defiService.getGlobalRaw();

        assertTrue(result.isPresent(), "getGlobalRaw() should return raw JSON");
        assertTrue(result.get().has("data"), "Raw response should contain 'data' key");
    }

    // ── /global/decentralized_finance_defi ────────────────────────────────────

    @Test
    void getDefiData_parsesDefiMarketCapAndDominance() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-defi.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<CoinGeckoDefiData> result = defiService.getDefiData();

        assertTrue(result.isPresent(), "getDefiData() should return data");
        CoinGeckoDefiData data = result.get();
        // defi_market_cap is a string in the fixture
        assertNotNull(data.getDefiMarketCap(), "DeFi market cap should not be null");
    }

    @Test
    void getDefiData_returnsEmptyOnHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404));

        Optional<CoinGeckoDefiData> result = defiService.getDefiData();

        assertTrue(result.isEmpty(), "Should return empty on HTTP 404");
    }

    @Test
    void getDefiData_cachesPreviousResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-defi.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<CoinGeckoDefiData> first  = defiService.getDefiData();
        Optional<CoinGeckoDefiData> second = defiService.getDefiData();

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertEquals(1, server.getRequestCount(), "Second call should use in-memory cache");
    }

    @Test
    void getDefiRaw_returnsJsonWithDataKey() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-defi.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<com.google.gson.JsonObject> result = defiService.getDefiRaw();

        assertTrue(result.isPresent(), "getDefiRaw() should return raw JSON");
        assertTrue(result.get().has("data"), "Raw DeFi response should contain 'data' key");
    }

    // ── /exchange_rates ───────────────────────────────────────────────────────

    @Test
    void getExchangeRates_parsesRatesMap() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-exchange-rates.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<CoinGeckoExchangeRates> result = defiService.getExchangeRates();

        assertTrue(result.isPresent(), "getExchangeRates() should return data");
        CoinGeckoExchangeRates rates = result.get();
        assertNotNull(rates.getRates(), "Rates map should not be null");
        assertFalse(rates.getRates().isEmpty(), "Rates map should not be empty");
    }

    @Test
    void getExchangeRates_returnsEmptyOnHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503));

        Optional<CoinGeckoExchangeRates> result = defiService.getExchangeRates();

        assertTrue(result.isEmpty(), "Should return empty on HTTP 503");
    }

    @Test
    void getExchangeRates_cachesPreviousResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-exchange-rates.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<CoinGeckoExchangeRates> first  = defiService.getExchangeRates();
        Optional<CoinGeckoExchangeRates> second = defiService.getExchangeRates();

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertEquals(1, server.getRequestCount(), "Second call should use in-memory cache");
    }

    @Test
    void getExchangeRates_rawContainsBtcEntry() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-exchange-rates.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<CoinGeckoExchangeRates> result = defiService.getExchangeRates();

        assertTrue(result.isPresent());
        assertTrue(result.get().getRates().containsKey("btc"),
                "Exchange rates should contain 'btc' entry");
        assertTrue(result.get().getRates().containsKey("usd"),
                "Exchange rates should contain 'usd' entry");
    }

    // ── Error handling ─────────────────────────────────────────────────────────

    @Test
    void getGlobalData_handles429WithRetryAfterHeader() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(429)
                .addHeader("Retry-After", "1"));

        Optional<CoinGeckoGlobalData> result = defiService.getGlobalData();

        assertTrue(result.isEmpty(), "Should return empty on rate limit 429");
    }

    @Test
    void getDefiData_returnsEmptyOn429() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(429)
                .addHeader("Retry-After", "1"));

        Optional<CoinGeckoDefiData> result = defiService.getDefiData();

        assertTrue(result.isEmpty(), "Should return empty on rate limit 429");
    }
}
