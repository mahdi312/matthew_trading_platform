package com.mst.matt.aiservice.config;

import com.mst.matt.contracts.provider.mock.NoOpAiAnalysisProvider;
import com.mst.matt.contracts.provider.ohlcv.OhlcvDataProvider;
import com.mst.matt.contracts.provider.mock.NoOpOhlcvDataProvider;
import com.mst.matt.contracts.provider.ai.AiAnalysisProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares NoOp (fallback) provider beans.
 *
 * <p>These beans serve as last-resort fallbacks in the ProviderRegistry.
 * The real {@code AiAnalysisProvider} implementation is wired in
 * {@link AiAnalysisProviderConfig}.
 */
@Configuration
public class NoOpAiProviderConfig {

    @Bean
    public AiAnalysisProvider noOpAiAnalysisProvider() {
        return new NoOpAiAnalysisProvider();
    }

    @Bean
    public OhlcvDataProvider noOpOhlcvDataProvider() {
        return new NoOpOhlcvDataProvider();
    }
}
