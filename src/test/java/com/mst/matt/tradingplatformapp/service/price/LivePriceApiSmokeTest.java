package com.mst.matt.tradingplatformapp.service.price;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Optional live smoke tests — run only when network access is available:
 * mvn test -Dtest=LivePriceApiSmokeTest -Dlive.api.tests=true
 *
 * These hit real public endpoints documented by each provider.
 */
@EnabledIfSystemProperty(named = "live.api.tests", matches = "true")
class LivePriceApiSmokeTest {

    private final OkHttpClientHolder clients = new OkHttpClientHolder();

    @Test
    void binance_liveBtcUsdtQuote() {
        BinanceService binance = clients.binance();
        Optional<PriceQuote> q = binance.getQuote("BTCUSDT");
        assertTrue(q.isPresent(), "Binance should return BTCUSDT — check api.binance.base-url or fallback-url");
        assertTrue(q.get().getPrice().doubleValue() > 0);
    }

    @Test
    void yahoo_liveAaplQuote() {
        YahooFinanceService yahoo = clients.yahoo();
        Optional<PriceQuote> q = yahoo.getQuote("AAPL");
        assertTrue(q.isPresent(), "Yahoo should return AAPL — check network/firewall");
        assertTrue(q.get().getPrice().doubleValue() > 0);
    }

    @Test
    void frankfurter_liveEurUsdQuote() {
        ForexService forex = clients.forex();
        Optional<PriceQuote> q = forex.getQuote("EURUSD");
        assertTrue(q.isPresent(), "Frankfurter should return EURUSD");
        assertTrue(q.get().getPrice().doubleValue() > 0);
    }

    @Test
    void coingecko_liveBtcQuote() {
        CoinGeckoService gecko = clients.coingecko();
        Optional<PriceQuote> q = gecko.getQuote("BTC");
        assertTrue(q.isPresent(), "CoinGecko should return BTC");
        assertTrue(q.get().getPrice().doubleValue() > 0);
    }

    /** Manual wiring without Spring context for live checks. */
    private static final class OkHttpClientHolder {
        private final com.mst.matt.tradingplatformapp.config.PriceHttpConfig cfg =
                new com.mst.matt.tradingplatformapp.config.PriceHttpConfig();
        private final okhttp3.OkHttpClient client = cfg.priceHttpClient(30, 45, 2);

        BinanceService binance() {
            BinanceService s = new BinanceService(client);
            org.springframework.test.util.ReflectionTestUtils.setField(s, "baseUrl", "https://api.binance.com");
            org.springframework.test.util.ReflectionTestUtils.setField(s, "fallbackUrl", "https://data-api.binance.vision");
            return s;
        }

        YahooFinanceService yahoo() {
            YahooFinanceService s = new YahooFinanceService(client);
            org.springframework.test.util.ReflectionTestUtils.setField(s, "baseUrl", "https://query1.finance.yahoo.com");
            return s;
        }

        ForexService forex() {
            ForexService s = new ForexService(client);
            org.springframework.test.util.ReflectionTestUtils.setField(s, "baseUrl", "https://api.frankfurter.app");
            return s;
        }

        CoinGeckoService coingecko() {
            CoinGeckoService s = new CoinGeckoService(client);
            org.springframework.test.util.ReflectionTestUtils.setField(s, "baseUrl", "https://api.coingecko.com/api/v3");
            org.springframework.test.util.ReflectionTestUtils.setField(s, "apiKey", "demo");
            return s;
        }
    }
}
