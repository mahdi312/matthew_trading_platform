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
 * CoinGecko API v3:
 * GET /coins/markets?vs_currency=usd&ids=bitcoin
 * GET /coins/bitcoin/ohlc?vs_currency=usd&days=30
 * Docs: https://docs.coingecko.com/reference/coins-markets
 */
class CoinGeckoServiceTest {

    private MockWebServer server;
    private CoinGeckoService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        OkHttpClient client = new PriceHttpConfig().priceHttpClient(5, 5, 0);
        service = new CoinGeckoService(client);
        PriceServiceTestSupport.setBaseUrl(service, "baseUrl", server.url("").toString().replaceAll("/$", ""));
        PriceServiceTestSupport.setBaseUrl(service, "apiKey", "test-key");
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void getQuote_parsesMarketsArrayWithNumericPrices() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-markets.json"))
                .addHeader("Content-Type", "application/json"));

        var quote = service.getQuote("BTC");

        assertTrue(quote.isPresent());
        assertEquals("BTC", quote.get().getSymbol());
        assertEquals("Bitcoin", quote.get().getAssetName());
        assertTrue(quote.get().getPrice().doubleValue() > 90000);
    }

    @Test
    void getOhlcv_parsesOhlcTupleArray() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-ohlc.json"))
                .addHeader("Content-Type", "application/json"));

        List<?> bars = service.getOhlcv("BTC", "1d", 1);

        assertEquals(1, bars.size());
    }

    @Test
    void supports_mapsUsdtSuffixToBaseCoin() {
        assertTrue(service.supports("BTCUSDT"));
        assertTrue(service.supports("ETH"));
        assertFalse(service.supports("AAPL"));
    }
}
