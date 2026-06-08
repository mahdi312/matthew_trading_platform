package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import com.mst.matt.tradingplatformapp.config.PriceHttpConfig;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * T-08: MockWebServer-based test for {@link PolygonPriceService}.
 *
 * <p>Polygon docs: https://polygon.io/docs/stocks/get_v2_snapshot_locale_us_markets_stocks_tickers__stocksticker_
 * Endpoint shape: {@code /v2/snapshot/locale/us/markets/stocks/tickers/AAPL?apiKey=KEY}.
 */
class PolygonPriceServiceTest {

    private MockWebServer server;
    private PolygonPriceService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        OkHttpClient client = new PriceHttpConfig().priceHttpClient(5, 5, 0);
        HttpJsonClient http = new HttpJsonClient(client);
        MarketApiProperties keys = new MarketApiProperties();
        keys.setPolygonKey("test-key");
        service = new PolygonPriceService(http, keys);
        PriceServiceTestSupport.setBaseUrl(service, "baseUrl",
                server.url("").toString().replaceAll("/$", ""));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void getQuote_parsesSnapshotResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("polygon-snapshot.json"))
                .addHeader("Content-Type", "application/json"));

        var quote = service.getQuote("AAPL");

        assertTrue(quote.isPresent());
        assertEquals("AAPL", quote.get().getSymbol());
        assertEquals(0, quote.get().getPrice().compareTo(new java.math.BigDecimal("174.25")));
    }
}
