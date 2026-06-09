package com.mst.matt.tradingplatformapp.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a single configured indicator instance on the chart.
 * Each instance has its own parameters (period, price source, etc.),
 * visual style (color, line weight) and computed value series.
 *
 * Multiple instances of the same indicator type are fully supported —
 * e.g. EMA(20) and EMA(50) can both be active simultaneously.
 */
public class IndicatorDefinition {

    // ── Indicator type catalogue ──────────────────────────────

    public enum Type {
        // Trend / Moving Averages (overlay on price)
        EMA("EMA", "Exponential Moving Average", DisplayPane.PRICE, true),
        SMA("SMA", "Simple Moving Average", DisplayPane.PRICE, true),
        WMA("WMA", "Weighted Moving Average", DisplayPane.PRICE, true),
        DEMA("DEMA", "Double EMA", DisplayPane.PRICE, true),
        TEMA("TEMA", "Triple EMA", DisplayPane.PRICE, true),
        HULL_MA("HullMA", "Hull Moving Average", DisplayPane.PRICE, true),
        KAMA("KAMA", "Kaufman's Adaptive MA", DisplayPane.PRICE, true),
        ZLEMA("ZLEMA", "Zero-Lag EMA", DisplayPane.PRICE, true),
        VWAP("VWAP", "Volume-Weighted Average Price", DisplayPane.PRICE, true),

        // Bands / Channel indicators (overlay)
        BOLLINGER("Bollinger", "Bollinger Bands", DisplayPane.PRICE, false),
        KELTNER("Keltner", "Keltner Channel", DisplayPane.PRICE, false),
        DONCHIAN("Donchian", "Donchian Channel", DisplayPane.PRICE, false),
        PARABOLIC_SAR("SAR", "Parabolic SAR", DisplayPane.PRICE, false),

        // Momentum oscillators (sub-pane)
        RSI("RSI", "Relative Strength Index", DisplayPane.SUB, false),
        MACD("MACD", "MACD", DisplayPane.SUB, false),
        STOCHASTIC("Stochastic", "Stochastic Oscillator", DisplayPane.SUB, false),
        STOCH_RSI("StochRSI", "Stochastic RSI", DisplayPane.SUB, false),
        CCI("CCI", "Commodity Channel Index", DisplayPane.SUB, false),
        WILLIAMS_R("Williams %R", "Williams %R", DisplayPane.SUB, false),
        ROC("ROC", "Rate of Change", DisplayPane.SUB, false),
        DPO("DPO", "Detrended Price Oscillator", DisplayPane.SUB, false),
        AROON("Aroon", "Aroon Oscillator", DisplayPane.SUB, false),
        CMO("CMO", "Chande Momentum Oscillator", DisplayPane.SUB, false),
        FISHER("Fisher", "Fisher Transform", DisplayPane.SUB, false),
        PPO("PPO", "Percentage Price Oscillator", DisplayPane.SUB, false),

        // Volatility indicators (sub-pane)
        ATR("ATR", "Average True Range", DisplayPane.SUB, false),
        ULCER_INDEX("Ulcer", "Ulcer Index", DisplayPane.SUB, false),

        // Volume indicators (sub-pane or price)
        OBV("OBV", "On-Balance Volume", DisplayPane.SUB, false),
        MFI("MFI", "Money Flow Index", DisplayPane.SUB, false),
        CMF("CMF", "Chaikin Money Flow", DisplayPane.SUB, false),
        CHAIKIN_OSC("ChaikinOsc", "Chaikin Oscillator", DisplayPane.SUB, false),

        // Trend strength
        ADX("ADX", "Average Directional Index", DisplayPane.SUB, false),

        // Complex multi-line (overlay)
        ICHIMOKU("Ichimoku", "Ichimoku Cloud", DisplayPane.PRICE, false),

        // Support / Resistance
        SUPPORT_RESISTANCE("S/R", "Support & Resistance", DisplayPane.PRICE, false);

        public final String shortName;
        public final String displayName;
        public final DisplayPane defaultPane;
        public final boolean isSingleLine; // false = multi-line or fill

        Type(String shortName, String displayName, DisplayPane defaultPane, boolean isSingleLine) {
            this.shortName = shortName;
            this.displayName = displayName;
            this.defaultPane = defaultPane;
            this.isSingleLine = isSingleLine;
        }
    }

    public enum DisplayPane {
        PRICE,  // overlaid on candlestick chart
        SUB     // plotted in a dedicated sub-pane below price
    }

    public enum PriceSource {
        CLOSE("Close"),
        OPEN("Open"),
        HIGH("High"),
        LOW("Low"),
        HL2("HL/2 (Median)"),
        HLC3("HLC/3 (Typical)"),
        OHLC4("OHLC/4 (Mean)");

        public final String label;
        PriceSource(String label) { this.label = label; }
    }

    // ── Instance fields ───────────────────────────────────────

    /** Unique id so two EMA(20) instances can be distinguished. */
    private final String id;

    /** What kind of indicator this is. */
    private final Type type;

    /** User-defined display label (e.g. "EMA 20"). */
    private String label;

    /** Main period parameter (bars). */
    private int period;

    /** Secondary period (e.g. slow period for MACD, %D for Stochastic). */
    private int period2;

    /** Tertiary period (e.g. signal period for MACD, Ichimoku Senkou). */
    private int period3;

    /** Which price is used to compute the indicator. */
    private PriceSource priceSource;

    /** Line color as JavaFX web string, e.g. "#388bfd". */
    private String color;

    /** Line stroke width in pixels. */
    private double lineWeight;

    /** Whether this indicator is currently visible. */
    private boolean visible;

    /**
     * Computed series values — one double per bar in the full loaded history.
     * Index 0 corresponds to bars[0] (oldest bar).
     */
    private List<Double> series;

    /**
     * For multi-line indicators (Bollinger, MACD, Stochastic, Ichimoku, etc.)
     * additional named series are stored here.
     * Key examples: "upper", "lower", "middle", "signal", "histogram",
     *               "tenkan", "kijun", "spanA", "spanB", "chikou",
     *               "d", "adxPlus", "adxMinus", "aroonUp", "aroonDown"
     */
    private Map<String, List<Double>> extraSeries;

    // ── Constructor ───────────────────────────────────────────

    public IndicatorDefinition(Type type) {
        this.id          = UUID.randomUUID().toString();
        this.type        = type;
        this.label       = type.shortName;
        this.period      = defaultPeriod(type);
        this.period2     = defaultPeriod2(type);
        this.period3     = defaultPeriod3(type);
        this.priceSource = PriceSource.CLOSE;
        this.color       = defaultColor(type);
        this.lineWeight  = 1.5;
        this.visible     = true;
        this.extraSeries = new LinkedHashMap<>();
    }

    // ── Default values ─────────────────────────────────────────

    private static int defaultPeriod(Type t) {
        return switch (t) {
            case EMA, SMA, WMA, DEMA, TEMA, HULL_MA, KAMA, ZLEMA -> 20;
            case VWAP    -> 14;
            case BOLLINGER -> 20;
            case KELTNER   -> 20;
            case DONCHIAN  -> 20;
            case RSI     -> 14;
            case MACD    -> 12;
            case STOCHASTIC -> 14;
            case STOCH_RSI  -> 14;
            case CCI     -> 20;
            case WILLIAMS_R -> 14;
            case ROC     -> 12;
            case DPO     -> 20;
            case AROON   -> 25;
            case CMO     -> 14;
            case FISHER  -> 9;
            case PPO     -> 12;
            case ATR     -> 14;
            case ULCER_INDEX -> 14;
            case OBV     -> 1;
            case MFI     -> 14;
            case CMF     -> 20;
            case CHAIKIN_OSC -> 3;
            case ADX     -> 14;
            case ICHIMOKU   -> 9;
            case PARABOLIC_SAR -> 0; // SAR has no period — uses step/max
            default         -> 14;
        };
    }

    private static int defaultPeriod2(Type t) {
        return switch (t) {
            case MACD        -> 26;  // slow EMA
            case STOCHASTIC  -> 3;   // %D smoothing
            case STOCH_RSI   -> 3;   // %D
            case KELTNER     -> 14;  // ATR period
            case ICHIMOKU    -> 26;  // Kijun
            case CHAIKIN_OSC -> 10;  // slow
            case PPO         -> 26;
            default          -> 0;
        };
    }

    private static int defaultPeriod3(Type t) {
        return switch (t) {
            case MACD     -> 9;   // signal line
            case STOCH_RSI -> 14; // RSI period
            case ICHIMOKU -> 52;  // Senkou B
            default       -> 0;
        };
    }

    private static String defaultColor(Type t) {
        return switch (t) {
            case EMA          -> "#388bfd";
            case SMA          -> "#bc8cff";
            case WMA          -> "#e3b341";
            case DEMA         -> "#f0883e";
            case TEMA         -> "#3fb950";
            case HULL_MA      -> "#ff7b72";
            case KAMA         -> "#ffa657";
            case ZLEMA        -> "#79c0ff";
            case VWAP         -> "#d2a8ff";
            case BOLLINGER    -> "#388bfd";
            case KELTNER      -> "#3fb950";
            case DONCHIAN     -> "#e3b341";
            case RSI          -> "#bc8cff";
            case MACD         -> "#388bfd";
            case STOCHASTIC   -> "#ffa657";
            case STOCH_RSI    -> "#ff7b72";
            case CCI          -> "#e3b341";
            case WILLIAMS_R   -> "#79c0ff";
            case ROC          -> "#3fb950";
            case DPO          -> "#f0883e";
            case AROON        -> "#bc8cff";
            case CMO          -> "#ffa657";
            case FISHER       -> "#ff7b72";
            case PPO          -> "#3fb950";
            case ATR          -> "#d2a8ff";
            case ULCER_INDEX  -> "#f85149";
            case OBV          -> "#58a6ff";
            case MFI          -> "#56d364";
            case CMF          -> "#79c0ff";
            case CHAIKIN_OSC  -> "#ffa657";
            case ADX          -> "#ff7b72";
            case ICHIMOKU     -> "#f85149";
            case PARABOLIC_SAR -> "#e3b341";
            default            -> "#888888";
        };
    }

    // ── Getters / Setters ──────────────────────────────────────

    public String getId()                         { return id; }
    public Type   getType()                       { return type; }
    public String getLabel()                      { return label; }
    public void   setLabel(String label)          { this.label = label; }
    public int    getPeriod()                     { return period; }
    public void   setPeriod(int period)           { this.period = period; }
    public int    getPeriod2()                    { return period2; }
    public void   setPeriod2(int period2)         { this.period2 = period2; }
    public int    getPeriod3()                    { return period3; }
    public void   setPeriod3(int period3)         { this.period3 = period3; }
    public PriceSource getPriceSource()           { return priceSource; }
    public void   setPriceSource(PriceSource s)   { this.priceSource = s; }
    public String getColor()                      { return color; }
    public void   setColor(String color)          { this.color = color; }
    public double getLineWeight()                 { return lineWeight; }
    public void   setLineWeight(double w)         { this.lineWeight = w; }
    public boolean isVisible()                    { return visible; }
    public void   setVisible(boolean v)           { this.visible = v; }
    public List<Double> getSeries()               { return series; }
    public void   setSeries(List<Double> series)  { this.series = series; }
    public Map<String, List<Double>> getExtraSeries()                  { return extraSeries; }
    public void   setExtraSeries(Map<String, List<Double>> es)         { this.extraSeries = es; }
    public List<Double> getExtraSeries(String key)                     { return extraSeries.get(key); }
    public void   putExtraSeries(String key, List<Double> values)      { extraSeries.put(key, values); }

    /** Computed pane — default to type's default, can be overridden. */
    public DisplayPane getPane()                  { return type.defaultPane; }

    /** Auto-generates a readable label from type + period. */
    public void autoLabel() {
        if (period2 > 0 && period3 > 0) {
            label = type.shortName + "(" + period + "," + period2 + "," + period3 + ")";
        } else if (period2 > 0) {
            label = type.shortName + "(" + period + "," + period2 + ")";
        } else if (period > 0) {
            label = type.shortName + "(" + period + ")";
        } else {
            label = type.shortName;
        }
    }

    @Override
    public String toString() {
        return "IndicatorDefinition{id=" + id + ", type=" + type + ", label=" + label + "}";
    }
}
