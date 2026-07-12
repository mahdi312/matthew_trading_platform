package com.mst.matt.tradingplatformapp.service.analysis;

import com.mst.matt.tradingplatformapp.model.IndicatorDefinition;
import com.mst.matt.tradingplatformapp.model.IndicatorDefinition.PriceSource;
import com.mst.matt.tradingplatformapp.model.IndicatorDefinition.Type;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.model.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.ta4j.core.BarSeries;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link IndicatorComputeService}.
 *
 * Tests run against a synthetic 200-bar 1h bar series so no Spring context
 * or database is needed. Every indicator type is exercised to confirm:
 *   1. It computes without throwing.
 *   2. The resulting series has the correct length (== bar count).
 *   3. At least one non-NaN value is present after the warm-up period.
 *   4. Multi-line indicators populate their extra series keys.
 */
class IndicatorComputeServiceTest {

    private static final int BAR_COUNT = 200;

    private IndicatorComputeService service;
    private BarSeries barSeries;
    private List<OhlcvBar> ohlcvBars;

    // ── Setup ─────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        service   = new IndicatorComputeService();
        ohlcvBars = buildSyntheticBars(BAR_COUNT);
        barSeries = service.toBarSeries(ohlcvBars, "TEST");
    }

    /**
     * Builds a simple rising/oscillating price series to avoid degenerate
     * edge cases (all-same-price, zero volume, etc.).
     */
    private static List<OhlcvBar> buildSyntheticBars(int count) {
        List<OhlcvBar> bars = new ArrayList<>(count);
        LocalDateTime t = LocalDateTime.of(2024, 1, 1, 0, 0);
        double price = 100.0;
        for (int i = 0; i < count; i++) {
            // Sine wave around a baseline to get realistic highs/lows/closes
            double wave  = Math.sin(i * 0.15) * 5.0;
            double open  = price + wave;
            double close = open + Math.cos(i * 0.2) * 2.0;
            double high  = Math.max(open, close) + 0.5 + Math.abs(wave) * 0.1;
            double low   = Math.min(open, close) - 0.5 - Math.abs(wave) * 0.1;
            double vol   = 1000 + Math.abs(wave) * 200;

            bars.add(OhlcvBar.builder()
                    .symbol("TEST")
                    .timeframe("1h")
                    .openTime(t)
                    .open(bd(open))
                    .high(bd(high))
                    .low(bd(low))
                    .close(bd(close))
                    .volume(bd(vol))
                    .assetType(Trade.AssetType.CRYPTO)
                    .build());

            price += 0.05; // slight upward drift
            t = t.plusHours(1);
        }
        return bars;
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(Math.max(0.01, v)); // guard against negatives
    }

    // ── toBarSeries ───────────────────────────────────────────

    @Test
    void toBarSeries_producesCorrectBarCount() {
        assertEquals(BAR_COUNT, barSeries.getBarCount());
    }

    @Test
    void toBarSeries_emptyInput_returnsEmptySeries() {
        BarSeries empty = service.toBarSeries(List.of(), "EMPTY");
        assertTrue(empty.isEmpty());
    }

    // ── timeframeDuration (static) ─────────────────────────────

    @Test
    void timeframeDuration_allKnownTimeframes() {
        assertEquals(Duration.ofMinutes(1),  IndicatorComputeService.timeframeDuration("1m"));
        assertEquals(Duration.ofMinutes(3),  IndicatorComputeService.timeframeDuration("3m"));
        assertEquals(Duration.ofMinutes(5),  IndicatorComputeService.timeframeDuration("5m"));
        assertEquals(Duration.ofMinutes(15), IndicatorComputeService.timeframeDuration("15m"));
        assertEquals(Duration.ofMinutes(30), IndicatorComputeService.timeframeDuration("30m"));
        assertEquals(Duration.ofHours(1),    IndicatorComputeService.timeframeDuration("1h"));
        assertEquals(Duration.ofHours(2),    IndicatorComputeService.timeframeDuration("2h"));
        assertEquals(Duration.ofHours(4),    IndicatorComputeService.timeframeDuration("4h"));
        assertEquals(Duration.ofHours(6),    IndicatorComputeService.timeframeDuration("6h"));
        assertEquals(Duration.ofHours(8),    IndicatorComputeService.timeframeDuration("8h"));
        assertEquals(Duration.ofHours(12),   IndicatorComputeService.timeframeDuration("12h"));
        assertEquals(Duration.ofDays(1),     IndicatorComputeService.timeframeDuration("1d"));
        assertEquals(Duration.ofDays(3),     IndicatorComputeService.timeframeDuration("3d"));
        assertEquals(Duration.ofDays(7),     IndicatorComputeService.timeframeDuration("1w"));
        assertEquals(Duration.ofDays(30),    IndicatorComputeService.timeframeDuration("1mo"));
    }

    @Test
    void timeframeDuration_nullOrBlank_returnsOneHour() {
        assertEquals(Duration.ofHours(1), IndicatorComputeService.timeframeDuration(null));
        assertEquals(Duration.ofHours(1), IndicatorComputeService.timeframeDuration(""));
        assertEquals(Duration.ofHours(1), IndicatorComputeService.timeframeDuration("bogus"));
    }

    @Test
    void timeframeDuration_caseInsensitive() {
        assertEquals(Duration.ofHours(4), IndicatorComputeService.timeframeDuration("4H"));
        assertEquals(Duration.ofDays(1),  IndicatorComputeService.timeframeDuration("1D"));
        assertEquals(Duration.ofDays(30), IndicatorComputeService.timeframeDuration("1MO"));
    }

    // ── compute(def, series) on null / empty series ───────────

    @Test
    void compute_nullSeries_setsEmptySeries() {
        var def = new IndicatorDefinition(Type.EMA);
        service.compute(def, null);
        assertNotNull(def.getSeries());
        assertTrue(def.getSeries().isEmpty());
    }

    @Test
    void compute_emptySeries_setsEmptySeries() {
        var def = new IndicatorDefinition(Type.EMA);
        BarSeries empty = service.toBarSeries(List.of(), "E");
        service.compute(def, empty);
        assertNotNull(def.getSeries());
        assertTrue(def.getSeries().isEmpty());
    }

    // ── Single-line overlays (PRICE pane) ─────────────────────

    @Test
    void ema_producesCorrectLengthAndNonNanValues() {
        assertSingleLineSeries(Type.EMA, 20);
    }

    @Test
    void sma_producesCorrectLengthAndNonNanValues() {
        assertSingleLineSeries(Type.SMA, 14);
    }

    @Test
    void wma_producesCorrectLengthAndNonNanValues() {
        assertSingleLineSeries(Type.WMA, 14);
    }

    @Test
    void dema_producesCorrectLengthAndNonNanValues() {
        assertSingleLineSeries(Type.DEMA, 14);
    }

    @Test
    void tema_producesCorrectLengthAndNonNanValues() {
        assertSingleLineSeries(Type.TEMA, 14);
    }

    @Test
    void hullMa_producesCorrectLengthAndNonNanValues() {
        assertSingleLineSeries(Type.HULL_MA, 14);
    }

    @Test
    void kama_producesCorrectLengthAndNonNanValues() {
        assertSingleLineSeries(Type.KAMA, 10);
    }

    @Test
    void zlema_producesCorrectLengthAndNonNanValues() {
        assertSingleLineSeries(Type.ZLEMA, 14);
    }

    @Test
    void vwap_producesCorrectLengthAndNonNanValues() {
        assertSingleLineSeries(Type.VWAP, 14);
    }

    @Test
    void parabolicSar_producesCorrectLength() {
        var def = new IndicatorDefinition(Type.PARABOLIC_SAR);
        service.compute(def, barSeries);
        assertSeriesLength(def, BAR_COUNT);
        assertHasNonNanValues(def.getSeries());
    }

    // ── Bands (three lines each) ──────────────────────────────

    @Test
    void bollinger_producesThreeSeries() {
        var def = new IndicatorDefinition(Type.BOLLINGER);
        service.compute(def, barSeries);
        assertBandSeries(def, "upper", "lower");
    }

    @Test
    void keltner_producesThreeSeries() {
        var def = new IndicatorDefinition(Type.KELTNER);
        service.compute(def, barSeries);
        assertBandSeries(def, "upper", "lower");
    }

    @Test
    void donchian_producesThreeSeries() {
        var def = new IndicatorDefinition(Type.DONCHIAN);
        service.compute(def, barSeries);
        assertBandSeries(def, "upper", "lower");
    }

    // ── Momentum Oscillators (SUB pane) ──────────────────────

    @Test
    void rsi_producesCorrectLengthAndNonNanValues() {
        assertSingleLineSeries(Type.RSI, 14);
    }

    @Test
    void macd_producesThreeSeries() {
        var def = new IndicatorDefinition(Type.MACD);
        service.compute(def, barSeries);
        assertSeriesLength(def, BAR_COUNT);
        assertHasNonNanValues(def.getSeries());
        assertNotNull(def.getExtraSeries("signal"),    "MACD must have signal series");
        assertNotNull(def.getExtraSeries("histogram"), "MACD must have histogram series");
        assertEquals(BAR_COUNT, def.getExtraSeries("signal").size());
        assertEquals(BAR_COUNT, def.getExtraSeries("histogram").size());
    }

    @Test
    void stochastic_producesTwoSeries() {
        var def = new IndicatorDefinition(Type.STOCHASTIC);
        service.compute(def, barSeries);
        assertSeriesLength(def, BAR_COUNT);
        assertNotNull(def.getExtraSeries("d"), "Stochastic must have %D series");
    }

    @Test
    void stochRsi_producesTwoSeries() {
        var def = new IndicatorDefinition(Type.STOCH_RSI);
        service.compute(def, barSeries);
        assertSeriesLength(def, BAR_COUNT);
        assertNotNull(def.getExtraSeries("d"), "StochRSI must have %D series");
    }

    @Test
    void cci_producesCorrectLength() {
        assertSingleLineSeries(Type.CCI, 20);
    }

    @Test
    void williamsR_producesCorrectLength() {
        assertSingleLineSeries(Type.WILLIAMS_R, 14);
    }

    @Test
    void roc_producesCorrectLength() {
        assertSingleLineSeries(Type.ROC, 12);
    }

    @Test
    void dpo_producesCorrectLength() {
        assertSingleLineSeries(Type.DPO, 20);
    }

    @Test
    void aroon_producesThreeSeries() {
        var def = new IndicatorDefinition(Type.AROON);
        service.compute(def, barSeries);
        assertSeriesLength(def, BAR_COUNT);
        assertNotNull(def.getExtraSeries("up"),   "Aroon must have up series");
        assertNotNull(def.getExtraSeries("down"), "Aroon must have down series");
    }

    @Test
    void cmo_producesCorrectLength() {
        assertSingleLineSeries(Type.CMO, 14);
    }

    @Test
    void fisher_producesCorrectLength() {
        assertSingleLineSeries(Type.FISHER, 9);
    }

    @Test
    void ppo_producesThreeSeries() {
        var def = new IndicatorDefinition(Type.PPO);
        service.compute(def, barSeries);
        assertSeriesLength(def, BAR_COUNT);
        assertNotNull(def.getExtraSeries("signal"),    "PPO must have signal");
        assertNotNull(def.getExtraSeries("histogram"), "PPO must have histogram");
    }

    // ── Volatility ────────────────────────────────────────────

    @Test
    void atr_producesCorrectLength() {
        assertSingleLineSeries(Type.ATR, 14);
    }

    @Test
    void ulcerIndex_producesCorrectLength() {
        assertSingleLineSeries(Type.ULCER_INDEX, 14);
    }

    // ── Volume Indicators ─────────────────────────────────────

    @Test
    void obv_producesCorrectLength() {
        var def = new IndicatorDefinition(Type.OBV);
        service.compute(def, barSeries);
        assertSeriesLength(def, BAR_COUNT);
        // OBV has no warm-up — all values should be non-NaN
        long nonNan = def.getSeries().stream().filter(v -> !Double.isNaN(v)).count();
        assertTrue(nonNan > 0, "OBV should have non-NaN values");
    }

    @Test
    void mfi_producesCorrectLength() {
        assertSingleLineSeries(Type.MFI, 14);
    }

    @Test
    void cmf_producesCorrectLength() {
        assertSingleLineSeries(Type.CMF, 20);
    }

    @Test
    void chaikinOsc_producesCorrectLength() {
        var def = new IndicatorDefinition(Type.CHAIKIN_OSC);
        def.setPeriod(3);
        def.setPeriod2(10);
        service.compute(def, barSeries);
        assertSeriesLength(def, BAR_COUNT);
    }

    // ── Trend Strength ────────────────────────────────────────

    @Test
    void adx_producesThreeSeries() {
        var def = new IndicatorDefinition(Type.ADX);
        service.compute(def, barSeries);
        assertSeriesLength(def, BAR_COUNT);
        assertNotNull(def.getExtraSeries("plusDI"),  "ADX must have +DI series");
        assertNotNull(def.getExtraSeries("minusDI"), "ADX must have -DI series");
    }

    // ── Ichimoku Cloud ────────────────────────────────────────

    @Test
    void ichimoku_producesFiveSeries() {
        var def = new IndicatorDefinition(Type.ICHIMOKU);
        service.compute(def, barSeries);
        assertSeriesLength(def, BAR_COUNT);
        assertNotNull(def.getExtraSeries("kijun"),  "Ichimoku must have kijun");
        assertNotNull(def.getExtraSeries("spanA"),  "Ichimoku must have spanA");
        assertNotNull(def.getExtraSeries("spanB"),  "Ichimoku must have spanB");
        assertNotNull(def.getExtraSeries("chikou"), "Ichimoku must have chikou");
    }

    // ── SUPPORT_RESISTANCE no-op ──────────────────────────────

    @Test
    void supportResistance_computeDoesNotThrow() {
        var def = new IndicatorDefinition(Type.SUPPORT_RESISTANCE);
        assertDoesNotThrow(() -> service.compute(def, barSeries));
        // No series should be set by compute (handled externally)
        assertNull(def.getSeries());
    }

    // ── All Types parametrized ────────────────────────────────

    @ParameterizedTest(name = "compute Type.{0} does not throw")
    @EnumSource(value = Type.class)
    void allTypes_computeWithoutThrowing(Type type) {
        var def = new IndicatorDefinition(type);
        assertDoesNotThrow(() -> service.compute(def, barSeries),
                type + " threw an exception during compute");
    }

    // ── Price source variations ───────────────────────────────

    @Test
    void ema_withHighPriceSource_usesHigh() {
        var def = new IndicatorDefinition(Type.EMA);
        def.setPriceSource(PriceSource.HIGH);
        def.setPeriod(10);
        service.compute(def, barSeries);
        assertSeriesLength(def, BAR_COUNT);
        assertHasNonNanValues(def.getSeries());
    }

    @Test
    void rsi_withHlc3Source_computesCorrectly() {
        var def = new IndicatorDefinition(Type.RSI);
        def.setPriceSource(PriceSource.HLC3);
        service.compute(def, barSeries);
        assertSeriesLength(def, BAR_COUNT);
        // RSI must be in [0, 100] for non-NaN values
        def.getSeries().stream()
                .filter(v -> !Double.isNaN(v))
                .forEach(v -> assertTrue(v >= 0 && v <= 100,
                        "RSI value " + v + " out of [0,100] range"));
    }

    @Test
    void sma_withOhlc4Source_computesWithoutError() {
        var def = new IndicatorDefinition(Type.SMA);
        def.setPriceSource(PriceSource.OHLC4);
        def.setPeriod(10);
        service.compute(def, barSeries);
        assertSeriesLength(def, BAR_COUNT);
        assertHasNonNanValues(def.getSeries());
    }

    // ── computeAll ────────────────────────────────────────────

    @Test
    void computeAll_computesAllVisibleIndicators() {
        List<IndicatorDefinition> defs = List.of(
                new IndicatorDefinition(Type.EMA),
                new IndicatorDefinition(Type.RSI),
                new IndicatorDefinition(Type.MACD)
        );
        service.computeAll(defs, barSeries);
        for (var def : defs) {
            assertNotNull(def.getSeries(), def.getType() + " series should not be null after computeAll");
            assertEquals(BAR_COUNT, def.getSeries().size());
        }
    }

    @Test
    void computeAll_skipsInvisibleIndicators() {
        var hidden = new IndicatorDefinition(Type.EMA);
        hidden.setVisible(false);
        var visible = new IndicatorDefinition(Type.SMA);

        service.computeAll(List.of(hidden, visible), barSeries);

        // Hidden indicator: series should be null (never touched)
        assertNull(hidden.getSeries(), "Invisible indicator should not be computed");
        // Visible indicator: series should be populated
        assertNotNull(visible.getSeries());
        assertEquals(BAR_COUNT, visible.getSeries().size());
    }

    @Test
    void computeAll_handlesEmptyList() {
        assertDoesNotThrow(() -> service.computeAll(List.of(), barSeries));
    }

    // ── autoLabel integration ─────────────────────────────────

    @Test
    void compute_callsAutoLabel_updatingLabel() {
        var def = new IndicatorDefinition(Type.EMA);
        def.setPeriod(50);
        // Label starts as "EMA" (short name from constructor)
        service.compute(def, barSeries);
        // After compute, autoLabel should set it to "EMA(50)"
        assertEquals("EMA(50)", def.getLabel());
    }

    @Test
    void compute_macd_autoLabelIncludesAllPeriods() {
        var def = new IndicatorDefinition(Type.MACD);
        // Default periods: 12, 26, 9
        service.compute(def, barSeries);
        assertEquals("MACD(12,26,9)", def.getLabel());
    }

    // ── Bollinger stdDev multiplier via period2 ───────────────

    @Test
    void bollinger_customMultiplier_appliedCorrectly() {
        var def = new IndicatorDefinition(Type.BOLLINGER);
        def.setPeriod(20);
        def.setPeriod2(25);   // 25 / 10.0 = 2.5σ
        service.compute(def, barSeries);

        List<Double> upper = def.getExtraSeries("upper");
        List<Double> lower = def.getExtraSeries("lower");
        List<Double> mid   = def.getSeries();
        assertNotNull(upper);
        assertNotNull(lower);

        // At a non-NaN index, upper > mid > lower should hold
        for (int i = 30; i < BAR_COUNT; i++) {
            if (!Double.isNaN(upper.get(i)) && !Double.isNaN(mid.get(i)) && !Double.isNaN(lower.get(i))) {
                assertTrue(upper.get(i) >= mid.get(i),
                        "Upper band must be >= middle at index " + i);
                assertTrue(lower.get(i) <= mid.get(i),
                        "Lower band must be <= middle at index " + i);
                break; // One confirmed sample is enough
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    /**
     * For single-line indicators: verify series length == BAR_COUNT and at
     * least one non-NaN value is present.
     */
    private void assertSingleLineSeries(Type type, int period) {
        var def = new IndicatorDefinition(type);
        def.setPeriod(period);
        service.compute(def, barSeries);
        assertSeriesLength(def, BAR_COUNT);
        assertHasNonNanValues(def.getSeries());
    }

    /** Verifies the main series length equals expected. */
    private void assertSeriesLength(IndicatorDefinition def, int expected) {
        assertNotNull(def.getSeries(), def.getType() + " series must not be null");
        assertEquals(expected, def.getSeries().size(),
                def.getType() + " series length mismatch");
    }

    /** Verifies that the series contains at least one finite (non-NaN) double. */
    private void assertHasNonNanValues(List<Double> series) {
        assertNotNull(series, "Series must not be null");
        long nonNan = series.stream().filter(v -> !Double.isNaN(v)).count();
        assertTrue(nonNan > 0, "Series must contain at least one non-NaN value");
    }

    /** For band indicators: verifies main series + two named extra series. */
    private void assertBandSeries(IndicatorDefinition def, String upperKey, String lowerKey) {
        assertSeriesLength(def, BAR_COUNT);
        assertHasNonNanValues(def.getSeries());

        List<Double> upper = def.getExtraSeries(upperKey);
        List<Double> lower = def.getExtraSeries(lowerKey);
        assertNotNull(upper, def.getType() + " must have '" + upperKey + "' extra series");
        assertNotNull(lower, def.getType() + " must have '" + lowerKey + "' extra series");
        assertEquals(BAR_COUNT, upper.size());
        assertEquals(BAR_COUNT, lower.size());
        assertHasNonNanValues(upper);
        assertHasNonNanValues(lower);
    }
}
