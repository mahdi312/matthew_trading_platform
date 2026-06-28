package com.mst.matt.tradingplatformapp.ui.chart.drawing;

import com.mst.matt.tradingplatformapp.model.ChartDrawing;
import com.mst.matt.tradingplatformapp.model.ChartDrawingProperties;
import com.mst.matt.tradingplatformapp.model.ChartDrawingToolType;
import com.mst.matt.tradingplatformapp.model.ChartPoint;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Renders user chart drawings in price-time coordinates.
 *
 * <p>Extended to support:
 * <ul>
 *   <li>Hover highlight (thicker line + glow effect)</li>
 *   <li>Line style: SOLID, DASHED, DOTTED, DASH_DOT</li>
 *   <li>Fill opacity as a per-drawing property</li>
 *   <li>Line weight 1–5 px</li>
 * </ul>
 */
public final class DrawingRenderer {

    private static final Font FONT_SMALL  = Font.font("Segoe UI", 11);
    private static final Font FONT_MEDIUM = Font.font("Segoe UI", 12);

    private static final double[] FIB_RETRACE_LEVELS = {0.0, 0.236, 0.382, 0.5, 0.618, 0.786, 1.0};
    private static final double[] FIB_RETRACE_EXT    = {1.272, 1.618, 2.618};
    private static final double[] FIB_EXT_LEVELS     = {0.618, 1.000, 1.272, 1.618, 2.618, 4.236};
    private static final double[] FIB_FAN_RATIOS     = {0.236, 0.382, 0.5, 0.618, 0.786};
    private static final double[] FIB_CHANNEL_LEVELS = {0.0, 0.382, 0.618, 1.0, 1.618};
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

    /**
     * Renders a single drawing. {@code hovered} adds a glow highlight.
     */
    public static void render(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                              boolean selected, boolean showAnchors, boolean hovered) {
        if (d.getPoints() == null || d.getPoints().isEmpty()) return;
        ChartDrawingProperties props = d.getProperties() != null
                ? d.getProperties()
                : ChartDrawingProperties.defaultsFor(d.getToolType());
        Color color = safeColor(props.getColor(), "#58a6ff");

        // Hover glow
        if (hovered) {
            gc.save();
            DropShadow glow = new DropShadow(8, color);
            gc.setEffect(glow);
        }

        // Apply line style (dash pattern)
        double[] dashes = props.getDashPattern();
        gc.setLineDashes(dashes);

        // Hover gets +1 px line weight
        double lw = Math.max(1, Math.min(5, props.getLineWidth()));
        gc.setLineWidth(hovered ? lw + 1.0 : lw);

        switch (d.getToolType()) {
            // ── Line tools ────────────────────────────────────────────────────
            case TREND_LINE        -> drawTwoPointLine(gc, d, ctx, color, false, false, props);
            case RAY               -> drawTwoPointLine(gc, d, ctx, color, false, true, props);
            case EXTENDED_LINE     -> drawTwoPointLine(gc, d, ctx, color, true, true, props);
            case HORIZONTAL_LINE, PROFIT_TARGET_LINE, STOP_LOSS_LINE ->
                                      drawHorizontalLine(gc, d, ctx, color, props);
            case VERTICAL_LINE     -> drawVerticalLine(gc, d, ctx, color, props);
            case RECTANGLE         -> drawRectangle(gc, d, ctx, color, props);
            case FLAT_CHANNEL      -> drawFlatChannel(gc, d, ctx, color, props);
            case PARALLEL_CHANNEL  -> drawParallelChannel(gc, d, ctx, color, props);
            case ELLIPSE           -> drawEllipse(gc, d, ctx, color, props);

            // ── Chart Patterns ────────────────────────────────────────────────
            case XABCD_PATTERN        -> drawXabcdPattern(gc, d, ctx, color, props);
            case CYPHER_PATTERN       -> drawCypherPattern(gc, d, ctx, color, props);
            case HEAD_AND_SHOULDERS   -> drawHeadAndShoulders(gc, d, ctx, color, props);
            case ABCD_PATTERN         -> drawAbcdPattern(gc, d, ctx, color, props);
            case TRIANGLE_PATTERN     -> drawTrianglePattern(gc, d, ctx, color, props);
            case THREE_DRIVES_PATTERN -> drawThreeDrivesPattern(gc, d, ctx, color, props);

            // ── Elliott Waves ─────────────────────────────────────────────────
            case ELLIOTT_IMPULSE_WAVE    -> drawElliottWave(gc, d, ctx, color, props,
                    new String[]{"1","2","3","4","5"});
            case ELLIOTT_CORRECTION_WAVE -> drawElliottWave(gc, d, ctx, color, props,
                    new String[]{"A","B","C"});
            case ELLIOTT_TRIANGLE_WAVE   -> drawElliottWave(gc, d, ctx, color, props,
                    new String[]{"A","B","C","D","E"});
            case ELLIOTT_DOUBLE_COMBO    -> drawElliottWave(gc, d, ctx, color, props,
                    new String[]{"W","X","Y"});
            case ELLIOTT_TRIPLE_COMBO    -> drawElliottWave(gc, d, ctx, color, props,
                    new String[]{"W","X","Y","X","Z"});

            // ── Fibonacci ─────────────────────────────────────────────────────
            case FIB_RETRACEMENT        -> drawFibRetracement(gc, d, ctx, props);
            case FIB_EXTENSION          -> drawFibExtension(gc, d, ctx, props);
            case FIB_FAN                -> drawFibFan(gc, d, ctx, props);
            case FIB_TIME_ZONES         -> drawFibTimeZones(gc, d, ctx, props);
            case FIB_CHANNEL            -> drawFibChannel(gc, d, ctx, props);
            case FIB_SPEED_RESISTANCE   -> drawFibSpeedResistance(gc, d, ctx, props);
            case FIB_TREND_BASED_TIME   -> drawFibTrendBasedTime(gc, d, ctx, props);
            case FIB_CIRCLES            -> drawFibCircles(gc, d, ctx, color, props);
            case FIB_SPIRAL             -> drawFibSpiral(gc, d, ctx, color, props);
            case FIB_ARCS               -> drawFibArcs(gc, d, ctx, color, props);
            case FIB_WEDGE              -> drawFibWedge(gc, d, ctx, color, props);
            case PITCHFAN               -> drawPitchfan(gc, d, ctx, color, props);

            // ── Gann ─────────────────────────────────────────────────────────
            case GANN_BOX          -> drawGannBox(gc, d, ctx, color, props);
            case GANN_SQUARE_FIXED -> drawGannSquareFixed(gc, d, ctx, color, props);
            case GANN_SQUARE       -> drawGannSquare(gc, d, ctx, color, props);
            case GANN_FAN          -> drawGannFan(gc, d, ctx, color, props);

            // ── Forecasting ───────────────────────────────────────────────────
            case LONG_POSITION, SHORT_POSITION -> drawPosition(gc, d, ctx, props, selected);
            case POSITION_FORECAST  -> drawPositionForecast(gc, d, ctx, color, props);
            case BARS_PATTERN       -> drawBarsPattern(gc, d, ctx, color, props);
            case GHOST_FEED         -> drawGhostFeed(gc, d, ctx, color, props);
            case SECTOR             -> drawSector(gc, d, ctx, color, props);

            // ── Volume ───────────────────────────────────────────────────────
            case ANCHORED_VWAP              -> drawAnchoredVwap(gc, d, ctx, color, props);
            case FIXED_RANGE_VOLUME_PROFILE -> drawFixedRangeVolumeProfile(gc, d, ctx, color, props);
            case ANCHORED_VOLUME_PROFILE    -> drawAnchoredVolumeProfile(gc, d, ctx, color, props);

            // ── Measurers ─────────────────────────────────────────────────────
            case PRICE_RANGE         -> drawPriceRange(gc, d, ctx, color, props);
            case DATE_RANGE          -> drawDateRange(gc, d, ctx, color, props);
            case DATE_AND_PRICE_RANGE -> drawDateAndPriceRange(gc, d, ctx, color, props);

            // ── Annotations ───────────────────────────────────────────────────
            case TEXT_LABEL -> drawTextLabel(gc, d, ctx, props);
            case NOTE_ICON  -> drawNoteIcon(gc, d, ctx, props);
            case CALLOUT    -> drawCallout(gc, d, ctx, props);
            case RULER      -> drawRuler(gc, d, ctx, color);
            case ARROW      -> drawArrow(gc, d, ctx, color, props);

            // ── Cycles ───────────────────────────────────────────────────────
            case CYCLIC_LINES -> drawCyclicLines(gc, d, ctx, color, props);
            case TIME_CYCLES  -> drawTimeCycles(gc, d, ctx, color, props);
            case SINE_LINE    -> drawSineLine(gc, d, ctx, color, props);

            // ── Legacy / utility ──────────────────────────────────────────────
            default -> drawTwoPointLine(gc, d, ctx, color, false, false, props);
        }

        if (hovered) {
            gc.setEffect(null);
            gc.restore();
        }

        // Reset dashes / line width for subsequent draws
        gc.setLineDashes();
        gc.setLineWidth(props.getLineWidth());

        if (selected && showAnchors && !d.isLocked()) {
            drawAnchors(gc, d, ctx);
        }
    }

    /** Backward-compatible overload (no hover). */
    public static void render(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                              boolean selected, boolean showAnchors) {
        render(gc, d, ctx, selected, showAnchors, false);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Line tools
    // ─────────────────────────────────────────────────────────────────────────

    private static void drawTwoPointLine(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                         Color color, boolean extendLeft, boolean extendRight,
                                         ChartDrawingProperties props) {
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
        gc.setLineDashes();
        gc.fillText(formatPrice(price), ctx.right + 4, y + 4);
    }

    private static void drawVerticalLine(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                         Color color, ChartDrawingProperties props) {
        double x = timeToX(d.getPoints().getFirst().getTime(), ctx);
        gc.setStroke(color);
        // Vertical lines always dashed regardless of style
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
        gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(),
                clamp(props.getFillOpacity())));
        gc.fillRect(x, y, w, h);
        gc.setStroke(color);
        gc.strokeRect(x, y, w, h);
    }

    private static void drawFlatChannel(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                        Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double p1 = d.getPoints().get(0).getPrice();
        double p2 = d.getPoints().get(1).getPrice();
        double topPrice = Math.max(p1, p2);
        double botPrice = Math.min(p1, p2);
        double yTop = priceToY(topPrice, ctx);
        double yBot = priceToY(botPrice, ctx);
        double xLeft  = Math.min(x1, x2);
        double xRight = Math.max(x1, x2);
        double w = xRight - xLeft, h = yBot - yTop;

        gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(),
                clamp(props.getFillOpacity())));
        gc.fillRect(xLeft, yTop, w, h);
        gc.setStroke(color);
        gc.setLineWidth(props.getLineWidth());
        gc.strokeLine(xLeft, yTop, xRight, yTop);
        gc.strokeLine(xLeft, yBot, xRight, yBot);
        gc.setLineDashes(4, 4);
        gc.strokeLine(xLeft, yTop, xLeft, yBot);
        gc.strokeLine(xRight, yTop, xRight, yBot);
        gc.setLineDashes();
        gc.setFill(color);
        gc.setFont(FONT_SMALL);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(formatPrice(topPrice), xRight + 4, yTop + 4);
        gc.fillText(formatPrice(botPrice), xRight + 4, yBot + 4);
    }

    private static void drawTriangle(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                     Color color, ChartDrawingProperties props) {
        int n = d.getPoints().size();
        if (n < 2) return;
        double[] xs = new double[n], ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = timeToX(d.getPoints().get(i).getTime(), ctx);
            ys[i] = priceToY(d.getPoints().get(i).getPrice(), ctx);
        }
        if (n >= 3) {
            gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(),
                    clamp(props.getFillOpacity())));
            gc.fillPolygon(xs, ys, 3);
            gc.setStroke(color);
            gc.strokePolygon(xs, ys, 3);
        } else {
            gc.setStroke(color);
            gc.strokeLine(xs[0], ys[0], xs[1], ys[1]);
        }
    }

    private static void drawEllipse(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                    Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y1 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y2 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        double x = Math.min(x1, x2), y = Math.min(y1, y2);
        double w = Math.max(2, Math.abs(x2 - x1)), h = Math.max(2, Math.abs(y2 - y1));
        gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(),
                clamp(props.getFillOpacity())));
        gc.fillOval(x, y, w, h);
        gc.setStroke(color);
        gc.strokeOval(x, y, w, h);
    }

    private static void drawParallelChannel(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                            Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y1 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y2 = priceToY(d.getPoints().get(1).getPrice(), ctx);

        double offsetY;
        if (d.getPoints().size() >= 3) {
            double x3 = timeToX(d.getPoints().get(2).getTime(), ctx);
            double y3 = priceToY(d.getPoints().get(2).getPrice(), ctx);
            offsetY = computeParallelOffset(x1, y1, x2, y2, x3, y3);
        } else {
            double priceDiff = props.getChannelWidth() != null ? props.getChannelWidth() : 0;
            double refY1 = priceToY(d.getPoints().get(0).getPrice(), ctx);
            double refY2 = priceToY(d.getPoints().get(0).getPrice() + priceDiff, ctx);
            offsetY = refY2 - refY1;
        }

        gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(),
                clamp(props.getFillOpacity())));
        gc.beginPath();
        gc.moveTo(x1, y1); gc.lineTo(x2, y2);
        gc.lineTo(x2, y2 + offsetY); gc.lineTo(x1, y1 + offsetY);
        gc.closePath(); gc.fill();

        gc.setStroke(color);
        gc.setLineWidth(props.getLineWidth());
        gc.strokeLine(x1, y1, x2, y2);
        gc.strokeLine(x1, y1 + offsetY, x2, y2 + offsetY);

        if (d.getPoints().size() >= 3) {
            gc.setLineDashes(3, 3);
            gc.strokeLine(x1, y1 + offsetY / 2, x2, y2 + offsetY / 2);
            gc.setLineDashes();
        }
        gc.setFill(color); gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(formatPrice(d.getPoints().get(0).getPrice()), x1 + 4, y1 - 4);
        gc.fillText(formatPrice(d.getPoints().get(1).getPrice()), x2 + 4, y2 - 4);
    }

    private static double computeParallelOffset(double x1, double y1, double x2, double y2,
                                                 double x3, double y3) {
        double dx = x2 - x1, dy = y2 - y1;
        double len2 = dx * dx + dy * dy;
        if (len2 < 1e-10) return y3 - y1;
        double t = ((x3 - x1) * dx + (y3 - y1) * dy) / len2;
        double projY = y1 + t * dy;
        return y3 - projY;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Fibonacci Tools
    // ─────────────────────────────────────────────────────────────────────────

    private static void drawFibRetracement(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                           ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double p0 = d.getPoints().get(0).getPrice(), p1 = d.getPoints().get(1).getPrice();
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        Color base = safeColor(props.getColor(), "#d29922");
        gc.setFont(FONT_SMALL);
        gc.setStroke(base.deriveColor(0, 1, 1, 0.6));
        gc.setLineWidth(props.getLineWidth() + 0.5);
        gc.strokeLine(x1, priceToY(p0, ctx), x2, priceToY(p1, ctx));
        gc.setLineWidth(props.getLineWidth());

        // Use custom levels if set, otherwise fall back to defaults + extension levels
        double[] levels = resolveCustomFibLevels(props, FIB_RETRACE_LEVELS);
        String[] levelColors = {"#e6edf3","#d29922","#22c55e","#3b82f6","#ef4444","#a855f7","#e6edf3"};
        for (int i = 0; i < levels.length; i++) {
            double level = levels[i];
            double price = p0 + (p1 - p0) * level;
            double y = priceToY(price, ctx);
            if (y < ctx.priceTop - 5 || y > ctx.priceBottom + 5) continue;
            Color lColor = safeColor(i < levelColors.length ? levelColors[i] : "#d29922", "#d29922");
            gc.setStroke(lColor.deriveColor(0, 1, 1, 0.7));
            gc.setLineDashes(3, 4);
            gc.strokeLine(Math.min(x1, x2), y, ctx.right, y);
            gc.setLineDashes();
            gc.setFill(lColor); gc.setTextAlign(TextAlignment.LEFT);
            gc.fillText(String.format("%.1f%%  %s", level * 100, formatPrice(price)), Math.min(x1, x2) + 2, y - 3);
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(formatPrice(price), ctx.right - 2, y - 3);
        }
        gc.setTextAlign(TextAlignment.LEFT);
        // Extension levels only rendered when using default levels (not custom)
        if (props.getCustomFibLevels() == null || props.getCustomFibLevels().isEmpty()) {
            for (double level : FIB_RETRACE_EXT) {
                double price = p0 + (p1 - p0) * level;
                double y = priceToY(price, ctx);
                if (y < ctx.priceTop - 5 || y > ctx.priceBottom + 5) continue;
                gc.setStroke(base.deriveColor(0, 1, 1, 0.4));
                gc.setLineDashes(2, 5);
                gc.strokeLine(Math.min(x1, x2), y, ctx.right, y);
                gc.setLineDashes();
                gc.setFill(base.deriveColor(0, 1, 1, 0.7));
                gc.fillText(String.format("%.1f%%  %s", level * 100, formatPrice(price)), Math.min(x1, x2) + 2, y - 3);
            }
        }
        gc.setFill(base);
        gc.fillOval(x1 - 3, priceToY(p0, ctx) - 3, 6, 6);
        gc.fillOval(x2 - 3, priceToY(p1, ctx) - 3, 6, 6);
    }

    /**
     * Returns the effective Fibonacci levels array.
     * If the drawing has custom levels set, those are used; otherwise the default array is returned.
     */
    private static double[] resolveCustomFibLevels(ChartDrawingProperties props, double[] defaults) {
        if (props == null || props.getCustomFibLevels() == null || props.getCustomFibLevels().isEmpty()) {
            return defaults;
        }
        return props.getCustomFibLevels().stream()
                .sorted()
                .mapToDouble(Double::doubleValue)
                .toArray();
    }

    private static void drawFibExtension(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                         ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double pA = d.getPoints().get(0).getPrice(), pB = d.getPoints().get(1).getPrice();
        double swing = pB - pA;
        double pC = d.getPoints().size() >= 3 ? d.getPoints().get(2).getPrice() : pB;
        double xA = timeToX(d.getPoints().get(0).getTime(), ctx);
        double xB = timeToX(d.getPoints().get(1).getTime(), ctx);
        double xC = d.getPoints().size() >= 3 ? timeToX(d.getPoints().get(2).getTime(), ctx) : xB;
        Color base = safeColor(props.getColor(), "#d29922");
        gc.setFont(FONT_SMALL);
        gc.setStroke(base.deriveColor(0, 1, 1, 0.5));
        gc.setLineDashes(4, 4);
        gc.strokeLine(xA, priceToY(pA, ctx), xB, priceToY(pB, ctx));
        if (d.getPoints().size() >= 3) gc.strokeLine(xB, priceToY(pB, ctx), xC, priceToY(pC, ctx));
        gc.setLineDashes();
        String[] extColors = {"#22c55e","#3b82f6","#d29922","#ef4444","#a855f7","#e06c75"};
        double xFrom = d.getPoints().size() >= 3 ? xC : xB;
        double[] effectiveExtLevels = resolveCustomFibLevels(props, FIB_EXT_LEVELS);
        for (int i = 0; i < effectiveExtLevels.length; i++) {
            double mult = effectiveExtLevels[i];
            double price = pC + swing * mult;
            double y = priceToY(price, ctx);
            if (y < ctx.priceTop - 5 || y > ctx.priceBottom + 5) continue;
            Color lColor = safeColor(i < extColors.length ? extColors[i] : "#d29922", "#d29922");
            gc.setStroke(lColor.deriveColor(0, 1, 1, 0.75));
            gc.strokeLine(xFrom, y, ctx.right, y);
            gc.setFill(lColor); gc.setTextAlign(TextAlignment.LEFT);
            gc.fillText(String.format("%.3f  %s", mult, formatPrice(price)), xFrom + 2, y - 3);
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(formatPrice(price), ctx.right - 2, y - 3);
        }
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(base);
        gc.fillOval(xA - 3, priceToY(pA, ctx) - 3, 6, 6);
        gc.fillOval(xB - 3, priceToY(pB, ctx) - 3, 6, 6);
        if (d.getPoints().size() >= 3) gc.fillOval(xC - 3, priceToY(pC, ctx) - 3, 6, 6);
    }

    private static void drawFibFan(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                   ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x0 = timeToX(d.getPoints().get(0).getTime(), ctx), y0 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x1 = timeToX(d.getPoints().get(1).getTime(), ctx), y1 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        Color base = safeColor(props.getColor(), "#d29922");
        double dx = x1 - x0, dy = y1 - y0;
        gc.setStroke(base.deriveColor(0, 1, 1, 0.35)); gc.setLineDashes(3, 5);
        gc.strokeLine(x1, y0, x1, y1); gc.strokeLine(x0, y0, x1, y0); gc.setLineDashes();
        String[] fanColors = {"#22c55e","#3b82f6","#e6edf3","#d29922","#ef4444"};
        for (int i = 0; i < FIB_FAN_RATIOS.length; i++) {
            double ratio = FIB_FAN_RATIOS[i];
            double yEnd = y0 + dy * ratio;
            double extY = Math.abs(dx) > 1e-6 ? y0 + (yEnd - y0) / dx * (ctx.right - x0) : yEnd;
            Color lColor = safeColor(i < fanColors.length ? fanColors[i] : "#d29922", "#d29922");
            gc.setStroke(lColor.deriveColor(0, 1, 1, 0.8)); gc.setLineWidth(props.getLineWidth());
            gc.strokeLine(x0, y0, ctx.right, extY);
            if (extY >= ctx.priceTop - 5 && extY <= ctx.priceBottom + 5) {
                gc.setFill(lColor); gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.RIGHT);
                gc.fillText(String.format("%.1f%%", ratio * 100), ctx.right - 2, extY - 2);
            }
        }
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(base);
        gc.fillOval(x0 - 3, y0 - 3, 6, 6); gc.fillOval(x1 - 3, y1 - 3, 6, 6);
    }

    private static void drawFibTimeZones(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                         ChartDrawingProperties props) {
        if (d.getPoints().isEmpty()) return;
        int startIdx = DrawingCoordinateMapper.timeToBarIndex(ctx.bars, d.getPoints().getFirst().getTime());
        int[] fibSeq = {1, 2, 3, 5, 8, 13, 21, 34, 55};
        Color base = safeColor(props.getColor(), "#d29922");
        double barW = ctx.plotWidth() / Math.max(1, ctx.visibleBars);
        double xStart = ctx.left + (startIdx - ctx.startBarIndex + 0.5) * barW;
        if (xStart >= ctx.left && xStart <= ctx.right) {
            gc.setStroke(base.deriveColor(0, 1, 1, 0.8)); gc.setLineWidth(1.5);
            gc.setLineDashes(4, 4); gc.strokeLine(xStart, ctx.priceTop, xStart, ctx.priceBottom); gc.setLineDashes();
            gc.setFill(base); gc.setFont(FONT_SMALL); gc.fillText("0", xStart + 2, ctx.priceTop + 12);
        }
        for (int j = 0; j < fibSeq.length; j++) {
            int idx = startIdx + fibSeq[j];
            if (idx < 0 || idx >= ctx.bars.size()) continue;
            double x = ctx.left + (idx - ctx.startBarIndex + 0.5) * barW;
            if (x < ctx.left || x > ctx.right) continue;
            double alpha = 0.08 + (j % 2) * 0.06;
            gc.setFill(Color.color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
            gc.fillRect(x - barW / 2, ctx.priceTop, barW, ctx.priceBottom - ctx.priceTop);
            gc.setStroke(base.deriveColor(0, 1, 1, 0.5)); gc.setLineWidth(1.0);
            gc.setLineDashes(3, 5); gc.strokeLine(x, ctx.priceTop, x, ctx.priceBottom); gc.setLineDashes();
            gc.setFill(base); gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(String.valueOf(fibSeq[j]), x, ctx.priceTop + 12);
        }
        gc.setTextAlign(TextAlignment.LEFT); gc.setLineWidth(props.getLineWidth());
    }

    private static void drawFibChannel(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                       ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx), y1 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx), y2 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        double unitDy = y2 - y1;
        if (d.getPoints().size() >= 3) unitDy = priceToY(d.getPoints().get(2).getPrice(), ctx) - y1;
        Color base = safeColor(props.getColor(), "#d29922");
        String[] chColors = {"#e6edf3","#3b82f6","#22c55e","#d29922","#ef4444"};
        for (int i = 0; i < FIB_CHANNEL_LEVELS.length; i++) {
            double lvl = FIB_CHANNEL_LEVELS[i];
            double offsetY = unitDy * lvl;
            Color lColor = safeColor(i < chColors.length ? chColors[i] : "#d29922", "#d29922");
            gc.setStroke(lColor.deriveColor(0, 1, 1, lvl == 0.0 || lvl == 1.0 ? 0.9 : 0.65));
            gc.setLineWidth(lvl == 0.0 || lvl == 1.0 ? props.getLineWidth() + 0.5 : props.getLineWidth());
            if (lvl != 0.0 && lvl != 1.0) gc.setLineDashes(3, 4);
            double[] ext = extendLine(x1, y1 + offsetY, x2, y2 + offsetY, ctx.left, ctx.right, ctx.priceTop, ctx.priceBottom, false, true);
            gc.strokeLine(ext[0], ext[1], ext[2], ext[3]);
            gc.setLineDashes();
            double rY = Math.abs(x2 - x1) > 1e-6 ? y1 + offsetY + (y2 - y1) / (x2 - x1) * (ctx.right - x1) : y1 + offsetY;
            if (rY >= ctx.priceTop - 5 && rY <= ctx.priceBottom + 5) {
                gc.setFill(lColor); gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.RIGHT);
                gc.fillText(String.format("%.3f", lvl), ctx.right - 2, rY - 2);
            }
        }
        gc.setTextAlign(TextAlignment.LEFT); gc.setLineWidth(props.getLineWidth());
        gc.setFill(base);
        gc.fillOval(x1 - 3, y1 - 3, 6, 6); gc.fillOval(x2 - 3, y2 - 3, 6, 6);
    }

    private static void drawFibSpeedResistance(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                               ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x0 = timeToX(d.getPoints().get(0).getTime(), ctx), y0 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x1 = timeToX(d.getPoints().get(1).getTime(), ctx), y1 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        Color base = safeColor(props.getColor(), "#d29922");
        gc.setStroke(base.deriveColor(0, 1, 1, 0.25)); gc.setLineDashes(3, 5);
        gc.strokeLine(x1, y0, x1, y1); gc.strokeLine(x0, y1, x1, y1); gc.setLineDashes();
        double fullSlope = (x1 > x0) ? (y1 - y0) / (x1 - x0) : 0;
        double extYFull = y0 + fullSlope * (ctx.right - x0);
        gc.setStroke(base.deriveColor(0, 1, 1, 0.7)); gc.setLineWidth(props.getLineWidth() + 0.5);
        gc.strokeLine(x0, y0, ctx.right, extYFull); gc.setLineWidth(props.getLineWidth());
        if (extYFull >= ctx.priceTop - 5 && extYFull <= ctx.priceBottom + 5) {
            gc.setFill(base); gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText("1×1", ctx.right - 2, extYFull - 2);
        }
        String[] srColors = {"#22c55e","#3b82f6","#d29922","#ef4444","#a855f7"};
        String[] srLabels = {"1/8","1/5","1/3","1/2","2/3"};
        for (int i = 0; i < FIB_SR_MULTIPLES.length; i++) {
            double extY = y0 + fullSlope * FIB_SR_MULTIPLES[i] * (ctx.right - x0);
            Color lColor = safeColor(i < srColors.length ? srColors[i] : "#d29922", "#d29922");
            gc.setStroke(lColor.deriveColor(0, 1, 1, 0.75));
            gc.strokeLine(x0, y0, ctx.right, extY);
            if (extY >= ctx.priceTop - 5 && extY <= ctx.priceBottom + 5) {
                gc.setFill(lColor); gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.RIGHT);
                gc.fillText(srLabels[i], ctx.right - 2, extY - 2);
            }
        }
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(base);
        gc.fillOval(x0 - 3, y0 - 3, 6, 6); gc.fillOval(x1 - 3, y1 - 3, 6, 6);
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
        Double sl = props.getStopLoss(), tp = props.getTakeProfit();
        double yEntry = priceToY(entry, ctx);
        if (tp != null) {
            double yTp = priceToY(tp, ctx);
            gc.setFill(Color.web(isLong ? "#3fb95033" : "#f8514933"));
            gc.fillRect(left, Math.min(yEntry, yTp), right - left, Math.abs(yEntry - yTp));
        }
        if (sl != null) {
            double ySl = priceToY(sl, ctx);
            gc.setFill(Color.web(isLong ? "#f8514933" : "#3fb95033"));
            gc.fillRect(left, Math.min(yEntry, ySl), right - left, Math.abs(yEntry - ySl));
        }
        gc.setLineWidth(1.5);
        drawPriceLine(gc, left, right, yEntry, "#3fb950", "Entry " + formatPrice(entry));
        if (sl != null) {
            double slPct = entry != 0 ? (sl - entry) / entry * 100.0 : 0;
            String slLabel = String.format("SL  %s  (%.2f%%)", formatPrice(sl), slPct);
            drawPriceLine(gc, left, right, priceToY(sl, ctx), "#f85149", slLabel);
        }
        if (tp != null) {
            double tpPct = entry != 0 ? (tp - entry) / entry * 100.0 : 0;
            String tpLabel = String.format("TP  %s  (+%.2f%%)", formatPrice(tp), Math.abs(tpPct));
            drawPriceLine(gc, left, right, priceToY(tp, ctx), "#388bfd", tpLabel);
        }
        if (sl != null && tp != null) {
            double risk = Math.abs(entry - sl), reward = Math.abs(tp - entry);
            if (risk > 0) {
                gc.setFill(Color.web("#e6edf3")); gc.setFont(Font.font("Segoe UI", 12));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText(String.format("R:R 1:%.2f", reward / risk), (left + right) / 2, yEntry - 8);
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
        gc.setStroke(Color.web(colorHex)); gc.setLineDashes();
        gc.strokeLine(left, y, right, y);
        gc.setFill(Color.web(colorHex)); gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(label, left + 4, y - 3);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Annotation Tools
    // ─────────────────────────────────────────────────────────────────────────

    private static void drawTextLabel(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                      ChartDrawingProperties props) {
        if (d.getPoints().isEmpty()) return;
        ChartPoint pt = d.getPoints().getFirst();
        double x = timeToX(pt.getTime(), ctx), y = priceToY(pt.getPrice(), ctx);
        String text = props.getText() != null && !props.getText().isBlank() ? props.getText() : "Text";
        Color color = safeColor(props.getColor(), "#58a6ff");
        double fontSize = props.getFontSize() > 0 ? props.getFontSize() : 12;
        gc.setFont(Font.font("Segoe UI", fontSize));

        // ── Compute wrapped lines ──────────────────────────────────────────────
        double boxW = props.getTextBoxWidth() > 0
                ? props.getTextBoxWidth()
                : Math.max(80, Math.min(text.length() * (fontSize * 0.65) + 16, 240));
        double charW = fontSize * 0.62;
        int charsPerLine = Math.max(1, (int)((boxW - 16) / charW));
        java.util.List<String> lines = wrapText(text, charsPerLine);

        double lineH = fontSize + 4;
        double boxH  = props.getTextBoxHeight() > 0
                ? props.getTextBoxHeight()
                : Math.max(lineH + 10, lines.size() * lineH + 10);
        double boxY  = y - boxH + 4;

        gc.setFill(Color.web("#1c2128ee")); gc.fillRoundRect(x, boxY, boxW, boxH, 4, 4);
        gc.setStroke(color); gc.setLineWidth(1.2);
        gc.strokeRoundRect(x, boxY, boxW, boxH, 4, 4);

        // ── Render each wrapped line ───────────────────────────────────────────
        gc.setFill(Color.web("#e6edf3")); gc.setTextAlign(TextAlignment.LEFT);
        double maxVisibleLines = Math.floor((boxH - 10) / lineH);
        int renderCount = (int) Math.min(lines.size(), maxVisibleLines);
        for (int i = 0; i < renderCount; i++) {
            gc.fillText(lines.get(i), x + 8, boxY + 4 + lineH * (i + 1) - 2);
        }
        // Show "…" indicator if text is clipped
        if (lines.size() > renderCount) {
            gc.setFill(Color.web("#8b949e"));
            gc.fillText("…", x + boxW - 18, boxY + boxH - 5);
        }

        if (props.isEditing()) {
            gc.setStroke(Color.web("#e6edf3")); gc.setLineWidth(1.5);
            gc.strokeLine(x + 8, boxY + 4, x + 8, boxY + boxH - 4);
        }
    }

    private static void drawNoteIcon(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                     ChartDrawingProperties props) {
        if (d.getPoints().isEmpty()) return;
        ChartPoint pt = d.getPoints().getFirst();
        double x = timeToX(pt.getTime(), ctx), y = priceToY(pt.getPrice(), ctx);
        String text = props.getText() != null ? props.getText() : "";
        Color color = safeColor(props.getColor(), "#d29922");
        double noteW = props.getTextBoxWidth()  > 0 ? props.getTextBoxWidth()  : 120;
        double noteH = props.getTextBoxHeight() > 0 ? props.getTextBoxHeight() : 80;

        gc.setFill(Color.web("#2d2a00ee")); gc.fillRoundRect(x, y, noteW, noteH, 4, 4);
        gc.setStroke(color); gc.setLineWidth(1.2); gc.strokeRoundRect(x, y, noteW, noteH, 4, 4);

        // Folded corner decoration
        double foldSize = 12;
        gc.setFill(color.deriveColor(0, 1, 0.6, 0.8));
        gc.fillPolygon(new double[]{x + noteW - foldSize, x + noteW, x + noteW},
                       new double[]{y, y, y + foldSize}, 3);
        // Fold shadow line
        gc.setStroke(color.deriveColor(0, 1, 0.4, 0.5));
        gc.setLineWidth(0.8);
        gc.strokeLine(x + noteW - foldSize, y, x + noteW - foldSize, y + foldSize);
        gc.strokeLine(x + noteW - foldSize, y + foldSize, x + noteW, y + foldSize);

        // ── Render preview text with word-wrap ────────────────────────────────
        double fontSize = 10;
        gc.setFont(Font.font("Segoe UI", fontSize));
        gc.setFill(Color.web("#e6edf3cc"));
        gc.setTextAlign(TextAlignment.LEFT);

        double charW = fontSize * 0.62;
        int charsPerLine = Math.max(1, (int)((noteW - 14) / charW));
        double lineH = fontSize + 3;
        double usableH = noteH - 10;  // leave top/bottom padding
        int maxLines = (int)(usableH / lineH);

        if (!text.isBlank()) {
            java.util.List<String> lines = wrapText(text, charsPerLine);
            int renderCount = Math.min(lines.size(), maxLines);
            for (int i = 0; i < renderCount; i++) {
                gc.fillText(lines.get(i), x + 6, y + 6 + lineH * (i + 1));
            }
            // Show "…" if text was clipped
            if (lines.size() > renderCount) {
                gc.setFill(Color.web("#d29922cc"));
                gc.fillText("…", x + noteW - 16, y + noteH - 6);
            }
        } else {
            // Placeholder hint
            gc.setFill(Color.web("#d29922aa"));
            gc.fillText("✏ Double-click to edit", x + 6, y + 18);
        }

        if (props.isEditing()) {
            gc.setStroke(color.deriveColor(0, 1, 1, 1.0));
            gc.setLineWidth(2.0);
            gc.strokeRoundRect(x + 1, y + 1, noteW - 2, noteH - 2, 4, 4);
        }
    }

    private static void drawCallout(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                    ChartDrawingProperties props) {
        if (d.getPoints().isEmpty()) return;
        ChartPoint tip = d.getPoints().getFirst();
        double tipX = timeToX(tip.getTime(), ctx), tipY = priceToY(tip.getPrice(), ctx);
        String text = props.getText() != null && !props.getText().isBlank() ? props.getText() : "Callout";
        Color color = safeColor(props.getColor(), "#58a6ff");
        double fontSize = props.getFontSize() > 0 ? props.getFontSize() : 12;
        double boxX, boxY;
        if (d.getPoints().size() >= 2) {
            boxX = timeToX(d.getPoints().get(1).getTime(), ctx);
            boxY = priceToY(d.getPoints().get(1).getPrice(), ctx);
        } else { boxX = tipX + 40; boxY = tipY - 30; }

        // ── Compute wrapped lines ──────────────────────────────────────────────
        double boxW = props.getTextBoxWidth() > 0
                ? props.getTextBoxWidth()
                : Math.max(80, Math.min(text.length() * (fontSize * 0.62) + 16, 200));
        double charW = fontSize * 0.62;
        int charsPerLine = Math.max(1, (int)((boxW - 16) / charW));
        java.util.List<String> lines = wrapText(text, charsPerLine);
        double lineH = fontSize + 4;
        double boxH  = props.getTextBoxHeight() > 0
                ? props.getTextBoxHeight()
                : Math.max(lineH + 10, lines.size() * lineH + 10);

        double boxCX = boxX + boxW / 2, boxBY = boxY + boxH;
        gc.setStroke(color); gc.setLineWidth(1.5);
        gc.strokeLine(tipX, tipY, boxCX, boxBY);
        double angle = Math.atan2(tipY - boxBY, tipX - boxCX);
        gc.strokeLine(tipX, tipY, tipX - 8 * Math.cos(angle - Math.PI / 6), tipY - 8 * Math.sin(angle - Math.PI / 6));
        gc.strokeLine(tipX, tipY, tipX - 8 * Math.cos(angle + Math.PI / 6), tipY - 8 * Math.sin(angle + Math.PI / 6));
        gc.setFill(color); gc.fillOval(tipX - 3, tipY - 3, 6, 6);
        gc.setFill(Color.web("#1c2128ee")); gc.fillRoundRect(boxX, boxY, boxW, boxH, 4, 4);
        gc.setStroke(color); gc.setLineWidth(1.2); gc.strokeRoundRect(boxX, boxY, boxW, boxH, 4, 4);

        // Render wrapped lines
        gc.setFill(Color.web("#e6edf3")); gc.setFont(Font.font("Segoe UI", fontSize));
        gc.setTextAlign(TextAlignment.LEFT);
        double maxVisibleLines = Math.floor((boxH - 10) / lineH);
        int renderCount = (int) Math.min(lines.size(), maxVisibleLines);
        for (int i = 0; i < renderCount; i++) {
            gc.fillText(lines.get(i), boxX + 8, boxY + 4 + lineH * (i + 1) - 2);
        }
        if (lines.size() > renderCount) {
            gc.setFill(Color.web("#8b949e"));
            gc.fillText("…", boxX + boxW - 18, boxY + boxH - 5);
        }

        if (props.isEditing()) {
            gc.setStroke(Color.web("#e6edf3")); gc.setLineWidth(1.5);
            gc.strokeLine(boxX + 8, boxY + 4, boxX + 8, boxY + boxH - 4);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Utility tools
    // ─────────────────────────────────────────────────────────────────────────

    private static void drawRuler(GraphicsContext gc, ChartDrawing d, RenderContext ctx, Color color) {
        if (d.getPoints().size() < 2) return;
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx), y1 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx), y2 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        gc.setStroke(color); gc.setLineDashes(5, 3); gc.strokeLine(x1, y1, x2, y2); gc.setLineDashes();
        double priceDiff = d.getPoints().get(1).getPrice() - d.getPoints().get(0).getPrice();
        double pct = d.getPoints().get(0).getPrice() != 0 ? priceDiff / d.getPoints().get(0).getPrice() * 100 : 0;
        gc.setFill(color); gc.setFont(FONT_SMALL);
        gc.fillText(String.format("%s (%.2f%%)", formatPrice(priceDiff), pct),
                (x1 + x2) / 2 + 6, (y1 + y2) / 2 - 6);
    }

    private static void drawArrow(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                  Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx), y1 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx), y2 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        gc.setStroke(color); gc.setLineWidth(2);
        gc.strokeLine(x1, y1, x2, y2);
        double angle = Math.atan2(y2 - y1, x2 - x1);
        gc.strokeLine(x2, y2, x2 - 10 * Math.cos(angle - Math.PI / 6), y2 - 10 * Math.sin(angle - Math.PI / 6));
        gc.strokeLine(x2, y2, x2 - 10 * Math.cos(angle + Math.PI / 6), y2 - 10 * Math.sin(angle + Math.PI / 6));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Anchor handles
    // ─────────────────────────────────────────────────────────────────────────

    private static void drawAnchors(GraphicsContext gc, ChartDrawing d, RenderContext ctx) {
        gc.setFill(Color.web("#ffffff")); gc.setStroke(Color.web("#388bfd")); gc.setLineWidth(1.5);
        for (ChartPoint pt : d.getPoints()) {
            double x = timeToX(pt.getTime(), ctx), y = priceToY(pt.getPrice(), ctx);
            gc.fillOval(x - 4, y - 4, 8, 8); gc.strokeOval(x - 4, y - 4, 8, 8);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Coordinate helpers (public so engine can call them)
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
        if (left && Math.abs(dx) > 1e-6) { double t = (minX - x1) / dx; x1 = minX; y1 = y1 + dy * t; }
        if (right && Math.abs(dx) > 1e-6) { double t = (maxX - x1) / dx; x2 = maxX; y2 = y1 + dy * t; }
        return new double[]{x1, y1, x2, y2};
    }

    private static Color safeColor(String hex, String fallback) {
        try { if (hex != null && !hex.isBlank()) return Color.web(hex); }
        catch (IllegalArgumentException ignored) {}
        return Color.web(fallback);
    }

    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }

    private static String formatPrice(double p) {
        if (Math.abs(p) >= 1000) return String.format("%.2f", p);
        if (Math.abs(p) >= 1)    return String.format("%.4f", p);
        return String.format("%.6f", p);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Chart Patterns
    // ─────────────────────────────────────────────────────────────────────────

    // ─────────────────────────────────────────────────────────────────────────
    //  Shared harmonic pattern colors
    // ─────────────────────────────────────────────────────────────────────────

    /** Purple accent for bullish harmonic legs */
    private static final Color HARMONIC_BULL = Color.web("#a371f7");
    /** Orange accent for bearish harmonic legs */
    private static final Color HARMONIC_BEAR = Color.web("#f78166");
    /** Amber label text */
    private static final Color LABEL_AMBER   = Color.web("#d29922");

    // ─────────────────────────────────────────────────────────────────────────
    //  Chart Patterns — fully differentiated implementations
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * XABCD Harmonic Pattern (Gartley/Bat/Crab/Butterfly).
     *
     * <p>Draws the characteristic 5-point zigzag X→A→B→C→D with alternating
     * bullish (purple) / bearish (orange) legs, Fibonacci ratio labels, and a
     * filled PRZ (Potential Reversal Zone) box between C and D.
     */
    private static void drawXabcdPattern(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                         Color color, ChartDrawingProperties props) {
        int n = d.getPoints().size();
        if (n < 2) return;
        String[] labels = {"X","A","B","C","D"};
        double[] xs = new double[n], ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = timeToX(d.getPoints().get(i).getTime(), ctx);
            ys[i] = priceToY(d.getPoints().get(i).getPrice(), ctx);
        }
        // Alternating leg colors
        Color[] legColors = {HARMONIC_BULL, HARMONIC_BEAR, HARMONIC_BULL, HARMONIC_BEAR};
        gc.setLineWidth(props.getLineWidth());
        for (int i = 0; i < n - 1; i++) {
            Color lc = legColors[i % legColors.length];
            gc.setStroke(lc);
            gc.strokeLine(xs[i], ys[i], xs[i+1], ys[i+1]);
            // Mid-leg Fibonacci ratio annotation
            if (i > 0) {
                double legLen = Math.abs(ys[i] - ys[i-1]);
                double retracement = legLen > 0 ? Math.abs(ys[i+1] - ys[i]) / legLen : 0;
                gc.setFill(LABEL_AMBER); gc.setFont(FONT_SMALL);
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText(String.format("%.3f", retracement),
                        (xs[i] + xs[i+1]) / 2, (ys[i] + ys[i+1]) / 2 - 6);
            }
        }
        // PRZ fill box between last two defined points
        if (n >= 4) {
            int last = n - 1;
            double xL = Math.min(xs[last-1], xs[last]);
            double xR = Math.max(xs[last-1], xs[last]);
            double yT = Math.min(ys[last-1], ys[last]);
            double yB = Math.max(ys[last-1], ys[last]);
            gc.setFill(Color.color(HARMONIC_BULL.getRed(), HARMONIC_BULL.getGreen(),
                    HARMONIC_BULL.getBlue(), 0.12));
            gc.fillRect(xL, yT, xR - xL, yB - yT);
            gc.setStroke(HARMONIC_BULL.deriveColor(0,1,1,0.4));
            gc.setLineDashes(4, 3); gc.strokeRect(xL, yT, xR - xL, yB - yT);
            gc.setLineDashes();
            gc.setFill(LABEL_AMBER); gc.setFont(FONT_SMALL);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("PRZ", (xL+xR)/2, yT - 4);
        }
        // Dots and labels
        gc.setFont(FONT_MEDIUM); gc.setTextAlign(TextAlignment.CENTER);
        for (int i = 0; i < n; i++) {
            Color dot = (i % 2 == 0) ? HARMONIC_BULL : HARMONIC_BEAR;
            gc.setFill(dot); gc.fillOval(xs[i]-5, ys[i]-5, 10, 10);
            gc.setFill(Color.web("#e6edf3"));
            gc.fillText(labels[i], xs[i], ys[i] - 12);
        }
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /**
     * Cypher Harmonic Pattern.
     *
     * <p>Visually distinct from XABCD: the Cypher uses a gold/teal color scheme
     * and highlights the characteristic C-D retracement into the X-A range with
     * a dedicated shaded zone.
     */
    private static void drawCypherPattern(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                          Color color, ChartDrawingProperties props) {
        int n = d.getPoints().size();
        if (n < 2) return;
        String[] labels = {"X","A","B","C","D"};
        Color cypherGold = Color.web("#d29922");
        Color cypherTeal = Color.web("#39d353");
        double[] xs = new double[n], ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = timeToX(d.getPoints().get(i).getTime(), ctx);
            ys[i] = priceToY(d.getPoints().get(i).getPrice(), ctx);
        }
        // X-A-B shaded zone (background)
        if (n >= 3) {
            double polyX[] = {xs[0], xs[1], xs[2], xs[0]};
            double polyY[] = {ys[0], ys[1], ys[2], ys[0]};
            gc.setFill(Color.color(cypherGold.getRed(), cypherGold.getGreen(),
                    cypherGold.getBlue(), 0.07));
            gc.fillPolygon(polyX, polyY, 4);
        }
        // Legs — alternate gold / teal
        Color[] legColors = {cypherGold, cypherTeal, cypherGold, cypherTeal};
        gc.setLineWidth(props.getLineWidth());
        for (int i = 0; i < n - 1; i++) {
            gc.setStroke(legColors[i % legColors.length]);
            gc.strokeLine(xs[i], ys[i], xs[i+1], ys[i+1]);
        }
        // C-D retracement box
        if (n >= 5) {
            double xL = Math.min(xs[3], xs[4]), xR = Math.max(xs[3], xs[4]);
            double yT = Math.min(ys[3], ys[4]), yB = Math.max(ys[3], ys[4]);
            gc.setFill(Color.color(cypherTeal.getRed(), cypherTeal.getGreen(),
                    cypherTeal.getBlue(), 0.15));
            gc.fillRect(xL, yT, xR-xL, yB-yT);
            gc.setStroke(cypherTeal.deriveColor(0,1,1,0.5));
            gc.setLineDashes(3, 4); gc.strokeRect(xL, yT, xR-xL, yB-yT); gc.setLineDashes();
            gc.setFill(cypherGold); gc.setFont(FONT_SMALL);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("Cypher PRZ", (xL+xR)/2, yT - 4);
        }
        // Dots and labels
        gc.setFont(FONT_MEDIUM); gc.setTextAlign(TextAlignment.CENTER);
        for (int i = 0; i < n; i++) {
            Color dot = (i % 2 == 0) ? cypherGold : cypherTeal;
            gc.setFill(dot); gc.fillOval(xs[i]-5, ys[i]-5, 10, 10);
            gc.setFill(Color.web("#e6edf3"));
            gc.fillText(labels[i], xs[i], ys[i] - 12);
        }
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /**
     * Head and Shoulders pattern.
     *
     * <p>Three anchor points: Left Shoulder (LS), Head (H), Right Shoulder (RS).
     * Neckline is drawn between LS and RS and extended to the right as a dashed
     * breakout projection.  Head height is annotated.
     */
    private static void drawHeadAndShoulders(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                              Color color, ChartDrawingProperties props) {
        int n = d.getPoints().size();
        if (n < 2) return;
        String[] labels = {"LS","H","RS"};
        double[] xs = new double[n], ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = timeToX(d.getPoints().get(i).getTime(), ctx);
            ys[i] = priceToY(d.getPoints().get(i).getPrice(), ctx);
        }
        // LS → H → RS zigzag in orange
        gc.setStroke(color); gc.setLineWidth(props.getLineWidth());
        for (int i = 0; i < n - 1; i++) gc.strokeLine(xs[i], ys[i], xs[i+1], ys[i+1]);

        // Neckline: connect LS and RS
        if (n >= 3) {
            double neckX1 = xs[0], neckY1 = ys[0];
            double neckX2 = xs[2], neckY2 = ys[2];
            gc.setStroke(Color.web("#3b82f6").deriveColor(0,1,1,0.8));
            gc.setLineWidth(1.5); gc.setLineDashes(5, 4);
            gc.strokeLine(neckX1, neckY1, neckX2, neckY2);
            // Extended neckline projection
            if (Math.abs(neckX2 - neckX1) > 1e-6) {
                double slope = (neckY2 - neckY1) / (neckX2 - neckX1);
                double xRight = ctx.right;
                double yRight = neckY2 + slope * (xRight - neckX2);
                gc.setLineDashes(2, 6);
                gc.strokeLine(neckX2, neckY2, xRight, yRight);
            }
            gc.setLineDashes(); gc.setLineWidth(props.getLineWidth());
            // "Neckline" label
            gc.setFill(Color.web("#3b82f6")); gc.setFont(FONT_SMALL);
            gc.setTextAlign(TextAlignment.LEFT);
            gc.fillText("Neckline", neckX2 + 6, neckY2 - 3);
            // Head height annotation
            double headY = ys[Math.min(1, n-1)];
            double midNeckY = (neckY1 + neckY2) / 2;
            double midNeckX = (neckX1 + neckX2) / 2;
            if (Math.abs(headY - midNeckY) > 4) {
                gc.setStroke(color.deriveColor(0,1,1,0.5));
                gc.setLineDashes(2, 3);
                gc.strokeLine(midNeckX, headY, midNeckX, midNeckY);
                gc.setLineDashes();
                double headPrice = d.getPoints().get(1).getPrice();
                double neckPrice = (d.getPoints().get(0).getPrice() + d.getPoints().get(2).getPrice()) / 2.0;
                double heightPct = neckPrice != 0 ? Math.abs(headPrice - neckPrice) / neckPrice * 100 : 0;
                gc.setFill(LABEL_AMBER); gc.setFont(FONT_SMALL);
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText(String.format("%.1f%%", heightPct), midNeckX, (headY + midNeckY) / 2);
            }
        }
        // Shoulder / head dots and labels
        gc.setFont(FONT_MEDIUM); gc.setTextAlign(TextAlignment.CENTER);
        Color[] dotColors = {Color.web("#58a6ff"), Color.web("#f85149"), Color.web("#58a6ff")};
        for (int i = 0; i < n; i++) {
            Color dc = dotColors[i < dotColors.length ? i : 0];
            gc.setFill(dc); gc.fillOval(xs[i]-5, ys[i]-5, 10, 10);
            gc.setFill(Color.web("#e6edf3"));
            gc.fillText(labels[i < labels.length ? i : 0], xs[i], ys[i] - 12);
        }
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /**
     * ABCD Harmonic Pattern.
     *
     * <p>Three-point zigzag A→B→C→D with Fibonacci retracement labels on each leg
     * and a distinct green PRZ shading at point D.
     */
    private static void drawAbcdPattern(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                        Color color, ChartDrawingProperties props) {
        int n = d.getPoints().size();
        if (n < 2) return;
        String[] labels = {"A","B","C","D"};
        Color abColor = Color.web("#58a6ff");
        Color cdColor = Color.web("#f78166");
        double[] xs = new double[n], ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = timeToX(d.getPoints().get(i).getTime(), ctx);
            ys[i] = priceToY(d.getPoints().get(i).getPrice(), ctx);
        }
        gc.setLineWidth(props.getLineWidth());
        // A→B (blue)
        if (n >= 2) { gc.setStroke(abColor); gc.strokeLine(xs[0], ys[0], xs[1], ys[1]); }
        // B→C (orange, dashed)
        if (n >= 3) {
            gc.setStroke(cdColor); gc.setLineDashes(4,3);
            gc.strokeLine(xs[1], ys[1], xs[2], ys[2]); gc.setLineDashes();
            // BC retracement of AB
            double ab = Math.abs(ys[0] - ys[1]);
            double bc = Math.abs(ys[1] - ys[2]);
            double ratio = ab > 0 ? bc / ab : 0;
            gc.setFill(LABEL_AMBER); gc.setFont(FONT_SMALL);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(String.format("%.3f", ratio), (xs[1]+xs[2])/2, (ys[1]+ys[2])/2-6);
        }
        // C→D (green)
        if (n >= 4) {
            Color cdGreen = Color.web("#3fb950");
            gc.setStroke(cdGreen); gc.strokeLine(xs[2], ys[2], xs[3], ys[3]);
            // PRZ at D
            double r = 6;
            gc.setFill(Color.color(cdGreen.getRed(), cdGreen.getGreen(), cdGreen.getBlue(), 0.25));
            gc.fillOval(xs[3]-r*2, ys[3]-r*2, r*4, r*4);
            gc.setStroke(cdGreen.deriveColor(0,1,1,0.6));
            gc.strokeOval(xs[3]-r*2, ys[3]-r*2, r*4, r*4);
            gc.setFill(Color.web("#e6edf3")); gc.setFont(FONT_SMALL);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("D (PRZ)", xs[3], ys[3]-14);
        }
        // Dots and labels
        gc.setFont(FONT_MEDIUM); gc.setTextAlign(TextAlignment.CENTER);
        for (int i = 0; i < n; i++) {
            gc.setFill(i % 2 == 0 ? abColor : cdColor);
            gc.fillOval(xs[i]-4, ys[i]-4, 8, 8);
            gc.setFill(Color.web("#e6edf3"));
            gc.fillText(labels[i < labels.length ? i : i % labels.length], xs[i], ys[i]-10);
        }
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /**
     * Triangle Pattern – ascending, descending, or symmetrical.
     *
     * <p>Upper trendline connects points 0 and 2; lower trendline connects 1 and 2.
     * Both lines are extended forward to highlight the convergence (apex) of the triangle.
     */
    private static void drawTrianglePattern(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                            Color color, ChartDrawingProperties props) {
        int n = Math.min(d.getPoints().size(), 3);
        if (n < 2) return;
        double x0 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y0 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x1 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y1 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        gc.setStroke(color); gc.setLineWidth(props.getLineWidth());
        // Upper trendline A→C
        if (n >= 3) {
            double x2 = timeToX(d.getPoints().get(2).getTime(), ctx);
            double y2 = priceToY(d.getPoints().get(2).getPrice(), ctx);
            // Fill triangle area
            gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(),
                    clamp(props.getFillOpacity() > 0 ? props.getFillOpacity() : 0.10)));
            gc.fillPolygon(new double[]{x0, x1, x2}, new double[]{y0, y1, y2}, 3);
            // Upper line (0→2)
            gc.setStroke(Color.web("#58a6ff")); gc.strokeLine(x0, y0, x2, y2);
            // Lower line (1→2)
            gc.setStroke(Color.web("#f78166")); gc.strokeLine(x1, y1, x2, y2);
            // Extend upper and lower to apex
            if (Math.abs(x2 - x0) > 1e-6 && Math.abs(x2 - x1) > 1e-6) {
                double slopeU = (y2 - y0) / (x2 - x0);
                double slopeL = (y2 - y1) / (x2 - x1);
                double xExt = ctx.right;
                gc.setStroke(Color.web("#58a6ff").deriveColor(0,1,1,0.4));
                gc.setLineDashes(4,4); gc.strokeLine(x2, y2, xExt, y2 + slopeU*(xExt-x2)); gc.setLineDashes();
                gc.setStroke(Color.web("#f78166").deriveColor(0,1,1,0.4));
                gc.setLineDashes(4,4); gc.strokeLine(x2, y2, xExt, y2 + slopeL*(xExt-x2)); gc.setLineDashes();
            }
            // Apex label
            gc.setFill(LABEL_AMBER); gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText("Apex", x2, y2 - 8);
        } else {
            gc.setStroke(color); gc.strokeLine(x0, y0, x1, y1);
        }
        // Labels A, B, C
        String[] labels = {"A","B","C"};
        gc.setFont(FONT_MEDIUM); gc.setTextAlign(TextAlignment.CENTER);
        Color[] dotColors = {Color.web("#58a6ff"), Color.web("#f78166"), color};
        for (int i = 0; i < n; i++) {
            double px = timeToX(d.getPoints().get(i).getTime(), ctx);
            double py = priceToY(d.getPoints().get(i).getPrice(), ctx);
            gc.setFill(dotColors[i]); gc.fillOval(px-4, py-4, 8, 8);
            gc.setFill(Color.web("#e6edf3")); gc.fillText(labels[i], px, py-10);
        }
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /**
     * Three Drives Pattern – three equal harmonic legs (1-2-3-4-5).
     *
     * <p>Rendered with alternating red/green segments to distinguish "drives"
     * (1,3,5) from "corrections" (2,4).  Each drive pair is shaded.
     */
    private static void drawThreeDrivesPattern(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                               Color color, ChartDrawingProperties props) {
        int n = d.getPoints().size();
        if (n < 2) return;
        String[] labels = {"1","2","3","4","5"};
        // Drive legs (0→1, 2→3, 4→5) in green; corrections (1→2, 3→4) in red
        Color driveColor  = Color.web("#3fb950");
        Color corrColor   = Color.web("#f85149");
        double[] xs = new double[n], ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = timeToX(d.getPoints().get(i).getTime(), ctx);
            ys[i] = priceToY(d.getPoints().get(i).getPrice(), ctx);
        }
        gc.setLineWidth(props.getLineWidth() + 0.5);
        for (int i = 0; i < n - 1; i++) {
            // drives are even-indexed legs (0→1, 2→3, 4→5)
            Color lc = (i % 2 == 0) ? driveColor : corrColor;
            gc.setStroke(lc); gc.strokeLine(xs[i], ys[i], xs[i+1], ys[i+1]);
            // Shade drive rectangles
            if (i % 2 == 0 && i + 1 < n) {
                double xL = Math.min(xs[i], xs[i+1]), xR = Math.max(xs[i], xs[i+1]);
                double yT = Math.min(ys[i], ys[i+1]), yB = Math.max(ys[i], ys[i+1]);
                gc.setFill(Color.color(driveColor.getRed(), driveColor.getGreen(),
                        driveColor.getBlue(), 0.07));
                gc.fillRect(xL, yT, xR-xL, yB-yT);
            }
        }
        // Dots and labels
        gc.setFont(FONT_MEDIUM); gc.setTextAlign(TextAlignment.CENTER);
        for (int i = 0; i < n; i++) {
            Color dc = (i % 2 == 0) ? driveColor : corrColor;
            gc.setFill(dc); gc.fillOval(xs[i]-5, ys[i]-5, 10, 10);
            gc.setFill(Color.web("#e6edf3"));
            gc.fillText(labels[i < labels.length ? i : i % labels.length], xs[i], ys[i]-12);
        }
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /**
     * Generic labelled zigzag used by most chart patterns and Elliott waves.
     * Connects all points with lines and draws a label at each anchor.
     */
    private static void drawLabelledZigzag(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                           Color color, ChartDrawingProperties props, String[] labels) {
        int n = d.getPoints().size();
        if (n < 2) return;
        gc.setStroke(color);
        gc.setLineWidth(props.getLineWidth());
        double[] xs = new double[n], ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = timeToX(d.getPoints().get(i).getTime(), ctx);
            ys[i] = priceToY(d.getPoints().get(i).getPrice(), ctx);
        }
        // Draw fill between consecutive pairs
        gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(),
                clamp(props.getFillOpacity() > 0 ? props.getFillOpacity() : 0.08)));
        if (n >= 3) gc.fillPolygon(xs, ys, n);

        // Draw zigzag lines
        for (int i = 0; i < n - 1; i++) {
            gc.setStroke(color.deriveColor(0, 1, 1, i % 2 == 0 ? 0.9 : 0.65));
            gc.strokeLine(xs[i], ys[i], xs[i+1], ys[i+1]);
        }
        // Draw dots and labels
        gc.setFill(color);
        gc.setFont(FONT_MEDIUM);
        gc.setTextAlign(TextAlignment.CENTER);
        for (int i = 0; i < n; i++) {
            gc.fillOval(xs[i] - 4, ys[i] - 4, 8, 8);
            if (i < labels.length) {
                gc.setFill(Color.web("#e6edf3"));
                gc.fillText(labels[i], xs[i], ys[i] - 10);
                gc.setFill(color);
            }
        }
        gc.setTextAlign(TextAlignment.LEFT);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Elliott Waves — color-coded by wave type
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Elliott wave renderer with fully color-coded segments.
     *
     * <p>Impulse waves (1,3,5) are drawn in green; corrective waves (2,4) in red.
     * Correction (A,B,C) and combination waves use distinct colors per leg.
     * A small badge box is drawn at each anchor.
     */
    private static void drawElliottWave(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                        Color color, ChartDrawingProperties props, String[] waveLabels) {
        int n = d.getPoints().size();
        if (n < 2) return;
        double[] xs = new double[n], ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = timeToX(d.getPoints().get(i).getTime(), ctx);
            ys[i] = priceToY(d.getPoints().get(i).getPrice(), ctx);
        }
        // Color palette per leg: impulse alternates green/red; ABC = blue/red/blue; WXY = amber/gray/amber
        boolean isImpulse    = waveLabels.length == 5 && waveLabels[0].equals("1");
        boolean isCorrection = waveLabels.length == 3 && waveLabels[0].equals("A");
        boolean isTriangle   = waveLabels.length == 5 && waveLabels[0].equals("A");
        // isCombo = !isImpulse && !isCorrection && !isTriangle (else branch below)

        Color[] impulseColors    = {Color.web("#3fb950"), Color.web("#f85149"),
                                    Color.web("#3fb950"), Color.web("#f85149"), Color.web("#3fb950")};
        Color[] correctionColors = {Color.web("#f85149"), Color.web("#3fb950"), Color.web("#f85149")};
        Color[] triangleColors   = {Color.web("#58a6ff"), Color.web("#d29922"), Color.web("#58a6ff"),
                                    Color.web("#d29922"), Color.web("#58a6ff")};
        Color[] comboColors      = {Color.web("#d29922"), Color.web("#8b949e"), Color.web("#d29922"),
                                    Color.web("#8b949e"), Color.web("#d29922")};

        Color[] legColors = isImpulse    ? impulseColors
                          : isCorrection ? correctionColors
                          : isTriangle   ? triangleColors
                          : comboColors;

        gc.setLineWidth(props.getLineWidth());
        for (int i = 0; i < n - 1; i++) {
            Color lc = legColors[i % legColors.length];
            gc.setStroke(lc);
            // Impulse wave 3 (index 2→3) is typically the strongest — draw thicker
            if (isImpulse && i == 2) gc.setLineWidth(props.getLineWidth() + 1);
            else gc.setLineWidth(props.getLineWidth());
            gc.strokeLine(xs[i], ys[i], xs[i+1], ys[i+1]);
        }

        // Shaded region under impulse waves (1,3,5)
        if (isImpulse && n >= 6) {
            gc.setFill(Color.color(0.24, 0.72, 0.31, 0.06));
            gc.fillPolygon(xs, ys, n);
        }

        // Badge labels at each anchor
        gc.setFont(Font.font("Segoe UI", 10));
        gc.setTextAlign(TextAlignment.CENTER);
        for (int i = 0; i < n; i++) {
            Color lc = legColors[Math.min(i, legColors.length-1)];
            // Badge background
            double bw = 16, bh = 14;
            gc.setFill(lc.deriveColor(0,1,0.4,0.85));
            gc.fillRoundRect(xs[i]-bw/2, ys[i]-bh-6, bw, bh, 4, 4);
            gc.setStroke(lc); gc.setLineWidth(1);
            gc.strokeRoundRect(xs[i]-bw/2, ys[i]-bh-6, bw, bh, 4, 4);
            // Label text
            gc.setFill(Color.web("#e6edf3"));
            String lbl = i < waveLabels.length ? waveLabels[i] : "?";
            gc.fillText(lbl, xs[i], ys[i] - 9);
            // Dot
            gc.setFill(lc); gc.fillOval(xs[i]-4, ys[i]-4, 8, 8);
        }
        gc.setLineWidth(props.getLineWidth());
        gc.setTextAlign(TextAlignment.LEFT);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Additional Fibonacci Tools
    // ─────────────────────────────────────────────────────────────────────────

    /** Trend-Based Fib Time: vertical Fibonacci-spaced time zones anchored to a trend. */
    private static void drawFibTrendBasedTime(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                              ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        int startIdx = DrawingCoordinateMapper.timeToBarIndex(ctx.bars, d.getPoints().get(0).getTime());
        int endIdx   = DrawingCoordinateMapper.timeToBarIndex(ctx.bars, d.getPoints().get(1).getTime());
        int trendLen = Math.abs(endIdx - startIdx);
        Color base = safeColor(props.getColor(), "#d29922");
        double barW = ctx.plotWidth() / Math.max(1, ctx.visibleBars);
        double[] fibMultipliers = {0.618, 1.0, 1.272, 1.618, 2.0, 2.618, 4.236};
        for (double mult : fibMultipliers) {
            int offset = (int) Math.round(trendLen * mult);
            int idx = endIdx + offset;
            if (idx < 0 || idx >= ctx.bars.size()) continue;
            double x = ctx.left + (idx - ctx.startBarIndex + 0.5) * barW;
            if (x < ctx.left || x > ctx.right) continue;
            gc.setStroke(base.deriveColor(0, 1, 1, 0.6));
            gc.setLineWidth(1.0);
            gc.setLineDashes(3, 5);
            gc.strokeLine(x, ctx.priceTop, x, ctx.priceBottom);
            gc.setLineDashes();
            gc.setFill(base);
            gc.setFont(FONT_SMALL);
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(String.format("%.3f", mult), x, ctx.priceTop + 12);
        }
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setLineWidth(props.getLineWidth());
    }

    /** Fib Circles: concentric circles centred on point 0, radii at Fibonacci multiples. */
    private static void drawFibCircles(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                       Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double cx = timeToX(d.getPoints().get(0).getTime(), ctx);
        double cy = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double ex = timeToX(d.getPoints().get(1).getTime(), ctx);
        double ey = priceToY(d.getPoints().get(1).getPrice(), ctx);
        double baseR = Math.sqrt((ex - cx) * (ex - cx) + (ey - cy) * (ey - cy));
        double[] fibR = {0.382, 0.618, 1.0, 1.618, 2.618};
        gc.setFill(color); gc.fillOval(cx - 3, cy - 3, 6, 6);
        for (int i = 0; i < fibR.length; i++) {
            double r = baseR * fibR[i];
            gc.setStroke(color.deriveColor(0, 1, 1, 0.7 - i * 0.1));
            gc.setLineWidth(props.getLineWidth());
            gc.setLineDashes(i % 2 == 0 ? null : new double[]{4, 4});
            gc.strokeOval(cx - r, cy - r, r * 2, r * 2);
            gc.setLineDashes();
            gc.setFill(color.deriveColor(0, 1, 1, 0.8));
            gc.setFont(FONT_SMALL);
            gc.setTextAlign(TextAlignment.LEFT);
            gc.fillText(String.format("%.3f", fibR[i]), cx + r * 0.7, cy - r * 0.7);
        }
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /** Fib Spiral: golden spiral drawn using quarter-circle arcs. */
    private static void drawFibSpiral(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                      Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double cx = timeToX(d.getPoints().get(0).getTime(), ctx);
        double cy = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double ex = timeToX(d.getPoints().get(1).getTime(), ctx);
        double ey = priceToY(d.getPoints().get(1).getPrice(), ctx);
        double baseR = Math.sqrt((ex - cx) * (ex - cx) + (ey - cy) * (ey - cy));
        // Draw approximation using quarter arcs at Fibonacci-scaled radii
        double[] fibR = {1.0, 1.618, 2.618, 4.236, 6.854};
        double startAngle = 270;  // degrees
        gc.setStroke(color);
        gc.setLineWidth(props.getLineWidth());
        for (double r : fibR) {
            double rad = baseR * r;
            gc.strokeArc(cx - rad, cy - rad, rad * 2, rad * 2, startAngle, 90,
                    javafx.scene.shape.ArcType.OPEN);
            startAngle += 90;
        }
        gc.setFill(color); gc.fillOval(cx - 3, cy - 3, 6, 6);
    }

    /** Fib Speed Resistance Arcs: arcs radiating from origin. */
    private static void drawFibArcs(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                    Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x0 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y0 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x1 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y1 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        double baseR = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
        double[] levels = {0.382, 0.5, 0.618, 0.786, 1.0};
        String[] labels = {"38.2%","50%","61.8%","78.6%","100%"};
        gc.setFill(color); gc.fillOval(x0 - 3, y0 - 3, 6, 6);
        for (int i = 0; i < levels.length; i++) {
            double r = baseR * levels[i];
            gc.setStroke(color.deriveColor(0, 1, 1, 0.75 - i * 0.1));
            gc.setLineWidth(props.getLineWidth());
            gc.strokeArc(x0 - r, y0 - r, r * 2, r * 2, 180, -180,
                    javafx.scene.shape.ArcType.OPEN);
            if (y0 - r >= ctx.priceTop - 5 && y0 - r <= ctx.priceBottom + 5) {
                gc.setFill(color); gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.RIGHT);
                gc.fillText(labels[i], x0, y0 - r - 2);
            }
        }
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /** Fib Wedge: converging trendlines based on Fibonacci levels. */
    private static void drawFibWedge(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                     Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y1 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y2 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        // Draw upper and lower converging lines at Fib ratios
        double[] ratios = {1.0, 0.618, 0.382};
        String[] colors = {"#e6edf3","#d29922","#3b82f6"};
        for (int i = 0; i < ratios.length; i++) {
            double mid = y1 + (y2 - y1) * ratios[i];
            Color lc = safeColor(colors[i], "#d29922");
            gc.setStroke(lc.deriveColor(0, 1, 1, 0.8));
            gc.setLineWidth(i == 0 ? props.getLineWidth() + 0.5 : props.getLineWidth());
            if (i > 0) gc.setLineDashes(3, 4);
            gc.strokeLine(x1, mid, x2, y2);
            gc.setLineDashes();
        }
        gc.setFill(color); gc.fillOval(x1 - 3, y1 - 3, 6, 6); gc.fillOval(x2 - 3, y2 - 3, 6, 6);
        gc.setFill(color); gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("Fib Wedge", x1 + 4, y1 - 8);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /** Pitchfan: Andrew's Pitchfork with Fibonacci fan lines. */
    private static void drawPitchfan(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                     Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 3) return;
        double xA = timeToX(d.getPoints().get(0).getTime(), ctx);
        double yA = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double xB = timeToX(d.getPoints().get(1).getTime(), ctx);
        double yB = priceToY(d.getPoints().get(1).getPrice(), ctx);
        double xC = timeToX(d.getPoints().get(2).getTime(), ctx);
        double yC = priceToY(d.getPoints().get(2).getPrice(), ctx);
        double midX = (xB + xC) / 2, midY = (yB + yC) / 2;
        // Median line
        gc.setStroke(color); gc.setLineWidth(props.getLineWidth() + 0.5);
        gc.strokeLine(xA, yA, midX, midY);
        // Extend median to right edge
        if (Math.abs(midX - xA) > 1e-6) {
            double slope = (midY - yA) / (midX - xA);
            gc.strokeLine(midX, midY, ctx.right, midY + slope * (ctx.right - midX));
        }
        // Upper and lower handles
        gc.setStroke(color.deriveColor(0, 1, 1, 0.7)); gc.setLineWidth(props.getLineWidth());
        gc.strokeLine(xA, yA, xB, yB);
        gc.strokeLine(xA, yA, xC, yC);
        // Fib fan lines from pivot A
        double[] fibRatios = {0.236, 0.382, 0.5, 0.618, 0.786};
        String[] fanColors = {"#22c55e","#3b82f6","#e6edf3","#d29922","#ef4444"};
        double dy = yB - yC;
        for (int i = 0; i < fibRatios.length; i++) {
            double targetY = midY + dy * fibRatios[i];
            Color lc = safeColor(fanColors[i], "#d29922");
            gc.setStroke(lc.deriveColor(0, 1, 1, 0.7)); gc.setLineDashes(3, 4);
            gc.strokeLine(xA, yA, ctx.right, targetY);
            gc.setLineDashes();
            gc.setFill(lc); gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(String.format("%.1f%%", fibRatios[i] * 100), ctx.right - 2, targetY - 2);
        }
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(color);
        gc.fillOval(xA - 3, yA - 3, 6, 6);
        gc.fillOval(xB - 3, yB - 3, 6, 6);
        gc.fillOval(xC - 3, yC - 3, 6, 6);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Gann Tools
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Gann Box — rectangular grid of Gann angles radiating from the pivot corner.
     *
     * <p>All seven canonical Gann angles (8×1, 4×1, 2×1, 1×1, 1×2, 1×4, 1×8) are
     * drawn inside the box in color-coded fashion: 1×1 (white/bold), steep angles
     * (>1×1, blue), shallow angles (<1×1, amber).  Horizontal/vertical mid-lines
     * divide the box into quadrants.
     */
    private static void drawGannBox(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                    Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y1 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y2 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        double xL = Math.min(x1, x2), xR = Math.max(x1, x2);
        double yT = Math.min(y1, y2), yB = Math.max(y1, y2);
        double w = xR - xL, h = yB - yT;
        if (w < 2 || h < 2) return;

        // Subtle fill
        gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(),
                clamp(props.getFillOpacity() > 0 ? props.getFillOpacity() : 0.05)));
        gc.fillRect(xL, yT, w, h);
        // Outer border
        gc.setStroke(color); gc.setLineWidth(props.getLineWidth());
        gc.strokeRect(xL, yT, w, h);

        // Mid-lines (quadrant dividers)
        gc.setStroke(color.deriveColor(0,1,1,0.3)); gc.setLineWidth(0.8);
        gc.setLineDashes(3,4);
        gc.strokeLine(xL, (yT+yB)/2, xR, (yT+yB)/2); // horizontal mid
        gc.strokeLine((xL+xR)/2, yT, (xL+xR)/2, yB); // vertical mid
        gc.setLineDashes();

        // Pivot corner: bottom-left (standard Gann from low-left)
        double px = xL, py = yB;

        // Gann angles: {xRatio, yRatio, label, color-hint}
        // xRatio: how far along width; yRatio: how far up height (both 0..1)
        double[][] angles = {
            {8.0/8, 1.0/8, 8,1},
            {4.0/5, 1.0/5, 4,1},
            {2.0/3, 1.0/3, 2,1},
            {1.0/2, 1.0/2, 1,1},  // 1×1
            {1.0/3, 2.0/3, 1,2},
            {1.0/5, 4.0/5, 1,4},
            {1.0/8, 7.0/8, 1,8}
        };
        String[] aLabels = {"8×1","4×1","2×1","1×1","1×2","1×4","1×8"};
        // Colors: steep=blue, 1×1=white, shallow=amber
        String[] aColors = {"#3b82f6","#58a6ff","#8b5cf6","#e6edf3","#f59e0b","#f97316","#ef4444"};
        for (int i = 0; i < angles.length; i++) {
            double eX = xL + w * angles[i][0];
            double eY = yB - h * angles[i][1];
            eX = Math.min(eX, xR); eY = Math.max(eY, yT);
            Color lc = safeColor(aColors[i], "#e6edf3");
            gc.setStroke(lc.deriveColor(0,1,1, i==3 ? 0.9 : 0.65));
            gc.setLineWidth(i == 3 ? props.getLineWidth()+0.5 : 1.0);
            gc.setLineDashes(i == 3 ? null : new double[]{3,4});
            gc.strokeLine(px, py, eX, eY);
            gc.setLineDashes();
            if (eX > xL + 8 && eY > yT + 4) {
                gc.setFill(lc); gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.LEFT);
                gc.fillText(aLabels[i], eX + 2, eY - 2);
            }
        }
        gc.setLineWidth(props.getLineWidth());
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /**
     * Gann Square Fixed — a perfect square overlay.
     *
     * <p>The "fixed" square ensures equal price-to-time units by constraining
     * the bounding box to a square (minimum of width/height) and drawing
     * an inner price-scale grid with 8 evenly-spaced horizontal levels.
     * This is visually distinct from Gann Box (no angles, instead a price grid).
     */
    private static void drawGannSquareFixed(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                            Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y1 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y2 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        double xL = Math.min(x1, x2), yT = Math.min(y1, y2);
        double side = Math.min(Math.abs(x2-x1), Math.abs(y2-y1));
        if (side < 2) return;
        Color fixedColor = Color.web("#d29922");  // gold — distinct from Gann Box

        // Background fill
        gc.setFill(Color.color(fixedColor.getRed(), fixedColor.getGreen(),
                fixedColor.getBlue(), 0.06));
        gc.fillRect(xL, yT, side, side);
        // Outer square border (thicker, gold)
        gc.setStroke(fixedColor); gc.setLineWidth(props.getLineWidth() + 0.5);
        gc.strokeRect(xL, yT, side, side);
        // Inner price-scale grid: 8 equal subdivisions
        int divisions = 8;
        gc.setStroke(fixedColor.deriveColor(0,1,1,0.3));
        gc.setLineWidth(0.7); gc.setLineDashes(2,3);
        for (int i = 1; i < divisions; i++) {
            double gY = yT + side * i / divisions;
            gc.strokeLine(xL, gY, xL + side, gY);
            double gX = xL + side * i / divisions;
            gc.strokeLine(gX, yT, gX, yT + side);
        }
        gc.setLineDashes(); gc.setLineWidth(props.getLineWidth());
        // Diagonal (45° price-time) — the "square of nine" axis
        gc.setStroke(fixedColor); gc.setLineWidth(1.5);
        gc.strokeLine(xL, yT + side, xL + side, yT);  // bottom-left to top-right
        gc.strokeLine(xL, yT, xL + side, yT + side);  // top-left to bottom-right
        // Label
        gc.setFill(fixedColor); gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("Sq. Fixed", xL + side/2, yT + side/2);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /**
     * Gann Square (dynamic) — similar to Gann Box but rotated 45° so the 1×1
     * diagonal runs along the dominant trend, and concentric square rings are
     * drawn outward at 1/4 increments.  Visually distinct from both Gann Box
     * and Gann Square Fixed.
     */
    private static void drawGannSquare(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                       Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y1 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y2 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        double xL = Math.min(x1, x2), xR = Math.max(x1, x2);
        double yT = Math.min(y1, y2), yB = Math.max(y1, y2);
        double w = xR - xL, h = yB - yT;
        if (w < 2 || h < 2) return;
        Color sqColor = Color.web("#a371f7");  // purple — distinct from Gann Box (blue) & Fixed (gold)

        // Outer box
        gc.setFill(Color.color(sqColor.getRed(), sqColor.getGreen(), sqColor.getBlue(), 0.06));
        gc.fillRect(xL, yT, w, h);
        gc.setStroke(sqColor); gc.setLineWidth(props.getLineWidth());
        gc.strokeRect(xL, yT, w, h);
        // Concentric inner squares at 1/4, 1/2, 3/4
        for (int ring = 1; ring <= 3; ring++) {
            double shrink = ring * 0.25;
            double rx = xL + w * shrink / 2;
            double ry = yT + h * shrink / 2;
            double rw = w * (1 - shrink);
            double rh = h * (1 - shrink);
            if (rw < 2 || rh < 2) break;
            gc.setStroke(sqColor.deriveColor(0,1,1,0.35));
            gc.setLineWidth(0.8); gc.setLineDashes(3,3);
            gc.strokeRect(rx, ry, rw, rh);
            gc.setLineDashes();
        }
        // Cross-hair through center
        double cx = (xL+xR)/2, cy = (yT+yB)/2;
        gc.setStroke(sqColor.deriveColor(0,1,1,0.4)); gc.setLineWidth(0.8);
        gc.strokeLine(xL, cy, xR, cy);
        gc.strokeLine(cx, yT, cx, yB);
        // Center dot and label
        gc.setFill(sqColor); gc.fillOval(cx-4, cy-4, 8, 8);
        gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("Gann Sq", cx, yT + 14);
        gc.setLineWidth(props.getLineWidth());
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private static void drawGannFan(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                    Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x0 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y0 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x1 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y1 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        // Gann fan angles from pivot
        double dx = x1 - x0, dy = y1 - y0;
        double[][] gannRatios = {{1,1},{1,2},{2,1},{1,4},{4,1},{1,8},{8,1}};
        String[] gannLabels = {"1×1","1×2","2×1","1×4","4×1","1×8","8×1"};
        String[] gannColors = {"#e6edf3","#3b82f6","#3b82f6","#22c55e","#22c55e","#d29922","#d29922"};
        gc.setFill(color); gc.fillOval(x0 - 4, y0 - 4, 8, 8);
        for (int i = 0; i < gannRatios.length; i++) {
            double sX = gannRatios[i][0], sY = gannRatios[i][1];
            double dirX = dx * sX / Math.max(sX, sY);
            double dirY = dy * sY / Math.max(sX, sY);
            // Extend to right edge
            if (Math.abs(dirX) > 1e-6) {
                double t = (ctx.right - x0) / dirX;
                double extY = y0 + t * dirY;
                Color lc = safeColor(gannColors[i], "#d29922");
                gc.setStroke(lc.deriveColor(0, 1, 1, 0.8));
                gc.setLineWidth(i == 0 ? props.getLineWidth() + 0.5 : props.getLineWidth());
                gc.strokeLine(x0, y0, ctx.right, extY);
                if (extY >= ctx.priceTop - 5 && extY <= ctx.priceBottom + 5) {
                    gc.setFill(lc); gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.RIGHT);
                    gc.fillText(gannLabels[i], ctx.right - 2, extY - 2);
                }
            }
        }
        gc.setTextAlign(TextAlignment.LEFT);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Forecasting Tools
    // ─────────────────────────────────────────────────────────────────────────

    private static void drawPositionForecast(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                             Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y1 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y2 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        gc.setStroke(color); gc.setLineWidth(props.getLineWidth());
        gc.setLineDashes(6, 4);
        gc.strokeLine(x1, y1, x2, y2);
        gc.setLineDashes();
        gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(),
                clamp(props.getFillOpacity() > 0 ? props.getFillOpacity() : 0.15)));
        gc.fillRect(Math.min(x1, x2), Math.min(y1, y2), Math.abs(x2 - x1), Math.abs(y2 - y1));
        gc.setFill(color); gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.CENTER);
        double pctChange = d.getPoints().get(0).getPrice() != 0
                ? (d.getPoints().get(1).getPrice() - d.getPoints().get(0).getPrice())
                / d.getPoints().get(0).getPrice() * 100.0
                : 0;
        gc.fillText(String.format("Forecast  %+.2f%%", pctChange), (x1 + x2) / 2, Math.min(y1, y2) - 5);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setFill(color); gc.fillOval(x1 - 3, y1 - 3, 6, 6); gc.fillOval(x2 - 3, y2 - 3, 6, 6);
    }

    private static void drawBarsPattern(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                        Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y1 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y2 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        // Draw a rectangle representing bar pattern area
        double xL = Math.min(x1, x2), xR = Math.max(x1, x2);
        double yT = Math.min(y1, y2), yB = Math.max(y1, y2);
        gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(),
                clamp(props.getFillOpacity() > 0 ? props.getFillOpacity() : 0.12)));
        gc.fillRect(xL, yT, xR - xL, yB - yT);
        gc.setStroke(color); gc.setLineWidth(props.getLineWidth());
        gc.setLineDashes(4, 4);
        gc.strokeRect(xL, yT, xR - xL, yB - yT);
        gc.setLineDashes();
        gc.setFill(color); gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("Bars Pattern", (xL + xR) / 2, yT - 5);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private static void drawGhostFeed(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                      Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y1 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y2 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        // Draw ghost (projected) price bars as dashed rectangles
        int numBars = 8;
        double barW = Math.max(4, (x2 - x1) / numBars);
        double priceStep = (d.getPoints().get(1).getPrice() - d.getPoints().get(0).getPrice()) / numBars;
        gc.setStroke(color.deriveColor(0, 1, 1, 0.5));
        gc.setLineWidth(1.0);
        gc.setLineDashes(3, 3);
        for (int i = 0; i < numBars; i++) {
            double bx = x1 + i * barW;
            double bTop = y1 + i * (y2 - y1) / numBars - Math.abs(priceStep) * 0.4;
            double bBot = bTop + Math.abs(priceStep) * 0.8;
            gc.strokeRect(bx + 1, bTop, barW - 2, bBot - bTop);
        }
        gc.setLineDashes();
        gc.setStroke(color.deriveColor(0, 1, 1, 0.7));
        gc.strokeLine(x1, y1, x2, y2);
        gc.setFill(color); gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("Ghost Feed", x1 + 4, y1 - 8);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private static void drawSector(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                   Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 3) return;
        double x0 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y0 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x1 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y1 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        double x2 = timeToX(d.getPoints().get(2).getTime(), ctx);
        double y2 = priceToY(d.getPoints().get(2).getPrice(), ctx);
        // Draw sector as a pie-segment approximation
        gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(),
                clamp(props.getFillOpacity() > 0 ? props.getFillOpacity() : 0.15)));
        gc.fillPolygon(new double[]{x0, x1, x2}, new double[]{y0, y1, y2}, 3);
        gc.setStroke(color); gc.setLineWidth(props.getLineWidth());
        gc.strokePolygon(new double[]{x0, x1, x2}, new double[]{y0, y1, y2}, 3);
        gc.setFill(color); gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("Sector", (x0 + x1 + x2) / 3, (y0 + y1 + y2) / 3);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Volume Tools
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Anchored VWAP — VWAP line anchored to a user-selected bar, plus ±1σ and ±2σ bands.
     *
     * <p>The main VWAP line is drawn in the tool's chosen color.
     * Upper bands (+1σ, +2σ) are drawn in green; lower bands (−1σ, −2σ) in red.
     * This makes the deviation envelope immediately readable for mean-reversion setups.
     */
    private static void drawAnchoredVwap(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                         Color color, ChartDrawingProperties props) {
        if (d.getPoints().isEmpty() || ctx.bars == null || ctx.bars.isEmpty()) return;
        int anchorIdx = DrawingCoordinateMapper.timeToBarIndex(ctx.bars, d.getPoints().getFirst().getTime());
        double barW = ctx.plotWidth() / Math.max(1, ctx.visibleBars);
        Color bandUp1 = Color.web("#3fb950cc");   // green +1σ
        Color bandUp2 = Color.web("#3fb95066");   // green +2σ (lighter)
        Color bandDn1 = Color.web("#f85149cc");   // red  −1σ
        Color bandDn2 = Color.web("#f8514966");   // red  −2σ (lighter)

        // Compute running VWAP, TP, and TP² from anchor bar
        List<OhlcvBar> bars = ctx.bars;
        int barCount = bars.size();
        double[] vwapY  = new double[barCount];
        double[] up1Y   = new double[barCount];
        double[] dn1Y   = new double[barCount];
        double[] up2Y   = new double[barCount];
        double[] dn2Y   = new double[barCount];
        double[] barX   = new double[barCount];
        boolean[] valid = new boolean[barCount];
        double cumPV = 0, cumPV2 = 0, cumV = 0;

        for (int i = anchorIdx; i < barCount; i++) {
            OhlcvBar bar = bars.get(i);
            double tp  = (bar.getHigh().doubleValue() + bar.getLow().doubleValue()
                    + bar.getClose().doubleValue()) / 3.0;
            double vol = bar.getVolume().doubleValue();
            cumPV  += tp * vol;
            cumPV2 += tp * tp * vol;
            cumV   += vol;
            if (cumV == 0) continue;
            double vwap  = cumPV / cumV;
            double vwap2 = cumPV2 / cumV;
            double variance = Math.max(0, vwap2 - vwap * vwap);
            double sigma = Math.sqrt(variance);
            int slot = i - ctx.startBarIndex;
            double x = ctx.left + (slot + 0.5) * barW;
            if (x < ctx.left - barW || x > ctx.right + barW) continue;
            barX[i]  = x;
            vwapY[i] = DrawingRenderer.priceToY(vwap, ctx);
            up1Y[i]  = DrawingRenderer.priceToY(vwap + sigma, ctx);
            dn1Y[i]  = DrawingRenderer.priceToY(vwap - sigma, ctx);
            up2Y[i]  = DrawingRenderer.priceToY(vwap + 2*sigma, ctx);
            dn2Y[i]  = DrawingRenderer.priceToY(vwap - 2*sigma, ctx);
            valid[i] = true;
        }
        // Helper: draw a line series
        java.util.function.BiConsumer<double[], Color> drawSeries = (yArr, lc) -> {
            gc.setStroke(lc); gc.beginPath();
            boolean started = false;
            for (int i = anchorIdx; i < barCount; i++) {
                if (!valid[i]) { started = false; continue; }
                if (!started) { gc.moveTo(barX[i], yArr[i]); started = true; }
                else gc.lineTo(barX[i], yArr[i]);
            }
            gc.stroke();
        };
        // ±2σ fill between bands
        // (simplified: just draw band lines, no polygon fill for performance)
        gc.setLineWidth(0.8);
        drawSeries.accept(up2Y, bandUp2);
        drawSeries.accept(dn2Y, bandDn2);
        gc.setLineWidth(1.2);
        drawSeries.accept(up1Y, bandUp1);
        drawSeries.accept(dn1Y, bandDn1);
        // Main VWAP line
        gc.setLineWidth(props.getLineWidth());
        drawSeries.accept(vwapY, color);

        // Anchor dot and label
        double anchorX = anchorIdx >= ctx.startBarIndex
                ? ctx.left + (anchorIdx - ctx.startBarIndex + 0.5) * barW : ctx.left;
        gc.setFill(color); gc.fillOval(anchorX - 5, ctx.priceTop + 4, 10, 10);
        gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("VWAP", anchorX + 7, ctx.priceTop + 14);
        gc.setFill(bandUp1); gc.fillText("+1σ", anchorX + 7, ctx.priceTop + 26);
        gc.setFill(bandDn1); gc.fillText("-1σ",  anchorX + 7, ctx.priceTop + 38);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /**
     * Fixed Range Volume Profile — color-coded histogram (green = bullish bars, red = bearish).
     *
     * <p>The profile is drawn on the RIGHT side of the selected range as a horizontal
     * histogram.  Each price bucket is colored green if the average bar in that bucket
     * was bullish (close > open) and red if bearish.  The POC (Point of Control — the
     * highest volume bucket) is highlighted with a thicker yellow outline.
     */
    private static void drawFixedRangeVolumeProfile(GraphicsContext gc, ChartDrawing d,
                                                     RenderContext ctx, Color color,
                                                     ChartDrawingProperties props) {
        if (d.getPoints().size() < 2 || ctx.bars == null) return;
        int startIdx = DrawingCoordinateMapper.timeToBarIndex(ctx.bars, d.getPoints().get(0).getTime());
        int endIdx   = DrawingCoordinateMapper.timeToBarIndex(ctx.bars, d.getPoints().get(1).getTime());
        if (startIdx > endIdx) { int t = startIdx; startIdx = endIdx; endIdx = t; }
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double xL = Math.min(x1, x2), xR = Math.max(x1, x2);

        // Range border
        gc.setStroke(color.deriveColor(0, 1, 1, 0.4));
        gc.setLineWidth(1.0); gc.setLineDashes(4, 4);
        gc.strokeRect(xL, ctx.priceTop, xR - xL, ctx.priceBottom - ctx.priceTop);
        gc.setLineDashes();

        int numBuckets = 24;
        double minP = Double.MAX_VALUE, maxP = -Double.MAX_VALUE;
        for (int i = startIdx; i <= endIdx && i < ctx.bars.size(); i++) {
            OhlcvBar b = ctx.bars.get(i);
            minP = Math.min(minP, b.getLow().doubleValue());
            maxP = Math.max(maxP, b.getHigh().doubleValue());
        }
        if (minP >= maxP) return;

        double[] bucketVol   = new double[numBuckets];
        double[] bucketBullV = new double[numBuckets];  // bullish volume
        for (int i = startIdx; i <= endIdx && i < ctx.bars.size(); i++) {
            OhlcvBar b = ctx.bars.get(i);
            boolean bull = b.getClose().compareTo(b.getOpen()) >= 0;
            // Distribute bar's volume into price buckets it spans
            double lo = b.getLow().doubleValue(), hi = b.getHigh().doubleValue();
            double barVol = b.getVolume().doubleValue();
            int bucLo = (int) Math.max(0, Math.min(numBuckets-1,
                    (lo - minP) / (maxP - minP) * numBuckets));
            int bucHi = (int) Math.max(0, Math.min(numBuckets-1,
                    (hi - minP) / (maxP - minP) * numBuckets));
            int span  = Math.max(1, bucHi - bucLo + 1);
            for (int b2 = bucLo; b2 <= bucHi; b2++) {
                bucketVol[b2]   += barVol / span;
                if (bull) bucketBullV[b2] += barVol / span;
            }
        }
        double maxVol = 0;
        int pocBucket = 0;
        for (int i = 0; i < numBuckets; i++) {
            if (bucketVol[i] > maxVol) { maxVol = bucketVol[i]; pocBucket = i; }
        }
        if (maxVol == 0) return;

        // Profile bars drawn to the right of xR
        double barHeight  = (ctx.priceBottom - ctx.priceTop) / numBuckets;
        double maxBarWidth = Math.min(80, (xR - xL) * 0.6);  // cap profile width

        for (int i = 0; i < numBuckets; i++) {
            if (bucketVol[i] == 0) continue;
            double bWidth = bucketVol[i] / maxVol * maxBarWidth;
            double bY = ctx.priceBottom - (i + 1) * barHeight;
            double bullFraction = bucketVol[i] > 0 ? bucketBullV[i] / bucketVol[i] : 0.5;

            // Green portion (bull)
            double greenW = bWidth * bullFraction;
            gc.setFill(Color.web("#3fb95066"));
            gc.fillRect(xR, bY, greenW, barHeight - 1);
            // Red portion (bear)
            if (bWidth > greenW) {
                gc.setFill(Color.web("#f8514966"));
                gc.fillRect(xR + greenW, bY, bWidth - greenW, barHeight - 1);
            }
            // POC highlight
            if (i == pocBucket) {
                gc.setStroke(Color.web("#d29922")); gc.setLineWidth(1.5);
                gc.strokeRect(xR, bY, bWidth, barHeight - 1);
                gc.setFill(Color.web("#d29922")); gc.setFont(FONT_SMALL);
                gc.setTextAlign(TextAlignment.LEFT);
                gc.fillText("POC", xR + bWidth + 2, bY + barHeight/2 + 4);
            }
        }
        gc.setFill(color); gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("Vol Profile", xL + 2, ctx.priceTop + 12);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /**
     * Anchored Volume Profile — same as Fixed Range but anchored from a single pivot
     * point to the right edge of the visible chart.
     */
    private static void drawAnchoredVolumeProfile(GraphicsContext gc, ChartDrawing d,
                                                   RenderContext ctx, Color color,
                                                   ChartDrawingProperties props) {
        if (d.getPoints().size() < 2 || ctx.bars == null) return;
        // Build a synthetic 2-point drawing using the anchor and the last visible bar
        ChartDrawing synth = new ChartDrawing();
        // second point: last bar in context
        OhlcvBar lastBar = ctx.bars.isEmpty() ? null : ctx.bars.get(ctx.bars.size() - 1);
        long endEpoch = lastBar != null && lastBar.getOpenTime() != null
                ? lastBar.getOpenTime().toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
                : System.currentTimeMillis();
        double endPrice = lastBar != null ? lastBar.getClose().doubleValue()
                : d.getPoints().get(0).getPrice();
        ChartPoint endPt = ChartPoint.ofEpoch(endEpoch, endPrice);
        synth.setPoints(List.of(d.getPoints().get(0), endPt));
        synth.setToolType(ChartDrawingToolType.FIXED_RANGE_VOLUME_PROFILE);
        synth.setProperties(d.getProperties());
        drawFixedRangeVolumeProfile(gc, synth, ctx, color, props);
        // Anchor dot
        double anchorX = timeToX(d.getPoints().get(0).getTime(), ctx);
        gc.setFill(color); gc.fillOval(anchorX - 5, ctx.priceTop + 4, 10, 10);
        gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("⊕ AVP", anchorX + 7, ctx.priceTop + 14);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Measurers
    // ─────────────────────────────────────────────────────────────────────────

    private static void drawPriceRange(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                       Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y1 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y2 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        double midX = (x1 + x2) / 2;
        gc.setStroke(color); gc.setLineWidth(props.getLineWidth());
        // Horizontal lines at each price
        gc.strokeLine(x1, y1, x2, y1);
        gc.strokeLine(x1, y2, x2, y2);
        // Vertical arrow
        gc.setLineDashes(3, 3);
        gc.strokeLine(midX, y1, midX, y2);
        gc.setLineDashes();
        // Arrowheads
        double arrowSize = 6;
        gc.strokeLine(midX, y1, midX - arrowSize / 2, y1 + arrowSize);
        gc.strokeLine(midX, y1, midX + arrowSize / 2, y1 + arrowSize);
        gc.strokeLine(midX, y2, midX - arrowSize / 2, y2 - arrowSize);
        gc.strokeLine(midX, y2, midX + arrowSize / 2, y2 - arrowSize);
        // Label
        double priceDiff = d.getPoints().get(1).getPrice() - d.getPoints().get(0).getPrice();
        double pct = d.getPoints().get(0).getPrice() != 0
                ? priceDiff / d.getPoints().get(0).getPrice() * 100.0 : 0;
        gc.setFill(color); gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(String.format("%s  %+.2f%%", formatPrice(Math.abs(priceDiff)), pct),
                midX, (y1 + y2) / 2 - 4);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private static void drawDateRange(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                      Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double midY = (ctx.priceTop + ctx.priceBottom) / 2;
        gc.setStroke(color); gc.setLineWidth(props.getLineWidth());
        // Vertical lines at each time
        gc.setLineDashes(4, 4);
        gc.strokeLine(x1, ctx.priceTop, x1, ctx.priceBottom);
        gc.strokeLine(x2, ctx.priceTop, x2, ctx.priceBottom);
        gc.setLineDashes();
        // Horizontal arrow
        gc.strokeLine(x1, midY, x2, midY);
        double arrowSize = 6;
        gc.strokeLine(x1, midY, x1 + arrowSize, midY - arrowSize / 2);
        gc.strokeLine(x1, midY, x1 + arrowSize, midY + arrowSize / 2);
        gc.strokeLine(x2, midY, x2 - arrowSize, midY - arrowSize / 2);
        gc.strokeLine(x2, midY, x2 - arrowSize, midY + arrowSize / 2);
        // Bar count label
        int bars = Math.abs(DrawingCoordinateMapper.timeToBarIndex(ctx.bars, d.getPoints().get(1).getTime())
                - DrawingCoordinateMapper.timeToBarIndex(ctx.bars, d.getPoints().get(0).getTime()));
        gc.setFill(color); gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(bars + " bars", (x1 + x2) / 2, midY - 5);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    private static void drawDateAndPriceRange(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                              Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        drawPriceRange(gc, d, ctx, color, props);
        drawDateRange(gc, d, ctx, color.deriveColor(0, 1, 1, 0.7), props);
        // Box outline
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y1 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y2 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        double xL = Math.min(x1, x2), yT = Math.min(y1, y2);
        gc.setFill(Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0.07));
        gc.fillRect(xL, yT, Math.abs(x2 - x1), Math.abs(y2 - y1));
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Cycles
    // ─────────────────────────────────────────────────────────────────────────

    private static void drawCyclicLines(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                        Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        int startIdx = DrawingCoordinateMapper.timeToBarIndex(ctx.bars, d.getPoints().get(0).getTime());
        int endIdx   = DrawingCoordinateMapper.timeToBarIndex(ctx.bars, d.getPoints().get(1).getTime());
        int period   = Math.abs(endIdx - startIdx);
        if (period == 0) return;
        double barW = ctx.plotWidth() / Math.max(1, ctx.visibleBars);
        gc.setStroke(color.deriveColor(0, 1, 1, 0.7));
        gc.setLineWidth(props.getLineWidth());
        gc.setLineDashes(4, 4);
        for (int i = startIdx; i < ctx.bars.size(); i += period) {
            double x = ctx.left + (i - ctx.startBarIndex + 0.5) * barW;
            if (x < ctx.left || x > ctx.right) continue;
            gc.strokeLine(x, ctx.priceTop, x, ctx.priceBottom);
        }
        gc.setLineDashes();
        // Draw anchor point
        double ancX = ctx.left + (startIdx - ctx.startBarIndex + 0.5) * barW;
        gc.setFill(color); gc.fillOval(ancX - 3, ctx.priceBottom - 8, 6, 6);
        gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(period + " bars", ancX + 4, ctx.priceBottom - 2);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setLineWidth(props.getLineWidth());
    }

    private static void drawTimeCycles(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                       Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        int p1 = DrawingCoordinateMapper.timeToBarIndex(ctx.bars, d.getPoints().get(0).getTime());
        int p2 = DrawingCoordinateMapper.timeToBarIndex(ctx.bars, d.getPoints().get(1).getTime());
        int basePeriod = Math.abs(p2 - p1);
        if (basePeriod == 0) return;
        double barW = ctx.plotWidth() / Math.max(1, ctx.visibleBars);
        int anchor  = Math.min(p1, p2);
        // Project several cycle repetitions forward
        int[] multiples = {1, 2, 3, 4, 5, 6, 8};
        gc.setFont(FONT_SMALL);
        for (int mult : multiples) {
            int idx = anchor + basePeriod * mult;
            if (idx >= ctx.bars.size()) break;
            double x = ctx.left + (idx - ctx.startBarIndex + 0.5) * barW;
            if (x < ctx.left || x > ctx.right) continue;
            double alpha = Math.max(0.2, 0.8 - mult * 0.1);
            gc.setStroke(color.deriveColor(0, 1, 1, alpha));
            gc.setLineWidth(mult == 1 ? props.getLineWidth() + 0.5 : props.getLineWidth());
            gc.setLineDashes(mult % 2 == 0 ? new double[]{3, 5} : null);
            gc.strokeLine(x, ctx.priceTop, x, ctx.priceBottom);
            gc.setLineDashes();
            gc.setFill(color.deriveColor(0, 1, 1, alpha));
            gc.setTextAlign(TextAlignment.CENTER);
            gc.fillText(mult + "×", x, ctx.priceTop + 12);
        }
        gc.setTextAlign(TextAlignment.LEFT);
        gc.setLineWidth(props.getLineWidth());
    }

    private static void drawSineLine(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                     Color color, ChartDrawingProperties props) {
        if (d.getPoints().size() < 2) return;
        double x1 = timeToX(d.getPoints().get(0).getTime(), ctx);
        double y1 = priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x2 = timeToX(d.getPoints().get(1).getTime(), ctx);
        double y2 = priceToY(d.getPoints().get(1).getPrice(), ctx);
        double amplitude = Math.abs(y2 - y1) / 2.0;
        double centreY   = (y1 + y2) / 2.0;
        double width     = x2 - x1;
        if (Math.abs(width) < 1e-6) return;
        gc.setStroke(color); gc.setLineWidth(props.getLineWidth());
        gc.beginPath();
        int steps = 200;
        boolean started = false;
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double x = x1 + t * (width * 3);  // project 3 full cycles to the right
            double y = centreY - amplitude * Math.sin(2 * Math.PI * t);
            if (x < ctx.left || x > ctx.right) { started = false; continue; }
            if (!started) { gc.moveTo(x, y); started = true; }
            else gc.lineTo(x, y);
        }
        gc.stroke();
        gc.setFill(color); gc.fillOval(x1 - 3, y1 - 3, 6, 6); gc.fillOval(x2 - 3, y2 - 3, 6, 6);
        gc.setFont(FONT_SMALL); gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("Sine", x1 + 4, centreY - amplitude - 4);
        gc.setTextAlign(TextAlignment.LEFT);
    }

    /**
     * Wraps {@code text} into lines of at most {@code maxChars} characters.
     * Honours existing newlines in the text. Words that exceed maxChars are
     * split hard.
     *
     * @param text      the raw text to wrap (may contain {@code \n})
     * @param maxChars  maximum characters per line
     * @return ordered list of display lines
     */
    static java.util.List<String> wrapText(String text, int maxChars) {
        if (text == null || text.isBlank()) return java.util.List.of("");
        maxChars = Math.max(1, maxChars);
        java.util.List<String> result = new java.util.ArrayList<>();
        // Split on explicit newlines first
        for (String para : text.split("\n", -1)) {
            if (para.isEmpty()) { result.add(""); continue; }
            String[] words = para.split(" ", -1);
            StringBuilder cur = new StringBuilder();
            for (String word : words) {
                // Hard-split words longer than maxChars
                while (word.length() > maxChars) {
                    int rem = maxChars - cur.length();
                    if (cur.length() > 0 && rem > 0) {
                        cur.append(word, 0, rem);
                        result.add(cur.toString()); cur.setLength(0);
                        word = word.substring(rem);
                    } else if (cur.length() == 0) {
                        result.add(word.substring(0, maxChars));
                        word = word.substring(maxChars);
                    } else {
                        result.add(cur.toString()); cur.setLength(0);
                    }
                }
                if (word.isEmpty()) continue;
                if (cur.length() == 0) {
                    cur.append(word);
                } else if (cur.length() + 1 + word.length() <= maxChars) {
                    cur.append(' ').append(word);
                } else {
                    result.add(cur.toString());
                    cur.setLength(0);
                    cur.append(word);
                }
            }
            if (cur.length() > 0) result.add(cur.toString());
        }
        return result;
    }
}
