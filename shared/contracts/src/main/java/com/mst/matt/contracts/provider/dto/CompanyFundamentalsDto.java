package com.mst.matt.contracts.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Normalized company financial fundamentals for equity / stock instruments.
 *
 * <p>Returned by {@link com.mst.matt.contracts.provider.fundamentals.FundamentalsProvider}
 * for {@link com.mst.matt.contracts.enums.AssetClass#STOCK}.
 * No consumer should ever see a provider-specific shape.</p>
 *
 * <p>Fields are nullable when not available from the underlying provider —
 * consumers must handle null gracefully.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyFundamentalsDto {

    // ── Identity ──────────────────────────────────────────────────────────────

    /** Canonical platform ticker symbol (e.g., "AAPL"). */
    private String symbol;

    /** Full company name (e.g., "Apple Inc."). */
    private String companyName;

    /** Exchange the stock is listed on (e.g., "NASDAQ", "NYSE"). */
    private String exchange;

    /** Primary sector (e.g., "Technology"). */
    private String sector;

    /** Sub-sector or industry (e.g., "Consumer Electronics"). */
    private String industry;

    /** Country of incorporation (ISO-3166 alpha-2, e.g., "US"). */
    private String country;

    /** ISO-4217 currency code for reported financials (e.g., "USD"). */
    private String currency;

    /** Name of the data provider that sourced this record. */
    private String providerName;

    /** Date this record was fetched / last updated. */
    private LocalDate fetchDate;

    // ── Valuation ─────────────────────────────────────────────────────────────

    /** Market capitalisation in {@link #currency}. */
    private BigDecimal marketCap;

    /** Enterprise value in {@link #currency}. */
    private BigDecimal enterpriseValue;

    /** Price-to-earnings ratio (trailing twelve months). */
    private BigDecimal peRatioTtm;

    /** Forward price-to-earnings ratio. */
    private BigDecimal peRatioForward;

    /** Price-to-book ratio. */
    private BigDecimal priceToBook;

    /** Price-to-sales ratio (trailing twelve months). */
    private BigDecimal priceToSalesTtm;

    /** EV/EBITDA ratio. */
    private BigDecimal evToEbitda;

    // ── Income statement highlights ───────────────────────────────────────────

    /** Annual revenue (most recent fiscal year) in {@link #currency}. */
    private BigDecimal revenueAnnual;

    /** Revenue growth rate year-over-year (e.g., 0.12 = 12%). */
    private BigDecimal revenueGrowthYoy;

    /** Gross profit margin (e.g., 0.43 = 43%). */
    private BigDecimal grossMargin;

    /** Operating income margin. */
    private BigDecimal operatingMargin;

    /** Net income margin (net profit margin). */
    private BigDecimal netMargin;

    /** Earnings per share (diluted, trailing twelve months). */
    private BigDecimal epsDilutedTtm;

    /** Earnings per share growth year-over-year. */
    private BigDecimal epsGrowthYoy;

    // ── Balance sheet highlights ──────────────────────────────────────────────

    /** Total assets in {@link #currency}. */
    private BigDecimal totalAssets;

    /** Total debt in {@link #currency}. */
    private BigDecimal totalDebt;

    /** Cash and short-term investments in {@link #currency}. */
    private BigDecimal cashAndEquivalents;

    /** Debt-to-equity ratio. */
    private BigDecimal debtToEquity;

    /** Current ratio (current assets / current liabilities). */
    private BigDecimal currentRatio;

    // ── Cash flow ─────────────────────────────────────────────────────────────

    /** Free cash flow (trailing twelve months) in {@link #currency}. */
    private BigDecimal freeCashFlowTtm;

    /** Free cash flow per share. */
    private BigDecimal freeCashFlowPerShare;

    // ── Dividends ─────────────────────────────────────────────────────────────

    /** Dividend yield (e.g., 0.015 = 1.5%); {@code null} if no dividend. */
    private BigDecimal dividendYield;

    /** Annual dividend per share; {@code null} if no dividend. */
    private BigDecimal dividendPerShare;

    /** Dividend payout ratio (dividends / net income). */
    private BigDecimal payoutRatio;

    // ── Analyst consensus ─────────────────────────────────────────────────────

    /** Number of analysts covering this stock. */
    private Integer analystCount;

    /** Consensus target price (average). */
    private BigDecimal targetPriceMean;

    /** Consensus recommendation (e.g., "BUY", "HOLD", "SELL"). */
    private String analystRecommendation;
}
