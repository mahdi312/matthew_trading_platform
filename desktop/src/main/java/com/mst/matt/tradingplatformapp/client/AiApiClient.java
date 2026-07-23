package com.mst.matt.tradingplatformapp.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Typed HTTP client for {@code ai-service} exposed through the Gateway at
 * {@code /api/ai/**}.
 */
@Slf4j
@Component
public class AiApiClient {

    private final WebClient webClient;

    public AiApiClient(WebClient gatewayWebClient) {
        this.webClient = gatewayWebClient;
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NormalizedOhlcvBar {
        private String     symbol;
        private String     assetClass;
        private String     providerName;
        private Instant    openTime;
        private Instant    closeTime;
        private String     interval;
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private BigDecimal volume;
        private BigDecimal quoteVolume;
        private Long       tradeCount;
        private boolean    isLive;
        private boolean    isSynthetic;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NewsArticle {
        private String       articleId;
        private String       providerName;
        private String       title;
        private String       summary;
        private String       url;
        private String       source;
        private Instant      publishedAt;
        private List<String> relatedSymbols;
        private Double       sentimentScore;
        private String       sentimentLabel;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SummaryRequest {
        private List<NormalizedOhlcvBar> bars;
        private List<NewsArticle>        news;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AiMarketSummary {
        private String         symbol;
        private String         assetClass;
        private String         providerName;
        private Instant        generatedAt;
        private String         modelVersion;
        private String         headline;
        private String         summary;
        private List<String>   keyPoints;
        private Double         sentimentScore;
        private String         sentimentLabel;
        private Double         confidence;
        private List<AiSignal> signals;
        private Integer        newsArticleCount;
        private Integer        ohlcvBarCount;
        private String         analysisPeriod;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AiSignal {
        private String     signalType;
        private String     direction;
        private Double     strength;
        private String     description;
        private String     rationale;
        private BigDecimal suggestedEntry;
        private BigDecimal targetPrice;
        private BigDecimal stopLoss;
        private String     timeHorizon;
        private Instant    generatedAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CritiqueRequest {
        private String tradeId;
        private String tradeContext;
        private String assetClass;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AiTradeJournalCritique {
        private String       tradeId;
        private String       providerName;
        private Instant      generatedAt;
        private Integer      overallScore;
        private String       overallAssessment;
        private Integer      entryTimingScore;
        private Integer      exitTimingScore;
        private Integer      riskManagementScore;
        private Integer      planAdherenceScore;
        private List<String> strengths;
        private List<String> improvements;
        private List<String> identifiedBiases;
        private boolean      marketContextIncluded;
        private String       marketContextAtEntry;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NewsItem {
        private String headline;
        private String source;
        private String sentiment;
        private String url;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AiInsight {
        private String           symbol;
        private String           query;
        private List<NewsItem>   news;
        private String           overallSentiment;
        private String           recommendation;
        private String           riskWarning;
        private String           modelLabel;
        private boolean          aiGenerated;
        private String           llmNotice;
        private LocalDateTime    generatedAt;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AiLlmModel {
        private String id;
        private String providerName;
        private String displayName;
        private String modelId;
        private String baseUrl;
        private String format;
        private String apiKeyProperty;
    }

    // ── Summary ───────────────────────────────────────────────────────────────

    /** {@code GET /api/ai/summary/{symbol}} */
    public Optional<AiMarketSummary> getSummary(String symbol, String assetClass) {
        try {
            AiMarketSummary summary = webClient.get()
                    .uri(u -> u.path("/api/ai/summary/{symbol}")
                            .queryParam("assetClass", assetClass != null ? assetClass : "CRYPTO")
                            .build(symbol))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Fetch AI summary failed: " + body))))
                    .bodyToMono(AiMarketSummary.class)
                    .block();
            return Optional.ofNullable(summary);
        } catch (Exception ex) {
            log.warn("getSummary({}) failed: {}", symbol, ex.getMessage());
            return Optional.empty();
        }
    }

    /** {@code POST /api/ai/summary/{symbol}} */
    public AiMarketSummary summarizeMarket(String symbol, String assetClass, SummaryRequest request) {
        try {
            AiMarketSummary summary = webClient.post()
                    .uri(u -> u.path("/api/ai/summary/{symbol}")
                            .queryParam("assetClass", assetClass != null ? assetClass : "CRYPTO")
                            .build(symbol))
                    .bodyValue(request != null ? request : new SummaryRequest())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("AI summary failed: " + body))))
                    .bodyToMono(AiMarketSummary.class)
                    .block();
            if (summary == null) throw new RuntimeException("Server returned empty response.");
            log.debug("AI summary generated for symbol={}", symbol);
            return summary;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Could not generate AI summary: " + ex.getMessage(), ex);
        }
    }

    // ── Signals ───────────────────────────────────────────────────────────────

    /** {@code GET /api/ai/signals/{symbol}} */
    public List<AiSignal> getSignals(String symbol, String assetClass) {
        try {
            List<AiSignal> signals = webClient.get()
                    .uri(u -> u.path("/api/ai/signals/{symbol}")
                            .queryParam("assetClass", assetClass != null ? assetClass : "CRYPTO")
                            .build(symbol))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Fetch AI signals failed: " + body))))
                    .bodyToMono(new ParameterizedTypeReference<List<AiSignal>>() {})
                    .block();
            return signals != null ? signals : List.of();
        } catch (Exception ex) {
            log.warn("getSignals({}) failed: {}", symbol, ex.getMessage());
            return List.of();
        }
    }

    /** {@code POST /api/ai/signals/{symbol}} */
    public List<AiSignal> generateSignals(String symbol, String assetClass, SummaryRequest request) {
        try {
            List<AiSignal> signals = webClient.post()
                    .uri(u -> u.path("/api/ai/signals/{symbol}")
                            .queryParam("assetClass", assetClass != null ? assetClass : "CRYPTO")
                            .build(symbol))
                    .bodyValue(request != null ? request : new SummaryRequest())
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Generate AI signals failed: " + body))))
                    .bodyToMono(new ParameterizedTypeReference<List<AiSignal>>() {})
                    .block();
            return signals != null ? signals : List.of();
        } catch (Exception ex) {
            throw new RuntimeException("Could not generate AI signals: " + ex.getMessage(), ex);
        }
    }

    // ── Journal critique ──────────────────────────────────────────────────────

    /** {@code POST /api/ai/journal-critique} */
    public AiTradeJournalCritique critiqueJournalEntry(CritiqueRequest request) {
        try {
            AiTradeJournalCritique critique = webClient.post()
                    .uri("/api/ai/journal-critique")
                    .bodyValue(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Journal critique failed: " + body))))
                    .bodyToMono(AiTradeJournalCritique.class)
                    .block();
            if (critique == null) throw new RuntimeException("Server returned empty response.");
            log.debug("Journal critique generated for tradeId={}", request.getTradeId());
            return critique;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Could not generate journal critique: " + ex.getMessage(), ex);
        }
    }

    // ── News insight ──────────────────────────────────────────────────────────

    /** {@code GET /api/ai/news/insight} */
    public Optional<AiInsight> getNewsInsight(String query, String modelId) {
        try {
            AiInsight insight = webClient.get()
                    .uri(u -> {
                        var builder = u.path("/api/ai/news/insight")
                                .queryParam("query", query != null ? query : "MARKET");
                        if (modelId != null && !modelId.isBlank()) {
                            builder.queryParam("modelId", modelId);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Fetch news insight failed: " + body))))
                    .bodyToMono(AiInsight.class)
                    .block();
            return Optional.ofNullable(insight);
        } catch (Exception ex) {
            log.warn("getNewsInsight('{}') failed: {}", query, ex.getMessage());
            return Optional.empty();
        }
    }

    // ── Models ────────────────────────────────────────────────────────────────

    /** {@code GET /api/ai/models} */
    public List<AiLlmModel> listModels() {
        try {
            List<AiLlmModel> models = webClient.get()
                    .uri("/api/ai/models")
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Fetch AI models failed: " + body))))
                    .bodyToMono(new ParameterizedTypeReference<List<AiLlmModel>>() {})
                    .block();
            return models != null ? models : List.of();
        } catch (Exception ex) {
            log.warn("listModels failed: {}", ex.getMessage());
            return List.of();
        }
    }
}
