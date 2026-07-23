package com.mst.matt.alertservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Feign client for {@code market-service}'s historical OHLCV endpoint.
 * Used by {@code MarketServiceOhlcvProvider} so alert evaluation gets real prices.
 */
@FeignClient(
        name = "market-service",
        url  = "${services.market.url:}",
        path = "/api/market"
)
public interface MarketDataClient {

    @GetMapping("/ohlcv/{symbol}")
    List<OhlcvBarResponse> getOhlcv(
            @PathVariable("symbol") String symbol,
            @RequestParam(value = "timeframe", defaultValue = "1d") String timeframe,
            @RequestParam(value = "limit",     defaultValue = "200") int limit
    );

    record OhlcvBarResponse(
            Long          id,
            String        symbol,
            String        timeframe,
            LocalDateTime openTime,
            BigDecimal    open,
            BigDecimal    high,
            BigDecimal    low,
            BigDecimal    close,
            BigDecimal    volume,
            String        assetType,
            String        provider
    ) {}
}
