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
 * Yahoo Finance chart API (unofficial):
 * GET /v8/finance/chart/{symbol}?interval=1d&range=5d
 * Docs: https://github.com/ranaroussi/yfinance (endpoint used by community clients)
 */
class YahooFinanceServiceTest {

    private MockWebServer server;
    private YahooFinanceService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        OkHttpClient client = new PriceHttpConfig().priceHttpClient(5, 5, 0);
        service = new YahooFinanceService(client);
        PriceServiceTestSupport.setBaseUrl(service, "baseUrl", server.url("").toString().replaceAll("/$", ""));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void getQuote_parsesChartMetaWithNumericFields() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("yahoo-chart-aapl.json"))
                .addHeader("Content-Type", "application/json"));

        var quote = service.getQuote("AAPL");

        assertTrue(quote.isPresent());
        assertEquals("AAPL", quote.get().getSymbol());
        assertEquals("Apple Inc.", quote.get().getAssetName());
        assertTrue(quote.get().getPrice().doubleValue() > 0);
    }

    @Test
    void getOhlcv_parsesTimestampAndQuoteArrays() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("yahoo-chart-aapl.json"))
                .addHeader("Content-Type", "application/json"));

        List<?> bars = service.getOhlcv("AAPL", "1d", 2);

        assertEquals(2, bars.size());
    }

    @Test
    void supports_rejectsForexPairsAndCryptoUsdt() {
        assertTrue(service.supports("MSFT"));
        assertFalse(service.supports("EURUSD"));
        assertFalse(service.supports("BTCUSDT"));
    }
}
