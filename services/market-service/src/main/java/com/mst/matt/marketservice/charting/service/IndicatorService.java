package com.mst.matt.marketservice.charting.service;

import com.mst.matt.marketservice.charting.model.IndicatorConfig;
import com.mst.matt.marketservice.model.OhlcvBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.ta4j.core.*;
import org.ta4j.core.indicators.*;
import org.ta4j.core.indicators.bollinger.*;
import org.ta4j.core.indicators.helpers.*;
import org.ta4j.core.indicators.ichimoku.*;
import org.ta4j.core.indicators.statistics.*;
import org.ta4j.core.indicators.volume.VWAPIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes all technical indicators for a given bar series.
 *
 * Returns an {@link IndicatorResult} containing current values and series for
 * every configured indicator, ready for signal scoring and chart rendering.
 *
 * Ported from desktop {@code service.analysis.IndicatorService}.
 * Adapted for ta4j 0.16 (MACD signal/histogram computed manually).
 */
@Service
public class IndicatorService {

    private static final Logger log = LoggerFactory.getLogger(IndicatorService.class);

    private final IndicatorComputeService computeService;

    public IndicatorService(IndicatorComputeService computeService) {
        this.computeService = computeService;
    }

    /** Converts a list of {@link OhlcvBar}s into a ta4j {@link BarSeries}. */
    public BarSeries toBarSeries(List<OhlcvBar> bars, String name) {
        return computeService.toBarSeries(bars, name);
    }

    /**
     * Main entry point: computes all configured indicators and returns a full result object.
     */
    public IndicatorResult compute(BarSeries series, IndicatorConfig config) {
        if (series == null || series.isEmpty()) return IndicatorResult.empty();

        int last = series.getEndIndex();
        IndicatorResult.IndicatorResultBuilder result = IndicatorResult.builder();

        try {
            ClosePriceIndicator close = new ClosePriceIndicator(series);

            // ── EMA (Fast + Slow) ─────────────────────────────
            if (config.isEmaEnabled()) {
                EMAIndicator emaFast = new EMAIndicator(close, config.getEmaFastPeriod());
                EMAIndicator emaSlow = new EMAIndicator(close, config.getEmaSlowPeriod());
                result.emaFast(toDouble(emaFast.getValue(last)));
                result.emaSlow(toDouble(emaSlow.getValue(last)));
                result.emaCrossSignal(computeCrossSignal(emaFast, emaSlow, last));

                EMAIndicator ema50  = new EMAIndicator(close, config.getGoldCrossShortPeriod());
                EMAIndicator ema200 = new EMAIndicator(close, config.getGoldCrossLongPeriod());
                result.ema50(toDouble(ema50.getValue(last)));
                result.ema200(toDouble(ema200.getValue(last)));
                result.goldenDeathCrossSignal(computeCrossSignal(ema50, ema200, last));

                result.emaFastSeries(extractSeries(emaFast, series));
                result.emaSlowSeries(extractSeries(emaSlow, series));
                result.ema50Series(extractSeries(ema50, series));
                result.ema200Series(extractSeries(ema200, series));
            }

            // ── MACD ─────────────────────────────────────────
            if (config.isMacdEnabled()) {
                MACDIndicator macd = new MACDIndicator(close,
                        config.getEmaFastPeriod(), config.getEmaSlowPeriod());
                // ta4j 0.16: compute signal + histogram manually
                EMAIndicator macdSignal = new EMAIndicator(macd, 9);
                org.ta4j.core.indicators.numeric.NumericIndicator histogram =
                        org.ta4j.core.indicators.numeric.NumericIndicator.of(macd)
                                .minus(org.ta4j.core.indicators.numeric.NumericIndicator.of(macdSignal));

                result.macdLine(toDouble(macd.getValue(last)));
                result.macdSignal(toDouble(macdSignal.getValue(last)));
                result.macdHistogram(toDouble(histogram.getValue(last)));
                result.macdCrossSignal(computeCrossSignal(macd, macdSignal, last));
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
                if      (rsiVal <= config.getRsiOversold())   sig = +1;
                else if (rsiVal >= config.getRsiOverbought()) sig = -1;
                result.rsiSignal(sig);
            }

            // ── Bollinger Bands ───────────────────────────────
            if (config.isBollingerEnabled()) {
                SMAIndicator sma = new SMAIndicator(close, config.getBollingerPeriod());
                StandardDeviationIndicator sd =
                        new StandardDeviationIndicator(close, config.getBollingerPeriod());
                BollingerBandsMiddleIndicator bbMiddle = new BollingerBandsMiddleIndicator(sma);
                Num bbK = DecimalNum.valueOf(config.getBollingerDeviation());
                BollingerBandsUpperIndicator bbUpper = new BollingerBandsUpperIndicator(bbMiddle, sd, bbK);
                BollingerBandsLowerIndicator bbLower = new BollingerBandsLowerIndicator(bbMiddle, sd, bbK);

                double closeVal  = toDouble(close.getValue(last));
                double upperVal  = toDouble(bbUpper.getValue(last));
                double middleVal = toDouble(bbMiddle.getValue(last));
                double lowerVal  = toDouble(bbLower.getValue(last));

                result.bbUpper(upperVal).bbMiddle(middleVal).bbLower(lowerVal);
                result.bbUpperSeries(extractSeries(bbUpper, series));
                result.bbMiddleSeries(extractSeries(bbMiddle, series));
                result.bbLowerSeries(extractSeries(bbLower, series));

                int sig = 0;
                if      (closeVal <= lowerVal) sig = +1;
                else if (closeVal >= upperVal) sig = -1;
                result.bollingerSignal(sig);
                result.bbBandwidth(upperVal - lowerVal);
            }

            // ── Stochastic ────────────────────────────────────
            if (config.isStochasticEnabled()) {
                StochasticOscillatorKIndicator stochK =
                        new StochasticOscillatorKIndicator(series, config.getStochasticKPeriod());
                StochasticOscillatorDIndicator stochD = new StochasticOscillatorDIndicator(stochK);

                double kVal = toDouble(stochK.getValue(last));
                double dVal = toDouble(stochD.getValue(last));
                result.stochasticK(kVal).stochasticD(dVal);
                result.stochasticKSeries(extractSeries(stochK, series));
                result.stochasticDSeries(extractSeries(stochD, series));

                int sig = 0;
                if      (kVal < 20 && kVal > dVal) sig = +1;
                else if (kVal > 80 && kVal < dVal) sig = -1;
                result.stochasticSignal(sig);
            }

            // ── ATR ──────────────────────────────────────────
            if (config.isAtrEnabled()) {
                ATRIndicator atr = new ATRIndicator(series, config.getAtrPeriod());
                result.atr(toDouble(atr.getValue(last)));
                result.atrSeries(extractSeries(atr, series));
            }

            // ── CCI ──────────────────────────────────────────
            if (config.isCciEnabled()) {
                CCIIndicator cci = new CCIIndicator(series, config.getCciPeriod());
                double cciVal    = toDouble(cci.getValue(last));
                result.cci(cciVal);
                result.cciSeries(extractSeries(cci, series));

                int sig = 0;
                if      (cciVal < -100) sig = +1;
                else if (cciVal >  100) sig = -1;
                result.cciSignal(sig);
            }

            // ── VWAP ─────────────────────────────────────────
            if (config.isVwapEnabled()) {
                VWAPIndicator vwap  = new VWAPIndicator(series, 14);
                double vwapVal      = toDouble(vwap.getValue(last));
                double closeVal     = toDouble(close.getValue(last));
                result.vwap(vwapVal);
                result.vwapSeries(extractSeries(vwap, series));
                result.vwapSignal(Double.compare(closeVal, vwapVal));
            }

            // ── Ichimoku Cloud ────────────────────────────────
            if (config.isIchimokuEnabled()) {
                IchimokuResult ichimoku = computeIchimoku(series,
                        config.getIchimokuTenkanPeriod(),
                        config.getIchimokuKijunPeriod(),
                        config.getIchimokuSenkouPeriod(),
                        last);
                result.ichimoku(ichimoku);
            }

        } catch (Exception e) {
            log.error("Indicator computation error: {}", e.getMessage(), e);
        }

        return result.build();
    }

    // ── Ichimoku Full Computation ────────────────────────────

    private IchimokuResult computeIchimoku(BarSeries series,
            int tenkanPeriod, int kijunPeriod, int senkouPeriod, int last) {

        IchimokuTenkanSenIndicator  tenkan = new IchimokuTenkanSenIndicator(series, tenkanPeriod);
        IchimokuKijunSenIndicator   kijun  = new IchimokuKijunSenIndicator(series, kijunPeriod);
        IchimokuSenkouSpanAIndicator spanA = new IchimokuSenkouSpanAIndicator(series, tenkan, kijun, kijunPeriod);
        IchimokuSenkouSpanBIndicator spanB = new IchimokuSenkouSpanBIndicator(series, senkouPeriod);
        IchimokuChikouSpanIndicator chikou = new IchimokuChikouSpanIndicator(series, kijunPeriod);

        double tenkanVal = toDouble(tenkan.getValue(last));
        double kijunVal  = toDouble(kijun.getValue(last));
        double spanAVal  = toDouble(spanA.getValue(last));
        double spanBVal  = toDouble(spanB.getValue(last));
        double chikouVal = toDouble(chikou.getValue(last));
        double closeVal  = series.getBar(last).getClosePrice().doubleValue();

        double cloudTop    = Math.max(spanAVal, spanBVal);
        double cloudBottom = Math.min(spanAVal, spanBVal);

        boolean aboveCloud    = closeVal > cloudTop;
        boolean belowCloud    = closeVal < cloudBottom;
        boolean tenkanAbove   = tenkanVal > kijunVal;
        int chikouRefIdx      = Math.max(0, last - 26);
        boolean chikouBullish = chikouVal > series.getBar(chikouRefIdx).getClosePrice().doubleValue();

        int signal = 0;
        if      (aboveCloud && tenkanAbove && chikouBullish)        signal = +1;
        else if (belowCloud && !tenkanAbove && !chikouBullish)      signal = -1;
        else if (aboveCloud)                                         signal =  1;
        else if (belowCloud)                                         signal = -1;

        return IchimokuResult.builder()
                .tenkanSen(tenkanVal).kijunSen(kijunVal)
                .senkouSpanA(spanAVal).senkouSpanB(spanBVal)
                .chikouSpan(chikouVal)
                .cloudTop(cloudTop).cloudBottom(cloudBottom)
                .aboveCloud(aboveCloud).belowCloud(belowCloud)
                .signal(signal)
                .tenkanSeries(extractSeries(tenkan, series))
                .kijunSeries(extractSeries(kijun, series))
                .spanASeries(extractSeries(spanA, series))
                .spanBSeries(extractSeries(spanB, series))
                .build();
    }

    // ── Cross Signal Helper ──────────────────────────────────

    private int computeCrossSignal(Indicator<Num> fast, Indicator<Num> slow, int last) {
        if (last < 1) return 0;
        double fastNow  = toDouble(fast.getValue(last));
        double slowNow  = toDouble(slow.getValue(last));
        double fastPrev = toDouble(fast.getValue(last - 1));
        double slowPrev = toDouble(slow.getValue(last - 1));

        if (fastPrev <= slowPrev && fastNow > slowNow) return +1;
        if (fastPrev >= slowPrev && fastNow < slowNow) return -1;
        if (fastNow > slowNow) return  1;
        if (fastNow < slowNow) return -1;
        return 0;
    }

    // ── Extract full series values ────────────────────────────

    public List<Double> extractSeries(Indicator<Num> indicator, BarSeries series) {
        List<Double> values = new ArrayList<>();
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            try   { values.add(toDouble(indicator.getValue(i))); }
            catch (Exception e) { values.add(Double.NaN); }
        }
        return values;
    }

    private double toDouble(Num num) {
        try   { return num.doubleValue(); }
        catch (Exception e) { return Double.NaN; }
    }
}
