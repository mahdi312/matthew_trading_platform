package com.mst.matt.contracts.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Normalized macroeconomic indicators relevant to forex (and broader markets).
 *
 * <p>Returned by {@link com.mst.matt.contracts.provider.fundamentals.FundamentalsProvider}
 * for {@link com.mst.matt.contracts.enums.AssetClass#FOREX}.
 * No consumer should ever see a provider-specific response shape.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ForexMacroIndicatorsDto {

    // ── Identity ──────────────────────────────────────────────────────────────

    /**
     * Currency pair in canonical format (e.g., "EUR/USD").
     * For country-level indicators, this may be the ISO-4217 currency code
     * (e.g., "USD" for US indicators).
     */
    private String symbol;

    /**
     * Base currency ISO code (e.g., "EUR" for EUR/USD).
     * {@code null} for country-level single-currency indicators.
     */
    private String baseCurrency;

    /**
     * Quote currency ISO code (e.g., "USD" for EUR/USD).
     */
    private String quoteCurrency;

    /** ISO-3166 alpha-2 country code associated with the base currency (e.g., "EU", "US"). */
    private String country;

    /** Name of the data provider that sourced this record. */
    private String providerName;

    /** Date this record was fetched / last updated. */
    private LocalDate fetchDate;

    // ── Interest rates ────────────────────────────────────────────────────────

    /** Central bank benchmark/policy interest rate (e.g., 0.045 = 4.5%). */
    private BigDecimal centralBankRate;

    /** Previous central bank rate (before the most recent change). */
    private BigDecimal previousCentralBankRate;

    /** Date of the last central bank rate decision. */
    private LocalDate lastRateDecisionDate;

    // ── Inflation ─────────────────────────────────────────────────────────────

    /** Consumer Price Index year-over-year change (e.g., 0.031 = 3.1%). */
    private BigDecimal cpiYoy;

    /** Core CPI (excluding food and energy) year-over-year change. */
    private BigDecimal coreCpiYoy;

    /** Producer Price Index year-over-year change. */
    private BigDecimal ppiYoy;

    // ── Growth ────────────────────────────────────────────────────────────────

    /** GDP growth rate quarter-over-quarter, annualised. */
    private BigDecimal gdpGrowthQoq;

    /** GDP growth rate year-over-year. */
    private BigDecimal gdpGrowthYoy;

    /** Unemployment rate (e.g., 0.038 = 3.8%). */
    private BigDecimal unemploymentRate;

    // ── Trade & current account ───────────────────────────────────────────────

    /** Trade balance (exports minus imports) in billions of USD. */
    private BigDecimal tradeBalanceBln;

    /** Current account balance as percentage of GDP. */
    private BigDecimal currentAccountPctGdp;

    // ── FX-specific ───────────────────────────────────────────────────────────

    /** Interest rate differential between base and quote currency central banks. */
    private BigDecimal interestRateDifferential;

    /** Current spot exchange rate. */
    private BigDecimal spotRate;

    /** 52-week high of the exchange rate. */
    private BigDecimal weekHigh52;

    /** 52-week low of the exchange rate. */
    private BigDecimal weekLow52;

    // ── Purchasing Power Parity ───────────────────────────────────────────────

    /** PPP-implied fair value of the currency pair. */
    private BigDecimal pppFairValue;

    /** Percentage over/under-valuation relative to PPP. */
    private BigDecimal pppDeviation;
}
