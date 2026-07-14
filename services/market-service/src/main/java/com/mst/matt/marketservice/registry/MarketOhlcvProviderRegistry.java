package com.mst.matt.marketservice.registry;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import com.mst.matt.contracts.provider.ohlcv.OhlcvDataProvider;
import com.mst.matt.contracts.provider.registry.ProviderRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Market-service facade over {@link ProviderRegistry}{@code <OhlcvDataProvider>}.
 *
 * <p>Exposes typed methods for OHLCV retrieval with per-AssetClass fallback chains
 * and per-provider Resilience4j circuit breakers (handled inside {@link ProviderRegistry}).
 *
 * <p>Priority chains (matching the monolith's {@code PriceProviderRegistry.defaultOrder()}):
 * <ul>
 *   <li>CRYPTO: Binance → CoinGecko → CoinMarketCap → AlphaVantage → TwelveData → Finnhub → NoOp</li>
 *   <li>STOCK:  AlphaVantage → TwelveData → Finnhub → Polygon → Marketstack → Yahoo → NoOp</li>
 *   <li>FOREX:  Frankfurter → AlphaVantage → TwelveData → Finnhub → Fixer → ExchangeRateApi
 *               → FreeCurrencyApi → CurrencyLayer → OpenExchangeRates → NoOp</li>
 * </ul>
 *
 * <p>The registry bean is constructed in
 * {@link com.mst.matt.marketservice.config.MarketProviderConfig}.
 */
@Slf4j
@Component
public class MarketOhlcvProviderRegistry {

    private final ProviderRegistry<OhlcvDataProvider> registry;

    public MarketOhlcvProviderRegistry(ProviderRegistry<OhlcvDataProvider> registry) {
        this.registry = registry;
    }

    // ── Historical bars — limit-based ─────────────────────────────────────────

    /**
     * Fetch historical bars, trying providers in priority order with circuit-breaker fallback.
     *
     * @param symbol     canonical platform symbol
     * @param assetClass asset class
     * @param interval   timeframe label (e.g. "1h", "1d")
     * @param limit      max bars to return
     * @return bars from first successful provider; empty list if all providers fail
     */
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                       AssetClass assetClass,
                                                       String interval,
                                                       int limit) {
        try {
            return registry.executeWithFallback(assetClass,
                    p -> p.getHistoricalBars(symbol, assetClass, interval, limit));
        } catch (Exception ex) {
            log.warn("[OhlcvRegistry] all providers failed for {}/{}/{}: {}",
                    symbol, assetClass, interval, ex.getMessage());
            return List.of();
        }
    }

    // ── Historical bars — time-range based ───────────────────────────────────

    /**
     * Fetch historical bars within a specific time range.
     *
     * @param symbol     canonical platform symbol
     * @param assetClass asset class
     * @param interval   timeframe label
     * @param from       range start (inclusive)
     * @param to         range end (inclusive)
     * @return bars from first successful provider; empty list if all providers fail
     */
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                       AssetClass assetClass,
                                                       String interval,
                                                       Instant from,
                                                       Instant to) {
        try {
            return registry.executeWithFallback(assetClass,
                    p -> p.getHistoricalBars(symbol, assetClass, interval, from, to));
        } catch (Exception ex) {
            log.warn("[OhlcvRegistry] all providers failed for {}/{}/{} range: {}",
                    symbol, assetClass, interval, ex.getMessage());
            return List.of();
        }
    }

    // ── Provider inspection ───────────────────────────────────────────────────

    /**
     * Returns the registry — useful for testing/admin inspection.
     */
    public ProviderRegistry<OhlcvDataProvider> getRegistry() {
        return registry;
    }
}
