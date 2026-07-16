package com.mst.matt.aiservice.config;

import com.mst.matt.aiservice.provider.MarketServiceOhlcvProvider;
import com.mst.matt.aiservice.service.AiAnalysisProviderImpl;
import com.mst.matt.contracts.provider.ai.AiAnalysisProvider;
import com.mst.matt.contracts.provider.mock.NoOpAiAnalysisProvider;
import com.mst.matt.contracts.provider.mock.NoOpOhlcvDataProvider;
import com.mst.matt.contracts.provider.ohlcv.OhlcvDataProvider;
import com.mst.matt.contracts.provider.registry.ProviderRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the provider registries used by ai-service analysis pipelines.
 *
 * <h3>AI analysis registry</h3>
 * <p>Priority order: {@link AiAnalysisProviderImpl} (LLM-backed) first,
 * then {@link NoOpAiAnalysisProvider} as a safe last-resort fallback.
 * The registry applies Resilience4j circuit-breaker protection per provider;
 * if the LLM provider trips a circuit breaker (e.g. repeated API failures)
 * the registry automatically falls through to the NoOp provider.</p>
 *
 * <h3>OHLCV registry (Gap 2)</h3>
 * <p>Priority order:
 * <ol>
 *   <li>{@link MarketServiceOhlcvProvider} — delegates to market-service via
 *       Feign; market-service resolves real bar data through its own provider
 *       registry (Binance → CoinGecko for crypto; AlphaVantage for stock; etc.).</li>
 *   <li>{@link NoOpOhlcvDataProvider} — returns empty lists; kept as final
 *       safety fallback so analysis never throws on a hard market-service outage.</li>
 * </ol>
 * </p>
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

    /**
     * OHLCV provider registry for the AI analysis pipeline.
     *
     * <p>{@link MarketServiceOhlcvProvider} is registered <em>first</em> so that
     * GET summary/signal endpoints receive real historical bars fetched from
     * market-service rather than empty lists.  {@link NoOpOhlcvDataProvider}
     * remains as the final fallback in case market-service is unreachable.</p>
     */
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
