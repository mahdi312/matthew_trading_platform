package com.mst.matt.tradingplatformapp.model.fundamental;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
public class FundamentalsReport {
    private String symbol;
    private String companyName;
    private String sector;
    private String industry;
    private String country;
    private String providerUsed;
    private String assetTypeLabel;
    @Builder.Default
    private List<YearlyFinancialRow> yearlyRows = new ArrayList<>();
    @Builder.Default
    private List<String> earningsNotes = new ArrayList<>();
    private String summaryText;
}
