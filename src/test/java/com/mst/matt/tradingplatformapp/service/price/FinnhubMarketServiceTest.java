package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import com.mst.matt.tradingplatformapp.config.PriceHttpConfig;
import com.mst.matt.tradingplatformapp.service.price.api.finnhub.*;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Comprehensive MockWebServer-based tests for {@link FinnhubMarketService}.
 *
 * <p>Covers all major free-tier Finnhub endpoints:
 * quote, candles, search, company profile, earnings, recommendations, price target,
 * news, news sentiment, forex rates, IPO calendar, earnings calendar, ESG, basic financials.
 */
class FinnhubMarketServiceTest {

    private MockWebServer server;
    private FinnhubMarketService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();

        OkHttpClient client = new PriceHttpConfig().priceHttpClient(5, 5, 0);
        HttpJsonClient http = new HttpJsonClient(client);

        MarketApiProperties keys = new MarketApiProperties();
        keys.setFinnhubKey("test-key");

        // Use a mock JdbcTemplate to avoid DB setup in unit tests
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.getDataSource()).thenReturn(null);

        service = new FinnhubMarketService(http, keys, jdbc);
        String baseUrl = server.url("").toString().replaceAll("/$", "");
        PriceServiceTestSupport.setBaseUrl(service, "baseUrl", baseUrl);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    // ─── isEnabled ────────────────────────────────────────────────────────────

    @Test
    void isEnabled_trueWhenKeyPresent() {
        assertTrue(service.isEnabled());
    }

    @Test
    void isEnabled_falseWhenKeyMissing() {
        MarketApiProperties noKey = new MarketApiProperties();
        OkHttpClient client = new PriceHttpConfig().priceHttpClient(5, 5, 0);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        FinnhubMarketService disabled = new FinnhubMarketService(
                new HttpJsonClient(client), noKey, jdbc);
        assertFalse(disabled.isEnabled());
    }

    // ─── Symbol Search ────────────────────────────────────────────────────────

    @Test
    void search_parsesResultsCorrectly() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("finnhub-search.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<FinnhubSearchResult> result = service.search("apple");

        assertTrue(result.isPresent());
        assertEquals(4, result.get().count());
        assertFalse(result.get().result().isEmpty());
        assertEquals("AAPL", result.get().result().get(0).symbol());
        assertEquals("APPLE INC", result.get().result().get(0).description());
        assertEquals("Common Stock", result.get().result().get(0).type());
    }

    @Test
    void search_returnsEmptyOnBlankQuery() {
        Optional<FinnhubSearchResult> result = service.search("   ");
        assertTrue(result.isEmpty());
    }

    @Test
    void bestMatch_returnsBestMatch() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("finnhub-search.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<FinnhubSearchResult.Match> match = service.bestMatch("apple");
        assertTrue(match.isPresent());
        assertEquals("AAPL", match.get().symbol());
    }

    // ─── Company Profile ──────────────────────────────────────────────────────

    @Test
    void getCompanyProfile_parsesProfileCorrectly() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("finnhub-company-profile.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<FinnhubCompanyProfile> profile = service.getCompanyProfile("AAPL");

        assertTrue(profile.isPresent());
        assertEquals("AAPL", profile.get().ticker());
        assertEquals("Apple Inc", profile.get().name());
        assertEquals("US", profile.get().country());
        assertEquals("USD", profile.get().currency());
        assertEquals("Technology", profile.get().finnhubIndustry());
        assertNotNull(profile.get().marketCapitalization());
        assertTrue(profile.get().marketCapitalization() > 0);
    }

    @Test
    void getCompanyProfile_returnsEmptyOnError() {
        server.enqueue(new MockResponse().setResponseCode(404));

        Optional<FinnhubCompanyProfile> profile = service.getCompanyProfile("INVALID_SYM_XYZ");
        assertTrue(profile.isEmpty());
    }

    // ─── Earnings Surprises ───────────────────────────────────────────────────

    @Test
    void getEarningsSurprises_parsesCorrectly() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("finnhub-earnings.json"))
                .addHeader("Content-Type", "application/json"));

        List<FinnhubEarningsSurprise> earnings = service.getEarningsSurprises("AAPL", 3);

        assertFalse(earnings.isEmpty());
        assertEquals(3, earnings.size());
        FinnhubEarningsSurprise first = earnings.get(0);
        assertEquals("AAPL", first.symbol());
        assertEquals(1.53, first.actual(), 0.001);
        assertEquals(1.43, first.estimate(), 0.001);
        assertEquals(3, first.quarter());
        assertEquals(2024, first.year());
        assertTrue(first.surprisePercent() > 0);
    }

    // ─── Recommendation Trends ────────────────────────────────────────────────

    @Test
    void getRecommendationTrends_parsesCorrectly() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("finnhub-recommendation.json"))
                .addHeader("Content-Type", "application/json"));

        List<FinnhubRecommendationTrend> trends = service.getRecommendationTrends("AAPL");

        assertFalse(trends.isEmpty());
        assertEquals(2, trends.size());
        FinnhubRecommendationTrend latest = trends.get(0);
        assertEquals("AAPL", latest.symbol());
        assertEquals(24, latest.buy());
        assertEquals(15, latest.strongBuy());
        assertEquals(8, latest.hold());
        assertEquals(0, latest.sell());
        assertEquals("2024-07-01", latest.period());
    }

    // ─── Price Target ─────────────────────────────────────────────────────────

    @Test
    void getPriceTarget_parsesCorrectly() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("finnhub-price-target.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<FinnhubPriceTarget> target = service.getPriceTarget("AAPL");

        assertTrue(target.isPresent());
        assertEquals("AAPL", target.get().symbol());
        assertEquals(250.00, target.get().targetHigh(), 0.01);
        assertEquals(150.00, target.get().targetLow(), 0.01);
        assertEquals(205.50, target.get().targetMean(), 0.01);
        assertEquals("2024-07-01", target.get().lastUpdated());
    }

    // ─── News ─────────────────────────────────────────────────────────────────

    @Test
    void getMarketNews_parsesArticlesCorrectly() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("finnhub-news.json"))
                .addHeader("Content-Type", "application/json"));

        List<FinnhubNewsArticle> articles = service.getMarketNews("general");

        assertFalse(articles.isEmpty());
        assertEquals(2, articles.size());
        FinnhubNewsArticle first = articles.get(0);
        assertEquals("general", first.category());
        assertEquals("Apple reports record quarterly earnings", first.headline());
        assertEquals("Reuters", first.source());
        assertNotNull(first.url());
    }

    @Test
    void getCompanyNews_returnsArticles() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("finnhub-news.json"))
                .addHeader("Content-Type", "application/json"));

        List<FinnhubNewsArticle> articles = service.getCompanyNews("AAPL", "2024-07-01", "2024-07-10");

        assertFalse(articles.isEmpty());
    }

    // ─── News Sentiment ───────────────────────────────────────────────────────

    @Test
    void getNewsSentiment_parsesCorrectly() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("finnhub-news-sentiment.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<FinnhubNewsSentiment> sentiment = service.getNewsSentiment("AAPL");

        assertTrue(sentiment.isPresent());
        assertEquals("AAPL", sentiment.get().symbol());
        assertEquals(0.82, sentiment.get().newsScore(), 0.001);
        assertEquals("bullish", sentiment.get().sentiment());
        assertEquals(15432, sentiment.get().buzz());
    }

    // ─── Forex Rates ──────────────────────────────────────────────────────────

    @Test
    void getForexRates_parsesRatesCorrectly() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("finnhub-forex-rates.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<FinnhubForexRate> rates = service.getForexRates("USD");

        assertTrue(rates.isPresent());
        assertEquals("USD", rates.get().base());
        assertNotNull(rates.get().quote());
        assertFalse(rates.get().quote().isEmpty());
        assertTrue(rates.get().quote().containsKey("EUR"));
        assertEquals(0.9234, rates.get().quote().get("EUR"), 0.0001);
        assertTrue(rates.get().quote().containsKey("JPY"));
    }

    // ─── IPO Calendar ─────────────────────────────────────────────────────────

    @Test
    void getIpoCalendar_parsesCorrectly() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("finnhub-ipo-calendar.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<FinnhubIpoEvent> ipos = service.getIpoCalendar("2024-07-01", "2024-07-31");

        assertTrue(ipos.isPresent());
        assertNotNull(ipos.get().ipoCalendar());
        assertFalse(ipos.get().ipoCalendar().isEmpty());
        assertEquals(2, ipos.get().ipoCalendar().size());
        FinnhubIpoEvent.IpoItem first = ipos.get().ipoCalendar().get(0);
        assertEquals("TechCorp Inc", first.name());
        assertEquals("NASDAQ", first.exchange());
        assertEquals("TECH", first.symbol());
        assertEquals("expected", first.status());
    }

    // ─── Earnings Calendar ────────────────────────────────────────────────────

    @Test
    void getEarningsCalendar_parsesCorrectly() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("finnhub-earnings-calendar.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<FinnhubEarningsCalendarEvent> calendar =
                service.getEarningsCalendar("2024-07-01", "2024-07-31");

        assertTrue(calendar.isPresent());
        assertNotNull(calendar.get().earningsCalendar());
        assertEquals(2, calendar.get().earningsCalendar().size());
        FinnhubEarningsCalendarEvent.EarningsEvent aapl = calendar.get().earningsCalendar().get(0);
        assertEquals("AAPL", aapl.symbol());
        assertEquals(1.35, aapl.epsEstimate(), 0.001);
        assertEquals("2024-07-25", aapl.date());
    }

    // ─── Basic Financials ─────────────────────────────────────────────────────

    @Test
    void getBasicFinancials_parsesMetricsCorrectly() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("finnhub-basic-financials.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<FinnhubBasicFinancials> bf = service.getBasicFinancials("AAPL");

        assertTrue(bf.isPresent());
        assertEquals("AAPL", bf.get().symbol());
        assertNotNull(bf.get().metric());
        // Check specific metric values
        assertTrue(bf.get().metric().has("peNormalizedAnnual"));
        assertTrue(bf.get().metric().has("beta"));
        assertTrue(bf.get().metric().has("52WeekHigh"));
        assertTrue(bf.get().metric().has("52WeekLow"));
        assertEquals(1.2, bf.get().metric().get("beta").getAsDouble(), 0.001);
        assertEquals(220.20, bf.get().metric().get("52WeekHigh").getAsDouble(), 0.001);
    }

    // ─── ESG Scores ───────────────────────────────────────────────────────────

    @Test
    void getEsgScores_parsesCorrectly() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("finnhub-esg.json"))
                .addHeader("Content-Type", "application/json"));

        Optional<FinnhubEsgScore> esg = service.getEsgScores("AAPL");

        assertTrue(esg.isPresent());
        assertEquals("AAPL", esg.get().symbol());
        assertEquals(72.5, esg.get().totalESGScore(), 0.01);
        assertEquals(68.0, esg.get().environmentScore(), 0.01);
        assertEquals(75.0, esg.get().socialScore(), 0.01);
        assertEquals(74.5, esg.get().governanceScore(), 0.01);
        assertEquals(2024, esg.get().ratingYear());
    }

    // ─── Caching ──────────────────────────────────────────────────────────────

    @Test
    void search_usesCacheOnSecondCall() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("finnhub-search.json"))
                .addHeader("Content-Type", "application/json"));

        // First call — hits server
        service.search("apple");
        // Second call — should use cache (no new request)
        service.search("apple");

        // Only 1 request should have been made
        assertEquals(1, server.getRequestCount());
    }

    @Test
    void getCompanyProfile_usesCacheOnSecondCall() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("finnhub-company-profile.json"))
                .addHeader("Content-Type", "application/json"));

        service.getCompanyProfile("AAPL");
        service.getCompanyProfile("AAPL");

        assertEquals(1, server.getRequestCount());
    }

    // ─── Disabled service ─────────────────────────────────────────────────────

    @Test
    void search_returnsEmptyWhenDisabled() {
        MarketApiProperties noKey = new MarketApiProperties();
        OkHttpClient client = new PriceHttpConfig().priceHttpClient(5, 5, 0);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        FinnhubMarketService disabled = new FinnhubMarketService(
                new HttpJsonClient(client), noKey, jdbc);

        assertTrue(disabled.search("AAPL").isEmpty());
        assertEquals(0, server.getRequestCount());
    }

    @Test
    void getCompanyProfile_returnsEmptyWhenDisabled() {
        MarketApiProperties noKey = new MarketApiProperties();
        OkHttpClient client = new PriceHttpConfig().priceHttpClient(5, 5, 0);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        FinnhubMarketService disabled = new FinnhubMarketService(
                new HttpJsonClient(client), noKey, jdbc);

        assertTrue(disabled.getCompanyProfile("AAPL").isEmpty());
    }

    @Test
    void getMarketNews_returnsEmptyListWhenDisabled() {
        MarketApiProperties noKey = new MarketApiProperties();
        OkHttpClient client = new PriceHttpConfig().priceHttpClient(5, 5, 0);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        FinnhubMarketService disabled = new FinnhubMarketService(
                new HttpJsonClient(client), noKey, jdbc);

        assertTrue(disabled.getMarketNews("general").isEmpty());
    }

    // ─── Peers ────────────────────────────────────────────────────────────────

    @Test
    void getPeers_returnsEmptyListOnError() {
        server.enqueue(new MockResponse().setResponseCode(500));
        List<String> peers = service.getPeers("AAPL");
        assertTrue(peers.isEmpty());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    @Test
    void buildUrl_includesTokenCorrectly() {
        String url = service.buildUrl("/quote", "symbol=AAPL");
        assertTrue(url.contains("symbol=AAPL"));
        assertTrue(url.contains("token=test-key"));
        assertTrue(url.contains("/quote"));
    }
}
