package com.mst.matt.contracts.provider.mock;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.ai.AiAnalysisProvider;
import com.mst.matt.contracts.provider.dto.AiMarketSummaryDto;
import com.mst.matt.contracts.provider.dto.AiSignalDto;
import com.mst.matt.contracts.provider.dto.AiTradeJournalCritiqueDto;
import com.mst.matt.contracts.provider.dto.NewsArticleDto;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;

import java.time.Instant;
import java.util.List;

/**
 * No-op mock implementation of {@link AiAnalysisProvider}.
 * Returns neutral/empty data so the registry compiles and is testable.
 * DO NOT use in production.
 */
public class NoOpAiAnalysisProvider implements AiAnalysisProvider {
    public static final String PROVIDER_NAME = "NOOP";

    @Override public String providerName() { return PROVIDER_NAME; }
    @Override public List<AssetClass> supportedAssetClasses() { return List.of(AssetClass.values()); }

    @Override
    public AiMarketSummaryDto summarizeMarket(String symbol, AssetClass assetClass,
                                               List<NormalizedOhlcvBar> ohlcvBars,
                                               List<NewsArticleDto> newsArticles) {
        return AiMarketSummaryDto.builder()
                .symbol(symbol).assetClass(assetClass)
                .providerName(PROVIDER_NAME).generatedAt(Instant.now())
                .headline("No-op: AI analysis not configured")
                .summary("No-op provider. Wire a real AiAnalysisProvider implementation.")
                .sentimentScore(0.0).sentimentLabel("NEUTRAL").confidence(0.0)
                .build();
    }

    @Override
    public List<NewsArticleDto> scoreNewsSentiment(String symbol, AssetClass assetClass,
                                                    List<NewsArticleDto> newsArticles) {
        return newsArticles; // return as-is; no scoring applied
    }

    @Override
    public List<AiSignalDto> generateSignals(String symbol, AssetClass assetClass,
                                              List<NormalizedOhlcvBar> ohlcvBars,
                                              List<NewsArticleDto> newsArticles) {
        return List.of();
    }

    @Override
    public AiTradeJournalCritiqueDto critiqueTradeJournalEntry(String tradeId,
                                                                String tradeContext,
                                                                AssetClass assetClass) {
        return AiTradeJournalCritiqueDto.builder()
                .tradeId(tradeId).providerName(PROVIDER_NAME).generatedAt(Instant.now())
                .overallScore(0)
                .overallAssessment("No-op provider. Wire a real AiAnalysisProvider.")
                .build();
    }
}
