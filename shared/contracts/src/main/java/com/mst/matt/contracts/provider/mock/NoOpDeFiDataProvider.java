package com.mst.matt.contracts.provider.mock;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.defi.DeFiDataProvider;
import com.mst.matt.contracts.provider.dto.DeFiPoolDto;

import java.util.List;

/**
 * No-op mock implementation of {@link DeFiDataProvider}.
 * Returns empty lists so the registry always has a safe fallback.
 */
public class NoOpDeFiDataProvider implements DeFiDataProvider {

    public static final String PROVIDER_NAME = "NOOP";

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public List<AssetClass> supportedAssetClasses() {
        return List.of(AssetClass.CRYPTO);
    }

    @Override
    public List<DeFiPoolDto> getTrendingPools() {
        return List.of();
    }

    @Override
    public List<DeFiPoolDto> getPoolsByNetwork(String networkId) {
        return List.of();
    }

    @Override
    public List<DeFiPoolDto> searchPools(String query) {
        return List.of();
    }
}
