package com.mst.matt.tradingplatformapp.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Per-profile indicator configuration.
 * Controls which indicators are active and their weight in the
 * composite BUY/SELL signal scoring engine.
 */
@Entity
@Table(name = "indicator_configs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IndicatorConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false, unique = true)
    private UserProfile profile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IndicatorProfile activeProfile;  // SWING_TRADING, SCALPING, etc.

    // ── MACD ────────────────────────────────────────
    private boolean macdEnabled;
    private int macdWeight;           // 0–10

    // ── RSI ─────────────────────────────────────────
    private boolean rsiEnabled;
    private int rsiWeight;
    private int rsiPeriod;            // default 14
    private int rsiOverbought;        // default 70
    private int rsiOversold;          // default 30

    // ── Ichimoku ─────────────────────────────────────
    private boolean ichimokuEnabled;
    private int ichimokuWeight;
    private int ichimokuTenkanPeriod;   // default 9
    private int ichimokuKijunPeriod;    // default 26
    private int ichimokuSenkouPeriod;   // default 52

    // ── Moving Averages ──────────────────────────────
    private boolean emaEnabled;
    private int emaWeight;
    private int emaFastPeriod;        // default 12
    private int emaSlowPeriod;        // default 26
    private int goldCrossShortPeriod; // default 50
    private int goldCrossLongPeriod;  // default 200

    // ── Bollinger Bands ──────────────────────────────
    private boolean bollingerEnabled;
    private int bollingerWeight;
    private int bollingerPeriod;      // default 20
    private double bollingerDeviation;// default 2.0

    // ── Fibonacci ────────────────────────────────────
    private boolean fibonacciEnabled;
    private int fibonacciWeight;
    private int fibonacciLookback;    // default 50 bars

    // ── Stochastic ───────────────────────────────────
    private boolean stochasticEnabled;
    private int stochasticWeight;
    private int stochasticKPeriod;    // default 14
    private int stochasticDPeriod;    // default 3

    // ── ATR (Average True Range) ─────────────────────
    private boolean atrEnabled;
    private int atrPeriod;            // default 14

    // ── VWAP ─────────────────────────────────────────
    private boolean vwapEnabled;
    private int vwapWeight;

    // ── CCI ──────────────────────────────────────────
    private boolean cciEnabled;
    private int cciWeight;
    private int cciPeriod;            // default 20

    public enum IndicatorProfile {
        SWING_TRADING,
        SCALPING,
        DAY_TRADING,
        LONG_TERM,
        CRYPTO_MOMENTUM,
        CONSERVATIVE,
        CUSTOM
    }

    /**
     * Factory: returns a preset config for the given profile type.
     */
    public static IndicatorConfig fromProfile(IndicatorProfile profile, UserProfile user) {
        IndicatorConfig cfg = IndicatorConfig.builder()
                .profile(user)
                .activeProfile(profile)
                .build();
        switch (profile) {
            case SWING_TRADING -> {
                cfg.setMacdEnabled(true);    cfg.setMacdWeight(8);
                cfg.setRsiEnabled(true);     cfg.setRsiWeight(7);   cfg.setRsiPeriod(14); cfg.setRsiOverbought(70); cfg.setRsiOversold(30);
                cfg.setIchimokuEnabled(true);cfg.setIchimokuWeight(9);
                cfg.setIchimokuTenkanPeriod(9); cfg.setIchimokuKijunPeriod(26); cfg.setIchimokuSenkouPeriod(52);
                cfg.setEmaEnabled(true);     cfg.setEmaWeight(6);   cfg.setEmaFastPeriod(12); cfg.setEmaSlowPeriod(26);
                cfg.setGoldCrossShortPeriod(50); cfg.setGoldCrossLongPeriod(200);
                cfg.setBollingerEnabled(true); cfg.setBollingerWeight(5); cfg.setBollingerPeriod(20); cfg.setBollingerDeviation(2.0);
                cfg.setFibonacciEnabled(true); cfg.setFibonacciWeight(8); cfg.setFibonacciLookback(50);
                cfg.setStochasticEnabled(false); cfg.setStochasticWeight(0);
                cfg.setStochasticKPeriod(14); cfg.setStochasticDPeriod(3);
                cfg.setAtrEnabled(true);     cfg.setAtrPeriod(14);
                cfg.setVwapEnabled(true);    cfg.setVwapWeight(6);
                cfg.setCciEnabled(false);    cfg.setCciWeight(0);   cfg.setCciPeriod(20);
            }
            case SCALPING -> {
                cfg.setMacdEnabled(true);    cfg.setMacdWeight(6);
                cfg.setRsiEnabled(true);     cfg.setRsiWeight(9);   cfg.setRsiPeriod(7); cfg.setRsiOverbought(80); cfg.setRsiOversold(20);
                cfg.setIchimokuEnabled(false);cfg.setIchimokuWeight(0);
                cfg.setIchimokuTenkanPeriod(9); cfg.setIchimokuKijunPeriod(26); cfg.setIchimokuSenkouPeriod(52);
                cfg.setEmaEnabled(true);     cfg.setEmaWeight(9);   cfg.setEmaFastPeriod(5); cfg.setEmaSlowPeriod(13);
                cfg.setGoldCrossShortPeriod(20); cfg.setGoldCrossLongPeriod(50);
                cfg.setBollingerEnabled(true); cfg.setBollingerWeight(8); cfg.setBollingerPeriod(10); cfg.setBollingerDeviation(2.0);
                cfg.setFibonacciEnabled(false); cfg.setFibonacciWeight(0); cfg.setFibonacciLookback(20);
                cfg.setStochasticEnabled(true); cfg.setStochasticWeight(8);
                cfg.setStochasticKPeriod(5); cfg.setStochasticDPeriod(3);
                cfg.setAtrEnabled(true);     cfg.setAtrPeriod(7);
                cfg.setVwapEnabled(true);    cfg.setVwapWeight(9);
                cfg.setCciEnabled(true);     cfg.setCciWeight(7);   cfg.setCciPeriod(14);
            }
            case CRYPTO_MOMENTUM -> {
                cfg.setMacdEnabled(true);    cfg.setMacdWeight(9);
                cfg.setRsiEnabled(true);     cfg.setRsiWeight(8);   cfg.setRsiPeriod(14); cfg.setRsiOverbought(75); cfg.setRsiOversold(25);
                cfg.setIchimokuEnabled(true);cfg.setIchimokuWeight(7);
                cfg.setIchimokuTenkanPeriod(9); cfg.setIchimokuKijunPeriod(26); cfg.setIchimokuSenkouPeriod(52);
                cfg.setEmaEnabled(true);     cfg.setEmaWeight(8);   cfg.setEmaFastPeriod(8); cfg.setEmaSlowPeriod(21);
                cfg.setGoldCrossShortPeriod(50); cfg.setGoldCrossLongPeriod(200);
                cfg.setBollingerEnabled(true); cfg.setBollingerWeight(6); cfg.setBollingerPeriod(20); cfg.setBollingerDeviation(2.5);
                cfg.setFibonacciEnabled(true); cfg.setFibonacciWeight(7); cfg.setFibonacciLookback(60);
                cfg.setStochasticEnabled(true); cfg.setStochasticWeight(5);
                cfg.setStochasticKPeriod(14); cfg.setStochasticDPeriod(3);
                cfg.setAtrEnabled(true);     cfg.setAtrPeriod(14);
                cfg.setVwapEnabled(false);   cfg.setVwapWeight(0);
                cfg.setCciEnabled(true);     cfg.setCciWeight(6);   cfg.setCciPeriod(20);
            }
            case DAY_TRADING -> {
                cfg.setMacdEnabled(true);    cfg.setMacdWeight(8);
                cfg.setRsiEnabled(true);     cfg.setRsiWeight(8);   cfg.setRsiPeriod(14); cfg.setRsiOverbought(70); cfg.setRsiOversold(30);
                cfg.setIchimokuEnabled(false); cfg.setIchimokuWeight(0);
                cfg.setIchimokuTenkanPeriod(9); cfg.setIchimokuKijunPeriod(26); cfg.setIchimokuSenkouPeriod(52);
                cfg.setEmaEnabled(true);     cfg.setEmaWeight(9);   cfg.setEmaFastPeriod(9); cfg.setEmaSlowPeriod(21);
                cfg.setGoldCrossShortPeriod(50); cfg.setGoldCrossLongPeriod(200);
                cfg.setBollingerEnabled(true); cfg.setBollingerWeight(7); cfg.setBollingerPeriod(20); cfg.setBollingerDeviation(2.0);
                cfg.setFibonacciEnabled(true); cfg.setFibonacciWeight(6); cfg.setFibonacciLookback(30);
                cfg.setStochasticEnabled(true); cfg.setStochasticWeight(7);
                cfg.setStochasticKPeriod(14); cfg.setStochasticDPeriod(3);
                cfg.setAtrEnabled(true);     cfg.setAtrPeriod(14);
                cfg.setVwapEnabled(true);    cfg.setVwapWeight(9);
                cfg.setCciEnabled(true);     cfg.setCciWeight(6);   cfg.setCciPeriod(20);
            }
            case LONG_TERM -> {
                cfg.setMacdEnabled(true);    cfg.setMacdWeight(6);
                cfg.setRsiEnabled(true);     cfg.setRsiWeight(5);   cfg.setRsiPeriod(21); cfg.setRsiOverbought(70); cfg.setRsiOversold(30);
                cfg.setIchimokuEnabled(true);cfg.setIchimokuWeight(8);
                cfg.setIchimokuTenkanPeriod(9); cfg.setIchimokuKijunPeriod(26); cfg.setIchimokuSenkouPeriod(52);
                cfg.setEmaEnabled(true);     cfg.setEmaWeight(9);   cfg.setEmaFastPeriod(50); cfg.setEmaSlowPeriod(200);
                cfg.setGoldCrossShortPeriod(50); cfg.setGoldCrossLongPeriod(200);
                cfg.setBollingerEnabled(false); cfg.setBollingerWeight(0); cfg.setBollingerPeriod(20); cfg.setBollingerDeviation(2.0);
                cfg.setFibonacciEnabled(true); cfg.setFibonacciWeight(9); cfg.setFibonacciLookback(100);
                cfg.setStochasticEnabled(false); cfg.setStochasticWeight(0);
                cfg.setStochasticKPeriod(14); cfg.setStochasticDPeriod(3);
                cfg.setAtrEnabled(true);     cfg.setAtrPeriod(21);
                cfg.setVwapEnabled(false);   cfg.setVwapWeight(0);
                cfg.setCciEnabled(false);    cfg.setCciWeight(0);   cfg.setCciPeriod(20);
            }
            case CONSERVATIVE -> {
                cfg.setMacdEnabled(true);    cfg.setMacdWeight(7);
                cfg.setRsiEnabled(true);     cfg.setRsiWeight(9);   cfg.setRsiPeriod(14); cfg.setRsiOverbought(65); cfg.setRsiOversold(35);
                cfg.setIchimokuEnabled(true);cfg.setIchimokuWeight(8);
                cfg.setIchimokuTenkanPeriod(9); cfg.setIchimokuKijunPeriod(26); cfg.setIchimokuSenkouPeriod(52);
                cfg.setEmaEnabled(true);     cfg.setEmaWeight(7);   cfg.setEmaFastPeriod(20); cfg.setEmaSlowPeriod(50);
                cfg.setGoldCrossShortPeriod(50); cfg.setGoldCrossLongPeriod(200);
                cfg.setBollingerEnabled(true); cfg.setBollingerWeight(8); cfg.setBollingerPeriod(20); cfg.setBollingerDeviation(2.0);
                cfg.setFibonacciEnabled(true); cfg.setFibonacciWeight(8); cfg.setFibonacciLookback(50);
                cfg.setStochasticEnabled(false); cfg.setStochasticWeight(0);
                cfg.setStochasticKPeriod(14); cfg.setStochasticDPeriod(3);
                cfg.setAtrEnabled(true);     cfg.setAtrPeriod(14);
                cfg.setVwapEnabled(true);    cfg.setVwapWeight(5);
                cfg.setCciEnabled(false);    cfg.setCciWeight(0);   cfg.setCciPeriod(20);
            }
            default -> { /* CUSTOM: all indicators disabled, user configures manually */ }
        }
        return cfg;
    }
}