package com.mst.matt.aiservice.controller;

import com.mst.matt.aiservice.service.AiNewsService;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.ai.AiAnalysisProvider;
import com.mst.matt.contracts.provider.dto.*;
import com.mst.matt.contracts.provider.ohlcv.OhlcvDataProvider;
import com.mst.matt.contracts.provider.registry.ProviderRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for AI-powered market analysis.
 *
 * <p>All endpoints delegate to the {@link ProviderRegistry} which handles
 * provider ordering and Resilience4j circuit-breaker fallback.
 *
 * <ul>
 *   <li>{@code GET  /api/ai/summary/{symbol}}   — market narrative summary (auto-fetches OHLCV)</li>
 *   <li>{@code POST /api/ai/summary/{symbol}}   — market narrative summary (caller-supplied bars)</li>
 *   <li>{@code GET  /api/ai/signals/{symbol}}   — discrete trading signals (auto-fetches OHLCV)</li>
 *   <li>{@code POST /api/ai/signals/{symbol}}   — discrete trading signals (caller-supplied bars)</li>
 *   <li>{@code POST /api/ai/journal-critique}   — trade journal critique</li>
 *   <li>{@code GET  /api/ai/news/insight}       — AiNewsService insight (legacy compat)</li>
 *   <li>{@code GET  /api/ai/models}             — available LLM models</li>
 * </ul>
 *
 * <h3>OHLCV auto-fetch (Gap 2)</h3>
 * <p>The GET convenience endpoints for {@code summary} and {@code signals}
 * now inject real historical bars by calling through the {@code ohlcvRegistry}
 * before invoking the AI provider.  The registry's first entry,
 * {@link com.mst.matt.aiservice.provider.MarketServiceOhlcvProvider}, delegates
 * to market-service; {@code NoOpOhlcvDataProvider} is the final fallback.</p>
 */
@RestController
@Tag(name = "AI Analysis", description = "AI-powered market summaries, signals, and journal critique")
@RequestMapping("/api/ai")
public class AiController {

    /**
     * Default number of OHLCV bars to fetch for GET convenience endpoints.
     */
    private static final int DEFAULT_OHLCV_LIMIT = 200;

    private final ProviderRegistry<AiAnalysisProvider> aiRegistry;
    private final ProviderRegistry<OhlcvDataProvider> ohlcvRegistry;
    private final AiNewsService newsService;

    public AiController(ProviderRegistry<AiAnalysisProvider> aiRegistry,
                        ProviderRegistry<OhlcvDataProvider> ohlcvRegistry,
                        AiNewsService newsService) {
        this.aiRegistry = aiRegistry;
        this.ohlcvRegistry = ohlcvRegistry;
        this.newsService = newsService;
    }

    // ── GET /api/ai/summary/{symbol} ──────────────────────────────────────────

    /**
     * Generate a narrative market summary for a symbol.
     *
     * @param symbol     canonical symbol (e.g. "AAPL", "BTCUSDT")
     * @param assetClass optional asset class override; default CRYPTO
     * @param request    optional body with OHLCV bars and news articles
     */
    @Operation(summary = "Generate a market summary with caller-supplied OHLCV data")
    @PostMapping("/summary/{symbol}")
    public ResponseEntity<AiMarketSummaryDto> summarizeMarket(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "CRYPTO") String assetClass,
            @RequestBody(required = false) SummaryRequest request) {

        AssetClass ac = parseAssetClass(assetClass);
        List<NormalizedOhlcvBar> bars = request != null && request.bars() != null
                ? request.bars() : List.of();
        List<NewsArticleDto> news = request != null && request.news() != null
                ? request.news() : List.of();

        AiMarketSummaryDto result = aiRegistry.executeWithFallback(ac,
                provider -> provider.summarizeMarket(symbol.toUpperCase(), ac, bars, news));

        return ResponseEntity.ok(result);
    }

    /**
     * GET convenience: summary auto-fetching OHLCV bars from market-service
     * via the {@code ohlcvRegistry}.
     *
     * <p>Bars are fetched using the {@code "1d"} timeframe and a limit of
     * {@value DEFAULT_OHLCV_LIMIT} bars so the LLM has adequate context.
     * If market-service is unreachable the registry falls through to NoOp
     * and the analysis proceeds with an empty bar list (graceful degradation).</p>
     */
    @Operation(summary = "Generate a market summary (auto-fetches OHLCV)")
    @GetMapping("/summary/{symbol}")
    public ResponseEntity<AiMarketSummaryDto> summarizeMarketGet(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "CRYPTO") String assetClass) {

        AssetClass ac = parseAssetClass(assetClass);
        String sym = symbol.toUpperCase();

        List<NormalizedOhlcvBar> bars = fetchOhlcv(sym, ac);

        AiMarketSummaryDto result = aiRegistry.executeWithFallback(ac,
                provider -> provider.summarizeMarket(sym, ac, bars, List.of()));
        return ResponseEntity.ok(result);
    }

    // ── GET /api/ai/signals/{symbol} ──────────────────────────────────────────

    /**
     * Generate discrete trading signals for a symbol.
     *
     * @param symbol     canonical symbol
     * @param assetClass optional asset class override
     * @param request    optional body with OHLCV bars and news articles
     */
    @Operation(summary = "Generate trading signals with caller-supplied OHLCV data")
    @PostMapping("/signals/{symbol}")
    public ResponseEntity<List<AiSignalDto>> generateSignals(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "CRYPTO") String assetClass,
            @RequestBody(required = false) SummaryRequest request) {

        AssetClass ac = parseAssetClass(assetClass);
        List<NormalizedOhlcvBar> bars = request != null && request.bars() != null
                ? request.bars() : List.of();
        List<NewsArticleDto> news = request != null && request.news() != null
                ? request.news() : List.of();

        List<AiSignalDto> signals = aiRegistry.executeWithFallback(ac,
                provider -> provider.generateSignals(symbol.toUpperCase(), ac, bars, news));
        return ResponseEntity.ok(signals);
    }

    /**
     * GET convenience: signals auto-fetching OHLCV bars from market-service
     * via the {@code ohlcvRegistry}.
     *
     * <p>Same graceful-degradation behaviour as {@link #summarizeMarketGet}:
     * if market-service is unavailable the signals are generated with an
     * empty bar list (NoOp fallback).</p>
     */
    @Operation(summary = "Generate trading signals (auto-fetches OHLCV)")
    @GetMapping("/signals/{symbol}")
    public ResponseEntity<List<AiSignalDto>> generateSignalsGet(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "CRYPTO") String assetClass) {

        AssetClass ac = parseAssetClass(assetClass);
        String sym = symbol.toUpperCase();

        List<NormalizedOhlcvBar> bars = fetchOhlcv(sym, ac);

        List<AiSignalDto> signals = aiRegistry.executeWithFallback(ac,
                provider -> provider.generateSignals(sym, ac, bars, List.of()));
        return ResponseEntity.ok(signals);
    }

    // ── POST /api/ai/journal-critique ─────────────────────────────────────────

    /**
     * Generate an AI critique of a trade journal entry.
     *
     * @param request body containing tradeId, tradeContext (JSON/text), assetClass
     */
    @Operation(summary = "Generate an AI critique of a trade journal entry")
    @PostMapping("/journal-critique")
    public ResponseEntity<AiTradeJournalCritiqueDto> critiqueJournalEntry(
            @RequestBody CritiqueRequest request) {

        AssetClass ac = parseAssetClass(
                request.assetClass() != null ? request.assetClass() : "CRYPTO");

        AiTradeJournalCritiqueDto critique = aiRegistry.executeWithFallback(ac,
                provider -> provider.critiqueTradeJournalEntry(
                        request.tradeId(), request.tradeContext(), ac));
        return ResponseEntity.ok(critique);
    }

    // ── GET /api/ai/news/insight ──────────────────────────────────────────────

    /**
     * Returns an AI news insight for a symbol/query using the AiNewsService pipeline.
     *
     * @param query   symbol or natural-language query (e.g. "NVDA", "AI sector")
     * @param modelId optional registry model id to use (e.g. "groq:llama-3.3-70b")
     */
    @Operation(summary = "Get an AI news insight for a symbol or query")
    @GetMapping("/news/insight")
    public ResponseEntity<AiNewsService.AiInsight> getNewsInsight(
            @RequestParam(defaultValue = "MARKET") String query,
            @RequestParam(required = false) String modelId) {

        return ResponseEntity.ok(newsService.getInsight(query, modelId));
    }

    // ── GET /api/ai/models ────────────────────────────────────────────────────

    /**
     * Returns all known LLM models (configured status can be checked client-side).
     */
    @Operation(summary = "List all available LLM models")
    @GetMapping("/models")
    public ResponseEntity<?> listModels() {
        return ResponseEntity.ok(newsService.availableModels());
    }

    /**
     * Returns only LLM models that have an API key configured.
     */
    @Operation(summary = "List LLM models with configured API keys")
    @GetMapping("/models/configured")
    public ResponseEntity<?> listConfiguredModels() {
        return ResponseEntity.ok(newsService.configuredModels());
    }

    // ── Request / DTO records ─────────────────────────────────────────────────

    public record SummaryRequest(
            List<NormalizedOhlcvBar> bars,
            List<NewsArticleDto> news
    ) {
    }

    public record CritiqueRequest(
            String tradeId,
            String tradeContext,
            String assetClass
    ) {
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Fetches OHLCV bars for a symbol via the {@code ohlcvRegistry}.
     * Returns an empty list on any failure so analysis is never blocked.
     */
    private List<NormalizedOhlcvBar> fetchOhlcv(String symbol, AssetClass assetClass) {
        try {
            return ohlcvRegistry.executeWithFallback(assetClass,
                    provider -> provider.getHistoricalBars(
                            symbol, assetClass, "1d", DEFAULT_OHLCV_LIMIT));
        } catch (Exception e) {
            // Registry itself threw (e.g. all circuit breakers open) — degrade gracefully
            return List.of();
        }
    }

    private static AssetClass parseAssetClass(String s) {
        if (s == null || s.isBlank()) return AssetClass.CRYPTO;
        try {
            return AssetClass.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return AssetClass.CRYPTO;
        }
    }

}
