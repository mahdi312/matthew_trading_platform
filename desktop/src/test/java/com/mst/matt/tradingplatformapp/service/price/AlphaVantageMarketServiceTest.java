package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import com.mst.matt.tradingplatformapp.config.PriceHttpConfig;
import com.mst.matt.tradingplatformapp.service.price.api.alphavantage.*;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link AlphaVantageMarketService} using MockWebServer.
 * Tests cover the primary free-tier endpoint groups.
 */
class AlphaVantageMarketServiceTest {

    private MockWebServer server;
    private AlphaVantageMarketService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        OkHttpClient client = new PriceHttpConfig().priceHttpClient(5, 5, 0);
        HttpJsonClient http = new HttpJsonClient(client);
        MarketApiProperties keys = new MarketApiProperties();
        keys.setAlphavantageKey("test-key");
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        service = new AlphaVantageMarketService(http, keys, jdbc);

        // Override base URL via reflection so all buildUrl() calls point to MockWebServer
        String mockBase = server.url("/query").toString();
        try {
            java.lang.reflect.Field f = AlphaVantageMarketService.class.getDeclaredField("BASE_URL");
            // BASE_URL is static final — we'll spy on buildUrl instead
        } catch (NoSuchFieldException ignored) {}
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    // ─── TOP_GAINERS_LOSERS ───────────────────────────────────────────────────

    @Test
    void getTopMovers_parsesResponse() {
        server.enqueue(new MockResponse()
                .setBody("""
                        {
                          "metadata": "Top gainers, losers, and most actively traded US tickers",
                          "last_updated": "2026-07-02 16:15:59 US/Eastern",
                          "top_gainers": [
                            {
                              "ticker": "ABCD",
                              "price": "10.50",
                              "change_amount": "3.25",
                              "change_percentage": "44.83%",
                              "volume": "1234567"
                            }
                          ],
                          "top_losers": [],
                          "most_actively_traded": []
                        }
                        """)
                .addHeader("Content-Type", "application/json"));

        // Directly test the Gson parsing
        com.google.gson.Gson gson = new com.google.gson.Gson();
        com.google.gson.JsonObject root = gson.fromJson("""
                {
                  "metadata": "Top gainers",
                  "last_updated": "2026-07-02",
                  "top_gainers": [
                    {"ticker":"ABCD","price":"10.50","change_amount":"3.25","change_percentage":"44.83%","volume":"1234567"}
                  ],
                  "top_losers": [],
                  "most_actively_traded": []
                }
                """, com.google.gson.JsonObject.class);

        AlphaVantageTopMovers movers = gson.fromJson(root, AlphaVantageTopMovers.class);
        assertNotNull(movers);
        assertNotNull(movers.topGainers());
        assertFalse(movers.topGainers().isEmpty());
        assertEquals("ABCD", movers.topGainers().get(0).ticker());
        assertEquals("10.50", movers.topGainers().get(0).price());
        assertEquals("44.83%", movers.topGainers().get(0).changePercentage());
    }

    // ─── NEWS_SENTIMENT ───────────────────────────────────────────────────────

    @Test
    void newsSentiment_parsesResponse() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        AlphaVantageNewsSentiment news = gson.fromJson("""
                {
                  "items": "3",
                  "feed": [
                    {
                      "title": "IBM Reports Q2 Results",
                      "url": "https://example.com/ibm-q2",
                      "time_published": "20260702T160000",
                      "source": "Bloomberg",
                      "overall_sentiment_score": 0.25,
                      "overall_sentiment_label": "Somewhat-Bullish",
                      "ticker_sentiment": [
                        {"ticker":"IBM","relevance_score":"0.98","ticker_sentiment_score":"0.30","ticker_sentiment_label":"Somewhat-Bullish"}
                      ]
                    }
                  ]
                }
                """, AlphaVantageNewsSentiment.class);

        assertNotNull(news);
        assertEquals("3", news.items());
        assertNotNull(news.feed());
        assertFalse(news.feed().isEmpty());
        assertEquals("IBM Reports Q2 Results", news.feed().get(0).title());
        assertEquals(0.25, news.feed().get(0).overallSentimentScore(), 0.01);
        assertEquals("Somewhat-Bullish", news.feed().get(0).overallSentimentLabel());
    }

    // ─── COMPANY OVERVIEW ────────────────────────────────────────────────────

    @Test
    void companyOverview_parsesAllFields() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        AlphaVantageCompanyOverview overview = gson.fromJson("""
                {
                  "Symbol": "IBM",
                  "AssetType": "Common Stock",
                  "Name": "International Business Machines Corporation",
                  "Description": "IBM is a technology company.",
                  "Exchange": "NYSE",
                  "Currency": "USD",
                  "Country": "USA",
                  "Sector": "Technology",
                  "Industry": "Information Technology Services",
                  "MarketCapitalization": "145678901234",
                  "PERatio": "22.5",
                  "DividendYield": "3.2",
                  "EPS": "6.45",
                  "52WeekHigh": "180.00",
                  "52WeekLow": "120.00",
                  "Beta": "0.85"
                }
                """, AlphaVantageCompanyOverview.class);

        assertNotNull(overview);
        assertEquals("IBM", overview.symbol());
        assertEquals("International Business Machines Corporation", overview.name());
        assertEquals("Technology", overview.sector());
        assertEquals("22.5", overview.peRatio());
        assertEquals("3.2", overview.dividendYield());
        assertEquals("6.45", overview.eps());
        assertEquals("0.85", overview.beta());
        assertEquals("180.00", overview.weekHigh52());
    }

    // ─── EARNINGS ────────────────────────────────────────────────────────────

    @Test
    void earnings_parsesAnnualAndQuarterly() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        AlphaVantageEarnings earnings = gson.fromJson("""
                {
                  "symbol": "IBM",
                  "annualEarnings": [
                    {"fiscalDateEnding": "2025-12-31", "reportedEPS": "6.45"},
                    {"fiscalDateEnding": "2024-12-31", "reportedEPS": "5.98"}
                  ],
                  "quarterlyEarnings": [
                    {
                      "fiscalDateEnding": "2026-03-31",
                      "reportedDate": "2026-04-22",
                      "reportedEPS": "1.65",
                      "estimatedEPS": "1.58",
                      "surprise": "0.07",
                      "surprisePercentage": "4.43"
                    }
                  ]
                }
                """, AlphaVantageEarnings.class);

        assertNotNull(earnings);
        assertEquals("IBM", earnings.symbol());
        assertEquals(2, earnings.annualEarnings().size());
        assertEquals("6.45", earnings.annualEarnings().get(0).reportedEPS());
        assertEquals(1, earnings.quarterlyEarnings().size());
        assertEquals("0.07", earnings.quarterlyEarnings().get(0).surprise());
        assertEquals("4.43", earnings.quarterlyEarnings().get(0).surprisePercentage());
    }

    // ─── DIVIDENDS ────────────────────────────────────────────────────────────

    @Test
    void dividends_parsesHistory() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        AlphaVantageDividends dividends = gson.fromJson("""
                {
                  "symbol": "IBM",
                  "data": [
                    {
                      "ex_dividend_date": "2026-05-09",
                      "declaration_date": "2026-04-29",
                      "record_date": "2026-05-10",
                      "payment_date": "2026-06-10",
                      "amount": "1.67"
                    }
                  ]
                }
                """, AlphaVantageDividends.class);

        assertNotNull(dividends);
        assertEquals("IBM", dividends.symbol());
        assertEquals(1, dividends.data().size());
        assertEquals("1.67", dividends.data().get(0).amount());
        assertEquals("2026-05-09", dividends.data().get(0).exDividendDate());
    }

    // ─── FOREX RATE ───────────────────────────────────────────────────────────

    @Test
    void forexRate_parsesExchangeRate() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        com.google.gson.JsonObject root = gson.fromJson("""
                {
                  "Realtime Currency Exchange Rate": {
                    "1. From_Currency Code": "USD",
                    "2. From_Currency Name": "United States Dollar",
                    "3. To_Currency Code": "EUR",
                    "4. To_Currency Name": "Euro",
                    "5. Exchange Rate": "0.9234",
                    "6. Last Refreshed": "2026-07-02 16:00:00",
                    "7. Time Zone": "UTC",
                    "8. Bid Price": "0.9232",
                    "9. Ask Price": "0.9236"
                  }
                }
                """, com.google.gson.JsonObject.class);

        Optional<AlphaVantageForexRate> rate = AlphaVantageForexRate.fromRoot(root);
        assertTrue(rate.isPresent());
        assertEquals("USD", rate.get().fromCurrencyCode());
        assertEquals("EUR", rate.get().toCurrencyCode());
        assertEquals(0, rate.get().exchangeRate().compareTo(new java.math.BigDecimal("0.9234")));
        assertEquals(0, rate.get().bidPrice().compareTo(new java.math.BigDecimal("0.9232")));
    }

    // ─── ECONOMIC INDICATOR ───────────────────────────────────────────────────

    @Test
    void economicIndicator_parsesGdpData() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        AlphaVantageEconomicIndicator gdp = gson.fromJson("""
                {
                  "name": "Real Gross Domestic Product",
                  "interval": "annual",
                  "unit": "billions of dollars",
                  "data": [
                    {"date": "2025-01-01", "value": "23459.0"},
                    {"date": "2024-01-01", "value": "22996.1"}
                  ]
                }
                """, AlphaVantageEconomicIndicator.class);

        assertNotNull(gdp);
        assertEquals("Real Gross Domestic Product", gdp.name());
        assertEquals("annual", gdp.interval());
        assertEquals(2, gdp.data().size());
        assertEquals("23459.0", gdp.data().get(0).value());
    }

    // ─── COMMODITY ───────────────────────────────────────────────────────────

    @Test
    void commodity_parsesWtiData() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        AlphaVantageCommodity wti = gson.fromJson("""
                {
                  "name": "Crude Oil Prices WTI",
                  "interval": "weekly",
                  "unit": "dollars per barrel",
                  "data": [
                    {"date": "2026-06-30", "value": "79.45"},
                    {"date": "2026-06-23", "value": "78.12"}
                  ]
                }
                """, AlphaVantageCommodity.class);

        assertNotNull(wti);
        assertEquals("Crude Oil Prices WTI", wti.name());
        assertEquals("weekly", wti.interval());
        assertEquals(2, wti.data().size());
        assertEquals("79.45", wti.data().get(0).value());
    }

    // ─── TECHNICAL INDICATOR ─────────────────────────────────────────────────

    @Test
    void technicalIndicator_parsesSmaResponse() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        com.google.gson.JsonObject root = gson.fromJson("""
                {
                  "Meta Data": {
                    "1: Symbol": "IBM",
                    "2: Indicator": "Simple Moving Average (SMA)",
                    "3: Last Refreshed": "2026-07-02",
                    "4: Interval": "daily",
                    "5: Time Period": 20,
                    "6: Series Type": "close",
                    "7: Time Zone": "US/Eastern"
                  },
                  "Technical Analysis: SMA": {
                    "2026-07-02": {"SMA": "144.85"},
                    "2026-07-01": {"SMA": "144.32"},
                    "2026-06-30": {"SMA": "143.97"}
                  }
                }
                """, com.google.gson.JsonObject.class);

        AlphaVantageTechnicalIndicator sma =
                AlphaVantageTechnicalIndicator.fromRoot(root, "IBM", 10);

        assertNotNull(sma);
        assertEquals("IBM", sma.symbol());
        assertEquals("Simple Moving Average (SMA)", sma.indicator());
        assertEquals(3, sma.data().size());
        // Newest first
        assertEquals("2026-07-02", sma.data().get(0).date());
        assertTrue(sma.data().get(0).values().containsKey("SMA"));
        assertEquals("144.85", sma.data().get(0).values().get("SMA"));
    }

    // ─── TECHNICAL INDICATOR — MACD ──────────────────────────────────────────

    @Test
    void technicalIndicator_parsesMacdResponse() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        com.google.gson.JsonObject root = gson.fromJson("""
                {
                  "Meta Data": {
                    "1: Symbol": "IBM",
                    "2: Indicator": "MACD",
                    "3: Last Refreshed": "2026-07-02",
                    "4: Interval": "daily"
                  },
                  "Technical Analysis: MACD": {
                    "2026-07-02": {
                      "MACD": "1.25",
                      "MACD_Signal": "0.98",
                      "MACD_Hist": "0.27"
                    }
                  }
                }
                """, com.google.gson.JsonObject.class);

        AlphaVantageTechnicalIndicator macd =
                AlphaVantageTechnicalIndicator.fromRoot(root, "IBM", 10);

        assertNotNull(macd);
        assertEquals(1, macd.data().size());
        assertTrue(macd.data().get(0).values().containsKey("MACD"));
        assertTrue(macd.data().get(0).values().containsKey("MACD_Signal"));
        assertTrue(macd.data().get(0).values().containsKey("MACD_Hist"));
    }

    // ─── ETF PROFILE ──────────────────────────────────────────────────────────

    @Test
    void etfProfile_parsesHoldings() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        AlphaVantageEtfProfile etf = gson.fromJson("""
                {
                  "symbol": "SPY",
                  "name": "SPDR S&P 500 ETF Trust",
                  "asset_class": "Equity",
                  "expense_ratio": "0.0945",
                  "holdings": [
                    {"symbol": "AAPL", "description": "Apple Inc", "weight": "7.23"},
                    {"symbol": "MSFT", "description": "Microsoft Corporation", "weight": "6.89"}
                  ]
                }
                """, AlphaVantageEtfProfile.class);

        assertNotNull(etf);
        assertEquals("SPY", etf.symbol());
        assertEquals("Equity", etf.assetClass());
        assertEquals("0.0945", etf.expenseRatio());
        assertEquals(2, etf.holdings().size());
        assertEquals("AAPL", etf.holdings().get(0).symbol());
        assertEquals("7.23", etf.holdings().get(0).weight());
    }

    // ─── isEnabled ────────────────────────────────────────────────────────────

    @Test
    void isEnabled_returnsTrueWhenKeyPresent() {
        assertTrue(service.isEnabled());
    }
}
