package com.mst.matt.tradingplatformapp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-profile global drawing settings.
 * Stored as a JSON blob in the user-profile preferences (AppSettingsService)
 * under the key {@code "drawingSettings"}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalDrawingSettings {

    // ── Visibility & interaction ──────────────────────────────────────────────

    /** Whether all drawings are currently visible on the chart. */
    @Builder.Default
    private boolean showAllDrawings = true;

    /** Whether all drawings are locked (no move/edit). */
    @Builder.Default
    private boolean lockAllDrawings = false;

    // ── Hover / quick-delete behaviour ────────────────────────────────────────

    /** Show the quick-delete × button when hovering over a drawing. */
    @Builder.Default
    private boolean showHoverDeleteButton = true;

    /** Require a brief confirmation before hover-delete. */
    @Builder.Default
    private boolean confirmHoverDelete = false;

    // ── Default visual properties per tool group ──────────────────────────────

    @Builder.Default private String defaultLineColor  = "#58a6ff";
    @Builder.Default private String defaultFibColor   = "#d29922";
    @Builder.Default private String defaultShapeColor = "#58a6ff";
    @Builder.Default private String defaultAnnotationColor = "#e6edf3";

    @Builder.Default private double defaultLineWidth = 1.5;
    @Builder.Default private String defaultLineStyle = "SOLID";  // SOLID|DASHED|DOTTED|DASH_DOT
    @Builder.Default private double defaultFillOpacity = 0.12;
}
