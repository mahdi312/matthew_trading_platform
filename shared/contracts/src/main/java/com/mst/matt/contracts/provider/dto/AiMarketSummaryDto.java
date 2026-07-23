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
 * Normalized AI-generated market summary for a symbol or asset class.
 *
 * <p>Returned by
 * {@link com.mst.matt.contracts.provider.ai.AiAnalysisProvider#summarizeMarket}.
 * No consumer (AI tab, Analysis tab) should ever see a provider-specific shape.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiMarketSummaryDto {

    // ── Identity ──────────────────────────────────────────────────────────────

    /**
     * Canonical platform symbol the summary covers
     * (e.g., "BTCUSDT", "AAPL", "EURUSD"); {@code null} for asset-class
     * level summaries.
     */
    private String symbol;

    /** Asset class of the subject. */
    private AssetClass assetClass;

    /**
     * Name of the AI backend that generated this summary
     * (e.g., "OPENAI_GPT4O", "GEMINI_PRO", "CLAUDE_SONNET").
     */
    private String providerName;

    /** Timestamp the summary was generated. */
    private Instant generatedAt;

    /** Model version used (e.g., "gpt-4o-2024-08-06"). */
    private String modelVersion;

    // ── Summary content ───────────────────────────────────────────────────────

    /**
     * Short headline / one-liner (up to 120 characters) for display in
     * list views or push notifications.
     */
    private String headline;

    /**
     * Full narrative summary (a few paragraphs) covering price action,
     * key drivers, and forward-looking context.
     */
    private String summary;

    /**
     * Bullet-point key takeaways (3–5 items).
     */
    @Singular
    private List<String> keyPoints;

    // ── Sentiment ─────────────────────────────────────────────────────────────

    /**
     * Overall sentiment score in the range [{@code -1.0} … {@code +1.0}].
     * {@code -1.0} = strongly bearish; {@code 0.0} = neutral;
     * {@code +1.0} = strongly bullish.
     */
    private Double sentimentScore;

    /**
     * Sentiment label derived from {@link #sentimentScore}
     * (e.g., "STRONGLY_BULLISH", "BULLISH", "NEUTRAL", "BEARISH",
     * "STRONGLY_BEARISH").
     */
    private String sentimentLabel;

    /**
     * Confidence level of the AI's assessment in the range [0.0 … 1.0].
     * Low confidence should be surfaced to the user.
     */
    private Double confidence;

    // ── Signals ───────────────────────────────────────────────────────────────

    /**
     * List of AI-generated trading signals / insights derived from the
     * combined market data and news context.
     */
    @Singular
    private List<AiSignalDto> signals;

    // ── Input metadata ────────────────────────────────────────────────────────

    /**
     * Number of news articles that were fed into this analysis.
     */
    private Integer newsArticleCount;

    /**
     * Number of OHLCV bars that were fed into this analysis.
     */
    private Integer ohlcvBarCount;

    /**
     * Timeframe covered by the input data (e.g., "7d", "30d").
     */
    private String analysisPeriod;
}
