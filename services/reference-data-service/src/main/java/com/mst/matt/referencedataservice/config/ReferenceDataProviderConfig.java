package com.mst.matt.referencedataservice.config;

import com.mst.matt.contracts.provider.calendar.EconomicCalendarProvider;
import com.mst.matt.contracts.provider.fundamentals.FundamentalsProvider;
import com.mst.matt.contracts.provider.news.NewsProvider;
import com.mst.matt.contracts.provider.nft.NftDataProvider;
import com.mst.matt.contracts.provider.registry.ProviderRegistry;
import com.mst.matt.contracts.provider.search.SymbolSearchProvider;
import com.mst.matt.contracts.provider.sentiment.SentimentProvider;
import com.mst.matt.referencedataservice.provider.calendar.AlphaVantageCalendarProvider;
import com.mst.matt.referencedataservice.provider.calendar.FinnhubCalendarProvider;
import com.mst.matt.referencedataservice.provider.fundamentals.AlphaVantageFundamentalsProvider;
import com.mst.matt.referencedataservice.provider.fundamentals.FinnhubFundamentalsProvider;
import com.mst.matt.referencedataservice.provider.news.AlphaVantageNewsProvider;
import com.mst.matt.referencedataservice.provider.news.FinnhubNewsProvider;
import com.mst.matt.referencedataservice.provider.nft.CoinGeckoNftDataProvider;
import com.mst.matt.referencedataservice.provider.search.AlphaVantageSearchProvider;
import com.mst.matt.referencedataservice.provider.search.CoinGeckoSearchProvider;
import com.mst.matt.referencedataservice.provider.search.FinnhubSearchProvider;
import com.mst.matt.referencedataservice.provider.search.TwelveDataSearchProvider;
import com.mst.matt.referencedataservice.provider.sentiment.CmcSentimentProvider;
import com.mst.matt.referencedataservice.provider.sentiment.CoinGeckoSentimentProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Wires every {@link ProviderRegistry} with an explicit, priority-ordered provider chain.
 *
 * <h3>Priority chains</h3>
 * <ul>
 *   <li><b>Fundamentals</b>: Finnhub → AlphaVantage → NoOp</li>
 *   <li><b>News</b>:         AlphaVantage → Finnhub → NoOp</li>
 *   <li><b>Sentiment</b>:   CMC → CoinGecko → NoOp</li>
 *   <li><b>Calendar</b>:    AlphaVantage → Finnhub → NoOp</li>
 *   <li><b>Search</b>:      AlphaVantage → Finnhub → TwelveData → CoinGecko → NoOp</li>
 *   <li><b>NFT</b>:         CoinGecko → NoOp</li>
 * </ul>
 *
 * <p>Registration order = priority order inside each chain.
 * Providers are registered explicitly (not via {@code List<T>} auto-injection)
 * so that ordering is deterministic and independent of bean discovery order.</p>
 *
 * <p>{@link RefDataProviderProperties} is enabled here; no annotation is needed
 * on the application class.</p>
 */
@Configuration
@EnableConfigurationProperties(RefDataProviderProperties.class)
public class ReferenceDataProviderConfig {

    // ── FundamentalsProvider registry ─────────────────────────────────────────
    // Priority: Finnhub (STOCK) → AlphaVantage (STOCK + FOREX) → NoOp

    @Bean
    public ProviderRegistry<FundamentalsProvider> fundamentalsRegistry(
            FinnhubFundamentalsProvider finnhub,
            AlphaVantageFundamentalsProvider alphaVantage,
            FundamentalsProvider noOpFundamentalsProvider) {

        return ProviderRegistry.<FundamentalsProvider>builder()
                .register(finnhub)
                .register(alphaVantage)
                .register(noOpFundamentalsProvider)
                .build();
    }

    // ── NewsProvider registry ─────────────────────────────────────────────────
    // Priority: AlphaVantage → Finnhub → NoOp

    @Bean
    public ProviderRegistry<NewsProvider> newsRegistry(
            AlphaVantageNewsProvider alphaVantage,
            FinnhubNewsProvider finnhub,
            NewsProvider noOpNewsProvider) {

        return ProviderRegistry.<NewsProvider>builder()
                .register(alphaVantage)
                .register(finnhub)
                .register(noOpNewsProvider)
                .build();
    }

    // ── SentimentProvider registry ────────────────────────────────────────────
    // Priority: CMC → CoinGecko → NoOp

    @Bean
    public ProviderRegistry<SentimentProvider> sentimentRegistry(
            CmcSentimentProvider cmc,
            CoinGeckoSentimentProvider coinGecko,
            SentimentProvider noOpSentimentProvider) {

        return ProviderRegistry.<SentimentProvider>builder()
                .register(cmc)
                .register(coinGecko)
                .register(noOpSentimentProvider)
                .build();
    }

    // ── EconomicCalendarProvider registry ────────────────────────────────────
    // Priority: AlphaVantage → Finnhub → NoOp

    @Bean
    public ProviderRegistry<EconomicCalendarProvider> calendarRegistry(
            AlphaVantageCalendarProvider alphaVantage,
            FinnhubCalendarProvider finnhub,
            EconomicCalendarProvider noOpEconomicCalendarProvider) {

        return ProviderRegistry.<EconomicCalendarProvider>builder()
                .register(alphaVantage)
                .register(finnhub)
                .register(noOpEconomicCalendarProvider)
                .build();
    }

    // ── SymbolSearchProvider registry ─────────────────────────────────────────
    // Priority: AlphaVantage → Finnhub → TwelveData → CoinGecko → NoOp

    @Bean
    public ProviderRegistry<SymbolSearchProvider> searchRegistry(
            AlphaVantageSearchProvider alphaVantage,
            FinnhubSearchProvider finnhub,
            TwelveDataSearchProvider twelveData,
            CoinGeckoSearchProvider coinGecko,
            SymbolSearchProvider noOpSymbolSearchProvider) {

        return ProviderRegistry.<SymbolSearchProvider>builder()
                .register(alphaVantage)
                .register(finnhub)
                .register(twelveData)
                .register(coinGecko)
                .register(noOpSymbolSearchProvider)
                .build();
    }

    // ── NftDataProvider registry ──────────────────────────────────────────────
    // Priority: CoinGecko → NoOp

    @Bean
    public ProviderRegistry<NftDataProvider> nftRegistry(
            CoinGeckoNftDataProvider coinGecko,
            NftDataProvider noOpNftDataProvider) {

        return ProviderRegistry.<NftDataProvider>builder()
                .register(coinGecko)
                .register(noOpNftDataProvider)
                .build();
    }
}
