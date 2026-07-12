package com.mst.matt.tradingplatformapp.service.analysis;

import com.mst.matt.tradingplatformapp.model.IndicatorDefinition;
import com.mst.matt.tradingplatformapp.model.IndicatorDefinition.PriceSource;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.ta4j.core.*;
import org.ta4j.core.indicators.*;
import org.ta4j.core.indicators.adx.*;
import org.ta4j.core.indicators.aroon.*;
import org.ta4j.core.indicators.averages.*;
import org.ta4j.core.indicators.bollinger.*;
import org.ta4j.core.indicators.donchian.*;
import org.ta4j.core.indicators.helpers.*;
import org.ta4j.core.indicators.ichimoku.*;
import org.ta4j.core.indicators.keltner.*;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.indicators.statistics.*;
import org.ta4j.core.indicators.volume.*;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Computes any {@link IndicatorDefinition} against a ta4j {@link BarSeries}.
 *
 * Covers ALL indicator types listed in {@link IndicatorDefinition.Type}:
 *   Moving Averages (EMA, SMA, WMA, DEMA, TEMA, Hull/HMA, KAMA, ZLEMA)
 *   Bands (Bollinger, Keltner, Donchian)
 *   Oscillators (RSI, MACD, Stochastic, StochRSI, CCI, Williams %R, ROC, DPO, Aroon, CMO, Fisher, PPO)
 *   Volatility (ATR, Ulcer Index)
 *   Volume (OBV, MFI, CMF, Chaikin Oscillator)
 *   Trend strength (ADX)
 *   Complex overlays (Ichimoku Cloud, Parabolic SAR)
 */
@Service
public class IndicatorComputeService {

    private static final Logger log = LoggerFactory.getLogger(IndicatorComputeService.class);

    // ── Bar Series Builder ────────────────────────────────────

    public BarSeries toBarSeries(List<OhlcvBar> bars, String name) {
        BaseBarSeries series = new BaseBarSeriesBuilder().withName(name).build();
        series.setMaximumBarCount(3000);
        for (OhlcvBar bar : bars) {
            Duration period = timeframeDuration(bar.getTimeframe());
            ZonedDateTime openZdt = bar.getOpenTime().atZone(ZoneOffset.UTC);
            Instant begin = openZdt.toInstant();
            Instant end   = openZdt.plus(period).toInstant();
            try {
                series.addBar(new BaseBar(
                        period, begin, end,
                        DecimalNum.valueOf(bar.getOpen()),
                        DecimalNum.valueOf(bar.getHigh()),
                        DecimalNum.valueOf(bar.getLow()),
                        DecimalNum.valueOf(bar.getClose()),
                        DecimalNum.valueOf(bar.getVolume()),
                        DecimalNum.valueOf(0),
                        0L
                ), false);
            } catch (Exception e) {
                log.trace("Skipping bar: {}", e.getMessage());
            }
        }
        return series;
    }

    public static Duration timeframeDuration(String tf) {
        if (tf == null || tf.isBlank()) return Duration.ofHours(1);
        return switch (tf.toLowerCase()) {
            case "1m"                    -> Duration.ofMinutes(1);
            case "3m"                    -> Duration.ofMinutes(3);
            case "5m"                    -> Duration.ofMinutes(5);
            case "15m"                   -> Duration.ofMinutes(15);
            case "30m"                   -> Duration.ofMinutes(30);
            case "1h"                    -> Duration.ofHours(1);
            case "2h"                    -> Duration.ofHours(2);
            case "4h"                    -> Duration.ofHours(4);
            case "6h"                    -> Duration.ofHours(6);
            case "8h"                    -> Duration.ofHours(8);
            case "12h"                   -> Duration.ofHours(12);
            case "1d"                    -> Duration.ofDays(1);
            case "3d"                    -> Duration.ofDays(3);
            case "1w"                    -> Duration.ofDays(7);
            case "1mo", "1month", "1mn" -> Duration.ofDays(30);
            default                      -> Duration.ofHours(1);
        };
    }

    // ── Main compute entry ────────────────────────────────────

    public void compute(IndicatorDefinition def, BarSeries series) {
        if (series == null || series.isEmpty()) {
            def.setSeries(Collections.emptyList());
            return;
        }
        try {
            switch (def.getType()) {
                case EMA             -> computeEma(def, series);
                case SMA             -> computeSma(def, series);
                case WMA             -> computeWma(def, series);
                case DEMA            -> computeDema(def, series);
                case TEMA            -> computeTema(def, series);
                case HULL_MA         -> computeHullMa(def, series);
                case KAMA            -> computeKama(def, series);
                case ZLEMA           -> computeZlema(def, series);
                case VWAP            -> computeVwap(def, series);
                case BOLLINGER       -> computeBollinger(def, series);
                case KELTNER         -> computeKeltner(def, series);
                case DONCHIAN        -> computeDonchian(def, series);
                case PARABOLIC_SAR   -> computeParabolicSar(def, series);
                case RSI             -> computeRsi(def, series);
                case MACD            -> computeMacd(def, series);
                case STOCHASTIC      -> computeStochastic(def, series);
                case STOCH_RSI       -> computeStochRsi(def, series);
                case CCI             -> computeCci(def, series);
                case WILLIAMS_R      -> computeWilliamsR(def, series);
                case ROC             -> computeRoc(def, series);
                case DPO             -> computeDpo(def, series);
                case AROON           -> computeAroon(def, series);
                case CMO             -> computeCmo(def, series);
                case FISHER          -> computeFisher(def, series);
                case PPO             -> computePpo(def, series);
                case ATR             -> computeAtr(def, series);
                case ULCER_INDEX     -> computeUlcerIndex(def, series);
                case OBV             -> computeObv(def, series);
                case MFI             -> computeMfi(def, series);
                case CMF             -> computeCmf(def, series);
                case CHAIKIN_OSC     -> computeChaikinOsc(def, series);
                case ADX             -> computeAdx(def, series);
                case ICHIMOKU        -> computeIchimoku(def, series);
                case TWO_MA          -> computeTwoMa(def, series);
                case THREE_MA        -> computeThreeMa(def, series);
                case SUPPORT_RESISTANCE -> { /* handled externally by SupportResistanceService */ }
                default -> log.warn("Unhandled indicator type: {}", def.getType());
            }
            def.autoLabel();
        } catch (Exception e) {
            log.error("Failed to compute {}: {}", def.getType(), e.getMessage(), e);
            def.setSeries(Collections.emptyList());
        }
    }

    // ── Price source helper ───────────────────────────────────

    private Indicator<Num> priceIndicator(BarSeries series, PriceSource src) {
        if (src == null) return new ClosePriceIndicator(series);
        return switch (src) {
            case OPEN   -> new OpenPriceIndicator(series);
            case HIGH   -> new HighPriceIndicator(series);
            case LOW    -> new LowPriceIndicator(series);
            case HL2    -> new MedianPriceIndicator(series);
            case HLC3   -> new TypicalPriceIndicator(series);
            case OHLC4  -> {
                // Mean price = (O+H+L+C)/4  — built from arithmetic combination of price helpers
                var o = NumericIndicator.of(new OpenPriceIndicator(series));
                var h = NumericIndicator.of(new HighPriceIndicator(series));
                var lo = NumericIndicator.of(new LowPriceIndicator(series));
                var c = NumericIndicator.of(new ClosePriceIndicator(series));
                yield o.plus(h).plus(lo).plus(c).dividedBy(4);
            }
            default -> new ClosePriceIndicator(series);
        };
    }

    // ── Extract full-series to List<Double> ──────────────────

    private List<Double> extract(Indicator<Num> ind, BarSeries series) {
        int n     = series.getBarCount();
        int begin = series.getBeginIndex();
        List<Double> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            try {
                double v = ind.getValue(begin + i).doubleValue();
                out.add(Double.isInfinite(v) ? Double.NaN : v);
            } catch (Exception e) {
                out.add(Double.NaN);
            }
        }
        return out;
    }

    // ── Moving Averages ───────────────────────────────────────

    private void computeEma(IndicatorDefinition def, BarSeries s) {
        def.setSeries(extract(new EMAIndicator(priceIndicator(s, def.getPriceSource()),
                Math.max(2, def.getPeriod())), s));
    }

    private void computeSma(IndicatorDefinition def, BarSeries s) {
        def.setSeries(extract(new SMAIndicator(priceIndicator(s, def.getPriceSource()),
                Math.max(2, def.getPeriod())), s));
    }

    private void computeWma(IndicatorDefinition def, BarSeries s) {
        def.setSeries(extract(new WMAIndicator(priceIndicator(s, def.getPriceSource()),
                Math.max(2, def.getPeriod())), s));
    }

    private void computeDema(IndicatorDefinition def, BarSeries s) {
        def.setSeries(extract(new DoubleEMAIndicator(priceIndicator(s, def.getPriceSource()),
                Math.max(2, def.getPeriod())), s));
    }

    private void computeTema(IndicatorDefinition def, BarSeries s) {
        def.setSeries(extract(new TripleEMAIndicator(priceIndicator(s, def.getPriceSource()),
                Math.max(2, def.getPeriod())), s));
    }

    private void computeHullMa(IndicatorDefinition def, BarSeries s) {
        // ta4j 0.22 has HMAIndicator in averages package
        def.setSeries(extract(new HMAIndicator(priceIndicator(s, def.getPriceSource()),
                Math.max(2, def.getPeriod())), s));
    }

    private void computeKama(IndicatorDefinition def, BarSeries s) {
        // KAMAIndicator(price, erPeriod, fastPeriod, slowPeriod)
        def.setSeries(extract(new KAMAIndicator(priceIndicator(s, def.getPriceSource()),
                Math.max(2, def.getPeriod()), 2, 30), s));
    }

    private void computeZlema(IndicatorDefinition def, BarSeries s) {
        def.setSeries(extract(new ZLEMAIndicator(priceIndicator(s, def.getPriceSource()),
                Math.max(2, def.getPeriod())), s));
    }

    private void computeVwap(IndicatorDefinition def, BarSeries s) {
        def.setSeries(extract(new VWAPIndicator(s, Math.max(2, def.getPeriod())), s));
    }

    // ── Bands ─────────────────────────────────────────────────

    private void computeBollinger(IndicatorDefinition def, BarSeries s) {
        int p = Math.max(2, def.getPeriod());
        // period2 stores stdDev multiplier * 10 (e.g. 20 = 2.0σ, 25 = 2.5σ)
        double k = def.getPeriod2() > 0 ? def.getPeriod2() / 10.0 : 2.0;
        if (k < 0.5 || k > 10.0) k = 2.0;

        var src   = priceIndicator(s, def.getPriceSource());
        var sma   = new SMAIndicator(src, p);
        var sd    = new StandardDeviationIndicator(src, p);
        var mid   = new BollingerBandsMiddleIndicator(sma);
        var upper = new BollingerBandsUpperIndicator(mid, sd, DecimalNum.valueOf(k));
        var lower = new BollingerBandsLowerIndicator(mid, sd, DecimalNum.valueOf(k));

        def.setSeries(extract(mid, s));
        def.putExtraSeries("upper", extract(upper, s));
        def.putExtraSeries("lower", extract(lower, s));
    }

    private void computeKeltner(IndicatorDefinition def, BarSeries s) {
        int emaPeriod = Math.max(2, def.getPeriod());
        int atrPeriod = def.getPeriod2() > 0 ? def.getPeriod2() : emaPeriod;
        double mult   = 2.0;
        var mid   = new KeltnerChannelMiddleIndicator(s, emaPeriod);
        var upper = new KeltnerChannelUpperIndicator(mid, mult, atrPeriod);
        var lower = new KeltnerChannelLowerIndicator(mid, mult, atrPeriod);
        def.setSeries(extract(mid, s));
        def.putExtraSeries("upper", extract(upper, s));
        def.putExtraSeries("lower", extract(lower, s));
    }

    private void computeDonchian(IndicatorDefinition def, BarSeries s) {
        int p     = Math.max(2, def.getPeriod());
        var upper = new DonchianChannelUpperIndicator(s, p);
        var lower = new DonchianChannelLowerIndicator(s, p);
        var mid   = NumericIndicator.of(upper).plus(NumericIndicator.of(lower))
                .dividedBy(2);
        def.setSeries(extract(mid, s));
        def.putExtraSeries("upper", extract(upper, s));
        def.putExtraSeries("lower", extract(lower, s));
    }

    private void computeParabolicSar(IndicatorDefinition def, BarSeries s) {
        def.setSeries(extract(
                new ParabolicSarIndicator(s, DecimalNum.valueOf(0.02), DecimalNum.valueOf(0.2)),
                s));
    }

    // ── Momentum Oscillators ──────────────────────────────────

    private void computeRsi(IndicatorDefinition def, BarSeries s) {
        def.setSeries(extract(new RSIIndicator(priceIndicator(s, def.getPriceSource()),
                Math.max(2, def.getPeriod())), s));
    }

    private void computeMacd(IndicatorDefinition def, BarSeries s) {
        int fast   = Math.max(2, def.getPeriod());
        int slow   = Math.max(fast + 1, def.getPeriod2() > 0 ? def.getPeriod2() : 26);
        int signal = def.getPeriod3() > 0 ? def.getPeriod3() : 9;
        var src    = priceIndicator(s, def.getPriceSource());
        var macd   = new MACDIndicator(src, fast, slow);
        var sig    = macd.getSignalLine(signal);
        var hist   = macd.getHistogram(signal);
        def.setSeries(extract(macd, s));
        def.putExtraSeries("signal",    extract(sig, s));
        def.putExtraSeries("histogram", extract(hist, s));
    }

    private void computeStochastic(IndicatorDefinition def, BarSeries s) {
        int k      = Math.max(2, def.getPeriod());
        int smooth = def.getPeriod2() > 0 ? def.getPeriod2() : 3;
        var stochK = new StochasticOscillatorKIndicator(s, k);
        var stochD = new StochasticOscillatorDIndicator(stochK);
        def.setSeries(extract(stochK, s));
        def.putExtraSeries("d", extract(stochD, s));
    }

    private void computeStochRsi(IndicatorDefinition def, BarSeries s) {
        // StochasticRSIIndicator(series, barCount) — uses close price internally
        int p = Math.max(2, def.getPeriod());
        var stochRsi = new StochasticRSIIndicator(s, p);
        var smoothD  = new SMAIndicator(stochRsi, def.getPeriod2() > 0 ? def.getPeriod2() : 3);
        def.setSeries(extract(stochRsi, s));
        def.putExtraSeries("d", extract(smoothD, s));
    }

    private void computeCci(IndicatorDefinition def, BarSeries s) {
        def.setSeries(extract(new CCIIndicator(s, Math.max(2, def.getPeriod())), s));
    }

    private void computeWilliamsR(IndicatorDefinition def, BarSeries s) {
        def.setSeries(extract(new WilliamsRIndicator(s, Math.max(2, def.getPeriod())), s));
    }

    private void computeRoc(IndicatorDefinition def, BarSeries s) {
        def.setSeries(extract(new ROCIndicator(priceIndicator(s, def.getPriceSource()),
                Math.max(2, def.getPeriod())), s));
    }

    private void computeDpo(IndicatorDefinition def, BarSeries s) {
        // DPOIndicator(BarSeries, barCount) is available in ta4j 0.22
        def.setSeries(extract(new DPOIndicator(s, Math.max(2, def.getPeriod())), s));
    }

    private void computeAroon(IndicatorDefinition def, BarSeries s) {
        int p    = Math.max(2, def.getPeriod());
        // AroonOscillatorIndicator = AroonUp - AroonDown
        var osc  = new AroonOscillatorIndicator(s, p);
        var up   = new AroonUpIndicator(s, p);
        var down = new AroonDownIndicator(s, p);
        def.setSeries(extract(osc, s));
        def.putExtraSeries("up",   extract(up, s));
        def.putExtraSeries("down", extract(down, s));
    }

    private void computeCmo(IndicatorDefinition def, BarSeries s) {
        // CMOIndicator(Indicator<Num>, barCount)
        def.setSeries(extract(new CMOIndicator(priceIndicator(s, def.getPriceSource()),
                Math.max(2, def.getPeriod())), s));
    }

    private void computeFisher(IndicatorDefinition def, BarSeries s) {
        // FisherIndicator(Indicator<Num> price, int barCount)
        int p = Math.max(2, def.getPeriod());
        def.setSeries(extract(new FisherIndicator(priceIndicator(s, def.getPriceSource()), p), s));
    }

    private void computePpo(IndicatorDefinition def, BarSeries s) {
        // PPOIndicator(Indicator<Num>, shortBarCount, longBarCount)
        int fast   = Math.max(2, def.getPeriod());
        int slow   = Math.max(fast + 1, def.getPeriod2() > 0 ? def.getPeriod2() : 26);
        int signal = def.getPeriod3() > 0 ? def.getPeriod3() : 9;
        var src    = priceIndicator(s, def.getPriceSource());
        var ppo    = new PPOIndicator(src, fast, slow);
        var sig    = new EMAIndicator(ppo, signal);
        var hist   = NumericIndicator.of(ppo).minus(NumericIndicator.of(sig));
        def.setSeries(extract(ppo, s));
        def.putExtraSeries("signal",    extract(sig, s));
        def.putExtraSeries("histogram", extract(hist, s));
    }

    // ── Volatility ────────────────────────────────────────────

    private void computeAtr(IndicatorDefinition def, BarSeries s) {
        def.setSeries(extract(new ATRIndicator(s, Math.max(2, def.getPeriod())), s));
    }

    private void computeUlcerIndex(IndicatorDefinition def, BarSeries s) {
        // UlcerIndexIndicator(Indicator<Num>, barCount)
        def.setSeries(extract(new UlcerIndexIndicator(priceIndicator(s, def.getPriceSource()),
                Math.max(2, def.getPeriod())), s));
    }

    // ── Volume Indicators ─────────────────────────────────────

    private void computeObv(IndicatorDefinition def, BarSeries s) {
        def.setSeries(extract(new OnBalanceVolumeIndicator(s), s));
    }

    private void computeMfi(IndicatorDefinition def, BarSeries s) {
        def.setSeries(extract(new MoneyFlowIndexIndicator(s, Math.max(2, def.getPeriod())), s));
    }

    private void computeCmf(IndicatorDefinition def, BarSeries s) {
        def.setSeries(extract(new ChaikinMoneyFlowIndicator(s, Math.max(2, def.getPeriod())), s));
    }

    private void computeChaikinOsc(IndicatorDefinition def, BarSeries s) {
        // ChaikinOscillatorIndicator(BarSeries, shortBarCount, longBarCount)
        int fast = Math.max(2, def.getPeriod());
        int slow = def.getPeriod2() > 0 ? def.getPeriod2() : 10;
        def.setSeries(extract(new ChaikinOscillatorIndicator(s, fast, slow), s));
    }

    // ── Trend Strength ────────────────────────────────────────

    private void computeAdx(IndicatorDefinition def, BarSeries s) {
        int p = Math.max(2, def.getPeriod());
        var adx     = new ADXIndicator(s, p, p);
        var plusDI  = new PlusDIIndicator(s, p);
        var minusDI = new MinusDIIndicator(s, p);
        def.setSeries(extract(adx, s));
        def.putExtraSeries("plusDI",  extract(plusDI, s));
        def.putExtraSeries("minusDI", extract(minusDI, s));
    }

    // ── Ichimoku Cloud ────────────────────────────────────────

    private void computeIchimoku(IndicatorDefinition def, BarSeries s) {
        int tenkan = Math.max(2, def.getPeriod());
        int kijun  = def.getPeriod2() > 0 ? def.getPeriod2() : 26;
        int senkou = def.getPeriod3() > 0 ? def.getPeriod3() : 52;

        var tenkanSen = new IchimokuTenkanSenIndicator(s, tenkan);
        var kijunSen  = new IchimokuKijunSenIndicator(s, kijun);
        var spanA     = new IchimokuSenkouSpanAIndicator(s, tenkanSen, kijunSen, kijun);
        var spanB     = new IchimokuSenkouSpanBIndicator(s, senkou);
        var chikou    = new IchimokuChikouSpanIndicator(s, kijun);

        def.setSeries(extract(tenkanSen, s));
        def.putExtraSeries("kijun",  extract(kijunSen, s));
        def.putExtraSeries("spanA",  extract(spanA, s));
        def.putExtraSeries("spanB",  extract(spanB, s));
        def.putExtraSeries("chikou", extract(chikou, s));
    }

    // ── Batch compute ─────────────────────────────────────────

    public void computeAll(List<IndicatorDefinition> indicators, BarSeries series) {
        for (IndicatorDefinition def : indicators) {
            if (def.isVisible()) {
                compute(def, series);
            }
        }
    }

    // ── Feature 3: Multi-MA ───────────────────────────────────

    /**
     * TWO_MA: plots two EMA lines.
     *   period  = fast period (default 9)
     *   period2 = slow period (default 21)
     * Primary series = fast line; extraSeries["line2"] = slow line.
     */
    private void computeTwoMa(IndicatorDefinition def, BarSeries s) {
        int fast = Math.max(2, def.getPeriod());
        int slow = Math.max(fast + 1, def.getPeriod2() > 0 ? def.getPeriod2() : 21);
        var src  = priceIndicator(s, def.getPriceSource());

        def.setSeries(extract(new EMAIndicator(src, fast), s));           // line1
        def.putExtraSeries("line2", extract(new EMAIndicator(src, slow), s)); // line2
        def.autoLabel();
    }

    /**
     * THREE_MA: plots three EMA lines.
     *   period  = fast  (default 9)
     *   period2 = mid   (default 21)
     *   period3 = slow  (default 50)
     * Primary series = fast line; extraSeries["line2"] = mid; extraSeries["line3"] = slow.
     */
    private void computeThreeMa(IndicatorDefinition def, BarSeries s) {
        int fast = Math.max(2, def.getPeriod());
        int mid  = Math.max(fast + 1,  def.getPeriod2() > 0 ? def.getPeriod2() : 21);
        int slow = Math.max(mid  + 1,  def.getPeriod3() > 0 ? def.getPeriod3() : 50);
        var src  = priceIndicator(s, def.getPriceSource());

        def.setSeries(extract(new EMAIndicator(src, fast), s));            // line1
        def.putExtraSeries("line2", extract(new EMAIndicator(src, mid),  s)); // line2
        def.putExtraSeries("line3", extract(new EMAIndicator(src, slow), s)); // line3
        def.autoLabel();
    }
}
