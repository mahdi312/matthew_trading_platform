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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Finnhub implementation of {@link NewsProvider}.
 *
 * <h3>Endpoints used</h3>
 * <ul>
 *   <li>{@code GET /company-news?symbol=...&from=...&to=...} — company-specific news</li>
 *   <li>{@code GET /news?category=...} — market-wide news by category</li>
 *   <li>{@code GET /news-sentiment?symbol=...} — aggregated sentiment label</li>
 * </ul>
 *
 * <p>Rate limit: 60 req/min — throttle key {@code "finnhub"}.</p>
 */
@Component
public class FinnhubNewsProvider implements NewsProvider {

    public static final String PROVIDER_NAME = "FINNHUB";
    private static final String BASE_URL     = "https://finnhub.io/api/v1";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final RefDataProviderProperties keys;
    private final RefDataHttpClient http;
    private final Gson gson = new Gson();

    public FinnhubNewsProvider(RefDataProviderProperties keys, RefDataHttpClient http) {
        this.keys = keys;
        this.http = http;
    }

    @Override public String providerName() { return PROVIDER_NAME; }

    @Override
    public List<AssetClass> supportedAssetClasses() {
        return List.of(AssetClass.STOCK, AssetClass.CRYPTO);
    }

    // ── NewsProvider ──────────────────────────────────────────────────────────

    @Override
    public List<NewsArticleDto> getNewsBySymbol(String symbol, AssetClass assetClass, int limit) {
        if (!keys.hasFinnhubKey()) return List.of();
        // Default: last 7 days
        LocalDate to   = LocalDate.now();
        LocalDate from = to.minusDays(7);
        return fetchCompanyNews(symbol, from.format(DATE_FMT), to.format(DATE_FMT),
                assetClass, limit);
    }

    @Override
    public List<NewsArticleDto> getNewsBySymbol(String symbol, AssetClass assetClass,
                                                  Instant from, Instant to, int limit) {
        if (!keys.hasFinnhubKey()) return List.of();
        String fromStr = LocalDate.ofInstant(from, java.time.ZoneOffset.UTC).format(DATE_FMT);
        String toStr   = LocalDate.ofInstant(to,   java.time.ZoneOffset.UTC).format(DATE_FMT);
        return fetchCompanyNews(symbol, fromStr, toStr, assetClass, limit);
    }

    @Override
    public List<NewsArticleDto> getNewsByAssetClass(AssetClass assetClass, int limit) {
        if (!keys.hasFinnhubKey()) return List.of();
        String category = assetClassToFinnhubCategory(assetClass);
        String url = BASE_URL + "/news?category=" + category
                + "&token=" + keys.getFinnhubKey();
        return fetchArrayNews(url, assetClass, limit);
    }

    @Override
    public List<NewsArticleDto> searchNews(String query, AssetClass assetClass, int limit) {
        // Finnhub does not expose a free-tier free-text news search
        return getNewsBySymbol(query, assetClass != null ? assetClass : AssetClass.STOCK, limit);
    }

    @Override
    public List<NewsArticleDto> getNewsBatch(List<String> symbols, AssetClass assetClass,
                                               int limitEach) {
        if (!keys.hasFinnhubKey()) return List.of();
        List<NewsArticleDto> combined = new ArrayList<>();
        for (String sym : symbols) {
            combined.addAll(getNewsBySymbol(sym, assetClass, limitEach));
        }
        combined.sort((a, b) -> {
            if (a.getPublishedAt() == null && b.getPublishedAt() == null) return 0;
            if (a.getPublishedAt() == null) return 1;
            if (b.getPublishedAt() == null) return -1;
            return b.getPublishedAt().compareTo(a.getPublishedAt());
        });
        return Collections.unmodifiableList(combined);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private List<NewsArticleDto> fetchCompanyNews(String symbol, String fromDate, String toDate,
                                                    AssetClass assetClass, int limit) {
        String url = BASE_URL + "/company-news?symbol=" + symbol.toUpperCase()
                + "&from=" + fromDate + "&to=" + toDate
                + "&token=" + keys.getFinnhubKey();
        return fetchArrayNews(url, assetClass, limit);
    }

    private List<NewsArticleDto> fetchArrayNews(String url, AssetClass assetClass, int limit) {
        return http.getJsonElement(url, null, "finnhub")
                .filter(JsonElement::isJsonArray)
                .map(el -> {
                    JsonArray arr = el.getAsJsonArray();
                    List<NewsArticleDto> result = new ArrayList<>();
                    for (JsonElement item : arr) {
                        if (!item.isJsonObject()) continue;
                        result.add(parseArticle(item.getAsJsonObject(), assetClass));
                        if (result.size() >= limit) break;
                    }
                    return (List<NewsArticleDto>) Collections.unmodifiableList(result);
                })
                .orElse(List.of());
    }

    private NewsArticleDto parseArticle(JsonObject a, AssetClass assetClass) {
        Long epochSec = JsonUtil.longVal(a, "datetime");
        Instant published = epochSec != null ? Instant.ofEpochSecond(epochSec) : null;

        String related = JsonUtil.str(a, "related");
        List<String> relatedSymbols = new ArrayList<>();
        if (related != null && !related.isBlank()) {
            for (String t : related.split(",")) {
                String sym = t.trim();
                if (!sym.isEmpty()) relatedSymbols.add(sym);
            }
        }

        Long idLong = JsonUtil.longVal(a, "id");
        String articleId = idLong != null ? String.valueOf(idLong) : JsonUtil.str(a, "url");

        String category = JsonUtil.str(a, "category");
        List<String> categories = category != null ? List.of(category) : List.of();

        return NewsArticleDto.builder()
                .articleId(articleId)
                .providerName(PROVIDER_NAME)
                .title(JsonUtil.str(a, "headline"))
                .summary(JsonUtil.str(a, "summary"))
                .url(JsonUtil.str(a, "url"))
                .imageUrl(JsonUtil.str(a, "image"))
                .source(JsonUtil.str(a, "source"))
                .language("en")
                .publishedAt(published)
                .updatedAt(published)
                .assetClass(assetClass)
                .relatedSymbols(relatedSymbols)
                .categories(categories)
                .build();
    }

    private static String assetClassToFinnhubCategory(AssetClass ac) {
        return switch (ac) {
            case CRYPTO -> "crypto";
            case FOREX  -> "forex";
            default     -> "general";
        };
    }
}
