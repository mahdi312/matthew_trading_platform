package com.mst.matt.contracts.provider.nft;

import com.mst.matt.contracts.provider.dto.NftAssetDto;
import com.mst.matt.contracts.provider.dto.NftCollectionDto;
import com.mst.matt.contracts.provider.dto.NftEventDto;

import java.util.List;
import java.util.Optional;

/**
 * Unified contract for fetching NFT collection, asset, and market data.
 *
 * <h3>Coverage</h3>
 * <p>Returns normalized {@link NftCollectionDto}, {@link NftAssetDto}, and
 * {@link NftEventDto} objects. No consumer (NFT tab, AI tab) should ever see a
 * provider-specific response shape — all provider implementations must map
 * their raw API responses to these DTOs.</p>
 *
 * <h3>Supported providers (future implementations)</h3>
 * <ul>
 *   <li><b>OpenSea</b> — collections, assets, events API</li>
 *   <li><b>Reservoir</b> — aggregated floor prices, rarity, multi-marketplace</li>
 * </ul>
 *
 * <h3>Implementation home</h3>
 * <p>Concrete implementations live in {@code reference-data-service}.
 * Only the no-op mock ({@code NoOpNftDataProvider}) is wired in Step 4.5.</p>
 *
 * <h3>Registry</h3>
 * <p>Implementations are registered in the
 * {@link com.mst.matt.contracts.provider.registry.ProviderRegistry} keyed by
 * {@code (AssetClass.NFT, providerName)}.</p>
 */
public interface NftDataProvider {

    /**
     * Returns the logical provider name (e.g., "OPENSEA", "RESERVOIR").
     */
    String providerName();

    // ── Collection ────────────────────────────────────────────────────────────

    /**
     * Fetch metadata and market statistics for a collection.
     *
     * @param collectionSlug canonical collection slug (e.g., "boredapeyachtclub")
     * @return an {@link Optional} containing the collection, or empty if not found
     */
    Optional<NftCollectionDto> getCollection(String collectionSlug);

    /**
     * Search collections by name or keyword.
     *
     * @param query  search term
     * @param limit  maximum number of results
     * @return list of matching {@link NftCollectionDto} objects (may be empty)
     */
    List<NftCollectionDto> searchCollections(String query, int limit);

    /**
     * Fetch trending collections ranked by volume or sales in the last 24 hours.
     *
     * @param limit maximum number of results
     * @return list of trending collections ordered by rank (most trending first)
     */
    List<NftCollectionDto> getTrendingCollections(int limit);

    // ── Individual token / asset ──────────────────────────────────────────────

    /**
     * Fetch metadata, rarity, and market data for a specific NFT token.
     *
     * @param collectionSlug canonical collection slug
     * @param tokenId        token ID within the collection
     * @return an {@link Optional} containing the NFT asset, or empty if not found
     */
    Optional<NftAssetDto> getAsset(String collectionSlug, String tokenId);

    /**
     * Fetch multiple tokens from a collection.
     *
     * @param collectionSlug canonical collection slug
     * @param limit          maximum number of results
     * @param offset         pagination offset
     * @return list of {@link NftAssetDto} objects; may be empty
     */
    List<NftAssetDto> getCollectionAssets(String collectionSlug, int limit, int offset);

    /**
     * Fetch the rarest tokens in a collection (by rarity rank).
     *
     * @param collectionSlug canonical collection slug
     * @param limit          maximum number of results
     * @return list of rarest {@link NftAssetDto} objects ordered by rarity rank
     */
    List<NftAssetDto> getRarestAssets(String collectionSlug, int limit);

    // ── Activity / events ─────────────────────────────────────────────────────

    /**
     * Fetch recent activity (sales, transfers, listings) for a collection.
     *
     * @param collectionSlug canonical collection slug
     * @param limit          maximum number of events to return
     * @return list of {@link NftEventDto} ordered most-recent-first
     */
    List<NftEventDto> getCollectionActivity(String collectionSlug, int limit);

    /**
     * Fetch recent activity for a specific token.
     *
     * @param collectionSlug canonical collection slug
     * @param tokenId        token ID within the collection
     * @param limit          maximum number of events to return
     * @return list of {@link NftEventDto} ordered most-recent-first
     */
    List<NftEventDto> getAssetActivity(String collectionSlug, String tokenId, int limit);

    // ── Floor price ───────────────────────────────────────────────────────────

    /**
     * Convenience method — fetch only the current floor price for a collection
     * as a lightweight alternative to the full {@link #getCollection(String)} call.
     *
     * @param collectionSlug canonical collection slug
     * @return a {@link NftCollectionDto} with at minimum
     *         {@link NftCollectionDto#getFloorPrice()} populated;
     *         other fields may be {@code null}
     */
    NftCollectionDto getFloorPrice(String collectionSlug);
}
