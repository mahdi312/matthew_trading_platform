package com.mst.matt.tradingplatformapp.ui.chart.drawing;

import com.mst.matt.tradingplatformapp.model.ChartDrawing;
import com.mst.matt.tradingplatformapp.model.ChartDrawingProperties;
import com.mst.matt.tradingplatformapp.model.ChartDrawingToolType;
import com.mst.matt.tradingplatformapp.model.ChartPoint;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Renders user chart drawings in price-time coordinates.
 */
public final class DrawingRenderer {

    private static final Font FONT_SMALL = Font.font("Segoe UI", 11);
    private static final double[] FIB_LEVELS = {0, 0.236, 0.382, 0.5, 0.618, 0.786, 1.0};
    private static final double[] FIB_EXT_LEVELS = {1.272, 1.618, 2.618};

    private DrawingRenderer() {}

    public record RenderContext(
            double left, double right, double priceTop, double priceBottom, double priceH,
            int startBarIndex, int visibleBars, double maxPrice, double minPrice,
            List<OhlcvBar> bars
    ) {
        public double plotWidth() { return right - left; }
    }

    public static void render(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                              boolean selected, boolean showAnchors) {
        if (d.getPoints() == null || d.getPoints().isEmpty()) return;
        ChartDrawingProperties props = d.getProperties() != null
                ? d.getProperties()
                : ChartDrawingProperties.defaultsFor(d.getToolType());
        Color color = safeColor(props.getColor(), "#58a6ff");
        gc.setLineWidth(props.getLineWidth());
        gc.setLineDashes();

        switch (d.getToolType()) {
            case TREND_LINE -> drawTwoPointLine(gc, d, ctx, color, false, false);
            case RAY -> drawTwoPointLine(gc, d, ctx, color, false, true);
            case EXTENDED_LINE -> drawTwoPointLine(gc, d, ctx, color, true, true);
            case HORIZONTAL_LINE, PROFIT_TARGET_LINE, STOP_LOSS_LINE ->
                    drawHorizontalLine(gc, d, ctx, color, props);
            case VERTICAL_LINE -> drawVerticalLine(gc, d, ctx, color);
            case RECTANGLE, FLAT_CHANNEL -> drawRectangle(gc, d, ctx, color, props);
            case FIB_RETRACEMENT -> drawFibRetracement(gc, d, ctx, props);
            case FIB_EXTENSION -> drawFibExtension(gc, d, ctx, props);
            case PARALLEL_CHANNEL -> drawParallelChannel(gc, d, ctx, color, props);
            case LONG_POSITION, SHORT_POSITION -> drawPosition(gc, d, ctx, props, selected);
            case TEXT_LABEL, CALLOUT, NOTE_ICON -> drawTextLabel(gc, d, ctx, props);
            case RULER -> drawRuler(gc, d, ctx, color);
            case ARROW -> drawArrow(gc, d, ctx, color, props);
            case FIB_FAN -> drawFibFan(gc, d, ctx, props);
            case FIB_TIME_ZONES -> drawFibTimeZones(gc, d, ctx, props);
            default -> drawTwoPointLine(gc, d, ctx, color, false, false);
        }

        if (selected && showAnchors && !d.isLocked()) {
            drawAnchors(gc, d, ctx);
        }
    }

    private static void drawTwoPointLine(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                         Color color, boolean extendLeft, boolean extendRight) {
        if (d.getPoints().size() < 2) return;
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y1 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y2 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        if (extendLeft || extendRight) {
            double[] ext = extendLine(x1, y1, x2, y2, ctx.left, ctx.right,
                    ctx.priceTop, ctx.priceBottom, extendLeft, extendRight);
            x1 = ext[0]; y1 = ext[1]; x2 = ext[2]; y2 = ext[3];
        }
        gc.setStroke(color);
        gc.strokeLine(x1, y1, x2, y2);
    }

    private static void drawHorizontalLine(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                           Color color, ChartDrawingProperties props) {
        double price = d.getPoints().getFirst().getPrice();
        double y = priceToY(price, ctx);
        gc.setStroke(color);
        gc.strokeLine(ctx.left, y, ctx.right, y);
        gc.setFill(color);
        gc.setFont(FONT_SMALL);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(formatPrice(price), ctx.right + 4, y + 4);
    }

    private static void drawVerticalLine(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                         Color color) {
        double x = timeToX(d.getPoints().getFirst().getTime(), ctx);
        gc.setStroke(color);
        gc.setLineDashes(4, 4);
        gc.strokeLine(x, ctx.priceTop, x, ctx.priceBottom);
        gc.setLineDashes();
    }

    private static void drawRectangle(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                      Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y1 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y2 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        double x = Math.min(x1, x2), y = Math.min(y1, y2);
        double w = Math.abs(x2 - x1), h = Math.abs(y2 - y1);
        gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), props.getFillOpacity()));
        gc.fillRect(x, y, w, h);
        gc.setStroke(color);
        gc.strokeRect(x, y, w, h);
    }

    private static void drawFibRetracement(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                            ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double p0 = d.getPoints().get(0).getPrice();
        double p1 = d.getPoints().get(1).getPrice();
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double xLeft = Math.min(x1, x2);
        double xRight = Math.max(x1, x2);
        Color base = safeColor(props.getColor(), "#d29922");
        gc.setFont(FONT_SMALL);
        for (double level : FIB_LEVELS) {
            double price = p0 + (p1 - p0) * level;
            double y = priceToY(price, ctx);
            gc.setStroke(base.deriveColor(0, 1, 1, 0.5 + level * 0.3));
            gc.setLineDashes(3, 3);
            gc.strokeLine(xLeft, y, ctx.right, y);
            gc.setLineDashes();
            gc.setFill(base);
            gc.setTextAlign(TextAlignment.LEFT);
            gc.fillText(String.format("%.1f%%  %s", level * 100, formatPrice(price)), xLeft + 4, y - 2);
        }
        gc.setStroke(base);
        gc.strokeLine(xLeft, priceToY(p0, ctx), xRight, priceToY(p1, ctx));
    }

    private static void drawFibExtension(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                         ChartDrawingProperties props) {
        if (d.getPoints().size() < 3) return;
        double pA = d.getPoints().get(0).getPrice();
        double pB = d.getPoints().get(1).getPrice();
        double pC = d.getPoints().get(2).getPrice();
        double swing = pB - pA;
        Color base = safeColor(props.getColor(), "#d29922");
        gc.setFont(FONT_SMALL);
        for (double mult : FIB_EXT_LEVELS) {
            double price = pC + swing * mult;
            double y = priceToY(price, ctx);
            gc.setStroke(base);
            gc.setLineDashes(4, 4);
            gc.strokeLine(ctx.left, y, ctx.right, y);
            gc.setLineDashes();
            gc.setFill(base);
            gc.fillText(String.format("%.3f  %s", mult, formatPrice(price)), ctx.left + 4, y - 2);
        }
    }

    private static void drawParallelChannel(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                            Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y1 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y2 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        double width = props.getChannelWidth() != null ? props.getChannelWidth() : 0;
        double offsetY = priceToY(d.getPoints().get(0).getPrice() + width, ctx) - y1;
        gc.setStroke(color);
        gc.strokeLine(x1, y1, x2, y2);
        gc.strokeLine(x1, y1 + offsetY, x2, y2 + offsetY);
    }

    private static void drawPosition(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                     ChartDrawingProperties props, boolean selected) {
        if (d.getPoints().size() < 2 || props.getEntryPrice() == null) return;
        boolean isLong = d.getToolType() == ChartDrawingToolType.LONG_POSITION;
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double left = Math.min(x1, x2), right = Math.max(x1, x2);
        double entry = props.getEntryPrice();
        Double sl = props.getStopLoss();
        Double tp = props.getTakeProfit();
        double yEntry = priceToY(entry, ctx);

        if (tp != null) {
            double yTp = priceToY(tp, ctx);
            double top = Math.min(yEntry, yTp);
            double h = Math.abs(yEntry - yTp);
            gc.setFill(Color.web(isLong ? "#3fb95033" : "#f8514933"));
            gc.fillRect(left, top, right - left, h);
        }
        if (sl != null) {
            double ySl = priceToY(sl, ctx);
            double top = Math.min(yEntry, ySl);
            double h = Math.abs(yEntry - ySl);
            gc.setFill(Color.web(isLong ? "#f8514933" : "#3fb95033"));
            gc.fillRect(left, top, right - left, h);
        }

        gc.setLineWidth(1.5);
        drawPriceLine(gc, left, right, yEntry, "#3fb950", "Entry " + formatPrice(entry));
        if (sl != null)
            drawPriceLine(gc, left, right, priceToY(sl, ctx), "#f85149", "SL " + formatPrice(sl));
        if (tp != null)
            drawPriceLine(gc, left, right, priceToY(tp, ctx), "#388bfd", "TP " + formatPrice(tp));

        if (sl != null && tp != null) {
            double risk = Math.abs(entry - sl);
            double reward = Math.abs(tp - entry);
            if (risk > 0) {
                double rr = reward / risk;
                gc.setFill(Color.web("#e6edf3"));
                gc.setFont(Font.font("Segoe UI", 12));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText(String.format("R:R 1:%.2f", rr), (left + right) / 2, yEntry - 8);
            }
        }

        if (selected) {
            gc.setFill(Color.web("#e6edf3cc"));
            gc.fillRoundRect(right + 4, yEntry - 10, 52, 20, 4, 4);
            gc.setFill(Color.web("#0d1117"));
            gc.setFont(FONT_SMALL);
            gc.fillText("+ Trade", right + 8, yEntry + 4);
        }
    }

    private static void drawPriceLine(GraphicsContext gc, double left, double right,
                                      double y, String colorHex, String label) {
        gc.setStroke(Color.web(colorHex));
        gc.setLineDashes();
        gc.strokeLine(left, y, right, y);
        gc.setFill(Color.web(colorHex));
        gc.setFont(FONT_SMALL);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(label, left + 4, y - 3);
    }

    private static void drawTextLabel(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                      ChartDrawingProperties props) {
        ChartPoint pt = d.getPoints().getFirst();
        double x = timeToX(pt.getTime(), ctx);
        double y = priceToY(pt.getPrice(), ctx);
        String text = props.getText() != null ? props.getText() : "Label";
        gc.setFill(Color.web("#1c2128ee"));
        gc.fillRoundRect(x, y, Math.max(60, text.length() * 7 + 16), 22, 4, 4);
        gc.setStroke(safeColor(props.getColor(), "#58a6ff"));
        gc.strokeRoundRect(x, y, Math.max(60, text.length() * 7 + 16), 22, 4, 4);
        gc.setFill(Color.web("#e6edf3"));
        gc.setFont(Font.font("Segoe UI", props.getFontSize()));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(text, x + 8, y + 15);
    }

    private static void drawRuler(GraphicsContext gc, ChartDrawing d, RenderContext ctx, Color color) {
        if (d.getPoints().size() < 2) return;
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y1 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y2 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        gc.setStroke(color);
        gc.setLineDashes(5, 3);
        gc.strokeLine(x1, y1, x2, y2);
        gc.setLineDashes();
        double priceDiff = d.getPoints().get(1).getPrice() - d.getPoints().get(0).getPrice();
        double pct = d.getPoints().get(0).getPrice() != 0
                ? priceDiff / d.getPoints().get(0).getPrice() * 100 : 0;
        gc.setFill(color);
        gc.setFont(FONT_SMALL);
        gc.fillText(String.format("%s (%.2f%%)", formatPrice(priceDiff), pct),
                (x1 + x2) / 2 + 6, (y1 + y2) / 2 - 6);
    }

    private static void drawArrow(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                  Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y1 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y2 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        gc.setStroke(color);
        gc.setLineWidth(2);
        gc.strokeLine(x1, y1, x2, y2);
        double angle = Math.atan2(y2 - y1, x2 - x1);
        double len = 10;
        gc.strokeLine(x2, y2,
                x2 - len * Math.cos(angle - Math.PI / 6), y2 - len * Math.sin(angle - Math.PI / 6));
        gc.strokeLine(x2, y2,
                x2 - len * Math.cos(angle + Math.PI / 6), y2 - len * Math.sin(angle + Math.PI / 6));
    }

    private static void drawFibFan(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                   ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x0 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y0 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x1 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y1 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        Color base = safeColor(props.getColor(), "#d29922");
        for (double level : FIB_LEVELS) {
            double yEnd = y0 + (y1 - y0) * level;
            gc.setStroke(base.deriveColor(0, 1, 1, 0.4 + level * 0.4));
            gc.strokeLine(x0, y0, ctx.right, y0 + (yEnd - y0) * (ctx.right - x0) / Math.max(1, x1 - x0));
        }
    }

    private static void drawFibTimeZones(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                         ChartDrawingProperties props) {
        if (d.getPoints().isEmpty()) return;
        int startIdx = DrawingCoordinateMapper.timeToBarIndex(ctx.bars, d.getPoints().getFirst().getTime());
        int[] fibSeq = {1, 2, 3, 5, 8, 13, 21};
        Color base = safeColor(props.getColor(), "#d29922");
        double barW = ctx.plotWidth() / Math.max(1, ctx.visibleBars);
        for (int fib : fibSeq) {
            int idx = startIdx + fib;
            if (idx < 0 || idx >= ctx.bars.size()) continue;
            double x = ctx.left + (idx - ctx.startBarIndex + 0.5) * barW;
            if (x < ctx.left || x > ctx.right) continue;
            gc.setStroke(base.deriveColor(0, 1, 1, 0.35));
            gc.setLineDashes(3, 5);
            gc.strokeLine(x, ctx.priceTop, x, ctx.priceBottom);
        }
        gc.setLineDashes();
    }

    private static void drawAnchors(GraphicsContext gc, ChartDrawing d, RenderContext ctx) {
        gc.setFill(Color.web("#ffffff"));
        gc.setStroke(Color.web("#388bfd"));
        gc.setLineWidth(1.5);
        for (ChartPoint pt : d.getPoints()) {
            double x = timeToX(pt.getTime(), ctx);
            double y = priceToY(pt.getPrice(), ctx);
            gc.fillOval(x - 4, y - 4, 8, 8);
            gc.strokeOval(x - 4, y - 4, 8, 8);
        }
    }

    public static double timeToX(LocalDateTime time, RenderContext ctx) {
        int idx = DrawingCoordinateMapper.timeToBarIndex(ctx.bars, time);
        double barW = ctx.plotWidth() / Math.max(1, ctx.visibleBars);
        return ctx.left + (idx - ctx.startBarIndex + 0.5) * barW;
    }

    public static double priceToY(double price, RenderContext ctx) {
        double range = Math.max(ctx.maxPrice - ctx.minPrice, 1e-10);
        return ctx.priceTop + ctx.priceH * (1.0 - (price - ctx.minPrice) / range);
    }

    private static double[] extendLine(double x1, double y1, double x2, double y2,
                                       double minX, double maxX, double minY, double maxY,
                                       boolean left, boolean right) {
        double dx = x2 - x1, dy = y2 - y1;
        if (Math.abs(dx) < 1e-6 && Math.abs(dy) < 1e-6) return new double[]{x1, y1, x2, y2};
        if (left) {
            if (Math.abs(dx) > 1e-6) {
                double t = (minX - x1) / dx;
                x1 = minX; y1 = y1 + dy * t;
            }
        }
        if (right) {
            if (Math.abs(dx) > 1e-6) {
                double t = (maxX - x1) / dx;
                x2 = maxX; y2 = y1 + dy * t;
            }
        }
        return new double[]{x1, y1, x2, y2};
    }

    private static Color safeColor(String hex, String fallback) {
        try {
            if (hex != null && !hex.isBlank()) return Color.web(hex);
        } catch (IllegalArgumentException ignored) {}
        return Color.web(fallback);
    }

    private static String formatPrice(double p) {
        if (Math.abs(p) >= 1000) return String.format("%.2f", p);
        if (Math.abs(p) >= 1) return String.format("%.4f", p);
        return String.format("%.6f", p);
    }
}
