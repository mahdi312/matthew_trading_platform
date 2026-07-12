package com.mst.matt.contracts.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Normalized tokenomics / fundamentals data for cryptocurrency assets.
 *
 * <p>Returned by {@link com.mst.matt.contracts.provider.fundamentals.FundamentalsProvider}
 * for {@link com.mst.matt.contracts.enums.AssetClass#CRYPTO}.
 * No consumer should ever see a provider-specific response shape.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CryptoTokenomicsDto {

    // ── Identity ──────────────────────────────────────────────────────────────

    /** Canonical platform ticker symbol (e.g., "BTC", "ETH"). */
    private String symbol;

    /** Full token / coin name (e.g., "Bitcoin", "Ethereum"). */
    private String name;

    /** Short description of the project. */
    private String description;

    /**
     * Blockchain / network the token lives on (e.g., "Bitcoin", "Ethereum",
     * "Solana").  Multi-chain tokens may list multiple.
     */
    private List<String> blockchains;

    /** Token category tags (e.g., "DeFi", "Layer1", "Meme"). */
    private List<String> categories;

    /** Name of the data provider that sourced this record. */
    private String providerName;

    /** Date this record was fetched / last updated. */
    private LocalDate fetchDate;

    // ── Market metrics ────────────────────────────────────────────────────────

    /** USD-denominated market capitalisation. */
    private BigDecimal marketCapUsd;

    /** Fully diluted valuation (FDV) in USD. */
    private BigDecimal fullyDilutedValuationUsd;

    /** 24-hour trading volume in USD across all exchanges. */
    private BigDecimal volume24hUsd;

    /** Market cap rank by total market cap (lower = larger). */
    private Integer marketCapRank;

    // ── Supply ────────────────────────────────────────────────────────────────

    /** Number of coins/tokens currently in circulating supply. */
    private BigDecimal circulatingSupply;

    /** Total supply (circulating + locked/reserved). */
    private BigDecimal totalSupply;

    /**
     * Maximum possible supply; {@code null} for assets with no hard cap
     * (e.g., ETH post-merge inflation model).
     */
    private BigDecimal maxSupply;

    /** Inflation rate per year (e.g., 0.02 = 2%); may be negative (deflationary). */
    private BigDecimal inflationRateYearly;

    // ── On-chain / network metrics ────────────────────────────────────────────

    /** Number of active addresses in the last 24 hours. */
    private Long activeAddresses24h;

    /** Number of transactions in the last 24 hours. */
    private Long transactions24h;

    /** Average transaction fee in native token units. */
    private BigDecimal avgTransactionFee;

    /** Hash rate (PoW chains); {@code null} for PoS. */
    private BigDecimal hashRate;

    /** Network stake ratio — percentage of total supply staked (PoS). */
    private BigDecimal stakeRatio;

    // ── Vesting / token distribution ─────────────────────────────────────────

    /** Percentage of supply held by team / insiders. */
    private BigDecimal teamAllocationPct;

    /**
     * Next major unlock event date; {@code null} if no scheduled unlock.
     */
    private Instant nextUnlockDate;

    /** Amount to be unlocked at {@link #nextUnlockDate}, in token units. */
    private BigDecimal nextUnlockAmount;

    // ── DeFi / protocol specific ──────────────────────────────────────────────

    /** Total value locked in the protocol in USD; {@code null} for non-DeFi. */
    private BigDecimal tvlUsd;

    /** Protocol revenue (last 30 days) in USD; {@code null} if not applicable. */
    private BigDecimal protocol30dRevenueUsd;

    // ── Developer activity ────────────────────────────────────────────────────

    /** GitHub stars (or equivalent); {@code null} if not available. */
    private Integer githubStars;

    /** Number of GitHub commits in the last 30 days. */
    private Integer githubCommits30d;

    /** Number of open GitHub issues. */
    private Integer githubOpenIssues;

    // ── Analyst / community ───────────────────────────────────────────────────

    /** Sentiment label from the provider (e.g., "BULLISH", "BEARISH", "NEUTRAL"). */
    private String sentimentLabel;

    /** All-time high price in USD. */
    private BigDecimal athUsd;

    /** Date the all-time high was reached. */
    private LocalDate athDate;

    /** Percentage change from all-time high to current price (negative = below ATH). */
    private BigDecimal athChangePercent;
}
