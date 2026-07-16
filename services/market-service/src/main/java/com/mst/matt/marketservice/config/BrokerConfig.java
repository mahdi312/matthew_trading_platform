package com.mst.matt.marketservice.config;

import com.mst.matt.contracts.broker.market.MarketDataProvider;
import com.mst.matt.contracts.broker.registry.BrokerCapabilities;
import com.mst.matt.contracts.broker.registry.BrokerRegistry;
import com.mst.matt.contracts.broker.registry.DefaultBrokerRegistry;
import com.mst.matt.contracts.broker.trading.TradingProvider;
import com.mst.matt.contracts.enums.BrokerType;
import com.mst.matt.contracts.enums.InstrumentType;
import com.mst.matt.marketservice.bitunix.support.BitUnixIntervalSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wires {@link DefaultBrokerRegistry} as this service's {@link BrokerRegistry}
 * bean (Step 5.5) and declares BitUnix's {@link BrokerCapabilities}.
 *
 * <p>{@code DefaultBrokerRegistry} is intentionally not {@code @Component}
 * -annotated in {@code shared/contracts} (see its own Javadoc) — each
 * service that needs broker routing declares it as a {@code @Bean} here,
 * injecting whatever {@code MarketDataProvider}/{@code TradingProvider}/
 * {@code BrokerCapabilities} beans exist in its own application context.
 * market-service has zero {@link TradingProvider} beans (that is
 * trading-service's domain, Step 6) — Spring autowires an empty
 * {@code List<TradingProvider>} for {@link #brokerRegistry}, which is the
 * correct behaviour here, not a no-op placeholder bean.</p>
 */
@Configuration
public class BrokerConfig {

    /**
     * Capability descriptor for BitUnix, as implemented by
     * {@link com.mst.matt.marketservice.bitunix.BitUnixMarketDataProvider}.
     *
     * <ul>
     *   <li>{@code supportsLiveStream = true} — Step 5.4's WebSocket client.</li>
     *   <li>{@code supportedIntervals} — exactly
     *       {@link BitUnixIntervalSupport#SUPPORTED_INTERVALS}, the set the
     *       REST Kline endpoint accepts (and the WS kline channel covers via
     *       {@code BitUnixWsChannelSupport}'s translation).</li>
     *   <li>{@code supportsSpot = false}, {@code supportsFutures = true} —
     *       every endpoint used here is under BitUnix's {@code /futures}
     *       REST path and the {@code market_kline_*}/{@code ticker} WS
     *       channels are documented as part of the same futures feed; this
     *       provider does not touch BitUnix's (separate) spot market API.</li>
     *   <li>{@code maxLeverage} — BitUnix's futures leverage cap is
     *       trading-service's concern (order placement, Step 6), not
     *       market-data's; left at {@code 0} ("not applicable/unknown" per
     *       {@code BrokerCapabilities}'s own Javadoc) rather than guessing a
     *       number this service has no way to verify or use.</li>
     *   <li>{@code supportedInstruments = {CRYPTO_FUTURES}} — matches the
     *       futures-only scope above.</li>
     *   <li>{@code supportedOrderTypes} — left empty. Order types are
     *       meaningless for a market-data-only registration; trading-service
     *       (Step 6) will register BitUnix's real order-type support
     *       alongside its own {@code BitUnixTradingProvider}.</li>
     * </ul>
     */
    @Bean
    public BrokerCapabilities bitUnixBrokerCapabilities() {
        return BrokerCapabilities.builder()
                .brokerType(BrokerType.BITUNIX)
                .displayName("BitUnix")
                .supportsLiveStream(true)
                .supportedIntervals(BitUnixIntervalSupport.SUPPORTED_INTERVALS)
                .supportsSpot(false)
                .supportsFutures(true)
                .maxLeverage(0)
                .supportedInstrument(InstrumentType.CRYPTO_FUTURES)
                .build();
    }

    /**
     * {@code List<TradingProvider>} is empty in market-service's context —
     * see class Javadoc. Declared explicitly (rather than relying on an
     * implicit empty autowired list) only to make that intent unmistakable
     * at the call site.
     */
    @Bean
    public BrokerRegistry brokerRegistry(
            List<MarketDataProvider> marketProviders,
            List<TradingProvider> tradingProviders,
            List<BrokerCapabilities> capabilities) {
        return new DefaultBrokerRegistry(marketProviders, tradingProviders, capabilities);
    }
}
