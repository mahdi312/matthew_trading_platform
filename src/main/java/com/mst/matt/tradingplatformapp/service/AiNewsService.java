package com.mst.matt.tradingplatformapp.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
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
 * <p>Fetches real news (Finnhub when configured) and uses OpenAI to produce
 * sentiment, recommendations, and risk warnings. Results are cached per symbol
 * with a 15-minute TTL.
 */
@Service
public class AiNewsService {

    private static final Logger log = LoggerFactory.getLogger(AiNewsService.class);
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);
    private static final Gson GSON = new Gson();

    @Value("${app.api.finnhub.key:}")
    private String finnhubKey;

    @Value("${app.api.openai.key:}")
    private String openAiKey;

    @Value("${app.api.openai.model:gpt-4o-mini}")
    private String openAiModel;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final Map<String, CachedInsight> cache = new ConcurrentHashMap<>();

    public record NewsItem(
            String headline,
            String source,
            String sentiment,
            String url
    ) {}

    public record AiInsight(
            String symbol,
            String query,
            List<NewsItem> news,
            String overallSentiment,
            String recommendation,
            String riskWarning,
            LocalDateTime generatedAt
    ) {}

    private record CachedInsight(AiInsight insight, LocalDateTime expiresAt) {
        boolean isValid() { return LocalDateTime.now().isBefore(expiresAt); }
    }

    public AiInsight getInsight(String query) {
        if (query == null || query.isBlank()) query = "MARKET";
        String key = query.trim().toUpperCase();

        if (openAiKey == null || openAiKey.isBlank()) {
            throw new AiNewsException(
                    "OpenAI API key is not configured. Add app.api.openai.key to application-local.properties.");
        }

        CachedInsight cached = cache.get(key);
        if (cached != null && cached.isValid()) {
            log.debug("AI news cache hit for '{}'", key);
            return cached.insight();
        }

        AiInsight insight = fetchInsight(key);
        cache.put(key, new CachedInsight(insight, LocalDateTime.now().plus(CACHE_TTL)));
        return insight;
    }

    public void invalidate(String query) {
        if (query != null) cache.remove(query.trim().toUpperCase());
    }

    public List<String> popularSymbols() {
        return List.of(
                "AAPL", "TSLA", "NVDA", "MSFT", "AMZN", "GOOGL", "META", "NFLX",
                "BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT",
                "EURUSD", "GBPUSD", "USDJPY",
                "AI sector", "Crypto market", "Tech sector", "Market sentiment"
        );
    }

    private AiInsight fetchInsight(String query) {
        boolean isSymbol = query.matches("[A-Z0-9]{2,10}");
        List<NewsItem> news = new ArrayList<>();

        if (isSymbol && finnhubKey != null && !finnhubKey.isBlank()) {
            news.addAll(fetchFinnhubNews(query));
        }

        OpenAiAnalysis analysis = callOpenAi(query, news);
        List<NewsItem> enrichedNews = mergeNewsSentiments(news, analysis);

        return new AiInsight(
                query,
                query,
                enrichedNews,
                analysis.overallSentiment(),
                analysis.recommendation(),
                analysis.riskWarning(),
                LocalDateTime.now());
    }

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
                return parseFinnhubResponse(resp.body());
            }
            log.warn("Finnhub returned HTTP {} for {}", resp.statusCode(), symbol);
        } catch (Exception e) {
            log.warn("Finnhub news fetch failed for {}: {}", symbol, e.getMessage());
        }
        return List.of();
    }

    private List<NewsItem> parseFinnhubResponse(String json) {
        List<NewsItem> items = new ArrayList<>();
        try {
            List<Map<String, Object>> arr = GSON.fromJson(json,
                    new TypeToken<List<Map<String, Object>>>() {}.getType());
            if (arr == null) return items;

            for (int i = 0; i < Math.min(arr.size(), 8); i++) {
                Map<String, Object> item = arr.get(i);
                String headline = (String) item.getOrDefault("headline", "");
                String source   = (String) item.getOrDefault("source", "Finnhub");
                String url      = (String) item.getOrDefault("url", "");
                if (headline.isBlank()) continue;
                items.add(new NewsItem(headline, source, "NEUTRAL", url));
            }
        } catch (Exception e) {
            log.warn("Finnhub JSON parse error: {}", e.getMessage());
        }
        return items;
    }

    private OpenAiAnalysis callOpenAi(String query, List<NewsItem> news) {
        String headlinesBlock = news.isEmpty()
                ? "(No recent headlines available — base analysis on the symbol/query only.)"
                : news.stream()
                        .map(n -> "- " + n.headline() + " [" + n.source() + "]")
                        .reduce((a, b) -> a + "\n" + b)
                        .orElse("");

        String systemPrompt = """
                You are a financial analyst assistant. Respond ONLY with valid JSON (no markdown).
                Schema:
                {
                  "overallSentiment": "BULLISH" | "NEUTRAL" | "BEARISH",
                  "recommendation": "2-4 sentence actionable investment view",
                  "riskWarning": "1-2 sentence risk disclaimer",
                  "headlineSentiments": [{"index": 0, "sentiment": "BULLISH"|"NEUTRAL"|"BEARISH"}]
                }
                headlineSentiments.index matches the 0-based order of headlines provided (omit if no headlines).
                """;

        String userPrompt = "Symbol/query: " + query + "\n\nRecent headlines:\n" + headlinesBlock;

        JsonObject body = new JsonObject();
        body.addProperty("model", openAiModel);
        body.add("response_format", GSON.fromJson("{\"type\":\"json_object\"}", JsonObject.class));

        JsonObject sysMsg = new JsonObject();
        sysMsg.addProperty("role", "system");
        sysMsg.addProperty("content", systemPrompt);

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", userPrompt);

        body.add("messages", GSON.toJsonTree(List.of(sysMsg, userMsg)));

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openai.com/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(45))
                    .header("Authorization", "Bearer " + openAiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                String detail = extractOpenAiError(resp.body());
                throw new AiNewsException("OpenAI API error (HTTP " + resp.statusCode() + "): " + detail);
            }

            JsonObject root = GSON.fromJson(resp.body(), JsonObject.class);
            String content = root.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();

            JsonObject parsed = GSON.fromJson(content, JsonObject.class);
            String sentiment = normalizeSentiment(parsed.get("overallSentiment").getAsString());
            String recommendation = parsed.get("recommendation").getAsString();
            String risk = parsed.get("riskWarning").getAsString();

            List<HeadlineSentiment> headlineSentiments = new ArrayList<>();
            if (parsed.has("headlineSentiments") && parsed.get("headlineSentiments").isJsonArray()) {
                parsed.getAsJsonArray("headlineSentiments").forEach(el -> {
                    JsonObject o = el.getAsJsonObject();
                    headlineSentiments.add(new HeadlineSentiment(
                            o.get("index").getAsInt(),
                            normalizeSentiment(o.get("sentiment").getAsString())));
                });
            }

            return new OpenAiAnalysis(sentiment, recommendation, risk, headlineSentiments);
        } catch (AiNewsException e) {
            throw e;
        } catch (Exception e) {
            throw new AiNewsException("Failed to reach OpenAI: " + e.getMessage(), e);
        }
    }

    private static String extractOpenAiError(String body) {
        try {
            JsonObject err = GSON.fromJson(body, JsonObject.class);
            if (err.has("error")) {
                JsonObject e = err.getAsJsonObject("error");
                if (e.has("message")) return e.get("message").getAsString();
            }
        } catch (Exception ignored) {}
        return body != null && body.length() > 200 ? body.substring(0, 200) + "…" : String.valueOf(body);
    }

    private static String normalizeSentiment(String s) {
        if (s == null) return "NEUTRAL";
        return switch (s.trim().toUpperCase()) {
            case "BULLISH", "BUY", "POSITIVE" -> "BULLISH";
            case "BEARISH", "SELL", "NEGATIVE" -> "BEARISH";
            default -> "NEUTRAL";
        };
    }

    private List<NewsItem> mergeNewsSentiments(List<NewsItem> news, OpenAiAnalysis analysis) {
        if (news.isEmpty()) return List.of();
        List<NewsItem> out = new ArrayList<>(news.size());
        for (int i = 0; i < news.size(); i++) {
            NewsItem n = news.get(i);
            final int idx = i;
            String sent = analysis.headlineSentiments().stream()
                    .filter(h -> h.index() == idx)
                    .map(HeadlineSentiment::sentiment)
                    .findFirst()
                    .orElse("NEUTRAL");
            out.add(new NewsItem(n.headline(), n.source(), sent, n.url()));
        }
        return out;
    }

    private record OpenAiAnalysis(
            String overallSentiment,
            String recommendation,
            String riskWarning,
            List<HeadlineSentiment> headlineSentiments
    ) {}

    private record HeadlineSentiment(int index, String sentiment) {}
}
