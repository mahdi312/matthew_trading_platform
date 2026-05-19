package com.mst.matt.tradingplatformapp.service.analysis;

import lombok.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Holds all five Ichimoku components + signal + series for chart rendering.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IchimokuResult {

    @Builder.Default private double tenkanSen   = Double.NaN;
    @Builder.Default private double kijunSen    = Double.NaN;
    @Builder.Default private double senkouSpanA = Double.NaN;
    @Builder.Default private double senkouSpanB = Double.NaN;
    @Builder.Default private double chikouSpan  = Double.NaN;

    @Builder.Default private double cloudTop    = Double.NaN;
    @Builder.Default private double cloudBottom = Double.NaN;

    private boolean aboveCloud;
    private boolean belowCloud;

    @Builder.Default private int signal = 0; // +1 bull, -1 bear, 0 neutral

    // Full series for chart overlays
    @Builder.Default private List<Double> tenkanSeries = new ArrayList<>();
    @Builder.Default private List<Double> kijunSeries  = new ArrayList<>();
    @Builder.Default private List<Double> spanASeries  = new ArrayList<>();
    @Builder.Default private List<Double> spanBSeries  = new ArrayList<>();
}