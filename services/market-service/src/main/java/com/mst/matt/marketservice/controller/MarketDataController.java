package com.mst.matt.marketservice.controller;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import com.mst.matt.marketservice.model.AssetType;
import com.mst.matt.marketservice.model.OhlcvBar;
import com.mst.matt.marketservice.model.SymbolEntry;
import com.mst.matt.marketservice.service.AssetClassDetector;
import com.mst.matt.marketservice.service.OhlcvCacheService;
import com.mst.matt.marketservice.service.OhlcvStorageService;
import com.mst.matt.marketservice.service.SymbolSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * REST controller exposing historical/reference market data endpoints.
 *
 * <p>This is the <strong>historical/reference-data path</strong>, not the live-broker path.
 * Live streaming market data (BitUnix WebSocket) continues to flow through the existing
 * {@link com.mst.matt.marketservice.controller.MarketStreamController}.
 *
 * <h3>Base path: {@code /api/market}</h3>
 *
 * <table border="1">
 *   <caption>Endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>GET</td><td>/api/market/ohlcv/{symbol}</td>       <td>Fetch stored OHLCV bars; falls through to external provider on cache/DB miss</td></tr>
 *   <tr><td>GET</td><td>/api/market/symbols/search</td>       <td>Search symbol catalogue</td></tr>
 *   <tr><td>GET</td><td>/api/market/symbols/{symbol}</td>     <td>Lookup single symbol by name</td></tr>
 * </table>
 *
 * <h3>OHLCV read path (Gap 1)</h3>
 * <ol>
 *   <li>Check {@link OhlcvStorageService} for existing bars.</li>
 *   <li>If empty <em>or</em> stale, call {@link OhlcvCacheService#getCached} —
 *       which resolves the asset class via {@link AssetClassDetector} and
 *       delegates to the {@code MarketOhlcvProviderRegistry} fallback chain.</li>
 *   <li>Persist the fetched bars back to storage via
 *       {@link OhlcvStorageService#saveOrUpdateBars} so subsequent requests
 *       are served from the DB.</li>
 *   <li>Return the freshest available bars to the caller.</li>
 * </ol>
 */
@Slf4j
@Tag(name = "Market Data", description = "Historical OHLCV bars and symbol catalogue")
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketDataController {

    private final OhlcvStorageService ohlcvStorageService;
    private final OhlcvCacheService   ohlcvCacheService;
    private final SymbolSyncService   symbolSyncService;

    // ── GET /api/market/ohlcv/{symbol} ────────────────────────────────────────

    /**
     * Returns OHLCV bars for a symbol and timeframe.
     *
     * <p>Read path:
     * <ol>
     *   <li>Try storage first ({@link OhlcvStorageService#getBars}).</li>
     *   <li>On empty or stale result, fall through to {@link OhlcvCacheService#getCached},
     *       which resolves the correct {@link AssetClass} via
     *       {@link AssetClassDetector#detect} and delegates to the
     *       {@code MarketOhlcvProviderRegistry} fallback chain
     *       (Binance → CoinGecko … for crypto; AlphaVantage → … for stock; etc.).</li>
     *   <li>Persist freshly fetched bars back to {@link OhlcvStorageService} so
     *       the next request is served from storage (write-through).</li>
     * </ol>
     *
     * @param symbol    trading symbol, e.g. {@code BTCUSDT}
     * @param timeframe candle timeframe: 1m, 5m, 15m, 30m, 1h, 4h, 1d, 1w (default: 1h)
     * @param limit     max bars to return (default 200, max 1000)
     */
    @Operation(summary = "Fetch OHLCV bars for a symbol")
    @GetMapping("/ohlcv/{symbol}")
    public ResponseEntity<List<OhlcvBar>> getOhlcv(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1h") String timeframe,
            @RequestParam(defaultValue = "200") int limit) {

        String sym      = symbol.toUpperCase();
        int    safeLimit = Math.min(Math.max(limit, 1), 1000);

        log.debug("GET /api/market/ohlcv/{} tf={} limit={}", sym, timeframe, safeLimit);

        // 1. Try storage first
        List<OhlcvBar> stored = ohlcvStorageService.getBars(sym, timeframe, safeLimit);

        // 2. Fall through to provider if empty or stale
        if (stored.isEmpty() || ohlcvStorageService.isStale(stored, timeframe)) {
            log.debug("Storage miss or stale for {}/{} — fetching via provider registry", sym, timeframe);
            List<OhlcvBar> fresh = fetchAndPersist(sym, timeframe, safeLimit);
            if (!fresh.isEmpty()) {
                return ResponseEntity.ok(fresh);
            }
            // Still return whatever was in storage (may be stale-but-present)
            if (!stored.isEmpty()) return ResponseEntity.ok(stored);
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(stored);
    }

    // ── GET /api/market/symbols/search ────────────────────────────────────────

    /**
     * Search the symbol catalogue by name or ticker (case-insensitive).
     *
     * @param q     search query (minimum 1 character)
     * @param type  optional filter: CRYPTO | STOCK | FOREX
     */
    @Operation(summary = "Search the symbol catalogue")
    @GetMapping("/symbols/search")
    public ResponseEntity<List<SymbolEntry>> searchSymbols(
            @RequestParam String q,
            @RequestParam(required = false) SymbolEntry.AssetType type) {
        log.debug("GET /api/market/symbols/search q='{}' type={}", q, type);
        if (q == null || q.isBlank()) return ResponseEntity.badRequest().build();
        List<SymbolEntry> results = type != null
                ? symbolSyncService.searchByType(type, q)
                : symbolSyncService.search(q);
        return ResponseEntity.ok(results);
    }

    // ── GET /api/market/symbols/{symbol} ─────────────────────────────────────

    /**
     * Look up a single symbol entry by its exact ticker (case-insensitive).
     */
    @Operation(summary = "Look up a symbol by ticker")
    @GetMapping("/symbols/{symbol}")
    public ResponseEntity<List<SymbolEntry>> getSymbol(@PathVariable String symbol) {
        log.debug("GET /api/market/symbols/{}", symbol);
        List<SymbolEntry> results = symbolSyncService.search(symbol.toUpperCase());
        return results.isEmpty()
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(results);
    }

    // ── Exception handler ─────────────────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Fetches bars from the external provider registry via {@link OhlcvCacheService},
     * persists them to storage (write-through), and returns the mapped {@link OhlcvBar} list.
     *
     * <p>The asset class is resolved from the symbol string using
     * {@link AssetClassDetector#detect} so callers do not need to supply it.</p>
     */
    private List<OhlcvBar> fetchAndPersist(String symbol, String timeframe, int limit) {
        try {
            AssetClass assetClass = toContractsAssetClass(AssetClassDetector.detect(symbol));
            List<NormalizedOhlcvBar> normalized =
                    ohlcvCacheService.getCached(symbol, assetClass, timeframe, limit);

            if (normalized == null || normalized.isEmpty()) {
                log.debug("Provider registry returned empty bars for {}/{}", symbol, timeframe);
                return List.of();
            }

            // Map NormalizedOhlcvBar → OhlcvBar (market-service entity)
            AssetType assetType = toAssetType(assetClass);
            List<OhlcvBar> mapped = normalized.stream()
                    .map(n -> toOhlcvBar(n, symbol, timeframe, assetType))
                    .toList();

            // Write-through: persist to DB so next request is served from storage
            List<OhlcvBar> persisted = ohlcvStorageService.saveOrUpdateBars(symbol, timeframe, mapped);
            log.info("Fetched and persisted {} bars for {}/{} via provider registry",
                    persisted.size(), symbol, timeframe);
            return persisted;

        } catch (Exception e) {
            log.warn("Provider fetch failed for {}/{}: {} — serving from storage only",
                    symbol, timeframe, e.getMessage());
            return List.of();
        }
    }

    /** Maps the local {@link AssetClassDetector.AssetClass} to the contracts {@link AssetClass}. */
    private static AssetClass toContractsAssetClass(AssetClassDetector.AssetClass local) {
        return switch (local) {
            case CRYPTO    -> AssetClass.CRYPTO;
            case FOREX     -> AssetClass.FOREX;
            case STOCK,
                 COMMODITY,
                 INDEX      -> AssetClass.STOCK;
        };
    }

    /** Maps the contracts {@link AssetClass} to the local {@link AssetType} entity enum. */
    private static AssetType toAssetType(AssetClass ac) {
        return switch (ac) {
            case CRYPTO -> AssetType.CRYPTO;
            case FOREX  -> AssetType.FOREX;
            default     -> AssetType.STOCK;
        };
    }

    /** Maps a {@link NormalizedOhlcvBar} to an {@link OhlcvBar} JPA entity. */
    private static OhlcvBar toOhlcvBar(NormalizedOhlcvBar n, String symbol,
                                        String timeframe, AssetType assetType) {
        LocalDateTime openTime = n.getOpenTime() != null
                ? LocalDateTime.ofInstant(n.getOpenTime(), ZoneOffset.UTC)
                : LocalDateTime.now(ZoneOffset.UTC);

        return OhlcvBar.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .openTime(openTime)
                .open(nvl(n.getOpen()))
                .high(nvl(n.getHigh()))
                .low(nvl(n.getLow()))
                .close(nvl(n.getClose()))
                .volume(nvl(n.getVolume()))
                .assetType(assetType)
                .provider(n.getProviderName())
                .build();
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
