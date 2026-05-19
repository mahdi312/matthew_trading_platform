package com.mst.matt.tradingplatformapp.service.analysis;

import lombok.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds computed values for ALL indicators at the current bar.
 * Both single values (for signal scoring) and full series (for chart rendering).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndicatorResult {

    // ── EMA ──────────────────────────────────────────────────
    @Builder.Default private double  emaFast = Double.NaN;
    @Builder.Default private double  emaSlow = Double.NaN;
    @Builder.Default private double  ema50   = Double.NaN;
    @Builder.Default private double  ema200  = Double.NaN;
    @Builder.Default private int     emaCrossSignal       = 0; // +1/0/-1
    @Builder.Default private int     goldenDeathCrossSignal = 0;
    @Builder.Default private List<Double> emaFastSeries  = new ArrayList<>();
    @Builder.Default private List<Double> emaSlowSeries  = new ArrayList<>();
    @Builder.Default private List<Double> ema50Series    = new ArrayList<>();
    @Builder.Default private List<Double> ema200Series   = new ArrayList<>();

    // ── MACD ──────────────────────────────────────────────────
    @Builder.Default private double  macdLine      = Double.NaN;
    @Builder.Default private double  macdSignal    = Double.NaN;
    @Builder.Default private double  macdHistogram = Double.NaN;
    @Builder.Default private int     macdCrossSignal = 0;
    @Builder.Default private List<Double> macdLineSeries      = new ArrayList<>();
    @Builder.Default private List<Double> macdSignalSeries    = new ArrayList<>();
    @Builder.Default private List<Double> macdHistogramSeries = new ArrayList<>();

    // ── RSI ───────────────────────────────────────────────────
    @Builder.Default private double  rsi       = Double.NaN;
    @Builder.Default private int     rsiSignal = 0;
    @Builder.Default private List<Double> rsiSeries = new ArrayList<>();

    // ── Bollinger Bands ───────────────────────────────────────
    @Builder.Default private double  bbUpper     = Double.NaN;
    @Builder.Default private double  bbMiddle    = Double.NaN;
    @Builder.Default private double  bbLower     = Double.NaN;
    @Builder.Default private double  bbBandwidth = Double.NaN;
    @Builder.Default private int     bollingerSignal = 0;
    @Builder.Default private List<Double> bbUpperSeries  = new ArrayList<>();
    @Builder.Default private List<Double> bbMiddleSeries = new ArrayList<>();
    @Builder.Default private List<Double> bbLowerSeries  = new ArrayList<>();

    // ── Stochastic ────────────────────────────────────────────
    @Builder.Default private double  stochasticK      = Double.NaN;
    @Builder.Default private double  stochasticD      = Double.NaN;
    @Builder.Default private int     stochasticSignal = 0;
    @Builder.Default private List<Double> stochasticKSeries = new ArrayList<>();
    @Builder.Default private List<Double> stochasticDSeries = new ArrayList<>();

    // ── ATR ───────────────────────────────────────────────────
    @Builder.Default private double  atr = Double.NaN;
    @Builder.Default private List<Double> atrSeries = new ArrayList<>();

    // ── CCI ───────────────────────────────────────────────────
    @Builder.Default private double  cci       = Double.NaN;
    @Builder.Default private int     cciSignal = 0;
    @Builder.Default private List<Double> cciSeries = new ArrayList<>();

    // ── VWAP ──────────────────────────────────────────────────
    @Builder.Default private double  vwap       = Double.NaN;
    @Builder.Default private int     vwapSignal = 0;
    @Builder.Default private List<Double> vwapSeries = new ArrayList<>();

    // ── Ichimoku ──────────────────────────────────────────────
    @Builder.Default private IchimokuResult ichimoku = new IchimokuResult();

    public static IndicatorResult empty() {
        return IndicatorResult.builder().build();
    }
}