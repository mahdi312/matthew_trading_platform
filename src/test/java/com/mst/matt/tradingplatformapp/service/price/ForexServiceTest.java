package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.config.PriceHttpConfig;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Frankfurter ECB forex API:
 * GET /latest?from=EUR&to=USD
 * GET /{start}..{end}?from=EUR&to=USD
 * Docs: https://www.frankfurter.app/docs/
 */
class ForexServiceTest {

    private MockWebServer server;
    private ForexService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        OkHttpClient client = new PriceHttpConfig().priceHttpClient(5, 5, 0);
        service = new ForexService(client);
        PriceServiceTestSupport.setBaseUrl(service, "baseUrl", server.url("").toString().replaceAll("/$", ""));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void getQuote_parsesLatestRatesWithNumericValues() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("frankfurter-latest.json"))
                .addHeader("Content-Type", "application/json"));

        var quote = service.getQuote("EURUSD");

        assertTrue(quote.isPresent());
        assertEquals("EUR/USD", quote.get().getSymbol());
        assertTrue(quote.get().getPrice().doubleValue() > 1.0);
    }

    @Test
    void getOhlcv_parsesHistoricalDailyRates() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("frankfurter-history.json"))
                .addHeader("Content-Type", "application/json"));

        List<?> bars = service.getOhlcv("EURUSD", "1d", 3);

        assertEquals(3, bars.size());
    }

    @Test
    void supports_acceptsSixLetterPairsWithSlashes() {
        assertTrue(service.supports("EUR/USD"));
        assertTrue(service.supports("GBPUSD"));
        assertFalse(service.supports("AAPL"));
    }
}
