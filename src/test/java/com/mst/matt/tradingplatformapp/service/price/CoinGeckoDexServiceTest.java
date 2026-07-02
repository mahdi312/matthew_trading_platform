package com.mst.matt.tradingplatformapp.service.price;

import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.service.marketdata.DynamicOhlcvTableService;
import com.mst.matt.tradingplatformapp.config.PriceHttpConfig;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CoinGeckoDexService} (GeckoTerminal / Onchain endpoints).
 *
 * Endpoints tested:
 * - GET /onchain/networks                         → getNetworks() / getNetworkIds()
 * - GET /onchain/networks/{n}/pools               → getTopPools()
 * - GET /onchain/networks/trending_pools          → getTrendingPoolsGlobal()
 * - GET /onchain/networks/{n}/trending_pools      → getTrendingPoolsByNetwork()
 * - GET /onchain/networks/{n}/pools/{a}/ohlcv/{tf}→ getPoolOhlcv()
 * - GET /onchain/networks/{n}/tokens/{a}/pools    → getPoolsByToken()
 * - GET /onchain/search/pools                     → searchPools()
 *
 * DynamicOhlcvTableService is mocked to avoid DB dependency.
 */
class CoinGeckoDexServiceTest {

    private MockWebServer          server;
    private CoinGeckoService       coinGeckoService;
    private CoinGeckoDexService    dexService;
    private DynamicOhlcvTableService mockDynamic;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        OkHttpClient client = new PriceHttpConfig().priceHttpClient(5, 5, 0);
        coinGeckoService = new CoinGeckoService(client, new CoinGeckoRateLimiter());
        PriceServiceTestSupport.setBaseUrl(coinGeckoService, "baseUrl",
                server.url("").toString().replaceAll("/$", ""));
        PriceServiceTestSupport.setBaseUrl(coinGeckoService, "apiKey", "test-key");

        mockDynamic = mock(DynamicOhlcvTableService.class);
        doNothing().when(mockDynamic).upsertBars(anyString(), any(), anyList());
        when(mockDynamic.findBars(anyString(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(List.of());

        dexService = new CoinGeckoDexService(coinGeckoService, mockDynamic);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    // ── Networks ──────────────────────────────────────────────────────────────

    @Test
    void getNetworks_returnsDataWrapperWithNetworkArray() throws Exception {
        String networksJson = "[{\"id\":\"eth\",\"type\":\"network\"},{\"id\":\"bsc\",\"type\":\"network\"}]";
        server.enqueue(new MockResponse()
                .setBody(networksJson)
                .addHeader("Content-Type", "application/json"));

        Optional<JsonObject> result = dexService.getNetworks();

        assertTrue(result.isPresent(), "getNetworks() should return data");
        assertTrue(result.get().has("data"), "Result should be wrapped in a 'data' key");
    }

    @Test
    void getNetworks_returnsEmptyOnHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));

        Optional<JsonObject> result = dexService.getNetworks();

        assertTrue(result.isEmpty(), "Should return empty on HTTP error");
    }

    @Test
    void getNetworkIds_extractsIdStrings() throws Exception {
        String networksJson = "[{\"id\":\"eth\"},{\"id\":\"bsc\"},{\"id\":\"polygon_pos\"}]";
        server.enqueue(new MockResponse()
                .setBody(networksJson)
                .addHeader("Content-Type", "application/json"));

        List<String> ids = dexService.getNetworkIds();

        assertEquals(3, ids.size(), "Should return 3 network IDs");
        assertTrue(ids.contains("eth"), "Should contain 'eth'");
        assertTrue(ids.contains("bsc"), "Should contain 'bsc'");
    }

    @Test
    void getNetworks_cachedOnSecondCall() throws Exception {
        String networksJson = "[{\"id\":\"eth\"}]";
        server.enqueue(new MockResponse()
                .setBody(networksJson)
                .addHeader("Content-Type", "application/json"));

        dexService.getNetworks(); // first call — hits server
        dexService.getNetworks(); // second call — from cache

        assertEquals(1, server.getRequestCount(), "Second call should use cache");
    }

    // ── Top pools by network ──────────────────────────────────────────────────

    @Test
    void getTopPools_returnsDataWrapperWithPoolArray() throws Exception {
        String poolsJson = "[{\"id\":\"eth_0x88e6\"},{\"id\":\"eth_0xabcd\"}]";
        server.enqueue(new MockResponse()
                .setBody(poolsJson)
                .addHeader("Content-Type", "application/json"));

        Optional<JsonObject> result = dexService.getTopPools("eth", 1);

        assertTrue(result.isPresent(), "getTopPools() should return data");
        assertTrue(result.get().has("data"), "Result should have 'data' key");
        assertTrue(result.get().getAsJsonArray("data").size() > 0,
                "Data array should not be empty");
    }

    @Test
    void getTopPools_returnsEmptyOnHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404));

        Optional<JsonObject> result = dexService.getTopPools("eth", 1);

        assertTrue(result.isEmpty(), "Should return empty on HTTP 404");
    }

    @Test
    void getTopPools_cachedOnSecondCallSameNetwork() throws Exception {
        String poolsJson = "[{\"id\":\"eth_0x88e6\"}]";
        server.enqueue(new MockResponse()
                .setBody(poolsJson)
                .addHeader("Content-Type", "application/json"));

        dexService.getTopPools("eth", 1); // first — hits server
        dexService.getTopPools("eth", 1); // second — from cache

        assertEquals(1, server.getRequestCount(), "Second call for same network should use cache");
    }

    // ── Trending pools ────────────────────────────────────────────────────────

    @Test
    void getTrendingPoolsGlobal_returnsDataOnSuccess() throws Exception {
        String trendingJson = "{\"data\":[{\"id\":\"eth_0xabc\",\"type\":\"pool\"}]}";
        server.enqueue(new MockResponse()
                .setBody(trendingJson)
                .addHeader("Content-Type", "application/json"));

        Optional<JsonObject> result = dexService.getTrendingPoolsGlobal();

        assertTrue(result.isPresent(), "getTrendingPoolsGlobal() should return data");
    }

    @Test
    void getTrendingPoolsGlobal_returnsEmptyOnError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));

        Optional<JsonObject> result = dexService.getTrendingPoolsGlobal();

        assertTrue(result.isEmpty(), "Should return empty on HTTP 500");
    }

    @Test
    void getTrendingPoolsByNetwork_returnsDataForKnownNetwork() throws Exception {
        String trendingJson = "{\"data\":[{\"id\":\"eth_0xabc\"}]}";
        server.enqueue(new MockResponse()
                .setBody(trendingJson)
                .addHeader("Content-Type", "application/json"));

        Optional<JsonObject> result = dexService.getTrendingPoolsByNetwork("eth");

        assertTrue(result.isPresent(), "getTrendingPoolsByNetwork() should return data for 'eth'");
    }

    // ── Pool OHLCV ────────────────────────────────────────────────────────────

    @Test
    void getPoolOhlcv_parsesOhlcvList() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-pool-ohlcv.json"))
                .addHeader("Content-Type", "application/json"));

        List<OhlcvBar> bars = dexService.getPoolOhlcv(
                "eth",
                "0x88e6a0c2ddd26feeb64f039a2c41296fcb3f5640",
                "hour", 1, 100);

        assertFalse(bars.isEmpty(), "Pool OHLCV should return non-empty bar list");
        OhlcvBar first = bars.get(0);
        assertNotNull(first.getOpenTime(), "Bar should have an open time");
        assertNotNull(first.getOpen(),     "Bar should have an open price");
        assertNotNull(first.getClose(),    "Bar should have a close price");
        assertTrue(first.getVolume().compareTo(java.math.BigDecimal.ZERO) > 0,
                "Bar volume should be positive");
    }

    @Test
    void getPoolOhlcv_returnsEmptyOnHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));

        List<OhlcvBar> bars = dexService.getPoolOhlcv(
                "eth", "0x88e6a0c2ddd26feeb64f039a2c41296fcb3f5640",
                "hour", 1, 100);

        assertTrue(bars.isEmpty(), "Should return empty list on HTTP error");
    }

    @Test
    void getPoolOhlcv_persistsBarsToDb() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-pool-ohlcv.json"))
                .addHeader("Content-Type", "application/json"));

        dexService.getPoolOhlcv("eth",
                "0x88e6a0c2ddd26feeb64f039a2c41296fcb3f5640",
                "hour", 1, 100);

        // Should have called upsertBars once with non-empty bars
        verify(mockDynamic, atLeastOnce()).upsertBars(anyString(), any(), anyList());
    }

    @Test
    void getPoolOhlcvFromDb_returnsEmptyWhenNoData() {
        when(mockDynamic.findBars(anyString(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(List.of());

        List<OhlcvBar> bars = dexService.getPoolOhlcvFromDb(
                "eth", "0x88e6a0c2ddd26feeb64f039a2c41296fcb3f5640",
                "hour", 1, 100);

        assertTrue(bars.isEmpty(), "DB read should return empty when no data");
        assertEquals(0, server.getRequestCount(), "DB read should not make HTTP calls");
    }

    // ── Pools by token ────────────────────────────────────────────────────────

    @Test
    void getPoolsByToken_returnsDataOnSuccess() throws Exception {
        String poolsJson = "{\"data\":[{\"id\":\"eth_0xabc\",\"type\":\"pool\"}]}";
        server.enqueue(new MockResponse()
                .setBody(poolsJson)
                .addHeader("Content-Type", "application/json"));

        Optional<JsonObject> result = dexService.getPoolsByToken(
                "eth", "0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2", 1);

        assertTrue(result.isPresent(), "getPoolsByToken() should return data");
    }

    @Test
    void getPoolsByToken_returnsEmptyOnError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404));

        Optional<JsonObject> result = dexService.getPoolsByToken(
                "eth", "0xnonexistent", 1);

        assertTrue(result.isEmpty(), "Should return empty on HTTP 404");
    }

    // ── Search pools ──────────────────────────────────────────────────────────

    @Test
    void searchPools_returnsDataForValidQuery() throws Exception {
        String searchJson = "{\"data\":[{\"id\":\"eth_0x88e6\",\"type\":\"pool\"}]}";
        server.enqueue(new MockResponse()
                .setBody(searchJson)
                .addHeader("Content-Type", "application/json"));

        Optional<JsonObject> result = dexService.searchPools("WETH USDC");

        assertTrue(result.isPresent(), "searchPools() should return data for valid query");
    }

    @Test
    void searchPools_returnsEmptyOnHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));

        Optional<JsonObject> result = dexService.searchPools("WETH");

        assertTrue(result.isEmpty(), "Should return empty on HTTP error");
    }

    // ── Error handling ─────────────────────────────────────────────────────────

    @Test
    void getTopPools_handles429RateLimitGracefully() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(429)
                .addHeader("Retry-After", "1"));

        Optional<JsonObject> result = dexService.getTopPools("eth", 1);

        assertTrue(result.isEmpty(), "Should return empty on 429 rate limit");
    }
}
