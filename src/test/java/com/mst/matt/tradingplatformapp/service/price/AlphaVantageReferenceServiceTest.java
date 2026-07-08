package com.mst.matt.tradingplatformapp.service.price;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import com.mst.matt.tradingplatformapp.config.PriceHttpConfig;
import com.mst.matt.tradingplatformapp.service.price.api.alphavantage.AlphaVantageSearchResult;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AlphaVantageReferenceService}.
 */
class AlphaVantageReferenceServiceTest {

    private MockWebServer server;
    private AlphaVantageReferenceService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        OkHttpClient client = new PriceHttpConfig().priceHttpClient(5, 5, 0);
        HttpJsonClient http = new HttpJsonClient(client);
        MarketApiProperties keys = new MarketApiProperties();
        keys.setAlphavantageKey("test-key");

        // Mock JdbcTemplate to avoid DB calls
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(String.class), anyString()))
                .thenReturn(List.of());
        when(jdbc.getDataSource()).thenReturn(null);

        service = new AlphaVantageReferenceService(http, keys, jdbc);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    // ─── SYMBOL_SEARCH parsing ────────────────────────────────────────────────

    @Test
    void symbolSearch_parsesMatchesCorrectly() {
        Gson gson = new Gson();
        AlphaVantageSearchResult result = gson.fromJson("""
                {
                  "bestMatches": [
                    {
                      "1. symbol": "IBM",
                      "2. name": "International Business Machines Corporation",
                      "3. type": "Equity",
                      "4. region": "United States",
                      "5. marketOpen": "09:30",
                      "6. marketClose": "16:00",
                      "7. timezone": "UTC-04",
                      "8. currency": "USD",
                      "9. matchScore": "1.0000"
                    },
                    {
                      "1. symbol": "IBM.LON",
                      "2. name": "International Business Machines Corporation",
                      "3. type": "Equity",
                      "4. region": "United Kingdom",
                      "5. marketOpen": "08:00",
                      "6. marketClose": "16:30",
                      "7. timezone": "UTC+01",
                      "8. currency": "GBX",
                      "9. matchScore": "0.7500"
                    }
                  ]
                }
                """, AlphaVantageSearchResult.class);

        assertNotNull(result);
        assertNotNull(result.bestMatches());
        assertEquals(2, result.bestMatches().size());

        AlphaVantageSearchResult.Match first = result.bestMatches().get(0);
        assertEquals("IBM", first.symbol());
        assertEquals("International Business Machines Corporation", first.name());
        assertEquals("Equity", first.type());
        assertEquals("United States", first.region());
        assertEquals("USD", first.currency());
        assertEquals("1.0000", first.matchScore());
        assertEquals("09:30", first.marketOpen());
        assertEquals("16:00", first.marketClose());
    }

    @Test
    void searchByType_filtersEquityMatches() {
        // Test filtering by type
        AlphaVantageSearchResult all = new AlphaVantageSearchResult(List.of(
                new AlphaVantageSearchResult.Match(
                        "IBM", "IBM Corp", "Equity", "US", "09:30", "16:00", "UTC-4", "USD", "1.0"),
                new AlphaVantageSearchResult.Match(
                        "IBMQ", "IBM ETF", "ETF", "US", "09:30", "16:00", "UTC-4", "USD", "0.8")
        ));

        List<AlphaVantageSearchResult.Match> equities = all.bestMatches().stream()
                .filter(m -> "Equity".equalsIgnoreCase(m.type()))
                .toList();

        assertEquals(1, equities.size());
        assertEquals("IBM", equities.get(0).symbol());
    }

    @Test
    void isEnabled_returnsTrueWhenKeyPresent() {
        assertTrue(service.isEnabled());
    }

    // ─── MARKET_STATUS parsing ────────────────────────────────────────────────

    @Test
    void marketStatus_parsesOpenStatus() {
        Gson gson = new Gson();
        JsonObject status = gson.fromJson("""
                {
                  "endpoint": "Global Market Open & Close Status",
                  "markets": [
                    {
                      "market_type": "Equity",
                      "region": "United States",
                      "primary_exchanges": "NASDAQ, NYSE",
                      "local_open": "09:30",
                      "local_close": "16:00",
                      "current_status": "open",
                      "notes": "Regular trading session"
                    }
                  ]
                }
                """, JsonObject.class);

        assertNotNull(status);
        assertTrue(status.has("markets"));
        JsonObject firstMarket = status.getAsJsonArray("markets")
                .get(0).getAsJsonObject();
        assertEquals("United States", firstMarket.get("region").getAsString());
        assertEquals("open", firstMarket.get("current_status").getAsString());
    }
}
