package com.mst.matt.marketservice.charting.controller;

import com.mst.matt.marketservice.charting.model.IndicatorConfig;
import com.mst.matt.marketservice.charting.repository.IndicatorConfigRepository;
import com.mst.matt.marketservice.charting.service.IndicatorResult;
import com.mst.matt.marketservice.charting.service.IndicatorService;
import com.mst.matt.marketservice.model.OhlcvBar;
import com.mst.matt.marketservice.service.OhlcvStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ta4j.core.BarSeries;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for user-specific indicator configuration and on-demand
 * indicator computation.
 *
 * <h3>Base path: {@code /api/indicators}</h3>
 *
 * <table border="1">
 *   <caption>Indicator endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>GET</td> <td>/api/indicators/configs</td>             <td>Get the authenticated user's indicator config</td></tr>
 *   <tr><td>POST</td><td>/api/indicators/configs</td>             <td>Save the authenticated user's indicator config</td></tr>
 *   <tr><td>GET</td> <td>/api/indicators/{symbol}/compute</td>    <td>Compute indicators for a symbol</td></tr>
 * </table>
 *
 * <h3>User identification</h3>
 * <p>The caller's identity is resolved exclusively from the {@code X-User-Id} HTTP
 * header injected by {@code gateway-service}'s {@code GatewayJwtAuthFilter} — the same
 * mechanism used throughout the platform (e.g. {@code alert-service}'s AlertController
 * and {@link ChartingController}).
 * The {@code userId} is <strong>never</strong> accepted as a request parameter.</p>
 *
 * <h3>Compute path</h3>
 * <p>{@code GET /api/indicators/{symbol}/compute} fetches stored OHLCV bars for the
 * requested symbol/timeframe/limit from {@link OhlcvStorageService}, converts them
 * to a ta4j {@link BarSeries} via {@link IndicatorService#toBarSeries}, then calls
 * {@link IndicatorService#compute} with the user's saved {@link IndicatorConfig}
 * (falling back to the {@code SWING_TRADING} preset if no config has been saved).</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/indicators")
@RequiredArgsConstructor
public class IndicatorController {

    /** Default number of OHLCV bars to load for indicator computation. */
    private static final int DEFAULT_LIMIT    = 300;
    /** Default timeframe used when not specified by the caller. */
    private static final String DEFAULT_TF    = "1h";
    /** Fallback profile used when a user has no saved config. */
    private static final IndicatorConfig.IndicatorProfile DEFAULT_PROFILE =
            IndicatorConfig.IndicatorProfile.SWING_TRADING;

    private final IndicatorConfigRepository indicatorConfigRepository;
    private final IndicatorService          indicatorService;
    private final OhlcvStorageService       ohlcvStorageService;

    // ── GET /api/indicators/configs ───────────────────────────────────────────

    /**
     * Returns the authenticated user's indicator configuration.
     *
     * <p>If the user has never saved a config, a {@code SWING_TRADING} preset is
     * returned (without persisting it) so the client always receives a usable
     * response.</p>
     *
     * @param userId authenticated user id, injected by the gateway JWT filter
     * @return the user's {@link IndicatorConfig} or a preset default; never 404
     */
    @GetMapping("/configs")
    public ResponseEntity<IndicatorConfig> getConfig(
            @RequestHeader("X-User-Id") Long userId) {

        log.debug("GET /api/indicators/configs userId={}", userId);
        IndicatorConfig config = indicatorConfigRepository.findByUserId(userId)
                .orElseGet(() -> {
                    IndicatorConfig preset = IndicatorConfig.fromProfile(DEFAULT_PROFILE);
                    preset.setUserId(userId);
                    return preset;
                });
        return ResponseEntity.ok(config);
    }

    // ── POST /api/indicators/configs ──────────────────────────────────────────

    /**
     * Saves (upserts) the authenticated user's indicator configuration.
     *
     * <p>The {@code userId} is always taken from the {@code X-User-Id} header —
     * any {@code userId} field in the request body is overwritten to prevent
     * client-side spoofing. One config row per user (unique on {@code user_id}).</p>
     *
     * @param userId authenticated user id, injected by the gateway JWT filter
     * @param config indicator configuration body from the client
     * @return the persisted {@link IndicatorConfig} with its assigned {@code id}
     */
    @PostMapping("/configs")
    public ResponseEntity<IndicatorConfig> saveConfig(
            @RequestHeader("X-User-Id") Long            userId,
            @RequestBody                IndicatorConfig config) {

        log.debug("POST /api/indicators/configs userId={} profile={}", userId, config.getActiveProfile());

        // Enforce userId from JWT — never trust the request body
        config.setUserId(userId);

        // Upsert: if the user already has a row, carry over its DB id so JPA updates rather than inserts
        indicatorConfigRepository.findByUserId(userId)
                .ifPresent(existing -> config.setId(existing.getId()));

        IndicatorConfig saved = indicatorConfigRepository.save(config);
        return ResponseEntity.ok(saved);
    }

    // ── GET /api/indicators/{symbol}/compute ──────────────────────────────────

    /**
     * Computes all enabled technical indicators for a symbol using the authenticated
     * user's saved config (or the {@code SWING_TRADING} preset if none exists).
     *
     * <p>Steps:
     * <ol>
     *   <li>Load up to {@code limit} OHLCV bars from {@link OhlcvStorageService}.</li>
     *   <li>Convert bars to a ta4j {@link BarSeries} via
     *       {@link IndicatorService#toBarSeries}.</li>
     *   <li>Compute all enabled indicators via {@link IndicatorService#compute}.</li>
     * </ol>
     * Returns {@link IndicatorResult#empty()} if no bars are available.
     * </p>
     *
     * @param userId    authenticated user id, injected by the gateway JWT filter
     * @param symbol    trading symbol, e.g. {@code BTCUSDT}
     * @param timeframe candle timeframe, e.g. {@code 1h} (default: {@value DEFAULT_TF})
     * @param limit     max number of bars to load (default: {@value DEFAULT_LIMIT})
     * @return computed indicator values and series for all enabled indicators
     */
    @GetMapping("/{symbol}/compute")
    public ResponseEntity<IndicatorResult> computeIndicators(
            @RequestHeader("X-User-Id")           Long   userId,
            @PathVariable                          String symbol,
            @RequestParam(defaultValue = DEFAULT_TF)  String timeframe,
            @RequestParam(defaultValue = "300")    int    limit) {

        String sym      = symbol.toUpperCase();
        int safeLimit   = Math.min(Math.max(limit, 1), 1000);

        log.debug("GET /api/indicators/{}/compute userId={} tf={} limit={}", sym, userId, timeframe, safeLimit);

        // 1. Fetch stored bars
        List<OhlcvBar> bars = ohlcvStorageService.getBars(sym, timeframe, safeLimit);
        if (bars.isEmpty()) {
            log.debug("No OHLCV bars for {}/{} — returning empty IndicatorResult", sym, timeframe);
            return ResponseEntity.ok(IndicatorResult.empty());
        }

        // 2. Load user config (or preset)
        IndicatorConfig config = indicatorConfigRepository.findByUserId(userId)
                .orElseGet(() -> {
                    IndicatorConfig preset = IndicatorConfig.fromProfile(DEFAULT_PROFILE);
                    preset.setUserId(userId);
                    return preset;
                });

        // 3. Convert bars → BarSeries → compute
        BarSeries series = indicatorService.toBarSeries(bars, sym + "_" + timeframe);
        IndicatorResult result = indicatorService.compute(series, config);

        return ResponseEntity.ok(result);
    }

    // ── Exception handler ──────────────────────────────────────────────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }
}
