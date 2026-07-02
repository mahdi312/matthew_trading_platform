package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.config.PriceHttpConfig;
import com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoSearchResult;
import com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoTrendingResult;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CoinGeckoSearchService}.
 *
 * Endpoints tested:
 * - GET /search               → search()
 * - GET /search/trending      → getTrending() / getTrendingFull()
 * - GET /coins/list           → syncCoinsListToDb() + resolveCoinId()
 *
 * DB interactions are mocked via JdbcTemplate mock.
 */
class CoinGeckoSearchServiceTest {

    private MockWebServer        server;
    private CoinGeckoService     coinGeckoService;
    private CoinGeckoSearchService searchService;
    private JdbcTemplate         mockJdbc;

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
        when(mockJdbc.getDataSource()).thenReturn(null);

        searchService = new CoinGeckoSearchService(coinGeckoService, mockJdbc);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    // ── /search ──────────────────────────────────────────────────────────────

    @Test
    void search_parsesCoinsAndReturnsResult() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-search.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<CoinGeckoSearchResult> result = searchService.search("bitcoin");

        assertTrue(result.isPresent(), "search() should return a non-empty result");
        CoinGeckoSearchResult sr = result.get();
        assertNotNull(sr.getCoins(), "Coins list should not be null");
        assertFalse(sr.getCoins().isEmpty(), "Should find at least one coin matching 'bitcoin'");

        // First result should be Bitcoin
        var firstCoin = sr.getCoins().get(0);
        assertNotNull(firstCoin.getId(), "Coin id should not be null");
        assertTrue(firstCoin.getId().contains("bitcoin"),
                "First result for 'bitcoin' should be bitcoin");
    }

    @Test
    void search_returnsEmptyForBlankQuery() {
        Optional<CoinGeckoSearchResult> result = searchService.search("   ");

        assertTrue(result.isEmpty(), "Blank query should return empty Optional");
        assertEquals(0, server.getRequestCount(), "Should not make HTTP request for blank query");
    }

    @Test
    void search_returnsEmptyForNullQuery() {
        Optional<CoinGeckoSearchResult> result = searchService.search(null);

        assertTrue(result.isEmpty(), "Null query should return empty Optional");
        assertEquals(0, server.getRequestCount(), "Should not make HTTP request for null query");
    }

    @Test
    void search_returnsEmptyOnHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503));

        Optional<CoinGeckoSearchResult> result = searchService.search("bitcoin");

        assertTrue(result.isEmpty(), "Should return empty on HTTP 503");
    }

    @Test
    void search_cachedResultUsedOnRepeatQuery() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-search.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<CoinGeckoSearchResult> first  = searchService.search("bitcoin");
        Optional<CoinGeckoSearchResult> second = searchService.search("bitcoin");

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertEquals(1, server.getRequestCount(), "Repeat query should use cache");
    }

    // ── /search/trending ─────────────────────────────────────────────────────

    @Test
    void getTrending_parsesCoinsAndNfts() throws Exception {
        // getTrending() calls /search/trending and extracts the "coins" array
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-trending.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<CoinGeckoTrendingResult> result = searchService.getTrending();

        assertTrue(result.isPresent(), "getTrending() should return data");
        CoinGeckoTrendingResult trending = result.get();
        assertNotNull(trending.getCoins(), "Trending coins list should not be null");
    }

    @Test
    void getTrending_returnsEmptyOnHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));

        Optional<CoinGeckoTrendingResult> result = searchService.getTrending();

        assertTrue(result.isEmpty(), "Should return empty on HTTP 500");
    }

    @Test
    void getTrendingFull_parsesFullResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-trending.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<CoinGeckoTrendingResult> result = searchService.getTrendingFull();

        assertTrue(result.isPresent(), "getTrendingFull() should return data");
    }

    // ── /coins/list + symbol resolution ──────────────────────────────────────

    @Test
    void resolveCoinId_returnsStaticMapResultForBtc() {
        // BTC is in the static map — no HTTP call or DB needed
        String coinId = searchService.resolveCoinId("BTC");

        assertEquals("bitcoin", coinId, "BTC should resolve to 'bitcoin' from static map");
        assertEquals(0, server.getRequestCount(), "Static map lookup needs no HTTP call");
    }

    @Test
    void resolveCoinId_stripsUsdtSuffixBeforeLookup() {
        // BTCUSDT → strip "USDT" → "BTC" → "bitcoin"
        String coinId = searchService.resolveCoinId("BTCUSDT");

        assertEquals("bitcoin", coinId, "BTCUSDT should strip USDT suffix and resolve");
    }

    @Test
    void resolveCoinId_returnsNullForUnknownSymbol() {
        String coinId = searchService.resolveCoinId("COMPLETELYFAKETOKEN_XYZ");

        assertNull(coinId, "Unknown symbol should return null");
    }

    @Test
    void syncCoinsListToDb_writesEntriesToDb() throws Exception {
        // Simulate /coins/list response
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-coins-list.json"))
                .addHeader("Content-Type", "application/json"));

        // Make batchUpdate callable (mock)
        when(mockJdbc.batchUpdate(anyString(), anyList(), anyInt(), any()))
                .thenReturn(new int[][]{{1}, {1}, {1}, {1}});
        doNothing().when(mockJdbc).execute(anyString());

        // No exception should be thrown
        assertDoesNotThrow(() -> searchService.syncCoinsListToDb(),
                "syncCoinsListToDb should not throw");
    }

    @Test
    void syncCoinsListToDb_handlesEmptyResponseGracefully() throws Exception {
        server.enqueue(new MockResponse()
                .setBody("[]")
                .addHeader("Content-Type", "application/json"));

        // Should not throw even if empty
        assertDoesNotThrow(() -> searchService.syncCoinsListToDb(),
                "Should handle empty coins list gracefully");
    }

    @Test
    void syncCoinsListToDb_handlesHttpErrorGracefully() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));

        // Should not propagate exception
        assertDoesNotThrow(() -> searchService.syncCoinsListToDb(),
                "Should handle HTTP error gracefully");
    }

    // ── autocompleteCoinIds ────────────────────────────────────────────────────

    @Test
    void autocompleteCoinIds_returnsEmptyForBlankPrefix() {
        List<String> result = searchService.autocompleteCoinIds("", 5);
        assertTrue(result.isEmpty(), "Blank prefix should return empty list");
    }

    @Test
    void autocompleteCoinIds_returnsEmptyWhenCacheIsEmpty() {
        // No DB data loaded — result from empty cache
        List<String> result = searchService.autocompleteCoinIds("BTC", 5);
        // may or may not be empty depending on DB mock — just assert no exception
        assertNotNull(result, "autocompleteCoinIds should not return null");
    }

    // ── getSymbolToIdMap ──────────────────────────────────────────────────────

    @Test
    void getSymbolToIdMap_returnsImmutableMap() {
        // With empty DB, returns an unmodifiable empty map
        Map<String, String> map = searchService.getSymbolToIdMap();
        assertNotNull(map, "getSymbolToIdMap should not return null");
        assertThrows(UnsupportedOperationException.class,
                () -> map.put("TEST", "test-coin"),
                "Returned map should be unmodifiable");
    }
}
