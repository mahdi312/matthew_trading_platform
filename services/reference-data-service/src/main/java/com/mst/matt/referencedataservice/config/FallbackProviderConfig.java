package com.mst.matt.referencedataservice.config;

import com.mst.matt.contracts.provider.calendar.EconomicCalendarProvider;
import com.mst.matt.contracts.provider.defi.DeFiDataProvider;
import com.mst.matt.contracts.provider.fundamentals.FundamentalsProvider;
import com.mst.matt.contracts.provider.mock.*;
import com.mst.matt.contracts.provider.news.NewsProvider;
import com.mst.matt.contracts.provider.nft.NftDataProvider;
import com.mst.matt.contracts.provider.search.SymbolSearchProvider;
import com.mst.matt.contracts.provider.sentiment.SentimentProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the NoOp / fallback provider beans.
 *
 * <p>These beans act as last-resort fallbacks in every
 * {@link com.mst.matt.contracts.provider.registry.ProviderRegistry} chain — they
 * always return empty results rather than throwing, ensuring callers never see
 * a {@code ProviderUnavailableException} purely from missing API keys.</p>
 *
 * <p>Registry wiring (with explicit ordering) lives in
 * {@link ReferenceDataProviderConfig}.</p>
 */
@Configuration
public class FallbackProviderConfig {

    @Bean
    public FundamentalsProvider noOpFundamentalsProvider() {
        return new NoOpFundamentalsProvider();
    }

    @Bean
    public NftDataProvider noOpNftDataProvider() {
        return new NoOpNftDataProvider();
    }

    @Bean
    public NewsProvider noOpNewsProvider() {
        return new NoOpNewsProvider();
    }

    @Bean
    public SentimentProvider noOpSentimentProvider() {
        return new NoOpSentimentProvider();
    }

    @Bean
    public EconomicCalendarProvider noOpEconomicCalendarProvider() {
        return new NoOpEconomicCalendarProvider();
    }

    @Bean
    public SymbolSearchProvider noOpSymbolSearchProvider() {
        return new NoOpSymbolSearchProvider();
    }

    @Bean
    public DeFiDataProvider noOpDeFiDataProvider() {
        return new NoOpDeFiDataProvider();
    }
}
