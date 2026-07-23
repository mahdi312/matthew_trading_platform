package com.mst.matt.aiservice.service;

import com.mst.matt.aiservice.model.AiLlmModel;
import com.mst.matt.aiservice.model.AiLlmModelRegistry;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.ai.AiAnalysisProvider;
import com.mst.matt.contracts.provider.dto.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Real {@link AiAnalysisProvider} implementation backed by an LLM.
 *
 * <p>Delegates news-insight generation to {@link AiNewsService} (which
 * fetches live news via Feign from {@code reference-data-service}) and
 * simplified technical signal scoring to {@link AiSignalService}.
 *
 * <p>When no LLM key is configured, every method returns a valid,
 * rule-based result — the service never throws.
 */
@Component("aiAnalysisProviderImpl")
public class AiAnalysisProviderImpl implements AiAnalysisProvider {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisProviderImpl.class);
    private static final String PROVIDER = "LLM_AI_PROVIDER";
    private static final MediaType JSON  = MediaType.get("application/json; charset=utf-8");

    private final AiLlmModelRegistry  modelRegistry;
    private final AiNewsService       newsService;
    private final AiSignalService     signalService;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .readTimeout(java.time.Duration.ofSeconds(45))
            .build();

    public AiAnalysisProviderImpl(AiLlmModelRegistry modelRegistry,
                                   AiNewsService newsService,
                                   AiSignalService signalService) {
        this.modelRegistry = modelRegistry;
        this.newsService   = newsService;
        this.signalService = signalService;
    }

    // ── DataProvider identity ─────────────────────────────────────────────────

    @Override public String providerName() { return PROVIDER; }

    @Override
    public List<AssetClass> supportedAssetClasses() {
        return List.of(AssetClass.values());
    }

    // ── summarizeMarket ───────────────────────────────────────────────────────

    @Override
    public AiMarketSummaryDto summarizeMarket(String symbol, AssetClass assetClass,
                                               List<NormalizedOhlcvBar> ohlcvBars,
                                               List<NewsArticleDto> newsArticles) {
        // Score signals first (pure arithmetic — always succeeds)
        AiSignalService.SignalResult signals =
                signalService.score(symbol, assetClass, ohlcvBars, newsArticles);

        // Attempt LLM narrative
        Optional<AiLlmModel> model = modelRegistry.defaultModel();
        String headline;
        String summary;
        String modelVersion = "rule-based";
        List<String> keyPoints;

        if (model.isPresent()) {
            try {
                String prompt = buildSummaryPrompt(symbol, assetClass, ohlcvBars, newsArticles, signals);
                String response = callLlmText(model.get(), prompt, 400);
                headline     = extractHeadline(response, symbol, signals.recommendation());
                summary      = response;
                modelVersion = model.get().modelId();
                keyPoints    = extractKeyPoints(newsArticles, signals);
            } catch (Exception e) {
                log.warn("LLM summarizeMarket failed for {}: {}", symbol, e.getMessage());
                headline  = ruleHeadline(symbol, signals.recommendation());
                summary   = ruleSummary(symbol, signals, newsArticles);
                keyPoints = extractKeyPoints(newsArticles, signals);
            }
        } else {
            headline  = ruleHeadline(symbol, signals.recommendation());
            summary   = ruleSummary(symbol, signals, newsArticles);
            keyPoints = extractKeyPoints(newsArticles, signals);
        }

        double sentimentScore = signals.compositeScore();
        return AiMarketSummaryDto.builder()
                .symbol(symbol)
                .assetClass(assetClass)
                .providerName(PROVIDER)
                .generatedAt(Instant.now())
                .modelVersion(modelVersion)
                .headline(headline)
                .summary(summary)
                .keyPoints(keyPoints)
                .sentimentScore(sentimentScore)
                .sentimentLabel(toSentimentLabel(sentimentScore))
                .confidence(signals.confidence() / 100.0)
                .signals(signals.signals())
                .newsArticleCount(newsArticles != null ? newsArticles.size() : 0)
                .ohlcvBarCount(ohlcvBars != null ? ohlcvBars.size() : 0)
                .analysisPeriod(derivePeriod(ohlcvBars))
                .build();
    }

    // ── scoreNewsSentiment ────────────────────────────────────────────────────

    @Override
    public List<NewsArticleDto> scoreNewsSentiment(String symbol, AssetClass assetClass,
                                                    List<NewsArticleDto> newsArticles) {
        if (newsArticles == null || newsArticles.isEmpty()) return List.of();

        Optional<AiLlmModel> model = modelRegistry.defaultModel();

        // If we have an LLM, ask it to score each article
        if (model.isPresent()) {
            try {
                return scoreSentimentWithLlm(model.get(), newsArticles);
            } catch (Exception e) {
                log.warn("LLM scoreNewsSentiment failed: {}", e.getMessage());
            }
        }
        // Fallback: keyword heuristic
        return newsArticles.stream().map(this::scoreHeuristic).toList();
    }

    // ── generateSignals ───────────────────────────────────────────────────────

    @Override
    public List<AiSignalDto> generateSignals(String symbol, AssetClass assetClass,
                                              List<NormalizedOhlcvBar> ohlcvBars,
                                              List<NewsArticleDto> newsArticles) {
        AiSignalService.SignalResult result =
                signalService.score(symbol, assetClass, ohlcvBars, newsArticles);
        return result.signals();
    }

    // ── critiqueTradeJournalEntry ─────────────────────────────────────────────

    @Override
    public AiTradeJournalCritiqueDto critiqueTradeJournalEntry(String tradeId,
                                                                String tradeContext,
                                                                AssetClass assetClass) {
        Optional<AiLlmModel> model = modelRegistry.defaultModel();

        if (model.isPresent()) {
            try {
                return critiqueWithLlm(model.get(), tradeId, tradeContext, assetClass);
            } catch (Exception e) {
                log.warn("LLM critiqueTradeJournalEntry failed for trade {}: {}", tradeId, e.getMessage());
            }
        }
        return ruleBasedCritique(tradeId, tradeContext, assetClass);
    }

    // ── Private LLM helpers ───────────────────────────────────────────────────

    private String callLlmText(AiLlmModel model, String prompt, int maxTokens) throws IOException {
        String apiKey = modelRegistry.resolveApiKey(model);
        if (apiKey.isBlank()) throw new IllegalStateException("API key missing for " + model.providerName());

        String body = buildOpenAiCompatRequest(model, prompt, maxTokens);
        Request.Builder rb = new Request.Builder()
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body, JSON));

        if (model.format() == AiLlmModel.ApiFormat.ANTHROPIC) {
            rb.url(model.baseUrl() + "/messages")
              .addHeader("x-api-key", apiKey)
              .addHeader("anthropic-version", "2023-06-01");
        } else {
            rb.url(model.baseUrl() + "/chat/completions")
              .addHeader("Authorization", "Bearer " + apiKey);
        }

        try (Response resp = httpClient.newCall(rb.build()).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) throw new IOException("HTTP " + resp.code() + ": " + respBody);
            return model.format() == AiLlmModel.ApiFormat.ANTHROPIC
                    ? parseAnthropicText(respBody)
                    : parseOpenAiText(respBody);
        }
    }

    private String buildOpenAiCompatRequest(AiLlmModel model, String prompt, int maxTokens) {
        JsonObject root = new JsonObject();
        root.addProperty("model", model.format() == AiLlmModel.ApiFormat.ANTHROPIC
                ? model.modelId() : model.modelId());
        root.addProperty("max_tokens", maxTokens);
        root.addProperty("temperature", 0.7);
        JsonArray messages = new JsonArray();
        JsonObject msg = new JsonObject();
        msg.addProperty("role", "user");
        msg.addProperty("content", prompt);
        messages.add(msg);
        root.add("messages", messages);
        return root.toString();
    }

    private String parseOpenAiText(String body) {
        try {
            var choices = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) return "";
            return choices.get(0).getAsJsonObject().getAsJsonObject("message")
                    .get("content").getAsString().trim();
        } catch (Exception e) { return ""; }
    }

    private String parseAnthropicText(String body) {
        try {
            var content = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("content");
            if (content == null || content.isEmpty()) return "";
            return content.get(0).getAsJsonObject().get("text").getAsString().trim();
        } catch (Exception e) { return ""; }
    }

    // ── Prompt builders ───────────────────────────────────────────────────────

    private String buildSummaryPrompt(String symbol, AssetClass assetClass,
                                       List<NormalizedOhlcvBar> bars,
                                       List<NewsArticleDto> news,
                                       AiSignalService.SignalResult signals) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a professional trading analyst. Provide a concise market summary (3-4 sentences) for ")
          .append(symbol).append(" (").append(assetClass).append(").\n\n");

        if (bars != null && !bars.isEmpty()) {
            NormalizedOhlcvBar last = bars.get(bars.size() - 1);
            sb.append("Recent price: ").append(last.getClose()).append("\n");
            sb.append("OHLCV bars provided: ").append(bars.size()).append("\n");
        }
        sb.append("Signal recommendation: ").append(signals.recommendation())
          .append(" (score: ").append(String.format("%.2f", signals.compositeScore())).append(")\n\n");

        if (news != null && !news.isEmpty()) {
            sb.append("Recent news:\n");
            news.stream().limit(5).forEach(a ->
                    sb.append("- ").append(a.getTitle() != null ? a.getTitle() : "").append("\n"));
        }
        sb.append("\nRespond with a single-line headline (max 120 chars) on the first line, "
                + "then a blank line, then a 3-4 sentence summary. Be specific and professional.");
        return sb.toString();
    }

    private String buildCritiquePrompt(String tradeContext, AssetClass assetClass) {
        return "You are an expert trading coach critiquing a trade journal entry.\n\n"
                + "Asset class: " + assetClass + "\n"
                + "Trade details:\n" + tradeContext + "\n\n"
                + "Respond with a JSON object with these keys: "
                + "overallScore (0-10), overallAssessment (string), "
                + "entryTimingScore (0-10), exitTimingScore (0-10), "
                + "riskManagementScore (0-10), planAdherenceScore (0-10), "
                + "strengths (array of strings), improvements (array of strings), "
                + "identifiedBiases (array of strings), marketContextAtEntry (string). "
                + "Be constructive, specific, and educational.";
    }

    // ── LLM critique parsing ──────────────────────────────────────────────────

    private AiTradeJournalCritiqueDto critiqueWithLlm(AiLlmModel model, String tradeId,
                                                       String tradeContext, AssetClass assetClass)
            throws IOException {
        String prompt = buildCritiquePrompt(tradeContext, assetClass);
        String raw    = callLlmText(model, prompt, 600);
        return parseCritiqueJson(raw, tradeId, model.label());
    }

    private AiTradeJournalCritiqueDto parseCritiqueJson(String raw, String tradeId, String modelLabel) {
        try {
            // Strip markdown code fences if present
            String json = raw.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("^```[a-zA-Z]*\\n?", "").replaceAll("```$", "").trim();
            }
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            List<String> strengths   = toStringList(obj, "strengths");
            List<String> improvements = toStringList(obj, "improvements");
            List<String> biases      = toStringList(obj, "identifiedBiases");

            return AiTradeJournalCritiqueDto.builder()
                    .tradeId(tradeId)
                    .providerName(PROVIDER + " (" + modelLabel + ")")
                    .generatedAt(Instant.now())
                    .overallScore(getInt(obj, "overallScore", 5))
                    .overallAssessment(getString(obj, "overallAssessment", ""))
                    .entryTimingScore(getInt(obj, "entryTimingScore", 5))
                    .exitTimingScore(getInt(obj, "exitTimingScore", 5))
                    .riskManagementScore(getInt(obj, "riskManagementScore", 5))
                    .planAdherenceScore(getInt(obj, "planAdherenceScore", 5))
                    .strengths(strengths)
                    .improvements(improvements)
                    .identifiedBiases(biases)
                    .marketContextIncluded(!tradeId.isBlank())
                    .marketContextAtEntry(getString(obj, "marketContextAtEntry", ""))
                    .build();
        } catch (Exception e) {
            log.warn("Critique JSON parse failed: {}", e.getMessage());
            return ruleBasedCritique(tradeId, raw, null);
        }
    }

    // ── LLM sentiment scoring ─────────────────────────────────────────────────

    private List<NewsArticleDto> scoreSentimentWithLlm(AiLlmModel model,
                                                        List<NewsArticleDto> articles) throws IOException {
        StringBuilder titles = new StringBuilder();
        for (int i = 0; i < articles.size(); i++) {
            titles.append(i).append(". ").append(articles.get(i).getTitle()).append("\n");
        }
        String prompt = "Rate the trading sentiment of each headline from -1.0 (very bearish) "
                + "to +1.0 (very bullish). Reply with only a JSON array of numbers in the same "
                + "order as the input, e.g. [0.5, -0.3, 0.0]. Headlines:\n" + titles;

        String raw = callLlmText(model, prompt, 200);
        try {
            JsonArray arr = JsonParser.parseString(raw.trim()).getAsJsonArray();
            List<NewsArticleDto> scored = new ArrayList<>();
            for (int i = 0; i < articles.size(); i++) {
                NewsArticleDto a = articles.get(i);
                double score = i < arr.size() ? arr.get(i).getAsDouble() : 0.0;
                scored.add(NewsArticleDto.builder()
                        .articleId(a.getArticleId())
                        .providerName(a.getProviderName())
                        .title(a.getTitle())
                        .summary(a.getSummary())
                        .url(a.getUrl())
                        .source(a.getSource())
                        .publishedAt(a.getPublishedAt())
                        .assetClasses(a.getAssetClasses())
                        .relatedSymbols(a.getRelatedSymbols())
                        .categories(a.getCategories())
                        .sentimentScore(score)
                        .sentimentLabel(toSentimentLabel(score))
                        .build());
            }
            return scored;
        } catch (Exception e) {
            log.debug("LLM sentiment array parse failed: {}", e.getMessage());
            return articles.stream().map(this::scoreHeuristic).toList();
        }
    }

    // ── Rule-based fallbacks ──────────────────────────────────────────────────

    private AiTradeJournalCritiqueDto ruleBasedCritique(String tradeId, String context,
                                                         AssetClass assetClass) {
        return AiTradeJournalCritiqueDto.builder()
                .tradeId(tradeId)
                .providerName(PROVIDER + " (rule-based)")
                .generatedAt(Instant.now())
                .overallScore(5)
                .overallAssessment("Unable to generate AI critique at this time. "
                        + "Please configure an LLM API key for detailed feedback. "
                        + "Review your trade for risk/reward ratio, entry timing, and plan adherence.")
                .entryTimingScore(5).exitTimingScore(5).riskManagementScore(5).planAdherenceScore(5)
                .strength("Trade was submitted for review — self-reflection is key to improvement.")
                .improvement("Configure an AI provider key for personalised, detailed feedback.")
                .marketContextIncluded(context != null && !context.isBlank())
                .build();
    }

    private NewsArticleDto scoreHeuristic(NewsArticleDto a) {
        String title = a.getTitle() != null ? a.getTitle().toLowerCase() : "";
        long bull = java.util.Arrays.stream(new String[]{
                "surge", "rally", "gain", "beat", "upgrade", "positive", "rise", "soar", "strong"
        }).filter(title::contains).count();
        long bear = java.util.Arrays.stream(new String[]{
                "drop", "fall", "decline", "miss", "downgrade", "negative", "crash", "warn", "weak"
        }).filter(title::contains).count();
        double score = bull > bear ? 0.4 : bear > bull ? -0.4 : 0.0;
        return NewsArticleDto.builder()
                .articleId(a.getArticleId()).providerName(a.getProviderName())
                .title(a.getTitle()).summary(a.getSummary()).url(a.getUrl())
                .source(a.getSource()).publishedAt(a.getPublishedAt())
                .assetClasses(a.getAssetClasses()).relatedSymbols(a.getRelatedSymbols())
                .categories(a.getCategories())
                .sentimentScore(score).sentimentLabel(toSentimentLabel(score))
                .build();
    }

    // ── Prose helpers ─────────────────────────────────────────────────────────

    private String ruleHeadline(String symbol, String recommendation) {
        return symbol + ": " + recommendation.replace('_', ' ')
               + " — rule-based analysis (configure LLM key for AI insights)";
    }

    private String ruleSummary(String symbol, AiSignalService.SignalResult signals,
                                List<NewsArticleDto> news) {
        String rec = signals.recommendation();
        int newsCount = news != null ? news.size() : 0;
        return String.format("%s shows a %s signal (composite score %.2f, confidence %.0f%%). "
                + "Technical analysis from %d OHLCV signals (%d bullish, %d bearish, %d neutral). "
                + "%d news article(s) included in sentiment analysis. "
                + "Configure an LLM API key in application.yml for AI-generated narrative.",
                symbol, rec.replace('_', ' '), signals.compositeScore(), signals.confidence(),
                signals.bullishCount() + signals.bearishCount() + signals.neutralCount(),
                signals.bullishCount(), signals.bearishCount(), signals.neutralCount(),
                newsCount);
    }

    private String extractHeadline(String llmResponse, String symbol, String recommendation) {
        if (llmResponse == null || llmResponse.isBlank()) return ruleHeadline(symbol, recommendation);
        String[] lines = llmResponse.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (!line.isBlank()) {
                return line.length() > 120 ? line.substring(0, 120) : line;
            }
        }
        return ruleHeadline(symbol, recommendation);
    }

    private List<String> extractKeyPoints(List<NewsArticleDto> news, AiSignalService.SignalResult signals) {
        List<String> pts = new ArrayList<>();
        pts.add("Signal recommendation: " + signals.recommendation()
                + " (confidence " + String.format("%.0f%%", signals.confidence()) + ")");
        pts.add("Bullish signals: " + signals.bullishCount()
                + ", Bearish: " + signals.bearishCount()
                + ", Neutral: " + signals.neutralCount());
        if (news != null && !news.isEmpty()) {
            pts.add(news.size() + " news article(s) factored into sentiment analysis");
        }
        if (signals.compositeScore() >= 0.2) {
            pts.add("Positive momentum detected — consider risk-defined long exposure");
        } else if (signals.compositeScore() <= -0.2) {
            pts.add("Negative momentum detected — caution advised; use tight stops");
        } else {
            pts.add("Mixed signals — wait for clearer directional confirmation");
        }
        return pts;
    }

    private String derivePeriod(List<NormalizedOhlcvBar> bars) {
        if (bars == null || bars.isEmpty()) return "N/A";
        if (bars.size() >= 200) return "200d";
        if (bars.size() >= 90)  return "90d";
        if (bars.size() >= 30)  return "30d";
        if (bars.size() >= 7)   return "7d";
        return bars.size() + " bars";
    }

    static String toSentimentLabel(double score) {
        if (score >= 0.6)  return "STRONGLY_BULLISH";
        if (score >= 0.2)  return "BULLISH";
        if (score <= -0.6) return "STRONGLY_BEARISH";
        if (score <= -0.2) return "BEARISH";
        return "NEUTRAL";
    }

    // ── JSON field helpers ────────────────────────────────────────────────────

    private static int getInt(JsonObject obj, String key, int def) {
        try { return obj.has(key) ? obj.get(key).getAsInt() : def; } catch (Exception e) { return def; }
    }

    private static String getString(JsonObject obj, String key, String def) {
        try { return obj.has(key) ? obj.get(key).getAsString() : def; } catch (Exception e) { return def; }
    }

    private static List<String> toStringList(JsonObject obj, String key) {
        List<String> list = new ArrayList<>();
        try {
            if (obj.has(key) && obj.get(key).isJsonArray()) {
                obj.getAsJsonArray(key).forEach(e -> list.add(e.getAsString()));
            }
        } catch (Exception ignored) {}
        return list;
    }
}
