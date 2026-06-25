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
            case TREND_LINE        -> drawTwoPointLine(gc, d, ctx, color, false, false, props);
            case RAY               -> drawTwoPointLine(gc, d, ctx, color, false, true, props);
            case EXTENDED_LINE     -> drawTwoPointLine(gc, d, ctx, color, true, true, props);
            case HORIZONTAL_LINE, PROFIT_TARGET_LINE, STOP_LOSS_LINE ->
                                      drawHorizontalLine(gc, d, ctx, color, props);
            case VERTICAL_LINE     -> drawVerticalLine(gc, d, ctx, color, props);
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
            default                -> drawTwoPointLine(gc, d, ctx, color, false, false, props);
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

        String[] levelColors = {"#e6edf3","#d29922","#22c55e","#3b82f6","#ef4444","#a855f7","#e6edf3"};
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
            gc.setFill(lColor); gc.setTextAlign(TextAlignment.LEFT);
            gc.fillText(String.format("%.1f%%  %s", level * 100, formatPrice(price)), Math.min(x1, x2) + 2, y - 3);
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(formatPrice(price), ctx.right - 2, y - 3);
        }
        gc.setTextAlign(TextAlignment.LEFT);
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
        gc.setFill(base);
        gc.fillOval(x1 - 3, priceToY(p0, ctx) - 3, 6, 6);
        gc.fillOval(x2 - 3, priceToY(p1, ctx) - 3, 6, 6);
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
        for (int i = 0; i < FIB_EXT_LEVELS.length; i++) {
            double mult = FIB_EXT_LEVELS[i];
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
        if (sl != null) drawPriceLine(gc, left, right, priceToY(sl, ctx), "#f85149", "SL " + formatPrice(sl));
        if (tp != null) drawPriceLine(gc, left, right, priceToY(tp, ctx), "#388bfd", "TP " + formatPrice(tp));
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
        // Use stored size if available, otherwise auto-size
        double boxW = props.getTextBoxWidth() > 0
                ? props.getTextBoxWidth()
                : Math.max(60, text.length() * (fontSize * 0.65) + 16);
        double boxH = props.getTextBoxHeight() > 0
                ? props.getTextBoxHeight()
                : fontSize + 12;
        gc.setFill(Color.web("#1c2128ee")); gc.fillRoundRect(x, y - boxH + 4, boxW, boxH, 4, 4);
        gc.setStroke(color); gc.setLineWidth(1.2);
        gc.strokeRoundRect(x, y - boxH + 4, boxW, boxH, 4, 4);
        gc.setFill(Color.web("#e6edf3")); gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(text, x + 8, y);
        if (props.isEditing()) {
            double cursorX = x + 8 + text.length() * (fontSize * 0.6);
            gc.setStroke(Color.web("#e6edf3")); gc.setLineWidth(1.5);
            gc.strokeLine(cursorX, y - boxH + 6, cursorX, y - 2);
        }
    }

    private static void drawNoteIcon(GraphicsContext gc, ChartDrawing d, RenderContext ctx,
                                     ChartDrawingProperties props) {
        if (d.getPoints().isEmpty()) return;
        ChartPoint pt = d.getPoints().getFirst();
        double x = timeToX(pt.getTime(), ctx), y = priceToY(pt.getPrice(), ctx);
        String text = props.getText() != null ? props.getText() : "";
        Color color = safeColor(props.getColor(), "#d29922");
        double noteW = props.getTextBoxWidth()  > 0 ? props.getTextBoxWidth()  : 80;
        double noteH = props.getTextBoxHeight() > 0 ? props.getTextBoxHeight() : 60;
        gc.setFill(Color.web("#2d2a00ee")); gc.fillRoundRect(x, y, noteW, noteH, 4, 4);
        gc.setStroke(color); gc.setLineWidth(1.2); gc.strokeRoundRect(x, y, noteW, noteH, 4, 4);
        double foldSize = 10;
        gc.setFill(color.deriveColor(0, 1, 0.6, 0.8));
        gc.fillPolygon(new double[]{x + noteW - foldSize, x + noteW, x + noteW},
                       new double[]{y, y, y + foldSize}, 3);
        gc.setFill(Color.web("#e6edf3cc")); gc.setFont(Font.font("Segoe UI", 10)); gc.setTextAlign(TextAlignment.LEFT);
        if (!text.isBlank()) {
            String preview = text.length() > 24 ? text.substring(0, 24) + "…" : text;
            if (preview.length() > 12) {
                gc.fillText(preview.substring(0, Math.min(12, preview.length())), x + 5, y + 16);
                gc.fillText(preview.substring(Math.min(12, preview.length())), x + 5, y + 28);
            } else { gc.fillText(preview, x + 5, y + 16); }
        } else { gc.fillText("✏ Note", x + 8, y + 22); }
        if (props.isEditing()) { gc.setStroke(color); gc.strokeRoundRect(x + 1, y + 1, noteW - 2, noteH - 2, 4, 4); }
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
        double boxW = props.getTextBoxWidth() > 0
                ? props.getTextBoxWidth()
                : Math.max(70, text.length() * (fontSize * 0.62) + 16);
        double boxH = props.getTextBoxHeight() > 0
                ? props.getTextBoxHeight()
                : fontSize + 12;
        double boxCX = boxX + boxW / 2, boxBY = boxY + boxH;
        gc.setStroke(color); gc.setLineWidth(1.5);
        gc.strokeLine(tipX, tipY, boxCX, boxBY);
        double angle = Math.atan2(tipY - boxBY, tipX - boxCX);
        gc.strokeLine(tipX, tipY, tipX - 8 * Math.cos(angle - Math.PI / 6), tipY - 8 * Math.sin(angle - Math.PI / 6));
        gc.strokeLine(tipX, tipY, tipX - 8 * Math.cos(angle + Math.PI / 6), tipY - 8 * Math.sin(angle + Math.PI / 6));
        gc.setFill(color); gc.fillOval(tipX - 3, tipY - 3, 6, 6);
        gc.setFill(Color.web("#1c2128ee")); gc.fillRoundRect(boxX, boxY, boxW, boxH, 4, 4);
        gc.setStroke(color); gc.setLineWidth(1.2); gc.strokeRoundRect(boxX, boxY, boxW, boxH, 4, 4);
        gc.setFill(Color.web("#e6edf3")); gc.setFont(Font.font("Segoe UI", fontSize)); gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(text, boxX + 8, boxY + fontSize + 1);
        if (props.isEditing()) {
            double cursorX = boxX + 8 + text.length() * (fontSize * 0.6);
            gc.setStroke(Color.web("#e6edf3")); gc.setLineWidth(1.5);
            gc.strokeLine(cursorX, boxY + 3, cursorX, boxY + boxH - 3);
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
}
