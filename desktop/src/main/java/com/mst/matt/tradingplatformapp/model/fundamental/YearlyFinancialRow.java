package com.mst.matt.tradingplatformapp.model.fundamental;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/** One fiscal year of income-statement highlights. */
@Data
@Builder
public class YearlyFinancialRow {
    private String fiscalYear;
    private BigDecimal totalRevenue;
    private BigDecimal grossProfit;
    private BigDecimal operatingIncome;
    private BigDecimal netIncome;
    private BigDecimal ebitda;
    private String currency;
}
