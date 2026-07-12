package com.mst.matt.tradingplatformapp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Visual and tool-specific properties stored as JSON on {@link ChartDrawing}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChartDrawingProperties {

    // ── Visual Properties ──────────────────────────────────────────────────────

    @Builder.Default
    private String color = "#58a6ff";

    @Builder.Default
    private double lineWidth = 1.5;

    /**
     * Line style: SOLID, DASHED, DOTTED, DASH_DOT.
     * Stored as a string for easy JSON round-trip.
     */
    @Builder.Default
    private String lineStyle = "SOLID";

    @Builder.Default
    private double fillOpacity = 0.12;

    @Builder.Default
    private boolean extendLeft = false;

    @Builder.Default
    private boolean extendRight = false;

    // ── Position tools ─────────────────────────────────────────────────────────

    /** Position tools: entry / SL / TP price levels. */
    private Double entryPrice;
    private Double stopLoss;
    private Double takeProfit;

    // ── Channel ────────────────────────────────────────────────────────────────

    /** Channel width in price units. */
    private Double channelWidth;

    // ── Annotations ────────────────────────────────────────────────────────────

    /**
     * Background/fill colour for NOTE and TEXT shapes.
     * Stored as a hex string with optional alpha, e.g. {@code "#2d2a00"} or {@code "#2d2a00cc"}.
     * {@code null} means "use the default background for this shape type".
     */
    private String backgroundColor;

    /**
     * Background opacity (0.0 = fully transparent, 1.0 = fully opaque) for NOTE/TEXT boxes.
     * Defaults to 0.87 (≈ 0xdd alpha) if not set.
     */
    @Builder.Default
    private double backgroundOpacity = 0.87;

    /** Text annotation content. */
    private String text;

    /** Font size for text labels. */
    @Builder.Default
    private double fontSize = 12;

    /**
     * Text / Note box width in pixels (0 = auto-size based on text length).
     * Persisted so the box size is restored on reload.
     */
    @Builder.Default
    private double textBoxWidth = 0;

    /**
     * Text / Note box height in pixels (0 = auto-size based on font size).
     * Persisted so the box size is restored on reload.
     */
    @Builder.Default
    private double textBoxHeight = 0;

    /** Arrow direction: UP, DOWN, LEFT, RIGHT. */
    private String arrowDirection;

    /**
     * True when the annotation is currently being edited (in-place text editing mode).
     * Session-only flag — excluded from JSON persistence via @JsonIgnore.
     */
    @Builder.Default
    @com.fasterxml.jackson.annotation.JsonIgnore
    private boolean editing = false;

    // ── Mirror / Copy metadata ─────────────────────────────────────────────────

    /**
     * For MIRROR copies: axis type that was used ("VERTICAL" or "HORIZONTAL").
     * Informational only — the reflected drawing is independent after creation.
     */
    private String mirrorAxis;

    /**
     * For PARALLEL_LINES copies: the offset in price units from the source line.
     * Stored for display / slider adjustment after creation.
     */
    private Double parallelOffset;

    // ── Fibonacci custom levels ────────────────────────────────────────────────

    /**
     * User-defined Fibonacci levels in the 0.0–4.0+ range (e.g. 0.236, 0.5, 1.0).
     * When {@code null} or empty, the tool uses its built-in default levels.
     * Persisted as a JSON array, e.g. {@code [0.0, 0.236, 0.382, 0.5, 0.618, 0.786, 1.0]}.
     */
    private List<Double> customFibLevels;

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Returns default properties for the given tool type using hard-coded defaults.
     * Prefer {@link #defaultsFor(ChartDrawingToolType, GlobalDrawingSettings)} when
     * per-profile settings are available.
     */
    public static ChartDrawingProperties defaultsFor(ChartDrawingToolType type) {
        return defaultsFor(type, null);
    }

    /**
     * Returns default properties for the given tool type, applying any per-profile
     * settings from {@code globalSettings}.  If {@code globalSettings} is null the
     * method falls back to the hard-coded colour palette (Issue 7.2 fix).
     *
     * @param type           the drawing tool type
     * @param globalSettings optional per-profile drawing settings (may be null)
     */
    public static ChartDrawingProperties defaultsFor(ChartDrawingToolType type,
                                                      GlobalDrawingSettings globalSettings) {
        ChartDrawingProperties p = ChartDrawingProperties.builder().build();

        // ── Apply global line width, style and fill from profile settings ──────
        if (globalSettings != null) {
            if (globalSettings.getDefaultLineWidth() > 0)
                p.setLineWidth(globalSettings.getDefaultLineWidth());
            if (globalSettings.getDefaultLineStyle() != null
                    && !globalSettings.getDefaultLineStyle().isBlank())
                p.setLineStyle(globalSettings.getDefaultLineStyle());
            if (globalSettings.getDefaultFillOpacity() >= 0)
                p.setFillOpacity(globalSettings.getDefaultFillOpacity());
        }

        // ── Per-type semantic colours (position tools always keep their colours) ──
        if (type == ChartDrawingToolType.LONG_POSITION) {
            p.setColor("#3fb950");
        } else if (type == ChartDrawingToolType.SHORT_POSITION) {
            p.setColor("#f85149");
        } else if (type == ChartDrawingToolType.FIB_RETRACEMENT
                || type == ChartDrawingToolType.FIB_EXTENSION
                || type == ChartDrawingToolType.FIB_FAN
                || type == ChartDrawingToolType.FIB_CHANNEL
                || type == ChartDrawingToolType.FIB_TIME_ZONES
                || type == ChartDrawingToolType.FIB_SPEED_RESISTANCE) {
            p.setColor(globalSettings != null && globalSettings.getDefaultFibColor() != null
                    ? globalSettings.getDefaultFibColor() : "#d29922");
        } else if (type == ChartDrawingToolType.HORIZONTAL_LINE
                || type == ChartDrawingToolType.PROFIT_TARGET_LINE) {
            p.setColor(globalSettings != null && globalSettings.getDefaultLineColor() != null
                    ? globalSettings.getDefaultLineColor() : "#388bfd");
        } else if (type == ChartDrawingToolType.STOP_LOSS_LINE) {
            p.setColor("#f85149");   // always red for stop-loss visual clarity
        } else if (type == ChartDrawingToolType.NOTE_ICON) {
            p.setColor(globalSettings != null && globalSettings.getDefaultAnnotationColor() != null
                    ? globalSettings.getDefaultAnnotationColor() : "#d29922");
            p.setText("");
        } else if (type == ChartDrawingToolType.TEXT_LABEL) {
            p.setColor(globalSettings != null && globalSettings.getDefaultAnnotationColor() != null
                    ? globalSettings.getDefaultAnnotationColor() : "#e6edf3");
            p.setText("Text");
        } else if (type == ChartDrawingToolType.CALLOUT) {
            p.setColor(globalSettings != null && globalSettings.getDefaultAnnotationColor() != null
                    ? globalSettings.getDefaultAnnotationColor() : "#58a6ff");
            p.setText("Callout");
        } else if (type == ChartDrawingToolType.RECTANGLE
                || type == ChartDrawingToolType.ELLIPSE
                || type == ChartDrawingToolType.TRIANGLE_PATTERN
                || type == ChartDrawingToolType.FLAT_CHANNEL
                || type == ChartDrawingToolType.PARALLEL_CHANNEL
                || type == ChartDrawingToolType.XABCD_PATTERN
                || type == ChartDrawingToolType.CYPHER_PATTERN
                || type == ChartDrawingToolType.HEAD_AND_SHOULDERS
                || type == ChartDrawingToolType.ABCD_PATTERN
                || type == ChartDrawingToolType.THREE_DRIVES_PATTERN) {
            p.setColor(globalSettings != null && globalSettings.getDefaultShapeColor() != null
                    ? globalSettings.getDefaultShapeColor() : "#58a6ff");
        } else {
            // Default line tools
            p.setColor(globalSettings != null && globalSettings.getDefaultLineColor() != null
                    ? globalSettings.getDefaultLineColor() : "#58a6ff");
        }
        return p;
    }

    /** Returns JavaFX {@code double[]} dash pattern from the lineStyle string. */
    public double[] getDashPattern() {
        if (lineStyle == null) return new double[0];
        return switch (lineStyle) {
            case "DASHED"   -> new double[]{8, 5};
            case "DOTTED"   -> new double[]{2, 4};
            case "DASH_DOT" -> new double[]{8, 4, 2, 4};
            default          -> new double[0];  // SOLID
        };
    }
}
