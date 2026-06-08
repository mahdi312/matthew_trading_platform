package com.mst.matt.tradingplatformapp.service.analysis;


import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.IndicatorConfig;
import org.ta4j.core.*;
import org.ta4j.core.indicators.*;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.bollinger.*;
import org.ta4j.core.indicators.helpers.*;
import org.ta4j.core.indicators.ichimoku.*;
import org.ta4j.core.indicators.statistics.*;
import org.ta4j.core.indicators.volume.VWAPIndicator;
import org.ta4j.core.num.DecimalNum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Computes ALL technical indicators for a given bar series.
 *
 * Returns an IndicatorResult containing the current value of every
 * configured indicator, ready to be consumed by:
 *   - The SignalScoringService (for BUY/SELL recommendation)
 *   - The CandlestickChartController (for overlay rendering)
 */
@Service
public class IndicatorService {

    private static final Logger log = LoggerFactory.getLogger(IndicatorService.class);

    /**
     * Converts a list of OhlcvBars into a ta4j BarSeries.
     */
    public BarSeries toBarSeries(List<OhlcvBar> bars, String name) {
        BaseBarSeries series = new BaseBarSeriesBuilder().withName(name).build();
        series.setMaximumBarCount(2000);

        for (OhlcvBar bar : bars) {
            Duration period = timeframeDuration(bar.getTimeframe());
            ZonedDateTime openZdt = bar.getOpenTime().atZone(ZoneOffset.UTC);
            Instant begin = openZdt.toInstant();
            Instant end = openZdt.plus(period).toInstant();
            series.addBar(new BaseBar(
                    period,
                    begin,
                    end,
                    DecimalNum.valueOf(bar.getOpen()),
                    DecimalNum.valueOf(bar.getHigh()),
                    DecimalNum.valueOf(bar.getLow()),
                    DecimalNum.valueOf(bar.getClose()),
                    DecimalNum.valueOf(bar.getVolume()),
                    DecimalNum.valueOf(0),
                    0L
            ), true);
        }
        return series;
    }

    /** Maps app timeframe labels (e.g. {@code 1h}) to a bar period for ta4j. */
    static Duration timeframeDuration(String timeframe) {
        if (timeframe == null || timeframe.isBlank()) {
            return Duration.ofHours(1);
        }
        return switch (timeframe.toLowerCase()) {
            case "1m"  -> Duration.ofMinutes(1);
            case "3m"  -> Duration.ofMinutes(3);
            case "5m"  -> Duration.ofMinutes(5);
            case "15m" -> Duration.ofMinutes(15);
            case "30m" -> Duration.ofMinutes(30);
            case "1h"  -> Duration.ofHours(1);
            case "2h"  -> Duration.ofHours(2);
            case "4h"  -> Duration.ofHours(4);
            case "6h"  -> Duration.ofHours(6);
            case "8h"  -> Duration.ofHours(8);
            case "12h" -> Duration.ofHours(12);
            case "1d"  -> Duration.ofDays(1);
            case "3d"  -> Duration.ofDays(3);
            case "1w"  -> Duration.ofDays(7);
            case "1mo" -> Duration.ofDays(30);
            default    -> Duration.ofHours(1);
        };
    }

    /**
     * Main entry point: computes all indicators and returns a full result object.
     */
    public IndicatorResult compute(BarSeries series, IndicatorConfig config) {
        if (series == null || series.isEmpty()) return IndicatorResult.empty();

        int last = series.getEndIndex();
        IndicatorResult.IndicatorResultBuilder result = IndicatorResult.builder();

        try {
            // ── Close / High / Low / Volume Helpers ──────────
            ClosePriceIndicator close = new ClosePriceIndicator(series);

            // ── EMA (Fast + Slow) ─────────────────────────────
            if (config.isEmaEnabled()) {
                EMAIndicator emaFast = new EMAIndicator(close, config.getEmaFastPeriod());
                EMAIndicator emaSlow = new EMAIndicator(close, config.getEmaSlowPeriod());
                result.emaFast(toDouble(emaFast.getValue(last)));
                result.emaSlow(toDouble(emaSlow.getValue(last)));
                result.emaCrossSignal(
                        computeCrossSignal(emaFast, emaSlow, last)
                );

                // Golden / Death Cross
                EMAIndicator ema50  = new EMAIndicator(close, config.getGoldCrossShortPeriod());
                EMAIndicator ema200 = new EMAIndicator(close, config.getGoldCrossLongPeriod());
                result.ema50(toDouble(ema50.getValue(last)));
                result.ema200(toDouble(ema200.getValue(last)));
                result.goldenDeathCrossSignal(
                        computeCrossSignal(ema50, ema200, last)
                );

                // Store series for chart overlays
                result.emaFastSeries(extractSeries(emaFast, series));
                result.emaSlowSeries(extractSeries(emaSlow, series));
                result.ema50Series(extractSeries(ema50, series));
                result.ema200Series(extractSeries(ema200, series));
            }

            // ── MACD ─────────────────────────────────────────
            // NOTE: ta4j 0.22.x does NOT have getSignalLine()/getHistogram() on MACDIndicator.
            // Signal line = EMA(9) of MACD; Histogram = MACD − Signal.
            if (config.isMacdEnabled()) {
                MACDIndicator macd = new MACDIndicator(
                        close, config.getEmaFastPeriod(), config.getEmaSlowPeriod());
                Indicator<org.ta4j.core.num.Num> macdSignal = macd.getSignalLine(9);
                Indicator<org.ta4j.core.num.Num> histogram = macd.getHistogram(9);

                result.macdLine(toDouble(macd.getValue(last)));
                result.macdSignal(toDouble(macdSignal.getValue(last)));
                result.macdHistogram(toDouble(histogram.getValue(last)));
                result.macdCrossSignal(
                        computeCrossSignal(macd, macdSignal, last)
                );
                result.macdLineSeries(extractSeries(macd, series));
                result.macdSignalSeries(extractSeries(macdSignal, series));
                result.macdHistogramSeries(extractSeries(histogram, series));
            }

            // ── RSI ───────────────────────────────────────────
            if (config.isRsiEnabled()) {
                RSIIndicator rsi = new RSIIndicator(close, config.getRsiPeriod());
                double rsiVal    = toDouble(rsi.getValue(last));
                result.rsi(rsiVal);
                result.rsiSeries(extractSeries(rsi, series));

                int sig = 0;
                if      (rsiVal <= config.getRsiOversold())   sig = +1; // oversold → buy
                else if (rsiVal >= config.getRsiOverbought()) sig = -1; // overbought → sell
                result.rsiSignal(sig);
            }

            // ── Bollinger Bands ───────────────────────────────
            if (config.isBollingerEnabled()) {
                SMAIndicator sma = new SMAIndicator(close, config.getBollingerPeriod());
                StandardDeviationIndicator sd =
                        new StandardDeviationIndicator(close, config.getBollingerPeriod());
                BollingerBandsMiddleIndicator bbMiddle =
                        new BollingerBandsMiddleIndicator(sma);
                org.ta4j.core.num.Num bbK =
                        DecimalNum.valueOf(config.getBollingerDeviation());
                BollingerBandsUpperIndicator bbUpper =
                        new BollingerBandsUpperIndicator(bbMiddle, sd, bbK);
                BollingerBandsLowerIndicator bbLower =
                        new BollingerBandsLowerIndicator(bbMiddle, sd, bbK);

                double closeVal   = toDouble(close.getValue(last));
                double upperVal   = toDouble(bbUpper.getValue(last));
                double middleVal  = toDouble(bbMiddle.getValue(last));
                double lowerVal   = toDouble(bbLower.getValue(last));

                result.bbUpper(upperVal).bbMiddle(middleVal).bbLower(lowerVal);
                result.bbUpperSeries(extractSeries(bbUpper, series));
                result.bbMiddleSeries(extractSeries(bbMiddle, series));
                result.bbLowerSeries(extractSeries(bbLower, series));

                int sig = 0;
                if      (closeVal <= lowerVal) sig = +1; // price at lower band → buy
                else if (closeVal >= upperVal) sig = -1; // price at upper band → sell
                result.bollingerSignal(sig);

                // Bandwidth (volatility measure)
                double bandwidth = upperVal - lowerVal;
                result.bbBandwidth(bandwidth);
            }

            // ── Stochastic ────────────────────────────────────
            if (config.isStochasticEnabled()) {
                StochasticOscillatorKIndicator stochK =
                        new StochasticOscillatorKIndicator(
                                series, config.getStochasticKPeriod());
                StochasticOscillatorDIndicator stochD =
                        new StochasticOscillatorDIndicator(stochK);

                double kVal = toDouble(stochK.getValue(last));
                double dVal = toDouble(stochD.getValue(last));
                result.stochasticK(kVal).stochasticD(dVal);
                result.stochasticKSeries(extractSeries(stochK, series));
                result.stochasticDSeries(extractSeries(stochD, series));

                int sig = 0;
                if      (kVal < 20 && kVal > dVal) sig = +1; // oversold + K crossing D up
                else if (kVal > 80 && kVal < dVal) sig = -1; // overbought + K crossing D down
                result.stochasticSignal(sig);
            }

            // ── ATR (Average True Range) ─────────────────────
            if (config.isAtrEnabled()) {
                ATRIndicator atr = new ATRIndicator(series, config.getAtrPeriod());
                result.atr(toDouble(atr.getValue(last)));
                result.atrSeries(extractSeries(atr, series));
            }

            // ── CCI (Commodity Channel Index) ─────────────────
            if (config.isCciEnabled()) {
                CCIIndicator cci = new CCIIndicator(series, config.getCciPeriod());
                double cciVal    = toDouble(cci.getValue(last));
                result.cci(cciVal);
                result.cciSeries(extractSeries(cci, series));

                int sig = 0;
                if      (cciVal < -100) sig = +1; // oversold
                else if (cciVal >  100) sig = -1; // overbought
                result.cciSignal(sig);
            }

            // ── VWAP ──────────────────────────────────────────
            if (config.isVwapEnabled()) {
                VWAPIndicator vwap = new VWAPIndicator(series, 14);
                double vwapVal     = toDouble(vwap.getValue(last));
                double closeVal    = toDouble(close.getValue(last));
                result.vwap(vwapVal);
                result.vwapSeries(extractSeries(vwap, series));
                result.vwapSignal(Double.compare(closeVal, vwapVal));
            }

            // ── Ichimoku Cloud ────────────────────────────────
            if (config.isIchimokuEnabled()) {
                IchimokuResult ichimoku = computeIchimoku(
                        series,
                        config.getIchimokuTenkanPeriod(),
                        config.getIchimokuKijunPeriod(),
                        config.getIchimokuSenkouPeriod(),
                        last
                );
                result.ichimoku(ichimoku);
            }

        } catch (Exception e) {
            log.error("Indicator computation error: {}", e.getMessage(), e);
        }

        return result.build();
    }

    // ── Ichimoku Full Computation ────────────────────────────

    private IchimokuResult computeIchimoku(
            BarSeries series, int tenkanPeriod, int kijunPeriod,
            int senkouPeriod, int last) {

        IchimokuTenkanSenIndicator tenkan =
                new IchimokuTenkanSenIndicator(series, tenkanPeriod);
        IchimokuKijunSenIndicator kijun =
                new IchimokuKijunSenIndicator(series, kijunPeriod);
        IchimokuSenkouSpanAIndicator spanA =
                new IchimokuSenkouSpanAIndicator(series, tenkan, kijun, kijunPeriod);
        IchimokuSenkouSpanBIndicator spanB =
                new IchimokuSenkouSpanBIndicator(series, senkouPeriod);
        IchimokuChikouSpanIndicator chikou =
                new IchimokuChikouSpanIndicator(series, kijunPeriod);

        double tenkanVal = toDouble(tenkan.getValue(last));
        double kijunVal  = toDouble(kijun.getValue(last));
        double spanAVal  = toDouble(spanA.getValue(last));
        double spanBVal  = toDouble(spanB.getValue(last));
        double chikouVal = toDouble(chikou.getValue(last));
        double closeVal  = series.getBar(last).getClosePrice().doubleValue();

        // Cloud top and bottom
        double cloudTop    = Math.max(spanAVal, spanBVal);
        double cloudBottom = Math.min(spanAVal, spanBVal);

        // Signal determination
        int signal = 0;
        boolean aboveCloud    = closeVal > cloudTop;
        boolean belowCloud    = closeVal < cloudBottom;
        boolean tenkanAbove   = tenkanVal > kijunVal;
        boolean chikouBullish = chikouVal > series.getBar(
                Math.max(0, last - 26)).getClosePrice().doubleValue();

        if (aboveCloud && tenkanAbove && chikouBullish)       signal = +1; // Strong Bull
        else if (belowCloud && !tenkanAbove && !chikouBullish) signal = -1; // Strong Bear
        else if (aboveCloud)                                   signal =  1; // Weak Bull
        else if (belowCloud)                                   signal = -1; // Weak Bear

        return IchimokuResult.builder()
                .tenkanSen(tenkanVal)
                .kijunSen(kijunVal)
                .senkouSpanA(spanAVal)
                .senkouSpanB(spanBVal)
                .chikouSpan(chikouVal)
                .cloudTop(cloudTop)
                .cloudBottom(cloudBottom)
                .aboveCloud(aboveCloud)
                .belowCloud(belowCloud)
                .signal(signal)
                .tenkanSeries(extractSeries(tenkan, series))
                .kijunSeries(extractSeries(kijun, series))
                .spanASeries(extractSeries(spanA, series))
                .spanBSeries(extractSeries(spanB, series))
                .build();
    }

    // ── Cross Signal Helper ──────────────────────────────────

    /**
     * Returns +1 if fast crossed above slow (bullish),
     * -1 if crossed below (bearish), 0 if no recent cross.
     */
    private int computeCrossSignal(Indicator<org.ta4j.core.num.Num> fast,
                                   Indicator<org.ta4j.core.num.Num> slow, int last) {
        if (last < 1) return 0;
        double fastNow  = toDouble(fast.getValue(last));
        double slowNow  = toDouble(slow.getValue(last));
        double fastPrev = toDouble(fast.getValue(last - 1));
        double slowPrev = toDouble(slow.getValue(last - 1));

        if (fastPrev <= slowPrev && fastNow > slowNow) return +1; // bullish cross
        if (fastPrev >= slowPrev && fastNow < slowNow) return -1; // bearish cross
        // Sustained position (not a fresh cross)
        if (fastNow > slowNow) return 1;
        if (fastNow < slowNow) return -1;
        return 0;
    }

    // ── Extract full series values for chart rendering ────────

    public List<Double> extractSeries(Indicator<org.ta4j.core.num.Num> indicator,
                                      BarSeries series) {
        List<Double> values = new ArrayList<>();
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            try { values.add(toDouble(indicator.getValue(i))); }
            catch (Exception e) { values.add(Double.NaN); }
        }
        return values;
    }

    private double toDouble(org.ta4j.core.num.Num num) {
        try { return num.doubleValue(); }
        catch (Exception e) { return Double.NaN; }
    }
}