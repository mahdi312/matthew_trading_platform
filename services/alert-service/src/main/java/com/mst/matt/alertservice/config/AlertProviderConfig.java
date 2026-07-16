package com.mst.matt.alertservice.config;

import com.mst.matt.contracts.provider.mock.NoOpOhlcvDataProvider;
import com.mst.matt.contracts.provider.ohlcv.OhlcvDataProvider;
import com.mst.matt.contracts.provider.registry.ProviderRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wires the {@code OhlcvDataProvider} registry that
 * {@code AlertEvaluationService} uses to read live/latest prices.
 *
 * <h3>Why {@code OhlcvDataProvider} and not the broker-tied {@code MarketDataProvider}</h3>
 * <p>{@code alert-service} watches symbols across every {@code AssetClass}
 * (stocks, crypto, forex — see Step 4.5), not just broker-tradeable
 * instruments, so it resolves prices through the asset-class-aware
 * {@link ProviderRegistry}&lt;{@link OhlcvDataProvider}&gt; rather than the
 * single-broker {@code BrokerRegistry} from Step 4. This mirrors the same
 * registry-config pattern used by {@code reference-data-service} and
 * {@code ai-service} in Step 4.5.</p>
 *
 * <p>Boots with {@link NoOpOhlcvDataProvider} for now — once
 * {@code market-service}'s BitUnix implementation (Step 5) and other real
 * {@code OhlcvDataProvider} implementations exist, they register themselves
 * as additional beans here and the fallback chain picks them up automatically,
 * with zero changes to {@code AlertEvaluationService}.</p>
 */
@Configuration
public class AlertProviderConfig {

    @Bean
    public OhlcvDataProvider noOpOhlcvDataProvider() {
        return new NoOpOhlcvDataProvider();
    }

    @Bean
    public ProviderRegistry<OhlcvDataProvider> ohlcvRegistry(List<OhlcvDataProvider> providers) {
        return ProviderRegistry.<OhlcvDataProvider>builder().registerAll(providers).build();
    }
}
