package com.mst.matt.tradingservice.dto;

import com.mst.matt.tradingservice.model.YearlyFinancialRow;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Fundamentals report DTO — returned by reference-data-service's
 * {@code FundamentalsProvider} API and consumed by the yearly-report endpoint.
 *
 * <p>This DTO mirrors the data returned from the external service call (over HTTP),
 * not a persisted entity.  Fields map to what the reference-data-service returns.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundamentalsReportDto {

    private String symbol;
    private String companyName;
    private String sector;
    private String industry;
    private String country;
    private String assetTypeLabel;
    private String summaryText;
    private String providerUsed;

    /** Yearly income-statement rows, newest first. */
    private List<YearlyFinancialRow> yearlyRows;

    /** Earnings calendar notes (upcoming earnings dates, estimates). */
    private List<String> earningsNotes;
}
