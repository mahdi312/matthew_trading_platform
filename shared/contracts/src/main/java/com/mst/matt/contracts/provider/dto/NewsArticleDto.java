package com.mst.matt.contracts.provider.dto;

import com.mst.matt.contracts.enums.AssetClass;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;

import java.time.Instant;
import java.util.List;

/**
 * Normalized news article / headline returned by
 * {@link com.mst.matt.contracts.provider.news.NewsProvider}.
 *
 * <p>No consumer (AI tab, Analysis tab) should ever see a provider-specific
 * response shape — all provider implementations must map their raw API
 * responses to this DTO.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsArticleDto {

    // ── Identity ──────────────────────────────────────────────────────────────

    /**
     * Provider-assigned unique identifier for this article.
     * Consumers should use this for deduplication when aggregating
     * across multiple providers.
     */
    private String articleId;

    /** Name of the data provider that sourced this article (e.g., "FINNHUB", "BENZINGA"). */
    private String providerName;

    // ── Content ───────────────────────────────────────────────────────────────

    /** Article headline / title. */
    private String title;

    /**
     * Article summary / excerpt (up to a few sentences).
     * May be {@code null} if the provider only returns headlines.
     */
    private String summary;

    /**
     * Full article body text; {@code null} if the provider does not expose
     * full text (paywalled or headline-only).
     */
    private String fullText;

    /** Canonical URL to the original article. */
    private String url;

    /**
     * URL to the article thumbnail / header image; {@code null} if not
     * available.
     */
    private String imageUrl;

    /** Name of the publishing outlet (e.g., "Reuters", "CoinDesk", "Bloomberg"). */
    private String source;

    /** Original author name; may be {@code null}. */
    private String author;

    /** Language of the article (ISO-639-1 code, e.g., "en", "de"). */
    private String language;

    // ── Timing ────────────────────────────────────────────────────────────────

    /** Timestamp the article was published. */
    private Instant publishedAt;

    /** Timestamp the article was last updated; may equal {@link #publishedAt}. */
    private Instant updatedAt;

    // ── Classification ────────────────────────────────────────────────────────

    /**
     * Asset class(es) this article is most relevant to.
     * An article about crypto and stocks would list both.
     */
    @Singular
    private List<AssetClass> assetClasses;

    /**
     * Canonical platform symbols mentioned in this article
     * (e.g., ["AAPL", "MSFT"], ["BTC", "ETH"]).
     * May be empty if the provider does not tag symbols.
     */
    @Singular
    private List<String> relatedSymbols;

    /**
     * Category tags provided by the source (e.g., "earnings", "merger",
     * "regulation", "macro").
     */
    @Singular
    private List<String> categories;

    // ── Pre-computed sentiment (if provider supplies it) ──────────────────────

    /**
     * Pre-computed sentiment score from the news provider in the range
     * [{@code -1.0} (very negative) … {@code +1.0} (very positive)].
     * {@code null} if the provider does not compute sentiment — the
     * platform's own {@link com.mst.matt.contracts.provider.ai.AiAnalysisProvider}
     * will fill this in post-processing.
     */
    private Double sentimentScore;

    /**
     * Pre-computed sentiment label (e.g., "POSITIVE", "NEGATIVE", "NEUTRAL").
     * {@code null} if not provided.
     */
    private String sentimentLabel;
}
