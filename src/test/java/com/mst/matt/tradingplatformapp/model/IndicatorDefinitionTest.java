package com.mst.matt.tradingplatformapp.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link IndicatorDefinition}.
 * Verifies default values, autoLabel generation, extra-series storage,
 * and that every enum constant has coherent defaults.
 */
class IndicatorDefinitionTest {

    // ── Construction & Defaults ───────────────────────────────

    @Test
    void constructor_assignsUniqueId() {
        var a = new IndicatorDefinition(IndicatorDefinition.Type.EMA);
        var b = new IndicatorDefinition(IndicatorDefinition.Type.EMA);
        assertNotNull(a.getId());
        assertNotNull(b.getId());
        assertNotEquals(a.getId(), b.getId(), "Two instances must have different UUIDs");
    }

    @Test
    void ema_hasExpectedDefaults() {
        var def = new IndicatorDefinition(IndicatorDefinition.Type.EMA);
        assertEquals(IndicatorDefinition.Type.EMA, def.getType());
        assertEquals(20, def.getPeriod());
        assertEquals(0,  def.getPeriod2());
        assertEquals(0,  def.getPeriod3());
        assertEquals(IndicatorDefinition.PriceSource.CLOSE, def.getPriceSource());
        assertEquals("#388bfd", def.getColor());
        assertEquals(1.5, def.getLineWeight(), 0.001);
        assertTrue(def.isVisible());
        assertEquals(IndicatorDefinition.DisplayPane.PRICE, def.getPane());
        assertNotNull(def.getExtraSeries());
        assertTrue(def.getExtraSeries().isEmpty());
    }

    @Test
    void macd_hasCorrectThreePeriodDefaults() {
        var def = new IndicatorDefinition(IndicatorDefinition.Type.MACD);
        assertEquals(12, def.getPeriod(),  "MACD fast period");
        assertEquals(26, def.getPeriod2(), "MACD slow period");
        assertEquals(9,  def.getPeriod3(), "MACD signal period");
        assertEquals(IndicatorDefinition.DisplayPane.SUB, def.getPane());
    }

    @Test
    void ichimoku_hasAllThreePeriodDefaults() {
        var def = new IndicatorDefinition(IndicatorDefinition.Type.ICHIMOKU);
        assertEquals(9,  def.getPeriod(),  "Tenkan period");
        assertEquals(26, def.getPeriod2(), "Kijun period");
        assertEquals(52, def.getPeriod3(), "Senkou B period");
        assertEquals(IndicatorDefinition.DisplayPane.PRICE, def.getPane());
    }

    @Test
    void bollingerBands_isInPricePane() {
        var def = new IndicatorDefinition(IndicatorDefinition.Type.BOLLINGER);
        assertEquals(IndicatorDefinition.DisplayPane.PRICE, def.getPane());
        assertFalse(def.getType().isSingleLine);
    }

    @Test
    void rsi_isInSubPane() {
        var def = new IndicatorDefinition(IndicatorDefinition.Type.RSI);
        assertEquals(IndicatorDefinition.DisplayPane.SUB, def.getPane());
        assertEquals(14, def.getPeriod());
    }

    // ── autoLabel ─────────────────────────────────────────────

    @Test
    void autoLabel_singlePeriod_formatsProperly() {
        var def = new IndicatorDefinition(IndicatorDefinition.Type.EMA);
        def.setPeriod(21);
        def.autoLabel();
        assertEquals("EMA(21)", def.getLabel());
    }

    @Test
    void autoLabel_twoPeriods_includesBoth() {
        var def = new IndicatorDefinition(IndicatorDefinition.Type.MACD);
        def.setPeriod(12);
        def.setPeriod2(26);
        def.setPeriod3(0);
        def.autoLabel();
        assertEquals("MACD(12,26)", def.getLabel());
    }

    @Test
    void autoLabel_threePeriods_includesAll() {
        var def = new IndicatorDefinition(IndicatorDefinition.Type.ICHIMOKU);
        def.setPeriod(9);
        def.setPeriod2(26);
        def.setPeriod3(52);
        def.autoLabel();
        assertEquals("Ichimoku(9,26,52)", def.getLabel());
    }

    @Test
    void autoLabel_zeroPeriod_usesShortName() {
        var def = new IndicatorDefinition(IndicatorDefinition.Type.PARABOLIC_SAR);
        def.setPeriod(0);
        def.setPeriod2(0);
        def.setPeriod3(0);
        def.autoLabel();
        // period == 0, so condition `period > 0` is false
        assertEquals("SAR", def.getLabel());
    }

    // ── Setters / Getters ─────────────────────────────────────

    @Test
    void settersRoundTrip() {
        var def = new IndicatorDefinition(IndicatorDefinition.Type.RSI);
        def.setPeriod(7);
        def.setPeriod2(3);
        def.setPeriod3(5);
        def.setPriceSource(IndicatorDefinition.PriceSource.HLC3);
        def.setColor("#ff0000");
        def.setLineWeight(2.0);
        def.setVisible(false);
        def.setLabel("MyRSI");

        assertEquals(7,  def.getPeriod());
        assertEquals(3,  def.getPeriod2());
        assertEquals(5,  def.getPeriod3());
        assertEquals(IndicatorDefinition.PriceSource.HLC3, def.getPriceSource());
        assertEquals("#ff0000", def.getColor());
        assertEquals(2.0, def.getLineWeight(), 0.001);
        assertFalse(def.isVisible());
        assertEquals("MyRSI", def.getLabel());
    }

    // ── Extra series ──────────────────────────────────────────

    @Test
    void putExtraSeries_storesAndRetrievesValues() {
        var def = new IndicatorDefinition(IndicatorDefinition.Type.BOLLINGER);
        List<Double> upper = List.of(1.0, 2.0, 3.0);
        List<Double> lower = List.of(0.5, 1.5, 2.5);
        def.putExtraSeries("upper", upper);
        def.putExtraSeries("lower", lower);

        assertEquals(upper, def.getExtraSeries("upper"));
        assertEquals(lower, def.getExtraSeries("lower"));
        assertEquals(2,     def.getExtraSeries().size());
    }

    @Test
    void setSeries_roundTrips() {
        var def = new IndicatorDefinition(IndicatorDefinition.Type.SMA);
        List<Double> data = List.of(10.0, 20.0, 30.0);
        def.setSeries(data);
        assertEquals(data, def.getSeries());
    }

    // ── Enum completeness ─────────────────────────────────────

    @ParameterizedTest(name = "Type.{0} has non-null shortName, displayName, defaultPane")
    @EnumSource(IndicatorDefinition.Type.class)
    void everyTypeHasNonNullEnumFields(IndicatorDefinition.Type type) {
        assertNotNull(type.shortName,    type + " shortName is null");
        assertFalse(type.shortName.isBlank(), type + " shortName is blank");
        assertNotNull(type.displayName,  type + " displayName is null");
        assertNotNull(type.defaultPane,  type + " defaultPane is null");
    }

    @ParameterizedTest(name = "new IndicatorDefinition(Type.{0}) succeeds")
    @EnumSource(IndicatorDefinition.Type.class)
    void canInstantiateAllTypes(IndicatorDefinition.Type type) {
        var def = new IndicatorDefinition(type);
        assertNotNull(def.getId());
        assertNotNull(def.getColor());
        assertFalse(def.getColor().isBlank());
        assertTrue(def.getPeriod() >= 0, type + " period should be >= 0");
    }

    // ── PriceSource label ─────────────────────────────────────

    @ParameterizedTest(name = "PriceSource.{0} has non-blank label")
    @EnumSource(IndicatorDefinition.PriceSource.class)
    void everyPriceSourceHasLabel(IndicatorDefinition.PriceSource src) {
        assertNotNull(src.label);
        assertFalse(src.label.isBlank());
    }

    // ── toString ─────────────────────────────────────────────

    @Test
    void toString_containsTypeAndLabel() {
        var def = new IndicatorDefinition(IndicatorDefinition.Type.ADX);
        String s = def.toString();
        assertTrue(s.contains("ADX"));
    }
}
