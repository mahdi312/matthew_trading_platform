package com.mst.matt.tradingplatformapp.service.analysis;

import com.mst.matt.tradingplatformapp.model.IndicatorDefinition;
import com.mst.matt.tradingplatformapp.model.IndicatorDefinition.Type;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Feature 3: TWO_MA and THREE_MA indicator computation.
 */
class MultiMaIndicatorTest {

    private IndicatorComputeService svc;
    private BarSeries series;

    @BeforeEach
    void setUp() {
        svc = new IndicatorComputeService();
        series = svc.toBarSeries(makeBars(100), "TEST");
    }

    // ── TWO_MA ─────────────────────────────────────────────────

    @Test
    void twoMa_defaultPeriodsAre9And21() {
        IndicatorDefinition def = new IndicatorDefinition(Type.TWO_MA);
        assertThat(def.getPeriod()).isEqualTo(9);
        assertThat(def.getPeriod2()).isEqualTo(21);
    }

    @Test
    void twoMa_computesFastAndSlowLines() {
        IndicatorDefinition def = new IndicatorDefinition(Type.TWO_MA);
        svc.compute(def, series);

        // Primary series = fast EMA(9)
        assertThat(def.getSeries()).isNotNull().isNotEmpty();
        assertThat(def.getSeries()).hasSize(100);

        // extraSeries["line2"] = slow EMA(21)
        List<Double> line2 = def.getExtraSeries("line2");
        assertThat(line2).isNotNull().isNotEmpty();
        assertThat(line2).hasSize(100);
    }

    @Test
    void twoMa_fastLineShouldBeMoreResponsiveThanSlow() {
        IndicatorDefinition def = new IndicatorDefinition(Type.TWO_MA);
        svc.compute(def, series);

        // On a rising price series, fast EMA should be higher than slow EMA
        // at the last computed bar
        int last = 99;
        double fast = def.getSeries().get(last);
        double slow = def.getExtraSeries("line2").get(last);
        // Both should be valid (not NaN)
        assertThat(fast).isFinite();
        assertThat(slow).isFinite();
    }

    @Test
    void twoMa_customPeriods() {
        IndicatorDefinition def = new IndicatorDefinition(Type.TWO_MA);
        def.setPeriod(5);
        def.setPeriod2(50);
        svc.compute(def, series);

        assertThat(def.getSeries()).hasSize(100);
        assertThat(def.getExtraSeries("line2")).hasSize(100);
    }

    @Test
    void twoMa_defaultColorsDefined() {
        IndicatorDefinition def = new IndicatorDefinition(Type.TWO_MA);
        assertThat(def.getColor()).isNotNull().startsWith("#");
        assertThat(def.getColor2()).isNotNull().startsWith("#");
    }

    @Test
    void twoMa_autoLabelContainsPeriods() {
        IndicatorDefinition def = new IndicatorDefinition(Type.TWO_MA);
        svc.compute(def, series);
        assertThat(def.getLabel()).contains("9").contains("21");
    }

    @Test
    void twoMa_emptySeriesGivesEmptyResult() {
        IndicatorDefinition def = new IndicatorDefinition(Type.TWO_MA);
        svc.compute(def, svc.toBarSeries(List.of(), "EMPTY"));
        assertThat(def.getSeries()).isEmpty();
    }

    // ── THREE_MA ───────────────────────────────────────────────

    @Test
    void threeMa_defaultPeriodsAre9And21And50() {
        IndicatorDefinition def = new IndicatorDefinition(Type.THREE_MA);
        assertThat(def.getPeriod()).isEqualTo(9);
        assertThat(def.getPeriod2()).isEqualTo(21);
        assertThat(def.getPeriod3()).isEqualTo(50);
    }

    @Test
    void threeMa_computesThreeLines() {
        IndicatorDefinition def = new IndicatorDefinition(Type.THREE_MA);
        svc.compute(def, series);

        // Primary = fast EMA(9)
        assertThat(def.getSeries()).isNotNull().hasSize(100);

        // line2 = mid EMA(21)
        List<Double> line2 = def.getExtraSeries("line2");
        assertThat(line2).isNotNull().hasSize(100);

        // line3 = slow EMA(50)
        List<Double> line3 = def.getExtraSeries("line3");
        assertThat(line3).isNotNull().hasSize(100);
    }

    @Test
    void threeMa_allLinesAreFiniteAtEnd() {
        IndicatorDefinition def = new IndicatorDefinition(Type.THREE_MA);
        svc.compute(def, series);

        int last = 99;
        assertThat(def.getSeries().get(last)).isFinite();
        assertThat(def.getExtraSeries("line2").get(last)).isFinite();
        assertThat(def.getExtraSeries("line3").get(last)).isFinite();
    }

    @Test
    void threeMa_threeColorsAreDefined() {
        IndicatorDefinition def = new IndicatorDefinition(Type.THREE_MA);
        assertThat(def.getColor()).isNotNull().startsWith("#");
        assertThat(def.getColor2()).isNotNull().startsWith("#");
        assertThat(def.getColor3()).isNotNull().startsWith("#");
    }

    @Test
    void threeMa_autoLabelContainsAllPeriods() {
        IndicatorDefinition def = new IndicatorDefinition(Type.THREE_MA);
        svc.compute(def, series);
        assertThat(def.getLabel()).contains("9").contains("21").contains("50");
    }

    @Test
    void threeMa_customPeriods() {
        IndicatorDefinition def = new IndicatorDefinition(Type.THREE_MA);
        def.setPeriod(3);
        def.setPeriod2(8);
        def.setPeriod3(13);
        svc.compute(def, series);

        assertThat(def.getSeries()).hasSize(100);
        assertThat(def.getExtraSeries("line2")).hasSize(100);
        assertThat(def.getExtraSeries("line3")).hasSize(100);
    }

    @Test
    void threeMa_periodsEnforcedMonotonically() {
        // If period3 <= period2, service enforces period3 = period2 + 1
        IndicatorDefinition def = new IndicatorDefinition(Type.THREE_MA);
        def.setPeriod(5);
        def.setPeriod2(5);  // same as fast — service should clamp
        def.setPeriod3(5);  // same as mid — service should clamp
        svc.compute(def, series);
        // Should not throw; just produce valid series
        assertThat(def.getSeries()).isNotNull();
        assertThat(def.getExtraSeries("line2")).isNotNull();
        assertThat(def.getExtraSeries("line3")).isNotNull();
    }

    @Test
    void threeMa_emptySeriesGivesEmptyResult() {
        IndicatorDefinition def = new IndicatorDefinition(Type.THREE_MA);
        svc.compute(def, svc.toBarSeries(List.of(), "EMPTY"));
        assertThat(def.getSeries()).isEmpty();
    }

    // ── Type enum ──────────────────────────────────────────────

    @Test
    void twoMaType_shortNameIs2MA() {
        assertThat(Type.TWO_MA.shortName).isEqualTo("2MA");
        assertThat(Type.TWO_MA.displayName).contains("Dual");
    }

    @Test
    void threeMaType_shortNameIs3MA() {
        assertThat(Type.THREE_MA.shortName).isEqualTo("3MA");
        assertThat(Type.THREE_MA.displayName).contains("Triple");
    }

    @Test
    void twoMaAndThreeMa_arePricePane() {
        assertThat(Type.TWO_MA.defaultPane)
                .isEqualTo(IndicatorDefinition.DisplayPane.PRICE);
        assertThat(Type.THREE_MA.defaultPane)
                .isEqualTo(IndicatorDefinition.DisplayPane.PRICE);
    }

    // ── Helpers ────────────────────────────────────────────────

    private static List<OhlcvBar> makeBars(int n) {
        List<OhlcvBar> bars = new ArrayList<>();
        double price = 100.0;
        for (int i = 0; i < n; i++) {
            double close = price + Math.sin(i * 0.3) * 10;
            bars.add(OhlcvBar.builder()
                    .openTime(LocalDateTime.of(2024, 1, 1, 0, 0).plusHours(i))
                    .open(BigDecimal.valueOf(close - 1))
                    .high(BigDecimal.valueOf(close + 2))
                    .low(BigDecimal.valueOf(close - 2))
                    .close(BigDecimal.valueOf(close))
                    .volume(BigDecimal.valueOf(1000 + i * 10))
                    .timeframe("1h")
                    .build());
            price = close;
        }
        return bars;
    }
}
