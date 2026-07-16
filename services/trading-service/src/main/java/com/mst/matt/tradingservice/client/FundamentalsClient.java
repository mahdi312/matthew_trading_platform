package com.mst.matt.tradingservice.client;

import com.mst.matt.tradingservice.dto.FundamentalsReportDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/**
 * Feign client for the {@code reference-data-service} FundamentalsProvider API.
 *
 * <p>Step 10 of the migration guide requires that any fundamentals data needed by
 * the yearly report (e.g. company financials context) is fetched from
 * reference-data-service over HTTP — not duplicated here.</p>
 *
 * <p>The service name {@code reference-data-service} resolves via Eureka.
 * The path {@code /api/fundamentals/{symbol}} is the endpoint exposed by
 * reference-data-service's FundamentalsController from Step 4.</p>
 */
@FeignClient(
        name  = "reference-data-service",
        url   = "${reference-data.service.url:}",   // override in non-Eureka environments
        fallback = FundamentalsClientFallback.class
)
public interface FundamentalsClient {

    /**
     * Fetch a full fundamentals report for the given symbol.
     *
     * @param symbol the stock ticker (e.g. "AAPL"); crypto/forex returns null from fallback
     * @param provider optional provider name override (e.g. "FINNHUB"); omit for AUTO
     */
    @GetMapping("/api/reference/fundamentals/{symbol}")
    FundamentalsReportDto getFundamentals(
            @PathVariable("symbol") String symbol,
            @RequestParam(value = "provider", required = false) String provider);
}
