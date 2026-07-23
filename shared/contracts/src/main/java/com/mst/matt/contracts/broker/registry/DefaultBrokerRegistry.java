package com.mst.matt.contracts.broker.registry;

import com.mst.matt.contracts.broker.market.MarketDataProvider;
import com.mst.matt.contracts.broker.trading.TradingProvider;
import com.mst.matt.contracts.enums.BrokerType;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Default implementation of {@link BrokerRegistry}.
 *
 * <h3>Instantiation</h3>
 * <p>This class is <em>not</em> annotated with {@code @Component} here so that
 * it does not automatically become a bean in modules that import
 * {@code shared/contracts} but do not need it (e.g., {@code identity-service}).
 * Services that need broker routing — {@code market-service} and
 * {@code trading-service} — must declare it as a {@code @Bean} in their own
 * Spring configuration, injecting their lists of providers:</p>
 *
 * <pre>{@code
 * // In market-service or trading-service @Configuration:
 * @Bean
 * public BrokerRegistry brokerRegistry(
 *         List<MarketDataProvider> marketProviders,
 *         List<TradingProvider>    tradingProviders,
 *         List<BrokerCapabilities> capabilities) {
 *     return new DefaultBrokerRegistry(marketProviders, tradingProviders, capabilities);
 * }
 * }</pre>
 *
 * <h3>How broker implementations register themselves</h3>
 * <ol>
 *   <li>Implement {@link MarketDataProvider} and/or {@link TradingProvider}.</li>
 *   <li>Annotate the implementation class with {@code @Component} (or declare
 *       it as a {@code @Bean}).</li>
 *   <li>Spring collects all such beans into the {@code List<>} parameters above.</li>
 *   <li>{@code DefaultBrokerRegistry} indexes them by {@link BrokerType}.</li>
 * </ol>
 *
 * <p><b>NO broker SDK calls are made here.</b> This class only performs
 * map-lookups.</p>
 */
public class DefaultBrokerRegistry implements BrokerRegistry {

    private final Map<BrokerType, MarketDataProvider> marketProviders;
    private final Map<BrokerType, TradingProvider>    tradingProviders;
    private final Map<BrokerType, BrokerCapabilities> capabilities;

    /**
     * Construct and index all registered broker implementations.
     *
     * @param marketProviders  all {@link MarketDataProvider} beans in the application context
     * @param tradingProviders all {@link TradingProvider} beans in the application context
     * @param capabilityList   all {@link BrokerCapabilities} beans in the application context
     */
    public DefaultBrokerRegistry(
            List<MarketDataProvider>  marketProviders,
            List<TradingProvider>     tradingProviders,
            List<BrokerCapabilities>  capabilityList) {

        Map<BrokerType, MarketDataProvider> mMap = new EnumMap<>(BrokerType.class);
        for (MarketDataProvider p : marketProviders) {
            mMap.put(p.brokerType(), p);
        }
        this.marketProviders = Collections.unmodifiableMap(mMap);

        Map<BrokerType, TradingProvider> tMap = new EnumMap<>(BrokerType.class);
        for (TradingProvider p : tradingProviders) {
            tMap.put(p.brokerType(), p);
        }
        this.tradingProviders = Collections.unmodifiableMap(tMap);

        Map<BrokerType, BrokerCapabilities> cMap = new EnumMap<>(BrokerType.class);
        for (BrokerCapabilities c : capabilityList) {
            cMap.put(c.getBrokerType(), c);
        }
        this.capabilities = Collections.unmodifiableMap(cMap);
    }

    // ── MarketDataProvider resolution ─────────────────────────────────────────

    @Override
    public Optional<MarketDataProvider> getMarketDataProvider(BrokerType brokerType) {
        return Optional.ofNullable(marketProviders.get(brokerType));
    }

    @Override
    public MarketDataProvider requireMarketDataProvider(BrokerType brokerType) {
        return getMarketDataProvider(brokerType)
                .orElseThrow(() -> new NoSuchElementException(
                        "No MarketDataProvider registered for broker: " + brokerType));
    }

    // ── TradingProvider resolution ────────────────────────────────────────────

    @Override
    public Optional<TradingProvider> getTradingProvider(BrokerType brokerType) {
        return Optional.ofNullable(tradingProviders.get(brokerType));
    }

    @Override
    public TradingProvider requireTradingProvider(BrokerType brokerType) {
        return getTradingProvider(brokerType)
                .orElseThrow(() -> new NoSuchElementException(
                        "No TradingProvider registered for broker: " + brokerType));
    }

    // ── Capabilities ──────────────────────────────────────────────────────────

    @Override
    public Optional<BrokerCapabilities> getCapabilities(BrokerType brokerType) {
        return Optional.ofNullable(capabilities.get(brokerType));
    }

    @Override
    public Collection<BrokerCapabilities> getAllCapabilities() {
        return capabilities.values();
    }

    @Override
    public Collection<BrokerType> getRegisteredBrokers() {
        // Union of all registered market + trading providers
        java.util.Set<BrokerType> all = java.util.EnumSet.noneOf(BrokerType.class);
        all.addAll(marketProviders.keySet());
        all.addAll(tradingProviders.keySet());
        return Collections.unmodifiableSet(all);
    }
}
