package com.mst.matt.contracts.provider.mock;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.SentimentSnapshotDto;
import com.mst.matt.contracts.provider.sentiment.SentimentProvider;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * No-op mock implementation of {@link SentimentProvider}.
 * Returns neutral data so the registry compiles and is testable.
 * DO NOT use in production.
 */
public class NoOpSentimentProvider implements SentimentProvider {
    public static final String PROVIDER_NAME = "NOOP";

    @Override public String providerName() { return PROVIDER_NAME; }
    @Override public List<AssetClass> supportedAssetClasses() { return List.of(AssetClass.values()); }
    @Override public Optional<SentimentSnapshotDto> getSentiment(String symbol, AssetClass assetClass) { return Optional.empty(); }
    @Override public List<SentimentSnapshotDto> getSentimentBatch(List<String> symbols, AssetClass assetClass) { return List.of(); }
    @Override public SentimentSnapshotDto getMarketSentimentIndex(AssetClass assetClass) {
        return SentimentSnapshotDto.builder()
                .assetClass(assetClass).providerName(PROVIDER_NAME).snapshotAt(Instant.now())
                .sentimentScore(0.0).sentimentLabel("NEUTRAL").rawIndexValue(50)
                .build();
    }
    @Override public List<SentimentSnapshotDto> getTrendingByVolume(AssetClass assetClass, int limit) { return List.of(); }
    @Override public List<SentimentSnapshotDto> getMostBullish(AssetClass assetClass, int limit) { return List.of(); }
    @Override public List<SentimentSnapshotDto> getMostBearish(AssetClass assetClass, int limit) { return List.of(); }
}
