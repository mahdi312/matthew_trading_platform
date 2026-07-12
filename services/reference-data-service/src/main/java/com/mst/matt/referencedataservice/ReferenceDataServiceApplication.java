package com.mst.matt.referencedataservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Reference Data Service — Spring Boot entry point.
 *
 * <h3>Responsibility (Step 4.5)</h3>
 * <p>Hosts concrete implementations of the platform-wide data-provider
 * abstraction layer defined in {@code shared/contracts}:</p>
 * <ul>
 *   <li>{@link com.mst.matt.contracts.provider.fundamentals.FundamentalsProvider}</li>
 *   <li>{@link com.mst.matt.contracts.provider.nft.NftDataProvider}</li>
 *   <li>{@link com.mst.matt.contracts.provider.news.NewsProvider}</li>
 *   <li>{@link com.mst.matt.contracts.provider.sentiment.SentimentProvider}</li>
 *   <li>{@link com.mst.matt.contracts.provider.calendar.EconomicCalendarProvider}</li>
 *   <li>{@link com.mst.matt.contracts.provider.search.SymbolSearchProvider}</li>
 * </ul>
 *
 * <p>All providers are wired via {@code NoOpProviderConfig} with no-op/mock
 * implementations until real provider integrations (Finnhub, CoinGecko,
 * OpenSea, etc.) are added in later steps.</p>
 */
@SpringBootApplication
public class ReferenceDataServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReferenceDataServiceApplication.class, args);
    }
}
