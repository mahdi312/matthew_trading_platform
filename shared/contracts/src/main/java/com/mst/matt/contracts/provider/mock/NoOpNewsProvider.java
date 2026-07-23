package com.mst.matt.contracts.provider.mock;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NewsArticleDto;
import com.mst.matt.contracts.provider.news.NewsProvider;

import java.time.Instant;
import java.util.List;

/**
 * No-op mock implementation of {@link NewsProvider}.
 * Returns empty data so that the registry compiles and is testable.
 * DO NOT use in production.
 */
public class NoOpNewsProvider implements NewsProvider {
    public static final String PROVIDER_NAME = "NOOP";

    @Override public String providerName() { return PROVIDER_NAME; }
    @Override public List<AssetClass> supportedAssetClasses() { return List.of(AssetClass.values()); }
    @Override public List<NewsArticleDto> getNewsBySymbol(String symbol, AssetClass assetClass, int limit) { return List.of(); }
    @Override public List<NewsArticleDto> getNewsBySymbol(String symbol, AssetClass assetClass, Instant from, Instant to, int limit) { return List.of(); }
    @Override public List<NewsArticleDto> getNewsByAssetClass(AssetClass assetClass, int limit) { return List.of(); }
    @Override public List<NewsArticleDto> searchNews(String query, AssetClass assetClass, int limit) { return List.of(); }
    @Override public List<NewsArticleDto> getNewsBatch(List<String> symbols, AssetClass assetClass, int limitEach) { return List.of(); }
}
