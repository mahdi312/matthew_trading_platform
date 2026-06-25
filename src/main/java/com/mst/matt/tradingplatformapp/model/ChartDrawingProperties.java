package com.mst.matt.tradingplatformapp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    /** Text annotation content. */
    private String text;

    /** Font size for text labels. */
    @Builder.Default
    private double fontSize = 12;

    /** Arrow direction: UP, DOWN, LEFT, RIGHT. */
    private String arrowDirection;

    /**
     * Text box dimensions (for TEXT_LABEL, NOTE_ICON, CALLOUT).
     * Stored as screen-independent units — pixel size at 1:1 scale.
     * 0 means "auto" (computed from text length).
     */
    @Builder.Default
    private double boxWidth = 0;

    @Builder.Default
    private double boxHeight = 0;

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

    // ── Factory ───────────────────────────────────────────────────────────────

    public static ChartDrawingProperties defaultsFor(ChartDrawingToolType type) {
        ChartDrawingProperties p = ChartDrawingProperties.builder().build();
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
            p.setColor("#d29922");
        } else if (type == ChartDrawingToolType.HORIZONTAL_LINE
                || type == ChartDrawingToolType.PROFIT_TARGET_LINE) {
            p.setColor("#388bfd");
        } else if (type == ChartDrawingToolType.STOP_LOSS_LINE) {
            p.setColor("#f85149");
        } else if (type == ChartDrawingToolType.NOTE_ICON) {
            p.setColor("#d29922");
            p.setText("");
        } else if (type == ChartDrawingToolType.TEXT_LABEL) {
            p.setText("Text");
        } else if (type == ChartDrawingToolType.CALLOUT) {
            p.setText("Callout");
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
