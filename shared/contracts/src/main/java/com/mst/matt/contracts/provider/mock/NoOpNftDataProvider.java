package com.mst.matt.contracts.provider.mock;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NftAssetDto;
import com.mst.matt.contracts.provider.dto.NftCollectionDto;
import com.mst.matt.contracts.provider.dto.NftEventDto;
import com.mst.matt.contracts.provider.nft.NftDataProvider;

import java.util.List;
import java.util.Optional;

/**
 * No-op mock implementation of {@link NftDataProvider}.
 * Returns empty data so that the registry compiles and is testable.
 * DO NOT use in production.
 */
public class NoOpNftDataProvider implements NftDataProvider {
    public static final String PROVIDER_NAME = "NOOP";

    @Override public String providerName() { return PROVIDER_NAME; }
    @Override public List<AssetClass> supportedAssetClasses() { return List.of(AssetClass.NFT); }
    @Override public Optional<NftCollectionDto> getCollection(String collectionSlug) { return Optional.empty(); }
    @Override public List<NftCollectionDto> searchCollections(String query, int limit) { return List.of(); }
    @Override public List<NftCollectionDto> getTrendingCollections(int limit) { return List.of(); }
    @Override public Optional<NftAssetDto> getAsset(String collectionSlug, String tokenId) { return Optional.empty(); }
    @Override public List<NftAssetDto> getCollectionAssets(String collectionSlug, int limit, int offset) { return List.of(); }
    @Override public List<NftAssetDto> getRarestAssets(String collectionSlug, int limit) { return List.of(); }
    @Override public List<NftEventDto> getCollectionActivity(String collectionSlug, int limit) { return List.of(); }
    @Override public List<NftEventDto> getAssetActivity(String collectionSlug, String tokenId, int limit) { return List.of(); }
    @Override public NftCollectionDto getFloorPrice(String collectionSlug) { return NftCollectionDto.builder().collectionSlug(collectionSlug).providerName(PROVIDER_NAME).build(); }
}
