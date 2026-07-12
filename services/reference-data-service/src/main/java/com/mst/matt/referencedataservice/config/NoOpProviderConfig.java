package com.mst.matt.referencedataservice.config;

import com.mst.matt.contracts.provider.calendar.EconomicCalendarProvider;
import com.mst.matt.contracts.provider.fundamentals.FundamentalsProvider;
import com.mst.matt.contracts.provider.mock.*;
import com.mst.matt.contracts.provider.news.NewsProvider;
import com.mst.matt.contracts.provider.nft.NftDataProvider;
import com.mst.matt.contracts.provider.registry.ProviderRegistry;
import com.mst.matt.contracts.provider.search.SymbolSearchProvider;
import com.mst.matt.contracts.provider.sentiment.SentimentProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class NoOpProviderConfig {
    @Bean public FundamentalsProvider noOpFundamentalsProvider() { return new NoOpFundamentalsProvider(); }
    @Bean public NftDataProvider noOpNftDataProvider() { return new NoOpNftDataProvider(); }
    @Bean public NewsProvider noOpNewsProvider() { return new NoOpNewsProvider(); }
    @Bean public SentimentProvider noOpSentimentProvider() { return new NoOpSentimentProvider(); }
    @Bean public EconomicCalendarProvider noOpEconomicCalendarProvider() { return new NoOpEconomicCalendarProvider(); }
    @Bean public SymbolSearchProvider noOpSymbolSearchProvider() { return new NoOpSymbolSearchProvider(); }

    @Bean public ProviderRegistry<FundamentalsProvider> fundamentalsRegistry(List<FundamentalsProvider> p) { return ProviderRegistry.<FundamentalsProvider>builder().registerAll(p).build(); }
    @Bean public ProviderRegistry<NftDataProvider> nftRegistry(List<NftDataProvider> p) { return ProviderRegistry.<NftDataProvider>builder().registerAll(p).build(); }
    @Bean public ProviderRegistry<NewsProvider> newsRegistry(List<NewsProvider> p) { return ProviderRegistry.<NewsProvider>builder().registerAll(p).build(); }
    @Bean public ProviderRegistry<SentimentProvider> sentimentRegistry(List<SentimentProvider> p) { return ProviderRegistry.<SentimentProvider>builder().registerAll(p).build(); }
    @Bean public ProviderRegistry<EconomicCalendarProvider> calendarRegistry(List<EconomicCalendarProvider> p) { return ProviderRegistry.<EconomicCalendarProvider>builder().registerAll(p).build(); }
    @Bean public ProviderRegistry<SymbolSearchProvider> searchRegistry(List<SymbolSearchProvider> p) { return ProviderRegistry.<SymbolSearchProvider>builder().registerAll(p).build(); }
}
