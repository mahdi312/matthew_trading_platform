package com.mst.matt.aiservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Feign client for {@code market-service}'s historical OHLCV endpoint (Gap 2).
 *
 * <p>Resolves the service URL via Eureka using the logical name
 * {@code market-service}, or the hard-coded fallback URL from
 * {@code services.market.url} when Eureka is not running.</p>
 *
 * <p>Calls {@code GET /api/market/ohlcv/{symbol}}, which (after Gap 1) serves
 * data from storage on a hit and from the full provider fallback chain
 * (Binance → CoinGecko → … for crypto; AlphaVantage → … for stock) on a miss.</p>
 *
 * <p>The response body is deserialised into {@link OhlcvBarResponse} records — a
 * lightweight local mirror of market-service's {@code OhlcvBar} entity — so that
 * ai-service has no compile-time dependency on market-service's JPA classes.
 * The adapter {@code MarketServiceOhlcvProvider} maps these to
 * {@link com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar}.</p>
 *
 * @see com.mst.matt.aiservice.provider.MarketServiceOhlcvProvider
 */
@FeignClient(
        name = "market-service",
        url  = "${services.market.url:}",
        path = "/api/market"
)
public interface MarketDataClient {

    /**
     * Fetches historical OHLCV bars for a symbol.
     *
     * @param symbol    canonical symbol (e.g. {@code "BTCUSDT"}, {@code "AAPL"})
     * @param timeframe candle timeframe (e.g. {@code "1h"}, {@code "1d"}); default {@code "1d"}
     * @param limit     max bars to return; default 200
     * @return list of OHLCV bars, oldest-first; empty if none available
     */
    @GetMapping("/ohlcv/{symbol}")
    List<OhlcvBarResponse> getOhlcv(
            @PathVariable("symbol") String symbol,
            @RequestParam(value = "timeframe", defaultValue = "1d") String timeframe,
            @RequestParam(value = "limit",     defaultValue = "200") int limit
    );

    /**
     * Lightweight local mirror of market-service's {@code OhlcvBar} JPA entity.
     *
     * <p>Declared here to avoid a compile-time dependency on market-service's
     * JPA classes. Field names match the JSON keys in the REST response.</p>
     */
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
