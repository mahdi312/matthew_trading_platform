package com.mst.matt.tradingplatformapp.service.price;

import com.mst.matt.tradingplatformapp.config.MarketApiProperties;
import com.mst.matt.tradingplatformapp.config.PriceHttpConfig;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TwelveDataPriceService}.
 * Uses MockWebServer to simulate TwelveData API responses.
 *
 * <p>Endpoints tested:
 * <ul>
 *   <li>{@code GET /quote}        — full quote snapshot</li>
 *   <li>{@code GET /time_series}  — historical OHLCV bars</li>
 *   <li>{@code GET /price}        — latest price (fallback)</li>
 *   <li>{@code GET /eod}          — end-of-day price</li>
 *   <li>{@code GET /exchange_rate} — forex rate</li>
 *   <li>{@code GET /currency_conversion} — amount conversion</li>
 *   <li>{@code GET /market_movers} — gainers / losers</li>
 *   <li>{@code GET /exchange_status} — open/closed exchanges</li>
 *   <li>{@code GET /logo}         — company logo URL</li>
 *   <li>{@code GET /rsi}          — RSI indicator</li>
 *   <li>{@code GET /macd}         — MACD indicator</li>
 * </ul>
 */
class TwelveDataPriceServiceTest {

    private MockWebServer server;
    private TwelveDataPriceService service;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        OkHttpClient client = new PriceHttpConfig().priceHttpClient(5, 5, 0);
        MarketApiProperties keys = new MarketApiProperties();
        keys.setTwelvedataKey("test-key");
        TwelveDataRateLimiter rateLimiter = new TwelveDataRateLimiter();
        service = new TwelveDataPriceService(client, keys, rateLimiter);
        PriceServiceTestSupport.setBaseUrl(service, "baseUrl",
                server.url("").toString().replaceAll("/$", ""));
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    // ── /quote ────────────────────────────────────────────────────────────────

    @Test
    void getQuote_parsesQuoteResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("twelvedata-quote.json"))
                .addHeader("Content-Type", "application/json"));

        var quote = service.getQuote("AAPL");

        assertTrue(quote.isPresent(), "Quote should be present");
        assertEquals(0, quote.get().getPrice().compareTo(new BigDecimal("174.25")),
                "Price should be 174.25");
        assertTrue(quote.get().isUp(), "Quote should be up (positive change)");
        assertEquals("Apple Inc", quote.get().getAssetName());
    }

    @Test
    void getQuote_returnsEmptyOnEmptyBody() {
        server.enqueue(new MockResponse()
                .setBody("{}")
                .addHeader("Content-Type", "application/json"));

        var quote = service.getQuote("AAPL");
        assertFalse(quote.isPresent(), "Quote should be empty when symbol is null in response");
    }

    @Test
    void fetchQuote_typedResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("twelvedata-quote.json"))
                .addHeader("Content-Type", "application/json"));

        var q = service.fetchQuote("AAPL");

        assertTrue(q.isPresent());
        assertEquals("AAPL", q.get().symbol());
        assertEquals("NASDAQ", q.get().exchange());
        assertEquals("174.25", q.get().close());
    }

    // ── /time_series ─────────────────────────────────────────────────────────

    @Test
    void getOhlcv_parsesTimeSeries() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("twelvedata-time-series.json"))
                .addHeader("Content-Type", "application/json"));

        var bars = service.getOhlcv("AAPL", "1d", 5);

        assertFalse(bars.isEmpty(), "Should return OHLCV bars");
        assertEquals(3, bars.size(), "Should parse all 3 bars");
        // Bars should be in chronological order (oldest first)
        assertTrue(bars.get(0).getOpenTime().isBefore(bars.get(bars.size() - 1).getOpenTime()),
                "Bars should be chronological (oldest first)");
        assertEquals(0, bars.get(2).getClose().compareTo(new BigDecimal("214.88")));
    }

    @Test
    void getOhlcv_returnsEmptyOnErrorStatus() {
        server.enqueue(new MockResponse()
                .setBody("{\"status\":\"error\",\"message\":\"Invalid symbol\"}")
                .addHeader("Content-Type", "application/json"));

        var bars = service.getOhlcv("INVALID", "1d", 10);
        assertTrue(bars.isEmpty(), "Should return empty list on error response");
    }

    // ── /price ────────────────────────────────────────────────────────────────

    @Test
    void fetchLatestPrice_parsesPrice() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("twelvedata-price.json"))
                .addHeader("Content-Type", "application/json"));

        var price = service.fetchLatestPrice("AAPL");

        assertTrue(price.isPresent(), "Price should be present");
        assertEquals(0, price.get().compareTo(new BigDecimal("214.88")));
    }

    // ── /eod ─────────────────────────────────────────────────────────────────

    @Test
    void fetchEod_parsesEodResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("twelvedata-eod.json"))
                .addHeader("Content-Type", "application/json"));

        var eod = service.fetchEod("AAPL");

        assertTrue(eod.isPresent(), "EOD response should be present");
        assertEquals("AAPL", eod.get().symbol());
        assertEquals("2026-06-30", eod.get().datetime());
        assertEquals("214.88", eod.get().close());
    }

    // ── /exchange_rate ────────────────────────────────────────────────────────

    @Test
    void fetchExchangeRate_parsesRate() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("twelvedata-exchange-rate.json"))
                .addHeader("Content-Type", "application/json"));

        var rate = service.fetchExchangeRate("USD/JPY");

        assertTrue(rate.isPresent(), "Exchange rate should be present");
        assertEquals("USD/JPY", rate.get().symbol());
        assertEquals(144.32, rate.get().rate(), 0.001);
    }

    // ── /currency_conversion ──────────────────────────────────────────────────

    @Test
    void fetchCurrencyConversion_parsesConversion() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("twelvedata-currency-conversion.json"))
                .addHeader("Content-Type", "application/json"));

        var conv = service.fetchCurrencyConversion("EUR/USD", 100.0);

        assertTrue(conv.isPresent(), "Currency conversion should be present");
        assertEquals("EUR/USD", conv.get().symbol());
        assertEquals(108.62, conv.get().amount(), 0.001);
    }

    // ── /market_movers ────────────────────────────────────────────────────────

    @Test
    void fetchMarketMoversTyped_parsesMovers() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("twelvedata-market-movers.json"))
                .addHeader("Content-Type", "application/json"));

        var movers = service.fetchMarketMoversTyped("NASDAQ", null);

        assertTrue(movers.isPresent(), "Market movers should be present");
        assertNotNull(movers.get().gainers());
        assertFalse(movers.get().gainers().isEmpty(), "Should have gainers");
        assertEquals("NVDA", movers.get().gainers().get(0).symbol());
        assertFalse(movers.get().losers().isEmpty(), "Should have losers");
        assertEquals("INTC", movers.get().losers().get(0).symbol());
    }

    // ── /exchange_status ──────────────────────────────────────────────────────

    @Test
    void fetchExchangeStatusTyped_parsesStatus() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("twelvedata-exchange-status.json"))
                .addHeader("Content-Type", "application/json"));

        var status = service.fetchExchangeStatusTyped();

        assertTrue(status.isPresent(), "Exchange status should be present");
        assertNotNull(status.get().data());
        assertEquals(3, status.get().data().size());
        assertTrue(status.get().data().get(0).isMarketOpen(), "NYSE should be open");
        assertFalse(status.get().data().get(2).isMarketOpen(), "LSE should be closed");
    }

    // ── /logo ─────────────────────────────────────────────────────────────────

    @Test
    void fetchLogoUrl_returnsUrl() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("twelvedata-logo.json"))
                .addHeader("Content-Type", "application/json"));

        var logoUrl = service.fetchLogoUrl("AAPL");

        assertTrue(logoUrl.isPresent(), "Logo URL should be present");
        assertTrue(logoUrl.get().contains("aapl"), "Logo URL should contain symbol");
    }

    // ── /rsi ──────────────────────────────────────────────────────────────────

    @Test
    void fetchRsi_parsesIndicator() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("twelvedata-rsi.json"))
                .addHeader("Content-Type", "application/json"));

        var rsi = service.fetchRsi("AAPL", "1day", 14, 5);

        assertTrue(rsi.isPresent(), "RSI response should be present");
        assertTrue(rsi.get().isOk(), "RSI status should be ok");
        assertNotNull(rsi.get().values());
        assertFalse(rsi.get().values().isEmpty());
        assertEquals("58.32104", rsi.get().values().get(0).rsi());
    }

    // ── /macd ─────────────────────────────────────────────────────────────────

    @Test
    void fetchMacd_parsesIndicator() throws Exception {
        server.enqueue(new MockResponse()
                .setBody(PriceServiceTestSupport.fixture("twelvedata-macd.json"))
                .addHeader("Content-Type", "application/json"));

        var macd = service.fetchMacd("AAPL", "1day", 12, 26, 9, 5);

        assertTrue(macd.isPresent(), "MACD response should be present");
        assertTrue(macd.get().isOk(), "MACD status should be ok");
        assertNotNull(macd.get().values());
        assertFalse(macd.get().values().isEmpty());
        assertEquals("2.45123", macd.get().values().get(0).macd());
        assertEquals("1.98456", macd.get().values().get(0).macdSignal());
        assertEquals("0.46667", macd.get().values().get(0).macdHist());
    }

    // ── Symbol formatting ─────────────────────────────────────────────────────

    @Test
    void formatSymbol_formatsForexCorrectly() {
        assertEquals("EUR/USD", TwelveDataPriceService.formatSymbol("EURUSD"));
        assertEquals("USD/JPY", TwelveDataPriceService.formatSymbol("USDJPY"));
    }

    @Test
    void formatSymbol_formatsCryptoCorrectly() {
        assertEquals("BTC/USDT", TwelveDataPriceService.formatSymbol("BTCUSDT"));
        assertEquals("ETH/USDT", TwelveDataPriceService.formatSymbol("ETHUSDT"));
    }

    @Test
    void formatSymbol_leavesStocksUnchanged() {
        assertEquals("AAPL", TwelveDataPriceService.formatSymbol("AAPL"));
        assertEquals("MSFT", TwelveDataPriceService.formatSymbol("MSFT"));
    }

    // ── Interval mapping ──────────────────────────────────────────────────────

    @Test
    void mapInterval_mapsAllTimeframes() {
        assertEquals("1min",   TwelveDataPriceService.mapInterval("1m"));
        assertEquals("5min",   TwelveDataPriceService.mapInterval("5m"));
        assertEquals("15min",  TwelveDataPriceService.mapInterval("15m"));
        assertEquals("30min",  TwelveDataPriceService.mapInterval("30m"));
        assertEquals("1h",     TwelveDataPriceService.mapInterval("1h"));
        assertEquals("4h",     TwelveDataPriceService.mapInterval("4h"));
        assertEquals("1day",   TwelveDataPriceService.mapInterval("1d"));
        assertEquals("1week",  TwelveDataPriceService.mapInterval("1w"));
        assertEquals("1month", TwelveDataPriceService.mapInterval("1mo"));
        assertEquals("1day",   TwelveDataPriceService.mapInterval(null));
    }

    // ── Provider metadata ──────────────────────────────────────────────────────

    @Test
    void getProviderName_returnsTwelveData() {
        assertEquals("Twelve Data", service.getProviderName());
    }

    @Test
    void getProviderId_returnsTwelveDataEnum() {
        assertEquals(MarketDataProvider.TWELVE_DATA, service.getProviderId());
    }

    @Test
    void isEnabled_trueWhenKeySet() {
        assertTrue(service.isEnabled(), "Service should be enabled when key is set");
    }

    @Test
    void supports_trueForValidSymbol() {
        assertTrue(service.supports("AAPL"), "Should support valid symbol");
    }
}
