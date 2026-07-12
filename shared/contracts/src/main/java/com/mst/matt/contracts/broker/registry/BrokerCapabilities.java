package com.mst.matt.contracts.broker.registry;

import com.mst.matt.contracts.enums.BrokerType;
import com.mst.matt.contracts.enums.InstrumentType;
import com.mst.matt.contracts.enums.OrderType;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.Set;

/**
 * Immutable metadata object that describes the capabilities of a specific
 * broker integration.
 *
 * <h3>Purpose</h3>
 * <p>Before forwarding an order or market-data request to a broker, the
 * platform checks the broker's {@code BrokerCapabilities} to ensure the
 * requested operation is actually supported.  This prevents runtime errors
 * from reaching the external API and gives callers a consistent way to
 * discover broker limitations.</p>
 *
 * <h3>Registration</h3>
 * <p>Every broker implementation in {@code trading-service} and
 * {@code market-service} must expose a {@link BrokerCapabilities} instance
 * — typically as a Spring {@code @Bean} — so that the
 * {@link BrokerRegistry} can register it at startup.</p>
 *
 * <h3>Immutability</h3>
 * <p>Built with Lombok {@link Value} + {@link Builder} to ensure all
 * instances are deeply immutable (Lombok {@code @Value} makes all fields
 * {@code final} and generates no setters).</p>
 */
@Value
@Builder
public class BrokerCapabilities {

    // ── Identity ─────────────────────────────────────────────────────────────

    /**
     * The broker this capabilities object describes.
     */
    BrokerType brokerType;

    /**
     * Human-readable name (e.g., "BitUnix", "Binance Global").
     */
    String displayName;

    // ── Market data ───────────────────────────────────────────────────────────

    /**
     * Whether this broker/data-source supports live (WebSocket / SSE) price
     * streaming via
     * {@link com.mst.matt.contracts.broker.market.MarketDataProvider#streamLivePrice}.
     */
    boolean supportsLiveStream;

    /**
     * Set of OHLCV intervals (timeframes) this provider supports
     * (e.g., "1m", "5m", "15m", "1h", "4h", "1d", "1w").
     * Controllers must validate the requested interval against this set before
     * calling {@link com.mst.matt.contracts.broker.market.MarketDataProvider#getOhlcv}.
     */
    @Singular
    Set<String> supportedIntervals;

    // ── Trading ───────────────────────────────────────────────────────────────

    /**
     * Whether this broker supports spot trading
     * ({@link com.mst.matt.contracts.broker.trading.TradingProvider#placeSpotOrder}).
     */
    boolean supportsSpot;

    /**
     * Whether this broker supports futures (perpetual / dated) trading
     * ({@link com.mst.matt.contracts.broker.trading.TradingProvider#placeFuturesOrder}).
     */
    boolean supportsFutures;

    /**
     * Maximum leverage this broker allows for futures/margin positions.
     * {@code 1} for spot-only brokers (no leverage).
     * {@code 0} means leverage is not applicable or unknown.
     */
    int maxLeverage;

    /**
     * Set of instrument types this broker can trade.
     * Used for high-level routing (e.g., only route EQUITY orders to Alpaca).
     */
    @Singular
    Set<InstrumentType> supportedInstruments;

    /**
     * Set of order types this broker accepts.
     * Callers must check this before submitting an order with a specific
     * {@link OrderType}.
     */
    @Singular
    Set<OrderType> supportedOrderTypes;

    // ── Convenience predicates ────────────────────────────────────────────────

    /**
     * Returns {@code true} if this broker supports the given OHLCV interval.
     *
     * @param interval the requested timeframe string (e.g., "1h")
     */
    public boolean supportsInterval(String interval) {
        return supportedIntervals != null && supportedIntervals.contains(interval);
    }

    /**
     * Returns {@code true} if this broker supports the given order type.
     *
     * @param orderType the requested {@link OrderType}
     */
    public boolean supportsOrderType(OrderType orderType) {
        return supportedOrderTypes != null && supportedOrderTypes.contains(orderType);
    }

    /**
     * Returns {@code true} if this broker can trade the given instrument type.
     *
     * @param instrumentType the instrument classification to check
     */
    public boolean supportsInstrument(InstrumentType instrumentType) {
        return supportedInstruments != null && supportedInstruments.contains(instrumentType);
    }
}
