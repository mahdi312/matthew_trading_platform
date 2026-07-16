package com.mst.matt.tradingplatformapp.service.fundamental;

import com.mst.matt.tradingplatformapp.model.fundamental.FundamentalsReport;

import java.util.Optional;

public interface FundamentalService {

    FundamentalDataProvider getProviderId();

    boolean isEnabled();

    Optional<FundamentalsReport> fetchReport(String symbol);
}
