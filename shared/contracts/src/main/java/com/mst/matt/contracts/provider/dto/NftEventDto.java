package com.mst.matt.contracts.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Normalized NFT transfer or sale event.
 *
 * <p>Used inside {@link NftAssetDto#getRecentEvents()} and returned by
 * {@link com.mst.matt.contracts.provider.nft.NftDataProvider#getCollectionActivity}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NftEventDto {

    /** Type of event (e.g., "SALE", "TRANSFER", "LISTING", "OFFER", "MINT"). */
    private String eventType;

    /** Token ID involved in this event. */
    private String tokenId;

    /** Collection slug. */
    private String collectionSlug;

    /** Transaction hash on-chain (may be {@code null} for off-chain events). */
    private String transactionHash;

    /** Seller / from address; {@code null} for mint events. */
    private String fromAddress;

    /** Buyer / to address. */
    private String toAddress;

    /** Price paid in native currency; {@code null} for transfers/mints. */
    private BigDecimal price;

    /** Native currency of the price (e.g., "ETH"). */
    private String currency;

    /** Price in USD equivalent at the time of the event. */
    private BigDecimal priceUsd;

    /** Marketplace where the sale occurred (e.g., "OpenSea", "Blur"). */
    private String marketplace;

    /** Timestamp of the on-chain event. */
    private Instant eventTime;
}
