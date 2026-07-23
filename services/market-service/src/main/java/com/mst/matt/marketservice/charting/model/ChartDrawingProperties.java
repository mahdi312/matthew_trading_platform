package com.mst.matt.marketservice.charting.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Visual and tool-specific properties stored as JSON on {@link ChartDrawing}.
 * POJO — not a JPA entity. Ported from desktop {@code model.ChartDrawingProperties}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChartDrawingProperties {

    // ── Visual ───────────────────────────────────────────────────────────────
    @Builder.Default private String  color           = "#58a6ff";
    @Builder.Default private double  lineWidth       = 1.5;
    @Builder.Default private String  lineStyle       = "SOLID";   // SOLID|DASHED|DOTTED|DASH_DOT
    @Builder.Default private double  fillOpacity     = 0.12;
    @Builder.Default private boolean extendLeft      = false;
    @Builder.Default private boolean extendRight     = false;

    // ── Position / trade tools ────────────────────────────────────────────────
    private Double entryPrice;
    private Double stopLoss;
    private Double takeProfit;

    // ── Channel ───────────────────────────────────────────────────────────────
    private Double channelWidth;

    // ── Annotation / text ─────────────────────────────────────────────────────
    private String  backgroundColor;
    @Builder.Default private double backgroundOpacity = 0.87;
    private String  text;
    @Builder.Default private double fontSize         = 12;
    @Builder.Default private double textBoxWidth     = 0;
    @Builder.Default private double textBoxHeight    = 0;

    // ── Arrow ─────────────────────────────────────────────────────────────────
    private String arrowDirection;   // UP|DOWN|LEFT|RIGHT

    // ── Mirror / Parallel tools ───────────────────────────────────────────────
    private String mirrorAxis;
    private Double parallelOffset;

    // ── Custom Fibonacci levels ───────────────────────────────────────────────
    private List<Double> customFibLevels;

    // ── Factories ─────────────────────────────────────────────────────────────

    public static ChartDrawingProperties defaultsFor(ChartDrawingToolType type) {
        return defaultsFor(type, new GlobalDrawingSettings());
    }

    public static ChartDrawingProperties defaultsFor(ChartDrawingToolType type,
                                                      GlobalDrawingSettings settings) {
        ChartDrawingProperties p = ChartDrawingProperties.builder()
                .color(settings.getDefaultLineColor())
                .lineWidth(settings.getDefaultLineWidth())
                .lineStyle(settings.getDefaultLineStyle())
                .fillOpacity(settings.getDefaultFillOpacity())
                .build();

        switch (type) {
            case LONG_POSITION  -> { p.setColor("#26a69a"); p.setFillOpacity(0.15); }
            case SHORT_POSITION -> { p.setColor("#ef5350"); p.setFillOpacity(0.15); }
            case FIB_RETRACEMENT, FIB_EXTENSION, FIB_CHANNEL, FIB_FAN ->
                    p.setColor(settings.getDefaultFibColor());
            case RECTANGLE, ELLIPSE, TRIANGLE ->
                    p.setColor(settings.getDefaultShapeColor());
            case TEXT, CALLOUT, NOTE, ARROW, RULER ->
                    p.setColor(settings.getDefaultAnnotationColor());
            default -> { /* use defaults */ }
        }
        return p;
    }
}
