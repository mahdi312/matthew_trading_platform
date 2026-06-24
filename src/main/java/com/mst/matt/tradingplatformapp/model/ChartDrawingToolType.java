package com.mst.matt.tradingplatformapp.model;

/**
 * All supported chart drawing tool types (TradingView-style annotations).
 */
public enum ChartDrawingToolType {

    // Interaction
    SELECT,

    // Line & trend
    TREND_LINE,
    RAY,
    EXTENDED_LINE,
    HORIZONTAL_LINE,
    VERTICAL_LINE,
    PARALLEL_CHANNEL,
    FLAT_CHANNEL,

    // Fibonacci
    FIB_RETRACEMENT,
    FIB_EXTENSION,
    FIB_CHANNEL,
    FIB_TIME_ZONES,
    FIB_SPEED_RESISTANCE,
    FIB_FAN,

    // Position & risk
    LONG_POSITION,
    SHORT_POSITION,
    RISK_REWARD_LABEL,
    PROFIT_TARGET_LINE,
    STOP_LOSS_LINE,

    // Shapes
    RECTANGLE,
    TRIANGLE,
    ELLIPSE,
    ANDREWS_PITCHFORK,
    GANN_FAN,

    // Annotations
    TEXT_LABEL,
    CALLOUT,
    ARROW,
    RULER,
    NOTE_ICON,

    // Projection
    PARALLEL_LINES,
    MIRROR;

    /** Minimum anchor points required to complete the drawing. */
    public int requiredPoints() {
        return switch (this) {
            case SELECT -> 0;
            case HORIZONTAL_LINE, VERTICAL_LINE, TEXT_LABEL, NOTE_ICON,
                 PROFIT_TARGET_LINE, STOP_LOSS_LINE -> 1;
            case TREND_LINE, RAY, EXTENDED_LINE, RECTANGLE, FIB_RETRACEMENT,
                 RULER, PARALLEL_LINES, FLAT_CHANNEL, ARROW -> 2;
            case FIB_EXTENSION, PARALLEL_CHANNEL, TRIANGLE, ANDREWS_PITCHFORK -> 3;
            case LONG_POSITION, SHORT_POSITION -> 2;
            case FIB_CHANNEL, FIB_TIME_ZONES, FIB_SPEED_RESISTANCE, FIB_FAN,
                 CALLOUT, ELLIPSE, GANN_FAN, RISK_REWARD_LABEL, MIRROR -> 2;
        };
    }

    public boolean isPositionTool() {
        return this == LONG_POSITION || this == SHORT_POSITION;
    }

    public String displayName() {
        return switch (this) {
            case SELECT -> "Select";
            case TREND_LINE -> "Trend Line";
            case RAY -> "Ray";
            case EXTENDED_LINE -> "Extended Line";
            case HORIZONTAL_LINE -> "Horizontal";
            case VERTICAL_LINE -> "Vertical";
            case PARALLEL_CHANNEL -> "Parallel Channel";
            case FLAT_CHANNEL -> "Flat Channel";
            case FIB_RETRACEMENT -> "Fib Retrace";
            case FIB_EXTENSION -> "Fib Extension";
            case FIB_CHANNEL -> "Fib Channel";
            case FIB_TIME_ZONES -> "Fib Time Zones";
            case FIB_SPEED_RESISTANCE -> "Fib Speed";
            case FIB_FAN -> "Fib Fan";
            case LONG_POSITION -> "Long Position";
            case SHORT_POSITION -> "Short Position";
            case RISK_REWARD_LABEL -> "R:R Label";
            case PROFIT_TARGET_LINE -> "Take Profit";
            case STOP_LOSS_LINE -> "Stop Loss";
            case RECTANGLE -> "Rectangle";
            case TRIANGLE -> "Triangle";
            case ELLIPSE -> "Ellipse";
            case ANDREWS_PITCHFORK -> "Pitchfork";
            case GANN_FAN -> "Gann Fan";
            case TEXT_LABEL -> "Text";
            case CALLOUT -> "Callout";
            case ARROW -> "Arrow";
            case RULER -> "Ruler";
            case NOTE_ICON -> "Note";
            case PARALLEL_LINES -> "Parallel Lines";
            case MIRROR -> "Mirror";
        };
    }

    public String icon() {
        return switch (this) {
            case SELECT -> "↖";
            case TREND_LINE -> "╱";
            case RAY -> "→";
            case EXTENDED_LINE -> "↔";
            case HORIZONTAL_LINE -> "─";
            case VERTICAL_LINE -> "│";
            case PARALLEL_CHANNEL -> "⫽";
            case FLAT_CHANNEL -> "▭";
            case FIB_RETRACEMENT -> "φ";
            case FIB_EXTENSION -> "φ+";
            case FIB_CHANNEL -> "φ⫽";
            case FIB_TIME_ZONES -> "φT";
            case FIB_SPEED_RESISTANCE -> "φS";
            case FIB_FAN -> "φ∠";
            case LONG_POSITION -> "▲";
            case SHORT_POSITION -> "▼";
            case RISK_REWARD_LABEL -> "R:R";
            case PROFIT_TARGET_LINE -> "TP";
            case STOP_LOSS_LINE -> "SL";
            case RECTANGLE -> "▢";
            case TRIANGLE -> "△";
            case ELLIPSE -> "○";
            case ANDREWS_PITCHFORK -> "⋔";
            case GANN_FAN -> "∠";
            case TEXT_LABEL -> "T";
            case CALLOUT -> "💬";
            case ARROW -> "➤";
            case RULER -> "📏";
            case NOTE_ICON -> "📝";
            case PARALLEL_LINES -> "∥";
            case MIRROR -> "⇄";
        };
    }
}
