package com.mst.matt.referencedataservice;

import com.mst.matt.referencedataservice.config.RefDataProviderProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Reference Data Service — Spring Boot entry point.
 *
 * <h3>Responsibility</h3>
 * <p>Hosts concrete implementations of the platform-wide data-provider
 * abstraction layer defined in {@code shared/contracts}:</p>
 * <ul>
 *   <li>{@link com.mst.matt.contracts.provider.fundamentals.FundamentalsProvider}
 *       — AlphaVantage + Finnhub</li>
 *   <li>{@link com.mst.matt.contracts.provider.news.NewsProvider}
 *       — AlphaVantage + Finnhub</li>
 *   <li>{@link com.mst.matt.contracts.provider.sentiment.SentimentProvider}
 *       — CoinMarketCap + CoinGecko</li>
 *   <li>{@link com.mst.matt.contracts.provider.calendar.EconomicCalendarProvider}
 *       — AlphaVantage + Finnhub</li>
 *   <li>{@link com.mst.matt.contracts.provider.search.SymbolSearchProvider}
 *       — AlphaVantage + Finnhub + TwelveData + CoinGecko</li>
 *   <li>{@link com.mst.matt.contracts.provider.nft.NftDataProvider}
 *       — NoOp only (not yet implemented)</li>
 * </ul>
 *
 * <p>All providers are priority-chained via
 * {@link com.mst.matt.contracts.provider.registry.ProviderRegistry} with
 * per-provider Resilience4j circuit breakers. NoOp fallback providers ensure
 * the service starts cleanly even when all API keys are absent.</p>
 *
 * <p>Runs on port {@code 8085} and registers with Eureka.</p>
 */
@SpringBootApplication
@EnableFeignClients
@EnableConfigurationProperties(RefDataProviderProperties.class)
public class ReferenceDataServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReferenceDataServiceApplication.class, args);
    }
}
