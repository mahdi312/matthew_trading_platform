package com.mst.matt.marketservice.charting.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A single configured indicator instance on the chart.
 * Not a JPA entity — serialized as JSON within chart state.
 * Ported from desktop {@code model.IndicatorDefinition}.
 */
public class IndicatorDefinition {

    // ── Indicator type catalogue ──────────────────────────────────────────────

    public enum Type {
        EMA("EMA",          "Exponential Moving Average",       DisplayPane.PRICE, true),
        SMA("SMA",          "Simple Moving Average",            DisplayPane.PRICE, true),
        WMA("WMA",          "Weighted Moving Average",          DisplayPane.PRICE, true),
        DEMA("DEMA",        "Double EMA",                       DisplayPane.PRICE, true),
        TEMA("TEMA",        "Triple EMA",                       DisplayPane.PRICE, true),
        HULL_MA("HullMA",   "Hull Moving Average",              DisplayPane.PRICE, true),
        KAMA("KAMA",        "Kaufman's Adaptive MA",            DisplayPane.PRICE, true),
        ZLEMA("ZLEMA",      "Zero-Lag EMA",                     DisplayPane.PRICE, true),
        VWAP("VWAP",        "Volume-Weighted Average Price",    DisplayPane.PRICE, true),

        BOLLINGER("Bollinger","Bollinger Bands",                DisplayPane.PRICE, false),
        KELTNER("Keltner",  "Keltner Channel",                 DisplayPane.PRICE, false),
        DONCHIAN("Donchian","Donchian Channel",                 DisplayPane.PRICE, false),
        PARABOLIC_SAR("SAR","Parabolic SAR",                   DisplayPane.PRICE, false),

        RSI("RSI",          "Relative Strength Index",          DisplayPane.SUB,   false),
        MACD("MACD",        "MACD",                            DisplayPane.SUB,   false),
        STOCHASTIC("Stochastic","Stochastic Oscillator",       DisplayPane.SUB,   false),
        STOCH_RSI("StochRSI","Stochastic RSI",                 DisplayPane.SUB,   false),
        CCI("CCI",          "Commodity Channel Index",          DisplayPane.SUB,   false),
        WILLIAMS_R("Williams %R","Williams %R",                DisplayPane.SUB,   false),
        ROC("ROC",          "Rate of Change",                   DisplayPane.SUB,   false),
        DPO("DPO",          "Detrended Price Oscillator",       DisplayPane.SUB,   false),
        AROON("Aroon",      "Aroon Oscillator",                 DisplayPane.SUB,   false),
        CMO("CMO",          "Chande Momentum Oscillator",       DisplayPane.SUB,   false),
        FISHER("Fisher",    "Fisher Transform",                 DisplayPane.SUB,   false),
        PPO("PPO",          "Percentage Price Oscillator",      DisplayPane.SUB,   false),

        ATR("ATR",          "Average True Range",               DisplayPane.SUB,   false),
        ULCER_INDEX("Ulcer","Ulcer Index",                      DisplayPane.SUB,   false),

        OBV("OBV",          "On-Balance Volume",                DisplayPane.SUB,   false),
        MFI("MFI",          "Money Flow Index",                 DisplayPane.SUB,   false),
        CMF("CMF",          "Chaikin Money Flow",               DisplayPane.SUB,   false),
        CHAIKIN_OSC("ChaikinOsc","Chaikin Oscillator",          DisplayPane.SUB,   false),

        ADX("ADX",          "Average Directional Index",        DisplayPane.SUB,   false),

        ICHIMOKU("Ichimoku","Ichimoku Cloud",                   DisplayPane.PRICE, false),

        TWO_MA("2MA",       "Dual Moving Average (2 lines)",    DisplayPane.PRICE, false),
        THREE_MA("3MA",     "Triple Moving Average (3 lines)",  DisplayPane.PRICE, false),

        SUPPORT_RESISTANCE("S/R","Support & Resistance",        DisplayPane.PRICE, false);

        public final String shortName;
        public final String displayName;
        public final DisplayPane defaultPane;
        public final boolean isSingleLine;

        Type(String s, String d, DisplayPane p, boolean single) {
            shortName = s; displayName = d; defaultPane = p; isSingleLine = single;
        }
    }

    public enum DisplayPane { PRICE, SUB }

    public enum PriceSource {
        CLOSE("Close"), OPEN("Open"), HIGH("High"), LOW("Low"),
        HL2("HL/2 (Median)"), HLC3("HLC/3 (Typical)"), OHLC4("OHLC/4 (Mean)");
        public final String label;
        PriceSource(String l) { label = l; }
    }

    // ── Instance fields ───────────────────────────────────────────────────────

    private final String id;
    private final Type   type;
    private String       label;
    private int          period;
    private int          period2;
    private int          period3;
    private PriceSource  priceSource = PriceSource.CLOSE;
    private String       color   = "#58a6ff";
    private String       color2  = "#ef5350";
    private String       color3  = "#26a69a";
    private double       lineWeight = 1.5;
    private boolean      visible    = true;

    /** Computed series values for chart rendering. */
    private List<Double>              series      = List.of();
    private Map<String, List<Double>> extraSeries = new LinkedHashMap<>();

    public IndicatorDefinition(Type type) {
        this.id   = UUID.randomUUID().toString();
        this.type = type;
        this.period  = defaultPeriod(type);
        this.period2 = defaultPeriod2(type);
        this.period3 = defaultPeriod3(type);
        autoLabel();
    }

    public void autoLabel() {
        this.label = period > 0
                ? type.shortName + " (" + period + ")"
                : type.shortName;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String       getId()         { return id; }
    public Type         getType()       { return type; }
    public String       getLabel()      { return label; }
    public void         setLabel(String l) { this.label = l; }
    public int          getPeriod()     { return period; }
    public void         setPeriod(int p){ this.period = p; autoLabel(); }
    public int          getPeriod2()    { return period2; }
    public void         setPeriod2(int p){ this.period2 = p; }
    public int          getPeriod3()    { return period3; }
    public void         setPeriod3(int p){ this.period3 = p; }
    public PriceSource  getPriceSource(){ return priceSource; }
    public void         setPriceSource(PriceSource ps){ this.priceSource = ps; }
    public String       getColor()      { return color; }
    public void         setColor(String c){ this.color = c; }
    public String       getColor2()     { return color2; }
    public void         setColor2(String c){ this.color2 = c; }
    public String       getColor3()     { return color3; }
    public void         setColor3(String c){ this.color3 = c; }
    public double       getLineWeight() { return lineWeight; }
    public void         setLineWeight(double w){ this.lineWeight = w; }
    public boolean      isVisible()     { return visible; }
    public void         setVisible(boolean v){ this.visible = v; }
    public List<Double> getSeries()     { return series; }
    public void         setSeries(List<Double> s){ this.series = s; }
    public Map<String,List<Double>> getExtraSeries() { return extraSeries; }
    public void setExtraSeries(Map<String,List<Double>> m){ this.extraSeries = m; }
    public void putExtraSeries(String key, List<Double> values) { this.extraSeries.put(key, values); }

    // ── Default periods ───────────────────────────────────────────────────────

    private static int defaultPeriod(Type t) {
        return switch (t) {
            case EMA, SMA, WMA, DEMA, TEMA, HULL_MA, KAMA, ZLEMA -> 20;
            case RSI, MFI, ATR, ADX, ULCER_INDEX, CMO -> 14;
            case BOLLINGER, KELTNER, DONCHIAN, CCI -> 20;
            case MACD -> 12;
            case STOCHASTIC, STOCH_RSI -> 14;
            case WILLIAMS_R -> 14;
            case ROC, DPO -> 12;
            default -> 0;
        };
    }

    private static int defaultPeriod2(Type t) {
        return switch (t) {
            case MACD    -> 26;
            case STOCHASTIC, STOCH_RSI -> 3;
            default      -> 0;
        };
    }

    private static int defaultPeriod3(Type t) {
        return switch (t) {
            case MACD    -> 9;
            default      -> 0;
        };
    }
}
