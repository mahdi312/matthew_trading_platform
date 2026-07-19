package com.mst.matt.marketservice.watchlist.controller;

import com.mst.matt.marketservice.watchlist.dto.AddWatchlistRequest;
import com.mst.matt.marketservice.watchlist.dto.WatchlistItemDto;
import com.mst.matt.marketservice.watchlist.service.WatchlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for per-user watchlists.
 *
 * <h3>Base path: {@code /api/market/watchlist}</h3>
 *
 * <table border="1">
 *   <caption>Endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>GET</td><td>/api/market/watchlist</td>         <td>Current user's watchlist (seeds defaults on first access)</td></tr>
 *   <tr><td>POST</td><td>/api/market/watchlist</td>        <td>Add a symbol to the watchlist (idempotent)</td></tr>
 *   <tr><td>DELETE</td><td>/api/market/watchlist/{symbol}</td><td>Remove a symbol from the watchlist</td></tr>
 * </table>
 *
 * <p>The user id is resolved from the {@code X-User-Id} header injected by the
 * gateway's {@code GatewayJwtAuthFilter} — exactly the same pattern used by
 * {@code alert-service}'s {@code AlertController}.</p>
 */
@Tag(name = "Watchlist", description = "Per-user symbol watchlists")
@SecurityRequirement(name = "bearer-jwt")
@Slf4j
@RestController
@RequestMapping("/api/market/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;

    // ── GET /api/market/watchlist ─────────────────────────────────────────────

    /**
     * Returns the current user's watchlist.
     * If the user has no watchlist yet, seeds it from {@code WatchlistDefaults.CRYPTO_DEFAULTS}.
     *
     * @param userId    injected by the gateway via the {@code X-User-Id} header
     * @param assetClass optional — if supplied and the user has no watchlist yet,
     *                  seeds with the matching asset-class defaults
     */
    @Operation(summary = "Get the current user's watchlist")
    @GetMapping
    public ResponseEntity<List<WatchlistItemDto>> getWatchlist(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) String assetClass) {

        log.debug("GET /api/market/watchlist userId={} assetClass={}", userId, assetClass);
        List<WatchlistItemDto> items = watchlistService.getWatchlist(userId, assetClass).stream()
                .map(WatchlistItemDto::from)
                .toList();
        return ResponseEntity.ok(items);
    }

    // ── POST /api/market/watchlist ────────────────────────────────────────────

    /**
     * Adds a symbol to the watchlist.  Idempotent — re-adding an existing symbol
     * returns the stored entry with {@code 200 OK} instead of {@code 201 Created}.
     */
    @Operation(summary = "Add a symbol to the watchlist")
    @PostMapping
    public ResponseEntity<WatchlistItemDto> addSymbol(
            @RequestHeader("X-User-Id") Long userId,
            @RequestBody AddWatchlistRequest request) {

        if (request.getSymbol() == null || request.getSymbol().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        log.debug("POST /api/market/watchlist userId={} symbol={}", userId, request.getSymbol());
        WatchlistItemDto saved = WatchlistItemDto.from(
                watchlistService.addSymbol(userId, request.getSymbol(), request.getAssetClass())
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // ── DELETE /api/market/watchlist/{symbol} ──────────────────────────────────

    /**
     * Removes a symbol from the watchlist.
     * Returns {@code 204 No Content} even if the symbol was not present (idempotent).
     */
    @Operation(summary = "Remove a symbol from the watchlist")
    @DeleteMapping("/{symbol}")
    public ResponseEntity<Void> removeSymbol(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String symbol) {

        log.debug("DELETE /api/market/watchlist/{} userId={}", symbol, userId);
        watchlistService.removeSymbol(userId, symbol);
        return ResponseEntity.noContent().build();
    }
}
