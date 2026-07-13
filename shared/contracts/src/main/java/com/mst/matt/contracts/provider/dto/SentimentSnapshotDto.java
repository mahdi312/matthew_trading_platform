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
 * Normalized social / crowd sentiment snapshot for a symbol or asset class.
 *
 * <p>Returned by {@link com.mst.matt.contracts.provider.sentiment.SentimentProvider}.
 * No consumer should ever see a provider-specific response shape.</p>
 *
 * <p>Covers social media volume, mention trends, fear-and-greed indices,
 * and aggregated community mood signals.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SentimentSnapshotDto {

    // ── Identity ──────────────────────────────────────────────────────────────

    /**
     * Canonical platform symbol this snapshot covers; {@code null} for
     * asset-class level snapshots (e.g., the crypto fear-and-greed index).
     */
    private String symbol;

    /** Asset class of the subject. */
    private AssetClass assetClass;

    /**
     * Name of the data provider that sourced this snapshot
     * (e.g., "ALTERNATIVE_ME", "LUNARCRUSH", "STOCKTWITS", "REDDIT").
     */
    private String providerName;

    /** Timestamp this snapshot was taken. */
    private Instant snapshotAt;

    // ── Aggregate sentiment score ─────────────────────────────────────────────

    /**
     * Normalized sentiment score in the range [{@code -1.0} … {@code +1.0}].
     * {@code -1.0} = extreme fear / bearish; {@code 0.0} = neutral;
     * {@code +1.0} = extreme greed / bullish.
     */
    private Double sentimentScore;

    /**
     * Human-readable sentiment label (e.g., "EXTREME_FEAR", "FEAR",
     * "NEUTRAL", "GREED", "EXTREME_GREED").
     */
    private String sentimentLabel;

    /**
     * Raw provider-specific index value (e.g., 0–100 for fear-and-greed);
     * preserved for display without normalisation.
     */
    private Integer rawIndexValue;

    /** Previous day's raw index value; {@code null} if not available. */
    private Integer previousRawIndexValue;

    /** Yesterday's sentiment label. */
    private String previousSentimentLabel;

    // ── Social volume ─────────────────────────────────────────────────────────

    /** Total social mentions / posts in the last 24 hours. */
    private Long socialVolume24h;

    /** 24-hour percentage change in social volume (e.g., 0.20 = 20% increase). */
    private Double socialVolumeChange24hPct;

    /** Twitter/X mentions in the last 24 hours. */
    private Long twitterMentions24h;

    /** Reddit posts and comments in the last 24 hours. */
    private Long redditMentions24h;

    /** Telegram message volume in the last 24 hours. */
    private Long telegramMentions24h;

    // ── Engagement ────────────────────────────────────────────────────────────

    /**
     * Bullish post count as a percentage of total posts
     * (e.g., 0.65 = 65% bullish posts).
     */
    private Double bullishRatio;

    /** Bearish post percentage. */
    private Double bearishRatio;

    /** Neutral post percentage. */
    private Double neutralRatio;

    // ── Trend / time series ───────────────────────────────────────────────────

    /**
     * 7-day historical sentiment trend — one entry per day, oldest first.
     * Each entry contains the {@link #sentimentScore} for that day.
     */
    @Singular("sentimentTrend7dPoint")
    private List<Double> sentimentTrend7d;

    /** Sentiment scores over the last 24 hours (hourly); oldest first. */
    @Singular("sentimentTrend24hPoint")
    private List<Double> sentimentTrend24h;

    // ── Additional indices ────────────────────────────────────────────────────

    /**
     * Long/short ratio from derivatives market if applicable
     * (e.g., 1.2 = 1.2 longs per short); {@code null} if not available.
     */
    private Double longShortRatio;

    /**
     * Funding rate on perpetual futures; {@code null} if not applicable.
     * Positive = longs pay shorts (bullish bias);
     * negative = shorts pay longs (bearish bias).
     */
    private Double fundingRate;
}
