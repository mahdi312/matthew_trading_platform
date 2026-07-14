package com.mst.matt.tradingservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * One fiscal year of income-statement highlights for a stock/equity symbol.
 *
 * <p>Ported from {@code desktop/.../model/fundamental/YearlyFinancialRow.java}.
 * Re-homed in {@code trading-service} because it is consumed and rendered in the
 * trade/portfolio yearly-profit view — it is not a "fundamentals data" concept that
 * should live in reference-data-service.  Any fundamentals context required by
 * the yearly report is fetched from reference-data-service's FundamentalsProvider API
 * over HTTP (see {@link com.mst.matt.tradingservice.client.FundamentalsClient}).</p>
 *
 * <p>This is a plain DTO — no JPA {@code @Entity} annotation needed because the
 * fundamental data itself is fetched live from the external API and never persisted here.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YearlyFinancialRow {

    /** Fiscal year label, e.g. "2023", "FY2023", "TTM". */
    private String fiscalYear;

    private BigDecimal totalRevenue;

    private BigDecimal grossProfit;

    private BigDecimal operatingIncome;

    private BigDecimal netIncome;

    private BigDecimal ebitda;

    /** Currency code, e.g. "USD", "EUR". */
    private String currency;
}
