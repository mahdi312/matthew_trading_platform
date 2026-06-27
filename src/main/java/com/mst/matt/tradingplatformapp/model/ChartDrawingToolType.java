package com.mst.matt.tradingplatformapp.model;

/**
 * All supported chart drawing tool types (TradingView-style annotations).
 *
 * <p>Organised into logical groups:
 * <ul>
 *   <li>Interaction – SELECT</li>
 *   <li>Line &amp; trend tools</li>
 *   <li>Chart Patterns – XABCD, Cypher, Head &amp; Shoulders, ABCD, Triangle Pattern, Three Drives</li>
 *   <li>Elliott Waves – Impulse, Correction, Triangle, Double Combo, Triple Combo</li>
 *   <li>Fibonacci – Retracement, Extension, Channel, Time Zones, Speed Resistance Fan,
 *       Trend-Based Fib Time, Circles, Spiral, Arcs, Wedge, Pitchfan</li>
 *   <li>Gann – Box, Square Fixed, Square, Fan</li>
 *   <li>Forecasting – Long/Short Position, Position Forecast, Bars Pattern, Ghost Feed, Sector</li>
 *   <li>Volume – Anchored VWAP, Fixed Range Volume Profile, Anchored Volume Profile</li>
 *   <li>Measurers – Price Range, Date Range, Date &amp; Price Range</li>
 *   <li>Annotations – Text, Callout, Note, Arrow, Ruler</li>
 *   <li>Cycles – Cyclic Lines, Time Cycles, Sine Line</li>
 *   <li>Shapes &amp; utility</li>
 * </ul>
 */
public enum ChartDrawingToolType {

    // ── Interaction ───────────────────────────────────────────────────────────
    SELECT,

    // ── Line & trend ─────────────────────────────────────────────────────────
    TREND_LINE,
    RAY,
    EXTENDED_LINE,
    HORIZONTAL_LINE,
    VERTICAL_LINE,
    PARALLEL_CHANNEL,
    FLAT_CHANNEL,

    // ── Chart Patterns ────────────────────────────────────────────────────────
    /** Gartley/Bat/Crab/Butterfly – 5-point harmonic structure (X-A-B-C-D). */
    XABCD_PATTERN,
    /** Cypher harmonic pattern with specific Fibonacci ratios. */
    CYPHER_PATTERN,
    /** Classic reversal: left shoulder, head, right shoulder. */
    HEAD_AND_SHOULDERS,
    /** 3-point harmonic pattern (A-B-C-D) with Fibonacci retracements. */
    ABCD_PATTERN,
    /** Ascending, descending, or symmetrical triangle pattern. */
    TRIANGLE_PATTERN,
    /** Harmonic pattern with 3 equal legs. */
    THREE_DRIVES_PATTERN,

    // ── Elliott Waves ─────────────────────────────────────────────────────────
    /** 5-wave impulse structure labelled 1-2-3-4-5. */
    ELLIOTT_IMPULSE_WAVE,
    /** 3-wave corrective structure labelled A-B-C. */
    ELLIOTT_CORRECTION_WAVE,
    /** 5-wave triangle correction labelled A-B-C-D-E. */
    ELLIOTT_TRIANGLE_WAVE,
    /** Double combination correction labelled W-X-Y. */
    ELLIOTT_DOUBLE_COMBO,
    /** Triple combination correction labelled W-X-Y-X-Z. */
    ELLIOTT_TRIPLE_COMBO,

    // ── Fibonacci ─────────────────────────────────────────────────────────────
    FIB_RETRACEMENT,
    FIB_EXTENSION,
    FIB_CHANNEL,
    FIB_TIME_ZONES,
    FIB_SPEED_RESISTANCE,
    FIB_FAN,
    /** Time projections based on a selected trend (Trend-Based Fib Time). */
    FIB_TREND_BASED_TIME,
    /** Concentric circles based on Fibonacci ratios. */
    FIB_CIRCLES,
    /** Fibonacci / golden spiral. */
    FIB_SPIRAL,
    /** Arcs based on Fibonacci ratios. */
    FIB_ARCS,
    /** Wedge pattern drawn using Fibonacci levels. */
    FIB_WEDGE,
    /** Andrew's Pitchfork with Fibonacci extensions (Pitchfan). */
    PITCHFAN,

    // ── Gann ─────────────────────────────────────────────────────────────────
    /** Square box with Gann angles. */
    GANN_BOX,
    /** Fixed Gann square. */
    GANN_SQUARE_FIXED,
    /** Dynamic Gann square. */
    GANN_SQUARE,
    /** Angles radiating from a pivot point. */
    GANN_FAN,

    // ── Forecasting ───────────────────────────────────────────────────────────
    /** Long position with entry, SL, TP lines. */
    LONG_POSITION,
    /** Short position with entry, SL, TP lines. */
    SHORT_POSITION,
    /** Projected position outcome based on current price. */
    POSITION_FORECAST,
    /** Visual pattern based on bar sequences. */
    BARS_PATTERN,
    /** Projected future price bars (dashed ghost feed). */
    GHOST_FEED,
    /** Sector overlay indicator. */
    SECTOR,

    // ── Volume-Based ──────────────────────────────────────────────────────────
    /** VWAP anchored to a user-selected point. */
    ANCHORED_VWAP,
    /** Volume profile over a fixed price range. */
    FIXED_RANGE_VOLUME_PROFILE,
    /** Volume profile anchored to a pivot. */
    ANCHORED_VOLUME_PROFILE,

    // ── Measurers ────────────────────────────────────────────────────────────
    /** Horizontal price measurement. */
    PRICE_RANGE,
    /** Vertical time measurement. */
    DATE_RANGE,
    /** Box measurement with both price and time (Date & Price Range). */
    DATE_AND_PRICE_RANGE,

    // ── Annotations ──────────────────────────────────────────────────────────
    TEXT_LABEL,
    CALLOUT,
    ARROW,
    RULER,
    NOTE_ICON,

    // ── Cycles ───────────────────────────────────────────────────────────────
    /** Vertical lines at regular user-defined intervals. */
    CYCLIC_LINES,
    /** Projected cycle lengths based on selected pivots. */
    TIME_CYCLES,
    /** Sinusoidal wave projection over time. */
    SINE_LINE,

    // ── Shapes & utility (legacy / existing) ─────────────────────────────────
    RECTANGLE,
    ELLIPSE,
    ANDREWS_PITCHFORK,

    // ── Internal / legacy position helpers ───────────────────────────────────
    RISK_REWARD_LABEL,
    PROFIT_TARGET_LINE,
    STOP_LOSS_LINE,

    // ── Projection ───────────────────────────────────────────────────────────
    PARALLEL_LINES,
    MIRROR;

    // ─────────────────────────────────────────────────────────────────────────
    //  Minimum anchor-point counts
    // ─────────────────────────────────────────────────────────────────────────

    /** Minimum anchor points required to complete the drawing. */
    public int requiredPoints() {
        return switch (this) {
            case SELECT -> 0;

            // 1-point tools
            case HORIZONTAL_LINE, VERTICAL_LINE, TEXT_LABEL, NOTE_ICON,
                 PROFIT_TARGET_LINE, STOP_LOSS_LINE,
                 ANCHORED_VWAP, FIB_TIME_ZONES -> 1;

            // 2-point tools
            case TREND_LINE, RAY, EXTENDED_LINE, RECTANGLE, FIB_RETRACEMENT,
                 RULER, PARALLEL_LINES, FLAT_CHANNEL, ARROW,
                 FIB_CHANNEL, FIB_SPEED_RESISTANCE, FIB_FAN,
                 ELLIPSE, GANN_FAN, RISK_REWARD_LABEL, MIRROR,
                 LONG_POSITION, SHORT_POSITION, CALLOUT,
                 GANN_BOX, GANN_SQUARE_FIXED, GANN_SQUARE,
                 POSITION_FORECAST, DATE_RANGE, PRICE_RANGE, DATE_AND_PRICE_RANGE,
                 FIB_TREND_BASED_TIME, FIB_CIRCLES, FIB_ARCS, FIB_WEDGE,
                 CYCLIC_LINES, TIME_CYCLES, SINE_LINE,
                 FIXED_RANGE_VOLUME_PROFILE, ANCHORED_VOLUME_PROFILE,
                 GHOST_FEED, BARS_PATTERN -> 2;

            // 3-point tools
            case FIB_EXTENSION, PARALLEL_CHANNEL, ANDREWS_PITCHFORK,
                 PITCHFAN, FIB_SPIRAL,
                 ABCD_PATTERN, ELLIOTT_CORRECTION_WAVE, ELLIOTT_DOUBLE_COMBO,
                 CYPHER_PATTERN, HEAD_AND_SHOULDERS, SECTOR,
                 TRIANGLE_PATTERN -> 3;

            // 5-point tools
            case XABCD_PATTERN, THREE_DRIVES_PATTERN,
                 ELLIOTT_IMPULSE_WAVE, ELLIOTT_TRIANGLE_WAVE,
                 ELLIOTT_TRIPLE_COMBO -> 5;
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Category helpers
    // ─────────────────────────────────────────────────────────────────────────

    public boolean isPositionTool() {
        return this == LONG_POSITION || this == SHORT_POSITION;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Display names
    // ─────────────────────────────────────────────────────────────────────────

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

            // Chart Patterns
            case XABCD_PATTERN -> "XABCD Pattern";
            case CYPHER_PATTERN -> "Cypher Pattern";
            case HEAD_AND_SHOULDERS -> "Head & Shoulders";
            case ABCD_PATTERN -> "ABCD Pattern";
            case TRIANGLE_PATTERN -> "Triangle Pattern";
            case THREE_DRIVES_PATTERN -> "Three Drives";

            // Elliott Waves
            case ELLIOTT_IMPULSE_WAVE -> "Elliott Impulse (1-2-3-4-5)";
            case ELLIOTT_CORRECTION_WAVE -> "Elliott Correction (A-B-C)";
            case ELLIOTT_TRIANGLE_WAVE -> "Elliott Triangle (A-B-C-D-E)";
            case ELLIOTT_DOUBLE_COMBO -> "Elliott Double Combo (W-X-Y)";
            case ELLIOTT_TRIPLE_COMBO -> "Elliott Triple Combo (W-X-Y-X-Z)";

            // Fibonacci
            case FIB_RETRACEMENT -> "Fib Retracement";
            case FIB_EXTENSION -> "Trend-Based Fib Extension";
            case FIB_CHANNEL -> "Fib Channel";
            case FIB_TIME_ZONES -> "Fib Time Zone";
            case FIB_SPEED_RESISTANCE -> "Fib Speed Resistance Fan";
            case FIB_FAN -> "Fib Fan";
            case FIB_TREND_BASED_TIME -> "Trend-Based Fib Time";
            case FIB_CIRCLES -> "Fib Circles";
            case FIB_SPIRAL -> "Fib Spiral";
            case FIB_ARCS -> "Fib Speed Resistance Arcs";
            case FIB_WEDGE -> "Fib Wedge";
            case PITCHFAN -> "Pitchfan";

            // Gann
            case GANN_BOX -> "Gann Box";
            case GANN_SQUARE_FIXED -> "Gann Square Fixed";
            case GANN_SQUARE -> "Gann Square";
            case GANN_FAN -> "Gann Fan";

            // Forecasting
            case LONG_POSITION -> "Long Position";
            case SHORT_POSITION -> "Short Position";
            case POSITION_FORECAST -> "Position Forecast";
            case BARS_PATTERN -> "Bars Pattern";
            case GHOST_FEED -> "Ghost Feed";
            case SECTOR -> "Sector";

            // Volume
            case ANCHORED_VWAP -> "Anchored VWAP";
            case FIXED_RANGE_VOLUME_PROFILE -> "Fixed Range Volume Profile";
            case ANCHORED_VOLUME_PROFILE -> "Anchored Volume Profile";

            // Measurers
            case PRICE_RANGE -> "Price Range";
            case DATE_RANGE -> "Date Range";
            case DATE_AND_PRICE_RANGE -> "Date & Price Range";

            // Annotations
            case TEXT_LABEL -> "Text";
            case CALLOUT -> "Callout";
            case ARROW -> "Arrow";
            case RULER -> "Ruler";
            case NOTE_ICON -> "Note";

            // Cycles
            case CYCLIC_LINES -> "Cyclic Lines";
            case TIME_CYCLES -> "Time Cycles";
            case SINE_LINE -> "Sine Line";

            // Shapes & utility
            case RECTANGLE -> "Rectangle";
            case ELLIPSE -> "Ellipse";
            case ANDREWS_PITCHFORK -> "Andrews Pitchfork";
            case RISK_REWARD_LABEL -> "R:R Label";
            case PROFIT_TARGET_LINE -> "Take Profit";
            case STOP_LOSS_LINE -> "Stop Loss";
            case PARALLEL_LINES -> "Parallel Lines";
            case MIRROR -> "Mirror";
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Icons (Unicode / emoji)
    // ─────────────────────────────────────────────────────────────────────────

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

            // Chart Patterns
            case XABCD_PATTERN -> "XABCD";
            case CYPHER_PATTERN -> "⟨C⟩";
            case HEAD_AND_SHOULDERS -> "⌃";
            case ABCD_PATTERN -> "ABCD";
            case TRIANGLE_PATTERN -> "△";
            case THREE_DRIVES_PATTERN -> "3D";

            // Elliott Waves
            case ELLIOTT_IMPULSE_WAVE -> "12345";
            case ELLIOTT_CORRECTION_WAVE -> "ABC";
            case ELLIOTT_TRIANGLE_WAVE -> "ABCDE";
            case ELLIOTT_DOUBLE_COMBO -> "WXY";
            case ELLIOTT_TRIPLE_COMBO -> "WXYXZ";

            // Fibonacci
            case FIB_RETRACEMENT -> "φ";
            case FIB_EXTENSION -> "φ+";
            case FIB_CHANNEL -> "φ⫽";
            case FIB_TIME_ZONES -> "φT";
            case FIB_SPEED_RESISTANCE -> "φS";
            case FIB_FAN -> "φ∠";
            case FIB_TREND_BASED_TIME -> "φt";
            case FIB_CIRCLES -> "φ○";
            case FIB_SPIRAL -> "φ🌀";
            case FIB_ARCS -> "φ)";
            case FIB_WEDGE -> "φ∧";
            case PITCHFAN -> "⋔φ";

            // Gann
            case GANN_BOX -> "⊞G";
            case GANN_SQUARE_FIXED -> "□G";
            case GANN_SQUARE -> "▪G";
            case GANN_FAN -> "∠G";

            // Forecasting
            case LONG_POSITION -> "▲";
            case SHORT_POSITION -> "▼";
            case POSITION_FORECAST -> "◈";
            case BARS_PATTERN -> "⬚";
            case GHOST_FEED -> "⤞";
            case SECTOR -> "⊙";

            // Volume
            case ANCHORED_VWAP -> "V̄";
            case FIXED_RANGE_VOLUME_PROFILE -> "▐VP";
            case ANCHORED_VOLUME_PROFILE -> "⊕VP";

            // Measurers
            case PRICE_RANGE -> "↕$";
            case DATE_RANGE -> "↔T";
            case DATE_AND_PRICE_RANGE -> "⊡";

            // Annotations
            case TEXT_LABEL -> "T";
            case CALLOUT -> "💬";
            case ARROW -> "➤";
            case RULER -> "📏";
            case NOTE_ICON -> "📝";

            // Cycles
            case CYCLIC_LINES -> "⫶";
            case TIME_CYCLES -> "⟳";
            case SINE_LINE -> "∿";

            // Shapes & utility
            case RECTANGLE -> "▢";
            case ELLIPSE -> "○";
            case ANDREWS_PITCHFORK -> "⋔";
            case RISK_REWARD_LABEL -> "R:R";
            case PROFIT_TARGET_LINE -> "TP";
            case STOP_LOSS_LINE -> "SL";
            case PARALLEL_LINES -> "∥";
            case MIRROR -> "⇄";
        };
    }
}
