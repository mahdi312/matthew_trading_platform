package com.mst.matt.marketservice.charting.model;

/**
 * All supported chart drawing tool types.
 * Ported from desktop {@code model.ChartDrawingToolType} — no JPA changes.
 */
public enum ChartDrawingToolType {
    // Interaction
    SELECT,
    // Line & trend
    TREND_LINE, RAY, EXTENDED_LINE, HORIZONTAL_LINE, VERTICAL_LINE,
    PARALLEL_CHANNEL, FLAT_CHANNEL,
    // Chart Patterns
    XABCD_PATTERN, CYPHER_PATTERN, HEAD_AND_SHOULDERS, ABCD_PATTERN,
    TRIANGLE_PATTERN, THREE_DRIVES_PATTERN,
    // Elliott Waves
    ELLIOTT_IMPULSE_WAVE, ELLIOTT_CORRECTION_WAVE, ELLIOTT_TRIANGLE_WAVE,
    ELLIOTT_DOUBLE_COMBO, ELLIOTT_TRIPLE_COMBO,
    // Fibonacci
    FIB_RETRACEMENT, FIB_EXTENSION, FIB_CHANNEL, FIB_TIME_ZONES,
    FIB_SPEED_RESISTANCE, FIB_FAN, FIB_TREND_BASED_TIME, FIB_CIRCLES,
    FIB_SPIRAL, FIB_ARCS, FIB_WEDGE, FIB_PITCHFAN,
    // Gann
    GANN_BOX, GANN_SQUARE_FIXED, GANN_SQUARE, GANN_FAN,
    // Forecasting / Position tools
    LONG_POSITION, SHORT_POSITION, POSITION_FORECAST,
    BARS_PATTERN, GHOST_FEED, SECTOR,
    // Volume
    ANCHORED_VWAP, FIXED_RANGE_VOLUME_PROFILE, ANCHORED_VOLUME_PROFILE,
    // Measurers
    PRICE_RANGE, DATE_RANGE, DATE_PRICE_RANGE,
    // Annotations
    TEXT, CALLOUT, NOTE, ARROW, RULER,
    // Cycles
    CYCLIC_LINES, TIME_CYCLES, SINE_LINE,
    // Shapes
    RECTANGLE, ROTATED_RECTANGLE, ELLIPSE, TRIANGLE, POLYLINE,
    BRUSH, HIGHLIGHTER,
    // Technical overlays
    ICHIMOKU, PITCHFORK, SCHIFF_PITCHFORK, MODIFIED_SCHIFF_PITCHFORK,
    INSIDE_PITCHFORK, PITCHFAN_SCHIFF,
    // Generic
    CROSS_LINE, PATH;

    public int requiredPoints() {
        return switch (this) {
            case HORIZONTAL_LINE, VERTICAL_LINE, ANCHORED_VWAP -> 1;
            case TEXT, CALLOUT, NOTE, ARROW, LONG_POSITION, SHORT_POSITION -> 1;
            case TREND_LINE, RAY, EXTENDED_LINE, PRICE_RANGE,
                    DATE_RANGE, DATE_PRICE_RANGE, RECTANGLE -> 2;
            case PARALLEL_CHANNEL, FLAT_CHANNEL, FIB_RETRACEMENT,
                    FIB_EXTENSION, FIB_TIME_ZONES, FIB_FAN,
                    TRIANGLE, PITCHFORK, SCHIFF_PITCHFORK,
                    MODIFIED_SCHIFF_PITCHFORK -> 3;
            case ABCD_PATTERN, FIB_CHANNEL, GANN_BOX -> 4;
            case XABCD_PATTERN -> 5;
            default -> 2;
        };
    }

    public String displayName() {
        return name().replace('_', ' ').substring(0, 1)
                + name().replace('_', ' ').substring(1).toLowerCase();
    }
}
