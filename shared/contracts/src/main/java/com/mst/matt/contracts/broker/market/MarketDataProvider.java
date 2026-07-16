package com.mst.matt.contracts.broker.market;

import com.mst.matt.contracts.dto.OhlcvBarDto;
import com.mst.matt.contracts.dto.PriceTickDto;
import com.mst.matt.contracts.dto.TickerSnapshotDto;
import com.mst.matt.contracts.enums.BrokerType;

import java.util.List;
import java.util.stream.Stream;

/**
 * Unified contract for fetching market data from any broker or data-source.
 *
 * <h3>Design rules</h3>
 * <ul>
 *   <li>No controller or service may call a broker SDK directly; they must only
 *       call methods on a {@code MarketDataProvider} implementation.</li>
 *   <li>Every concrete implementation must register a
 *       {@link com.mst.matt.contracts.broker.registry.BrokerCapabilities} bean
 *       declaring what this provider supports.</li>
 *   <li>Implementations live in {@code market-service}, not here.</li>
 * </ul>
 *
 * <h3>Error handling convention</h3>
 * <p>Implementations should wrap broker-specific exceptions in a
 * {@code MarketDataException} (to be defined in {@code market-service}).
 * Callers should never catch broker-SDK-specific exceptions.</p>
 */
public interface MarketDataProvider {

    /**
     * Returns the {@link BrokerType} this implementation serves.
     * Used by the {@link com.mst.matt.contracts.broker.registry.BrokerRegistry}
     * to resolve the correct provider at runtime.
     */
    BrokerType brokerType();

    // ── Historical OHLCV ──────────────────────────────────────────────────────

    /**
     * Fetch historical OHLCV bars for a symbol.
     *
     * @param symbol   trading symbol as understood by this broker (e.g., "BTCUSDT")
     * @param interval candlestick interval/timeframe
     *                 (e.g., "1m", "5m", "15m", "1h", "4h", "1d", "1w")
     * @param limit    maximum number of bars to return (broker may return fewer)
     * @return list of {@link OhlcvBarDto} bars ordered oldest-first;
     *         empty list if no data is available
     * @throws com.mst.matt.contracts.broker.market.UnsupportedIntervalException
     *         if the interval is not supported by this provider
     */
    List<OhlcvBarDto> getOhlcv(String symbol, String interval, int limit);

    // ── Live price streaming ──────────────────────────────────────────────────

    /**
     * Open a live price stream for a symbol.
     *
     * <p>Returns a {@link Stream} of {@link PriceTickDto} that produces real-time
     * price ticks as they arrive from the broker.  The stream is <em>blocking</em>
     * from the caller's perspective — implementations should run this in a
     * dedicated thread or use a reactive pipeline internally.</p>
     *
     * <p>The caller is responsible for closing the stream (it implements
     * {@link AutoCloseable} via {@link Stream}).</p>
     *
     * @param symbol trading symbol to stream (e.g., "BTCUSDT")
     * @return a potentially infinite stream of price ticks
     * @throws UnsupportedOperationException if this provider does not support
     *         live streaming (check {@link com.mst.matt.contracts.broker.registry.BrokerCapabilities#supportsLiveStream()})
     */
    Stream<PriceTickDto> streamLivePrice(String symbol);

    // ── Ticker snapshot ───────────────────────────────────────────────────────

    /**
     * Fetch the latest 24-hour ticker statistics for a symbol.
     *
     * @param symbol trading symbol (e.g., "BTCUSDT")
     * @return a {@link TickerSnapshotDto} with 24h statistics and the last price
     */
    TickerSnapshotDto getTickerSnapshot(String symbol);
}
