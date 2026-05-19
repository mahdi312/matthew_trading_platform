package com.mst.matt.tradingplatformapp.service.analysis;


import com.mst.matt.tradingplatformapp.model.IndicatorConfig;
import com.mst.matt.tradingplatformapp.service.analysis.SupportResistanceService.SRResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Composite signal scoring engine.
 *
 * Aggregates signals from all active indicators using user-configured weights.
 * Produces a final score → BUY/SELL/NEUTRAL recommendation with confidence %.
 *
 * Score formula:
 *   compositeScore = Σ (signal_i × weight_i) / Σ weight_i
 *   confidence%    = |compositeScore| × 100
 *
 * Scoring thresholds:
 *   score ≥ +0.6  → STRONG BUY  🟢
 *   score ≥ +0.2  → BUY         🟡
 *  -0.2 < s < 0.2 → NEUTRAL     ⚪
 *   score ≤ -0.2  → SELL        🟠
 *   score ≤ -0.6  → STRONG SELL 🔴
 */
@Service
public class SignalScoringService {

    private static final Logger log = LoggerFactory.getLogger(SignalScoringService.class);

    /**
     * Computes the composite trading signal.
     *
     * @param indicators Computed indicator values
     * @param srResult   Support/resistance analysis
     * @param config     User's indicator weights
     * @param currentPrice Current asset price
     */
    public SignalResult score(IndicatorResult indicators,
                              SRResult srResult,
                              IndicatorConfig config,
                              double currentPrice) {

        List<WeightedSignal> signals = new ArrayList<>();

        // ── EMA Cross ─────────────────────────────────────────
        if (config.isEmaEnabled() && config.getEmaWeight() > 0) {
            signals.add(new WeightedSignal("EMA Cross",
                    indicators.getEmaCrossSignal(), config.getEmaWeight()));
        }

        // ── Golden/Death Cross ───────────────────────────────
        if (config.isEmaEnabled() && config.getEmaWeight() > 0) {
            signals.add(new WeightedSignal("Golden/Death Cross",
                    indicators.getGoldenDeathCrossSignal(),
                    config.getEmaWeight() / 2));  // Half weight for trend confirmation
        }

        // ── MACD Cross ───────────────────────────────────────
        if (config.isMacdEnabled() && config.getMacdWeight() > 0) {
            signals.add(new WeightedSignal("MACD",
                    indicators.getMacdCrossSignal(), config.getMacdWeight()));
            // MACD histogram direction (positive = bullish momentum)
            int histSig = Double.isNaN(indicators.getMacdHistogram()) ? 0
                    : indicators.getMacdHistogram() > 0 ? +1 : -1;
            signals.add(new WeightedSignal("MACD Histogram",
                    histSig, config.getMacdWeight() / 2));
        }

        // ── RSI ──────────────────────────────────────────────
        if (config.isRsiEnabled() && config.getRsiWeight() > 0) {
            signals.add(new WeightedSignal("RSI",
                    indicators.getRsiSignal(), config.getRsiWeight()));
        }

        // ── Bollinger Bands ───────────────────────────────────
        if (config.isBollingerEnabled() && config.getBollingerWeight() > 0) {
            signals.add(new WeightedSignal("Bollinger Bands",
                    indicators.getBollingerSignal(), config.getBollingerWeight()));
        }

        // ── Stochastic ────────────────────────────────────────
        if (config.isStochasticEnabled() && config.getStochasticWeight() > 0) {
            signals.add(new WeightedSignal("Stochastic",
                    indicators.getStochasticSignal(), config.getStochasticWeight()));
        }

        // ── CCI ───────────────────────────────────────────────
        if (config.isCciEnabled() && config.getCciWeight() > 0) {
            signals.add(new WeightedSignal("CCI",
                    indicators.getCciSignal(), config.getCciWeight()));
        }

        // ── VWAP ──────────────────────────────────────────────
        if (config.isVwapEnabled() && config.getVwapWeight() > 0) {
            signals.add(new WeightedSignal("VWAP",
                    indicators.getVwapSignal(), config.getVwapWeight()));
        }

        // ── Ichimoku ──────────────────────────────────────────
        if (config.isIchimokuEnabled() && config.getIchimokuWeight() > 0
                && indicators.getIchimoku() != null) {
            signals.add(new WeightedSignal("Ichimoku Cloud",
                    indicators.getIchimoku().getSignal(), config.getIchimokuWeight()));
        }

        // ── Fibonacci S/R proximity ───────────────────────────
        if (config.isFibonacciEnabled() && config.getFibonacciWeight() > 0
                && srResult != null) {
            int fibSig = computeFibSignal(currentPrice, srResult);
            signals.add(new WeightedSignal("Fibonacci",
                    fibSig, config.getFibonacciWeight()));
        }

        // ── Compute composite score ───────────────────────────
        double weightedSum  = 0;
        double totalWeight  = 0;

        for (WeightedSignal ws : signals) {
            weightedSum += ws.signal() * ws.weight();
            totalWeight += ws.weight();
        }

        double compositeScore = (totalWeight == 0) ? 0
                : weightedSum / totalWeight;
        double confidence     = (totalWeight == 0) ? 0
                : Math.abs(compositeScore) * 100;

        // ── Determine recommendation ──────────────────────────
        Recommendation rec;
        if      (compositeScore >= 0.6)  rec = Recommendation.STRONG_BUY;
        else if (compositeScore >= 0.2)  rec = Recommendation.BUY;
        else if (compositeScore <= -0.6) rec = Recommendation.STRONG_SELL;
        else if (compositeScore <= -0.2) rec = Recommendation.SELL;
        else                             rec = Recommendation.NEUTRAL;

        // ── Best price suggestions ────────────────────────────
        double bestBuy  = (srResult != null) ? srResult.getBestBuyPrice()  : currentPrice;
        double bestSell = (srResult != null) ? srResult.getBestSellPrice() : currentPrice;

        // Refine buy suggestion: nearest support with bullish confluence
        if (srResult != null && !srResult.getSupports().isEmpty()) {
            bestBuy = srResult.getSupports().stream()
                    .filter(s -> s.price() < currentPrice * 0.995) // at least 0.5% below
                    .mapToDouble(SupportResistanceService.SRLevel::price)
                    .max().orElse(bestBuy);
        }

        // Refine sell suggestion: nearest resistance with bearish confluence
        if (srResult != null && !srResult.getResistances().isEmpty()) {
            bestSell = srResult.getResistances().stream()
                    .filter(r -> r.price() > currentPrice * 1.005)
                    .mapToDouble(SupportResistanceService.SRLevel::price)
                    .min().orElse(bestSell);
        }

        log.debug("Signal for score={} rec={} conf={}%",
                compositeScore, rec, confidence);

        return SignalResult.builder()
                .compositeScore(compositeScore)
                .confidence(confidence)
                .recommendation(rec)
                .individualSignals(signals)
                .bestBuyPrice(round(bestBuy, 8))
                .bestSellPrice(round(bestSell, 8))
                .bullishCount((int) signals.stream()
                        .filter(s -> s.signal() > 0).count())
                .bearishCount((int) signals.stream()
                        .filter(s -> s.signal() < 0).count())
                .neutralCount((int) signals.stream()
                        .filter(s -> s.signal() == 0).count())
                .build();
    }

    private int computeFibSignal(double price,
                                 SRResult sr) {
        // Near a Fibonacci support → bullish
        boolean nearSupport = sr.getSupports().stream()
                .filter(s -> s.source() ==
                        SupportResistanceService.SRLevel.Source.FIBONACCI)
                .anyMatch(s -> Math.abs(s.price() - price) / price < 0.005);

        // Near a Fibonacci resistance → bearish
        boolean nearResistance = sr.getResistances().stream()
                .filter(r -> r.source() ==
                        SupportResistanceService.SRLevel.Source.FIBONACCI)
                .anyMatch(r -> Math.abs(r.price() - price) / price < 0.005);

        if (nearSupport)    return +1;
        if (nearResistance) return -1;
        return 0;
    }

    private double round(double val, int scale) {
        try {
            return new BigDecimal(val)
                    .setScale(scale, RoundingMode.HALF_UP)
                    .doubleValue();
        } catch (Exception e) { return val; }
    }

    // ── Result DTOs ──────────────────────────────────────────

    public enum Recommendation {
        STRONG_BUY("🟢 STRONG BUY",  "#1a4a1a", "#3fb950"),
        BUY       ("🟡 BUY",          "#1a3a1a", "#7ee787"),
        NEUTRAL   ("⚪ NEUTRAL",      "#2d333b", "#8b949e"),
        SELL      ("🟠 SELL",         "#3a2020", "#ffa198"),
        STRONG_SELL("🔴 STRONG SELL", "#4a1a1a", "#f85149");

        public final String label;
        public final String bgColor;
        public final String textColor;

        Recommendation(String label, String bg, String text) {
            this.label = label; this.bgColor = bg; this.textColor = text;
        }
    }

    public record WeightedSignal(String name, int signal, int weight) {}

    @lombok.Data @lombok.Builder
    @lombok.NoArgsConstructor @lombok.AllArgsConstructor
    public static class SignalResult {
        private double compositeScore;
        private double confidence;
        private Recommendation recommendation;
        private List<WeightedSignal> individualSignals;
        private double bestBuyPrice;
        private double bestSellPrice;
        private int bullishCount;
        private int bearishCount;
        private int neutralCount;
    }
}