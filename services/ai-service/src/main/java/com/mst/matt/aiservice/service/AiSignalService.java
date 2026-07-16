package com.mst.matt.aiservice.service;

import com.mst.matt.contracts.enums.AssetClass;
import com.mst.matt.contracts.provider.dto.AiSignalDto;
import com.mst.matt.contracts.provider.dto.NewsArticleDto;
import com.mst.matt.contracts.provider.dto.NormalizedOhlcvBar;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Simplified signal-scoring service for the AI-service tier.
 *
 * <p><b>Scope</b>: This class implements a purely arithmetic, indicator-free
 * signal pipeline that runs on normalised {@link NormalizedOhlcvBar} price
 * data and {@link NewsArticleDto} sentiment — <em>no</em> ta4j, no
 * {@code IndicatorResult}, no {@code SupportResistanceService} (those live in
 * market-service only).
 *
 * <p><b>Signals computed</b>:
 * <ul>
 *   <li>Trend direction from short vs. long EMA approximation (SMA20 / SMA50)</li>
 *   <li>Momentum from recent close-to-close percentage change</li>
 *   <li>Volume surge detection vs. 20-bar average</li>
 *   <li>News sentiment composite from {@link NewsArticleDto#getSentimentScore()}</li>
 * </ul>
 *
 * <p><b>Scoring</b>: Each signal contributes a weighted score in [−1, +1].
 * Composite score thresholds: ≥ 0.6 → STRONG_BUY, ≥ 0.2 → BUY,
 * ≤ −0.6 → STRONG_SELL, ≤ −0.2 → SELL, else NEUTRAL.
 */
@Service
public class AiSignalService {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    // ── Public API ─────────────────────────────────────────────────────────────

    public record SignalResult(
            double compositeScore,
            double confidence,
            String recommendation,   // STRONG_BUY, BUY, NEUTRAL, SELL, STRONG_SELL
            List<AiSignalDto> signals,
            int bullishCount,
            int bearishCount,
            int neutralCount
    ) {}

    /**
     * Score a symbol given recent OHLCV bars and news articles.
     *
     * @param symbol     canonical symbol
     * @param assetClass asset class
     * @param bars       recent OHLCV bars, oldest-first (at least 2 required)
     * @param news       recent news articles (may be empty)
     * @return scored result with individual signals and composite recommendation
     */
    public SignalResult score(String symbol, AssetClass assetClass,
                              List<NormalizedOhlcvBar> bars,
                              List<NewsArticleDto> news) {

        List<WeightedSignal> signals = new ArrayList<>();

        if (bars != null && bars.size() >= 2) {
            scoreTrend(bars, signals);
            scoreMomentum(bars, signals);
            scoreVolume(bars, signals);
        }
        scoreNewsSentiment(news, signals);

        double totalWeight    = signals.stream().mapToDouble(WeightedSignal::weight).sum();
        double weightedSum    = signals.stream().mapToDouble(s -> s.score() * s.weight()).sum();
        double compositeScore = totalWeight > 0 ? weightedSum / totalWeight : 0.0;
        double confidence     = Math.abs(compositeScore) * 100.0;

        String recommendation = toRecommendation(compositeScore);
        long bullish  = signals.stream().filter(s -> s.score() >  0.1).count();
        long bearish  = signals.stream().filter(s -> s.score() < -0.1).count();
        long neutral  = signals.size() - bullish - bearish;

        List<AiSignalDto> dtos = signals.stream()
                .sorted(Comparator.comparingDouble(s -> -Math.abs(s.score())))
                .map(s -> toDto(s, symbol))
                .toList();

        return new SignalResult(compositeScore, confidence, recommendation, dtos,
                (int) bullish, (int) bearish, (int) neutral);
    }

    // ── Signal scorers ─────────────────────────────────────────────────────────

    /** SMA20 vs SMA50 trend direction (weight 0.30). */
    private void scoreTrend(List<NormalizedOhlcvBar> bars, List<WeightedSignal> out) {
        List<BigDecimal> closes = closes(bars);
        int n = closes.size();
        if (n < 20) return;

        double sma20 = sma(closes, Math.min(20, n));
        double sma50 = n >= 50 ? sma(closes, 50) : sma(closes, n);
        double diff  = sma20 - sma50;
        double normalised = Math.max(-1.0, Math.min(1.0, diff / sma50 * 20));

        String direction = normalised > 0 ? "BULLISH" : normalised < 0 ? "BEARISH" : "NEUTRAL";
        String desc      = normalised > 0
                ? "SMA20 (" + fmt(sma20) + ") above SMA" + (n >= 50 ? "50" : n)
                  + " (" + fmt(sma50) + ") — uptrend"
                : normalised < 0
                ? "SMA20 (" + fmt(sma20) + ") below SMA" + (n >= 50 ? "50" : n)
                  + " (" + fmt(sma50) + ") — downtrend"
                : "SMA20 and longer-period SMA are flat — sideways";

        out.add(new WeightedSignal("TREND_DIRECTION", direction, normalised, 0.30, desc,
                "Moving-average crossover (SMA20 vs SMA" + (n >= 50 ? "50" : n) + ")",
                "MEDIUM_TERM"));
    }

    /** Close-to-close momentum over last 10 bars (weight 0.25). */
    private void scoreMomentum(List<NormalizedOhlcvBar> bars, List<WeightedSignal> out) {
        List<BigDecimal> closes = closes(bars);
        int n = closes.size();
        int window = Math.min(10, n - 1);
        if (window < 1) return;

        double current = closes.get(n - 1).doubleValue();
        double past    = closes.get(n - 1 - window).doubleValue();
        if (past == 0) return;

        double pctChange  = (current - past) / past * 100.0;
        double normalised = Math.max(-1.0, Math.min(1.0, pctChange / 10.0)); // cap at ±10 %

        String direction = normalised > 0 ? "BULLISH" : normalised < 0 ? "BEARISH" : "NEUTRAL";
        String desc = String.format("Price changed %.2f%% over %d bars (%s → %s)",
                pctChange, window, fmt(past), fmt(current));

        out.add(new WeightedSignal("MOMENTUM", direction, normalised, 0.25, desc,
                "Recent " + window + "-bar close-to-close price change",
                "SHORT_TERM"));
    }

    /** Volume surge vs. 20-bar average (weight 0.15). */
    private void scoreVolume(List<NormalizedOhlcvBar> bars, List<WeightedSignal> out) {
        int n = bars.size();
        if (n < 2) return;

        double lastVol = volume(bars.get(n - 1));
        if (lastVol <= 0) return;

        int window  = Math.min(20, n - 1);
        double avgVol = 0;
        for (int i = n - 1 - window; i < n - 1; i++) avgVol += volume(bars.get(i));
        avgVol /= window;
        if (avgVol <= 0) return;

        double ratio      = lastVol / avgVol;
        // >2× = strong surge (+1), <0.5× = shrinkage (−0.5), neutral otherwise
        double normalised = ratio >= 2.0 ? Math.min(1.0, (ratio - 1) / 2.0)
                          : ratio <= 0.5 ? -0.5
                          : 0.0;

        // Bias volume in the direction of the latest bar's price move
        BigDecimal open  = bars.get(n - 1).getOpen();
        BigDecimal close = bars.get(n - 1).getClose();
        if (open != null && close != null && close.compareTo(open) < 0) normalised = -normalised;

        String direction = normalised > 0 ? "BULLISH" : normalised < 0 ? "BEARISH" : "NEUTRAL";
        out.add(new WeightedSignal("VOLUME_SURGE", direction, normalised, 0.15,
                String.format("Volume ratio vs %d-bar avg: %.2f×", window, ratio),
                "Volume confirms or contradicts price action",
                "SHORT_TERM"));
    }

    /** News sentiment from pre-scored articles (weight 0.30). */
    private void scoreNewsSentiment(List<NewsArticleDto> news, List<WeightedSignal> out) {
        if (news == null || news.isEmpty()) return;

        double sum   = 0;
        int    count = 0;
        for (NewsArticleDto a : news) {
            Double score = a.getSentimentScore();
            if (score != null) {
                sum += score;
                count++;
            } else {
                // fall back to label-based heuristic
                String label = a.getSentimentLabel() != null ? a.getSentimentLabel().toUpperCase() : "";
                if (label.contains("BULLISH") || label.contains("POSITIVE")) { sum += 0.5; count++; }
                else if (label.contains("BEARISH") || label.contains("NEGATIVE")) { sum -= 0.5; count++; }
                else if (!label.isEmpty()) { count++; }
                else {
                    // no sentiment data — try title heuristic
                    String title = a.getTitle() != null ? a.getTitle().toLowerCase() : "";
                    if (!title.isBlank()) {
                        long bull = java.util.Arrays.stream(new String[]{
                                "surge", "rally", "gain", "beat", "upgrade", "positive", "rise", "soar"
                        }).filter(title::contains).count();
                        long bear = java.util.Arrays.stream(new String[]{
                                "drop", "fall", "decline", "miss", "downgrade", "negative", "crash", "warn"
                        }).filter(title::contains).count();
                        if (bull > bear) { sum += 0.4; count++; }
                        else if (bear > bull) { sum -= 0.4; count++; }
                    }
                }
            }
        }
        if (count == 0) return;

        double avgSentiment = sum / count;
        double normalised   = Math.max(-1.0, Math.min(1.0, avgSentiment));
        String direction    = normalised > 0.05 ? "BULLISH"
                            : normalised < -0.05 ? "BEARISH" : "NEUTRAL";

        out.add(new WeightedSignal("NEWS_SENTIMENT", direction, normalised, 0.30,
                String.format("News sentiment composite from %d article(s): %.2f", count, avgSentiment),
                "Aggregated news/headline sentiment scoring",
                "SHORT_TERM"));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static String toRecommendation(double score) {
        if (score >= 0.6)  return "STRONG_BUY";
        if (score >= 0.2)  return "BUY";
        if (score <= -0.6) return "STRONG_SELL";
        if (score <= -0.2) return "SELL";
        return "NEUTRAL";
    }

    private static List<BigDecimal> closes(List<NormalizedOhlcvBar> bars) {
        return bars.stream()
                .map(NormalizedOhlcvBar::getClose)
                .filter(c -> c != null && c.compareTo(BigDecimal.ZERO) > 0)
                .toList();
    }

    private static double sma(List<BigDecimal> closes, int period) {
        int n = closes.size();
        return closes.subList(n - period, n).stream()
                .mapToDouble(BigDecimal::doubleValue).average().orElse(0);
    }

    private static double volume(NormalizedOhlcvBar bar) {
        return bar.getVolume() != null ? bar.getVolume().doubleValue() : 0;
    }

    private static String fmt(double v) { return String.format("%.4g", v); }

    private static AiSignalDto toDto(WeightedSignal s, String symbol) {
        return AiSignalDto.builder()
                .signalType(s.type())
                .direction(s.direction())
                .strength(Math.abs(s.score()))
                .description(s.description())
                .rationale(s.rationale())
                .timeHorizon(s.timeHorizon())
                .generatedAt(Instant.now())
                .build();
    }

    // ── Inner types ────────────────────────────────────────────────────────────

    private record WeightedSignal(
            String type,
            String direction,
            double score,       // [-1, +1]
            double weight,
            String description,
            String rationale,
            String timeHorizon
    ) {}
}
