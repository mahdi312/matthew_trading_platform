package com.mst.matt.tradingservice.controller;

import com.mst.matt.tradingservice.dto.*;
import com.mst.matt.tradingservice.model.Trade;
import com.mst.matt.tradingservice.service.BrokerImportService;
import com.mst.matt.tradingservice.service.TradeService;
import com.mst.matt.tradingservice.service.TradingOrchestrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * REST controller exposing the trade journal and live-broker order endpoints.
 *
 * <p>This controller is <strong>new code</strong> — the desktop monolith had a
 * {@code TradeEntryController} which was a JavaFX view controller, not a REST
 * controller.  This replaces that UI-bound class with HTTP endpoints for the
 * same underlying operations.
 *
 * <h3>Base path: {@code /api/trades}</h3>
 *
 * <table border="1">
 *   <caption>Endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>POST</td>  <td>/api/trades</td>                   <td>Create a new trade (manual or live-broker)</td></tr>
 *   <tr><td>PUT</td>   <td>/api/trades/{id}</td>              <td>Update an existing trade</td></tr>
 *   <tr><td>DELETE</td><td>/api/trades/{id}</td>              <td>Delete a trade by ID</td></tr>
 *   <tr><td>POST</td>  <td>/api/trades/{id}/close</td>        <td>Close an open trade with an exit price</td></tr>
 *   <tr><td>GET</td>   <td>/api/trades</td>                   <td>List all trades for a user</td></tr>
 *   <tr><td>GET</td>   <td>/api/trades/{id}</td>              <td>Get a single trade by ID</td></tr>
 *   <tr><td>GET</td>   <td>/api/portfolio/stats</td>          <td>Portfolio-level statistics for a user</td></tr>
 *   <tr><td>POST</td>  <td>/api/trades/import</td>            <td>Upload a broker CSV for bulk import</td></tr>
 * </table>
 *
 * <h3>Authentication</h3>
 * <p>In production the {@code userId} would be extracted from the validated JWT
 * principal injected by the Gateway. For now it is accepted as a required
 * {@code ?userId=} query parameter or from the request body, flagged with a TODO.</p>
 */
@Tag(name = "Trades", description = "Trade journal entries, portfolio stats, and broker CSV import")
@SecurityRequirement(name = "bearer-jwt")
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TradeController {

    private final TradingOrchestrationService orchestrationService;
    private final TradeService                tradeService;
    private final BrokerImportService         brokerImportService;

    // ── POST /api/trades ──────────────────────────────────────────────────────

    /**
     * Create a new trade — manual journal entry or live-broker order.
     *
     * <p>The {@code source} field in the request body controls the path:
     * <ul>
     *   <li>{@code MANUAL} — saved to the DB only; no broker API call.</li>
     *   <li>{@code BROKER_LIVE} — order placed on the live broker (e.g., BitUnix)
     *       then a pending journal entry is created.</li>
     * </ul>
     *
     * <p>TODO: extract {@code userId} from JWT principal (Spring Security) instead
     * of reading it from the request body.
     */

    @Operation(summary = "Create a new trade (manual or live-broker)")
    @PostMapping("/trades")
    public ResponseEntity<TradeResponse> createTrade(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TradeRequest request) {
        log.debug("POST /api/trades userId={} symbol={} source={} idempotencyKey={}",
                request.getUserId(), request.getSymbol(), request.getSource(), idempotencyKey);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            java.util.Optional<Trade> existing = tradeService.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("Idempotent replay — returning existing trade id={} for key={}",
                        existing.get().getId(), idempotencyKey);
                return ResponseEntity.ok(TradeResponse.from(existing.get())); // 200, not 201 — not newly created
            }
        }

        Trade trade = orchestrationService.placeTrade(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(TradeResponse.from(trade));
    }

    // ── PUT /api/trades/{id} ──────────────────────────────────────────────────

    /**
     * Update an existing trade (e.g., edit notes, strategy, stop-loss).
     *
     * <p>Only modifies the database record; does <em>not</em> update an open
     * order on the live broker.  Changing {@code entryPrice} or {@code quantity}
     * on a BROKER_LIVE trade is a journal correction, not a broker order modification.</p>
     */
    @Operation(summary = "Update an existing trade")
    @PutMapping("/trades/{id}")
    public ResponseEntity<TradeResponse> updateTrade(
            @PathVariable Long id,
            @Valid @RequestBody TradeRequest request) {
        log.debug("PUT /api/trades/{} userId={}", id, request.getUserId());
        Trade existing = tradeService.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Trade not found: " + id));

        // Merge editable fields
        existing.setSymbol(request.getSymbol());
        existing.setAssetName(request.getAssetName());
        existing.setAssetType(request.getAssetType());
        existing.setDirection(request.getDirection());
        existing.setEntryPrice(request.getEntryPrice());
        existing.setExitPrice(request.getExitPrice());
        existing.setQuantity(request.getQuantity());
        existing.setStopLoss(request.getStopLoss());
        existing.setTakeProfit(request.getTakeProfit());
        existing.setFee(request.getFee());
        existing.setEntryTime(request.getEntryTime());
        existing.setExitTime(request.getExitTime());
        existing.setNotes(request.getNotes());
        existing.setExchange(request.getExchange());
        existing.setStrategy(request.getStrategy());
        existing.setScreenshotPath(request.getScreenshotPath());
        if (request.getExitPrice() != null) {
            existing.setStatus(Trade.TradeStatus.CLOSED);
        }

        Trade saved = tradeService.saveTrade(existing);
        return ResponseEntity.ok(TradeResponse.from(saved));
    }

    // ── DELETE /api/trades/{id} ───────────────────────────────────────────────

    /**
     * Delete a trade from the journal.
     *
     * <p>Does <em>not</em> cancel the live broker order; call {@code cancelOrder}
     * through a separate endpoint for that (future iteration).
     */
    @Operation(summary = "Delete a trade by ID")
    @DeleteMapping("/trades/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTrade(@PathVariable Long id) {
        log.debug("DELETE /api/trades/{}", id);
        tradeService.deleteTrade(id);
    }

    // ── POST /api/trades/{id}/close ───────────────────────────────────────────

    /**
     * Close an open trade with a given exit price.
     *
     * <p>Sets status to CLOSED, records exit time, and recomputes P&L.
     * Does not issue a corresponding close order to the live broker.
     */
    @Operation(summary = "Close an open trade with an exit price")
    @PostMapping("/trades/{id}/close")
    public ResponseEntity<TradeResponse> closeTrade(
            @PathVariable Long id,
            @Valid @RequestBody CloseTradeRequest closeRequest) {
        log.debug("POST /api/trades/{}/close exitPrice={}", id, closeRequest.getExitPrice());
        Trade closed = tradeService.closeTrade(id, closeRequest.getExitPrice());
        return ResponseEntity.ok(TradeResponse.from(closed));
    }

    // ── GET /api/trades ───────────────────────────────────────────────────────

    /**
     * List all trades for a user.
     *
     * @param userId required query parameter — will be replaced by JWT principal
     * @param status optional filter: OPEN | CLOSED | CANCELLED
     */
    @Operation(summary = "List all trades for a user")
    @GetMapping("/trades")
    public ResponseEntity<List<TradeResponse>> listTrades(
            @RequestParam Long userId,
            @RequestParam(required = false) Trade.TradeStatus status) {
        log.debug("GET /api/trades userId={} status={}", userId, status);
        List<Trade> trades = status != null
                ? tradeService.getTradesForUser(userId).stream()
                        .filter(t -> t.getStatus() == status).toList()
                : tradeService.getTradesForUser(userId);
        return ResponseEntity.ok(trades.stream().map(TradeResponse::from).toList());
    }

    // ── GET /api/trades/{id} ──────────────────────────────────────────────────

    @Operation(summary = "Get a single trade by ID")
    @GetMapping("/trades/{id}")
    public ResponseEntity<TradeResponse> getTrade(@PathVariable Long id) {
        log.debug("GET /api/trades/{}", id);
        return tradeService.findById(id)
                .map(TradeResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── GET /api/portfolio/stats ──────────────────────────────────────────────

    /**
     * Returns portfolio-level statistics for a user.
     *
     * @param userId required query parameter — will be replaced by JWT principal
     */
    @Operation(summary = "Get portfolio-level statistics for a user")
    @GetMapping("/portfolio/stats")
    public ResponseEntity<PortfolioStatsResponse> getPortfolioStats(@RequestParam Long userId) {
        log.debug("GET /api/portfolio/stats userId={}", userId);
        TradeService.PortfolioStats stats = tradeService.getStats(userId);
        return ResponseEntity.ok(PortfolioStatsResponse.from(stats));
    }

    // ── POST /api/trades/import ───────────────────────────────────────────────

    /**
     * Upload a broker CSV file and bulk-import all parseable trades.
     *
     * <p>Auto-detects the broker format from the CSV header row (Binance, Bybit,
     * eToro, MT4/MT5, Interactive Brokers, Generic).  All successfully parsed
     * rows are persisted; failed rows are returned in the response for review.
     *
     * @param file   the multipart CSV file
     * @param userId the user to associate all imported trades with
     */
    @Operation(summary = "Import trades from a broker CSV file")
    @PostMapping(value = "/trades/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResultResponse> importTrades(
            @RequestPart("file") MultipartFile file,
            @RequestParam Long userId) throws IOException {
        log.info("POST /api/trades/import userId={} file={} size={}",
                userId, file.getOriginalFilename(), file.getSize());
        BrokerImportService.ImportResult result = brokerImportService.importCsv(file, userId);
        return ResponseEntity.ok(ImportResultResponse.from(result));
    }

    // ── Exception handlers ────────────────────────────────────────────────────

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    // ── Inner response DTO for import ─────────────────────────────────────────

    /**
     * Lightweight wrapper for the broker import result, expressed as a
     * JSON-serialisable object.
     */
    public record ImportResultResponse(
            String broker,
            int totalRows,
            int importedCount,
            int skippedCount,
            List<String> skippedRows,
            List<TradeResponse> importedTrades
    ) {
        static ImportResultResponse from(BrokerImportService.ImportResult result) {
            return new ImportResultResponse(
                    result.broker().label,
                    result.totalRows(),
                    result.trades().size(),
                    result.skippedRows().size(),
                    result.skippedRows(),
                    result.trades().stream().map(TradeResponse::from).toList()
            );
        }
    }
}
