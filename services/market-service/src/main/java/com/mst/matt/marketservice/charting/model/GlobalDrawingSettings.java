package com.mst.matt.marketservice.charting.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * User-wide drawing canvas settings (POJO — not a JPA entity).
 * Stored as a JSON blob in user preferences.
 * Ported from desktop {@code model.GlobalDrawingSettings}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalDrawingSettings {

    @Builder.Default private boolean showAllDrawings       = true;
    @Builder.Default private boolean lockAllDrawings       = false;
    @Builder.Default private boolean showHoverDeleteButton = true;
    @Builder.Default private boolean confirmHoverDelete    = false;

    // Default visual style applied to newly created drawings
    @Builder.Default private String  defaultLineColor       = "#58a6ff";
    @Builder.Default private String  defaultFibColor        = "#e8a838";
    @Builder.Default private String  defaultShapeColor      = "#58a6ff";
    @Builder.Default private String  defaultAnnotationColor = "#ffffff";
    @Builder.Default private double  defaultLineWidth       = 1.5;
    @Builder.Default private String  defaultLineStyle       = "SOLID";
    @Builder.Default private double  defaultFillOpacity     = 0.12;
}
