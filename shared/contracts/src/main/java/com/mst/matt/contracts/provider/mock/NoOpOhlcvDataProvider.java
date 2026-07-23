package com.mst.matt.contracts.provider.mock;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import com.mst.matt.contracts.provider.ohlcv.OhlcvDataProvider;

import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/**
 * No-op mock implementation of {@link OhlcvDataProvider}.
 * Returns empty/default data so that the registry and downstream services
 * compile and are testable before real providers exist.
 * DO NOT use in production.
 */
public class NoOpOhlcvDataProvider implements OhlcvDataProvider {
    public static final String PROVIDER_NAME = "NOOP";

    @Override public String providerName() { return PROVIDER_NAME; }
    @Override public List<AssetClass> supportedAssetClasses() { return List.of(AssetClass.values()); }
    @Override public List<NormalizedOhlcvBar> getHistoricalBars(String symbol, AssetClass assetClass, String interval, int limit) { return List.of(); }
    @Override public List<NormalizedOhlcvBar> getHistoricalBars(String symbol, AssetClass assetClass, String interval, Instant from, Instant to) { return List.of(); }
    @Override public Stream<NormalizedOhlcvBar> streamLiveBars(String symbol, AssetClass assetClass, String interval) { return Stream.empty(); }
    @Override public boolean supportsStreaming(AssetClass assetClass) { return false; }
}
