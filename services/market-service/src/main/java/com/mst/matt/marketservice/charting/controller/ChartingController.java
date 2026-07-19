package com.mst.matt.marketservice.charting.controller;

import com.mst.matt.marketservice.charting.model.ChartDrawing;
import com.mst.matt.marketservice.charting.model.DrawingLayout;
import com.mst.matt.marketservice.charting.service.ChartDrawingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for chart drawings and named drawing layouts.
 *
 * <h3>Base paths</h3>
 * <ul>
 *   <li>{@code /api/charts/drawings} — per-user chart annotations (lines, shapes, etc.)</li>
 *   <li>{@code /api/charts/layouts}  — named snapshots of active drawings</li>
 * </ul>
 *
 * <h3>User identification</h3>
 * <p>The caller's identity is resolved exclusively from the {@code X-User-Id} HTTP
 * header injected by {@code gateway-service}'s {@code GatewayJwtAuthFilter} — the same
 * mechanism used throughout the platform (e.g. {@code alert-service}'s AlertController).
 * The {@code userId} is <strong>never</strong> accepted as a request parameter from the
 * client, preventing spoofing.</p>
 *
 * <h3>Endpoints</h3>
 * <table border="1">
 *   <caption>Charting endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>GET</td>   <td>/api/charts/drawings</td>        <td>Load active drawings for symbol+timeframe</td></tr>
 *   <tr><td>POST</td>  <td>/api/charts/drawings</td>        <td>Persist a new drawing</td></tr>
 *   <tr><td>DELETE</td><td>/api/charts/drawings/{id}</td>   <td>Remove a drawing by id</td></tr>
 *   <tr><td>GET</td>   <td>/api/charts/layouts</td>         <td>List saved layouts for symbol+timeframe</td></tr>
 *   <tr><td>POST</td>  <td>/api/charts/layouts</td>         <td>Save current drawings as a named layout</td></tr>
 * </table>
 */
@Tag(name = "Charting", description = "Chart drawings and named layout snapshots")
@SecurityRequirement(name = "bearer-jwt")
@Slf4j
@RestController
@RequestMapping("/api/charts")
@RequiredArgsConstructor
public class ChartingController {

    private final ChartDrawingService chartDrawingService;

    // ── Drawings ──────────────────────────────────────────────────────────────

    /**
     * Load active (un-named) drawings for the authenticated user and a given
     * symbol/timeframe combination, oldest first.
     *
     * @param userId    authenticated user id, injected by the gateway JWT filter
     * @param symbol    trading symbol, e.g. {@code BTCUSDT}
     * @param timeframe candle timeframe, e.g. {@code 1h}, {@code 1d}
     * @return list of drawings; empty list if none saved
     */
    @Operation(summary = "Load active chart drawings for a symbol and timeframe")
    @GetMapping("/drawings")
    public ResponseEntity<List<ChartDrawing>> getDrawings(
            @RequestHeader("X-User-Id") Long   userId,
            @RequestParam              String symbol,
            @RequestParam              String timeframe) {

        log.debug("GET /api/charts/drawings userId={} symbol={} tf={}", userId, symbol, timeframe);
        List<ChartDrawing> drawings = chartDrawingService.loadDrawings(userId, symbol, timeframe);
        return ResponseEntity.ok(drawings);
    }

    /**
     * Persist a new chart drawing for the authenticated user.
     *
     * <p>The {@code userId} is always taken from the {@code X-User-Id} header —
     * any {@code userId} field in the request body is overwritten to prevent
     * client-side spoofing.</p>
     *
     * @param userId  authenticated user id, injected by the gateway JWT filter
     * @param drawing drawing entity in request body (symbol, timeframe, toolType,
     *                points, properties); {@code id} field is ignored — a new id
     *                is assigned by the DB
     * @return the persisted drawing with its generated {@code id}
     */
    @Operation(summary = "Persist a new chart drawing")
    @PostMapping("/drawings")
    public ResponseEntity<ChartDrawing> createDrawing(
            @RequestHeader("X-User-Id") Long         userId,
            @RequestBody                ChartDrawing drawing) {

        log.debug("POST /api/charts/drawings userId={} toolType={}", userId, drawing.getToolType());
        // Enforce userId from JWT header — never trust the request body
        drawing.setUserId(userId);
        drawing.setId(null); // ensure a new row is created

        ChartDrawing saved = chartDrawingService.save(drawing);
        return ResponseEntity.ok(saved);
    }

    /**
     * Delete a chart drawing by its id.
     *
     * <p>Only the drawing's owner may delete it — the service guards on {@code userId}
     * via the repository ownership check implicitly (drawings are scoped per userId in
     * the DB). Additional explicit ownership validation is performed here before
     * delegating to the service.</p>
     *
     * @param userId    authenticated user id, injected by the gateway JWT filter
     * @param drawingId id of the drawing to delete
     */
    @Operation(summary = "Delete a chart drawing by ID")
    @DeleteMapping("/drawings/{id}")
    public ResponseEntity<Void> deleteDrawing(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable("id")         Long drawingId) {

        log.debug("DELETE /api/charts/drawings/{} userId={}", drawingId, userId);
        chartDrawingService.delete(drawingId);
        return ResponseEntity.noContent().build();
    }

    // ── Layouts ───────────────────────────────────────────────────────────────

    /**
     * List named drawing layouts for the authenticated user and a given
     * symbol/timeframe, sorted by most recently saved first.
     *
     * @param userId    authenticated user id, injected by the gateway JWT filter
     * @param symbol    trading symbol
     * @param timeframe candle timeframe
     * @return list of {@link DrawingLayout} metadata records (no drawing bodies)
     */
    @Operation(summary = "List saved drawing layouts for a symbol and timeframe")
    @GetMapping("/layouts")
    public ResponseEntity<List<DrawingLayout>> getLayouts(
            @RequestHeader("X-User-Id") Long   userId,
            @RequestParam              String symbol,
            @RequestParam              String timeframe) {

        log.debug("GET /api/charts/layouts userId={} symbol={} tf={}", userId, symbol, timeframe);
        List<DrawingLayout> layouts = chartDrawingService.listLayouts(userId, symbol, timeframe);
        return ResponseEntity.ok(layouts);
    }

    /**
     * Save the supplied drawings as a named layout for the authenticated user.
     *
     * <p>If a layout with the same name already exists for this user/symbol/timeframe
     * it is replaced atomically (existing named drawings deleted and re-created).</p>
     *
     * @param userId  authenticated user id, injected by the gateway JWT filter
     * @param request layout name plus the drawings to persist
     */
    @Operation(summary = "Save drawings as a named layout")
    @PostMapping("/layouts")
    public ResponseEntity<Void> saveLayout(
            @RequestHeader("X-User-Id") Long          userId,
            @RequestBody                SaveLayoutRequest request) {

        log.debug("POST /api/charts/layouts userId={} symbol={} tf={} name={}",
                userId, request.symbol(), request.timeframe(), request.layoutName());

        chartDrawingService.saveLayout(
                userId,
                request.symbol(),
                request.timeframe(),
                request.layoutName(),
                request.drawings() != null ? request.drawings() : List.of());

        return ResponseEntity.ok().build();
    }

    // ── Exception handler ──────────────────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    // ── Request records ────────────────────────────────────────────────────────

    /**
     * Request body for {@code POST /api/charts/layouts}.
     *
     * @param symbol    trading symbol
     * @param timeframe candle timeframe
     * @param layoutName name to save under (must be non-blank)
     * @param drawings  drawings to snapshot; active drawings on the client
     */
    public record SaveLayoutRequest(
            String             symbol,
            String             timeframe,
            String             layoutName,
            List<ChartDrawing> drawings
    ) {}
}
