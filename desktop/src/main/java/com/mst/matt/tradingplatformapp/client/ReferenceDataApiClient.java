package com.mst.matt.tradingplatformapp.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Typed HTTP client for {@code reference-data-service} exposed through the Gateway
 * at {@code /api/reference/**}.
 */
@Slf4j
@Component
public class ReferenceDataApiClient {

    private final WebClient webClient;

    public ReferenceDataApiClient(WebClient gatewayWebClient) {
        this.webClient = gatewayWebClient;
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NewsArticle {
        private String       articleId;
        private String       providerName;
        private String       title;
        private String       summary;
        private String       fullText;
        private String       url;
        private String       imageUrl;
        private String       source;
        private String       author;
        private String       language;
        private Instant      publishedAt;
        private Instant      updatedAt;
        private List<String> assetClasses;
        private List<String> relatedSymbols;
        private List<String> categories;
        private Double       sentimentScore;
        private String       sentimentLabel;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SentimentSnapshot {
        private String       symbol;
        private String       assetClass;
        private String       providerName;
        private Instant      snapshotAt;
        private Double       sentimentScore;
        private String       sentimentLabel;
        private Integer      rawIndexValue;
        private Integer      previousRawIndexValue;
        private String       previousSentimentLabel;
        private Long         socialVolume24h;
        private Double       socialVolumeChange24hPct;
        private Long         twitterMentions24h;
        private Long         redditMentions24h;
        private Long         telegramMentions24h;
        private Double       bullishRatio;
        private Double       bearishRatio;
        private Double       neutralRatio;
        private List<Double> sentimentTrend7d;
        private List<Double> sentimentTrend24h;
        private Double       longShortRatio;
        private Double       fundingRate;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EconomicEvent {
        private String       eventId;
        private String       providerName;
        private String       title;
        private String       description;
        private String       country;
        private String       category;
        private Instant      scheduledAt;
        private Instant      actualReleasedAt;
        private boolean      isTimeTentative;
        private String       impactLevel;
        private List<String> affectedAssetClasses;
        private List<String> affectedCurrencies;
        private String       forecast;
        private String       previous;
        private String       actual;
        private String       surprise;
        private boolean      isRecurring;
        private boolean      isReleased;
        private boolean      isRevised;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SymbolSearchResult {
        private String  symbol;
        private String  displayName;
        private String  assetClass;
        private String  instrumentType;
        private String  exchange;
        private String  quoteCurrency;
        private String  country;
        private String  providerSymbol;
        private String  providerName;
        private Double  relevanceScore;
        private boolean active;
        private boolean platformSupported;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NftCollection {
        private String       collectionSlug;
        private String       name;
        private String       description;
        private String       blockchain;
        private String       contractAddress;
        private String       tokenStandard;
        private String       providerName;
        private Instant      fetchedAt;
        private Long         totalSupply;
        private Long         ownerCount;
        private BigDecimal   top10HolderPct;
        private BigDecimal   floorPrice;
        private BigDecimal   floorPriceUsd;
        private String       nativeCurrency;
        private BigDecimal   floorPriceChange24hPct;
        private BigDecimal   floorPriceChange7dPct;
        private BigDecimal   athFloorPrice;
        private Instant      athFloorDate;
        private BigDecimal   volume24h;
        private BigDecimal   volume24hUsd;
        private BigDecimal   volume7d;
        private BigDecimal   volumeAllTime;
        private Long         sales24h;
        private BigDecimal   avgPrice24h;
        private List<String> categories;
        private List<String> externalLinks;
        private String       metadataBaseUri;
        private boolean      rarityAvailable;
        private Long         twitterFollowers;
        private Long         discordMembers;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DeFiPool {
        private String     poolAddress;
        private String     poolName;
        private String     networkId;
        private String     networkName;
        private String     dexId;
        private String     dexName;
        private String     baseTokenSymbol;
        private String     quoteTokenSymbol;
        private String     baseTokenAddress;
        private String     quoteTokenAddress;
        private BigDecimal priceUsd;
        private BigDecimal priceChangePercent5m;
        private BigDecimal priceChangePercent1h;
        private BigDecimal priceChangePercent24h;
        private BigDecimal volume24hUsd;
        private BigDecimal volume6hUsd;
        private BigDecimal volume1hUsd;
        private BigDecimal liquidityUsd;
        private BigDecimal marketCapUsd;
        private Long       txCount24h;
        private Long       buys24h;
        private Long       sells24h;
        private Instant    createdAt;
        private String     providerName;
        private Instant    fetchedAt;
    }

    // ── Fundamentals ──────────────────────────────────────────────────────────

    /**
     * {@code GET /api/reference/fundamentals/{symbol}?assetClass=}
     *
     * <p>Response shape varies by asset class (stock / crypto / forex). Returns raw JSON
     * so callers can bind to the appropriate nested DTO.</p>
     */
    public Optional<JsonNode> getFundamentals(String symbol, String assetClass) {
        try {
            JsonNode node = webClient.get()
                    .uri(u -> u.path("/api/reference/fundamentals/{symbol}")
                            .queryParam("assetClass", assetClass != null ? assetClass : "STOCK")
                            .build(symbol))
                    .retrieve()
                    .onStatus(status -> status.value() == 404, r -> Mono.empty())
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Fetch fundamentals failed: " + body))))
                    .bodyToMono(JsonNode.class)
                    .block();
            return Optional.ofNullable(node);
        } catch (Exception ex) {
            log.warn("getFundamentals({}) failed: {}", symbol, ex.getMessage());
            return Optional.empty();
        }
    }

    // ── News ──────────────────────────────────────────────────────────────────

    /** {@code GET /api/reference/news} */
    public List<NewsArticle> getNews(String symbol, String assetClass, int limit,
                                     LocalDate from, LocalDate to) {
        try {
            List<NewsArticle> articles = webClient.get()
                    .uri(u -> {
                        var builder = u.path("/api/reference/news")
                                .queryParam("limit", limit);
                        if (symbol != null && !symbol.isBlank()) {
                            builder.queryParam("symbol", symbol);
                        }
                        if (assetClass != null && !assetClass.isBlank()) {
                            builder.queryParam("assetClass", assetClass);
                        }
                        if (from != null) builder.queryParam("from", from);
                        if (to != null) builder.queryParam("to", to);
                        return builder.build();
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Fetch news failed: " + body))))
                    .bodyToMono(new ParameterizedTypeReference<List<NewsArticle>>() {})
                    .block();
            return articles != null ? articles : List.of();
        } catch (Exception ex) {
            log.warn("getNews failed: {}", ex.getMessage());
            return List.of();
        }
    }

    // ── Sentiment ─────────────────────────────────────────────────────────────

    /** {@code GET /api/reference/sentiment} */
    public Optional<SentimentSnapshot> getSentiment(String symbol, String assetClass) {
        try {
            SentimentSnapshot snapshot = webClient.get()
                    .uri(u -> {
                        var builder = u.path("/api/reference/sentiment");
                        if (symbol != null && !symbol.isBlank()) {
                            builder.queryParam("symbol", symbol);
                        }
                        if (assetClass != null && !assetClass.isBlank()) {
                            builder.queryParam("assetClass", assetClass);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .onStatus(status -> status.value() == 404, r -> Mono.empty())
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Fetch sentiment failed: " + body))))
                    .bodyToMono(SentimentSnapshot.class)
                    .block();
            return Optional.ofNullable(snapshot);
        } catch (Exception ex) {
            log.warn("getSentiment failed: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    // ── Calendar ──────────────────────────────────────────────────────────────

    /** {@code GET /api/reference/calendar} */
    public List<EconomicEvent> getCalendar(String assetClass, LocalDate from, LocalDate to,
                                           String impactLevel) {
        try {
            List<EconomicEvent> events = webClient.get()
                    .uri(u -> {
                        var builder = u.path("/api/reference/calendar");
                        if (assetClass != null && !assetClass.isBlank()) {
                            builder.queryParam("assetClass", assetClass);
                        }
                        if (from != null) builder.queryParam("from", from);
                        if (to != null) builder.queryParam("to", to);
                        if (impactLevel != null && !impactLevel.isBlank()) {
                            builder.queryParam("impactLevel", impactLevel);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Fetch calendar failed: " + body))))
                    .bodyToMono(new ParameterizedTypeReference<List<EconomicEvent>>() {})
                    .block();
            return events != null ? events : List.of();
        } catch (Exception ex) {
            log.warn("getCalendar failed: {}", ex.getMessage());
            return List.of();
        }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    /** {@code GET /api/reference/search?query=} */
    public List<SymbolSearchResult> search(String query, String assetClass, int limit) {
        try {
            List<SymbolSearchResult> results = webClient.get()
                    .uri(u -> {
                        var builder = u.path("/api/reference/search")
                                .queryParam("query", query)
                                .queryParam("limit", limit);
                        if (assetClass != null && !assetClass.isBlank()) {
                            builder.queryParam("assetClass", assetClass);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Reference search failed: " + body))))
                    .bodyToMono(new ParameterizedTypeReference<List<SymbolSearchResult>>() {})
                    .block();
            return results != null ? results : List.of();
        } catch (Exception ex) {
            log.warn("search('{}') failed: {}", query, ex.getMessage());
            return List.of();
        }
    }

    // ── NFT ───────────────────────────────────────────────────────────────────

    /** {@code GET /api/reference/nft/collections} */
    public List<NftCollection> getNftCollections(int limit, int page, boolean trending,
                                                 String query) {
        try {
            List<NftCollection> collections = webClient.get()
                    .uri(u -> {
                        var builder = u.path("/api/reference/nft/collections")
                                .queryParam("limit", limit)
                                .queryParam("page", page)
                                .queryParam("trending", trending);
                        if (query != null && !query.isBlank()) {
                            builder.queryParam("query", query);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Fetch NFT collections failed: " + body))))
                    .bodyToMono(new ParameterizedTypeReference<List<NftCollection>>() {})
                    .block();
            return collections != null ? collections : List.of();
        } catch (Exception ex) {
            log.warn("getNftCollections failed: {}", ex.getMessage());
            return List.of();
        }
    }

    // ── DeFi ──────────────────────────────────────────────────────────────────

    /** {@code GET /api/reference/defi/pools} */
    public List<DeFiPool> getDefiPools(String network, String query, boolean trending) {
        try {
            List<DeFiPool> pools = webClient.get()
                    .uri(u -> {
                        var builder = u.path("/api/reference/defi/pools")
                                .queryParam("trending", trending);
                        if (network != null && !network.isBlank()) {
                            builder.queryParam("network", network);
                        }
                        if (query != null && !query.isBlank()) {
                            builder.queryParam("query", query);
                        }
                        return builder.build();
                    })
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, r ->
                            r.bodyToMono(String.class)
                             .flatMap(body -> Mono.error(
                                     new RuntimeException("Fetch DeFi pools failed: " + body))))
                    .bodyToMono(new ParameterizedTypeReference<List<DeFiPool>>() {})
                    .block();
            return pools != null ? pools : List.of();
        } catch (Exception ex) {
            log.warn("getDefiPools failed: {}", ex.getMessage());
            return List.of();
        }
    }
}
