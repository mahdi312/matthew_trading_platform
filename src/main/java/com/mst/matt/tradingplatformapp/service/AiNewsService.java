package com.mst.matt.tradingplatformapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI News & Insights service.
 *
 * <p>Aggregates recent news from Finnhub (if API key is configured) and
 * generates AI-powered investment summaries using OpenAI GPT (if key is
 * configured in application-local.properties).
 *
 * <p>Priority:
 * <ol>
 *   <li>Fetch live news from Finnhub (if {@code app.api.finnhub.key} is set)</li>
 *   <li>Generate AI recommendation via OpenAI (if {@code app.api.openai.key} is set)</li>
 *   <li>Fall back to rule-based recommendation when OpenAI is not configured</li>
 *   <li>Use sample news only when Finnhub is also not configured</li>
 * </ol>
 *
 * <p>Results are cached per symbol with a 15-minute TTL to avoid rate-limit pressure.
 */
@Service
public class AiNewsService {

    private static final Logger log = LoggerFactory.getLogger(AiNewsService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);

    // Keys read from application-local.properties (or application.properties as fallback)
    @Value("${app.api.finnhub.key:}")
    private String finnhubKey;

    @Value("${app.api.alphavantage.key:}")
    private String alphaVantageKey;

    @Value("${app.api.openai.key:}")
    private String openAiKey;

    @Value("${app.api.openai.model:gpt-4o-mini}")
    private String openAiModel;

    @Value("${app.api.openai.base-url:https://api.openai.com/v1}")
    private String openAiBaseUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // ── In-memory cache ────────────────────────────────────────────────────────

    private final Map<String, CachedInsight> cache = new ConcurrentHashMap<>();

    // ── Domain objects ─────────────────────────────────────────────────────────

    /** A single news headline with sentiment. */
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
            String overallSentiment,   // "BULLISH", "NEUTRAL", "BEARISH"
            String recommendation,
            String riskWarning,
            LocalDateTime generatedAt
    ) {}

    private record CachedInsight(AiInsight insight, LocalDateTime expiresAt) {
        boolean isValid() { return LocalDateTime.now().isBefore(expiresAt); }
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Fetches AI-generated news insight for the given symbol / query.
     * Results are cached for 15 minutes.
     *
     * @param query  symbol (e.g. "NVDA") or natural-language query
     * @return       insight, never null
     */
    public AiInsight getInsight(String query) {
        if (query == null || query.isBlank()) query = "MARKET";
        String key = query.trim().toUpperCase();

        CachedInsight cached = cache.get(key);
        if (cached != null && cached.isValid()) {
            log.debug("AI news cache hit for '{}'", key);
            return cached.insight();
        }

        AiInsight insight = fetchInsight(key);
        cache.put(key, new CachedInsight(insight, LocalDateTime.now().plus(CACHE_TTL)));
        return insight;
    }

    /** Clears the cache entry for a specific symbol/query. */
    public void invalidate(String query) {
        if (query != null) cache.remove(query.trim().toUpperCase());
    }

    /** Returns a list of popular symbols for the autocomplete dropdown. */
    public List<String> popularSymbols() {
        return List.of(
                "AAPL", "TSLA", "NVDA", "MSFT", "AMZN", "GOOGL", "META", "NFLX",
                "BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT",
                "EURUSD", "GBPUSD", "USDJPY",
                "AI sector", "Crypto market", "Tech sector", "Market sentiment"
        );
    }

    // ── Private implementation ─────────────────────────────────────────────────

    private AiInsight fetchInsight(String query) {
        boolean isSymbol = query.matches("[A-Z0-9]{2,12}");

        List<NewsItem> news = new ArrayList<>();

        // 1. Try Finnhub for live company news
        if (isSymbol && !finnhubKey.isBlank()) {
            news.addAll(fetchFinnhubNews(query));
            log.debug("Fetched {} news items from Finnhub for {}", news.size(), query);
        }

        // 2. If Finnhub returned nothing (or no key), use sample news as context
        boolean usedSampleNews = false;
        if (news.isEmpty()) {
            news.addAll(generateSampleNews(query));
            usedSampleNews = true;
        }

        // 3. Generate AI recommendation — real OpenAI if key is set, otherwise rule-based
        String sentiment = computeOverallSentiment(news);
        String recommendation;
        String risk;

        if (!openAiKey.isBlank()) {
            // Use OpenAI to generate a real AI recommendation
            recommendation = generateOpenAiRecommendation(query, news, sentiment);
            risk = generateOpenAiRiskWarning(query, news);
        } else {
            // Rule-based fallback (no OpenAI key configured)
            log.debug("OpenAI key not configured — using rule-based recommendation for {}", query);
            recommendation = generateRuleBasedRecommendation(query, sentiment, news);
            risk = generateRuleBasedRiskWarning(query, news);
        }

        return new AiInsight(query, query, news, sentiment, recommendation, risk,
                LocalDateTime.now());
    }

    // ── Finnhub news fetching ──────────────────────────────────────────────────

    private List<NewsItem> fetchFinnhubNews(String symbol) {
        try {
            String today = java.time.LocalDate.now().toString();
            String from  = java.time.LocalDate.now().minusDays(7).toString();
            String url   = "https://finnhub.io/api/v1/company-news?symbol=" + symbol
                    + "&from=" + from + "&to=" + today + "&token=" + finnhubKey;

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .GET().build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                return parseFinnhubResponse(resp.body(), symbol);
            }
            log.debug("Finnhub returned HTTP {} for {}", resp.statusCode(), symbol);
        } catch (Exception e) {
            log.debug("Finnhub news fetch failed for {}: {}", symbol, e.getMessage());
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<NewsItem> parseFinnhubResponse(String json, String symbol) {
        List<NewsItem> items = new ArrayList<>();
        try {
            com.google.gson.Gson gson = new com.google.gson.Gson();
            com.google.gson.reflect.TypeToken<List<Map<String, Object>>> type =
                    new com.google.gson.reflect.TypeToken<>() {};
            List<Map<String, Object>> arr = gson.fromJson(json, type.getType());
            if (arr == null) return items;

            for (int i = 0; i < Math.min(arr.size(), 8); i++) {
                Map<String, Object> item = arr.get(i);
                String headline = (String) item.getOrDefault("headline", "");
                String source   = (String) item.getOrDefault("source", "Finnhub");
                String url      = (String) item.getOrDefault("url", "");
                if (headline.isBlank()) continue;
                String sentiment = guessSentiment(headline);
                items.add(new NewsItem(headline, source, sentiment, url));
            }
        } catch (Exception e) {
            log.debug("Finnhub JSON parse error: {}", e.getMessage());
        }
        return items;
    }

    // ── OpenAI recommendation generation ──────────────────────────────────────

    /**
     * Calls OpenAI Chat Completions to generate a real AI investment recommendation
     * based on the collected news items.
     *
     * <p>The API key is read from {@code app.api.openai.key} in
     * {@code application-local.properties}.
     */
    private String generateOpenAiRecommendation(String query, List<NewsItem> news,
                                                  String sentiment) {
        try {
            StringBuilder newsContext = new StringBuilder();
            for (NewsItem item : news) {
                newsContext.append("- [").append(item.sentiment()).append("] ")
                        .append(item.headline()).append(" (").append(item.source()).append(")\n");
            }

            String prompt = "You are a professional trading analyst. Based on the following recent "
                    + "news about \"" + query + "\", provide a concise investment recommendation "
                    + "(3-4 sentences). Overall sentiment is " + sentiment + ".\n\n"
                    + "Recent news:\n" + newsContext
                    + "\nProvide a practical recommendation including suggested action, "
                    + "key levels to watch, and important caveats. Be specific and professional.";

            String requestBody = buildOpenAiRequest(prompt, 200);
            String response = callOpenAi(requestBody);
            if (response != null && !response.isBlank()) {
                return response;
            }
        } catch (Exception e) {
            log.warn("OpenAI recommendation generation failed for {}: {}", query, e.getMessage());
        }
        // Fallback to rule-based
        return generateRuleBasedRecommendation(query, sentiment, news);
    }

    /**
     * Calls OpenAI Chat Completions to generate a concise risk warning.
     */
    private String generateOpenAiRiskWarning(String query, List<NewsItem> news) {
        try {
            StringBuilder newsContext = new StringBuilder();
            for (NewsItem item : news) {
                newsContext.append("- ").append(item.headline()).append("\n");
            }

            String prompt = "You are a risk analyst. Based on the following news about \""
                    + query + "\", identify the top 2-3 specific risk factors a trader should be "
                    + "aware of. Keep it concise (2-3 sentences total). Focus on actionable risks.\n\n"
                    + "News:\n" + newsContext;

            String requestBody = buildOpenAiRequest(prompt, 120);
            String response = callOpenAi(requestBody);
            if (response != null && !response.isBlank()) {
                return response;
            }
        } catch (Exception e) {
            log.warn("OpenAI risk warning generation failed for {}: {}", query, e.getMessage());
        }
        return generateRuleBasedRiskWarning(query, news);
    }

    /**
     * Builds the JSON payload for the OpenAI Chat Completions API.
     */
    private String buildOpenAiRequest(String userPrompt, int maxTokens) {
        // Use Gson to safely build JSON without string interpolation issues
        com.google.gson.JsonObject root = new com.google.gson.JsonObject();
        root.addProperty("model", openAiModel);
        root.addProperty("max_tokens", maxTokens);
        root.addProperty("temperature", 0.7);

        com.google.gson.JsonArray messages = new com.google.gson.JsonArray();
        com.google.gson.JsonObject msg = new com.google.gson.JsonObject();
        msg.addProperty("role", "user");
        msg.addProperty("content", userPrompt);
        messages.add(msg);
        root.add("messages", messages);

        return new com.google.gson.Gson().toJson(root);
    }

    /**
     * Sends a request to the OpenAI Chat Completions endpoint and returns the
     * content of the first choice, or {@code null} on error.
     */
    private String callOpenAi(String requestBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(openAiBaseUrl + "/chat/completions"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + openAiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            log.warn("OpenAI API returned HTTP {}: {}", resp.statusCode(),
                    resp.body().length() > 200 ? resp.body().substring(0, 200) : resp.body());
            return null;
        }

        // Parse the response: choices[0].message.content
        com.google.gson.JsonObject json = com.google.gson.JsonParser
                .parseString(resp.body()).getAsJsonObject();
        var choices = json.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) return null;
        var message = choices.get(0).getAsJsonObject()
                .getAsJsonObject("message");
        if (message == null) return null;
        return message.get("content").getAsString().trim();
    }

    // ── Heuristic helpers ──────────────────────────────────────────────────────

    /** Heuristic sentiment from headline keywords. */
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

    // ── Rule-based fallbacks (used when OpenAI key is not configured) ──────────

    private String generateRuleBasedRecommendation(String query, String sentiment,
                                                    List<NewsItem> news) {
        long bull = news.stream().filter(n -> "BULLISH".equals(n.sentiment())).count();
        long bear = news.stream().filter(n -> "BEARISH".equals(n.sentiment())).count();
        return switch (sentiment) {
            case "BULLISH" -> query + " is showing positive momentum with " + bull
                    + " bullish signal(s). Consider a long position with a defined stop-loss "
                    + "below recent support. Confirm with volume and technical trend direction "
                    + "before entering. (Configure app.api.openai.key for AI-generated analysis.)";
            case "BEARISH" -> query + " faces " + bear + " bearish headwind(s). Exercise caution — "
                    + "consider waiting for a confirmed reversal or managing existing longs with "
                    + "tighter stops. Short positions may be viable for experienced traders. "
                    + "(Configure app.api.openai.key for AI-generated analysis.)";
            default -> query + " sentiment is mixed. News flow is balanced between positive and "
                    + "negative catalysts. Consider waiting for a clearer directional signal "
                    + "before opening new positions. Monitor key support/resistance levels closely. "
                    + "(Configure app.api.openai.key for AI-generated analysis.)";
        };
    }

    private String generateRuleBasedRiskWarning(String query, List<NewsItem> news) {
        List<String> risks = new ArrayList<>();
        news.forEach(item -> {
            String h = item.headline().toLowerCase();
            if (h.contains("regulat")) risks.add("Regulatory developments may impact price.");
            if (h.contains("rate") || h.contains("fed")) risks.add("Macro/Fed policy risk present.");
            if (h.contains("supply chain")) risks.add("Supply chain disruptions possible.");
            if (h.contains("competition") || h.contains("compet")) risks.add("Competitive pressure noted.");
        });
        risks.add("Past performance does not guarantee future results. Always use a stop-loss.");
        return risks.stream().distinct().limit(3)
                .reduce((a, b) -> a + " " + b).orElse("Standard market risks apply.");
    }

    /** Generates realistic sample news when Finnhub is not configured. */
    private List<NewsItem> generateSampleNews(String query) {
        boolean isCrypto = query.endsWith("USDT") || query.equals("BTC") || query.equals("ETH")
                || query.equals("SOL") || query.equals("BNB");
        boolean isMarketQuery = query.contains("MARKET") || query.contains("SECTOR")
                || query.contains("SENTIMENT");

        if (isMarketQuery) {
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

        // Stock fallback
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
}
