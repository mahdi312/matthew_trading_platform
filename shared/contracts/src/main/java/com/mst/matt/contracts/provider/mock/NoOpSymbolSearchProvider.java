package com.mst.matt.contracts.provider.mock;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.SymbolSearchResultDto;
import com.mst.matt.contracts.provider.search.SymbolSearchProvider;

import java.util.List;
import java.util.Optional;

/**
 * No-op mock implementation of {@link SymbolSearchProvider}.
 * Returns empty data so the registry compiles and is testable.
 * DO NOT use in production.
 */
public class NoOpSymbolSearchProvider implements SymbolSearchProvider {
    public static final String PROVIDER_NAME = "NOOP";

    @Override public String providerName() { return PROVIDER_NAME; }
    @Override public List<AssetClass> supportedAssetClasses() { return List.of(AssetClass.values()); }
    @Override public List<SymbolSearchResultDto> search(String query, int limit) { return List.of(); }
    @Override public List<SymbolSearchResultDto> search(String query, AssetClass assetClass, int limit) { return List.of(); }
    @Override public Optional<SymbolSearchResultDto> lookup(String symbol, AssetClass assetClass) { return Optional.empty(); }
    @Override public List<SymbolSearchResultDto> listAll(AssetClass assetClass, int limit, int offset) { return List.of(); }
    @Override public List<SymbolSearchResultDto> getTopSymbols(AssetClass assetClass, int limit) { return List.of(); }
}
