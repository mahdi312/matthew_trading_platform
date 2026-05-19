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
 * Binance public market-data API shape:
 * GET /api/v3/ticker/24hr?symbol=BTCUSDT
 * GET /api/v3/klines?symbol=BTCUSDT&interval=1h&limit=200
 * Docs: https://binance-docs.github.io/apidocs/spot/en/#24hr-ticker-price-change-statistics
 */
class BinanceServiceTest {

    private MockWebServer server;
    private BinanceService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        OkHttpClient client = new PriceHttpConfig().priceHttpClient(5, 5, 0);
        service = new BinanceService(client);
        PriceServiceTestSupport.setBaseUrl(service, "baseUrl", server.url("").toString().replaceAll("/$", ""));
        PriceServiceTestSupport.setBaseUrl(service, "fallbackUrl", "http://127.0.0.1:1");
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void getQuote_parses24hrTickerResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("binance-ticker-24hr.json"))
                .addHeader("Content-Type", "application/json"));

        var quote = service.getQuote("BTCUSDT");

        assertTrue(quote.isPresent());
        assertEquals("BTCUSDT", quote.get().getSymbol());
        assertEquals(0, quote.get().getPrice().compareTo(new java.math.BigDecimal("95000.50")));
        assertEquals("Binance", quote.get().getExchange());
    }

    @Test
    void getQuote_normalizesBareCryptoSymbolToUsdtPair() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("binance-ticker-24hr.json"))
                .addHeader("Content-Type", "application/json"));

        var quote = service.getQuote("BTC");

        assertTrue(quote.isPresent());
        assertEquals("BTCUSDT", quote.get().getSymbol());
    }

    @Test
    void getOhlcv_parsesKlinesArrayResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("binance-klines.json"))
                .addHeader("Content-Type", "application/json"));

        List<?> bars = service.getOhlcv("BTCUSDT", "1h", 1);

        assertEquals(1, bars.size());
    }

    @Test
    void supports_acceptsUsdtPairsAndBareTickers() {
        assertTrue(service.supports("ETHUSDT"));
        assertTrue(service.supports("SOL"));
        assertFalse(service.supports("AAPL"));
    }
}
