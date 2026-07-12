package com.mst.matt.contracts.broker.registry;

import com.mst.matt.contracts.broker.market.MarketDataProvider;
import com.mst.matt.contracts.broker.trading.TradingProvider;
import com.mst.matt.contracts.enums.BrokerType;

import java.util.Collection;
import java.util.Optional;

/**
 * Central service-registry contract for resolving broker implementations
 * at runtime by {@link BrokerType}.
 *
 * <h3>Responsibility</h3>
 * <p>Instead of injecting concrete broker implementations everywhere, callers
 * ask the registry: <em>"Give me the MarketDataProvider for BrokerType.BITUNIX"</em>.
 * This decouples all business logic from concrete broker classes.</p>
 *
 * <h3>Implementation location</h3>
 * <p>The concrete implementation ({@code DefaultBrokerRegistry}) lives in
 * {@code market-service} or {@code trading-service} — whichever service hosts
 * the actual broker implementations. This interface lives in
 * {@code shared/contracts} so that any service can depend on it without
 * knowing the concrete class.</p>
 *
 * <h3>Registration</h3>
 * <p>Each broker implementation registers itself by implementing
 * {@link MarketDataProvider} and/or {@link TradingProvider} and annotating
 * the implementation class with {@code @Component} (or declaring it as a
 * {@code @Bean}).  The concrete registry collects all such beans via
 * Spring's dependency injection ({@code List<MarketDataProvider>} injection)
 * and builds an internal map keyed by {@link BrokerType}.</p>
 *
 * <h3>Usage example</h3>
 * <pre>{@code
 * // In a service or controller:
 * MarketDataProvider provider = brokerRegistry
 *         .getMarketDataProvider(BrokerType.BITUNIX)
 *         .orElseThrow(() -> new IllegalArgumentException("No provider for BITUNIX"));
 * List<OhlcvBarDto> bars = provider.getOhlcv("BTCUSDT", "1h", 200);
 * }</pre>
 */
public interface BrokerRegistry {

    // ── MarketDataProvider resolution ─────────────────────────────────────────

    /**
     * Resolve the {@link MarketDataProvider} for the given broker.
     *
     * @param brokerType the target broker
     * @return an {@link Optional} containing the provider, or empty if no
     *         implementation is registered for this broker
     */
    Optional<MarketDataProvider> getMarketDataProvider(BrokerType brokerType);

    /**
     * Resolve and require a {@link MarketDataProvider} for the given broker.
     *
     * @param brokerType the target broker
     * @return the registered provider
     * @throws java.util.NoSuchElementException if no provider is registered
     */
    MarketDataProvider requireMarketDataProvider(BrokerType brokerType);

    // ── TradingProvider resolution ────────────────────────────────────────────

    /**
     * Resolve the {@link TradingProvider} for the given broker.
     *
     * @param brokerType the target broker
     * @return an {@link Optional} containing the provider, or empty if no
     *         implementation is registered for this broker
     */
    Optional<TradingProvider> getTradingProvider(BrokerType brokerType);

    /**
     * Resolve and require a {@link TradingProvider} for the given broker.
     *
     * @param brokerType the target broker
     * @return the registered provider
     * @throws java.util.NoSuchElementException if no provider is registered
     */
    TradingProvider requireTradingProvider(BrokerType brokerType);

    // ── Capabilities ──────────────────────────────────────────────────────────

    /**
     * Retrieve the capability metadata for a specific broker.
     *
     * @param brokerType the target broker
     * @return an {@link Optional} containing the capabilities descriptor,
     *         or empty if the broker is not registered
     */
    Optional<BrokerCapabilities> getCapabilities(BrokerType brokerType);

    /**
     * Returns all registered {@link BrokerCapabilities} descriptors.
     * Useful for surfacing broker capabilities to the frontend.
     *
     * @return an immutable collection of all known capabilities
     */
    Collection<BrokerCapabilities> getAllCapabilities();

    /**
     * Returns all {@link BrokerType} values that have at least one registered
     * provider (market-data or trading).
     *
     * @return collection of registered broker types
     */
    Collection<BrokerType> getRegisteredBrokers();
}
