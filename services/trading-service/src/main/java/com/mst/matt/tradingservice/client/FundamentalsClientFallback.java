package com.mst.matt.tradingservice.client;

import com.mst.matt.tradingservice.dto.FundamentalsReportDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback for {@link FundamentalsClient} — returns {@code null} when
 * reference-data-service is unavailable.
 *
 * <p>The ReportingController and ExcelExportService both null-check the result
 * and skip the fundamentals sheet gracefully.</p>
 */
@Slf4j
@Component
public class FundamentalsClientFallback implements FundamentalsClient {

    @Override
    public FundamentalsReportDto getFundamentals(String symbol, String provider) {
        log.warn("FundamentalsClient fallback — reference-data-service unavailable for symbol={}", symbol);
        return null;
    }
}
