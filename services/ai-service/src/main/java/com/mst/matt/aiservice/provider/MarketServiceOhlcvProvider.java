package com.mst.matt.aiservice.provider;

import com.mst.matt.aiservice.client.MarketDataClient;
import com.mst.matt.aiservice.client.MarketDataClient.OhlcvBarResponse;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import com.mst.matt.contracts.provider.ohlcv.OhlcvDataProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@link OhlcvDataProvider} adapter that delegates to {@code market-service}
 * via the {@link MarketDataClient} Feign client (Gap 2).
 *
 * <p>This provider sits <strong>ahead</strong> of {@code NoOpOhlcvDataProvider}
 * in {@code AiAnalysisProviderConfig}'s {@code ohlcvRegistry}, so that every
 * AI analysis request backed by a symbol with live market data (crypto, stock,
 * forex) gets real OHLCV bars rather than an empty list.</p>
 *
 * <h3>Data flow</h3>
 * <pre>
 * AiController
 *   └─ ohlcvRegistry.executeWithFallback(assetClass, ...)
 *        └─ MarketServiceOhlcvProvider.getHistoricalBars(symbol, assetClass, interval, limit)
 *             └─ MarketDataClient.getOhlcv(symbol, interval, limit)    [Feign → market-service]
 *                  └─ market-service: storage hit → or → provider registry (Binance / CoinGecko / …)
 * </pre>
 *
 * <h3>No streaming support</h3>
 * <p>{@link #streamLiveBars} throws {@link UnsupportedOperationException};
 * callers should check {@link #supportsStreaming} first.</p>
 *
 * <h3>Cross-module isolation</h3>
 * <p>Responses are deserialised into the local {@link OhlcvBarResponse} record
 * (declared inside {@link MarketDataClient}) so this module has zero compile-time
 * dependency on market-service's JPA entities.</p>
 *
 * @see MarketDataClient
 * @see com.mst.matt.aiservice.config.AiAnalysisProviderConfig
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketServiceOhlcvProvider implements OhlcvDataProvider {

    /** Logical provider name registered in the {@code ohlcvRegistry}. */
    static final String PROVIDER_NAME = "MARKET_SERVICE";

    private final MarketDataClient marketDataClient;

    // ── OhlcvDataProvider contract ────────────────────────────────────────────

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    /**
     * Supports all {@link AssetClass} values: market-service's provider registry
     * already handles routing to the correct backend (Binance for CRYPTO,
     * AlphaVantage for STOCK, Frankfurter for FOREX, etc.).
     */
    @Override
    public List<AssetClass> supportedAssetClasses() {
        return Arrays.asList(AssetClass.values());
    }

    /**
     * Fetches historical OHLCV bars by delegating to market-service's
     * {@code GET /api/market/ohlcv/{symbol}} endpoint.
     *
     * <p>After Gap 1, market-service serves bars from its storage on a hit,
     * and from the full provider fallback chain on a miss — so this call will
     * always return the freshest available data.</p>
     *
     * @param symbol     canonical symbol (e.g. {@code "BTCUSDT"}, {@code "AAPL"})
     * @param assetClass asset class (used only for {@code supportedAssetClasses} routing;
     *                   market-service derives it independently from the symbol)
     * @param interval   candle timeframe (e.g. {@code "1h"}, {@code "1d"})
     * @param limit      max bars to return
     * @return list of {@link NormalizedOhlcvBar} oldest-first; empty on failure
     */
    @Override
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                       AssetClass assetClass,
                                                       String interval,
                                                       int limit) {
        try {
            List<OhlcvBarResponse> raw = marketDataClient.getOhlcv(symbol, interval, limit);
            if (raw == null || raw.isEmpty()) {
                log.debug("market-service returned empty OHLCV for {}/{}", symbol, interval);
                return List.of();
            }
            List<NormalizedOhlcvBar> bars = raw.stream()
                    .map(b -> toNormalized(b, assetClass, interval))
                    .toList();
            log.debug("market-service returned {} bars for {}/{}", bars.size(), symbol, interval);
            return bars;

        } catch (Exception e) {
            log.warn("MarketServiceOhlcvProvider: failed to fetch {}/{} — {}", symbol, interval, e.getMessage());
            return List.of();
        }
    }

    /**
     * Range-based overload — delegates to the limit-based overload with a
     * best-effort {@code limit} of 1000 (market-service will cap at its own
     * maximum).  Callers that need a precise range should post-filter the result.
     */
    @Override
    public List<NormalizedOhlcvBar> getHistoricalBars(String symbol,
                                                       AssetClass assetClass,
                                                       String interval,
                                                       Instant from,
                                                       Instant to) {
        // market-service's /api/market/ohlcv/{symbol} does not yet expose a
        // date-range parameter, so we fetch a wide batch and let the caller
        // post-filter by time if needed.
        return getHistoricalBars(symbol, assetClass, interval, 1000);
    }

    /**
     * Streaming is not supported — market-service WebSocket is a separate path.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public Stream<NormalizedOhlcvBar> streamLiveBars(String symbol,
                                                      AssetClass assetClass,
                                                      String interval) {
        throw new UnsupportedOperationException(
                "MarketServiceOhlcvProvider does not support live streaming; "
                + "use market-service's WebSocket endpoint directly.");
    }

    /** Always returns {@code false} — streaming not implemented here. */
    @Override
    public boolean supportsStreaming(AssetClass assetClass) {
        return false;
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    /**
     * Maps a {@link OhlcvBarResponse} record (from the Feign response) to a
     * {@link NormalizedOhlcvBar} contracts DTO.
     *
     * <p>{@code openTime} is a {@link java.time.LocalDateTime} in the JSON
     * (market-service's JPA entity stores it as a {@code LocalDateTime}).
     * We treat it as UTC when converting to {@link Instant}.</p>
     */
    private static NormalizedOhlcvBar toNormalized(OhlcvBarResponse b,
                                                    AssetClass assetClass,
                                                    String interval) {
        Instant openInstant = b.openTime() != null
                ? b.openTime().toInstant(ZoneOffset.UTC)
                : null;

        return NormalizedOhlcvBar.builder()
                .symbol(b.symbol())
                .assetClass(assetClass)
                .providerName(b.provider() != null ? b.provider() : PROVIDER_NAME)
                .openTime(openInstant)
                .closeTime(null)          // not in OhlcvBar entity; leave null
                .interval(interval)
                .open(b.open())
                .high(b.high())
                .low(b.low())
                .close(b.close())
                .volume(b.volume())
                .quoteVolume(null)
                .tradeCount(null)
                .isLive(false)
                .isSynthetic(false)
                .build();
    }
}
