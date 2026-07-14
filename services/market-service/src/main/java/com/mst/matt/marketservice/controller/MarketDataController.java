package com.mst.matt.marketservice.controller;

import com.mst.matt.marketservice.model.OhlcvBar;
import com.mst.matt.marketservice.model.SymbolEntry;
import com.mst.matt.marketservice.service.OhlcvStorageService;
import com.mst.matt.marketservice.service.SymbolSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
 *   <tr><td>GET</td><td>/api/market/ohlcv/{symbol}</td>       <td>Fetch stored OHLCV bars</td></tr>
 *   <tr><td>GET</td><td>/api/market/symbols/search</td>       <td>Search symbol catalogue</td></tr>
 *   <tr><td>GET</td><td>/api/market/symbols/{symbol}</td>     <td>Lookup single symbol by name</td></tr>
 * </table>
 *
 * <p><strong>Note:</strong> Phase 2 does NOT implement actual third-party OHLCV provider
 * fetches (AlphaVantage, CoinGecko, etc.). These endpoints serve data that has already
 * been stored in the local database by BitUnixMarketDataProvider or import flows.
 */
@Slf4j
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketDataController {

    private final OhlcvStorageService ohlcvStorageService;
    private final SymbolSyncService   symbolSyncService;

    // ── GET /api/market/ohlcv/{symbol} ────────────────────────────────────────

    /**
     * Returns stored OHLCV bars for a symbol and timeframe.
     *
     * @param symbol    trading symbol, e.g. {@code BTCUSDT}
     * @param timeframe candle timeframe: 1m, 5m, 15m, 30m, 1h, 4h, 1d, 1w (default: 1h)
     * @param limit     max bars to return (default 200, max 1000)
     */
    @GetMapping("/ohlcv/{symbol}")
    public ResponseEntity<List<OhlcvBar>> getOhlcv(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1h") String timeframe,
            @RequestParam(defaultValue = "200") int limit) {
        log.debug("GET /api/market/ohlcv/{} tf={} limit={}", symbol, timeframe, limit);
        int safeLimit = Math.min(Math.max(limit, 1), 1000);
        List<OhlcvBar> bars = ohlcvStorageService.getBars(symbol.toUpperCase(), timeframe, safeLimit);
        if (bars.isEmpty()) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(bars);
    }

    // ── GET /api/market/symbols/search ────────────────────────────────────────

    /**
     * Search the symbol catalogue by name or ticker (case-insensitive).
     *
     * @param q     search query (minimum 1 character)
     * @param type  optional filter: CRYPTO | STOCK | FOREX
     */
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
}
