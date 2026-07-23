package com.mst.matt.aiservice.service;

import com.mst.matt.aiservice.client.ReferenceDataClient;
import com.mst.matt.aiservice.model.AiLlmModel;
import com.mst.matt.aiservice.model.AiLlmModelRegistry;
import com.mst.matt.contracts.provider.dto.NewsArticleDto;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI News &amp; Insights service.
 *
 * <p>Fetches recent news via {@link ReferenceDataClient} (calls
 * {@code reference-data-service /api/reference/news}) and generates
 * AI-powered investment summaries using the configured LLM provider.
 *
 * <p>Priority:
 * <ol>
 *   <li>Fetch live news from reference-data-service (via Feign)</li>
 *   <li>Generate AI recommendation via the selected LLM (if its API key is set)</li>
 *   <li>Fall back to rule-based recommendation when no LLM key is configured</li>
 *   <li>Use sample news only when reference-data-service call fails</li>
 * </ol>
 *
 * <p>Results are cached per symbol + model with a 15-minute TTL to avoid
 * rate-limit pressure on LLM APIs.
 */
@Service
public class AiNewsService {

    private static final Logger log = LoggerFactory.getLogger(AiNewsService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final AiLlmModelRegistry modelRegistry;
    private final ReferenceDataClient refDataClient;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .readTimeout(java.time.Duration.ofSeconds(35))
            .build();

    public AiNewsService(AiLlmModelRegistry modelRegistry, ReferenceDataClient refDataClient) {
        this.modelRegistry  = modelRegistry;
        this.refDataClient  = refDataClient;
    }

    // ── In-memory cache ────────────────────────────────────────────────────────

    private final Map<String, CachedInsight> cache = new ConcurrentHashMap<>();

    // ── Domain objects ─────────────────────────────────────────────────────────

    /** A single news item normalised from {@link NewsArticleDto}. */
    public record NewsItem(
            String headline,
            String source,
            String sentiment,   // "BULLISH", "NEUTRAL", "BEARISH"
            String url
    ) {}

    /** The full AI insight response for a symbol or query. */
    public record AiInsight(
            String symbol,
            String query,
            List<NewsItem> news,
            String overallSentiment,
            String recommendation,
            String riskWarning,
            String modelLabel,
            boolean aiGenerated,
            String llmNotice,
            LocalDateTime generatedAt
    ) {}

    private record CachedInsight(AiInsight insight, LocalDateTime expiresAt) {
        boolean isValid() { return LocalDateTime.now().isBefore(expiresAt); }
    }

    private record LlmTextResult(String text, String error, boolean aiGenerated) {}
    private record LlmCallResult(String content, String error) {
        boolean success() { return content != null && !content.isBlank(); }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public AiInsight getInsight(String query) { return getInsight(query, null); }

    public AiInsight getInsight(String query, String modelId) {
        if (query == null || query.isBlank()) query = "MARKET";
        String normalizedQuery = query.trim().toUpperCase();
        AiLlmModel model = resolveModel(modelId).orElse(null);
        String cacheKey   = cacheKey(normalizedQuery, model);

        CachedInsight cached = cache.get(cacheKey);
        if (cached != null && cached.isValid()) {
            log.debug("AI news cache hit for '{}'", cacheKey);
            return cached.insight();
        }

        AiInsight insight = fetchInsight(normalizedQuery, model);
        cache.put(cacheKey, new CachedInsight(insight, LocalDateTime.now().plus(CACHE_TTL)));
        return insight;
    }

    public List<AiLlmModel> availableModels()  { return modelRegistry.allModels(); }
    public List<AiLlmModel> configuredModels() { return modelRegistry.configuredModels(); }
    public Optional<AiLlmModel> defaultModel() { return modelRegistry.defaultModel(); }

    public void invalidate(String query) {
        if (query == null) return;
        String prefix = query.trim().toUpperCase() + "::";
        cache.keySet().removeIf(k -> k.startsWith(prefix) || k.equals(query.trim().toUpperCase()));
    }

    public List<String> popularSymbols() {
        return List.of(
                "AAPL", "TSLA", "NVDA", "MSFT", "AMZN", "GOOGL", "META", "NFLX",
                "BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT",
                "EURUSD", "GBPUSD", "USDJPY",
                "AI sector", "Crypto market", "Tech sector", "Market sentiment"
        );
    }

    // ── Private implementation ─────────────────────────────────────────────────

    private Optional<AiLlmModel> resolveModel(String modelId) {
        if (modelId != null && !modelId.isBlank()) {
            return modelRegistry.findById(modelId)
                    .filter(modelRegistry::hasApiKey)
                    .map(modelRegistry::effectiveModel);
        }
        return modelRegistry.defaultModel();
    }

    private static String cacheKey(String query, AiLlmModel model) {
        String modelPart = model != null ? model.id() + ":" + model.modelId() : "rule-based";
        return query + "::" + modelPart;
    }

    private AiInsight fetchInsight(String query, AiLlmModel model) {
        // 1. Fetch live news via reference-data-service (Feign)
        List<NewsItem> news = new ArrayList<>(fetchNewsFromRefData(query));

        // 2. Fall back to sample news if nothing returned
        if (news.isEmpty()) {
            news.addAll(generateSampleNews(query));
        }

        // 3. Generate recommendation
        String sentiment = computeOverallSentiment(news);
        String recommendation;
        String risk;
        String modelLabel;
        boolean aiGenerated;
        String llmNotice = null;

        if (model != null && modelRegistry.hasApiKey(model)) {
            LlmTextResult recResult  = generateLlmRecommendation(model, query, news, sentiment);
            LlmTextResult riskResult = generateLlmRiskWarning(model, query, news);
            recommendation = recResult.text();
            risk           = riskResult.text();
            aiGenerated    = recResult.aiGenerated() && riskResult.aiGenerated();
            modelLabel     = aiGenerated ? model.label() : "Rule-based fallback";
            llmNotice      = firstNonBlank(recResult.error(), riskResult.error());
        } else {
            modelLabel  = "Rule-based (no AI key)";
            aiGenerated = false;
            log.debug("No LLM API key configured — using rule-based recommendation for {}", query);
            recommendation = generateRuleBasedRecommendation(query, sentiment, news, false);
            risk           = generateRuleBasedRiskWarning(query, news);
        }

        return new AiInsight(query, query, news, sentiment, recommendation, risk,
                modelLabel, aiGenerated, llmNotice, LocalDateTime.now());
    }

    // ── Feign-backed news fetch ────────────────────────────────────────────────

    private List<NewsItem> fetchNewsFromRefData(String symbol) {
        try {
            List<NewsArticleDto> articles = refDataClient.getNews(symbol, 10);
            if (articles == null || articles.isEmpty()) return List.of();

            List<NewsItem> items = new ArrayList<>();
            for (NewsArticleDto a : articles) {
                String headline = a.getTitle() != null ? a.getTitle()
                        : (a.getSummary() != null ? a.getSummary() : "");
                if (headline.isBlank()) continue;
                String source   = a.getSource() != null ? a.getSource() : "News";
                String url      = a.getUrl()    != null ? a.getUrl()    : "";
                // Prefer pre-computed sentiment label; fall back to heuristic
                String sentiment = normaliseSentimentLabel(a.getSentimentLabel(), headline);
                items.add(new NewsItem(headline, source, sentiment, url));
            }
            log.debug("Fetched {} news items from reference-data-service for {}", items.size(), symbol);
            return items;
        } catch (Exception e) {
            log.debug("reference-data-service news fetch failed for {}: {}", symbol, e.getMessage());
            return List.of();
        }
    }

    /** Normalise the provider sentiment label or fall back to keyword heuristic. */
    private String normaliseSentimentLabel(String label, String headline) {
        if (label != null) {
            String up = label.toUpperCase();
            if (up.contains("BULLISH") || up.contains("POSITIVE")) return "BULLISH";
            if (up.contains("BEARISH") || up.contains("NEGATIVE")) return "BEARISH";
        }
        return guessSentiment(headline);
    }

    // ── LLM calls ─────────────────────────────────────────────────────────────

    private LlmTextResult generateLlmRecommendation(AiLlmModel model, String query,
                                                     List<NewsItem> news, String sentiment) {
        try {
            StringBuilder ctx = new StringBuilder();
            for (NewsItem item : news) {
                ctx.append("- [").append(item.sentiment()).append("] ")
                   .append(item.headline()).append(" (").append(item.source()).append(")\n");
            }
            String prompt = "You are a professional trading analyst. Based on the following recent "
                    + "news about \"" + query + "\", provide a concise investment recommendation "
                    + "(3-4 sentences). Overall sentiment is " + sentiment + ".\n\nRecent news:\n"
                    + ctx + "\nProvide a practical recommendation including suggested action, "
                    + "key levels to watch, and important caveats. Be specific and professional.";

            LlmCallResult r = callLlm(model, prompt, 200);
            if (r.success()) return new LlmTextResult(r.content(), null, true);
            return new LlmTextResult(
                    generateRuleBasedRecommendation(query, sentiment, news, true), r.error(), false);
        } catch (Exception e) {
            log.warn("{} recommendation generation failed for {}: {}", model.providerName(), query, e.getMessage());
            return new LlmTextResult(
                    generateRuleBasedRecommendation(query, sentiment, news, true),
                    model.providerName() + " error: " + e.getMessage(), false);
        }
    }

    private LlmTextResult generateLlmRiskWarning(AiLlmModel model, String query, List<NewsItem> news) {
        try {
            StringBuilder ctx = new StringBuilder();
            for (NewsItem item : news) ctx.append("- ").append(item.headline()).append("\n");

            String prompt = "You are a risk analyst. Based on the following news about \""
                    + query + "\", identify the top 2-3 specific risk factors a trader should be "
                    + "aware of. Keep it concise (2-3 sentences total). Focus on actionable risks.\n\nNews:\n" + ctx;

            LlmCallResult r = callLlm(model, prompt, 120);
            if (r.success()) return new LlmTextResult(r.content(), null, true);
            return new LlmTextResult(generateRuleBasedRiskWarning(query, news), r.error(), false);
        } catch (Exception e) {
            log.warn("{} risk warning generation failed for {}: {}", model.providerName(), query, e.getMessage());
            return new LlmTextResult(
                    generateRuleBasedRiskWarning(query, news),
                    model.providerName() + " error: " + e.getMessage(), false);
        }
    }

    private LlmCallResult callLlm(AiLlmModel model, String userPrompt, int maxTokens) throws IOException {
        String apiKey = modelRegistry.resolveApiKey(model);
        if (apiKey.isBlank()) {
            return new LlmCallResult(null, "API key not configured for " + model.providerName());
        }

        String body = model.format() == AiLlmModel.ApiFormat.ANTHROPIC
                ? buildAnthropicRequest(model, userPrompt, maxTokens)
                : buildOpenAiCompatRequest(model, userPrompt, maxTokens);

        Request.Builder reqBuilder = new Request.Builder()
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body, JSON));

        if (model.format() == AiLlmModel.ApiFormat.ANTHROPIC) {
            reqBuilder.url(model.baseUrl() + "/messages")
                      .addHeader("x-api-key", apiKey)
                      .addHeader("anthropic-version", "2023-06-01");
        } else {
            reqBuilder.url(model.baseUrl() + "/chat/completions")
                      .addHeader("Authorization", "Bearer " + apiKey);
        }

        try (Response resp = httpClient.newCall(reqBuilder.build()).execute()) {
            String respBody = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                String error = parseApiError(model.providerName(), resp.code(), respBody);
                log.warn("{} API returned HTTP {}: {}", model.providerName(), resp.code(), error);
                return new LlmCallResult(null, error);
            }
            String content = model.format() == AiLlmModel.ApiFormat.ANTHROPIC
                    ? parseAnthropicResponse(respBody)
                    : parseOpenAiCompatResponse(respBody);
            if (content == null || content.isBlank()) {
                return new LlmCallResult(null, model.providerName() + " returned an empty response");
            }
            return new LlmCallResult(content, null);
        }
    }

    // ── Request / response builders ───────────────────────────────────────────

    private String buildOpenAiCompatRequest(AiLlmModel model, String userPrompt, int maxTokens) {
        JsonObject root = new JsonObject();
        root.addProperty("model",       model.modelId());
        root.addProperty("max_tokens",  maxTokens);
        root.addProperty("temperature", 0.7);
        JsonArray messages = new JsonArray();
        JsonObject msg = new JsonObject();
        msg.addProperty("role",    "user");
        msg.addProperty("content", userPrompt);
        messages.add(msg);
        root.add("messages", messages);
        return root.toString();
    }

    private String buildAnthropicRequest(AiLlmModel model, String userPrompt, int maxTokens) {
        JsonObject root = new JsonObject();
        root.addProperty("model",      model.modelId());
        root.addProperty("max_tokens", maxTokens);
        JsonArray messages = new JsonArray();
        JsonObject msg = new JsonObject();
        msg.addProperty("role",    "user");
        msg.addProperty("content", userPrompt);
        messages.add(msg);
        root.add("messages", messages);
        return root.toString();
    }

    private String parseOpenAiCompatResponse(String body) {
        try {
            JsonObject json    = JsonParser.parseString(body).getAsJsonObject();
            JsonArray  choices = json.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) return null;
            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (message == null) return null;
            return message.get("content").getAsString().trim();
        } catch (Exception e) {
            log.debug("OpenAI-compat response parse error: {}", e.getMessage());
            return null;
        }
    }

    private String parseAnthropicResponse(String body) {
        try {
            JsonObject json    = JsonParser.parseString(body).getAsJsonObject();
            JsonArray  content = json.getAsJsonArray("content");
            if (content == null || content.isEmpty()) return null;
            return content.get(0).getAsJsonObject().get("text").getAsString().trim();
        } catch (Exception e) {
            log.debug("Anthropic response parse error: {}", e.getMessage());
            return null;
        }
    }

    private String parseApiError(String provider, int statusCode, String body) {
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("error")) {
                var err = json.get("error");
                if (err.isJsonObject() && err.getAsJsonObject().has("message")) {
                    return provider + " HTTP " + statusCode + ": "
                            + err.getAsJsonObject().get("message").getAsString();
                }
                return provider + " HTTP " + statusCode + ": " + err.getAsString();
            }
            if (json.has("message")) {
                return provider + " HTTP " + statusCode + ": " + json.get("message").getAsString();
            }
        } catch (Exception ignored) {}
        String snippet = body != null && body.length() > 160 ? body.substring(0, 160) + "…" : body;
        return provider + " HTTP " + statusCode + (snippet != null ? ": " + snippet : "");
    }

    // ── Heuristic helpers ──────────────────────────────────────────────────────

    private String guessSentiment(String headline) {
        String lower = headline.toLowerCase();
        long bullish = java.util.Arrays.stream(new String[]{
                "surge", "rally", "gain", "beat", "upgrade", "buy", "bull",
                "strong", "record", "profit", "growth", "positive", "up", "rise", "soar"
        }).filter(lower::contains).count();
        long bearish = java.util.Arrays.stream(new String[]{
                "drop", "fall", "decline", "miss", "downgrade", "sell", "bear",
                "weak", "loss", "risk", "concern", "cut", "warn", "down", "crash"
        }).filter(lower::contains).count();
        if (bullish > bearish) return "BULLISH";
        if (bearish > bullish) return "BEARISH";
        return "NEUTRAL";
    }

    private String computeOverallSentiment(List<NewsItem> news) {
        long bull = news.stream().filter(n -> "BULLISH".equals(n.sentiment())).count();
        long bear = news.stream().filter(n -> "BEARISH".equals(n.sentiment())).count();
        if (bull > bear + 1) return "BULLISH";
        if (bear > bull + 1) return "BEARISH";
        return "NEUTRAL";
    }

    // ── Rule-based fallbacks ───────────────────────────────────────────────────

    private String generateRuleBasedRecommendation(String query, String sentiment,
                                                    List<NewsItem> news, boolean afterLlmFailure) {
        long bull = news.stream().filter(n -> "BULLISH".equals(n.sentiment())).count();
        long bear = news.stream().filter(n -> "BEARISH".equals(n.sentiment())).count();
        String suffix = afterLlmFailure ? ""
                : " (Configure an AI provider API key for AI-generated analysis.)";
        return switch (sentiment) {
            case "BULLISH" -> query + " is showing positive momentum with " + bull
                    + " bullish signal(s). Consider a long position with a defined stop-loss "
                    + "below recent support. Confirm with volume and technical trend direction "
                    + "before entering." + suffix;
            case "BEARISH" -> query + " faces " + bear + " bearish headwind(s). Exercise caution — "
                    + "consider waiting for a confirmed reversal or managing existing longs with "
                    + "tighter stops. Short positions may be viable for experienced traders." + suffix;
            default -> query + " sentiment is mixed. News flow is balanced between positive and "
                    + "negative catalysts. Consider waiting for a clearer directional signal "
                    + "before opening new positions. Monitor key support/resistance levels closely." + suffix;
        };
    }

    private String generateRuleBasedRiskWarning(String query, List<NewsItem> news) {
        List<String> risks = new ArrayList<>();
        news.forEach(item -> {
            String h = item.headline().toLowerCase();
            if (h.contains("regulat"))              risks.add("Regulatory developments may impact price.");
            if (h.contains("rate") || h.contains("fed")) risks.add("Macro/Fed policy risk present.");
            if (h.contains("supply chain"))         risks.add("Supply chain disruptions possible.");
            if (h.contains("competition") || h.contains("compet")) risks.add("Competitive pressure noted.");
        });
        risks.add("Past performance does not guarantee future results. Always use a stop-loss.");
        return risks.stream().distinct().limit(3).reduce((a, b) -> a + " " + b)
                .orElse("Standard market risks apply.");
    }

    /** Generates realistic sample news when reference-data-service returns nothing. */
    List<NewsItem> generateSampleNews(String query) {
        boolean isCrypto = query.endsWith("USDT") || query.equals("BTC")
                || query.equals("ETH") || query.equals("SOL") || query.equals("BNB");
        boolean isMarket = query.contains("MARKET") || query.contains("SECTOR")
                || query.contains("SENTIMENT");

        if (isMarket) {
            return List.of(
                    new NewsItem("Fed signals potential rate pause — risk assets rally broadly",
                            "Reuters (sample)", "BULLISH", ""),
                    new NewsItem("Q2 earnings season shows resilient corporate profit margins",
                            "Bloomberg (sample)", "BULLISH", ""),
                    new NewsItem("Geopolitical tensions weigh on commodity prices",
                            "WSJ (sample)", "BEARISH", ""),
                    new NewsItem("Tech sector leads gains as AI spending forecasts rise",
                            "CNBC (sample)", "BULLISH", ""),
                    new NewsItem("Consumer confidence index slightly below expectations",
                            "MarketWatch (sample)", "NEUTRAL", "")
            );
        }
        if (isCrypto) {
            return List.of(
                    new NewsItem(query + " breaks above key resistance — analysts eye next target",
                            "CoinDesk (sample)", "BULLISH", ""),
                    new NewsItem("Institutional inflows into crypto ETFs hit monthly high",
                            "Bloomberg (sample)", "BULLISH", ""),
                    new NewsItem("Regulatory clarity in EU boosts crypto market confidence",
                            "Reuters (sample)", "BULLISH", ""),
                    new NewsItem("On-chain data shows long-term holder accumulation phase",
                            "Glassnode (sample)", "BULLISH", ""),
                    new NewsItem("Macro headwinds from strong USD may cap crypto upside",
                            "CryptoSlate (sample)", "NEUTRAL", "")
            );
        }
        return List.of(
                new NewsItem(query + " reports Q2 earnings beat — EPS above consensus",
                        "Reuters (sample)", "BULLISH", ""),
                new NewsItem("Analysts raise price target for " + query + " citing AI tailwinds",
                        "Bloomberg (sample)", "BULLISH", ""),
                new NewsItem(query + " management affirms full-year guidance",
                        "WSJ (sample)", "NEUTRAL", ""),
                new NewsItem("Supply chain improvements benefit " + query + "'s margins",
                        "CNBC (sample)", "BULLISH", ""),
                new NewsItem("Sector rotation may create short-term pressure on " + query,
                        "MarketWatch (sample)", "NEUTRAL", "")
        );
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }
}
