package com.mst.matt.alertservice.config;

import com.mst.matt.alertservice.provider.MarketServiceOhlcvProvider;
import com.mst.matt.contracts.provider.mock.NoOpOhlcvDataProvider;
import com.mst.matt.contracts.provider.ohlcv.OhlcvDataProvider;
import com.mst.matt.contracts.provider.registry.ProviderRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@code OhlcvDataProvider} registry that
 * {@code AlertEvaluationService} uses to read live/latest prices.
 *
 * <p>Priority: {@link MarketServiceOhlcvProvider} (Feign → market-service) →
 * {@link NoOpOhlcvDataProvider} fallback.</p>
 */
@Configuration
public class AlertProviderConfig {

    @Bean
    public NoOpOhlcvDataProvider noOpOhlcvDataProvider() {
        return new NoOpOhlcvDataProvider();
    }

    @Bean
    public ProviderRegistry<OhlcvDataProvider> ohlcvRegistry(
            MarketServiceOhlcvProvider marketServiceOhlcvProvider,
            NoOpOhlcvDataProvider noOpOhlcvDataProvider) {

        return ProviderRegistry.<OhlcvDataProvider>builder()
                .register(marketServiceOhlcvProvider)
                .register(noOpOhlcvDataProvider)
                .build();
    }
}
