package com.mst.matt.tradingplatformapp.ui.chart;

import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.service.analysis.IndicatorResult;
import com.mst.matt.tradingplatformapp.service.analysis.IchimokuResult;
import com.mst.matt.tradingplatformapp.service.analysis.SupportResistanceService.SRResult;
import com.mst.matt.tradingplatformapp.service.analysis.SupportResistanceService.SRLevel;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Professional candlestick chart rendered on a JavaFX Canvas.
 *
 * Features:
 *   ✅ OHLC candlestick bodies and wicks
 *   ✅ Volume bars at bottom (30% height)
 *   ✅ EMA Fast / Slow overlays
 *   ✅ EMA 50 / 200 (Golden/Death Cross)
 *   ✅ Bollinger Bands (upper, middle, lower with cloud fill)
 *   ✅ Ichimoku Cloud (Kumo fill, Tenkan, Kijun lines)
 *   ✅ Support & Resistance horizontal lines (Fibonacci + Pivot)
 *   ✅ Mouse crosshair with OHLCV tooltip
 *   ✅ Scroll to zoom, drag to pan
 *   ✅ Toggleable indicator overlays
 */
public class CandlestickChartCanvas extends Canvas {

    // ── Layout constants ─────────────────────────────────────
    private static final double PRICE_AREA_HEIGHT_PCT  = 0.68;
    private static final double VOLUME_AREA_HEIGHT_PCT = 0.14;
    private static final double MACD_AREA_HEIGHT_PCT   = 0.09;
    private static final double RSI_AREA_HEIGHT_PCT    = 0.09;
    private static final double PADDING_LEFT   = 60;
    private static final double PADDING_RIGHT  = 80;  // price labels
    private static final double PADDING_TOP    = 20;
    private static final double PADDING_BOTTOM = 24;

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
    private static final Color EMA_FAST   = Color.web("#388bfd");
    private static final Color EMA_SLOW   = Color.web("#bc8cff");
    private static final Color EMA_50     = Color.web("#e3b341");
    private static final Color EMA_200    = Color.web("#f85149");
    private static final Color BB_UPPER   = Color.web("#388bfd88");
    private static final Color BB_LOWER   = Color.web("#388bfd88");
    private static final Color BB_FILL    = Color.web("#388bfd15");
    private static final Color ICH_TENKAN = Color.web("#f85149");
    private static final Color ICH_KIJUN  = Color.web("#388bfd");
    private static final Color ICH_BULL   = Color.web("#3fb95030");
    private static final Color ICH_BEAR   = Color.web("#f8514930");
    private static final Color CROSS      = Color.web("#e6edf355");
    private static final Color FIB_LINE   = Color.web("#d2992299");
    private static final Color PIVOT_LINE = Color.web("#bc8cff99");
    private static final Color SR_SUP     = Color.web("#3fb95066");
    private static final Color SR_RES     = Color.web("#f8514966");

    // ── Data ──────────────────────────────────────────────────
    private List<OhlcvBar>    bars;
    private IndicatorResult   indicators;
    private SRResult          srResult;

    // ── View state ────────────────────────────────────────────
    private int   visibleBars   = 100;
    private int   startBarIndex = 0;
    private double candleWidth  = 8.0;
    private double mouseX, mouseY;
    private boolean showCrosshair    = false;
    private double  dragStartX;
    private int     dragStartBar;

    // ── Overlay toggles ───────────────────────────────────────
    private boolean analysisMode    = false;
    private boolean showEma         = false;
    private boolean showBollinger   = false;
    private boolean showIchimoku    = false;
    private boolean showSR          = false;
    private boolean showVolume      = true;
    private boolean showMacd        = false;
    private boolean showRsi         = false;
    private double  lastPrice       = Double.NaN;

    private static final Font FONT_SMALL  =
            Font.font("Segoe UI", 11);
    private static final Font FONT_MEDIUM =
            Font.font("Segoe UI", 12);
    private static final Font FONT_BOLD   =
            Font.font("Segoe UI", 13);
    private static final DateTimeFormatter DTF =
            DateTimeFormatter.ofPattern("MM/dd HH:mm");

    public CandlestickChartCanvas() {
        super(800, 600);
        setFocusTraversable(true);
        setupMouseHandlers();
        // Wire resize so the chart re-renders whenever the parent Pane changes size
        widthProperty().addListener((o, a, b) -> render());
        heightProperty().addListener((o, a, b) -> render());
    }

    public CandlestickChartCanvas(double width, double height) {
        super(width, height);
        setFocusTraversable(true);
        setupMouseHandlers();

        // Resize listener
        widthProperty().addListener((o,a,b) -> render());
        heightProperty().addListener((o,a,b) -> render());
    }

    // ── Public API ────────────────────────────────────────────

    public void setData(List<OhlcvBar> bars,
                        IndicatorResult indicators,
                        SRResult srResult) {
        this.bars       = bars;
        this.indicators = indicators;
        this.srResult   = srResult;

        if (bars != null && !bars.isEmpty()) {
            visibleBars   = Math.min(100, bars.size());
            startBarIndex = Math.max(0, bars.size() - visibleBars);
        }
        render();
    }

    public void setLastPrice(double price) { this.lastPrice = price; render(); }
    public double getLastPrice() { return lastPrice; }

    public void setAnalysisMode(boolean analysisMode) {
        this.analysisMode = analysisMode;
        render();
    }

    public void setOverlaysEnabled(boolean enabled) {
        showEma = showBollinger = showIchimoku = showSR = showMacd = showRsi = enabled;
        render();
    }

    public void setShowEma(boolean v)       { showEma = v; render(); }
    public void setShowBollinger(boolean v) { showBollinger = v; render(); }
    public void setShowIchimoku(boolean v)  { showIchimoku = v; render(); }
    public void setShowSR(boolean v)        { showSR = v; render(); }
    public void setShowVolume(boolean v)    { showVolume = v; render(); }
    public void setShowMacd(boolean v)      { showMacd = v; render(); }
    public void setShowRsi(boolean v)       { showRsi = v; render(); }

    public void toggleEma()       { showEma       = !showEma;       render(); }
    public void toggleBollinger() { showBollinger = !showBollinger; render(); }
    public void toggleIchimoku()  { showIchimoku  = !showIchimoku;  render(); }
    public void toggleSR()        { showSR        = !showSR;        render(); }
    public void toggleVolume()    { showVolume    = !showVolume;     render(); }
    public void toggleMacd()      { showMacd      = !showMacd;       render(); }
    public void toggleRsi()       { showRsi       = !showRsi;        render(); }

    // ── Main Render ───────────────────────────────────────────

    public void render() {
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();

        if (w <= 0 || h <= 0 || bars == null || bars.isEmpty()) {
            drawEmpty(gc, w, h);
            return;
        }

        // Clamp the visible window before slicing the backing list.
        visibleBars = Math.max(1, Math.min(visibleBars, bars.size()));
        startBarIndex = Math.max(0,
                Math.min(startBarIndex, bars.size() - visibleBars));
        int endBarIndex = Math.min(bars.size() - 1,
                startBarIndex + visibleBars - 1);
        List<OhlcvBar> visible = bars.subList(startBarIndex, endBarIndex + 1);

        // Compute candle width
        double plotW = w - PADDING_LEFT - PADDING_RIGHT;
        candleWidth  = Math.max(2.0, plotW / visible.size() - 1.0);

        // Layout rectangles
        double priceH  = h * (showMacd || showRsi
                ? PRICE_AREA_HEIGHT_PCT : 0.80);
        double volH    = showVolume ? h * VOLUME_AREA_HEIGHT_PCT : 0;
        double macdH   = showMacd  ? h * MACD_AREA_HEIGHT_PCT   : 0;
        double rsiH    = showRsi   ? h * RSI_AREA_HEIGHT_PCT     : 0;

        ChartLayout layout = new ChartLayout(
                PADDING_LEFT, PADDING_TOP, w - PADDING_RIGHT, h - PADDING_BOTTOM,
                priceH, volH, macdH, rsiH);

        // Find price range for visible bars
        double maxPrice = visible.stream()
                .mapToDouble(b -> b.getHigh().doubleValue()).max().orElse(1);
        double minPrice = visible.stream()
                .mapToDouble(b -> b.getLow().doubleValue()).min().orElse(0);

        // Extend range for indicator lines
        if (showBollinger && indicators != null) {
            if (!indicators.getBbUpperSeries().isEmpty()) {
                maxPrice = Math.max(maxPrice, getVisibleMax(
                        indicators.getBbUpperSeries(), startBarIndex, endBarIndex));
            }
            if (!indicators.getBbLowerSeries().isEmpty()) {
                minPrice = Math.min(minPrice, getVisibleMin(
                        indicators.getBbLowerSeries(), startBarIndex, endBarIndex));
            }
        }

        double pricePad = (maxPrice - minPrice) * 0.05;
        maxPrice += pricePad;
        minPrice -= pricePad;

        // Max volume
        double maxVolume = visible.stream()
                .mapToDouble(b -> b.getVolume().doubleValue()).max().orElse(1);

        // ══ DRAW ══════════════════════════════════════════════

        // 1. Background
        gc.setFill(BG);
        gc.fillRect(0, 0, w, h);

        // 2. Grid
        drawGrid(gc, layout, maxPrice, minPrice, visible.size());

        // 3. Indicator overlays (chart + analysis views)
        if (showIchimoku && indicators != null)
            drawIchimokuCloud(gc, layout, maxPrice, minPrice,
                    startBarIndex, endBarIndex, visible.size());

        if (showBollinger && indicators != null)
            drawBollingerFill(gc, layout, maxPrice, minPrice,
                    startBarIndex, endBarIndex, visible.size());

        if (showSR && srResult != null)
            drawSupportResistance(gc, layout, maxPrice, minPrice, w);

        if (showEma && indicators != null)
            drawEmaLines(gc, layout, maxPrice, minPrice,
                    startBarIndex, endBarIndex, visible.size());

        if (showBollinger && indicators != null)
            drawBollingerLines(gc, layout, maxPrice, minPrice,
                    startBarIndex, endBarIndex, visible.size());

        if (showIchimoku && indicators != null)
            drawIchimokuLines(gc, layout, maxPrice, minPrice,
                    startBarIndex, endBarIndex, visible.size());

        // 9. Candles
        drawCandles(gc, layout, visible, maxPrice, minPrice, startBarIndex);

        // 9b. Last price — dotted red line from last candle close
        drawLastPriceLine(gc, layout, maxPrice, minPrice, visible);

        // 10. Volume bars
        if (showVolume)
            drawVolume(gc, layout, visible, maxVolume);

        if (showMacd && indicators != null)
            drawMacd(gc, layout, startBarIndex, endBarIndex, visible.size());

        if (showRsi && indicators != null)
            drawRsi(gc, layout, startBarIndex, endBarIndex, visible.size());

        // 13. Price axis labels
        drawPriceAxis(gc, layout, maxPrice, minPrice);

        // 14. Time axis labels
        drawTimeAxis(gc, layout, visible, w);

        // 15. Separator lines
        drawSeparators(gc, layout, w);

        // 16. Crosshair + tooltip
        if (showCrosshair)
            drawCrosshair(gc, layout, visible, maxPrice, minPrice, w);

        // 17. Legend
        drawLegend(gc, layout);
    }

    // ── Draw: Grid ────────────────────────────────────────────

    private void drawGrid(GraphicsContext gc, ChartLayout l,
                          double maxP, double minP, int barCount) {
        gc.setStroke(GRID);
        gc.setLineWidth(0.5);
        int gridLines = 6;
        for (int i = 0; i <= gridLines; i++) {
            double y = l.priceTop() + (l.priceHeight * i / gridLines);
            gc.strokeLine(l.left, y, l.right, y);
        }
        // Vertical grid every N bars
        int step = Math.max(1, barCount / 8);
        for (int i = 0; i < barCount; i += step) {
            double x = l.left + (i + 0.5) * (l.plotWidth() / barCount);
            gc.strokeLine(x, l.priceTop(), x, l.priceBottom());
        }
    }

    // ── Draw: Candles ─────────────────────────────────────────

    private void drawCandles(GraphicsContext gc, ChartLayout l,
                             List<OhlcvBar> bars, double maxP, double minP,
                             int startIdx) {
        int n = bars.size();
        double barW = l.plotWidth() / n;
        double bodyW = Math.max(1.5, barW * 0.65);
        double wickW = Math.max(1.0, bodyW * 0.2);

        for (int i = 0; i < n; i++) {
            OhlcvBar bar = bars.get(i);
            double o = bar.getOpen().doubleValue();
            double h = bar.getHigh().doubleValue();
            double lo = bar.getLow().doubleValue();
            double c = bar.getClose().doubleValue();
            boolean bull = c >= o;

            double cx = l.left + (i + 0.5) * barW;
            double topWick    = priceToY(h,  maxP, minP, l);
            double bottomWick = priceToY(lo, maxP, minP, l);
            double bodyTop    = priceToY(Math.max(o,c), maxP, minP, l);
            double bodyBot    = priceToY(Math.min(o,c), maxP, minP, l);
            double bodyH      = Math.max(1.5, bodyBot - bodyTop);

            // Wick
            gc.setStroke(bull ? BULL_WICK : BEAR_WICK);
            gc.setLineWidth(wickW);
            gc.strokeLine(cx, topWick, cx, bottomWick);

            // Body
            gc.setFill(bull ? BULL_BODY : BEAR_BODY);
            gc.fillRect(cx - bodyW/2, bodyTop, bodyW, bodyH);

            // Outline for doji candles (open ≈ close)
            if (bodyH <= 2) {
                gc.setStroke(bull ? BULL_BODY : BEAR_BODY);
                gc.setLineWidth(1);
                gc.strokeLine(cx - bodyW/2, bodyTop, cx + bodyW/2, bodyTop);
            }
        }
    }

    /** Dotted red horizontal line at last traded / close price. */
    private void drawLastPriceLine(GraphicsContext gc, ChartLayout l,
                                   double maxP, double minP, List<OhlcvBar> visible) {
        double price = lastPrice;
        if (Double.isNaN(price) && visible != null && !visible.isEmpty()) {
            price = visible.getLast().getClose().doubleValue();
        }
        if (Double.isNaN(price)) return;

        double y = priceToY(price, maxP, minP, l);
        gc.setStroke(Color.web("#f85149"));
        gc.setLineWidth(1.2);
        gc.setLineDashes(6, 6);
        gc.strokeLine(l.left, y, l.right, y);
        gc.setLineDashes();

        gc.setFill(Color.web("#f85149"));
        gc.setFont(FONT_SMALL);
        gc.setTextAlign(TextAlignment.RIGHT);
        gc.fillText(String.format("%.4f", price), l.right + 72, y + 4);
    }

    // ── Draw: Volume ──────────────────────────────────────────

    private void drawVolume(GraphicsContext gc, ChartLayout l,
                            List<OhlcvBar> bars, double maxVol) {
        if (l.volHeight <= 0) return;
        int n = bars.size();
        double barW = l.plotWidth() / n;
        double volBarW = Math.max(1.5, barW * 0.7);

        for (int i = 0; i < n; i++) {
            OhlcvBar bar = bars.get(i);
            double vol  = bar.getVolume().doubleValue();
            boolean bull = bar.getClose().compareTo(bar.getOpen()) >= 0;
            double barH = (vol / maxVol) * l.volHeight;
            double x = l.left + (i + 0.5) * barW - volBarW / 2;
            double y = l.volTop() + l.volHeight - barH;
            gc.setFill(bull ? VOL_BULL : VOL_BEAR);
            gc.fillRect(x, y, volBarW, barH);
        }
    }

    // ── Draw: EMA Lines ───────────────────────────────────────

    private void drawEmaLines(GraphicsContext gc, ChartLayout l,
                              double maxP, double minP,
                              int start, int end, int n) {
        if (indicators.getEmaFastSeries() != null)
            drawLineSeries(gc, l, indicators.getEmaFastSeries(),
                    start, end, n, maxP, minP, EMA_FAST, 1.5);
        if (indicators.getEmaSlowSeries() != null)
            drawLineSeries(gc, l, indicators.getEmaSlowSeries(),
                    start, end, n, maxP, minP, EMA_SLOW, 1.5);
        if (indicators.getEma50Series() != null)
            drawLineSeries(gc, l, indicators.getEma50Series(),
                    start, end, n, maxP, minP, EMA_50, 1.0);
        if (indicators.getEma200Series() != null)
            drawLineSeries(gc, l, indicators.getEma200Series(),
                    start, end, n, maxP, minP, EMA_200, 1.0);
    }

    // ── Draw: Bollinger Bands ─────────────────────────────────

    private void drawBollingerFill(GraphicsContext gc, ChartLayout l,
                                   double maxP, double minP,
                                   int start, int end, int n) {
        List<Double> upper = indicators.getBbUpperSeries();
        List<Double> lower = indicators.getBbLowerSeries();
        if (upper == null || lower == null || upper.size() < end) return;

        double barW = l.plotWidth() / n;
        gc.setFill(BB_FILL);
        gc.beginPath();
        boolean started = false;
        for (int i = 0; i < n; i++) {
            int idx = start + i;
            if (idx >= upper.size()) break;
            double x = l.left + (i + 0.5) * barW;
            double y = priceToY(upper.get(idx), maxP, minP, l);
            if (!started) { gc.moveTo(x, y); started = true; }
            else gc.lineTo(x, y);
        }
        for (int i = n - 1; i >= 0; i--) {
            int idx = start + i;
            if (idx >= lower.size()) continue;
            double x = l.left + (i + 0.5) * barW;
            double y = priceToY(lower.get(idx), maxP, minP, l);
            gc.lineTo(x, y);
        }
        gc.closePath();
        gc.fill();
    }

    private void drawBollingerLines(GraphicsContext gc, ChartLayout l,
                                    double maxP, double minP,
                                    int start, int end, int n) {
        drawLineSeries(gc, l, indicators.getBbUpperSeries(),
                start, end, n, maxP, minP, BB_UPPER, 1.0);
        drawLineSeries(gc, l, indicators.getBbMiddleSeries(),
                start, end, n, maxP, minP, Color.web("#388bfd55"), 0.8);
        drawLineSeries(gc, l, indicators.getBbLowerSeries(),
                start, end, n, maxP, minP, BB_LOWER, 1.0);
    }

    // ── Draw: Ichimoku Cloud ──────────────────────────────────

    private void drawIchimokuCloud(GraphicsContext gc, ChartLayout l,
                                   double maxP, double minP,
                                   int start, int end, int n) {
        IchimokuResult ich = indicators.getIchimoku();
        if (ich == null) return;

        List<Double> spanA = ich.getSpanASeries();
        List<Double> spanB = ich.getSpanBSeries();
        if (spanA == null || spanB == null) return;

        double barW = l.plotWidth() / n;

        // Draw cloud fill (bull = green, bear = red)
        for (int i = 0; i < n - 1; i++) {
            int idx = start + i;
            if (idx >= spanA.size() || idx >= spanB.size()) break;

            double aVal = spanA.get(idx);
            double bVal = spanB.get(idx);
            boolean bullCloud = aVal >= bVal;

            double x1 = l.left + (i + 0.5) * barW;
            double x2 = l.left + (i + 1.5) * barW;
            double ay1 = priceToY(aVal, maxP, minP, l);
            double by1 = priceToY(bVal, maxP, minP, l);

            double cloudTop    = Math.min(ay1, by1);
            double cloudBottom = Math.max(ay1, by1);

            gc.setFill(bullCloud ? ICH_BULL : ICH_BEAR);
            gc.fillRect(x1, cloudTop, x2 - x1, cloudBottom - cloudTop);
        }

        // Tenkan-sen and Kijun-sen lines
        drawLineSeries(gc, l, ich.getTenkanSeries(),
                start, end, n, maxP, minP, ICH_TENKAN, 1.5);
        drawLineSeries(gc, l, ich.getKijunSeries(),
                start, end, n, maxP, minP, ICH_KIJUN, 1.5);
    }

    private void drawIchimokuLines(GraphicsContext gc, ChartLayout l,
                                   double maxP, double minP,
                                   int start, int end, int n) {
        // Already drawn in drawIchimokuCloud for Tenkan/Kijun
        // Span A and B borders
        IchimokuResult ich = indicators.getIchimoku();
        if (ich == null) return;
        drawLineSeries(gc, l, ich.getSpanASeries(),
                start, end, n, maxP, minP, Color.web("#3fb95088"), 1.0);
        drawLineSeries(gc, l, ich.getSpanBSeries(),
                start, end, n, maxP, minP, Color.web("#f8514988"), 1.0);
    }

    // ── Draw: Support & Resistance ────────────────────────────

    private void drawSupportResistance(GraphicsContext gc, ChartLayout l,
                                       double maxP, double minP, double w) {
        // Supports
        for (SRLevel sr : srResult.getSupports()) {
            double price = sr.price();
            if (price < minP || price > maxP) continue;
            double y = priceToY(price, maxP, minP, l);
            gc.setStroke(colorForSr(sr));
            gc.setLineWidth(1.0);
            gc.setLineDashes(6, 4);
            gc.strokeLine(l.left, y, l.right, y);
            gc.setLineDashes(null);

            gc.setFill(SR_SUP);
            gc.setFont(FONT_SMALL);
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(sr.label() + " " + fmtPrice(price), l.right - 2, y - 2);
        }

        // Resistances
        for (SRLevel sr : srResult.getResistances()) {
            double price = sr.price();
            if (price < minP || price > maxP) continue;
            double y = priceToY(price, maxP, minP, l);
            gc.setStroke(colorForSr(sr));
            gc.setLineWidth(1.0);
            gc.setLineDashes(6, 4);
            gc.strokeLine(l.left, y, l.right, y);
            gc.setLineDashes(null);

            gc.setFill(SR_RES);
            gc.setFont(FONT_SMALL);
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(sr.label() + " " + fmtPrice(price), l.right - 2, y - 2);
        }
    }

    // ── Draw: MACD Sub-Chart ─────────────────────────────────

    private void drawMacd(GraphicsContext gc, ChartLayout l,
                          int start, int end, int n) {
        if (l.macdHeight <= 0) return;
        List<Double> macdLine = indicators.getMacdLineSeries();
        List<Double> sigLine  = indicators.getMacdSignalSeries();
        List<Double> hist     = indicators.getMacdHistogramSeries();
        if (macdLine == null || hist == null) return;

        // Background
        gc.setFill(Color.web("#161b22"));
        gc.fillRect(l.left, l.macdTop(), l.plotWidth(), l.macdHeight);

        // MACD label
        gc.setFill(TEXT_DIM);
        gc.setFont(FONT_SMALL);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("MACD", l.left + 4, l.macdTop() + 12);

        // Find range
        double maxM = 0, minM = 0;
        for (int i = start; i <= end && i < hist.size(); i++) {
            if (!Double.isNaN(hist.get(i))) {
                maxM = Math.max(maxM, hist.get(i));
                minM = Math.min(minM, hist.get(i));
            }
        }
        double rangeM = Math.max(Math.abs(maxM - minM), 0.0001);
        double zeroY  = l.macdTop() + l.macdHeight * (maxM / rangeM);

        double barW = l.plotWidth() / n;

        // Histogram bars
        for (int i = 0; i < n; i++) {
            int idx = start + i;
            if (idx >= hist.size()) break;
            double val = hist.get(idx);
            if (Double.isNaN(val)) continue;
            double x  = l.left + i * barW;
            double y  = l.macdTop() + l.macdHeight * ((maxM - val) / rangeM);
            double bh = Math.abs(y - zeroY);
            gc.setFill(val >= 0 ? Color.web("#3fb95099") : Color.web("#f8514999"));
            gc.fillRect(x, Math.min(y, zeroY), barW * 0.8, Math.max(1, bh));
        }

        // MACD and signal lines
        drawSubLineSeries(gc, macdLine, start, end, n, maxM, minM, l.macdTop(),
                l.macdHeight, Color.web("#388bfd"), 1.5);
        if (sigLine != null)
            drawSubLineSeries(gc, sigLine, start, end, n, maxM, minM, l.macdTop(),
                    l.macdHeight, Color.web("#f85149"), 1.0);
    }

    // ── Draw: RSI Sub-Chart ───────────────────────────────────

    private void drawRsi(GraphicsContext gc, ChartLayout l,
                         int start, int end, int n) {
        if (l.rsiHeight <= 0) return;
        List<Double> rsiSeries = indicators.getRsiSeries();
        if (rsiSeries == null) return;

        // Background
        gc.setFill(Color.web("#161b22"));
        gc.fillRect(l.left, l.rsiTop(), l.plotWidth(), l.rsiHeight);

        // Label
        gc.setFill(TEXT_DIM);
        gc.setFont(FONT_SMALL);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText("RSI(14)", l.left + 4, l.rsiTop() + 12);

        double maxR = 100, minR = 0;

        // Overbought/Oversold zones
        double y70 = subY(70, maxR, minR, l.rsiTop(), l.rsiHeight);
        double y30 = subY(30, maxR, minR, l.rsiTop(), l.rsiHeight);
        gc.setFill(Color.web("#f8514912"));
        gc.fillRect(l.left, l.rsiTop(), l.plotWidth(), y70 - l.rsiTop());
        gc.setFill(Color.web("#3fb95012"));
        gc.fillRect(l.left, y30, l.plotWidth(), l.rsiTop() + l.rsiHeight - y30);

        // Reference lines at 70 and 30
        gc.setStroke(Color.web("#f8514966"));
        gc.setLineWidth(0.8);
        gc.setLineDashes(4, 4);
        gc.strokeLine(l.left, y70, l.right, y70);
        gc.setStroke(Color.web("#3fb95066"));
        gc.strokeLine(l.left, y30, l.right, y30);
        gc.setLineDashes(null);

        // RSI line
        drawSubLineSeries(gc, rsiSeries, start, end, n, maxR, minR, l.rsiTop(),
                l.rsiHeight, Color.web("#bc8cff"), 1.5);

        // Current RSI value label
        double lastRsi = Double.NaN;
        for (int i = end; i >= start; i--) {
            if (i < rsiSeries.size() && !Double.isNaN(rsiSeries.get(i))) {
                lastRsi = rsiSeries.get(i); break;
            }
        }
        if (!Double.isNaN(lastRsi)) {
            gc.setFill(Color.web("#bc8cff"));
            gc.setFont(FONT_SMALL);
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(String.format("%.1f", lastRsi), l.right - 2,
                    l.rsiTop() + 12);
        }
    }

    // ── Draw: Price Axis ─────────────────────────────────────

    private void drawPriceAxis(GraphicsContext gc, ChartLayout l,
                               double maxP, double minP) {
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

    private void drawTimeAxis(GraphicsContext gc, ChartLayout l,
                              List<OhlcvBar> visible, double w) {
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
            gc.fillText(bar.getOpenTime().format(DTF), x, l.priceBottom() + 16);
        }
    }

    // ── Draw: Separators ─────────────────────────────────────

    private void drawSeparators(GraphicsContext gc, ChartLayout l, double w) {
        gc.setStroke(BORDER);
        gc.setLineWidth(1.0);
        gc.setLineDashes(null);
        gc.strokeLine(l.left, l.priceBottom(), l.right, l.priceBottom());
        if (l.volHeight > 0)
            gc.strokeLine(l.left, l.volTop() + l.volHeight,
                    l.right, l.volTop() + l.volHeight);
        if (l.macdHeight > 0)
            gc.strokeLine(l.left, l.macdTop() + l.macdHeight,
                    l.right, l.macdTop() + l.macdHeight);
    }

    // ── Draw: Crosshair ───────────────────────────────────────

    private void drawCrosshair(GraphicsContext gc, ChartLayout l,
                               List<OhlcvBar> visible, double maxP, double minP,
                               double w) {
        if (mouseX < l.left || mouseX > l.right) return;

        gc.setStroke(CROSS);
        gc.setLineWidth(0.8);
        gc.setLineDashes(4, 4);
        gc.strokeLine(mouseX, l.priceTop(), mouseX, l.priceBottom());
        gc.strokeLine(l.left, mouseY, l.right, mouseY);
        gc.setLineDashes(null);

        // Price label at right axis
        double price = yToPrice(mouseY, maxP, minP, l);
        gc.setFill(Color.web("#388bfd"));
        gc.fillRect(l.right + 1, mouseY - 9, PADDING_RIGHT - 4, 18);
        gc.setFill(Color.WHITE);
        gc.setFont(FONT_SMALL);
        gc.setTextAlign(TextAlignment.LEFT);
        gc.fillText(fmtPrice(price), l.right + 4, mouseY + 4);

        // Determine which candle is under the mouse
        int n    = visible.size();
        double barW = l.plotWidth() / Math.max(1, n);
        int barIdx = (int)((mouseX - l.left) / barW);
        barIdx = Math.max(0, Math.min(n - 1, barIdx));

        if (barIdx >= 0 && barIdx < visible.size()) {
            OhlcvBar bar = visible.get(barIdx);

            // OHLCV tooltip box near the mouse
            drawTooltip(gc, bar, mouseX, l.priceTop() + 4);

            // Date label in the time-axis zone at the bottom of the chart
            if (bar.getOpenTime() != null) {
                String dateStr = bar.getOpenTime().format(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                double labelW = 140, labelH = 16;
                double labelX = Math.max(l.left + labelW / 2,
                        Math.min(l.right - labelW / 2, mouseX));

                gc.setFill(Color.web("#388bfd"));
                gc.fillRoundRect(labelX - labelW / 2, l.priceBottom() + 2, labelW, labelH, 4, 4);
                gc.setFill(Color.WHITE);
                gc.setFont(FONT_SMALL);
                gc.setTextAlign(TextAlignment.CENTER);
                gc.fillText(dateStr, labelX, l.priceBottom() + 14);
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
        double bw = 130, bh = lines.length * 16 + 10;
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
        double ly = l.priceTop() + 18;

        gc.setFont(FONT_SMALL);
        gc.setTextAlign(TextAlignment.LEFT);

        Object[][] items = {
                {showEma,       EMA_FAST, "EMA Fast"},
                {showEma,       EMA_SLOW, "EMA Slow"},
                {showEma,       EMA_50,   "EMA 50"},
                {showEma,       EMA_200,  "EMA 200"},
                {showBollinger, BB_UPPER, "Bollinger"},
                {showIchimoku,  ICH_TENKAN, "Ichimoku"},
        };

        for (Object[] item : items) {
            boolean show = (boolean) item[0];
            if (!show) continue;
            Color c = (Color) item[1];
            String label = (String) item[2];
            gc.setStroke(c);
            gc.setLineWidth(2);
            gc.strokeLine(lx, ly - 4, lx + 16, ly - 4);
            gc.setFill(TEXT_DIM);
            gc.fillText(label, lx + 20, ly);
            lx += gc.getFont().getSize() * label.length() * 0.6 + 32;
        }
    }

    // ── Draw: Empty state ────────────────────────────────────

    private void drawEmpty(GraphicsContext gc, double w, double h) {
        gc.setFill(BG);
        gc.fillRect(0, 0, w, h);
        gc.setFill(TEXT_DIM);
        gc.setFont(Font.font("Segoe UI", 16));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("📈 Select a symbol and timeframe to load chart",
                w / 2, h / 2);
    }

    // ── Mouse Handlers ────────────────────────────────────────

    private void setupMouseHandlers() {
        setOnMouseMoved(this::onMouseMoved);
        setOnMouseDragged(this::onMouseDragged);
        setOnMousePressed(this::onMousePressed);
        setOnMouseExited(e -> { showCrosshair = false; render(); });
        setOnScroll(this::onScroll);
    }

    private void onMouseMoved(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
        showCrosshair = true;
        render();
    }

    private void onMousePressed(MouseEvent e) {
        dragStartX   = e.getX();
        dragStartBar = startBarIndex;
    }

    private void onMouseDragged(MouseEvent e) {
        if (bars == null || bars.isEmpty()) return;
        double dx    = e.getX() - dragStartX;
        double barW  = (getWidth() - PADDING_LEFT - PADDING_RIGHT)
                / Math.max(1, visibleBars);
        int shift    = (int)(dx / barW);
        startBarIndex = Math.max(0,
                Math.min(bars.size() - visibleBars,
                        dragStartBar - shift));
        mouseX = e.getX();
        mouseY = e.getY();
        render();
    }

    private void onScroll(ScrollEvent e) {
        if (bars == null || bars.isEmpty()) return;
        double delta = e.getDeltaY();

        // Compute which bar is under the mouse — zoom pivots around that bar
        double plotW = getWidth() - PADDING_LEFT - PADDING_RIGHT;
        double relX  = e.getX() - PADDING_LEFT;
        double fraction = (plotW > 0) ? Math.max(0, Math.min(1, relX / plotW)) : 0.5;
        // The bar index (in the full bars list) that should stay under the mouse
        int anchorBar = startBarIndex + (int)(fraction * visibleBars);

        int oldVisible = visibleBars;
        if (delta < 0) {
            // Zoom out — show more bars
            visibleBars = Math.min(bars.size(), (int)(visibleBars * 1.15));
        } else {
            // Zoom in — show fewer bars
            visibleBars = Math.max(5, (int)(visibleBars * 0.87));
        }
        visibleBars = Math.min(bars.size(), Math.max(1, visibleBars));

        // Adjust startBarIndex so the anchor bar stays under the mouse
        startBarIndex = anchorBar - (int)(fraction * visibleBars);
        startBarIndex = Math.max(0, Math.min(bars.size() - visibleBars, startBarIndex));
        render();
    }

    // ── Utility: Line series drawing ──────────────────────────

    private void drawLineSeries(GraphicsContext gc, ChartLayout l,
                                List<Double> series, int start, int end, int n,
                                double maxP, double minP, Color color, double width) {
        if (series == null || series.isEmpty()) return;
        gc.setStroke(color);
        gc.setLineWidth(width);
        gc.setLineDashes(null);
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
            else          gc.lineTo(x, y);
        }
        gc.stroke();
    }

    private void drawSubLineSeries(GraphicsContext gc, List<Double> series,
                                   int start, int end, int n,
                                   double maxV, double minV,
                                   double areaTop, double areaH,
                                   Color color, double width) {
        if (series == null) return;
        double barW = (getWidth() - PADDING_LEFT - PADDING_RIGHT) / n;
        gc.setStroke(color);
        gc.setLineWidth(width);
        gc.beginPath();
        boolean started = false;
        for (int i = 0; i < n; i++) {
            int idx = start + i;
            if (idx >= series.size()) break;
            double val = series.get(idx);
            if (Double.isNaN(val)) { started = false; continue; }
            double x = PADDING_LEFT + (i + 0.5) * barW;
            double y = subY(val, maxV, minV, areaTop, areaH);
            if (!started) { gc.moveTo(x, y); started = true; }
            else          gc.lineTo(x, y);
        }
        gc.stroke();
    }

    // ── Coordinate transforms ─────────────────────────────────

    private double priceToY(double price, double maxP, double minP, ChartLayout l) {
        double range = Math.max(maxP - minP, 0.0001);
        return l.priceTop() + l.priceHeight * (1.0 - (price - minP) / range);
    }

    private double yToPrice(double y, double maxP, double minP, ChartLayout l) {
        double range = Math.max(maxP - minP, 0.0001);
        return maxP - (y - l.priceTop()) / l.priceHeight * range;
    }

    private double subY(double val, double maxV, double minV,
                        double top, double height) {
        double range = Math.max(Math.abs(maxV - minV), 0.0001);
        return top + height * (1.0 - (val - minV) / range);
    }

    private double getVisibleMax(List<Double> series, int start, int end) {
        double max = Double.NEGATIVE_INFINITY;
        for (int i = start; i <= end && i < series.size(); i++) {
            if (!Double.isNaN(series.get(i)))
                max = Math.max(max, series.get(i));
        }
        return max == Double.NEGATIVE_INFINITY ? 0 : max;
    }

    private double getVisibleMin(List<Double> series, int start, int end) {
        double min = Double.POSITIVE_INFINITY;
        for (int i = start; i <= end && i < series.size(); i++) {
            if (!Double.isNaN(series.get(i)))
                min = Math.min(min, series.get(i));
        }
        return min == Double.POSITIVE_INFINITY ? 0 : min;
    }

    private String fmtPrice(double p) {
        if (p >= 1000) return String.format("%.2f", p);
        if (p >= 1)    return String.format("%.4f", p);
        return String.format("%.8f", p);
    }

    private Color colorForSr(SRLevel sr) {
        return switch (sr.source()) {
            case FIBONACCI -> FIB_LINE;
            case PIVOT     -> PIVOT_LINE;
            case SWING     -> SR_SUP;
        };
    }

    private String fmtVol(double v) {
        if (v >= 1_000_000) return String.format("%.2fM", v / 1_000_000);
        if (v >= 1_000)     return String.format("%.2fK", v / 1_000);
        return String.format("%.2f", v);
    }

    // ── Layout helper record ──────────────────────────────────

    private record ChartLayout(
            double left, double top, double right, double bottom,
            double priceHeight, double volHeight, double macdHeight, double rsiHeight
    ) {
        double priceTop()   { return top; }
        double priceBottom(){ return top + priceHeight; }
        double volTop()     { return priceBottom(); }
        double macdTop()    { return volTop() + volHeight; }
        double rsiTop()     { return macdTop() + macdHeight; }
        double plotWidth()  { return right - left; }
    }
}
