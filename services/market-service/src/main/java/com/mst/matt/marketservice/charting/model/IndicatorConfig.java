package com.mst.matt.marketservice.charting.model;

import jakarta.persistence.*;
import lombok.*;

/**
 * Per-user indicator configuration.
 *
 * <p>Adapted from desktop {@code model.IndicatorConfig}:
 * {@code UserProfile profile} → {@code Long userId}.
 * One row per user (unique on user_id).
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

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IndicatorProfile activeProfile;

    // ── MACD ──────────────────────────────────────────────────────────────────
    private boolean macdEnabled;
    private int     macdWeight;

    // ── RSI ───────────────────────────────────────────────────────────────────
    private boolean rsiEnabled;
    private int     rsiWeight;
    private int     rsiPeriod;
    private int     rsiOverbought;
    private int     rsiOversold;

    // ── Ichimoku ──────────────────────────────────────────────────────────────
    private boolean ichimokuEnabled;
    private int     ichimokuWeight;
    private int     ichimokuTenkanPeriod;
    private int     ichimokuKijunPeriod;
    private int     ichimokuSenkouPeriod;

    // ── Moving Averages ───────────────────────────────────────────────────────
    private boolean emaEnabled;
    private int     emaWeight;
    private int     emaFastPeriod;
    private int     emaSlowPeriod;
    private int     goldCrossShortPeriod;
    private int     goldCrossLongPeriod;

    // ── Bollinger Bands ───────────────────────────────────────────────────────
    private boolean bollingerEnabled;
    private int     bollingerWeight;
    private int     bollingerPeriod;
    private double  bollingerDeviation;

    // ── Fibonacci ─────────────────────────────────────────────────────────────
    private boolean fibonacciEnabled;
    private int     fibonacciWeight;
    private int     fibonacciLookback;

    // ── Stochastic ────────────────────────────────────────────────────────────
    private boolean stochasticEnabled;
    private int     stochasticWeight;
    private int     stochasticKPeriod;
    private int     stochasticDPeriod;

    // ── ATR ───────────────────────────────────────────────────────────────────
    private boolean atrEnabled;
    private int     atrPeriod;

    // ── VWAP ──────────────────────────────────────────────────────────────────
    private boolean vwapEnabled;
    private int     vwapWeight;

    // ── CCI ───────────────────────────────────────────────────────────────────
    private boolean cciEnabled;
    private int     cciWeight;
    private int     cciPeriod;

    // ── Enums & factory ───────────────────────────────────────────────────────

    public enum IndicatorProfile {
        SWING_TRADING, SCALPING, DAY_TRADING, LONG_TERM,
        CRYPTO_MOMENTUM, CONSERVATIVE, CUSTOM
    }

    /** Returns a preset config for the given profile type (without a userId — caller must set). */
    public static IndicatorConfig fromProfile(IndicatorProfile profile) {
        IndicatorConfig c = IndicatorConfig.builder()
                .activeProfile(profile).build();
        switch (profile) {
            case SWING_TRADING -> {
                c.setMacdEnabled(true);      c.setMacdWeight(8);
                c.setRsiEnabled(true);       c.setRsiWeight(7);   c.setRsiPeriod(14); c.setRsiOverbought(70); c.setRsiOversold(30);
                c.setIchimokuEnabled(true);  c.setIchimokuWeight(9);
                c.setIchimokuTenkanPeriod(9); c.setIchimokuKijunPeriod(26); c.setIchimokuSenkouPeriod(52);
                c.setEmaEnabled(true);       c.setEmaWeight(6);   c.setEmaFastPeriod(12); c.setEmaSlowPeriod(26);
                c.setGoldCrossShortPeriod(50); c.setGoldCrossLongPeriod(200);
                c.setBollingerEnabled(true); c.setBollingerWeight(5); c.setBollingerPeriod(20); c.setBollingerDeviation(2.0);
                c.setFibonacciEnabled(true); c.setFibonacciWeight(8); c.setFibonacciLookback(50);
                c.setStochasticEnabled(false); c.setStochasticWeight(0); c.setStochasticKPeriod(14); c.setStochasticDPeriod(3);
                c.setAtrEnabled(true);       c.setAtrPeriod(14);
                c.setVwapEnabled(true);      c.setVwapWeight(6);
                c.setCciEnabled(false);      c.setCciWeight(0);   c.setCciPeriod(20);
            }
            case SCALPING -> {
                c.setMacdEnabled(true);      c.setMacdWeight(6);
                c.setRsiEnabled(true);       c.setRsiWeight(9);   c.setRsiPeriod(7); c.setRsiOverbought(80); c.setRsiOversold(20);
                c.setIchimokuEnabled(false); c.setIchimokuWeight(0);
                c.setIchimokuTenkanPeriod(9); c.setIchimokuKijunPeriod(26); c.setIchimokuSenkouPeriod(52);
                c.setEmaEnabled(true);       c.setEmaWeight(9);   c.setEmaFastPeriod(5); c.setEmaSlowPeriod(13);
                c.setGoldCrossShortPeriod(20); c.setGoldCrossLongPeriod(50);
                c.setBollingerEnabled(true); c.setBollingerWeight(8); c.setBollingerPeriod(10); c.setBollingerDeviation(2.0);
                c.setFibonacciEnabled(false); c.setFibonacciWeight(0); c.setFibonacciLookback(20);
                c.setStochasticEnabled(true); c.setStochasticWeight(8); c.setStochasticKPeriod(5); c.setStochasticDPeriod(3);
                c.setAtrEnabled(true);       c.setAtrPeriod(7);
                c.setVwapEnabled(true);      c.setVwapWeight(9);
                c.setCciEnabled(true);       c.setCciWeight(7);   c.setCciPeriod(14);
            }
            case CRYPTO_MOMENTUM -> {
                c.setMacdEnabled(true);      c.setMacdWeight(9);
                c.setRsiEnabled(true);       c.setRsiWeight(8);   c.setRsiPeriod(14); c.setRsiOverbought(75); c.setRsiOversold(25);
                c.setIchimokuEnabled(true);  c.setIchimokuWeight(7);
                c.setIchimokuTenkanPeriod(9); c.setIchimokuKijunPeriod(26); c.setIchimokuSenkouPeriod(52);
                c.setEmaEnabled(true);       c.setEmaWeight(8);   c.setEmaFastPeriod(8); c.setEmaSlowPeriod(21);
                c.setGoldCrossShortPeriod(50); c.setGoldCrossLongPeriod(200);
                c.setBollingerEnabled(true); c.setBollingerWeight(6); c.setBollingerPeriod(20); c.setBollingerDeviation(2.5);
                c.setFibonacciEnabled(true); c.setFibonacciWeight(7); c.setFibonacciLookback(60);
                c.setStochasticEnabled(true); c.setStochasticWeight(5); c.setStochasticKPeriod(14); c.setStochasticDPeriod(3);
                c.setAtrEnabled(true);       c.setAtrPeriod(14);
                c.setVwapEnabled(false);     c.setVwapWeight(0);
                c.setCciEnabled(true);       c.setCciWeight(6);   c.setCciPeriod(20);
            }
            case DAY_TRADING -> {
                c.setMacdEnabled(true);      c.setMacdWeight(8);
                c.setRsiEnabled(true);       c.setRsiWeight(8);   c.setRsiPeriod(14); c.setRsiOverbought(70); c.setRsiOversold(30);
                c.setIchimokuEnabled(false); c.setIchimokuWeight(0);
                c.setIchimokuTenkanPeriod(9); c.setIchimokuKijunPeriod(26); c.setIchimokuSenkouPeriod(52);
                c.setEmaEnabled(true);       c.setEmaWeight(9);   c.setEmaFastPeriod(9); c.setEmaSlowPeriod(21);
                c.setGoldCrossShortPeriod(50); c.setGoldCrossLongPeriod(200);
                c.setBollingerEnabled(true); c.setBollingerWeight(7); c.setBollingerPeriod(20); c.setBollingerDeviation(2.0);
                c.setFibonacciEnabled(true); c.setFibonacciWeight(6); c.setFibonacciLookback(30);
                c.setStochasticEnabled(true); c.setStochasticWeight(7); c.setStochasticKPeriod(14); c.setStochasticDPeriod(3);
                c.setAtrEnabled(true);       c.setAtrPeriod(14);
                c.setVwapEnabled(true);      c.setVwapWeight(9);
                c.setCciEnabled(true);       c.setCciWeight(6);   c.setCciPeriod(20);
            }
            case LONG_TERM -> {
                c.setMacdEnabled(true);      c.setMacdWeight(6);
                c.setRsiEnabled(true);       c.setRsiWeight(5);   c.setRsiPeriod(21); c.setRsiOverbought(70); c.setRsiOversold(30);
                c.setIchimokuEnabled(true);  c.setIchimokuWeight(8);
                c.setIchimokuTenkanPeriod(9); c.setIchimokuKijunPeriod(26); c.setIchimokuSenkouPeriod(52);
                c.setEmaEnabled(true);       c.setEmaWeight(9);   c.setEmaFastPeriod(50); c.setEmaSlowPeriod(200);
                c.setGoldCrossShortPeriod(50); c.setGoldCrossLongPeriod(200);
                c.setBollingerEnabled(false); c.setBollingerWeight(0); c.setBollingerPeriod(20); c.setBollingerDeviation(2.0);
                c.setFibonacciEnabled(true); c.setFibonacciWeight(9); c.setFibonacciLookback(100);
                c.setStochasticEnabled(false); c.setStochasticWeight(0); c.setStochasticKPeriod(14); c.setStochasticDPeriod(3);
                c.setAtrEnabled(true);       c.setAtrPeriod(21);
                c.setVwapEnabled(false);     c.setVwapWeight(0);
                c.setCciEnabled(false);      c.setCciWeight(0);   c.setCciPeriod(20);
            }
            case CONSERVATIVE -> {
                c.setMacdEnabled(true);      c.setMacdWeight(7);
                c.setRsiEnabled(true);       c.setRsiWeight(9);   c.setRsiPeriod(14); c.setRsiOverbought(65); c.setRsiOversold(35);
                c.setIchimokuEnabled(true);  c.setIchimokuWeight(8);
                c.setIchimokuTenkanPeriod(9); c.setIchimokuKijunPeriod(26); c.setIchimokuSenkouPeriod(52);
                c.setEmaEnabled(true);       c.setEmaWeight(7);   c.setEmaFastPeriod(20); c.setEmaSlowPeriod(50);
                c.setGoldCrossShortPeriod(50); c.setGoldCrossLongPeriod(200);
                c.setBollingerEnabled(true); c.setBollingerWeight(8); c.setBollingerPeriod(20); c.setBollingerDeviation(2.0);
                c.setFibonacciEnabled(true); c.setFibonacciWeight(8); c.setFibonacciLookback(50);
                c.setStochasticEnabled(false); c.setStochasticWeight(0); c.setStochasticKPeriod(14); c.setStochasticDPeriod(3);
                c.setAtrEnabled(true);       c.setAtrPeriod(14);
                c.setVwapEnabled(true);      c.setVwapWeight(5);
                c.setCciEnabled(false);      c.setCciWeight(0);   c.setCciPeriod(20);
            }
            default -> { /* CUSTOM: all off, user configures manually */ }
        }
        return c;
    }
}
