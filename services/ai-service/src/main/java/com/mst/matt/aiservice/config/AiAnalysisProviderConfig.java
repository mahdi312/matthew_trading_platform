package com.mst.matt.aiservice.config;

import com.mst.matt.aiservice.service.AiAnalysisProviderImpl;
import com.mst.matt.contracts.provider.ai.AiAnalysisProvider;
import com.mst.matt.contracts.provider.mock.NoOpAiAnalysisProvider;
import com.mst.matt.contracts.provider.mock.NoOpOhlcvDataProvider;
import com.mst.matt.contracts.provider.ohlcv.OhlcvDataProvider;
import com.mst.matt.contracts.provider.registry.ProviderRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the real {@link AiAnalysisProvider} registry.
 *
 * <p>Priority order: {@link AiAnalysisProviderImpl} (LLM-backed) first,
 * then {@link NoOpAiAnalysisProvider} as a safe last-resort fallback.
 *
 * <p>The registry applies Resilience4j circuit-breaker protection per provider.
 * If the LLM provider trips a circuit breaker (e.g. repeated API failures)
 * the registry automatically falls through to the NoOp provider.
 */
@Configuration
public class AiAnalysisProviderConfig {

    @Bean
    public ProviderRegistry<AiAnalysisProvider> aiRegistry(
            AiAnalysisProviderImpl realProvider,
            NoOpAiAnalysisProvider noOpAiAnalysisProvider) {

        return ProviderRegistry.<AiAnalysisProvider>builder()
                .register(realProvider)
                .register(noOpAiAnalysisProvider)
                .build();
    }

    @Bean
    public ProviderRegistry<OhlcvDataProvider> ohlcvRegistry(
            NoOpOhlcvDataProvider noOpOhlcvDataProvider) {

        return ProviderRegistry.<OhlcvDataProvider>builder()
                .register(noOpOhlcvDataProvider)
                .build();
    }
}
