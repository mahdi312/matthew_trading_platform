package com.mst.matt.contracts.provider.defi;

import com.mst.matt.contracts.provider.dto.DeFiPoolDto;
import com.mst.matt.contracts.provider.registry.DataProvider;

import java.util.List;

/**
 * Unified contract for on-chain DeFi pool data (liquidity pools, APY, TVL).
 *
 * <p>Concrete implementations live in {@code reference-data-service} and are
 * registered in a {@link com.mst.matt.contracts.provider.registry.ProviderRegistry}
 * with a {@code NoOpDeFiDataProvider} fallback — same pattern as
 * {@link com.mst.matt.contracts.provider.nft.NftDataProvider}.</p>
 */
public interface DeFiDataProvider extends DataProvider {

    String providerName();

    /**
     * Globally trending pools ranked by volume / activity.
     */
    List<DeFiPoolDto> getTrendingPools();

    /**
     * Pools for a specific network id (e.g. {@code eth}, {@code solana}).
     */
    List<DeFiPoolDto> getPoolsByNetwork(String networkId);

    /**
     * Search pools by token symbol, pair name, or address fragment.
     */
    List<DeFiPoolDto> searchPools(String query);
}
