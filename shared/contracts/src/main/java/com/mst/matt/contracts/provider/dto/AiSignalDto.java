package com.mst.matt.contracts.provider.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Normalized AI-generated trading signal or insight.
 *
 * <p>Embedded inside {@link AiMarketSummaryDto#getSignals()} and returned
 * by {@link com.mst.matt.contracts.provider.ai.AiAnalysisProvider}.
 * Signals are <em>informational only</em> — the platform never auto-executes
 * trades based on AI signals.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiSignalDto {

    // ── Classification ────────────────────────────────────────────────────────

    /**
     * Signal type / category (e.g., "TREND_CONTINUATION", "REVERSAL",
     * "BREAKOUT", "SUPPORT_RESISTANCE", "NEWS_CATALYST", "SENTIMENT_SHIFT",
     * "FUNDAMENTAL_CHANGE").
     */
    private String signalType;

    /**
     * Direction of the signal: "BULLISH", "BEARISH", or "NEUTRAL".
     */
    private String direction;

    /**
     * Signal strength / confidence in the range [0.0 … 1.0].
     */
    private Double strength;

    // ── Context ───────────────────────────────────────────────────────────────

    /**
     * Short human-readable description of the signal
     * (e.g., "Double-bottom pattern forming on the 4h chart with increasing volume").
     */
    private String description;

    /**
     * The primary evidence or reasoning that triggered this signal
     * (e.g., "RSI divergence on 1d", "Positive earnings surprise").
     */
    private String rationale;

    // ── Price targets (optional) ──────────────────────────────────────────────

    /**
     * Suggested entry price level; {@code null} if the signal does not
     * specify a precise entry.
     */
    private BigDecimal suggestedEntry;

    /**
     * Suggested target price / take-profit level; {@code null} if not specified.
     */
    private BigDecimal targetPrice;

    /**
     * Suggested stop-loss level; {@code null} if not specified.
     */
    private BigDecimal stopLoss;

    // ── Time horizon ─────────────────────────────────────────────────────────

    /**
     * Expected time horizon of the signal (e.g., "SHORT_TERM", "MEDIUM_TERM",
     * "LONG_TERM") — not a specific date.
     */
    private String timeHorizon;

    /** Timestamp when this signal was generated. */
    private Instant generatedAt;
}
