package com.mst.matt.marketservice.config;

import com.mst.matt.contracts.provider.mock.NoOpOhlcvDataProvider;
import com.mst.matt.contracts.provider.ohlcv.OhlcvDataProvider;
import com.mst.matt.contracts.provider.registry.ProviderRegistry;
import com.mst.matt.marketservice.provider.crypto.AlphaVantageOhlcvProvider;
import com.mst.matt.marketservice.provider.crypto.BinanceOhlcvProvider;
import com.mst.matt.marketservice.provider.crypto.CoinGeckoOhlcvProvider;
import com.mst.matt.marketservice.provider.crypto.CoinMarketCapOhlcvProvider;
import com.mst.matt.marketservice.provider.forex.CurrencyLayerForexOhlcvProvider;
import com.mst.matt.marketservice.provider.forex.ExchangeRateApiForexOhlcvProvider;
import com.mst.matt.marketservice.provider.forex.FixerForexOhlcvProvider;
import com.mst.matt.marketservice.provider.forex.FrankfurterForexOhlcvProvider;
import com.mst.matt.marketservice.provider.forex.FreeCurrencyApiForexOhlcvProvider;
import com.mst.matt.marketservice.provider.forex.OpenExchangeRatesForexOhlcvProvider;
import com.mst.matt.marketservice.provider.stock.FinnhubOhlcvProvider;
import com.mst.matt.marketservice.provider.stock.MarketstackOhlcvProvider;
import com.mst.matt.marketservice.provider.stock.PolygonOhlcvProvider;
import com.mst.matt.marketservice.provider.stock.TwelveDataOhlcvProvider;
import com.mst.matt.marketservice.provider.stock.YahooFinanceOhlcvProvider;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Spring configuration for the OHLCV provider layer.
 *
 * <p>Responsibilities:
 * <ol>
 *   <li>Declare the shared {@code marketProviderHttpClient} OkHttpClient bean
 *       (used by {@link com.mst.matt.marketservice.client.HttpJsonClient} and
 *       provider implementations that use OkHttpClient directly).</li>
 *   <li>Declare the {@link ProviderRegistry}{@code <OhlcvDataProvider>} bean,
 *       wiring up the ordered fallback chains per asset class.</li>
 * </ol>
 *
 * <h3>Provider priority chains</h3>
 * <ul>
 *   <li>CRYPTO: Binance → CoinGecko → CoinMarketCap → AlphaVantage → TwelveData → Finnhub → NoOp</li>
 *   <li>STOCK:  AlphaVantage → TwelveData → Finnhub → Polygon → Marketstack → Yahoo → NoOp</li>
 *   <li>FOREX:  Frankfurter → AlphaVantage → TwelveData → Finnhub → Fixer → ExchangeRateApi
 *               → FreeCurrencyApi → CurrencyLayer → OpenExchangeRates → NoOp</li>
 * </ul>
 *
 * <p>The NoOp provider is always registered last as a guaranteed fallback.
 */
@Configuration
public class MarketProviderConfig {

    // ── HTTP client ───────────────────────────────────────────────────────────

    /**
     * Shared OkHttpClient for market-data provider HTTP calls.
     *
     * <p>Qualified as {@code "marketProviderHttpClient"} so that
     * {@link com.mst.matt.marketservice.client.HttpJsonClient},
     * {@link BinanceOhlcvProvider}, and {@link YahooFinanceOhlcvProvider}
     * can inject it via {@code @Qualifier}.
     */
    @Bean(name = "marketProviderHttpClient")
    public OkHttpClient marketProviderHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .callTimeout(45, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    // ── ProviderRegistry<OhlcvDataProvider> ───────────────────────────────────

    /**
     * Builds the typed registry with per-AssetClass priority chains.
     *
     * <p>Registration order within each asset class determines fallback priority.
     * The NoOp provider is registered last (supports all asset classes) and acts
     * as the final guaranteed fallback so the registry never throws
     * {@code ProviderUnavailableException} from an empty chain.
     *
     * @param binance            Binance REST klines (CRYPTO)
     * @param coinGecko          CoinGecko /coins/*/ohlc (CRYPTO)
     * @param coinMarketCap      CoinMarketCap v2 OHLCV (CRYPTO)
     * @param alphaVantage       Alpha Vantage time-series (CRYPTO, STOCK, FOREX)
     * @param twelveData         TwelveData /time_series (STOCK, CRYPTO, FOREX)
     * @param finnhub            Finnhub /*/candle (STOCK, CRYPTO, FOREX)
     * @param polygon            Polygon /v2/aggs (STOCK)
     * @param marketstack        Marketstack /v1/eod (STOCK)
     * @param yahoo              Yahoo Finance v8 chart (STOCK)
     * @param frankfurter        Frankfurter date-range rates (FOREX)
     * @param fixer              Fixer.io EUR-base (FOREX)
     * @param exchangeRateApi    ExchangeRate-API (FOREX)
     * @param freeCurrencyApi    FreeCurrencyAPI (FOREX)
     * @param currencyLayer      CurrencyLayer USD-base (FOREX)
     * @param openExchangeRates  OpenExchangeRates USD-base (FOREX)
     * @return configured registry
     */
    @Bean
    public ProviderRegistry<OhlcvDataProvider> ohlcvProviderRegistry(
            BinanceOhlcvProvider          binance,
            CoinGeckoOhlcvProvider        coinGecko,
            CoinMarketCapOhlcvProvider    coinMarketCap,
            AlphaVantageOhlcvProvider     alphaVantage,
            TwelveDataOhlcvProvider       twelveData,
            FinnhubOhlcvProvider          finnhub,
            PolygonOhlcvProvider          polygon,
            MarketstackOhlcvProvider      marketstack,
            YahooFinanceOhlcvProvider     yahoo,
            FrankfurterForexOhlcvProvider frankfurter,
            FixerForexOhlcvProvider       fixer,
            ExchangeRateApiForexOhlcvProvider  exchangeRateApi,
            FreeCurrencyApiForexOhlcvProvider  freeCurrencyApi,
            CurrencyLayerForexOhlcvProvider    currencyLayer,
            OpenExchangeRatesForexOhlcvProvider openExchangeRates) {

        NoOpOhlcvDataProvider noOp = new NoOpOhlcvDataProvider();

        return ProviderRegistry.<OhlcvDataProvider>builder()
                // ── CRYPTO chain (highest priority first) ──────────────────
                .register(binance)
                .register(coinGecko)
                .register(coinMarketCap)
                // ── STOCK chain ────────────────────────────────────────────
                .register(alphaVantage)     // also supports CRYPTO + FOREX
                .register(twelveData)       // also supports CRYPTO + FOREX
                .register(finnhub)          // also supports CRYPTO + FOREX
                .register(polygon)
                .register(marketstack)
                .register(yahoo)
                // ── FOREX chain ────────────────────────────────────────────
                .register(frankfurter)
                .register(fixer)
                .register(exchangeRateApi)
                .register(freeCurrencyApi)
                .register(currencyLayer)
                .register(openExchangeRates)
                // ── Last-resort fallback (all asset classes) ───────────────
                .register(noOp)
                .build();
    }
}
