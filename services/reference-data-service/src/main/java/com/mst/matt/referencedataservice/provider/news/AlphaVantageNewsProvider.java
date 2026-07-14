package com.mst.matt.referencedataservice.provider.news;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.NewsArticleDto;
import com.mst.matt.contracts.provider.news.NewsProvider;
import com.mst.matt.referencedataservice.client.JsonUtil;
import com.mst.matt.referencedataservice.client.RefDataHttpClient;
import com.mst.matt.referencedataservice.config.RefDataProviderProperties;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Alpha Vantage implementation of {@link NewsProvider}.
 *
 * <h3>Endpoint</h3>
 * {@code GET /query?function=NEWS_SENTIMENT&tickers=SYMBOL&sort=LATEST&limit=N}
 *
 * <p>Returns pre-computed sentiment labels and scores alongside the articles.
 * Uses the shared {@code "alphavantage"} throttle (5 req/min).</p>
 */
@Component
public class AlphaVantageNewsProvider implements NewsProvider {

    public static final String PROVIDER_NAME = "ALPHA_VANTAGE";
    private static final String BASE_URL     = "https://www.alphavantage.co/query";
    private static final String THROTTLE     = "alphavantage";

    // AV time format: yyyyMMddTHHmmss
    private static final DateTimeFormatter AV_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private final RefDataProviderProperties keys;
    private final RefDataHttpClient http;
    private final Gson gson = new Gson();

    public AlphaVantageNewsProvider(RefDataProviderProperties keys,
                                     RefDataHttpClient http) {
        this.keys = keys;
        this.http = http;
    }

    @Override public String providerName() { return PROVIDER_NAME; }

    @Override
    public List<AssetClass> supportedAssetClasses() {
        return List.of(AssetClass.STOCK, AssetClass.CRYPTO, AssetClass.FOREX);
    }

    // ── NewsProvider ──────────────────────────────────────────────────────────

    @Override
    public List<NewsArticleDto> getNewsBySymbol(String symbol, AssetClass assetClass, int limit) {
        if (!keys.hasAlphavantageKey()) return List.of();
        int lim = Math.min(limit, 200);
        String url = BASE_URL + "?function=NEWS_SENTIMENT&tickers=" + symbol.toUpperCase()
                + "&sort=LATEST&limit=" + lim + "&apikey=" + keys.getAlphavantageKey();
        return fetchAndParse(url, assetClass, symbol, lim);
    }

    @Override
    public List<NewsArticleDto> getNewsBySymbol(String symbol, AssetClass assetClass,
                                                  Instant from, Instant to, int limit) {
        if (!keys.hasAlphavantageKey()) return List.of();
        String timeFrom = formatAvTime(from);
        String timeTo   = formatAvTime(to);
        int lim = Math.min(limit, 200);
        String url = BASE_URL + "?function=NEWS_SENTIMENT&tickers=" + symbol.toUpperCase()
                + "&sort=LATEST&limit=" + lim
                + "&time_from=" + timeFrom + "&time_to=" + timeTo
                + "&apikey=" + keys.getAlphavantageKey();
        return fetchAndParse(url, assetClass, symbol, lim);
    }

    @Override
    public List<NewsArticleDto> getNewsByAssetClass(AssetClass assetClass, int limit) {
        if (!keys.hasAlphavantageKey()) return List.of();
        // AV doesn't have a direct asset-class filter — use topic categories
        String topic = assetClassToAvTopic(assetClass);
        int lim = Math.min(limit, 200);
        String url = BASE_URL + "?function=NEWS_SENTIMENT&topics=" + topic
                + "&sort=LATEST&limit=" + lim + "&apikey=" + keys.getAlphavantageKey();
        return fetchAndParse(url, assetClass, null, lim);
    }

    @Override
    public List<NewsArticleDto> searchNews(String query, AssetClass assetClass, int limit) {
        if (!keys.hasAlphavantageKey()) return List.of();
        // AV does not support free-text queries — fall back to keyword-as-ticker search
        int lim = Math.min(limit, 200);
        String url = BASE_URL + "?function=NEWS_SENTIMENT&tickers=" + query.toUpperCase()
                + "&sort=RELEVANCE&limit=" + lim + "&apikey=" + keys.getAlphavantageKey();
        return fetchAndParse(url, assetClass, query, lim);
    }

    @Override
    public List<NewsArticleDto> getNewsBatch(List<String> symbols, AssetClass assetClass,
                                               int limitEach) {
        if (!keys.hasAlphavantageKey()) return List.of();
        // AV supports comma-separated tickers
        String tickers = symbols.stream()
                .map(String::toUpperCase)
                .collect(Collectors.joining(","));
        int lim = Math.min(limitEach * symbols.size(), 200);
        String url = BASE_URL + "?function=NEWS_SENTIMENT&tickers=" + tickers
                + "&sort=LATEST&limit=" + lim + "&apikey=" + keys.getAlphavantageKey();
        return fetchAndParse(url, assetClass, null, lim);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<NewsArticleDto> fetchAndParse(String url, AssetClass assetClass,
                                                String primarySymbol, int limit) {
        Optional<JsonObject> opt = http.getJson(url, null, THROTTLE);
        if (opt.isEmpty()) return List.of();
        JsonObject root = opt.get();
        if (!root.has("feed")) return List.of();

        JsonArray feed  = root.getAsJsonArray("feed");
        List<NewsArticleDto> result = new ArrayList<>();
        for (JsonElement el : feed) {
            if (!el.isJsonObject()) continue;
            JsonObject a = el.getAsJsonObject();
            result.add(parseArticle(a, assetClass));
            if (result.size() >= limit) break;
        }
        return Collections.unmodifiableList(result);
    }

    private NewsArticleDto parseArticle(JsonObject a, AssetClass assetClass) {
        String timeStr  = JsonUtil.str(a, "time_published");
        Instant published = parseAvTime(timeStr);

        // Collect related tickers from ticker_sentiment array
        List<String> relatedSymbols = new ArrayList<>();
        if (a.has("ticker_sentiment") && a.get("ticker_sentiment").isJsonArray()) {
            for (JsonElement ts : a.getAsJsonArray("ticker_sentiment")) {
                if (ts.isJsonObject()) {
                    String t = JsonUtil.str(ts.getAsJsonObject(), "ticker");
                    if (t != null) relatedSymbols.add(t);
                }
            }
        }

        // Collect authors
        String author = null;
        if (a.has("authors") && a.get("authors").isJsonArray()) {
            JsonArray arr = a.getAsJsonArray("authors");
            if (!arr.isEmpty()) author = arr.get(0).getAsString();
        }

        Double sentimentScore = null;
        String sentimentLabel = null;
        if (a.has("overall_sentiment_score") && !a.get("overall_sentiment_score").isJsonNull()) {
            sentimentScore = a.get("overall_sentiment_score").getAsDouble();
        }
        if (a.has("overall_sentiment_label")) {
            sentimentLabel = JsonUtil.normaliseSentimentLabel(
                    JsonUtil.str(a, "overall_sentiment_label"));
        }

        return NewsArticleDto.builder()
                .articleId(JsonUtil.str(a, "url"))   // AV has no unique ID; use URL
                .providerName(PROVIDER_NAME)
                .title(JsonUtil.str(a, "title"))
                .summary(JsonUtil.str(a, "summary"))
                .url(JsonUtil.str(a, "url"))
                .imageUrl(JsonUtil.str(a, "banner_image"))
                .source(JsonUtil.str(a, "source"))
                .author(author)
                .language("en")
                .publishedAt(published)
                .updatedAt(published)
                .assetClass(assetClass)
                .relatedSymbols(relatedSymbols)
                .sentimentScore(sentimentScore)
                .sentimentLabel(sentimentLabel)
                .build();
    }

    private static Instant parseAvTime(String s) {
        if (s == null || s.isBlank()) return Instant.now();
        try {
            return LocalDateTime.parse(s, AV_TIME).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return Instant.now();
        }
    }

    private static String formatAvTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC)
                .format(AV_TIME);
    }

    private static String assetClassToAvTopic(AssetClass ac) {
        return switch (ac) {
            case CRYPTO -> "blockchain,cryptocurrency";
            case FOREX  -> "forex";
            default     -> "earnings,financial_markets";
        };
    }
}
