package com.mst.matt.tradingplatformapp.ui.chart;

import com.mst.matt.tradingplatformapp.model.ChartDrawing;
import com.mst.matt.tradingplatformapp.model.ChartDrawingToolType;
import com.mst.matt.tradingplatformapp.model.IndicatorDefinition;
import com.mst.matt.tradingplatformapp.model.IndicatorDefinition.DisplayPane;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.service.analysis.SupportResistanceService.SRLevel;
import com.mst.matt.tradingplatformapp.service.analysis.SupportResistanceService.SRResult;
import com.mst.matt.tradingplatformapp.ui.chart.drawing.ChartDrawingEngine;
import com.mst.matt.tradingplatformapp.ui.chart.drawing.DrawingRenderer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TouchPoint;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.Window;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
public class CandlestickChartCanvas extends Canvas implements ChartDrawingEngine.Host {

    // ── Layout constants ──────────────────────────────────────
    private static final double PADDING_LEFT   = 60;
    private static final double PADDING_RIGHT  = 82;
    private static final double PADDING_TOP    = 20;
    private static final double PADDING_BOTTOM = 26;
    private static final double VOL_FRAC       = 0.14;  // fraction of full height for volume
    private static final double VOL_MIN_HEIGHT = 70.0;  // minimum px height for volume pane
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
     * <p>Fix 2: The startBarIndex may now be <b>negative</b> (empty space left of the first
     * candle) or greater than {@code bars.size() - visibleBars} (empty space right of the
     * last candle), allowing users to pan into blank space for annotations, notes, and
     * projections.  The render loop clips the visible window to valid bar indices, so only
     * the bars that actually exist are drawn.
     * <p>Extended pan range = ±25% of the total bar count.
     */
    private int    startBarIndex = 0;

    /**
     * Number of bars visible in the chart at the current zoom level.
     * INVARIANT: 1 ≤ visibleBars ≤ bars.size()
     */
    private int    visibleBars   = 100;

    /**
     * Fix 2: Fraction of the total bar count added as empty-space padding on each side.
     * At 0.25 a 200-bar dataset allows panning 50 bars left and 50 bars right of the data.
     */
    private static final double PAN_MARGIN_FRAC = 0.25;

    // ── Overlays ──────────────────────────────────────────────
    private boolean showVolume    = true;
    private boolean analysisMode  = false;

    // ── Mouse / drag ─────────────────────────────────────────
    private double  mouseX, mouseY;
    private boolean showCrosshair  = false;
    private double  dragStartX;
    private double  dragStartY;
    private int     dragStartBar;
    private double  dragStartPriceOffset;
    private boolean drawingHandledDrag;

    // ── Touch / gesture state ─────────────────────────────────
    /** Number of active touch points currently on the canvas. */
    private int    activeTouchCount  = 0;
    /** Mid-point X between two touch fingers at the start of a pinch/pan gesture. */
    private double touchMidX         = 0;
    /** Mid-point Y between two touch fingers at the start of a pinch/pan gesture. */
    private double touchMidY         = 0;
    /** Distance between two fingers at the start of a pinch gesture (pixels). */
    private double pinchStartDist     = 0;
    /** Number of visible bars at the start of a pinch gesture. */
    private int    pinchStartVisible  = 0;
    /** startBarIndex at the start of a two-finger pan. */
    private int    twoFingerPanStartBar = 0;
    /** Mid-X at the start of a two-finger pan. */
    private double twoFingerPanStartX  = 0;

    // ── Touch / gesture ───────────────────────────────────────
    /** X position of an active single-finger touch for pan. */
    private double  touchPanStartX  = 0;
    private int     touchPanStartBar = 0;
    /** Whether a multi-touch (zoom) gesture is in progress. */
    private boolean touchZooming    = false;
    /** Visible-bars count captured at the start of a zoom gesture. */
    private int     zoomStartBars   = 0;

    // ── Sensitivity settings ─────────────────────────────────
    /**
     * Zoom sensitivity multiplier (0.1 – 1.0).
     * At 1.0 the full ZoomEvent factor is applied; at 0.1 only 10% is applied per event.
     * Default 0.4 — noticeably gentler than the old 1.0 behaviour.
     */
    private double zoomSensitivity  = 0.4;
    /**
     * Pan sensitivity multiplier (0.1 – 1.0).
     * Scales the pixel delta before converting to bar shift.
     * Default 0.6 — a bit gentler than 1:1.
     */
    private double panSensitivity   = 0.6;

    /**
     * Vertical pan offset as a fraction of the visible price range.
     * Positive = shift price window upward (show lower prices); negative = downward.
     * Reset to 0 whenever new data is loaded.
     */
    private double  priceOffsetPct = 0.0;



    // ── Drawing overlay ───────────────────────────────────────
    private final ChartDrawingEngine drawingEngine = new ChartDrawingEngine(this);
    private DrawingRenderer.RenderContext lastRenderContext;
    private boolean snapMode;

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
        setupTouchHandlers();
        setupKeyHandlers();
        setupTouchHandlers2();
        widthProperty().addListener((o, a, b) -> {
            if (b.doubleValue() > 0 && getHeight() > 0) render();
        });
        heightProperty().addListener((o, a, b) -> {
            if (b.doubleValue() > 0 && getWidth() > 0) render();
        });
    }

    // ── Public API ────────────────────────────────────────────

    /**
     * The preferred number of bars to display after data is loaded.
     * Set by the controller from the barsCombo selection.
     * Defaults to 100.
     */
    private int preferredVisibleBars = 100;

    /** Sets the preferred number of visible bars (from the barsCombo control). */
    public void setPreferredVisibleBars(int n) {
        this.preferredVisibleBars = Math.max(5, n);
    }

    public int getPreferredVisibleBars() { return preferredVisibleBars; }

    /** Sets chart data and resets view to show the last {@link #preferredVisibleBars} bars. */
    public void setData(List<OhlcvBar> bars, List<IndicatorDefinition> indicators, SRResult srResult) {
        this.bars       = bars;
        this.indicators = indicators != null ? indicators : new ArrayList<>();
        this.srResult   = srResult;

        if (bars != null && !bars.isEmpty()) {
            // Use the preferred visible bars count (from barsCombo), capped to actual bar count
            visibleBars   = Math.min(preferredVisibleBars, bars.size());
            startBarIndex = Math.max(0, bars.size() - visibleBars);
        }
        priceOffsetPct = 0.0;  // reset vertical pan when new data arrives
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

    /**
     * Sets the zoom sensitivity (0.1 = very slow, 1.0 = full speed).
     * Applied to both mouse-wheel scroll zoom and touch pinch-zoom.
     */
    public void setZoomSensitivity(double v) {
        this.zoomSensitivity = Math.max(0.05, Math.min(1.0, v));
    }

    /**
     * Sets the pan sensitivity (0.1 = very slow, 1.0 = 1:1 with pixel delta).
     * Applied to both mouse drag and single-finger touch pan.
     */
    public void setPanSensitivity(double v) {
        this.panSensitivity = Math.max(0.05, Math.min(1.0, v));
    }

    public double getZoomSensitivity() { return zoomSensitivity; }
    public double getPanSensitivity()  { return panSensitivity; }

    public ChartDrawingEngine getDrawingEngine() { return drawingEngine; }

    public void setDrawings(List<ChartDrawing> drawings) {
        drawingEngine.setDrawings(drawings);
        render();
    }

    public void setActiveDrawingTool(ChartDrawingToolType tool) {
        drawingEngine.setActiveTool(tool);
        render();
    }

    /**
     * Captures a screenshot of the current chart canvas as a WritableImage.
     *
     * @return a {@link javafx.scene.image.WritableImage} of the canvas contents,
     *         or {@code null} if the canvas has no content.
     */
    public javafx.scene.image.WritableImage captureScreenshot() {
        double w = getWidth(), h = getHeight();
        if (w < 1 || h < 1) return null;
        javafx.scene.image.WritableImage img = new javafx.scene.image.WritableImage((int) w, (int) h);
        return snapshot(new javafx.scene.SnapshotParameters(), img);
    }

    // ── ChartDrawingEngine.Host ─────────────────────────────

    @Override public void requestRender() { render(); }

    @Override public DrawingRenderer.RenderContext currentRenderContext() {
        return lastRenderContext;
    }

    @Override public List<OhlcvBar> getBars() { return bars; }

    @Override public Window getWindow() {
        return getScene() != null ? getScene().getWindow() : null;
    }

    // ── Main Render ───────────────────────────────────────────

    public void render() {
        double w = getWidth();
        double h = getHeight();

        if (w <= 0 || h <= 0) return;

        GraphicsContext gc = getGraphicsContext2D();

        if (bars == null || bars.isEmpty()) {
            drawEmpty(gc, w, h);
            return;
        }

        // ── Clamp view window ──────────────────────────────────
        visibleBars = Math.max(1, Math.min(visibleBars, bars.size()));

        // Fix 2: Allow startBarIndex to extend beyond data bounds so users can pan into
        // empty space on both sides for annotations, trend-line projections, and notes.
        // panMargin = 25% of the total bar count (at least 10 bars each side).
        int panMargin = Math.max(10, (int)(bars.size() * PAN_MARGIN_FRAC));
        int minStart = -panMargin;                              // empty space left of first candle
        int maxStart = bars.size() - visibleBars + panMargin;  // empty space right of last candle
        startBarIndex = Math.max(minStart, Math.min(startBarIndex, maxStart));

        // Actual data bar indices that are visible (clipped to data bounds)
        int dataStart = Math.max(0, startBarIndex);
        int dataEnd   = Math.min(bars.size() - 1, startBarIndex + visibleBars - 1);
        int endBarIndex = startBarIndex + visibleBars - 1;  // may point beyond data bounds

        // Visible sub-list — used ONLY for drawing candles and volume
        // Guard against empty or out-of-bounds slice when panning into empty space
        List<OhlcvBar> visible = (dataStart <= dataEnd)
                ? bars.subList(dataStart, dataEnd + 1)
                : Collections.emptyList();

        // ── Layout ────────────────────────────────────────────
        // Count sub-pane indicators
        long subCount = subPaneCount();
        double totalH = h - PADDING_TOP - PADDING_BOTTOM;
        double subTotal  = Math.min(0.45, subCount * SUB_FRAC);
        // Volume pane: enforce a minimum height of VOL_MIN_HEIGHT px when visible
        double volH;
        if (showVolume) {
            volH = Math.max(VOL_MIN_HEIGHT, totalH * VOL_FRAC);
        } else {
            volH = 0;
        }
        double volFrac   = totalH > 0 ? volH / totalH : 0;
        double priceFrac = 1.0 - subTotal - volFrac;
        double priceH    = totalH * priceFrac;

        // Assign height to each sub-pane indicator
        double subPaneH = subCount > 0 ? (totalH * subTotal / subCount) : 0;

        ChartLayout layout = new ChartLayout(
                PADDING_LEFT, PADDING_TOP, w - PADDING_RIGHT, h - PADDING_BOTTOM,
                priceH, volH, subPaneH, subCount);

        // ── Price range ───────────────────────────────────────
        double maxPrice = visible.stream().mapToDouble(b -> b.getHigh().doubleValue()).max().orElse(1);
        double minPrice = visible.stream().mapToDouble(b -> b.getLow().doubleValue()).min().orElse(0);

        // Extend price range for price-pane indicator series (use clipped data indices)
        for (IndicatorDefinition def : indicators) {
            if (!def.isVisible() || def.getPane() != DisplayPane.PRICE) continue;
            extendPriceRange(def, dataStart, dataEnd);
        }
        // Extend for the indicator's own range
        for (IndicatorDefinition def : indicators) {
            if (!def.isVisible() || def.getPane() != DisplayPane.PRICE) continue;
            maxPrice = Math.max(maxPrice, visibleMax(def.getSeries(), dataStart, dataEnd));
            minPrice = Math.min(minPrice, visibleMin(def.getSeries(), dataStart, dataEnd));
            // Also check extra series (e.g. Bollinger upper)
            for (List<Double> extra : def.getExtraSeries().values()) {
                maxPrice = Math.max(maxPrice, visibleMax(extra, dataStart, dataEnd));
                minPrice = Math.min(minPrice, visibleMin(extra, dataStart, dataEnd));
            }
        }
        double pad = (maxPrice - minPrice) * 0.05;
        maxPrice += pad;
        minPrice -= pad;
        if (maxPrice == minPrice) { maxPrice += 1; minPrice -= 1; }

        // ── Apply vertical pan offset ──────────────────────────
        // Issue #5 (fixed): priceOffsetPct is now negated at drag-time so:
        //   drag UP   → priceOffsetPct > 0 → shift > 0 → maxPrice & minPrice increase → window moves UP
        //   drag DOWN → priceOffsetPct < 0 → shift < 0 → maxPrice & minPrice decrease → window moves DOWN
        if (priceOffsetPct != 0.0) {
            double priceRange = maxPrice - minPrice;
            double shift = priceRange * priceOffsetPct;
            maxPrice += shift;
            minPrice += shift;
        }

        double maxVol = visible.stream().mapToDouble(b -> b.getVolume().doubleValue()).max().orElse(1);

        // ── Draw ──────────────────────────────────────────────

        gc.setFill(BG);
        gc.fillRect(0, 0, w, h);

        drawGrid(gc, layout, maxPrice, minPrice, visibleBars);

        // Price-pane indicators (fills first, then lines)
        // Pass startBarIndex (which may be negative) and visibleBars; each drawing helper
        // performs its own clamp when indexing into the data arrays.
        drawPricePaneIndicatorFills(gc, layout, maxPrice, minPrice, startBarIndex, endBarIndex, visibleBars);
        drawPricePaneIndicatorLines(gc, layout, maxPrice, minPrice, startBarIndex, endBarIndex, visibleBars);

        // Support / Resistance
        if (srResult != null) {
            IndicatorDefinition srDef = indicators.stream()
                    .filter(d -> d.getType() == IndicatorDefinition.Type.SUPPORT_RESISTANCE && d.isVisible())
                    .findFirst().orElse(null);
            if (srDef != null) drawSupportResistance(gc, layout, maxPrice, minPrice);
        }

        // Candles — draw at the correct horizontal offset when startBarIndex < 0
        drawCandles(gc, layout, visible, maxPrice, minPrice, startBarIndex, visibleBars);

        // Last price line
        drawLastPriceLine(gc, layout, maxPrice, minPrice, visible);

        // Volume — same horizontal offset logic
        if (showVolume) drawVolume(gc, layout, visible, maxVol, startBarIndex, visibleBars);

        // Sub-pane indicators
        drawSubPaneIndicators(gc, layout, startBarIndex, endBarIndex, visibleBars);

        // Axes + separators
        drawPriceAxis(gc, layout, maxPrice, minPrice);
        drawTimeAxis(gc, layout, visible, w);
        drawSeparators(gc, layout, w);

        // Legend
        drawLegend(gc, layout);

        // User drawings (after candles, before crosshair)
        lastRenderContext = new DrawingRenderer.RenderContext(
                layout.left, layout.right, layout.priceTop(), layout.priceBottom(),
                layout.priceH, startBarIndex, visibleBars, maxPrice, minPrice, bars);
        drawingEngine.renderDrawings(gc, lastRenderContext);

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

    /**
     * Fix 2: Draw candles with correct horizontal offset when startBarIndex < 0 or > data bounds.
     * When panning into empty space, the first visible candle is offset from the left edge.
     *
     * @param startBarIndexArg the (possibly-negative) view start bar index
     * @param totalVisibleBars total visible bar slots (including empty space)
     */
    private void drawCandles(GraphicsContext gc, ChartLayout l,
                             List<OhlcvBar> visible, double maxP, double minP,
                             int startBarIndexArg, int totalVisibleBars) {
        if (visible == null || visible.isEmpty()) return;
        int n    = totalVisibleBars;  // total slots across the chart width
        double barW  = l.plotWidth() / Math.max(1, n);
        double bodyW = Math.max(1.5, barW * 0.65);
        double wickW = Math.max(1.0, bodyW * 0.25);

        // Offset in slots from the left edge where the actual data starts
        int dataSlotOffset = (startBarIndexArg < 0) ? -startBarIndexArg : 0;

        for (int i = 0; i < visible.size(); i++) {
            OhlcvBar bar = visible.get(i);
            double o  = bar.getOpen().doubleValue();
            double hi = bar.getHigh().doubleValue();
            double lo = bar.getLow().doubleValue();
            double c  = bar.getClose().doubleValue();
            boolean bull = c >= o;

            // Slot index from the left edge of the chart
            int slot = dataSlotOffset + i;
            double cx       = l.left + (slot + 0.5) * barW;
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

    /**
     * Fix 2: Draw volume bars with the same horizontal offset as candles.
     */
    private void drawVolume(GraphicsContext gc, ChartLayout l,
                            List<OhlcvBar> visible, double maxVol,
                            int startBarIndexArg, int totalVisibleBars) {
        if (l.volH <= 0 || visible == null || visible.isEmpty()) return;
        int n    = totalVisibleBars;
        double barW    = l.plotWidth() / Math.max(1, n);
        double volBarW = Math.max(1.5, barW * 0.7);
        double volTop  = l.volTop();

        // Offset in slots from the left edge where data starts
        int dataSlotOffset = (startBarIndexArg < 0) ? -startBarIndexArg : 0;

        for (int i = 0; i < visible.size(); i++) {
            OhlcvBar bar = visible.get(i);
            double vol  = bar.getVolume().doubleValue();
            boolean bull = bar.getClose().compareTo(bar.getOpen()) >= 0;
            double barH = (vol / maxVol) * l.volH;
            int slot = dataSlotOffset + i;
            double x    = l.left + (slot + 0.5) * barW - volBarW / 2;
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
        double barW = l.plotWidth() / Math.max(1, n);
        gc.setFill(fill);
        gc.beginPath();
        boolean started = false;
        for (int i = 0; i < n; i++) {
            int idx = start + i;
            if (idx < 0) continue;  // Fix 2: skip empty space before data
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
            if (idx < 0) continue;  // Fix 2: skip empty space before data
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
        double barW = l.plotWidth() / Math.max(1, n);
        for (int i = 0; i < n - 1; i++) {
            int idx = start + i;
            if (idx < 0) continue;  // Fix 2: skip empty space before data
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
        double barW = l.plotWidth() / Math.max(1, n);
        double r    = Math.max(2.0, def.getLineWeight() + 1.0);
        gc.setFill(Color.web(def.getColor()));
        for (int i = 0; i < n; i++) {
            int idx = start + i;
            if (idx < 0) continue;  // Fix 2: skip empty space before data
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
                if (idx < 0) continue;  // Fix 2: skip empty space before data
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

    /**
     * Fix 2: The time axis must account for the extended pan range.
     * When startBarIndex is negative, visible data bars start at a non-zero slot offset.
     * {@code visible} always contains only actual data bars; {@code totalVisibleBars} is
     * the total slot count (including empty space). {@code dataSlotOffset} is the number
     * of empty slots before the first data bar.
     */
    private void drawTimeAxis(GraphicsContext gc, ChartLayout l, List<OhlcvBar> visible, double w) {
        gc.setFill(TEXT_DIM);
        gc.setFont(FONT_SMALL);
        gc.setTextAlign(TextAlignment.CENTER);
        if (visible == null || visible.isEmpty()) return;
        int totalSlots = visibleBars;  // total bar slots across the chart width
        int dataSlotOffset = (startBarIndex < 0) ? -startBarIndex : 0;
        int n    = visible.size();
        int step = Math.max(1, Math.max(n, totalSlots) / 8);
        double barW = l.plotWidth() / Math.max(1, totalSlots);
        for (int i = 0; i < n; i += step) {
            OhlcvBar bar = visible.get(i);
            if (bar.getOpenTime() == null) continue;
            int slot = dataSlotOffset + i;
            double x = l.left + (slot + 0.5) * barW;
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

        // Candle tooltip — Fix 2: account for the data slot offset when startBarIndex < 0
        int totalSlots = visibleBars;
        int dataSlotOffsetCross = (startBarIndex < 0) ? -startBarIndex : 0;
        int n    = visible != null ? visible.size() : 0;
        double barW = l.plotWidth() / Math.max(1, totalSlots);
        int slotIdx = (int)((mouseX - l.left) / barW);
        int barIdx  = slotIdx - dataSlotOffsetCross;  // index into visible data list
        if (barIdx >= 0 && barIdx < n && visible != null) {
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
        setOnMouseMoved(e -> {
            mouseX = e.getX(); mouseY = e.getY();
            showCrosshair = true;
            drawingEngine.setSnapEnabled(snapMode || e.isShiftDown());
            // Update hover state (for highlight + quick-delete button)
            drawingEngine.handleMouseMoved(e, lastRenderContext);
            render();
        });
        setOnMouseDragged(e -> {
            drawingEngine.setSnapEnabled(snapMode || e.isShiftDown());
            drawingHandledDrag = drawingEngine.handleMouseDragged(e, lastRenderContext);
            if (!drawingHandledDrag) {
                // Drawing engine did not consume the drag.
                // In SELECT mode: pan the chart (only with primary button).
                // In drawing mode: do nothing (drawing engine handles the event via
                //   handleMouseDragged returning true once a point is in progress).
                if (!drawingEngine.isDrawingMode()
                        && e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                    onMouseDragged(e);
                } else {
                    mouseX = e.getX(); mouseY = e.getY();
                }
            } else {
                mouseX = e.getX(); mouseY = e.getY();
            }
        });
        setOnMousePressed(e -> {
            requestFocus();
            drawingEngine.setSnapEnabled(snapMode || e.isShiftDown());
            dragStartX = e.getX();
            dragStartY = e.getY();
            dragStartBar = startBarIndex;
            dragStartPriceOffset = priceOffsetPct;
            drawingHandledDrag = drawingEngine.handleMousePressed(e, lastRenderContext);
        });
        setOnMouseReleased(e -> {
            if (drawingEngine.handleMouseReleased(e, lastRenderContext)) {
                render();
            }
        });
        setOnMouseExited(e  -> { showCrosshair = false; render(); });
        setOnScroll(this::onScroll);
    }

    // ── Touch Handlers ────────────────────────────────────────

    /**
     * Sets up touch and zoom gesture handlers for the chart canvas.
     *
     * Gesture mapping:
     * - Pinch (two-finger zoom gesture)  → zoom in / out  [ZoomEvent]
     * - Single-finger drag               → pan the chart
     * - Drawing tool active + single touch → creates the shape (no zoom/pan interference)
     * - Touch pressed/released           → delegate to drawing engine where appropriate
     */
    private void setupTouchHandlers() {
        // ── Zoom gestures (JavaFX ZoomEvent — generated by 2-finger pinch) ──
        setOnZoomStarted(e -> {
            touchZooming = true;
            zoomStartBars = visibleBars;
            e.consume();
        });
        setOnZoom(e -> {
            if (bars == null || bars.isEmpty()) return;
            double rawFactor = e.getZoomFactor();
            if (rawFactor <= 0 || Double.isNaN(rawFactor)) return;
            // Dampen the zoom factor toward 1.0 using the sensitivity multiplier.
            // factor > 1.0 = zoom IN (fingers apart), < 1.0 = zoom OUT (fingers together)
            double dampedFactor = 1.0 + (rawFactor - 1.0) * zoomSensitivity;
            dampedFactor = Math.max(0.1, dampedFactor); // safety floor
            int newVisible = (int) Math.round(visibleBars / dampedFactor);
            newVisible = Math.max(5, Math.min(bars.size(), newVisible));
            // Keep the center of the view anchored
            int midAnchor = startBarIndex + visibleBars / 2;
            visibleBars = newVisible;
            startBarIndex = Math.max(0, Math.min(bars.size() - visibleBars, midAnchor - visibleBars / 2));
            render();
            e.consume();
        });
        setOnZoomFinished(e -> {
            touchZooming = false;
            e.consume();
        });

        // ── Single-finger touch → pan (or delegate to drawing engine) ──
        setOnTouchPressed(e -> {
            if (e.getTouchCount() > 1) return; // handled by zoom gesture
            requestFocus();
            TouchPoint tp = e.getTouchPoint();
            // If a drawing tool is active, delegate to the drawing engine
            if (drawingEngine.isDrawingMode()) {
                // Simulate a mouse-press at this touch point so the drawing engine works normally
                drawingEngine.setSnapEnabled(false);
                // We only capture the start position for pan fallback; drawing engine handles draw
                touchPanStartX   = tp.getX();
                touchPanStartBar = startBarIndex;
                e.consume();
                return;
            }
            touchPanStartX   = tp.getX();
            touchPanStartBar = startBarIndex;
            e.consume();
        });

        setOnTouchMoved(e -> {
            if (touchZooming || e.getTouchCount() > 1) return;
            if (bars == null || bars.isEmpty()) return;
            TouchPoint tp = e.getTouchPoint();

            // If drawing tool is active, do NOT pan — let the drawing engine handle it via mouse events
            if (drawingEngine.isDrawingMode()) {
                e.consume();
                return;
            }

            double dx  = (tp.getX() - touchPanStartX) * panSensitivity;
            double barW = (getWidth() - PADDING_LEFT - PADDING_RIGHT) / Math.max(1, visibleBars);
            int shift = (int)(dx / barW);
            // Fix 2: Allow panning into extended empty space on both sides
            int singleTouchPanMargin = Math.max(10, (int)(bars.size() * PAN_MARGIN_FRAC));
            startBarIndex = Math.max(-singleTouchPanMargin,
                    Math.min(bars.size() - visibleBars + singleTouchPanMargin, touchPanStartBar - shift));
            mouseX = tp.getX(); mouseY = tp.getY();
            render();
            e.consume();
        });

        setOnTouchReleased(e -> {
            e.consume();
        });
    }

    private void setupKeyHandlers() {
        // Single, authoritative key-pressed handler for all chart keyboard shortcuts.
        // Uses setOnKeyPressed (not addEventHandler) because ChartController adds
        // additional shortcuts via addEventHandler which does NOT override this one.
        setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case SHIFT -> {
                    snapMode = true;
                    drawingEngine.setSnapEnabled(true);
                }
                case Z -> {
                    if (e.isControlDown() || e.isMetaDown()) {
                        if (e.isShiftDown()) {
                            // Ctrl+Shift+Z = Redo (macOS convention)
                            drawingEngine.redo();
                        } else {
                            // Ctrl+Z = Undo (Windows/Linux/macOS)
                            drawingEngine.undo();
                        }
                        e.consume();
                    }
                }
                case Y -> {
                    // Ctrl+Y = Redo (Windows/Linux)
                    if (e.isControlDown() || e.isMetaDown()) {
                        drawingEngine.redo();
                        e.consume();
                    }
                }
                case T -> {
                    // Ctrl+Shift+T = Instant save trade from selected position drawing
                    if ((e.isControlDown() || e.isMetaDown()) && e.isShiftDown()) {
                        drawingEngine.instantSaveSelectedPosition();
                        e.consume();
                    }
                }
                case DELETE, BACK_SPACE -> {
                    // Delete key = delete selected drawing (when chart has focus)
                    if (drawingEngine.getSelected() != null
                            && !drawingEngine.getSelected().isLocked()) {
                        drawingEngine.deleteSelected();
                        e.consume();
                    }
                }
                default -> {}
            }
        });
        setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.SHIFT) {
                snapMode = false;
                drawingEngine.setSnapEnabled(false);
            }
        });
    }

    // ── Touch Handlers (T-1 … T-4) ───────────────────────────────────────────

    /**
     * Wires all touch-screen gestures to the chart canvas:
     * <ul>
     *   <li>T-1  Pinch-to-zoom — two-finger spread/pinch adjusts {@code visibleBars}.</li>
     *   <li>T-2  Two-finger pan — drag with two fingers to pan the chart.</li>
     *   <li>T-3  One-finger drag in Select mode — delegates to {@link ChartDrawingEngine}.</li>
     *   <li>T-4  Axis swipe — left/right swipes pan the time axis independently.</li>
     * </ul>
     */
    private void setupTouchHandlers2() {
        // ── T-1 / T-2: track active touch count via TOUCH_PRESSED / RELEASED ──
        setOnTouchPressed(e -> {
            activeTouchCount = e.getTouchCount();
            if (activeTouchCount == 2) {
                // Record initial two-finger state for both pinch and pan
                TouchPoint t0 = e.getTouchPoints().get(0);
                TouchPoint t1 = e.getTouchPoints().get(1);
                double dx = t1.getX() - t0.getX();
                double dy = t1.getY() - t0.getY();
                pinchStartDist    = Math.sqrt(dx * dx + dy * dy);
                pinchStartVisible = visibleBars;
                touchMidX         = (t0.getX() + t1.getX()) / 2.0;
                touchMidY         = (t0.getY() + t1.getY()) / 2.0;
                twoFingerPanStartBar = startBarIndex;
                twoFingerPanStartX   = touchMidX;
            } else if (activeTouchCount == 1) {
                // Treat single-finger press like a mouse press for drawing interaction
                TouchPoint t = e.getTouchPoints().get(0);
                dragStartX   = t.getX();
                dragStartBar = startBarIndex;
            }
            e.consume();
        });

        setOnTouchReleased(e -> {
            activeTouchCount = e.getTouchCount();
            if (activeTouchCount == 0) {
                pinchStartDist = 0;
            }
            e.consume();
        });

        setOnTouchMoved(e -> {
            activeTouchCount = e.getTouchCount();

            if (activeTouchCount == 2) {
                TouchPoint t0 = e.getTouchPoints().get(0);
                TouchPoint t1 = e.getTouchPoints().get(1);

                // ── T-1: Pinch-to-zoom ────────────────────────────────
                double dx      = t1.getX() - t0.getX();
                double dy      = t1.getY() - t0.getY();
                double curDist = Math.sqrt(dx * dx + dy * dy);
                if (pinchStartDist > 0 && curDist > 0) {
                    double scale = pinchStartDist / curDist; // >1 = zoom in (fingers apart)
                    int newVisible = (int) Math.round(pinchStartVisible * scale);
                    if (bars != null) {
                        newVisible = Math.max(5, Math.min(bars.size(), newVisible));
                    } else {
                        newVisible = Math.max(5, newVisible);
                    }
                    // Keep midpoint anchor stationary while zooming
                    double midFraction = (bars != null && bars.size() > 0)
                            ? Math.max(0.0, Math.min(1.0,
                                (touchMidX - PADDING_LEFT) / (getWidth() - PADDING_LEFT - PADDING_RIGHT)))
                            : 0.5;
                    int anchor = startBarIndex + (int) Math.round(midFraction * visibleBars);
                    visibleBars = newVisible;
                    if (bars != null) {
                        if (visibleBars >= bars.size()) {
                            startBarIndex = 0;
                        } else {
                            int newStart = anchor - (int) Math.round(midFraction * visibleBars);
                            startBarIndex = Math.max(0, Math.min(bars.size() - visibleBars, newStart));
                        }
                    }
                }

                // ── T-2: Two-finger pan ───────────────────────────────
                double newMidX = (t0.getX() + t1.getX()) / 2.0;
                if (bars != null && !bars.isEmpty()) {
                    double plotW = getWidth() - PADDING_LEFT - PADDING_RIGHT;
                    double barW  = plotW / Math.max(1, visibleBars);
                    int    shift = (int) ((newMidX - twoFingerPanStartX) / barW);
                    // Fix 2: Allow panning into extended empty space on both sides
                    int twoFingPanMargin = Math.max(10, (int)(bars.size() * PAN_MARGIN_FRAC));
                    startBarIndex = Math.max(-twoFingPanMargin,
                            Math.min(bars.size() - visibleBars + twoFingPanMargin,
                                    twoFingerPanStartBar - shift));
                }

                mouseX = newMidX;
                mouseY = (t0.getY() + t1.getY()) / 2.0;
                render();

            } else if (activeTouchCount == 1) {
                // ── T-3: Single-finger drag (drawing move or chart pan) ──
                TouchPoint t = e.getTouchPoints().get(0);
                if (drawingEngine.getActiveTool() == ChartDrawingToolType.SELECT) {
                    // Simulate mouse drag for drawing engine
                    MouseEvent syntheticDrag = new MouseEvent(
                            MouseEvent.MOUSE_DRAGGED,
                            t.getX(), t.getY(), t.getSceneX(), t.getScreenY(),
                            javafx.scene.input.MouseButton.PRIMARY, 1,
                            false, false, false, false,
                            true, false, false, false, false, false, null);
                    boolean handled = drawingEngine.handleMouseDragged(syntheticDrag, lastRenderContext);
                    if (!handled) {
                        // Pan the chart with a single finger when not dragging a drawing
                        onSingleTouchDrag(t.getX());
                    }
                } else {
                    onSingleTouchDrag(t.getX());
                }
                mouseX = t.getX();
                mouseY = t.getY();
                showCrosshair = true;
                render();
            }
            e.consume();
        });

        // ── T-4: Axis swipe — JavaFX SwipeEvent for quick flings ──
        setOnSwipeLeft(e -> {
            if (bars == null || bars.isEmpty()) return;
            int shift = Math.max(1, visibleBars / 5);
            int swipeMargin = Math.max(10, (int)(bars.size() * PAN_MARGIN_FRAC));
            startBarIndex = Math.min(bars.size() - visibleBars + swipeMargin, startBarIndex + shift);
            render();
            e.consume();
        });
        setOnSwipeRight(e -> {
            if (bars == null || bars.isEmpty()) return;
            int shift = Math.max(1, visibleBars / 5);
            int swipeMargin = Math.max(10, (int)(bars.size() * PAN_MARGIN_FRAC));
            startBarIndex = Math.max(-swipeMargin, startBarIndex - shift);
            render();
            e.consume();
        });

        // JavaFX built-in ZoomEvent — fires on trackpad pinch gestures (macOS / Windows Precision)
        setOnZoom(e -> {
            if (bars == null || bars.isEmpty()) return;
            double factor  = e.getZoomFactor(); // >1 = zoom in
            int    newVis  = (int) Math.round(visibleBars / factor);
            newVis = Math.max(5, Math.min(bars.size(), newVis));
            double plotW   = getWidth() - PADDING_LEFT - PADDING_RIGHT;
            double relX    = e.getX() - PADDING_LEFT;
            double frac    = (plotW > 0) ? Math.max(0, Math.min(1, relX / plotW)) : 0.5;
            int    anchor  = startBarIndex + (int) Math.round(frac * visibleBars);
            visibleBars    = newVis;
            if (visibleBars >= bars.size()) {
                startBarIndex = 0;
            } else {
                int newStart = anchor - (int) Math.round(frac * visibleBars);
                startBarIndex = Math.max(0, Math.min(bars.size() - visibleBars, newStart));
            }
            render();
            e.consume();
        });
    }

    /** Pans the chart from a single touch drag (used when not dragging a drawing). */
    private void onSingleTouchDrag(double currentX) {
        if (bars == null || bars.isEmpty()) return;
        double plotW = getWidth() - PADDING_LEFT - PADDING_RIGHT;
        double barW  = plotW / Math.max(1, visibleBars);
        int    shift = (int) ((currentX - dragStartX) / barW);
        // Fix 2: Allow panning into empty space beyond data bounds
        int touchPanMargin = Math.max(10, (int)(bars.size() * PAN_MARGIN_FRAC));
        startBarIndex = Math.max(-touchPanMargin,
                Math.min(bars.size() - visibleBars + touchPanMargin, dragStartBar - shift));
    }

    private void onMouseDragged(MouseEvent e) {
        if (bars == null || bars.isEmpty()) return;

        // ── Horizontal pan (time axis) ────────────────────────
        double dx   = (e.getX() - dragStartX) * panSensitivity;
        double barW = (getWidth() - PADDING_LEFT - PADDING_RIGHT) / Math.max(1, visibleBars);
        int shift   = (int)(dx / barW);
        // Fix 2: Allow panning into empty space beyond data bounds
        int panMargin2 = Math.max(10, (int)(bars.size() * PAN_MARGIN_FRAC));
        startBarIndex = Math.max(-panMargin2,
                Math.min(bars.size() - visibleBars + panMargin2, dragStartBar - shift));

        // ── Vertical pan (price axis) ─────────────────────────
        // Issue #5: Fix inverted vertical panning.
        // Natural behaviour (matching TradingView):
        //   drag UP   (dy < 0) → chart content moves UP → shows HIGHER prices
        //   drag DOWN (dy > 0) → chart content moves DOWN → shows LOWER prices
        // In the renderer:  maxPrice -= shift; minPrice -= shift;
        //   positive shift → prices decrease → content moves DOWN (shows lower prices)
        //   negative shift → prices increase → content moves UP (shows higher prices)
        // Therefore: drag UP (dy < 0) must produce a NEGATIVE priceOffsetPct,
        //            drag DOWN (dy > 0) must produce a POSITIVE priceOffsetPct.
        // This is the NATURAL mapping (dy / plotH), so we keep the sign.
        // Previously the comment was wrong — the implementation was actually correct
        // but the rendering applied it with inverted sign. The fix is to negate the
        // priceOffsetPct when applying it in the render pass (or negate here).
        // We negate here to keep the render logic untouched.
        double plotH = getHeight() - PADDING_TOP - PADDING_BOTTOM;
        if (plotH > 0) {
            double dy = e.getY() - dragStartY;
            // Negate dy so that dragging UP raises the chart (natural direction)
            double deltaFraction = -dy / plotH;
            priceOffsetPct = dragStartPriceOffset + deltaFraction;
            // Clamp so the chart cannot be panned too far off-screen (± 80% of range)
            priceOffsetPct = Math.max(-0.8, Math.min(0.8, priceOffsetPct));
        }

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
        // Scale the zoom step by zoomSensitivity so the slider controls scroll zoom too.
        double zoomStep = 0.12 * zoomSensitivity;   // at sensitivity=1.0 → 12% change; at 0.4 → ~5%
        if (delta < 0) {
            // Zoom OUT — always allow, expand visible range
            visibleBars = Math.min(bars.size(), (int) Math.ceil(visibleBars * (1.0 + zoomStep)));
        } else {
            // Zoom IN — limit to minimum 5 bars
            visibleBars = Math.max(5, (int) Math.floor(visibleBars * (1.0 - zoomStep)));
        }

        // Final clamp: visibleBars must be in [1, bars.size()]
        visibleBars = Math.max(1, Math.min(visibleBars, bars.size()));

        // Recalculate startBarIndex so that the anchor bar stays under the mouse.
        // When zooming out fully (visibleBars == bars.size()), force startBarIndex = 0.
        // Fix 2: Preserve extended pan range after zoom
        int scrollPanMargin = Math.max(10, (int)(bars.size() * PAN_MARGIN_FRAC));
        if (visibleBars >= bars.size()) {
            startBarIndex = 0;
        } else {
            int newStart = anchor - (int) Math.round(fraction * visibleBars);
            startBarIndex = Math.max(-scrollPanMargin,
                    Math.min(bars.size() - visibleBars + scrollPanMargin, newStart));
        }
        render();
    }

    // ── Line series helpers ───────────────────────────────────

    /**
     * Draws a line series from a list that covers the FULL bar history.
     * {@code start} is the absolute index of the first visible bar (may be negative when
     * panning into empty space left of the data — Fix 2).
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
        double barW = l.plotWidth() / Math.max(1, n);
        for (int i = 0; i < n; i++) {
            int idx = start + i;
            if (idx < 0) continue;             // Fix 2: skip empty space left of data
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
