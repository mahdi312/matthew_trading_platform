package com.mst.matt.contracts.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;

import java.time.Instant;
import java.util.List;

/**
 * Normalized AI critique of a trade journal entry.
 *
 * <p>Returned by
 * {@link com.mst.matt.contracts.provider.ai.AiAnalysisProvider#critiqueTradeJournalEntry}.
 * Used by the Trade Journal tab to provide AI-driven feedback on
 * individual trades.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiTradeJournalCritiqueDto {

    // ── Reference ─────────────────────────────────────────────────────────────

    /**
     * Internal trade ID from the Trade Journal service.
     * Used to correlate the critique back to the originating trade.
     */
    private String tradeId;

    /**
     * Name of the AI backend that generated this critique.
     */
    private String providerName;

    /** Timestamp the critique was generated. */
    private Instant generatedAt;

    // ── Overall assessment ────────────────────────────────────────────────────

    /**
     * Overall trade quality score in the range [0 … 10].
     * 0 = very poor execution; 10 = exemplary execution.
     */
    private Integer overallScore;

    /**
     * Summary of the overall assessment (2–4 sentences).
     */
    private String overallAssessment;

    // ── Dimensional scores ────────────────────────────────────────────────────

    /** Entry timing quality score [0 … 10]. */
    private Integer entryTimingScore;

    /** Exit timing quality score [0 … 10]. */
    private Integer exitTimingScore;

    /** Risk management quality score [0 … 10]. */
    private Integer riskManagementScore;

    /** Plan adherence score [0 … 10] — how well the trade followed the stated plan. */
    private Integer planAdherenceScore;

    // ── Detailed feedback ─────────────────────────────────────────────────────

    /**
     * What the trader did well (positive observations).
     */
    @Singular("strength")
    private List<String> strengths;

    /**
     * Areas for improvement and specific suggestions.
     */
    @Singular("improvement")
    private List<String> improvements;

    /**
     * Potential psychological biases identified (e.g., "FOMO entry",
     * "Revenge trade after loss", "Early exit due to loss aversion").
     */
    @Singular("bias")
    private List<String> identifiedBiases;

    // ── Context used ─────────────────────────────────────────────────────────

    /**
     * Whether market context (OHLCV + news at the time of the trade)
     * was included in the critique input.
     */
    private boolean marketContextIncluded;

    /**
     * Brief description of market conditions at trade entry, as understood
     * by the AI (e.g., "High volatility, post-CPI, BTC in downtrend on 4h").
     */
    private String marketContextAtEntry;
}
