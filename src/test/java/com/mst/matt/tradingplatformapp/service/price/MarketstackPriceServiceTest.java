package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
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
 * T-08: MockWebServer-based test for {@link MarketstackPriceService}.
 *
 * <p>Marketstack docs: https://marketstack.com/documentation
 * Endpoint shape: {@code /eod?access_key=KEY&symbols=AAPL&limit=2}.
 */
class MarketstackPriceServiceTest {

    private MockWebServer server;
    private MarketstackPriceService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        OkHttpClient client = new PriceHttpConfig().priceHttpClient(5, 5, 0);
        HttpJsonClient http = new HttpJsonClient(client);
        MarketApiProperties keys = new MarketApiProperties();
        keys.setMarketstackKey("test-key");
        service = new MarketstackPriceService(http, keys);
        PriceServiceTestSupport.setBaseUrl(service, "baseUrl",
                server.url("").toString().replaceAll("/$", ""));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void getOhlcv_parsesDataArray() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("marketstack-eod.json"))
                .addHeader("Content-Type", "application/json"));

        List<?> bars = service.getOhlcv("AAPL", "1d", 5);

        assertEquals(1, bars.size());
    }
}
