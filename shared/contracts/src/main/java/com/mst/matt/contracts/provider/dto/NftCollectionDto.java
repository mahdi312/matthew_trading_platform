package com.mst.matt.contracts.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Normalized NFT collection metadata and market statistics.
 *
 * <p>Returned by {@link com.mst.matt.contracts.provider.nft.NftDataProvider}.
 * No consumer should ever see a provider-specific response shape.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NftCollectionDto {

    // ── Identity ──────────────────────────────────────────────────────────────

    /**
     * Canonical collection slug / identifier used across the platform
     * (e.g., "boredapeyachtclub", "cryptopunks").
     */
    private String collectionSlug;

    /** Human-readable collection name (e.g., "Bored Ape Yacht Club"). */
    private String name;

    /** Collection description. */
    private String description;

    /**
     * Blockchain the collection lives on (e.g., "ethereum", "solana",
     * "polygon").
     */
    private String blockchain;

    /**
     * Smart-contract address of the collection; may be {@code null} for
     * off-chain or multi-contract collections.
     */
    private String contractAddress;

    /** NFT standard (e.g., "ERC-721", "ERC-1155", "SPL"). */
    private String tokenStandard;

    /** Name of the data provider that sourced this record. */
    private String providerName;

    /** Timestamp this record was fetched / last updated. */
    private Instant fetchedAt;

    // ── Supply ────────────────────────────────────────────────────────────────

    /** Total number of NFTs in the collection. */
    private Long totalSupply;

    /** Number of unique owner wallets. */
    private Long ownerCount;

    /** Ownership concentration — percentage of supply held by the top 10 wallets. */
    private BigDecimal top10HolderPct;

    // ── Pricing ───────────────────────────────────────────────────────────────

    /** Current floor price in native currency (e.g., ETH). */
    private BigDecimal floorPrice;

    /** Floor price denominated in USD. */
    private BigDecimal floorPriceUsd;

    /** Native currency used for floor price (e.g., "ETH", "SOL"). */
    private String nativeCurrency;

    /** 24-hour percentage change in floor price (e.g., 0.05 = 5%). */
    private BigDecimal floorPriceChange24hPct;

    /** 7-day percentage change in floor price. */
    private BigDecimal floorPriceChange7dPct;

    /** All-time high floor price in native currency. */
    private BigDecimal athFloorPrice;

    /** Date of the all-time high floor price. */
    private Instant athFloorDate;

    // ── Volume & liquidity ────────────────────────────────────────────────────

    /** 24-hour trading volume in native currency. */
    private BigDecimal volume24h;

    /** 24-hour trading volume in USD. */
    private BigDecimal volume24hUsd;

    /** 7-day trading volume in native currency. */
    private BigDecimal volume7d;

    /** All-time cumulative trading volume in native currency. */
    private BigDecimal volumeAllTime;

    /** Number of sales in the last 24 hours. */
    private Long sales24h;

    /** Average sale price in the last 24 hours in native currency. */
    private BigDecimal avgPrice24h;

    // ── Rarity / metadata ─────────────────────────────────────────────────────

    /** Category tags (e.g., "PFP", "Gaming", "Art", "Metaverse"). */
    private List<String> categories;

    /** External links (website, twitter, discord). */
    private List<String> externalLinks;

    /** IPFS or HTTPS base URI for metadata. */
    private String metadataBaseUri;

    /** Whether on-chain rarity ranking is available via this provider. */
    private boolean rarityAvailable;

    // ── Social ────────────────────────────────────────────────────────────────

    /** Twitter follower count; {@code null} if unavailable. */
    private Long twitterFollowers;

    /** Discord member count; {@code null} if unavailable. */
    private Long discordMembers;
}
