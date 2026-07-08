package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.config.PriceHttpConfig;
import com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoNftCollection;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CoinGeckoNftService}.
 *
 * Endpoints tested:
 * - GET /nfts/list               → listCollections() / getTopCollections()
 * - GET /nfts/{id}               → getCollectionDetail()
 * - GET /nfts/{platform}/contract/{address} → getCollectionByContract()
 *
 * JdbcTemplate is mocked — no DB required.
 */
class CoinGeckoNftServiceTest {

    private MockWebServer      server;
    private CoinGeckoService   coinGeckoService;
    private CoinGeckoNftService nftService;
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

        nftService = new CoinGeckoNftService(coinGeckoService, mockJdbc);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    // ── /nfts/list ────────────────────────────────────────────────────────────

    @Test
    void listCollections_parsesNftCollectionList() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-nfts-list.json"))
                .addHeader("Content-Type", "application/json"));

        List<CoinGeckoNftCollection> result = nftService.listCollections(50, 1, null);

        assertFalse(result.isEmpty(), "listCollections() should return a non-empty list");
        CoinGeckoNftCollection first = result.get(0);
        assertEquals("bored-ape-yacht-club", first.getId(),
                "First collection should be BAYC");
        assertEquals("Bored Ape Yacht Club", first.getName(),
                "Name should be parsed correctly");
        assertEquals("BAYC", first.getSymbol(),
                "Symbol should be BAYC");
        assertEquals("ethereum", first.getAssetPlatformId(),
                "Platform should be ethereum");
    }

    @Test
    void listCollections_parsesFloorPriceInUsd() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-nfts-list.json"))
                .addHeader("Content-Type", "application/json"));

        List<CoinGeckoNftCollection> result = nftService.listCollections(50, 1, null);

        assertFalse(result.isEmpty());
        CoinGeckoNftCollection bayc = result.get(0);
        assertNotNull(bayc.getFloorPrice(), "Floor price map should not be null");
        assertTrue(bayc.getFloorPrice().containsKey("usd"), "Floor price should contain USD entry");
        assertEquals(0, bayc.getFloorPrice().get("usd").compareTo(new BigDecimal("25000.0")),
                "BAYC floor price in USD should be 25000");
    }

    @Test
    void listCollections_returnsEmptyOnHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));

        List<CoinGeckoNftCollection> result = nftService.listCollections(50, 1, null);

        assertTrue(result.isEmpty(), "Should return empty list on HTTP error");
    }

    @Test
    void listCollections_returnsEmptyOn404() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404));

        List<CoinGeckoNftCollection> result = nftService.listCollections(50, 1, null);

        assertTrue(result.isEmpty(), "Should return empty on HTTP 404");
    }

    @Test
    void listCollections_cachedOnRepeatCall() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-nfts-list.json"))
                .addHeader("Content-Type", "application/json"));

        List<CoinGeckoNftCollection> first  = nftService.listCollections(50, 1, null);
        List<CoinGeckoNftCollection> second = nftService.listCollections(50, 1, null);

        assertFalse(first.isEmpty());
        assertEquals(first.size(), second.size(), "Cached result should match first call");
        assertEquals(1, server.getRequestCount(), "Second call should use cache");
    }

    @Test
    void getTopCollections_returns50DefaultCollections() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-nfts-list.json"))
                .addHeader("Content-Type", "application/json"));

        List<CoinGeckoNftCollection> result = nftService.getTopCollections();

        assertFalse(result.isEmpty(), "getTopCollections() should return collections");
    }

    // ── /nfts/{id} ────────────────────────────────────────────────────────────

    @Test
    void getCollectionDetail_parsesDetailedNftData() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-nft-detail.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<CoinGeckoNftCollection> result =
                nftService.getCollectionDetail("bored-ape-yacht-club");

        assertTrue(result.isPresent(), "getCollectionDetail() should return data");
        CoinGeckoNftCollection collection = result.get();
        assertEquals("bored-ape-yacht-club", collection.getId(),
                "Collection ID should match requested id");
        assertEquals("Bored Ape Yacht Club", collection.getName(),
                "Collection name should be parsed correctly");
    }

    @Test
    void getCollectionDetail_parsesMarketCapAndVolume() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-nft-detail.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<CoinGeckoNftCollection> result =
                nftService.getCollectionDetail("bored-ape-yacht-club");

        assertTrue(result.isPresent());
        CoinGeckoNftCollection collection = result.get();
        assertNotNull(collection.getMarketCap(), "Market cap should not be null");
        assertNotNull(collection.getVolume24h(), "Volume 24h should not be null");
        assertTrue(collection.getMarketCap().get("usd").compareTo(BigDecimal.ZERO) > 0,
                "Market cap USD should be positive");
    }

    @Test
    void getCollectionDetail_returnsEmptyOnHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404));

        Optional<CoinGeckoNftCollection> result =
                nftService.getCollectionDetail("nonexistent-collection");

        assertTrue(result.isEmpty(), "Should return empty for 404");
    }

    @Test
    void getCollectionDetail_cachedOnSecondCall() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-nft-detail.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<CoinGeckoNftCollection> first =
                nftService.getCollectionDetail("bored-ape-yacht-club");
        Optional<CoinGeckoNftCollection> second =
                nftService.getCollectionDetail("bored-ape-yacht-club");

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertEquals(1, server.getRequestCount(), "Second call should use cache");
    }

    // ── /nfts/{platform}/contract/{address} ───────────────────────────────────

    @Test
    void getCollectionByContract_parsesCollectionData() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-nft-detail.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<CoinGeckoNftCollection> result = nftService.getCollectionByContract(
                "ethereum", "0xbc4ca0eda7647a8ab7c2061c2e118a18a936f13d");

        assertTrue(result.isPresent(), "getCollectionByContract() should return data");
        assertNotNull(result.get().getId(), "Collection ID should not be null");
    }

    @Test
    void getCollectionByContract_returnsEmptyOnHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(404));

        Optional<CoinGeckoNftCollection> result = nftService.getCollectionByContract(
                "ethereum", "0xinvalid");

        assertTrue(result.isEmpty(), "Should return empty for 404");
    }

    // ── DB persistence verification ───────────────────────────────────────────

    @Test
    void listCollections_persistsSnapshotToDb() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-nfts-list.json"))
                .addHeader("Content-Type", "application/json"));

        nftService.listCollections(50, 1, null);

        // DDL should have been executed to ensure the table exists
        verify(mockJdbc, atLeastOnce()).execute(anyString());
        // Update/upsert should have been called for each collection
        verify(mockJdbc, atLeastOnce()).update(anyString(), any(Object[].class));
    }

    @Test
    void getCollectionDetail_persistsSnapshotToDb() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-nft-detail.json"))
                .addHeader("Content-Type", "application/json"));

        nftService.getCollectionDetail("bored-ape-yacht-club");

        // DDL table creation + upsert should both have been called
        verify(mockJdbc, atLeastOnce()).execute(anyString());
        verify(mockJdbc, atLeastOnce()).update(anyString(), any(Object[].class));
    }

    // ── Error handling ─────────────────────────────────────────────────────────

    @Test
    void listCollections_handles429RateLimitGracefully() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(429)
                .addHeader("Retry-After", "1"));

        List<CoinGeckoNftCollection> result = nftService.listCollections(50, 1, null);

        assertTrue(result.isEmpty(), "Should return empty on rate limit 429");
    }

    @Test
    void listCollections_handlesMultipleCollectionsInList() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-nfts-list.json"))
                .addHeader("Content-Type", "application/json"));

        List<CoinGeckoNftCollection> result = nftService.listCollections(50, 1, null);

        // Fixture has 2 collections (BAYC and CryptoPunks)
        assertEquals(2, result.size(), "Should return 2 collections from fixture");

        // Verify CryptoPunks is also present
        boolean hasCryptoPunks = result.stream()
                .anyMatch(c -> "cryptopunks".equals(c.getId()));
        assertTrue(hasCryptoPunks, "CryptoPunks should be in the list");
    }
}
