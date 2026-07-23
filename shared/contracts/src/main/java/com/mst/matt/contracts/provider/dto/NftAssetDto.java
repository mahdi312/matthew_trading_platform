package com.mst.matt.contracts.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Normalized metadata and market data for a single NFT token.
 *
 * <p>Returned by {@link com.mst.matt.contracts.provider.nft.NftDataProvider}.
 * No consumer should ever see a provider-specific response shape.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NftAssetDto {

    // ── Identity ──────────────────────────────────────────────────────────────

    /** Parent collection slug (see {@link NftCollectionDto#getCollectionSlug()}). */
    private String collectionSlug;

    /** Token ID within the collection (string to support large IDs). */
    private String tokenId;

    /** Token name (e.g., "Bored Ape #1234"). */
    private String name;

    /** Token description. */
    private String description;

    /** Blockchain the token lives on. */
    private String blockchain;

    /** Smart-contract address. */
    private String contractAddress;

    /** Name of the data provider that sourced this record. */
    private String providerName;

    /** Timestamp this record was fetched. */
    private Instant fetchedAt;

    // ── Media ─────────────────────────────────────────────────────────────────

    /** HTTPS URL for the token image (may be IPFS-resolved). */
    private String imageUrl;

    /** HTTPS URL for the animation / video (if applicable). */
    private String animationUrl;

    /** Raw metadata URI (IPFS or HTTPS). */
    private String metadataUri;

    // ── Traits / attributes ───────────────────────────────────────────────────

    /**
     * Trait map: trait-type → trait-value (e.g., "Background" → "Blue").
     * All values are strings; numeric trait values are also serialised as strings.
     */
    @Singular("trait")
    private Map<String, String> traits;

    // ── Rarity ────────────────────────────────────────────────────────────────

    /**
     * Rarity rank within the collection (1 = rarest); {@code null} if rarity
     * data is not available for this collection / provider.
     */
    private Integer rarityRank;

    /**
     * Rarity score (methodology varies by provider); {@code null} if not
     * available.
     */
    private BigDecimal rarityScore;

    // ── Market data ───────────────────────────────────────────────────────────

    /**
     * Current lowest active listing price in native currency; {@code null}
     * if not listed.
     */
    private BigDecimal listingPrice;

    /** Native currency of the listing price (e.g., "ETH"). */
    private String listingCurrency;

    /** Last sale price in native currency. */
    private BigDecimal lastSalePrice;

    /** Timestamp of the last sale. */
    private Instant lastSaleAt;

    // ── Ownership ─────────────────────────────────────────────────────────────

    /**
     * Current owner wallet address; may be {@code null} if the provider
     * does not expose ownership data.
     */
    private String ownerAddress;

    /** Number of owners (for ERC-1155 semi-fungible tokens). */
    private Integer ownerCount;

    // ── Activity ─────────────────────────────────────────────────────────────

    /** Recent transfer/sale events for this token. */
    private List<NftEventDto> recentEvents;
}
