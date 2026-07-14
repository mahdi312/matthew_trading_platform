package com.mst.matt.aiservice.controller;

import com.mst.matt.aiservice.service.AiNewsService;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.AiMarketSummaryDto;
import com.mst.matt.contracts.provider.dto.AiSignalDto;
import com.mst.matt.contracts.provider.dto.AiTradeJournalCritiqueDto;
import com.mst.matt.contracts.provider.dto.NewsArticleDto;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import com.mst.matt.contracts.provider.registry.ProviderRegistry;
import com.mst.matt.contracts.provider.ai.AiAnalysisProvider;
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
 *   <li>{@code GET  /api/ai/summary/{symbol}}   — market narrative summary</li>
 *   <li>{@code GET  /api/ai/signals/{symbol}}   — discrete trading signals</li>
 *   <li>{@code POST /api/ai/journal-critique}   — trade journal critique</li>
 *   <li>{@code GET  /api/ai/news/insight}       — AiNewsService insight (legacy compat)</li>
 *   <li>{@code GET  /api/ai/models}             — available LLM models</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final ProviderRegistry<AiAnalysisProvider> aiRegistry;
    private final AiNewsService newsService;

    public AiController(ProviderRegistry<AiAnalysisProvider> aiRegistry,
                        AiNewsService newsService) {
        this.aiRegistry  = aiRegistry;
        this.newsService = newsService;
    }

    // ── GET /api/ai/summary/{symbol} ──────────────────────────────────────────

    /**
     * Generate a narrative market summary for a symbol.
     *
     * @param symbol     canonical symbol (e.g. "AAPL", "BTCUSDT")
     * @param assetClass optional asset class override; default CRYPTO
     * @param bars       recent OHLCV bars in request body (may be empty)
     * @param news       recent news articles in request body (may be empty)
     */
    @PostMapping("/summary/{symbol}")
    public ResponseEntity<AiMarketSummaryDto> summarizeMarket(
            @PathVariable                                   String              symbol,
            @RequestParam(defaultValue = "CRYPTO")         String              assetClass,
            @RequestBody(required = false)                  SummaryRequest      request) {

        AssetClass ac = parseAssetClass(assetClass);
        List<NormalizedOhlcvBar> bars = request != null && request.bars() != null
                ? request.bars() : List.of();
        List<NewsArticleDto> news = request != null && request.news() != null
                ? request.news() : List.of();

        AiMarketSummaryDto result = aiRegistry.executeWithFallback(ac,
                provider -> provider.summarizeMarket(symbol.toUpperCase(), ac, bars, news));

        return ResponseEntity.ok(result);
    }

    /** GET convenience: summary with no OHLCV or news context. */
    @GetMapping("/summary/{symbol}")
    public ResponseEntity<AiMarketSummaryDto> summarizeMarketGet(
            @PathVariable                          String symbol,
            @RequestParam(defaultValue = "CRYPTO") String assetClass) {

        AssetClass ac = parseAssetClass(assetClass);
        AiMarketSummaryDto result = aiRegistry.executeWithFallback(ac,
                provider -> provider.summarizeMarket(
                        symbol.toUpperCase(), ac, List.of(), List.of()));
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
    @PostMapping("/signals/{symbol}")
    public ResponseEntity<List<AiSignalDto>> generateSignals(
            @PathVariable                          String         symbol,
            @RequestParam(defaultValue = "CRYPTO") String         assetClass,
            @RequestBody(required = false)         SummaryRequest request) {

        AssetClass ac = parseAssetClass(assetClass);
        List<NormalizedOhlcvBar> bars = request != null && request.bars() != null
                ? request.bars() : List.of();
        List<NewsArticleDto> news = request != null && request.news() != null
                ? request.news() : List.of();

        List<AiSignalDto> signals = aiRegistry.executeWithFallback(ac,
                provider -> provider.generateSignals(symbol.toUpperCase(), ac, bars, news));
        return ResponseEntity.ok(signals);
    }

    /** GET convenience: signals with no context. */
    @GetMapping("/signals/{symbol}")
    public ResponseEntity<List<AiSignalDto>> generateSignalsGet(
            @PathVariable                          String symbol,
            @RequestParam(defaultValue = "CRYPTO") String assetClass) {

        AssetClass ac = parseAssetClass(assetClass);
        List<AiSignalDto> signals = aiRegistry.executeWithFallback(ac,
                provider -> provider.generateSignals(
                        symbol.toUpperCase(), ac, List.of(), List.of()));
        return ResponseEntity.ok(signals);
    }

    // ── POST /api/ai/journal-critique ─────────────────────────────────────────

    /**
     * Generate an AI critique of a trade journal entry.
     *
     * @param request body containing tradeId, tradeContext (JSON/text), assetClass
     */
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
    @GetMapping("/news/insight")
    public ResponseEntity<AiNewsService.AiInsight> getNewsInsight(
            @RequestParam(defaultValue = "MARKET") String query,
            @RequestParam(required = false)        String modelId) {

        return ResponseEntity.ok(newsService.getInsight(query, modelId));
    }

    // ── GET /api/ai/models ────────────────────────────────────────────────────

    /** Returns all known LLM models (configured status can be checked client-side). */
    @GetMapping("/models")
    public ResponseEntity<?> listModels() {
        return ResponseEntity.ok(newsService.availableModels());
    }

    /** Returns only LLM models that have an API key configured. */
    @GetMapping("/models/configured")
    public ResponseEntity<?> listConfiguredModels() {
        return ResponseEntity.ok(newsService.configuredModels());
    }

    // ── Request / DTO records ─────────────────────────────────────────────────

    public record SummaryRequest(
            List<NormalizedOhlcvBar> bars,
            List<NewsArticleDto>     news
    ) {}

    public record CritiqueRequest(
            String tradeId,
            String tradeContext,
            String assetClass
    ) {}

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static AssetClass parseAssetClass(String s) {
        if (s == null || s.isBlank()) return AssetClass.CRYPTO;
        try { return AssetClass.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return AssetClass.CRYPTO; }
    }
}
