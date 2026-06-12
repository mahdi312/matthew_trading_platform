package com.mst.matt.tradingplatformapp.ui.chart;

import com.mst.matt.tradingplatformapp.model.IndicatorDefinition;
import com.mst.matt.tradingplatformapp.model.IndicatorDefinition.DisplayPane;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.service.analysis.SupportResistanceService.SRLevel;
import com.mst.matt.tradingplatformapp.service.analysis.SupportResistanceService.SRResult;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Professional candlestick chart rendered on a JavaFX Canvas.
 *
 * Key improvements over previous version:
 *  ✅ Indicator system based on {@link IndicatorDefinition} — unlimited indicators,
 *     each with its own color, line weight, and settings.
 *  ✅ Correct zoom/pan: series index tracking uses absolute bar indices referenced
 *     against the full loaded series, never against visible-only sub-lists.
 *  ✅ Sub-pane indicators (MACD, RSI, etc.) dynamically expand/collapse.
 *  ✅ Multi-line indicators (Bollinger, Keltner, Ichimoku) draw fills + borders.
 *  ✅ Parabolic SAR drawn as dots.
 *  ✅ Volume toggleable.
 *  ✅ Drag to pan, scroll to zoom (anchor under mouse).
 */
public class CandlestickChartCanvas extends Canvas {

    // ── Layout constants ──────────────────────────────────────
    private static final double PADDING_LEFT   = 60;
    private static final double PADDING_RIGHT  = 82;
    private static final double PADDING_TOP    = 20;
    private static final double PADDING_BOTTOM = 26;
    private static final double VOL_FRAC       = 0.14;  // fraction of full height for volume
    private static final double SUB_FRAC       = 0.15;  // fraction per sub-pane indicator

    // ── Colors ────────────────────────────────────────────────
    private static final Color BG         = Color.web("#0d1117");
    private static final Color GRID       = Color.web("#21262d");
    private static final Color BORDER     = Color.web("#30363d");
    private static final Color TEXT_DIM   = Color.web("#8b949e");
    private static final Color TEXT_MAIN  = Color.web("#e6edf3");
    private static final Color BULL_BODY  = Color.web("#3fb950");
    private static final Color BEAR_BODY  = Color.web("#f85149");
    private static final Color BULL_WICK  = Color.web("#3fb95099");
    private static final Color BEAR_WICK  = Color.web("#f8514999");
    private static final Color VOL_BULL   = Color.web("#3fb95066");
    private static final Color VOL_BEAR   = Color.web("#f8514966");
    private static final Color CROSS      = Color.web("#e6edf355");
    private static final Color SR_SUP     = Color.web("#3fb95099");
    private static final Color SR_RES     = Color.web("#f8514999");
    private static final Color ICH_BULL   = Color.web("#3fb95030");
    private static final Color ICH_BEAR   = Color.web("#f8514930");

    private static final Font FONT_SMALL  = Font.font("Segoe UI", 11);
    private static final Font FONT_MEDIUM = Font.font("Segoe UI", 12);

    // ── Timezone (user's local zone, applied to all time labels) ─────────
    /** User's local timezone — updated via {@link #setUserTimezone(ZoneId)}. */
    private ZoneId userTimezone = ZoneId.systemDefault();

    /** Pattern for time-axis labels (short timeframes show time, daily+ show date). */
    private DateTimeFormatter axisFormatter  = buildAxisFormatter(userTimezone);
    private DateTimeFormatter dtfDate        = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(userTimezone);
    private DateTimeFormatter tooltipFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(userTimezone);

    private static DateTimeFormatter buildAxisFormatter(ZoneId zone) {
        return DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(zone);
    }

    // ── Data ──────────────────────────────────────────────────
    private List<OhlcvBar>              bars;
    private List<IndicatorDefinition>   indicators  = new ArrayList<>();
    private SRResult                    srResult;
    private double                      lastPrice   = Double.NaN;
    /** Current timeframe string (e.g. "1m", "4h", "1d") for smart label formatting. */
    private String                      currentTimeframe = "1h";

    // ── View state — all relative to full bars list ───────────
    /**
     * Index (into {@link #bars}) of the first visible bar.
     * INVARIANT: 0 ≤ startBarIndex ≤ bars.size() - visibleBars
     */
    private int    startBarIndex = 0;

    /**
     * Number of bars visible in the chart at the current zoom level.
     * INVARIANT: 1 ≤ visibleBars ≤ bars.size()
     */
    private int    visibleBars   = 100;

    // ── Overlays ──────────────────────────────────────────────
    private boolean showVolume    = true;
    private boolean analysisMode  = false;

    // ── Mouse / drag ─────────────────────────────────────────
    private double  mouseX, mouseY;
    private boolean showCrosshair  = false;
    private double  dragStartX;
    private int     dragStartBar;

    // ── Constructors ──────────────────────────────────────────

    public CandlestickChartCanvas() {
        super(800, 600);
        init();
    }

    public CandlestickChartCanvas(double w, double h) {
        super(w, h);
        init();
    }

    private void init() {
        setFocusTraversable(true);
        setupMouseHandlers();
        widthProperty().addListener((o, a, b) -> render());
        heightProperty().addListener((o, a, b) -> render());
    }

    // ── Public API ────────────────────────────────────────────

    /** Sets chart data and resets view. */
    public void setData(List<OhlcvBar> bars, List<IndicatorDefinition> indicators, SRResult srResult) {
        this.bars       = bars;
        this.indicators = indicators != null ? indicators : new ArrayList<>();
        this.srResult   = srResult;

        if (bars != null && !bars.isEmpty()) {
            visibleBars   = Math.min(100, bars.size());
            startBarIndex = Math.max(0, bars.size() - visibleBars);
        }
        render();
    }

    public void setLastPrice(double price)     { this.lastPrice = price; render(); }
    public double getLastPrice()               { return lastPrice; }
    public void setShowVolume(boolean v)       { showVolume = v;    render(); }
    public void setAnalysisMode(boolean v)     { analysisMode = v;  render(); }
    public List<IndicatorDefinition> getIndicators() { return indicators; }
    public void setIndicators(List<IndicatorDefinition> ind) {
        this.indicators = ind != null ? ind : new ArrayList<>();
        render();
    }

    /**
     * Set the user's local timezone so all time labels (axis, tooltip, crosshair)
     * display times in the user's local time instead of UTC.
     */
    public void setUserTimezone(ZoneId zone) {
        if (zone == null) zone = ZoneId.systemDefault();
        this.userTimezone = zone;
        this.axisFormatter    = buildAxisFormatter(zone);
        this.dtfDate          = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zone);
        this.tooltipFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(zone);
        render();
    }

    /** Set the current timeframe string to pick an appropriate date/time format. */
    public void setCurrentTimeframe(String tf) {
        this.currentTimeframe = tf != null ? tf : "1h";
        // For daily/weekly/monthly bars, show only date on the axis
        boolean isDate = tf != null && (tf.equals("1d") || tf.equals("3d")
                || tf.equals("1w") || tf.equals("1mo"));
        this.axisFormatter = isDate
                ? DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(userTimezone)
                : DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(userTimezone);
    }

    // ── Main Render ───────────────────────────────────────────

    public void render() {
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();

        if (w <= 0 || h <= 0 || bars == null || bars.isEmpty()) {
            drawEmpty(gc, w, h);
            return;
        }

        // ── Clamp view window ──────────────────────────────────
        visibleBars   = Math.max(1, Math.min(visibleBars, bars.size()));
        startBarIndex = Math.max(0, Math.min(startBarIndex, bars.size() - visibleBars));
        int endBarIndex = startBarIndex + visibleBars - 1;  // inclusive, absolute index

        // Visible sub-list — used ONLY for drawing candles and volume
        List<OhlcvBar> visible = bars.subList(startBarIndex, endBarIndex + 1);

        // ── Layout ────────────────────────────────────────────
        // Count sub-pane indicators
        long subCount = subPaneCount();
        double totalH = h - PADDING_TOP - PADDING_BOTTOM;
        double subTotal  = Math.min(0.45, subCount * SUB_FRAC);
        double volFrac   = showVolume ? VOL_FRAC : 0;
        double priceFrac = 1.0 - subTotal - volFrac;

        double priceH = totalH * priceFrac;
        double volH   = totalH * volFrac;

        // Assign height to each sub-pane indicator
        double subPaneH = subCount > 0 ? (totalH * subTotal / subCount) : 0;

        ChartLayout layout = new ChartLayout(
                PADDING_LEFT, PADDING_TOP, w - PADDING_RIGHT, h - PADDING_BOTTOM,
                priceH, volH, subPaneH, subCount);

        // ── Price range ───────────────────────────────────────
        double maxPrice = visible.stream().mapToDouble(b -> b.getHigh().doubleValue()).max().orElse(1);
        double minPrice = visible.stream().mapToDouble(b -> b.getLow().doubleValue()).min().orElse(0);

        // Extend price range for price-pane indicator series
        for (IndicatorDefinition def : indicators) {
            if (!def.isVisible() || def.getPane() != DisplayPane.PRICE) continue;
            extendPriceRange(def, startBarIndex, endBarIndex);
        }
        // Extend for the indicator's own range
        for (IndicatorDefinition def : indicators) {
            if (!def.isVisible() || def.getPane() != DisplayPane.PRICE) continue;
            maxPrice = Math.max(maxPrice, visibleMax(def.getSeries(), startBarIndex, endBarIndex));
            minPrice = Math.min(minPrice, visibleMin(def.getSeries(), startBarIndex, endBarIndex));
            // Also check extra series (e.g. Bollinger upper)
            for (List<Double> extra : def.getExtraSeries().values()) {
                maxPrice = Math.max(maxPrice, visibleMax(extra, startBarIndex, endBarIndex));
                minPrice = Math.min(minPrice, visibleMin(extra, startBarIndex, endBarIndex));
            }
        }
        double pad = (maxPrice - minPrice) * 0.05;
        maxPrice += pad;
        minPrice -= pad;
        if (maxPrice == minPrice) { maxPrice += 1; minPrice -= 1; }

        double maxVol = visible.stream().mapToDouble(b -> b.getVolume().doubleValue()).max().orElse(1);

        // ── Draw ──────────────────────────────────────────────

        gc.setFill(BG);
        gc.fillRect(0, 0, w, h);

        drawGrid(gc, layout, maxPrice, minPrice, visibleBars);

        // Price-pane indicators (fills first, then lines)
        drawPricePaneIndicatorFills(gc, layout, maxPrice, minPrice, startBarIndex, endBarIndex, visibleBars);
        drawPricePaneIndicatorLines(gc, layout, maxPrice, minPrice, startBarIndex, endBarIndex, visibleBars);

        // Support / Resistance
        if (srResult != null) {
            IndicatorDefinition srDef = indicators.stream()
                    .filter(d -> d.getType() == IndicatorDefinition.Type.SUPPORT_RESISTANCE && d.isVisible())
                    .findFirst().orElse(null);
            if (srDef != null) drawSupportResistance(gc, layout, maxPrice, minPrice);
        }

        // Candles
        drawCandles(gc, layout, visible, maxPrice, minPrice);

        // Last price line
        drawLastPriceLine(gc, layout, maxPrice, minPrice, visible);

        // Volume
        if (showVolume) drawVolume(gc, layout, visible, maxVol);

        // Sub-pane indicators
        drawSubPaneIndicators(gc, layout, startBarIndex, endBarIndex, visibleBars);

        // Axes + separators
        drawPriceAxis(gc, layout, maxPrice, minPrice);
        drawTimeAxis(gc, layout, visible, w);
        drawSeparators(gc, layout, w);

        // Legend
        drawLegend(gc, layout);

        // Crosshair
        if (showCrosshair) drawCrosshair(gc, layout, visible, maxPrice, minPrice);
    }

    // ── Count sub-pane indicators ─────────────────────────────

    private long subPaneCount() {
        return indicators.stream()
                .filter(d -> d.isVisible() && d.getPane() == DisplayPane.SUB)
                .count();
    }

    // ── Draw: Grid ────────────────────────────────────────────

    private void drawGrid(GraphicsContext gc, ChartLayout l,
                          double maxP, double minP, int barCount) {
        gc.setStroke(GRID);
        gc.setLineWidth(0.5);
        int gridLines = 6;
        for (int i = 0; i <= gridLines; i++) {
            double y = l.priceTop() + l.priceH * i / gridLines;
            gc.strokeLine(l.left, y, l.right, y);
        }
        int step = Math.max(1, barCount / 8);
        double barW = l.plotWidth() / barCount;
        for (int i = 0; i < barCount; i += step) {
            double x = l.left + (i + 0.5) * barW;
            gc.strokeLine(x, l.priceTop(), x, l.priceBottom());
        }
    }

    // ── Draw: Candles ─────────────────────────────────────────

    private void drawCandles(GraphicsContext gc, ChartLayout l,
                             List<OhlcvBar> visible, double maxP, double minP) {
        int n    = visible.size();
        double barW  = l.plotWidth() / n;
        double bodyW = Math.max(1.5, barW * 0.65);
        double wickW = Math.max(1.0, bodyW * 0.25);

        for (int i = 0; i < n; i++) {
            OhlcvBar bar = visible.get(i);
            double o  = bar.getOpen().doubleValue();
            double hi = bar.getHigh().doubleValue();
            double lo = bar.getLow().doubleValue();
            double c  = bar.getClose().doubleValue();
            boolean bull = c >= o;

            double cx       = l.left + (i + 0.5) * barW;
            double topWick  = priceToY(hi, maxP, minP, l);
            double botWick  = priceToY(lo, maxP, minP, l);
            double bodyTop  = priceToY(Math.max(o, c), maxP, minP, l);
            double bodyBot  = priceToY(Math.min(o, c), maxP, minP, l);
            double bodyH    = Math.max(1.5, bodyBot - bodyTop);

            gc.setStroke(bull ? BULL_WICK : BEAR_WICK);
            gc.setLineWidth(wickW);
            gc.strokeLine(cx, topWick, cx, botWick);

            gc.setFill(bull ? BULL_BODY : BEAR_BODY);
            gc.fillRect(cx - bodyW / 2, bodyTop, bodyW, bodyH);
        }
    }

    /** Dotted red horizontal line at last price. */
    private void drawLastPriceLine(GraphicsContext gc, ChartLayout l,
                                   double maxP, double minP, List<OhlcvBar> visible) {
        double price = lastPrice;
        if (Double.isNaN(price) && visible != null && !visible.isEmpty()) {
            price = visible.get(visible.size() - 1).getClose().doubleValue();
        }
        if (Double.isNaN(price) || price < minP || price > maxP) return;

        double y = priceToY(price, maxP, minP, l);
        gc.setStroke(Color.web("#f85149"));
        gc.setLineWidth(1.2);
        gc.setLineDashes(6, 6);
        gc.strokeLine(l.left, y, l.right, y);
        gc.setLineDashes();

        gc.setFill(Color.web("#f85149"));
        gc.setFont(FONT_SMALL);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(fmtPrice(price), l.right + 4, y + 4);
    }

    // ── Draw: Volume ──────────────────────────────────────────

    private void drawVolume(GraphicsContext gc, ChartLayout l,
                            List<OhlcvBar> visible, double maxVol) {
        if (l.volH <= 0) return;
        int n    = visible.size();
        double barW    = l.plotWidth() / n;
        double volBarW = Math.max(1.5, barW * 0.7);
        double volTop  = l.volTop();

        for (int i = 0; i < n; i++) {
            OhlcvBar bar = visible.get(i);
            double vol  = bar.getVolume().doubleValue();
            boolean bull = bar.getClose().compareTo(bar.getOpen()) >= 0;
            double barH = (vol / maxVol) * l.volH;
            double x    = l.left + (i + 0.5) * barW - volBarW / 2;
            double y    = volTop + l.volH - barH;
            gc.setFill(bull ? VOL_BULL : VOL_BEAR);
            gc.fillRect(x, y, volBarW, barH);
        }
    }

    // ── Draw: Price-pane indicator fills ─────────────────────
    // (Must be drawn before candles so candles appear on top)

    private void drawPricePaneIndicatorFills(GraphicsContext gc, ChartLayout l,
                                             double maxP, double minP,
                                             int start, int end, int n) {
        for (IndicatorDefinition def : indicators) {
            if (!def.isVisible() || def.getPane() != DisplayPane.PRICE) continue;
            Color c = Color.web(def.getColor());

            switch (def.getType()) {
                case BOLLINGER -> drawBandFill(gc, l, def.getExtraSeries("upper"),
                        def.getExtraSeries("lower"), maxP, minP, start, n,
                        Color.color(c.getRed(), c.getGreen(), c.getBlue(), 0.08));
                case KELTNER   -> drawBandFill(gc, l, def.getExtraSeries("upper"),
                        def.getExtraSeries("lower"), maxP, minP, start, n,
                        Color.color(c.getRed(), c.getGreen(), c.getBlue(), 0.07));
                case DONCHIAN  -> drawBandFill(gc, l, def.getExtraSeries("upper"),
                        def.getExtraSeries("lower"), maxP, minP, start, n,
                        Color.color(c.getRed(), c.getGreen(), c.getBlue(), 0.07));
                case ICHIMOKU  -> drawIchimokuCloud(gc, l, def, maxP, minP, start, n);
                default        -> {} // no fill
            }
        }
    }

    private void drawBandFill(GraphicsContext gc, ChartLayout l,
                              List<Double> upper, List<Double> lower,
                              double maxP, double minP, int start, int n, Color fill) {
        if (upper == null || lower == null || upper.isEmpty() || lower.isEmpty()) return;
        double barW = l.plotWidth() / n;
        gc.setFill(fill);
        gc.beginPath();
        boolean started = false;
        for (int i = 0; i < n; i++) {
            int idx = start + i;
            if (idx >= upper.size()) break;
            double v = upper.get(idx);
            if (Double.isNaN(v)) { started = false; continue; }
            double x = l.left + (i + 0.5) * barW;
            double y = priceToY(v, maxP, minP, l);
            if (!started) { gc.moveTo(x, y); started = true; }
            else gc.lineTo(x, y);
        }
        for (int i = n - 1; i >= 0; i--) {
            int idx = start + i;
            if (idx >= lower.size()) continue;
            double v = lower.get(idx);
            if (Double.isNaN(v)) continue;
            double x = l.left + (i + 0.5) * barW;
            double y = priceToY(v, maxP, minP, l);
            gc.lineTo(x, y);
        }
        gc.closePath();
        gc.fill();
    }

    private void drawIchimokuCloud(GraphicsContext gc, ChartLayout l,
                                   IndicatorDefinition def,
                                   double maxP, double minP, int start, int n) {
        List<Double> spanA = def.getExtraSeries("spanA");
        List<Double> spanB = def.getExtraSeries("spanB");
        if (spanA == null || spanB == null) return;
        double barW = l.plotWidth() / n;
        for (int i = 0; i < n - 1; i++) {
            int idx = start + i;
            if (idx >= spanA.size() || idx >= spanB.size()) break;
            double a = spanA.get(idx), b = spanB.get(idx);
            if (Double.isNaN(a) || Double.isNaN(b)) continue;
            boolean bull = a >= b;
            double x1 = l.left + (i + 0.5) * barW;
            double x2 = l.left + (i + 1.5) * barW;
            double y1 = priceToY(a, maxP, minP, l);
            double y2 = priceToY(b, maxP, minP, l);
            gc.setFill(bull ? ICH_BULL : ICH_BEAR);
            gc.fillRect(x1, Math.min(y1, y2), x2 - x1, Math.abs(y2 - y1));
        }
    }

    // ── Draw: Price-pane indicator lines ─────────────────────

    private void drawPricePaneIndicatorLines(GraphicsContext gc, ChartLayout l,
                                             double maxP, double minP,
                                             int start, int end, int n) {
        for (IndicatorDefinition def : indicators) {
            if (!def.isVisible() || def.getPane() != DisplayPane.PRICE) continue;
            Color c   = Color.web(def.getColor());
            double lw = def.getLineWeight();

            switch (def.getType()) {
                // Single-line MA overlays
                case EMA, SMA, WMA, DEMA, TEMA, HULL_MA, KAMA, ZLEMA, VWAP ->
                        drawLineSeriesAbsolute(gc, l, def.getSeries(), start, n, maxP, minP, c, lw);

                // Bollinger: upper, middle (dashed), lower
                case BOLLINGER -> {
                    drawLineSeriesAbsolute(gc, l, def.getExtraSeries("upper"), start, n, maxP, minP, c, lw);
                    drawLineSeriesAbsolute(gc, l, def.getSeries(), start, n, maxP, minP,
                            Color.color(c.getRed(), c.getGreen(), c.getBlue(), 0.5), lw * 0.7);
                    drawLineSeriesAbsolute(gc, l, def.getExtraSeries("lower"), start, n, maxP, minP, c, lw);
                }
                // Keltner / Donchian: same as Bollinger
                case KELTNER, DONCHIAN -> {
                    drawLineSeriesAbsolute(gc, l, def.getExtraSeries("upper"), start, n, maxP, minP, c, lw);
                    drawLineSeriesAbsolute(gc, l, def.getSeries(),             start, n, maxP, minP,
                            Color.color(c.getRed(), c.getGreen(), c.getBlue(), 0.5), lw * 0.7);
                    drawLineSeriesAbsolute(gc, l, def.getExtraSeries("lower"), start, n, maxP, minP, c, lw);
                }
                // Ichimoku lines: tenkan, kijun, spanA, spanB
                case ICHIMOKU -> {
                    drawLineSeriesAbsolute(gc, l, def.getSeries(),                  start, n, maxP, minP,
                            Color.web("#f85149"), lw);          // tenkan
                    drawLineSeriesAbsolute(gc, l, def.getExtraSeries("kijun"), start, n, maxP, minP,
                            Color.web("#388bfd"), lw);          // kijun
                    drawLineSeriesAbsolute(gc, l, def.getExtraSeries("spanA"), start, n, maxP, minP,
                            Color.web("#3fb95088"), 0.8);       // span A border
                    drawLineSeriesAbsolute(gc, l, def.getExtraSeries("spanB"), start, n, maxP, minP,
                            Color.web("#f8514988"), 0.8);       // span B border
                }
                // Parabolic SAR — drawn as dots
                case PARABOLIC_SAR -> drawSarDots(gc, l, def, start, n, maxP, minP);

                // Feature 3: TWO_MA — two EMA lines with separate colors
                case TWO_MA -> {
                    Color c2 = safeWebColor(def.getColor2(), "#bc8cff");
                    drawLineSeriesAbsolute(gc, l, def.getSeries(),               start, n, maxP, minP, c,  lw);  // line1
                    drawLineSeriesAbsolute(gc, l, def.getExtraSeries("line2"),   start, n, maxP, minP, c2, lw);  // line2
                }

                // Feature 3: THREE_MA — three EMA lines with separate colors
                case THREE_MA -> {
                    Color c2 = safeWebColor(def.getColor2(), "#bc8cff");
                    Color c3 = safeWebColor(def.getColor3(), "#f0883e");
                    drawLineSeriesAbsolute(gc, l, def.getSeries(),               start, n, maxP, minP, c,  lw);  // line1 (fast)
                    drawLineSeriesAbsolute(gc, l, def.getExtraSeries("line2"),   start, n, maxP, minP, c2, lw);  // line2 (mid)
                    drawLineSeriesAbsolute(gc, l, def.getExtraSeries("line3"),   start, n, maxP, minP, c3, lw);  // line3 (slow)
                }

                default -> {} // handled elsewhere
            }
        }
    }

    /** Draws SAR as small circles at each bar position. */
    private void drawSarDots(GraphicsContext gc, ChartLayout l, IndicatorDefinition def,
                             int start, int n, double maxP, double minP) {
        List<Double> series = def.getSeries();
        if (series == null || series.isEmpty()) return;
        double barW = l.plotWidth() / n;
        double r    = Math.max(2.0, def.getLineWeight() + 1.0);
        gc.setFill(Color.web(def.getColor()));
        for (int i = 0; i < n; i++) {
            int idx = start + i;
            if (idx >= series.size()) break;
            double v = series.get(idx);
            if (Double.isNaN(v)) continue;
            double x = l.left + (i + 0.5) * barW;
            double y = priceToY(v, maxP, minP, l);
            gc.fillOval(x - r / 2, y - r / 2, r, r);
        }
    }

    // ── Draw: Support & Resistance ────────────────────────────

    private void drawSupportResistance(GraphicsContext gc, ChartLayout l,
                                       double maxP, double minP) {
        for (SRLevel sr : srResult.getSupports()) {
            double price = sr.price();
            if (price < minP || price > maxP) continue;
            double y = priceToY(price, maxP, minP, l);
            gc.setStroke(SR_SUP);
            gc.setLineWidth(1.0);
            gc.setLineDashes(6, 4);
            gc.strokeLine(l.left, y, l.right, y);
            gc.setLineDashes();
            gc.setFill(SR_SUP);
            gc.setFont(FONT_SMALL);
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(sr.label() + " " + fmtPrice(price), l.right - 2, y - 2);
        }
        for (SRLevel sr : srResult.getResistances()) {
            double price = sr.price();
            if (price < minP || price > maxP) continue;
            double y = priceToY(price, maxP, minP, l);
            gc.setStroke(SR_RES);
            gc.setLineWidth(1.0);
            gc.setLineDashes(6, 4);
            gc.strokeLine(l.left, y, l.right, y);
            gc.setLineDashes();
            gc.setFill(SR_RES);
            gc.setFont(FONT_SMALL);
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(sr.label() + " " + fmtPrice(price), l.right - 2, y - 2);
        }
    }

    // ── Draw: Sub-pane indicators ─────────────────────────────

    private void drawSubPaneIndicators(GraphicsContext gc, ChartLayout l,
                                       int start, int end, int n) {
        int paneIdx = 0;
        for (IndicatorDefinition def : indicators) {
            if (!def.isVisible() || def.getPane() != DisplayPane.SUB) continue;
            double paneTop = l.subPaneTop(paneIdx);
            double paneH   = l.subPaneH;

            // Background
            gc.setFill(Color.web("#0d1117"));
            gc.fillRect(l.left, paneTop, l.plotWidth(), paneH);

            // Draw specific sub-pane type
            drawSubPane(gc, l, def, paneTop, paneH, start, end, n);

            paneIdx++;
        }
    }

    private void drawSubPane(GraphicsContext gc, ChartLayout l,
                             IndicatorDefinition def,
                             double paneTop, double paneH,
                             int start, int end, int n) {
        Color c = Color.web(def.getColor());
        gc.setFill(TEXT_DIM);
        gc.setFont(FONT_SMALL);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(def.getLabel(), l.left + 4, paneTop + 12);

        switch (def.getType()) {
            case MACD, PPO  -> drawMacdPane(gc, l, def, paneTop, paneH, start, end, n, c);
            case RSI        -> drawRsiPane(gc, l, def, paneTop, paneH, start, end, n, c, 30, 70);
            case STOCHASTIC -> drawOscillatorPane(gc, l, def, paneTop, paneH, start, end, n, c, "d", 20, 80);
            case STOCH_RSI  -> drawOscillatorPane(gc, l, def, paneTop, paneH, start, end, n, c, "d", 20, 80);
            case WILLIAMS_R -> drawBoundedPane(gc, l, def, paneTop, paneH, start, end, n, c, -100, 0, -80, -20);
            case CCI        -> drawUnboundedOscPane(gc, l, def, paneTop, paneH, start, end, n, c);
            case ADX        -> drawAdxPane(gc, l, def, paneTop, paneH, start, end, n, c);
            case AROON      -> drawAroonPane(gc, l, def, paneTop, paneH, start, end, n, c);
            default         -> drawGenericSubPane(gc, l, def, paneTop, paneH, start, end, n, c);
        }
    }

    /** MACD with histogram and signal line. */
    private void drawMacdPane(GraphicsContext gc, ChartLayout l, IndicatorDefinition def,
                              double paneTop, double paneH,
                              int start, int end, int n, Color c) {
        List<Double> macdLine = def.getSeries();
        List<Double> sigLine  = def.getExtraSeries("signal");
        List<Double> hist     = def.getExtraSeries("histogram");
        if (macdLine == null) return;

        List<Double> allVals = new ArrayList<>();
        if (hist != null) allVals.addAll(hist.subList(start, Math.min(end + 1, hist.size())));
        else              allVals.addAll(macdLine.subList(start, Math.min(end + 1, macdLine.size())));
        double maxM = allVals.stream().filter(v -> !Double.isNaN(v)).mapToDouble(d -> d).max().orElse(1);
        double minM = allVals.stream().filter(v -> !Double.isNaN(v)).mapToDouble(d -> d).min().orElse(-1);
        if (maxM == minM) { maxM += 1; minM -= 1; }

        double barW  = l.plotWidth() / n;
        double zeroY = subY(0, maxM, minM, paneTop, paneH);

        if (hist != null) {
            for (int i = 0; i < n; i++) {
                int idx = start + i;
                if (idx >= hist.size()) break;
                double v = hist.get(idx);
                if (Double.isNaN(v)) continue;
                double x  = l.left + i * barW;
                double y  = subY(v, maxM, minM, paneTop, paneH);
                double bh = Math.abs(y - zeroY);
                gc.setFill(v >= 0 ? Color.web("#3fb95088") : Color.web("#f8514988"));
                gc.fillRect(x, Math.min(y, zeroY), barW * 0.85, Math.max(1, bh));
            }
        }

        drawSubLine(gc, l, macdLine, start, n, maxM, minM, paneTop, paneH, c, def.getLineWeight());
        if (sigLine != null)
            drawSubLine(gc, l, sigLine, start, n, maxM, minM, paneTop, paneH,
                    Color.web("#f85149"), 1.0);
    }

    /** RSI with overbought/oversold zones. */
    private void drawRsiPane(GraphicsContext gc, ChartLayout l, IndicatorDefinition def,
                             double paneTop, double paneH,
                             int start, int end, int n, Color c,
                             double oversold, double overbought) {
        List<Double> series = def.getSeries();
        if (series == null) return;
        double maxR = 100, minR = 0;
        double y70 = subY(overbought, maxR, minR, paneTop, paneH);
        double y30 = subY(oversold,   maxR, minR, paneTop, paneH);
        gc.setFill(Color.web("#f8514912"));
        gc.fillRect(l.left, paneTop, l.plotWidth(), y70 - paneTop);
        gc.setFill(Color.web("#3fb95012"));
        gc.fillRect(l.left, y30, l.plotWidth(), paneTop + paneH - y30);
        gc.setStroke(Color.web("#f8514966")); gc.setLineWidth(0.8);
        gc.setLineDashes(4, 4);
        gc.strokeLine(l.left, y70, l.right, y70);
        gc.setStroke(Color.web("#3fb95066"));
        gc.strokeLine(l.left, y30, l.right, y30);
        gc.setLineDashes();
        drawSubLine(gc, l, series, start, n, maxR, minR, paneTop, paneH, c, def.getLineWeight());
    }

    /** Stochastic / StochRSI with %D line. */
    private void drawOscillatorPane(GraphicsContext gc, ChartLayout l, IndicatorDefinition def,
                                    double paneTop, double paneH,
                                    int start, int end, int n, Color c,
                                    String dKey, double lo, double hi) {
        drawRsiPane(gc, l, def, paneTop, paneH, start, end, n, c, lo, hi);
        List<Double> dLine = def.getExtraSeries(dKey);
        if (dLine != null)
            drawSubLine(gc, l, dLine, start, n, 100, 0, paneTop, paneH,
                    Color.web("#e3b341"), 1.0);
    }

    /** Bounded oscillator like Williams %R (fixed range). */
    private void drawBoundedPane(GraphicsContext gc, ChartLayout l, IndicatorDefinition def,
                                 double paneTop, double paneH,
                                 int start, int end, int n, Color c,
                                 double minV, double maxV, double loLine, double hiLine) {
        double yHi = subY(hiLine, maxV, minV, paneTop, paneH);
        double yLo = subY(loLine, maxV, minV, paneTop, paneH);
        gc.setStroke(Color.web("#f8514966")); gc.setLineWidth(0.8);
        gc.setLineDashes(4, 4);
        gc.strokeLine(l.left, yHi, l.right, yHi);
        gc.setStroke(Color.web("#3fb95066"));
        gc.strokeLine(l.left, yLo, l.right, yLo);
        gc.setLineDashes();
        drawSubLine(gc, l, def.getSeries(), start, n, maxV, minV, paneTop, paneH, c, def.getLineWeight());
    }

    /** CCI / CMO / general unbounded oscillator. */
    private void drawUnboundedOscPane(GraphicsContext gc, ChartLayout l, IndicatorDefinition def,
                                      double paneTop, double paneH,
                                      int start, int end, int n, Color c) {
        List<Double> series = def.getSeries();
        if (series == null || series.isEmpty()) return;
        double maxV = Double.NEGATIVE_INFINITY, minV = Double.POSITIVE_INFINITY;
        for (int i = start; i <= end && i < series.size(); i++) {
            double v = series.get(i);
            if (!Double.isNaN(v)) { maxV = Math.max(maxV, v); minV = Math.min(minV, v); }
        }
        if (maxV == Double.NEGATIVE_INFINITY) return;
        double pad = Math.abs(maxV - minV) * 0.05 + 0.01;
        maxV += pad; minV -= pad;

        // Zero line
        double y0 = subY(0, maxV, minV, paneTop, paneH);
        gc.setStroke(GRID); gc.setLineWidth(0.7);
        gc.strokeLine(l.left, y0, l.right, y0);

        drawSubLine(gc, l, series, start, n, maxV, minV, paneTop, paneH, c, def.getLineWeight());
    }

    /** ADX with +DI / -DI. */
    private void drawAdxPane(GraphicsContext gc, ChartLayout l, IndicatorDefinition def,
                             double paneTop, double paneH,
                             int start, int end, int n, Color c) {
        drawSubLine(gc, l, def.getSeries(), start, n, 100, 0, paneTop, paneH, c, def.getLineWeight());
        List<Double> plus  = def.getExtraSeries("plusDI");
        List<Double> minus = def.getExtraSeries("minusDI");
        if (plus  != null) drawSubLine(gc, l, plus,  start, n, 100, 0, paneTop, paneH, Color.web("#3fb950"), 1.0);
        if (minus != null) drawSubLine(gc, l, minus, start, n, 100, 0, paneTop, paneH, Color.web("#f85149"), 1.0);
    }

    /** Aroon oscillator with up/down lines. */
    private void drawAroonPane(GraphicsContext gc, ChartLayout l, IndicatorDefinition def,
                               double paneTop, double paneH,
                               int start, int end, int n, Color c) {
        drawSubLine(gc, l, def.getSeries(),            start, n, 100, -100, paneTop, paneH, c, def.getLineWeight());
        List<Double> up   = def.getExtraSeries("up");
        List<Double> down = def.getExtraSeries("down");
        if (up   != null) drawSubLine(gc, l, up,   start, n, 100, 0, paneTop, paneH, Color.web("#3fb950"), 0.9);
        if (down != null) drawSubLine(gc, l, down, start, n, 100, 0, paneTop, paneH, Color.web("#f85149"), 0.9);
    }

    /** Generic: auto-scale the visible range. */
    private void drawGenericSubPane(GraphicsContext gc, ChartLayout l, IndicatorDefinition def,
                                    double paneTop, double paneH,
                                    int start, int end, int n, Color c) {
        drawUnboundedOscPane(gc, l, def, paneTop, paneH, start, end, n, c);
    }

    // ── Draw: Price Axis ──────────────────────────────────────

    private void drawPriceAxis(GraphicsContext gc, ChartLayout l, double maxP, double minP) {
        gc.setFill(TEXT_DIM);
        gc.setFont(FONT_SMALL);
        gc.setTextAlign(TextAlignment.LEFT);
        int levels = 6;
        for (int i = 0; i <= levels; i++) {
            double price = maxP - (maxP - minP) * i / levels;
            double y     = priceToY(price, maxP, minP, l);
            gc.fillText(fmtPrice(price), l.right + 4, y + 4);
        }
    }

    // ── Draw: Time Axis ───────────────────────────────────────

    private void drawTimeAxis(GraphicsContext gc, ChartLayout l, List<OhlcvBar> visible, double w) {
        gc.setFill(TEXT_DIM);
        gc.setFont(FONT_SMALL);
        gc.setTextAlign(TextAlignment.CENTER);
        int n    = visible.size();
        int step = Math.max(1, n / 8);
        double barW = l.plotWidth() / n;
        for (int i = 0; i < n; i += step) {
            OhlcvBar bar = visible.get(i);
            if (bar.getOpenTime() == null) continue;
            double x = l.left + (i + 0.5) * barW;
            // Convert bar time (assumed UTC / server-stored) to user's local timezone
            String label = bar.getOpenTime().atZone(ZoneId.of("UTC"))
                    .format(axisFormatter);
            gc.fillText(label, x, l.priceBottom() + 16);
        }
    }

    // ── Draw: Separators ─────────────────────────────────────

    private void drawSeparators(GraphicsContext gc, ChartLayout l, double w) {
        gc.setStroke(BORDER);
        gc.setLineWidth(1.0);
        gc.setLineDashes();
        gc.strokeLine(l.left, l.priceBottom(), l.right, l.priceBottom());
        if (l.volH > 0)
            gc.strokeLine(l.left, l.volTop() + l.volH, l.right, l.volTop() + l.volH);
        for (int i = 0; i < l.subCount; i++) {
            double y = l.subPaneTop(i) + l.subPaneH;
            gc.strokeLine(l.left, y, l.right, y);
        }
    }

    // ── Draw: Crosshair ──────────────────────────────────────

    private void drawCrosshair(GraphicsContext gc, ChartLayout l,
                               List<OhlcvBar> visible, double maxP, double minP) {
        if (mouseX < l.left || mouseX > l.right) return;

        gc.setStroke(CROSS);
        gc.setLineWidth(0.8);
        gc.setLineDashes(4, 4);
        gc.strokeLine(mouseX, l.priceTop(), mouseX, l.priceBottom());
        gc.strokeLine(l.left, mouseY, l.right, mouseY);
        gc.setLineDashes();

        // Price label
        double price = yToPrice(mouseY, maxP, minP, l);
        gc.setFill(Color.web("#388bfd"));
        gc.fillRect(l.right + 1, mouseY - 9, PADDING_RIGHT - 4, 18);
        gc.setFill(Color.WHITE);
        gc.setFont(FONT_SMALL);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(fmtPrice(price), l.right + 4, mouseY + 4);

        // Candle tooltip
        int n    = visible.size();
        double barW = l.plotWidth() / Math.max(1, n);
        int barIdx  = Math.max(0, Math.min(n - 1, (int)((mouseX - l.left) / barW)));
        if (barIdx < n) {
            OhlcvBar bar = visible.get(barIdx);
            drawTooltip(gc, bar, mouseX, l.priceTop() + 4);
            if (bar.getOpenTime() != null) {
                // Show time in user's local timezone
                String dateStr = bar.getOpenTime().atZone(ZoneId.of("UTC"))
                        .format(tooltipFormatter);
                double lw = 160, lh = 16;
                double lx = Math.max(l.left + lw / 2, Math.min(l.right - lw / 2, mouseX));
                gc.setFill(Color.web("#388bfd"));
                gc.fillRoundRect(lx - lw / 2, l.priceBottom() + 2, lw, lh, 4, 4);
                gc.setFill(Color.WHITE);
                gc.setFont(FONT_SMALL);
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText(dateStr, lx, l.priceBottom() + 14);
            }
        }
    }

    private void drawTooltip(GraphicsContext gc, OhlcvBar bar, double x, double y) {
        boolean bull = bar.getClose().compareTo(bar.getOpen()) >= 0;
        String[] lines = {
                "O: " + fmtPrice(bar.getOpen().doubleValue()),
                "H: " + fmtPrice(bar.getHigh().doubleValue()),
                "L: " + fmtPrice(bar.getLow().doubleValue()),
                "C: " + fmtPrice(bar.getClose().doubleValue()),
                "V: " + fmtVol(bar.getVolume().doubleValue())
        };
        double bw = 140, bh = lines.length * 16 + 10;
        double bx = Math.min(x + 10, getWidth() - bw - 10);
        gc.setFill(Color.web("#1c2128ee"));
        gc.fillRoundRect(bx, y, bw, bh, 6, 6);
        gc.setStroke(bull ? BULL_BODY : BEAR_BODY);
        gc.setLineWidth(1);
        gc.strokeRoundRect(bx, y, bw, bh, 6, 6);
        gc.setFill(TEXT_MAIN);
        gc.setFont(FONT_SMALL);
        gc.setTextAlign(TextAlignment.LEFT);
        for (int i = 0; i < lines.length; i++)
            gc.fillText(lines[i], bx + 8, y + 16 + i * 16);
    }

    // ── Draw: Legend ─────────────────────────────────────────

    private void drawLegend(GraphicsContext gc, ChartLayout l) {
        double lx = l.left + 8;
        double ly = l.priceTop() + 16;
        gc.setFont(FONT_SMALL);
        gc.setTextAlign(TextAlignment.LEFT);
        for (IndicatorDefinition def : indicators) {
            if (!def.isVisible() || def.getPane() != DisplayPane.PRICE) continue;
            Color c = Color.web(def.getColor());
            gc.setStroke(c);
            gc.setLineWidth(2);
            gc.strokeLine(lx, ly - 4, lx + 16, ly - 4);
            gc.setFill(TEXT_DIM);
            gc.fillText(def.getLabel(), lx + 20, ly);
            lx += def.getLabel().length() * 6.5 + 32;
            if (lx > l.right - 100) break;
        }
    }

    // ── Draw: Empty state ─────────────────────────────────────

    private void drawEmpty(GraphicsContext gc, double w, double h) {
        gc.setFill(BG);
        gc.fillRect(0, 0, w, h);
        gc.setFill(TEXT_DIM);
        gc.setFont(Font.font("Segoe UI", 16));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("📈 Select a symbol and timeframe to load chart", w / 2, h / 2);
    }

    // ── Mouse Handlers ────────────────────────────────────────

    private void setupMouseHandlers() {
        setOnMouseMoved(e -> { mouseX = e.getX(); mouseY = e.getY(); showCrosshair = true; render(); });
        setOnMouseDragged(this::onMouseDragged);
        setOnMousePressed(e -> { dragStartX = e.getX(); dragStartBar = startBarIndex; });
        setOnMouseExited(e  -> { showCrosshair = false; render(); });
        setOnScroll(this::onScroll);
    }

    private void onMouseDragged(MouseEvent e) {
        if (bars == null || bars.isEmpty()) return;
        double dx   = e.getX() - dragStartX;
        double barW = (getWidth() - PADDING_LEFT - PADDING_RIGHT) / Math.max(1, visibleBars);
        int shift   = (int)(dx / barW);
        startBarIndex = Math.max(0, Math.min(bars.size() - visibleBars, dragStartBar - shift));
        mouseX = e.getX(); mouseY = e.getY();
        render();
    }

    private void onScroll(ScrollEvent e) {
        if (bars == null || bars.isEmpty()) return;
        double delta    = e.getDeltaY();
        double plotW    = getWidth() - PADDING_LEFT - PADDING_RIGHT;
        double relX     = e.getX() - PADDING_LEFT;
        double fraction = (plotW > 0) ? Math.max(0, Math.min(1, relX / plotW)) : 0.5;

        // Bar index (absolute) that should stay fixed under the mouse
        // Clamp anchor to valid range before computing new start
        int oldVisible = visibleBars;
        int anchor = startBarIndex + (int) Math.round(fraction * oldVisible);
        anchor = Math.max(0, Math.min(bars.size() - 1, anchor));

        // Zoom in = scroll up (deltaY > 0), zoom out = scroll down (deltaY < 0)
        if (delta < 0) {
            // Zoom OUT — always allow, expand visible range
            visibleBars = Math.min(bars.size(), (int) Math.ceil(visibleBars * 1.15));
        } else {
            // Zoom IN — limit to minimum 5 bars
            visibleBars = Math.max(5, (int) Math.floor(visibleBars * 0.87));
        }

        // Final clamp: visibleBars must be in [1, bars.size()]
        visibleBars = Math.max(1, Math.min(visibleBars, bars.size()));

        // Recalculate startBarIndex so that the anchor bar stays under the mouse.
        // When zooming out fully (visibleBars == bars.size()), force startBarIndex = 0.
        if (visibleBars >= bars.size()) {
            startBarIndex = 0;
        } else {
            int newStart = anchor - (int) Math.round(fraction * visibleBars);
            startBarIndex = Math.max(0, Math.min(bars.size() - visibleBars, newStart));
        }
        render();
    }

    // ── Line series helpers ───────────────────────────────────

    /**
     * Draws a line series from a list that covers the FULL bar history.
     * {@code start} is the absolute index of the first visible bar.
     */
    private void drawLineSeriesAbsolute(GraphicsContext gc, ChartLayout l,
                                        List<Double> series, int start, int n,
                                        double maxP, double minP,
                                        Color color, double width) {
        if (series == null || series.isEmpty()) return;
        gc.setStroke(color);
        gc.setLineWidth(width);
        gc.setLineDashes();
        gc.beginPath();
        boolean started = false;
        double barW = l.plotWidth() / n;
        for (int i = 0; i < n; i++) {
            int idx = start + i;
            if (idx >= series.size()) break;
            double val = series.get(idx);
            if (Double.isNaN(val)) { started = false; continue; }
            double x = l.left + (i + 0.5) * barW;
            double y = priceToY(val, maxP, minP, l);
            if (!started) { gc.moveTo(x, y); started = true; }
            else gc.lineTo(x, y);
        }
        gc.stroke();
    }

    private void drawSubLine(GraphicsContext gc, ChartLayout l,
                             List<Double> series, int start, int n,
                             double maxV, double minV,
                             double paneTop, double paneH,
                             Color color, double width) {
        if (series == null || series.isEmpty()) return;
        gc.setStroke(color);
        gc.setLineWidth(width);
        gc.setLineDashes();
        gc.beginPath();
        boolean started = false;
        double barW = l.plotWidth() / n;
        for (int i = 0; i < n; i++) {
            int idx = start + i;
            if (idx >= series.size()) break;
            double val = series.get(idx);
            if (Double.isNaN(val)) { started = false; continue; }
            double x = l.left + (i + 0.5) * barW;
            double y = subY(val, maxV, minV, paneTop, paneH);
            if (!started) { gc.moveTo(x, y); started = true; }
            else gc.lineTo(x, y);
        }
        gc.stroke();
    }

    // ── Price range helpers for indicators ───────────────────

    private void extendPriceRange(IndicatorDefinition def, int start, int end) {
        // no-op: range is computed in render() directly
    }

    private double visibleMax(List<Double> series, int start, int end) {
        if (series == null || series.isEmpty()) return Double.NEGATIVE_INFINITY;
        double m = Double.NEGATIVE_INFINITY;
        for (int i = start; i <= end && i < series.size(); i++) {
            double v = series.get(i);
            if (!Double.isNaN(v)) m = Math.max(m, v);
        }
        return m == Double.NEGATIVE_INFINITY ? Double.NEGATIVE_INFINITY : m;
    }

    private double visibleMin(List<Double> series, int start, int end) {
        if (series == null || series.isEmpty()) return Double.POSITIVE_INFINITY;
        double m = Double.POSITIVE_INFINITY;
        for (int i = start; i <= end && i < series.size(); i++) {
            double v = series.get(i);
            if (!Double.isNaN(v)) m = Math.min(m, v);
        }
        return m == Double.POSITIVE_INFINITY ? Double.POSITIVE_INFINITY : m;
    }

    // ── Coordinate transforms ─────────────────────────────────

    private double priceToY(double price, double maxP, double minP, ChartLayout l) {
        double range = Math.max(maxP - minP, 1e-10);
        return l.priceTop() + l.priceH * (1.0 - (price - minP) / range);
    }

    private double yToPrice(double y, double maxP, double minP, ChartLayout l) {
        double range = Math.max(maxP - minP, 1e-10);
        return maxP - (y - l.priceTop()) / l.priceH * range;
    }

    private double subY(double val, double maxV, double minV,
                        double top, double height) {
        double range = Math.max(Math.abs(maxV - minV), 1e-10);
        return top + height * (1.0 - (val - minV) / range);
    }

    // ── Formatting ────────────────────────────────────────────

    private String fmtPrice(double p) {
        if (Double.isNaN(p) || Double.isInfinite(p)) return "—";
        if (p >= 10000) return String.format("%.2f", p);
        if (p >= 1000)  return String.format("%.2f", p);
        if (p >= 1)     return String.format("%.4f", p);
        return String.format("%.8f", p);
    }

    private String fmtVol(double v) {
        if (v >= 1_000_000_000) return String.format("%.2fB", v / 1_000_000_000);
        if (v >= 1_000_000)     return String.format("%.2fM", v / 1_000_000);
        if (v >= 1_000)         return String.format("%.2fK", v / 1_000);
        return String.format("%.2f", v);
    }

    /** Safely parse a web color string; falls back to {@code fallback} if null/invalid. */
    private static Color safeWebColor(String webColor, String fallback) {
        try {
            if (webColor != null && !webColor.isBlank()) return Color.web(webColor);
        } catch (IllegalArgumentException ignored) { }
        return Color.web(fallback);
    }

    // ── Layout record ─────────────────────────────────────────

    private record ChartLayout(
            double left, double top, double right, double bottom,
            double priceH, double volH, double subPaneH, long subCount
    ) {
        double priceTop()   { return top; }
        double priceBottom(){ return top + priceH; }
        double volTop()     { return priceBottom(); }
        double subPanesTop(){ return volTop() + volH; }
        double subPaneTop(int i) { return subPanesTop() + i * subPaneH; }
        double plotWidth()  { return right - left; }
    }
}
