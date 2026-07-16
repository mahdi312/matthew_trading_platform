package com.mst.matt.contracts.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Normalized DeFi liquidity pool / DEX trading pair data.
 *
 * <p>Sourced from GeckoTerminal on-chain API ({@code /onchain/networks/{n}/pools})
 * and returned by {@code CoinGeckoDeFiProvider}.
 * No consumer should ever see a provider-specific JSONAPI response shape.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeFiPoolDto {

    // ── Identity ──────────────────────────────────────────────────────────────

    /** On-chain pool contract address (unique within a network). */
    private String poolAddress;

    /** Pool name (e.g., "WETH / USDC 0.05%"). */
    private String poolName;

    /** Network/chain identifier (e.g., "eth", "bsc", "polygon_pos"). */
    private String networkId;

    /** Human-readable network name (e.g., "Ethereum", "BNB Smart Chain"). */
    private String networkName;

    /** DEX identifier (e.g., "uniswap_v3", "pancakeswap_v2"). */
    private String dexId;

    /** DEX display name (e.g., "Uniswap V3"). */
    private String dexName;

    // ── Tokens ────────────────────────────────────────────────────────────────

    /** Base token symbol (e.g., "WETH"). */
    private String baseTokenSymbol;

    /** Quote token symbol (e.g., "USDC"). */
    private String quoteTokenSymbol;

    /** Base token contract address. */
    private String baseTokenAddress;

    /** Quote token contract address. */
    private String quoteTokenAddress;

    // ── Pricing ───────────────────────────────────────────────────────────────

    /** Current price of the base token denominated in USD. */
    private BigDecimal priceUsd;

    /** Price change percentage in the last 5 minutes. */
    private BigDecimal priceChangePercent5m;

    /** Price change percentage in the last hour. */
    private BigDecimal priceChangePercent1h;

    /** Price change percentage in the last 24 hours. */
    private BigDecimal priceChangePercent24h;

    // ── Volume & Liquidity ────────────────────────────────────────────────────

    /** 24-hour trading volume in USD. */
    private BigDecimal volume24hUsd;

    /** 6-hour trading volume in USD. */
    private BigDecimal volume6hUsd;

    /** 1-hour trading volume in USD. */
    private BigDecimal volume1hUsd;

    /** Total value locked (liquidity) in USD. */
    private BigDecimal liquidityUsd;

    /** Market capitalisation of the base token in USD (if available). */
    private BigDecimal marketCapUsd;

    // ── Activity ─────────────────────────────────────────────────────────────

    /** Total number of swaps/transactions in the last 24 hours. */
    private Long txCount24h;

    /** Number of buys in the last 24 hours. */
    private Long buys24h;

    /** Number of sells in the last 24 hours. */
    private Long sells24h;

    /** Pool creation timestamp on-chain; {@code null} if unavailable. */
    private Instant createdAt;

    // ── Provider metadata ─────────────────────────────────────────────────────

    /** Name of the data provider that sourced this record. */
    private String providerName;

    /** Timestamp this record was fetched. */
    private Instant fetchedAt;
}
