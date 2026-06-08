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
 * T-08: MockWebServer-based test for {@link AlphaVantagePriceService}.
 *
 * <p>Alpha Vantage docs: https://www.alphavantage.co/documentation/#latestprice
 * Endpoint shape: {@code /query?function=GLOBAL_QUOTE&symbol=AAPL&apikey=KEY}.
 */
class AlphaVantagePriceServiceTest {

    private MockWebServer server;
    private AlphaVantagePriceService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        OkHttpClient client = new PriceHttpConfig().priceHttpClient(5, 5, 0);
        HttpJsonClient http = new HttpJsonClient(client);
        MarketApiProperties keys = new MarketApiProperties();
        keys.setAlphavantageKey("test-key");
        service = new AlphaVantagePriceService(http, keys);
        PriceServiceTestSupport.setBaseUrl(service, "baseUrl",
                server.url("/query").toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void getQuote_parsesGlobalQuoteResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("alphavantage-global-quote.json"))
                .addHeader("Content-Type", "application/json"));

        var quote = service.getQuote("AAPL");

        assertTrue(quote.isPresent());
        assertEquals("AAPL", quote.get().getSymbol());
        assertEquals(0, quote.get().getPrice().compareTo(new java.math.BigDecimal("174.2500")));
        assertEquals(0, quote.get().getChangePct24h().compareTo(new java.math.BigDecimal("1.0145")));
        assertTrue(quote.get().isUp());
    }

    @Test
    void supports_acceptsEquityAndForexSymbols() {
        // AlphaVantage advertises support for equities, forex, and crypto;
        // routing priority pushes crypto to Binance first.
        assertTrue(service.supports("AAPL"));
        assertTrue(service.supports("EURUSD"));
    }
}
