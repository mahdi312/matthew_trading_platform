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

    @Builder.Default
    private String color = "#58a6ff";

    @Builder.Default
    private double lineWidth = 1.5;

    @Builder.Default
    private double fillOpacity = 0.12;

    @Builder.Default
    private boolean extendLeft = false;

    @Builder.Default
    private boolean extendRight = false;

    /** Position tools: entry / SL / TP price levels. */
    private Double entryPrice;
    private Double stopLoss;
    private Double takeProfit;

    /** Channel width in price units. */
    private Double channelWidth;

    /** Text annotation content. */
    private String text;

    /** Font size for text labels. */
    @Builder.Default
    private double fontSize = 12;

    /** Arrow direction: UP, DOWN, LEFT, RIGHT. */
    private String arrowDirection;

    public static ChartDrawingProperties defaultsFor(ChartDrawingToolType type) {
        ChartDrawingProperties p = ChartDrawingProperties.builder().build();
        if (type == ChartDrawingToolType.LONG_POSITION) {
            p.setColor("#3fb950");
        } else if (type == ChartDrawingToolType.SHORT_POSITION) {
            p.setColor("#f85149");
        } else if (type == ChartDrawingToolType.FIB_RETRACEMENT) {
            p.setColor("#d29922");
        } else if (type == ChartDrawingToolType.HORIZONTAL_LINE
                || type == ChartDrawingToolType.PROFIT_TARGET_LINE) {
            p.setColor("#388bfd");
        } else if (type == ChartDrawingToolType.STOP_LOSS_LINE) {
            p.setColor("#f85149");
        }
        return p;
    }
}
