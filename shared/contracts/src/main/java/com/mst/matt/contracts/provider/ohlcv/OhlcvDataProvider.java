package com.mst.matt.contracts.provider.ohlcv;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/**
 * Unified, broker-agnostic contract for fetching OHLCV (candlestick) data
 * across all asset classes.
 *
 * <h3>Why this exists alongside {@link com.mst.matt.contracts.broker.market.MarketDataProvider}</h3>
 * <p>{@code MarketDataProvider} is tied to a specific trading <em>broker</em>
 * (BitUnix, Binance, Alpaca …) and returns broker-specific data via their
 * trading API. {@code OhlcvDataProvider} is tied to a dedicated
 * <em>market-data source</em> (Alpha Vantage for stocks, OANDA for forex,
 * CoinGecko for crypto …) and always returns
 * {@link NormalizedOhlcvBar} so that every consumer — AI tab, Analysis tab,
 * Trade Journal — sees the same shape regardless of the underlying source.</p>
 *
 * <h3>Provider registration</h3>
 * <p>Implementations are registered in the
 * {@link com.mst.matt.contracts.provider.registry.ProviderRegistry} keyed by
 * {@code (AssetClass, providerName)}.  The registry's fallback chain means
 * that if Provider A fails, Provider B is tried automatically.</p>
 *
 * <h3>Implementation locations (Phase 4.5 homes)</h3>
 * <ul>
 *   <li>Stock OHLCV  → {@code market-service} or {@code reference-data-service}</li>
 *   <li>Crypto OHLCV → {@code market-service} (can reuse broker data) or dedicated</li>
 *   <li>Forex OHLCV  → {@code reference-data-service}</li>
 * </ul>
 * <p>Actual provider classes are NOT created in this step — only no-op mocks
 * (see {@code NoOpOhlcvDataProvider}).</p>
 *
 * <h3>Error handling</h3>
 * <p>Implementations must throw {@code ProviderUnavailableException} (defined
 * in the consuming service) on transient failures so that the
 * {@link com.mst.matt.contracts.provider.registry.ProviderRegistry} circuit
 * breaker can intercept and trigger the fallback chain.</p>
 */
public interface OhlcvDataProvider {

    /**
     * Returns the logical provider name used to register this implementation
     * in the {@link com.mst.matt.contracts.provider.registry.ProviderRegistry}
     * (e.g., "ALPHA_VANTAGE", "BINANCE", "OANDA", "COINGECKO").
     */
    String providerName();

    /**
     * Returns the set of {@link AssetClass} values this provider can serve.
     * The registry uses this to route requests to the correct provider.
     *
     * @return non-empty list of supported asset classes
     */
    List<AssetClass> supportedAssetClasses();

    // ── Historical OHLCV ──────────────────────────────────────────────────────

    /**
     * Fetch historical OHLCV bars for a symbol.
     *
     * @param symbol     canonical platform symbol (e.g., "BTCUSDT", "AAPL", "EURUSD")
     * @param assetClass asset class context — used to validate support and
     *                   route to the correct internal endpoint
     * @param interval   standardised interval (e.g., "1m", "5m", "15m", "1h",
     *                   "4h", "1d", "1w"); providers must map this to their own
     *                   API parameter
     * @param limit      maximum number of bars to return (provider may return fewer)
     * @return list of {@link NormalizedOhlcvBar} ordered oldest-first;
     *         empty list if no data is available
     */
    List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                AssetClass assetClass,
                                                String interval,
                                                int limit);

    /**
     * Fetch historical OHLCV bars for a symbol within a time range.
     *
     * @param symbol     canonical platform symbol
     * @param assetClass asset class context
     * @param interval   standardised interval
     * @param from       range start (inclusive)
     * @param to         range end (inclusive)
     * @return list of {@link NormalizedOhlcvBar} ordered oldest-first
     */
    List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                AssetClass assetClass,
                                                String interval,
                                                Instant from,
                                                Instant to);

    // ── Live / streaming ──────────────────────────────────────────────────────

    /**
     * Open a live OHLCV stream for a symbol.
     *
     * <p>Returns a potentially infinite {@link Stream} of
     * {@link NormalizedOhlcvBar} objects as new bars or bar-updates arrive.
     * Callers are responsible for closing the stream.</p>
     *
     * <p>Implementations that do not support streaming must throw
     * {@link UnsupportedOperationException}.  Callers should check
     * {@link #supportsStreaming(AssetClass)} before calling this method.</p>
     *
     * @param symbol     canonical platform symbol
     * @param assetClass asset class context
     * @param interval   standardised interval for the live bars
     * @return stream of live bar updates
     * @throws UnsupportedOperationException if streaming is not supported
     */
    Stream<NormalizedOhlcvBar> streamLiveBars(String symbol,
                                               AssetClass assetClass,
                                               String interval);

    /**
     * Returns {@code true} if this provider supports live streaming for the
     * given asset class.
     *
     * @param assetClass asset class to check
     * @return {@code true} if {@link #streamLiveBars} is supported
     */
    boolean supportsStreaming(AssetClass assetClass);
}
