package com.mst.matt.aiservice.config;

import com.mst.matt.contracts.provider.ai.AiAnalysisProvider;
import com.mst.matt.contracts.provider.mock.NoOpAiAnalysisProvider;
import com.mst.matt.contracts.provider.ohlcv.OhlcvDataProvider;
import com.mst.matt.contracts.provider.mock.NoOpOhlcvDataProvider;
import com.mst.matt.contracts.provider.registry.ProviderRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class NoOpAiProviderConfig {
    @Bean public AiAnalysisProvider noOpAiAnalysisProvider() { return new NoOpAiAnalysisProvider(); }
    @Bean public OhlcvDataProvider noOpOhlcvDataProvider() { return new NoOpOhlcvDataProvider(); }

    @Bean public ProviderRegistry<AiAnalysisProvider> aiRegistry(List<AiAnalysisProvider> p) { return ProviderRegistry.<AiAnalysisProvider>builder().registerAll(p).build(); }
    @Bean public ProviderRegistry<OhlcvDataProvider> ohlcvRegistry(List<OhlcvDataProvider> p) { return ProviderRegistry.<OhlcvDataProvider>builder().registerAll(p).build(); }
}
