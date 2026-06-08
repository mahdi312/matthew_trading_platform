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
 * T-08: MockWebServer-based test for {@link FinnhubPriceService}.
 *
 * <p>Finnhub docs: https://finnhub.io/docs/api/quote
 * Endpoint shape: {@code /quote?symbol=AAPL&token=KEY}.
 */
class FinnhubPriceServiceTest {

    private MockWebServer server;
    private FinnhubPriceService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        OkHttpClient client = new PriceHttpConfig().priceHttpClient(5, 5, 0);
        HttpJsonClient http = new HttpJsonClient(client);
        MarketApiProperties keys = new MarketApiProperties();
        keys.setFinnhubKey("test-key");
        service = new FinnhubPriceService(http, keys);
        PriceServiceTestSupport.setBaseUrl(service, "baseUrl",
                server.url("").toString().replaceAll("/$", ""));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void getQuote_parsesCurrentPriceAndChange() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("finnhub-quote.json"))
                .addHeader("Content-Type", "application/json"));

        var quote = service.getQuote("AAPL");

        assertTrue(quote.isPresent());
        assertEquals("AAPL", quote.get().getSymbol());
        assertEquals(0, quote.get().getPrice().compareTo(new java.math.BigDecimal("174.25")));
        assertTrue(quote.get().isUp());
    }

    @Test
    void isEnabled_falseWhenKeyMissing() {
        MarketApiProperties empty = new MarketApiProperties();
        var unconfigured = new FinnhubPriceService(
                new HttpJsonClient(new PriceHttpConfig().priceHttpClient(5, 5, 0)),
                empty);
        assertFalse(unconfigured.isEnabled());
    }
}
