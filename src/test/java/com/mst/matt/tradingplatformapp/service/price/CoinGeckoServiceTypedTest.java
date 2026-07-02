package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.config.PriceHttpConfig;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoinGeckoServiceTypedTest {

    private MockWebServer server;
    private CoinGeckoService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        OkHttpClient client = new PriceHttpConfig().priceHttpClient(5, 5, 0);
        service = new CoinGeckoService(client, new CoinGeckoRateLimiter());
        PriceServiceTestSupport.setBaseUrl(service, "baseUrl", server.url("").toString().replaceAll("/$", ""));
        PriceServiceTestSupport.setBaseUrl(service, "apiKey", "test-key");
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void getCoinsMarketsTyped_parsesMarketArray() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("coingecko-markets.json"))
                .addHeader("Content-Type", "application/json"));

        List<?> list = service.getCoinsMarketsTyped("usd", "bitcoin", "market_cap_desc", 50, 1, false, "24h");
        assertFalse(list.isEmpty());
    }

    @Test
    void getSimplePriceTyped_parsesSimpleObject() throws Exception {
        String body = "{" +
                "\"bitcoin\":{\"usd\":12345.67,\"usd_24h_change\":1.23,\"usd_market_cap\":999999999}\n" +
                "}";
        server.enqueue(new MockResponse().setBody(body).addHeader("Content-Type", "application/json"));

        Map<String, ?> map = service.getSimplePriceTyped("bitcoin", "usd", true, true, false);
        assertTrue(map.containsKey("bitcoin"));
    }
}
