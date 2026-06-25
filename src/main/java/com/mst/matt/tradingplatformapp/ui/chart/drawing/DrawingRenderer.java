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
 * Fully corrected rendering for all Fibonacci tools, channels, shapes, and annotations.
 */
public final class DrawingRenderer {

    private static final Font FONT_SMALL  = Font.font("Segoe UI", 11);
    private static final Font FONT_MEDIUM = Font.font("Segoe UI", 12);

    // Standard Fibonacci retracement levels
    private static final double[] FIB_RETRACE_LEVELS = {0.0, 0.236, 0.382, 0.5, 0.618, 0.786, 1.0};
    // Optional extension levels for retracement tool
    private static final double[] FIB_RETRACE_EXT    = {1.272, 1.618, 2.618};
    // Fib Extension projection targets (from C point)
    private static final double[] FIB_EXT_LEVELS     = {0.618, 1.000, 1.272, 1.618, 2.618, 4.236};
    // Fan ratios
    private static final double[] FIB_FAN_RATIOS     = {0.236, 0.382, 0.5, 0.618, 0.786};
    // Channel Fib offsets
    private static final double[] FIB_CHANNEL_LEVELS = {0.0, 0.382, 0.618, 1.0, 1.618};
    // Speed Resistance fan multiples
    private static final double[] FIB_SR_MULTIPLES   = {1.0 / 8, 1.0 / 5, 1.0 / 3, 1.0 / 2, 2.0 / 3};

    private DrawingRenderer() {}

    public record RenderContext(
            double left, double right, double priceTop, double priceBottom, double priceH,
            int startBarIndex, int visibleBars, double maxPrice, double minPrice,
            List<OhlcvBar> bars
    ) {
        public double plotWidth() { return right - left; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Main dispatch
    // ─────────────────────────────────────────────────────────────────────────

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
            case TREND_LINE        -> drawTwoPointLine(gc, d, ctx, color, false, false);
            case RAY               -> drawTwoPointLine(gc, d, ctx, color, false, true);
            case EXTENDED_LINE     -> drawTwoPointLine(gc, d, ctx, color, true, true);
            case HORIZONTAL_LINE, PROFIT_TARGET_LINE, STOP_LOSS_LINE ->
                                      drawHorizontalLine(gc, d, ctx, color, props);
            case VERTICAL_LINE     -> drawVerticalLine(gc, d, ctx, color);
            case RECTANGLE         -> drawRectangle(gc, d, ctx, color, props);
            case FLAT_CHANNEL      -> drawFlatChannel(gc, d, ctx, color, props);
            case PARALLEL_CHANNEL  -> drawParallelChannel(gc, d, ctx, color, props);
            case TRIANGLE          -> drawTriangle(gc, d, ctx, color, props);
            case ELLIPSE           -> drawEllipse(gc, d, ctx, color, props);
            case FIB_RETRACEMENT   -> drawFibRetracement(gc, d, ctx, props);
            case FIB_EXTENSION     -> drawFibExtension(gc, d, ctx, props);
            case FIB_FAN           -> drawFibFan(gc, d, ctx, props);
            case FIB_TIME_ZONES    -> drawFibTimeZones(gc, d, ctx, props);
            case FIB_CHANNEL       -> drawFibChannel(gc, d, ctx, props);
            case FIB_SPEED_RESISTANCE -> drawFibSpeedResistance(gc, d, ctx, props);
            case LONG_POSITION, SHORT_POSITION -> drawPosition(gc, d, ctx, props, selected);
            case TEXT_LABEL        -> drawTextLabel(gc, d, ctx, props);
            case NOTE_ICON         -> drawNoteIcon(gc, d, ctx, props);
            case CALLOUT           -> drawCallout(gc, d, ctx, props);
            case RULER             -> drawRuler(gc, d, ctx, color);
            case ARROW             -> drawArrow(gc, d, ctx, color, props);
            default                -> drawTwoPointLine(gc, d, ctx, color, false, false);
        }

        if (selected && showAnchors && !d.isLocked()) {
            drawAnchors(gc, d, ctx);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Line tools
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    //  Shapes
    // ─────────────────────────────────────────────────────────────────────────

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

    /**
     * FLAT CHANNEL — a horizontal rectangle.
     * Point 0 = top-left anchor, Point 1 = bottom-right anchor.
     * Top and bottom edges are perfectly horizontal (constant price).
     */
    private static void drawFlatChannel(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                        Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double p1 = d.getPoints().get(0).getPrice();
        double p2 = d.getPoints().get(1).getPrice();
        // Top price = higher price, Bottom price = lower price
        double topPrice = Math.max(p1, p2);
        double botPrice = Math.min(p1, p2);
        double yTop = priceToY(topPrice, ctx);
        double yBot = priceToY(botPrice, ctx);
        double xLeft  = Math.min(x1, x2);
        double xRight = Math.max(x1, x2);
        double w = xRight - xLeft;
        double h = yBot - yTop;

        // Semi-transparent fill
        gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), props.getFillOpacity()));
        gc.fillRect(xLeft, yTop, w, h);

        // Border with top and bottom as bold horizontal lines
        gc.setStroke(color);
        gc.setLineWidth(props.getLineWidth());
        // Top horizontal edge (price level)
        gc.strokeLine(xLeft, yTop, xRight, yTop);
        // Bottom horizontal edge (price level)
        gc.strokeLine(xLeft, yBot, xRight, yBot);
        // Left and right vertical boundary lines (dashed)
        gc.setLineDashes(4, 4);
        gc.strokeLine(xLeft, yTop, xLeft, yBot);
        gc.strokeLine(xRight, yTop, xRight, yBot);
        gc.setLineDashes();

        // Labels: top and bottom price levels
        gc.setFill(color);
        gc.setFont(FONT_SMALL);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(formatPrice(topPrice), xRight + 4, yTop + 4);
        gc.fillText(formatPrice(botPrice), xRight + 4, yBot + 4);
    }

    /**
     * TRIANGLE — three anchor points connected as a closed polygon.
     */
    private static void drawTriangle(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                     Color color, ChartDrawingProperties props) {
        int n = d.getPoints().size();
        if (n < 2) return;
        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = timeToX(d.getPoints().get(i).getTime(), ctx);
            ys[i] = priceToY(d.getPoints().get(i).getPrice(), ctx);
        }
        if (n >= 3) {
            // Fill the triangle
            gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), props.getFillOpacity()));
            gc.fillPolygon(xs, ys, 3);
            gc.setStroke(color);
            gc.strokePolygon(xs, ys, 3);
        } else {
            // Draw partial (two points during creation preview)
            gc.setStroke(color);
            gc.strokeLine(xs[0], ys[0], xs[1], ys[1]);
        }
    }

    /**
     * ELLIPSE — bounding box defined by two anchor points.
     */
    private static void drawEllipse(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                    Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y1 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y2 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        double x = Math.min(x1, x2), y = Math.min(y1, y2);
        double w = Math.abs(x2 - x1), h = Math.abs(y2 - y1);
        if (w < 2) w = 2;
        if (h < 2) h = 2;
        gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), props.getFillOpacity()));
        gc.fillOval(x, y, w, h);
        gc.setStroke(color);
        gc.strokeOval(x, y, w, h);
    }

    /**
     * PARALLEL CHANNEL — correct 3-point construction.
     * Point 0 and Point 1 define the base trend line.
     * Point 2 defines the offset of the parallel line.
     * When only 2 points exist, derive offset from channelWidth property.
     */
    private static void drawParallelChannel(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                            Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;

        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y1 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y2 = priceToY(d.getPoints().get(1).getPrice(), ctx);

        // Calculate the perpendicular offset in screen pixels
        double offsetY;
        if (d.getPoints().size() >= 3) {
            // Third anchor defines the offset channel line (user has dragged it)
            double x3 = timeToX(d.getPoints().get(2).getTime(), ctx);
            double y3 = priceToY(d.getPoints().get(2).getPrice(), ctx);
            // Project point 3 onto the base line direction to get pure perpendicular offset
            offsetY = computeParallelOffset(x1, y1, x2, y2, x3, y3);
        } else {
            // Use channelWidth in price units, converted to pixels
            double priceDiff = props.getChannelWidth() != null ? props.getChannelWidth() : 0;
            double refY1 = priceToY(d.getPoints().get(0).getPrice(), ctx);
            double refY2 = priceToY(d.getPoints().get(0).getPrice() + priceDiff, ctx);
            offsetY = refY2 - refY1;
        }

        // Draw fill between base line and parallel line
        gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), props.getFillOpacity()));
        gc.beginPath();
        gc.moveTo(x1, y1);
        gc.lineTo(x2, y2);
        gc.lineTo(x2, y2 + offsetY);
        gc.lineTo(x1, y1 + offsetY);
        gc.closePath();
        gc.fill();

        gc.setStroke(color);
        gc.setLineWidth(props.getLineWidth());

        // Base line (center or lower edge)
        gc.strokeLine(x1, y1, x2, y2);
        // Parallel offset line
        gc.strokeLine(x1, y1 + offsetY, x2, y2 + offsetY);

        // If a third point exists, draw the center line between them
        if (d.getPoints().size() >= 3) {
            gc.setLineDashes(3, 3);
            gc.strokeLine(x1, y1 + offsetY / 2, x2, y2 + offsetY / 2);
            gc.setLineDashes();
        }

        // Label the two lines with prices
        gc.setFill(color);
        gc.setFont(FONT_SMALL);
        gc.setTextAlign(TextAlignment.LEFT);
        double price1 = d.getPoints().get(0).getPrice();
        double price2 = d.getPoints().get(1).getPrice();
        gc.fillText(formatPrice(price1), x1 + 4, y1 - 4);
        gc.fillText(formatPrice(price2), x2 + 4, y2 - 4);
    }

    /** Compute the Y-offset so that (x3, y3) lies on the parallel line. */
    private static double computeParallelOffset(double x1, double y1, double x2, double y2,
                                                double x3, double y3) {
        double dx = x2 - x1, dy = y2 - y1;
        double len2 = dx * dx + dy * dy;
        if (len2 < 1e-10) return y3 - y1;
        // Find the closest point on the base line to (x3, y3)
        double t = ((x3 - x1) * dx + (y3 - y1) * dy) / len2;
        double projX = x1 + t * dx;
        double projY = y1 + t * dy;
        // The offset is the vector from the projection to (x3, y3)
        return y3 - projY;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Fibonacci Tools — All correctly implemented
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * FIB RETRACEMENT — user drags from swing low (Point 0) to swing high (Point 1).
     * Levels: 0%, 23.6%, 38.2%, 50%, 61.8%, 78.6%, 100%
     * Extensions (optional): 127.2%, 161.8%, 261.8%
     * Each level is a horizontal line spanning the chart with label (% + price).
     */
    private static void drawFibRetracement(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                           ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double p0 = d.getPoints().get(0).getPrice();   // swing start
        double p1 = d.getPoints().get(1).getPrice();   // swing end
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        Color base = safeColor(props.getColor(), "#d29922");
        gc.setFont(FONT_SMALL);

        // Draw the swing line connecting the two anchor points
        gc.setStroke(base.deriveColor(0, 1, 1, 0.6));
        gc.setLineWidth(props.getLineWidth() + 0.5);
        gc.strokeLine(x1, priceToY(p0, ctx), x2, priceToY(p1, ctx));
        gc.setLineWidth(props.getLineWidth());

        // Standard retracement levels: price = p0 + (p1 - p0) * level
        // i.e. 0% = p0, 100% = p1
        // Colors vary slightly per level for visual distinction
        String[] levelColors = {"#e6edf3", "#d29922", "#22c55e", "#3b82f6", "#ef4444", "#a855f7", "#e6edf3"};

        for (int i = 0; i < FIB_RETRACE_LEVELS.length; i++) {
            double level = FIB_RETRACE_LEVELS[i];
            double price = p0 + (p1 - p0) * level;
            double y = priceToY(price, ctx);
            if (y < ctx.priceTop - 5 || y > ctx.priceBottom + 5) continue;

            Color lColor = safeColor(i < levelColors.length ? levelColors[i] : "#d29922", "#d29922");
            gc.setStroke(lColor.deriveColor(0, 1, 1, 0.7));
            gc.setLineDashes(3, 4);
            gc.strokeLine(Math.min(x1, x2), y, ctx.right, y);
            gc.setLineDashes();

            // Label: percentage + price
            gc.setFill(lColor);
            gc.setTextAlign(TextAlignment.LEFT);
            gc.fillText(String.format("%.1f%%  %s", level * 100, formatPrice(price)),
                    Math.min(x1, x2) + 2, y - 3);
            // Price on right axis
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(formatPrice(price), ctx.right - 2, y - 3);
        }
        gc.setTextAlign(TextAlignment.LEFT);

        // Extension levels (127.2%, 161.8%, 261.8%) — drawn past the 100% mark
        gc.setFont(FONT_SMALL);
        for (double level : FIB_RETRACE_EXT) {
            double price = p0 + (p1 - p0) * level;
            double y = priceToY(price, ctx);
            if (y < ctx.priceTop - 5 || y > ctx.priceBottom + 5) continue;
            gc.setStroke(base.deriveColor(0, 1, 1, 0.4));
            gc.setLineDashes(2, 5);
            gc.strokeLine(Math.min(x1, x2), y, ctx.right, y);
            gc.setLineDashes();
            gc.setFill(base.deriveColor(0, 1, 1, 0.7));
            gc.fillText(String.format("%.1f%%  %s", level * 100, formatPrice(price)),
                    Math.min(x1, x2) + 2, y - 3);
        }

        // Draw anchor dots on the swing points
        gc.setFill(base);
        gc.fillOval(x1 - 3, priceToY(p0, ctx) - 3, 6, 6);
        gc.fillOval(x2 - 3, priceToY(p1, ctx) - 3, 6, 6);
    }

    /**
     * FIB EXTENSION — three-point construction.
     * Point A (0) → Point B (1) defines the impulse move.
     * Point C (2) is the retracement end / projection origin.
     * Targets: 0.618, 1.000, 1.272, 1.618, 2.618, 4.236 of A→B swing projected from C.
     */
    private static void drawFibExtension(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                         ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;  // need at least A and B
        double pA = d.getPoints().get(0).getPrice();
        double pB = d.getPoints().get(1).getPrice();
        double swing = pB - pA;  // magnitude and direction of impulse

        // C point: use pB as origin if not yet placed
        double pC = d.getPoints().size() >= 3
                ? d.getPoints().get(2).getPrice()
                : pB;

        // x range
        double xA = timeToX(d.getPoints().get(0).getTime(), ctx);
        double xB = timeToX(d.getPoints().get(1).getTime(), ctx);
        double xC = d.getPoints().size() >= 3
                ? timeToX(d.getPoints().get(2).getTime(), ctx)
                : xB;

        Color base = safeColor(props.getColor(), "#d29922");
        gc.setFont(FONT_SMALL);

        // Draw A→B→C connector lines
        gc.setStroke(base.deriveColor(0, 1, 1, 0.5));
        gc.setLineDashes(4, 4);
        gc.strokeLine(xA, priceToY(pA, ctx), xB, priceToY(pB, ctx));
        if (d.getPoints().size() >= 3) {
            gc.strokeLine(xB, priceToY(pB, ctx), xC, priceToY(pC, ctx));
        }
        gc.setLineDashes();

        // Draw extension levels from C, in the direction of A→B
        String[] extColors = {"#22c55e", "#3b82f6", "#d29922", "#ef4444", "#a855f7", "#e06c75"};
        double xFrom = d.getPoints().size() >= 3 ? xC : xB;
        for (int i = 0; i < FIB_EXT_LEVELS.length; i++) {
            double mult = FIB_EXT_LEVELS[i];
            // Extension target price: C + swing * multiplier (in direction of A→B)
            double price = pC + swing * mult;
            double y = priceToY(price, ctx);
            if (y < ctx.priceTop - 5 || y > ctx.priceBottom + 5) continue;

            Color lColor = safeColor(i < extColors.length ? extColors[i] : "#d29922", "#d29922");
            gc.setStroke(lColor.deriveColor(0, 1, 1, 0.75));
            gc.strokeLine(xFrom, y, ctx.right, y);

            gc.setFill(lColor);
            gc.setTextAlign(TextAlignment.LEFT);
            String label = String.format("%.3f  %s", mult, formatPrice(price));
            gc.fillText(label, xFrom + 2, y - 3);
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(formatPrice(price), ctx.right - 2, y - 3);
        }
        gc.setTextAlign(TextAlignment.LEFT);

        // Anchor dots
        gc.setFill(base);
        gc.fillOval(xA - 3, priceToY(pA, ctx) - 3, 6, 6);
        gc.fillOval(xB - 3, priceToY(pB, ctx) - 3, 6, 6);
        if (d.getPoints().size() >= 3) {
            gc.fillOval(xC - 3, priceToY(pC, ctx) - 3, 6, 6);
        }
    }

    /**
     * FIB FAN — diagonal lines radiating from pivot (Point 0) to right edge.
     * The vertical axis end is determined by Point 1 (the opposing swing high/low).
     * Fan lines are drawn at Fibonacci ratios of the price range between the two points.
     * Lines extend all the way to the right edge of the chart.
     */
    private static void drawFibFan(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                   ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x0  = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y0  = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x1  = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y1  = priceToY(d.getPoints().get(1).getPrice(), ctx);
        Color base = safeColor(props.getColor(), "#d29922");

        // The "full" endpoint (100% fan line) goes from pivot (x0,y0) through (x1,y1)
        // and extends to the right edge of the chart.
        double dx = x1 - x0;
        double dy = y1 - y0;

        // Draw the 0% and 100% guide lines first
        gc.setStroke(base.deriveColor(0, 1, 1, 0.35));
        gc.setLineDashes(3, 5);
        // Vertical reference line at x1
        gc.strokeLine(x1, y0, x1, y1);
        // Horizontal reference line at y0
        gc.strokeLine(x0, y0, x1, y0);
        gc.setLineDashes();

        String[] fanColors = {"#22c55e", "#3b82f6", "#e6edf3", "#d29922", "#ef4444"};
        for (int i = 0; i < FIB_FAN_RATIOS.length; i++) {
            double ratio = FIB_FAN_RATIOS[i];
            // The fan line goes from (x0, y0) to a point at the same x as x1
            // but at a fraction `ratio` of the price range
            double yEnd = y0 + dy * ratio;

            // Extend this line to the right edge of chart
            double extY;
            if (Math.abs(dx) > 1e-6) {
                double slope = (yEnd - y0) / dx;
                extY = y0 + slope * (ctx.right - x0);
            } else {
                extY = yEnd;
            }

            Color lColor = safeColor(i < fanColors.length ? fanColors[i] : "#d29922", "#d29922");
            gc.setStroke(lColor.deriveColor(0, 1, 1, 0.8));
            gc.setLineWidth(props.getLineWidth());
            gc.strokeLine(x0, y0, ctx.right, extY);

            // Label at right edge
            if (extY >= ctx.priceTop - 5 && extY <= ctx.priceBottom + 5) {
                gc.setFill(lColor);
                gc.setFont(FONT_SMALL);
                gc.setTextAlign(TextAlignment.RIGHT);
                gc.fillText(String.format("%.1f%%", ratio * 100), ctx.right - 2, extY - 2);
            }
        }
        gc.setTextAlign(TextAlignment.LEFT);

        // Pivot anchor
        gc.setFill(base);
        gc.fillOval(x0 - 3, y0 - 3, 6, 6);
        gc.fillOval(x1 - 3, y1 - 3, 6, 6);
    }

    /**
     * FIB TIME ZONES — vertical bands at Fibonacci bar counts from the anchor.
     * Sequence: 1, 2, 3, 5, 8, 13, 21, 34, 55 bars from the click point.
     * Bands are semi-transparent coloured vertical regions.
     */
    private static void drawFibTimeZones(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                         ChartDrawingProperties props) {
        if (d.getPoints().isEmpty()) return;
        int startIdx = DrawingCoordinateMapper.timeToBarIndex(ctx.bars, d.getPoints().getFirst().getTime());
        // Extended Fibonacci sequence
        int[] fibSeq = {1, 2, 3, 5, 8, 13, 21, 34, 55};
        Color base = safeColor(props.getColor(), "#d29922");
        double barW = ctx.plotWidth() / Math.max(1, ctx.visibleBars);

        // Draw a marker at the start point
        double xStart = ctx.left + (startIdx - ctx.startBarIndex + 0.5) * barW;
        if (xStart >= ctx.left && xStart <= ctx.right) {
            gc.setStroke(base.deriveColor(0, 1, 1, 0.8));
            gc.setLineWidth(1.5);
            gc.setLineDashes(4, 4);
            gc.strokeLine(xStart, ctx.priceTop, xStart, ctx.priceBottom);
            gc.setLineDashes();
            gc.setFill(base);
            gc.setFont(FONT_SMALL);
            gc.fillText("0", xStart + 2, ctx.priceTop + 12);
        }

        for (int j = 0; j < fibSeq.length; j++) {
            int fib = fibSeq[j];
            int idx = startIdx + fib;
            if (idx < 0 || idx >= ctx.bars.size()) continue;
            double x = ctx.left + (idx - ctx.startBarIndex + 0.5) * barW;
            if (x < ctx.left || x > ctx.right) continue;

            // Draw a semi-transparent band (width = 1 bar)
            double alpha = 0.08 + (j % 2) * 0.06;
            gc.setFill(Color.color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
            gc.fillRect(x - barW / 2, ctx.priceTop, barW, ctx.priceBottom - ctx.priceTop);

            // Vertical line at the zone boundary
            gc.setStroke(base.deriveColor(0, 1, 1, 0.5));
            gc.setLineWidth(1.0);
            gc.setLineDashes(3, 5);
            gc.strokeLine(x, ctx.priceTop, x, ctx.priceBottom);
            gc.setLineDashes();

            // Label the Fibonacci number
            gc.setFill(base);
            gc.setFont(FONT_SMALL);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(String.valueOf(fib), x, ctx.priceTop + 12);
        }
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setLineWidth(props.getLineWidth());
    }

    /**
     * FIB CHANNEL — trendline with parallel channels at Fib ratios.
     * Point 0 = start of base line, Point 1 = end of base line (defines slope + direction).
     * The channel width is derived from the price range between the two anchor points,
     * then parallel lines are drawn at Fib ratios above/below.
     */
    private static void drawFibChannel(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                       ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y1 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y2 = priceToY(d.getPoints().get(1).getPrice(), ctx);

        // Use the vertical distance between the two points as the "unit"
        // (similar to TradingView: the base line is drawn, then parallels at Fib multiples)
        double unitDy = y2 - y1;  // in screen pixels

        // If third point exists, use it to determine the actual channel unit
        if (d.getPoints().size() >= 3) {
            double y3 = priceToY(d.getPoints().get(2).getPrice(), ctx);
            unitDy = y3 - y1;
        }

        Color base = safeColor(props.getColor(), "#d29922");
        String[] chColors = {"#e6edf3", "#3b82f6", "#22c55e", "#d29922", "#ef4444"};

        // Draw each Fib channel level as a line parallel to the base
        for (int i = 0; i < FIB_CHANNEL_LEVELS.length; i++) {
            double lvl = FIB_CHANNEL_LEVELS[i];
            double offsetY = unitDy * lvl;
            Color lColor = safeColor(i < chColors.length ? chColors[i] : "#d29922", "#d29922");
            gc.setStroke(lColor.deriveColor(0, 1, 1, lvl == 0.0 || lvl == 1.0 ? 0.9 : 0.65));
            gc.setLineWidth(lvl == 0.0 || lvl == 1.0 ? props.getLineWidth() + 0.5 : props.getLineWidth());
            if (lvl != 0.0 && lvl != 1.0) gc.setLineDashes(3, 4);
            // Extend to chart edges
            double[] ext = extendLine(x1, y1 + offsetY, x2, y2 + offsetY,
                    ctx.left, ctx.right, ctx.priceTop, ctx.priceBottom, false, true);
            gc.strokeLine(ext[0], ext[1], ext[2], ext[3]);
            gc.setLineDashes();

            // Label on the right
            double rY = y1 + offsetY + (y2 - y1 + offsetY - y1 - offsetY) * (ctx.right - x1) / Math.max(1, x2 - x1);
            // Simpler: project y at ctx.right
            if (Math.abs(x2 - x1) > 1e-6) {
                double slope = (y2 - y1) / (x2 - x1);
                rY = y1 + offsetY + slope * (ctx.right - x1);
            } else {
                rY = y1 + offsetY;
            }
            if (rY >= ctx.priceTop - 5 && rY <= ctx.priceBottom + 5) {
                gc.setFill(lColor);
                gc.setFont(FONT_SMALL);
                gc.setTextAlign(TextAlignment.RIGHT);
                gc.fillText(String.format("%.3f", lvl), ctx.right - 2, rY - 2);
            }
        }
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setLineWidth(props.getLineWidth());

        // Anchor dots
        gc.setFill(base);
        gc.fillOval(x1 - 3, y1 - 3, 6, 6);
        gc.fillOval(x2 - 3, y2 - 3, 6, 6);
    }

    /**
     * FIB SPEED RESISTANCE — angled lines from origin (Point 0) through the swing end (Point 1).
     * Draws fan lines at Speed Resistance ratios (1/8, 1/5, 1/3, 1/2, 2/3 of price/time slope).
     * Lines extend to the right edge of the chart.
     */
    private static void drawFibSpeedResistance(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                               ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x0  = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y0  = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x1  = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y1  = priceToY(d.getPoints().get(1).getPrice(), ctx);
        Color base = safeColor(props.getColor(), "#d29922");

        // Draw the reference box (swing boundary)
        gc.setStroke(base.deriveColor(0, 1, 1, 0.25));
        gc.setLineDashes(3, 5);
        gc.strokeLine(x1, y0, x1, y1);       // vertical side
        gc.strokeLine(x0, y1, x1, y1);       // horizontal bottom
        gc.setLineDashes();

        // Draw the full 1:1 diagonal line (from origin through full swing)
        double fullSlope = (x1 > x0) ? (y1 - y0) / (x1 - x0) : 0;
        double extYFull = y0 + fullSlope * (ctx.right - x0);
        gc.setStroke(base.deriveColor(0, 1, 1, 0.7));
        gc.setLineWidth(props.getLineWidth() + 0.5);
        gc.strokeLine(x0, y0, ctx.right, extYFull);
        gc.setLineWidth(props.getLineWidth());

        // Label the 1:1 line
        if (extYFull >= ctx.priceTop - 5 && extYFull <= ctx.priceBottom + 5) {
            gc.setFill(base);
            gc.setFont(FONT_SMALL);
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText("1×1", ctx.right - 2, extYFull - 2);
        }

        // Draw each Speed Resistance line at the Fib multiples of the full slope
        String[] srColors = {"#22c55e", "#3b82f6", "#d29922", "#ef4444", "#a855f7"};
        String[] srLabels = {"1/8", "1/5", "1/3", "1/2", "2/3"};
        for (int i = 0; i < FIB_SR_MULTIPLES.length; i++) {
            double mult = FIB_SR_MULTIPLES[i];
            double slope = fullSlope * mult;
            double extY = y0 + slope * (ctx.right - x0);

            Color lColor = safeColor(i < srColors.length ? srColors[i] : "#d29922", "#d29922");
            gc.setStroke(lColor.deriveColor(0, 1, 1, 0.75));
            gc.strokeLine(x0, y0, ctx.right, extY);

            if (extY >= ctx.priceTop - 5 && extY <= ctx.priceBottom + 5) {
                gc.setFill(lColor);
                gc.setFont(FONT_SMALL);
                gc.setTextAlign(TextAlignment.RIGHT);
                gc.fillText(srLabels[i], ctx.right - 2, extY - 2);
            }
        }

        gc.setTextAlign(TextAlignment.LEFT);

        // Anchor dots
        gc.setFill(base);
        gc.fillOval(x0 - 3, y0 - 3, 6, 6);
        gc.fillOval(x1 - 3, y1 - 3, 6, 6);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Position tools
    // ─────────────────────────────────────────────────────────────────────────

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
            // Render individual line anchors for Entry, SL, TP
            renderPositionLineAnchors(gc, d, ctx);
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

    // ─────────────────────────────────────────────────────────────────────────
    //  Annotation Tools (Text, Note, Callout)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * TEXT LABEL — inline text box on the chart.
     * Supports editing mode (when editingText is set in props).
     */
    private static void drawTextLabel(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                      ChartDrawingProperties props) {
        if (d.getPoints().isEmpty()) return;
        ChartPoint pt = d.getPoints().getFirst();
        double x = timeToX(pt.getTime(), ctx);
        double y = priceToY(pt.getPrice(), ctx);
        String text = props.getText() != null && !props.getText().isBlank() ? props.getText() : "Text";
        Color color = safeColor(props.getColor(), "#58a6ff");
        double fontSize = props.getFontSize() > 0 ? props.getFontSize() : 12;
        Font font = Font.font("Segoe UI", fontSize);
        gc.setFont(font);

        // Use stored dimensions if set, otherwise auto-compute
        double boxW = (props.getBoxWidth() > 0) ? props.getBoxWidth()
                : Math.max(60, text.length() * (fontSize * 0.65) + 16);
        double boxH = (props.getBoxHeight() > 0) ? props.getBoxHeight() : fontSize + 12;

        // Background
        gc.setFill(Color.web("#1c2128ee"));
        gc.fillRoundRect(x, y - boxH + 4, boxW, boxH, 4, 4);
        // Border
        gc.setStroke(color);
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(x, y - boxH + 4, boxW, boxH, 4, 4);
        // Text (clip to box)
        gc.save();
        gc.beginPath();
        gc.rect(x + 2, y - boxH + 4, boxW - 4, boxH);
        gc.clip();
        gc.setFill(Color.web("#e6edf3"));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(text, x + 8, y);
        gc.restore();

        // Editing cursor indicator
        if (props.isEditing()) {
            double cursorX = x + 8 + text.length() * (fontSize * 0.6);
            gc.setStroke(Color.web("#e6edf3"));
            gc.setLineWidth(1.5);
            gc.strokeLine(cursorX, y - boxH + 6, cursorX, y - 2);
        }
    }

    /**
     * NOTE ICON — sticky note with a preview of the first words.
     * Double-click triggers a popup for longer editing.
     */
    private static void drawNoteIcon(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                     ChartDrawingProperties props) {
        if (d.getPoints().isEmpty()) return;
        ChartPoint pt = d.getPoints().getFirst();
        double x = timeToX(pt.getTime(), ctx);
        double y = priceToY(pt.getPrice(), ctx);
        String text = props.getText() != null ? props.getText() : "";
        Color color = safeColor(props.getColor(), "#d29922");

        double noteW = (props.getBoxWidth()  > 0) ? props.getBoxWidth()  : 80;
        double noteH = (props.getBoxHeight() > 0) ? props.getBoxHeight() : 60;

        // Note background (yellow-tinted)
        gc.setFill(Color.web("#2d2a00ee"));
        gc.fillRoundRect(x, y, noteW, noteH, 4, 4);
        // Note border
        gc.setStroke(color);
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(x, y, noteW, noteH, 4, 4);

        // Folded corner triangle
        gc.setFill(color.deriveColor(0, 1, 0.6, 0.8));
        double foldSize = 10;
        gc.fillPolygon(
                new double[]{x + noteW - foldSize, x + noteW, x + noteW},
                new double[]{y, y, y + foldSize},
                3
        );

        // Preview text (first 2 lines, ~12 chars per line)
        gc.setFill(Color.web("#e6edf3cc"));
        gc.setFont(Font.font("Segoe UI", 10));
        gc.setTextAlign(TextAlignment.LEFT);
        if (!text.isBlank()) {
            String preview = text.length() > 24 ? text.substring(0, 24) + "…" : text;
            // Simple word wrap: split at 12 chars
            if (preview.length() > 12) {
                gc.fillText(preview.substring(0, Math.min(12, preview.length())), x + 5, y + 16);
                gc.fillText(preview.substring(Math.min(12, preview.length())), x + 5, y + 28);
            } else {
                gc.fillText(preview, x + 5, y + 16);
            }
        } else {
            // Pencil icon placeholder
            gc.fillText("✏ Note", x + 8, y + 22);
        }

        // Editing indicator
        if (props.isEditing()) {
            gc.setStroke(color);
            gc.strokeRoundRect(x + 1, y + 1, noteW - 2, noteH - 2, 4, 4);
        }
    }

    /**
     * CALLOUT — text box with a leader line pointing to a price/time location.
     * Point 0 = tip of the leader arrow (the target location on chart).
     * Point 1 = position of the text box (if set, else offset from point 0).
     * Double-click the text box area to edit.
     */
    private static void drawCallout(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                    ChartDrawingProperties props) {
        if (d.getPoints().isEmpty()) return;
        ChartPoint tip = d.getPoints().getFirst();
        double tipX = timeToX(tip.getTime(), ctx);
        double tipY = priceToY(tip.getPrice(), ctx);
        String text = props.getText() != null && !props.getText().isBlank() ? props.getText() : "Callout";
        Color color = safeColor(props.getColor(), "#58a6ff");
        double fontSize = props.getFontSize() > 0 ? props.getFontSize() : 12;

        // Text box position: use second point if available, else offset
        double boxX, boxY;
        if (d.getPoints().size() >= 2) {
            boxX = timeToX(d.getPoints().get(1).getTime(), ctx);
            boxY = priceToY(d.getPoints().get(1).getPrice(), ctx);
        } else {
            boxX = tipX + 40;
            boxY = tipY - 30;
        }

        double boxW = Math.max(70, text.length() * (fontSize * 0.62) + 16);
        double boxH = fontSize + 12;

        // Draw leader line from tip to text box center-bottom
        double boxCX = boxX + boxW / 2;
        double boxBY = boxY + boxH;
        gc.setStroke(color);
        gc.setLineWidth(1.5);
        gc.strokeLine(tipX, tipY, boxCX, boxBY);

        // Arrowhead at the tip
        double angle = Math.atan2(tipY - boxBY, tipX - boxCX);
        double arrowLen = 8;
        gc.strokeLine(tipX, tipY,
                tipX - arrowLen * Math.cos(angle - Math.PI / 6),
                tipY - arrowLen * Math.sin(angle - Math.PI / 6));
        gc.strokeLine(tipX, tipY,
                tipX - arrowLen * Math.cos(angle + Math.PI / 6),
                tipY - arrowLen * Math.sin(angle + Math.PI / 6));

        // Tip dot
        gc.setFill(color);
        gc.fillOval(tipX - 3, tipY - 3, 6, 6);

        // Text box background
        gc.setFill(Color.web("#1c2128ee"));
        gc.fillRoundRect(boxX, boxY, boxW, boxH, 4, 4);
        gc.setStroke(color);
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(boxX, boxY, boxW, boxH, 4, 4);

        // Text content
        gc.setFill(Color.web("#e6edf3"));
        gc.setFont(Font.font("Segoe UI", fontSize));
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(text, boxX + 8, boxY + fontSize + 1);

        // Editing cursor
        if (props.isEditing()) {
            double cursorX = boxX + 8 + text.length() * (fontSize * 0.6);
            gc.setStroke(Color.web("#e6edf3"));
            gc.setLineWidth(1.5);
            gc.strokeLine(cursorX, boxY + 3, cursorX, boxY + boxH - 3);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Utility tools
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    //  Anchor handles
    // ─────────────────────────────────────────────────────────────────────────

    private static void drawAnchors(GraphicsContext gc, ChartDrawing d, RenderContext ctx) {
        // For text/note tools, draw resize handles at corners of the bounding box
        if (d.getToolType() == ChartDrawingToolType.TEXT_LABEL
                || d.getToolType() == ChartDrawingToolType.NOTE_ICON
                || d.getToolType() == ChartDrawingToolType.CALLOUT) {
            drawTextResizeHandles(gc, d, ctx);
            return;
        }
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

    /**
     * Draws corner resize handles around a text box so the user can drag to resize it.
     * Handles are placed at top-left, top-right, bottom-left, bottom-right of the rendered box.
     */
    private static void drawTextResizeHandles(GraphicsContext gc, ChartDrawing d, RenderContext ctx) {
        if (d.getPoints().isEmpty()) return;
        double[] bb = ChartDrawingEngine.getDrawingBoundingBox(d, ctx);
        if (bb == null) return;
        double minX = bb[0], minY = bb[1], maxX = bb[2], maxY = bb[3];

        // Selection border
        gc.setStroke(Color.web("#388bfd88"));
        gc.setLineWidth(1.0);
        gc.setLineDashes(4, 3);
        gc.strokeRect(minX, minY, maxX - minX, maxY - minY);
        gc.setLineDashes();

        // 4 corner resize handles
        gc.setFill(Color.web("#ffffff"));
        gc.setStroke(Color.web("#388bfd"));
        gc.setLineWidth(1.5);
        double[][] corners = {
                {minX, minY}, {maxX, minY}, {minX, maxY}, {maxX, maxY}
        };
        for (double[] c : corners) {
            gc.fillRect(c[0] - 4, c[1] - 4, 8, 8);
            gc.strokeRect(c[0] - 4, c[1] - 4, 8, 8);
        }
        // Also mid-right handle for width resizing
        double midY = (minY + maxY) / 2;
        gc.fillRect(maxX - 4, midY - 4, 8, 8);
        gc.strokeRect(maxX - 4, midY - 4, 8, 8);
        // Mid-bottom handle for height resizing
        double midX = (minX + maxX) / 2;
        gc.fillRect(midX - 4, maxY - 4, 8, 8);
        gc.strokeRect(midX - 4, maxY - 4, 8, 8);
    }

    /**
     * Renders the stable delete (×) button at the top-right of the selected drawing's bounding box.
     * Called from ChartDrawingEngine.renderDrawings() only for the selected drawing.
     */
    public static void renderDeleteButton(GraphicsContext gc, ChartDrawing d, RenderContext ctx) {
        if (d == null || d.getPoints() == null || d.getPoints().isEmpty()) return;
        double[] bb = ChartDrawingEngine.getDrawingBoundingBox(d, ctx);
        if (bb == null) return;
        double btnX = bb[2] + 4;
        double btnY = bb[1] - 20;
        // Background pill
        gc.setFill(Color.web("#f85149ee"));
        gc.fillRoundRect(btnX, btnY, 20, 20, 6, 6);
        // × symbol
        gc.setFill(Color.web("#ffffff"));
        gc.setFont(Font.font("Segoe UI", 13));
        gc.setTextAlign(javafx.scene.text.TextAlignment.CENTER);
        gc.fillText("×", btnX + 10, btnY + 15);
        gc.setTextAlign(javafx.scene.text.TextAlignment.LEFT);
    }

    /**
     * Renders drag anchors on the Entry, SL, and TP lines of a selected position drawing.
     * These small triangles/handles indicate the user can drag individual lines.
     */
    public static void renderPositionLineAnchors(GraphicsContext gc, ChartDrawing d,
                                                  RenderContext ctx) {
        if (d == null || d.getProperties() == null || d.getPoints().size() < 2) return;
        ChartDrawingProperties props = d.getProperties();
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double midX = (x1 + x2) / 2;

        gc.setLineWidth(1.5);
        if (props.getEntryPrice() != null) {
            drawLineAnchorHandle(gc, midX, priceToY(props.getEntryPrice(), ctx), "#3fb950");
        }
        if (props.getStopLoss() != null) {
            drawLineAnchorHandle(gc, midX, priceToY(props.getStopLoss(), ctx), "#f85149");
        }
        if (props.getTakeProfit() != null) {
            drawLineAnchorHandle(gc, midX, priceToY(props.getTakeProfit(), ctx), "#388bfd");
        }
    }

    private static void drawLineAnchorHandle(GraphicsContext gc, double x, double y, String colorHex) {
        gc.setFill(Color.web(colorHex));
        gc.setStroke(Color.web("#ffffff"));
        gc.fillOval(x - 5, y - 5, 10, 10);
        gc.strokeOval(x - 5, y - 5, 10, 10);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Coordinate helpers
    // ─────────────────────────────────────────────────────────────────────────

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
