package com.mst.matt.tradingplatformapp.ui.chart.drawing;

import com.mst.matt.tradingplatformapp.model.*;
import com.mst.matt.tradingplatformapp.service.ChartDrawingService;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Window;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manages drawing tool interaction state: creation, selection, editing,
 * context menu, undo/redo, and annotation text editing.
 *
 * <p>Undo/Redo supported for: CREATE, DELETE, DUPLICATE, MOVE, MODIFY, LOCK_TOGGLE.
 * Keyboard shortcuts: Ctrl+Z (undo), Ctrl+Y / Ctrl+Shift+Z (redo).
 *
 * <p>Shape movement: clicking inside a shape (not on an anchor) and dragging
 * moves the entire shape. Anchors handle resize/reshape.
 *
 * <p>Position tools: individual anchor dragging adjusts Entry/SL/TP independently.
 * Dragging the body area moves the entire position shape.
 *
 * <p>Delete button: stable delete button shown when a drawing is selected,
 * rendered in DrawingRenderer at a fixed position relative to the bounding box.
 */
public class ChartDrawingEngine {

    public interface Host {
        void requestRender();
        DrawingRenderer.RenderContext currentRenderContext();
        List<OhlcvBar> getBars();
        Window getWindow();
    }

    private static final double HIT_THRESHOLD = 8;

    /** Drag mode: -1 = no drag, >=0 = anchor index, BODY_DRAG = whole shape. */
    private static final int BODY_DRAG = -10;
    /** Special drag index for position entry line. */
    private static final int POS_ENTRY_DRAG = -20;
    /** Special drag index for position SL line. */
    private static final int POS_SL_DRAG    = -21;
    /** Special drag index for position TP line. */
    private static final int POS_TP_DRAG    = -22;
    /** Text box resize: right edge or bottom-right corner. */
    private static final int TEXT_RESIZE_W  = -30;   // width resize (right edge)
    private static final int TEXT_RESIZE_H  = -31;   // height resize (bottom edge)
    private static final int TEXT_RESIZE_WH = -32;   // both (bottom-right corner)

    private final Host host;
    private final List<ChartDrawing> drawings = new ArrayList<>();
    private final DrawingHistoryManager history = new DrawingHistoryManager();

    private ChartDrawingToolType activeTool = ChartDrawingToolType.SELECT;
    private ChartDrawing selected;
    private ChartDrawing inProgress;
    private int dragAnchorIndex = -1;
    private boolean snapEnabled;
    private boolean isDragging;
    private double pressX, pressY;

    /** Previous mouse X/Y for body drag delta calculation. */
    private double lastDragX, lastDragY;

    /** Snapshot captured at mouse-press, before a drag/move starts. Used for undo. */
    private DrawingHistoryManager.DrawingSnapshot preDragSnapshot;

    private Consumer<ChartDrawing> onDrawingCreated;
    private Consumer<ChartDrawing> onDrawingUpdated;
    private Consumer<ChartDrawing> onDrawingDeleted;
    private Consumer<ChartDrawing> onCreateTradeFromDrawing;
    private Consumer<ChartDrawing> onInstantSaveTrade;
    private Runnable onSelectionChanged;
    /** Called after any undo/redo — usually triggers re-render + persistence. */
    private Consumer<ChartDrawing> onHistoryRestored;

    private ContextMenu contextMenu;

    public ChartDrawingEngine(Host host) {
        this.host = host;
        history.setOnDrawingRestored(d -> {
            if (onHistoryRestored != null) onHistoryRestored.accept(d);
            host.requestRender();
        });
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public void setDrawings(List<ChartDrawing> list) {
        drawings.clear();
        if (list != null) drawings.addAll(list);
        if (selected != null && !drawings.contains(selected)) selected = null;
    }

    public List<ChartDrawing> getDrawings() { return drawings; }

    public void setActiveTool(ChartDrawingToolType tool) {
        this.activeTool = tool != null ? tool : ChartDrawingToolType.SELECT;
        inProgress = null;
        if (tool != ChartDrawingToolType.SELECT) selected = null;
    }

    public ChartDrawingToolType getActiveTool() { return activeTool; }
    public void setSnapEnabled(boolean snap) { this.snapEnabled = snap; }
    public ChartDrawing getSelected() { return selected; }

    public void setOnDrawingCreated(Consumer<ChartDrawing> c) { onDrawingCreated = c; }
    public void setOnDrawingUpdated(Consumer<ChartDrawing> c) { onDrawingUpdated = c; }
    public void setOnDrawingDeleted(Consumer<ChartDrawing> c) { onDrawingDeleted = c; }
    public void setOnCreateTradeFromDrawing(Consumer<ChartDrawing> c) { onCreateTradeFromDrawing = c; }
    public void setOnInstantSaveTrade(Consumer<ChartDrawing> c) { onInstantSaveTrade = c; }
    public void setOnSelectionChanged(Runnable r) { onSelectionChanged = r; }
    public void setOnHistoryRestored(Consumer<ChartDrawing> c) { onHistoryRestored = c; }

    public boolean isDrawingMode() {
        return activeTool != ChartDrawingToolType.SELECT;
    }

    public DrawingHistoryManager getHistory() { return history; }

    // ── Global Drawing Defaults ──────────────────────────────────────────────

    private String globalDefaultColor       = "#58a6ff";
    private double globalDefaultLineWidth   = 1.5;
    private double globalDefaultFillOpacity = 0.12;

    /** Called by ChartController after AppSettingsService loads user preferences. */
    public void setGlobalDrawingDefaults(String color, double lineWidth, double fillOpacity) {
        if (color != null && !color.isBlank()) this.globalDefaultColor = color;
        this.globalDefaultLineWidth   = lineWidth;
        this.globalDefaultFillOpacity = fillOpacity;
    }

    public String getGlobalDefaultColor()       { return globalDefaultColor; }
    public double getGlobalDefaultLineWidth()   { return globalDefaultLineWidth; }
    public double getGlobalDefaultFillOpacity() { return globalDefaultFillOpacity; }

    // ── Undo / Redo ─────────────────────────────────────────────────────────

    /** Undo the last drawing action (Ctrl+Z). */
    public void undo() {
        ChartDrawing d = history.undo(drawings);
        selected = d;
        notifySelection();
        host.requestRender();
        if (d != null && onDrawingUpdated != null) onDrawingUpdated.accept(d);
    }

    /** Redo the last undone action (Ctrl+Y / Ctrl+Shift+Z). */
    public void redo() {
        ChartDrawing d = history.redo(drawings);
        selected = d;
        notifySelection();
        host.requestRender();
        if (d != null && onDrawingUpdated != null) onDrawingUpdated.accept(d);
    }

    // ── Mouse event handlers ─────────────────────────────────────────────────

    public boolean handleMousePressed(MouseEvent e, DrawingRenderer.RenderContext ctx) {
        if (ctx == null || host.getBars() == null || host.getBars().isEmpty()) return false;
        pressX = e.getX();
        pressY = e.getY();
        lastDragX = pressX;
        lastDragY = pressY;
        isDragging = false;

        // Double-click: annotation text editing
        if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) {
            ChartDrawing hit = hitTest(e.getX(), e.getY(), ctx);
            if (hit != null && isAnnotationTool(hit.getToolType())) {
                startTextEditing(hit, ctx);
                return true;
            }
        }

        if (e.getButton() == MouseButton.SECONDARY) {
            ChartDrawing hit = hitTest(e.getX(), e.getY(), ctx);
            if (hit != null) {
                selected = hit;
                notifySelection();
                showContextMenu(e.getScreenX(), e.getScreenY(), hit);
                host.requestRender();
                return true;
            }
            return false;
        }

        if (e.getButton() != MouseButton.PRIMARY) return false;

        // Delete button hit on selected drawing
        if (selected != null && isDeleteButtonHit(e.getX(), e.getY(), ctx)) {
            deleteSelected();
            return true;
        }

        // + Trade button hit on selected position drawing
        if (selected != null && selected.getToolType().isPositionTool()
                && isTradeButtonHit(e.getX(), e.getY(), ctx)) {
            if (onCreateTradeFromDrawing != null) onCreateTradeFromDrawing.accept(selected);
            return true;
        }

        if (activeTool == ChartDrawingToolType.SELECT) {
            // Position tools: check individual line anchors first (Entry, SL, TP)
            if (selected != null && selected.getToolType().isPositionTool() && !selected.isLocked()) {
                int posAnchor = hitPositionLineAnchor(selected, e.getX(), e.getY(), ctx);
                if (posAnchor != -1) {
                    dragAnchorIndex = posAnchor;
                    preDragSnapshot = new DrawingHistoryManager.DrawingSnapshot(selected);
                    return true;
                }
            }

            // Text box resize handle hit-test
            if (selected != null && isTextTool(selected.getToolType()) && !selected.isLocked()) {
                int resizeHandle = hitTextResizeHandle(selected, e.getX(), e.getY(), ctx);
                if (resizeHandle != -1) {
                    dragAnchorIndex = resizeHandle;
                    preDragSnapshot = new DrawingHistoryManager.DrawingSnapshot(selected);
                    return true;
                }
            }

            // Standard anchor hit test
            int anchor = hitAnchor(selected, e.getX(), e.getY(), ctx);
            if (anchor >= 0 && selected != null && !selected.isLocked()) {
                dragAnchorIndex = anchor;
                preDragSnapshot = new DrawingHistoryManager.DrawingSnapshot(selected);
                return true;
            }

            ChartDrawing hit = hitTest(e.getX(), e.getY(), ctx);
            if (hit != null) {
                if (selected == hit && !hit.isLocked()) {
                    // Already selected — start body drag
                    dragAnchorIndex = BODY_DRAG;
                    preDragSnapshot = new DrawingHistoryManager.DrawingSnapshot(hit);
                } else {
                    selected = hit;
                    notifySelection();
                    // Prepare for body drag on next drag event
                    if (!hit.isLocked()) {
                        dragAnchorIndex = BODY_DRAG;
                        preDragSnapshot = new DrawingHistoryManager.DrawingSnapshot(hit);
                    }
                }
                host.requestRender();
                return true;
            }
            // Clicked empty area — deselect
            selected = null;
            dragAnchorIndex = -1;
            preDragSnapshot = null;
            notifySelection();
            host.requestRender();
            return false;
        }

        // Drawing mode
        ChartPoint pt = pointFromMouse(e.getX(), e.getY(), ctx);
        if (inProgress == null) {
            inProgress = newDraft(activeTool, pt);
            if (activeTool.requiredPoints() == 1) {
                finalizeInProgress();
            }
        } else {
            inProgress.getPoints().add(pt);
            if (inProgress.getPoints().size() >= activeTool.requiredPoints()) {
                finalizeInProgress();
            }
        }
        host.requestRender();
        return true;
    }

    public boolean handleMouseDragged(MouseEvent e, DrawingRenderer.RenderContext ctx) {
        if (ctx == null) return false;
        isDragging = true;

        if (activeTool == ChartDrawingToolType.SELECT) {
            if (selected != null && !selected.isLocked()) {
                if (dragAnchorIndex == BODY_DRAG) {
                    // Move entire shape by delta
                    moveShapeByDelta(selected, e.getX() - lastDragX, e.getY() - lastDragY, ctx);
                    lastDragX = e.getX();
                    lastDragY = e.getY();
                    host.requestRender();
                    return true;
                } else if (dragAnchorIndex == POS_ENTRY_DRAG) {
                    // Move entry line vertically
                    double newPrice = DrawingCoordinateMapper.yToPrice(e.getY(),
                            ctx.priceTop(), ctx.priceH(), ctx.maxPrice(), ctx.minPrice());
                    selected.getProperties().setEntryPrice(newPrice);
                    host.requestRender();
                    return true;
                } else if (dragAnchorIndex == POS_SL_DRAG) {
                    double newPrice = DrawingCoordinateMapper.yToPrice(e.getY(),
                            ctx.priceTop(), ctx.priceH(), ctx.maxPrice(), ctx.minPrice());
                    selected.getProperties().setStopLoss(newPrice);
                    host.requestRender();
                    return true;
                } else if (dragAnchorIndex == POS_TP_DRAG) {
                    double newPrice = DrawingCoordinateMapper.yToPrice(e.getY(),
                            ctx.priceTop(), ctx.priceH(), ctx.maxPrice(), ctx.minPrice());
                    selected.getProperties().setTakeProfit(newPrice);
                    host.requestRender();
                    return true;
                } else if (dragAnchorIndex == TEXT_RESIZE_W
                        || dragAnchorIndex == TEXT_RESIZE_H
                        || dragAnchorIndex == TEXT_RESIZE_WH) {
                    // Resize text box
                    resizeTextBox(selected, e.getX(), e.getY(), ctx);
                    host.requestRender();
                    return true;
                } else if (dragAnchorIndex >= 0) {
                    ChartPoint pt = pointFromMouse(e.getX(), e.getY(), ctx);
                    selected.getPoints().set(dragAnchorIndex, pt);
                    updatePositionPricesFromAnchors(selected);
                    host.requestRender();
                    return true;
                }
            }
            return selected != null;
        }

        if (inProgress != null && !inProgress.getPoints().isEmpty()) {
            ChartPoint pt = pointFromMouse(e.getX(), e.getY(), ctx);
            int numPoints = inProgress.getPoints().size();
            if (numPoints == 1 && inProgress.getToolType().requiredPoints() > 1) {
                inProgress.getPoints().add(pt);
            } else if (numPoints >= 2) {
                inProgress.getPoints().set(numPoints - 1, pt);
            }
            updatePositionPricesFromAnchors(inProgress);
            host.requestRender();
            return true;
        }
        return false;
    }

    public boolean handleMouseReleased(MouseEvent e, DrawingRenderer.RenderContext ctx) {
        boolean wasDragging = isDragging;

        if (selected != null && !selected.isLocked()
                && (dragAnchorIndex == BODY_DRAG
                    || dragAnchorIndex >= 0
                    || dragAnchorIndex == POS_ENTRY_DRAG
                    || dragAnchorIndex == POS_SL_DRAG
                    || dragAnchorIndex == POS_TP_DRAG)
                && wasDragging) {
            dragAnchorIndex = -1;
            if (preDragSnapshot != null) {
                history.recordMove(selected, preDragSnapshot);
                preDragSnapshot = null;
            }
            if (onDrawingUpdated != null) onDrawingUpdated.accept(selected);
            return true;
        }

        if (inProgress != null && activeTool != ChartDrawingToolType.SELECT) {
            if (inProgress.getPoints().size() >= 1 && inProgress.getToolType().requiredPoints() > 1) {
                ChartPoint pt = pointFromMouse(e.getX(), e.getY(), ctx);
                int numPts = inProgress.getPoints().size();
                if (numPts < 2) inProgress.getPoints().add(pt);
                else inProgress.getPoints().set(numPts - 1, pt);
                updatePositionPricesFromAnchors(inProgress);
            }
            if (inProgress.getPoints().size() >= inProgress.getToolType().requiredPoints()) {
                finalizeInProgress();
            }
            host.requestRender();
            return true;
        }
        dragAnchorIndex = -1;
        preDragSnapshot = null;
        return false;
    }

    // ── Render ───────────────────────────────────────────────────────────────

    public void renderDrawings(GraphicsContext gc, DrawingRenderer.RenderContext ctx) {
        for (ChartDrawing d : drawings) {
            DrawingRenderer.render(gc, d, ctx, d == selected, activeTool == ChartDrawingToolType.SELECT);
        }
        if (inProgress != null) {
            DrawingRenderer.render(gc, inProgress, ctx, true, true);
        }
        // Draw stable delete button for selected drawing
        if (selected != null) {
            DrawingRenderer.renderDeleteButton(gc, selected, ctx);
        }
    }

    // ── Drawing manipulation ─────────────────────────────────────────────────

    public void deleteSelected() {
        if (selected == null) return;
        history.recordDelete(selected);
        drawings.remove(selected);
        if (onDrawingDeleted != null) onDrawingDeleted.accept(selected);
        selected = null;
        notifySelection();
        host.requestRender();
    }

    public void duplicateSelected() {
        if (selected == null) return;
        ChartDrawing copy = cloneDrawing(selected);
        copy.setId(null);
        offsetCopyPoints(copy);
        drawings.add(copy);
        history.recordCreate(copy);
        if (onDrawingCreated != null) onDrawingCreated.accept(copy);
        selected = copy;
        host.requestRender();
    }

    public void toggleLockSelected() {
        if (selected == null) return;
        DrawingHistoryManager.DrawingSnapshot before = new DrawingHistoryManager.DrawingSnapshot(selected);
        selected.setLocked(!selected.isLocked());
        DrawingHistoryManager.DrawingSnapshot after = new DrawingHistoryManager.DrawingSnapshot(selected);
        history.record(new DrawingHistoryManager.HistoryEntry(
                DrawingHistoryManager.ActionType.LOCK_TOGGLE, before, after));
        if (onDrawingUpdated != null) onDrawingUpdated.accept(selected);
        host.requestRender();
    }

    /** Keyboard shortcut: instant-save trade from selected position drawing. */
    public void instantSaveSelectedPosition() {
        if (selected != null && selected.getToolType().isPositionTool()
                && onInstantSaveTrade != null) {
            onInstantSaveTrade.accept(selected);
        }
    }

    // ── Annotation text editing ──────────────────────────────────────────────

    private void startTextEditing(ChartDrawing d, DrawingRenderer.RenderContext ctx) {
        if (d == null || d.getProperties() == null) return;
        DrawingHistoryManager.DrawingSnapshot before = new DrawingHistoryManager.DrawingSnapshot(d);

        switch (d.getToolType()) {
            case NOTE_ICON -> openNoteEditDialog(d, before);
            case TEXT_LABEL, CALLOUT -> openInlineTextEditor(d, ctx, before);
            default -> {}
        }
    }

    private void openNoteEditDialog(ChartDrawing d, DrawingHistoryManager.DrawingSnapshot before) {
        String current = d.getProperties().getText() != null ? d.getProperties().getText() : "";
        TextInputDialog dialog = new TextInputDialog(current);
        dialog.setTitle("Edit Note");
        dialog.setHeaderText("Enter note content:");
        dialog.setContentText("Note:");
        if (host.getWindow() != null) {
            try { dialog.initOwner(host.getWindow()); } catch (Exception ignored) {}
        }
        dialog.showAndWait().ifPresent(newText -> {
            d.getProperties().setText(newText);
            history.recordModify(d, before);
            if (onDrawingUpdated != null) onDrawingUpdated.accept(d);
            host.requestRender();
        });
    }

    private void openInlineTextEditor(ChartDrawing d, DrawingRenderer.RenderContext ctx,
                                      DrawingHistoryManager.DrawingSnapshot before) {
        String current = d.getProperties().getText() != null ? d.getProperties().getText() : "";
        d.getProperties().setEditing(true);
        host.requestRender();

        TextInputDialog dialog = new TextInputDialog(current);
        dialog.setTitle("Edit " + d.getToolType().displayName());
        dialog.setHeaderText("Enter label text:");
        dialog.setContentText("Text:");
        if (host.getWindow() != null) {
            try { dialog.initOwner(host.getWindow()); } catch (Exception ignored) {}
        }
        dialog.showAndWait().ifPresent(newText -> {
            d.getProperties().setText(newText.isBlank() ? "Text" : newText);
            history.recordModify(d, before);
            if (onDrawingUpdated != null) onDrawingUpdated.accept(d);
        });
        d.getProperties().setEditing(false);
        host.requestRender();
    }

    private static boolean isAnnotationTool(ChartDrawingToolType t) {
        return t == ChartDrawingToolType.TEXT_LABEL
                || t == ChartDrawingToolType.CALLOUT
                || t == ChartDrawingToolType.NOTE_ICON;
    }

    // ── Finalize drawing ──────────────────────────────────────────────────────

    private void finalizeInProgress() {
        if (inProgress == null) return;
        updatePositionPricesFromAnchors(inProgress);
        if (inProgress.getToolType() == ChartDrawingToolType.TEXT_LABEL
                && (inProgress.getProperties().getText() == null
                || inProgress.getProperties().getText().isBlank())) {
            inProgress.getProperties().setText("Text");
        } else if (inProgress.getToolType() == ChartDrawingToolType.CALLOUT
                && (inProgress.getProperties().getText() == null
                || inProgress.getProperties().getText().isBlank())) {
            inProgress.getProperties().setText("Callout");
        } else if (inProgress.getToolType() == ChartDrawingToolType.NOTE_ICON
                && (inProgress.getProperties().getText() == null
                || inProgress.getProperties().getText().isBlank())) {
            inProgress.getProperties().setText("");
        }
        drawings.add(inProgress);
        history.recordCreate(inProgress);
        if (onDrawingCreated != null) onDrawingCreated.accept(inProgress);
        selected = inProgress;
        inProgress = null;
        notifySelection();
    }

    private ChartDrawing newDraft(ChartDrawingToolType tool, ChartPoint pt) {
        ChartDrawingProperties props = ChartDrawingProperties.defaultsFor(tool);
        // Apply global defaults for tools that don't override colour (non-position, non-fib, non-note)
        boolean usesGlobalColor = !tool.isPositionTool()
                && tool != ChartDrawingToolType.FIB_RETRACEMENT
                && tool != ChartDrawingToolType.FIB_EXTENSION
                && tool != ChartDrawingToolType.FIB_FAN
                && tool != ChartDrawingToolType.FIB_CHANNEL
                && tool != ChartDrawingToolType.FIB_TIME_ZONES
                && tool != ChartDrawingToolType.FIB_SPEED_RESISTANCE
                && tool != ChartDrawingToolType.NOTE_ICON
                && tool != ChartDrawingToolType.HORIZONTAL_LINE
                && tool != ChartDrawingToolType.PROFIT_TARGET_LINE
                && tool != ChartDrawingToolType.STOP_LOSS_LINE;
        if (usesGlobalColor) {
            props.setColor(globalDefaultColor);
            props.setLineWidth(globalDefaultLineWidth);
            props.setFillOpacity(globalDefaultFillOpacity);
        }
        if (tool.isPositionTool()) {
            double entry = pt.getPrice();
            double offset = Math.abs(entry) * 0.02;
            if (tool == ChartDrawingToolType.LONG_POSITION) {
                props.setEntryPrice(entry);
                props.setStopLoss(entry - offset);
                props.setTakeProfit(entry + offset * 2);
            } else {
                props.setEntryPrice(entry);
                props.setStopLoss(entry + offset);
                props.setTakeProfit(entry - offset * 2);
            }
        }
        return ChartDrawing.builder()
                .toolType(tool)
                .points(new ArrayList<>(List.of(pt)))
                .properties(props)
                .locked(false)
                .build();
    }

    private void updatePositionPricesFromAnchors(ChartDrawing d) {
        if (!d.getToolType().isPositionTool() || d.getPoints().size() < 2) return;
        ChartDrawingProperties props = d.getProperties();
        if (props.getEntryPrice() == null && !d.getPoints().isEmpty()) {
            props.setEntryPrice(d.getPoints().getFirst().getPrice());
        }
        if (d.getPoints().size() >= 2 && props.getEntryPrice() != null) {
            double p2 = d.getPoints().get(1).getPrice();
            double entry = props.getEntryPrice();
            if (d.getToolType() == ChartDrawingToolType.LONG_POSITION) {
                if (p2 < entry) props.setStopLoss(p2);
                else props.setTakeProfit(p2);
            } else {
                if (p2 > entry) props.setStopLoss(p2);
                else props.setTakeProfit(p2);
            }
        }
    }

    // ── Shape movement helpers ─────────────────────────────────────────────

    /**
     * Moves all anchor points of the drawing by (dx, dy) in screen pixels,
     * converting the delta back to price/time units.
     */
    private void moveShapeByDelta(ChartDrawing d, double dx, double dy,
                                   DrawingRenderer.RenderContext ctx) {
        if (d.getPoints() == null || d.getPoints().isEmpty()) return;
        double barW = ctx.plotWidth() / Math.max(1, ctx.visibleBars());

        for (ChartPoint pt : d.getPoints()) {
            // Move time by dx (convert pixel delta to bar offset)
            double barsDelta = dx / barW;
            long secondsPerBar = estimateSecondsPerBar(host.getBars());
            long secondsDelta = (long)(barsDelta * secondsPerBar);
            pt.setTime(pt.getTime().plusSeconds(secondsDelta));

            // Move price by dy (convert pixel delta to price delta)
            double range = ctx.maxPrice() - ctx.minPrice();
            double priceDelta = -dy * range / ctx.priceH();
            pt.setPrice(pt.getPrice() + priceDelta);
        }

        // For position tools, shift the price levels too
        if (d.getToolType().isPositionTool()) {
            ChartDrawingProperties props = d.getProperties();
            double range = ctx.maxPrice() - ctx.minPrice();
            double priceDelta = -dy * range / ctx.priceH();
            if (props.getEntryPrice() != null) props.setEntryPrice(props.getEntryPrice() + priceDelta);
            if (props.getStopLoss()   != null) props.setStopLoss(props.getStopLoss() + priceDelta);
            if (props.getTakeProfit() != null) props.setTakeProfit(props.getTakeProfit() + priceDelta);
        }

        // For text/note, also needs no extra work since anchor is the single point
    }

    /**
     * Estimates the seconds per bar from the bar series, used for time delta on drag.
     */
    private static long estimateSecondsPerBar(List<OhlcvBar> bars) {
        if (bars == null || bars.size() < 2) return 60;
        try {
            LocalDateTime t0 = bars.get(0).getTime();
            LocalDateTime t1 = bars.get(1).getTime();
            long s = java.time.Duration.between(t0, t1).getSeconds();
            return s > 0 ? s : 60;
        } catch (Exception e) {
            return 60;
        }
    }

    // ── Coordinate helpers ────────────────────────────────────────────────────

    private ChartPoint pointFromMouse(double x, double y, DrawingRenderer.RenderContext ctx) {
        int barIdx = DrawingCoordinateMapper.xToBarIndex(x, ctx.left(), ctx.plotWidth(),
                ctx.startBarIndex(), ctx.visibleBars());
        LocalDateTime time = DrawingCoordinateMapper.barIndexToTime(host.getBars(), barIdx);
        double price = DrawingCoordinateMapper.yToPrice(y, ctx.priceTop(), ctx.priceH(),
                ctx.maxPrice(), ctx.minPrice());
        ChartPoint pt = ChartPoint.builder().time(time).price(price).build();
        if (snapEnabled) pt = DrawingCoordinateMapper.snapPoint(host.getBars(), pt);
        return pt;
    }

    // ── Hit testing ───────────────────────────────────────────────────────────

    private ChartDrawing hitTest(double x, double y, DrawingRenderer.RenderContext ctx) {
        for (int i = drawings.size() - 1; i >= 0; i--) {
            if (isNearDrawing(drawings.get(i), x, y, ctx)) return drawings.get(i);
        }
        return null;
    }

    private boolean isNearDrawing(ChartDrawing d, double x, double y, DrawingRenderer.RenderContext ctx) {
        if (d.getPoints() == null || d.getPoints().isEmpty()) return false;
        if (d.getToolType().isPositionTool()) return isNearPosition(d, x, y, ctx);

        switch (d.getToolType()) {
            case HORIZONTAL_LINE, PROFIT_TARGET_LINE, STOP_LOSS_LINE -> {
                double py = DrawingRenderer.priceToY(d.getPoints().getFirst().getPrice(), ctx);
                return Math.abs(y - py) < HIT_THRESHOLD && x >= ctx.left() && x <= ctx.right();
            }
            case VERTICAL_LINE -> {
                double px = DrawingRenderer.timeToX(d.getPoints().getFirst().getTime(), ctx);
                return Math.abs(x - px) < HIT_THRESHOLD && y >= ctx.priceTop() && y <= ctx.priceBottom();
            }
            case RECTANGLE, FLAT_CHANNEL -> {
                return isNearRect(d, x, y, ctx);
            }
            case TRIANGLE -> {
                return isNearPolygon(d, x, y, ctx);
            }
            case ELLIPSE -> {
                return isNearEllipse(d, x, y, ctx);
            }
            case TEXT_LABEL, NOTE_ICON -> {
                double ax = DrawingRenderer.timeToX(d.getPoints().getFirst().getTime(), ctx);
                double ay = DrawingRenderer.priceToY(d.getPoints().getFirst().getPrice(), ctx);
                // Wider hit area for text boxes (matches the rendered box)
                double boxW = estimateTextBoxWidth(d);
                double boxH = estimateTextBoxHeight(d);
                return x >= ax && x <= ax + boxW && y >= ay - boxH && y <= ay + 10;
            }
            case CALLOUT -> {
                if (d.getPoints().size() < 2) {
                    double ax = DrawingRenderer.timeToX(d.getPoints().getFirst().getTime(), ctx);
                    double ay = DrawingRenderer.priceToY(d.getPoints().getFirst().getPrice(), ctx);
                    return x >= ax && x <= ax + 120 && y >= ay - 30 && y <= ay + 5;
                }
                double bx = DrawingRenderer.timeToX(d.getPoints().get(1).getTime(), ctx);
                double by = DrawingRenderer.priceToY(d.getPoints().get(1).getPrice(), ctx);
                String text = d.getProperties().getText() != null ? d.getProperties().getText() : "Callout";
                double bw = Math.max(70, text.length() * 7 + 16);
                double bh = 24;
                return x >= bx && x <= bx + bw && y >= by && y <= by + bh;
            }
            default -> {
                if (d.getPoints().size() >= 2) {
                    double x1 = DrawingRenderer.timeToX(d.getPoints().get(0).getTime(), ctx);
                    double y1 = DrawingRenderer.priceToY(d.getPoints().get(0).getPrice(), ctx);
                    double x2 = DrawingRenderer.timeToX(d.getPoints().get(1).getTime(), ctx);
                    double y2 = DrawingRenderer.priceToY(d.getPoints().get(1).getPrice(), ctx);
                    return distToSegment(x, y, x1, y1, x2, y2) < HIT_THRESHOLD;
                }
                double px = DrawingRenderer.timeToX(d.getPoints().getFirst().getTime(), ctx);
                double py = DrawingRenderer.priceToY(d.getPoints().getFirst().getPrice(), ctx);
                return Math.hypot(x - px, y - py) < HIT_THRESHOLD * 2;
            }
        }
    }

    private double estimateTextBoxWidth(ChartDrawing d) {
        if (d.getProperties() == null) return 120;
        String text = d.getProperties().getText();
        if (text == null || text.isBlank()) return 80;
        double fontSize = d.getProperties().getFontSize() > 0 ? d.getProperties().getFontSize() : 12;
        return Math.max(60, text.length() * (fontSize * 0.65) + 16);
    }

    private double estimateTextBoxHeight(ChartDrawing d) {
        if (d.getToolType() == ChartDrawingToolType.NOTE_ICON) return 60;
        double fontSize = (d.getProperties() != null && d.getProperties().getFontSize() > 0)
                ? d.getProperties().getFontSize() : 12;
        return fontSize + 12;
    }

    private boolean isNearRect(ChartDrawing d, double x, double y, DrawingRenderer.RenderContext ctx) {
        if (d.getPoints().size() < 2) return false;
        double x1 = DrawingRenderer.timeToX(d.getPoints().get(0).getTime(), ctx);
        double y1 = DrawingRenderer.priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x2 = DrawingRenderer.timeToX(d.getPoints().get(1).getTime(), ctx);
        double y2 = DrawingRenderer.priceToY(d.getPoints().get(1).getPrice(), ctx);
        double minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        double minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        boolean insideX = x >= minX - HIT_THRESHOLD && x <= maxX + HIT_THRESHOLD;
        boolean insideY = y >= minY - HIT_THRESHOLD && y <= maxY + HIT_THRESHOLD;
        if (!insideX || !insideY) return false;
        return Math.abs(x - minX) < HIT_THRESHOLD || Math.abs(x - maxX) < HIT_THRESHOLD
                || Math.abs(y - minY) < HIT_THRESHOLD || Math.abs(y - maxY) < HIT_THRESHOLD
                || (x >= minX && x <= maxX && y >= minY && y <= maxY);
    }

    private boolean isNearPolygon(ChartDrawing d, double x, double y, DrawingRenderer.RenderContext ctx) {
        List<ChartPoint> pts = d.getPoints();
        int n = pts.size();
        if (n < 2) return false;
        for (int i = 0; i < n - 1; i++) {
            double x1 = DrawingRenderer.timeToX(pts.get(i).getTime(), ctx);
            double y1 = DrawingRenderer.priceToY(pts.get(i).getPrice(), ctx);
            double x2 = DrawingRenderer.timeToX(pts.get(i + 1).getTime(), ctx);
            double y2 = DrawingRenderer.priceToY(pts.get(i + 1).getPrice(), ctx);
            if (distToSegment(x, y, x1, y1, x2, y2) < HIT_THRESHOLD) return true;
        }
        if (n >= 3) {
            double x1 = DrawingRenderer.timeToX(pts.get(n - 1).getTime(), ctx);
            double y1 = DrawingRenderer.priceToY(pts.get(n - 1).getPrice(), ctx);
            double x2 = DrawingRenderer.timeToX(pts.get(0).getTime(), ctx);
            double y2 = DrawingRenderer.priceToY(pts.get(0).getPrice(), ctx);
            if (distToSegment(x, y, x1, y1, x2, y2) < HIT_THRESHOLD) return true;
        }
        return false;
    }

    private boolean isNearEllipse(ChartDrawing d, double x, double y, DrawingRenderer.RenderContext ctx) {
        if (d.getPoints().size() < 2) return false;
        double x1 = DrawingRenderer.timeToX(d.getPoints().get(0).getTime(), ctx);
        double y1 = DrawingRenderer.priceToY(d.getPoints().get(0).getPrice(), ctx);
        double x2 = DrawingRenderer.timeToX(d.getPoints().get(1).getTime(), ctx);
        double y2 = DrawingRenderer.priceToY(d.getPoints().get(1).getPrice(), ctx);
        double cx = (x1 + x2) / 2, cy = (y1 + y2) / 2;
        double rx = Math.abs(x2 - x1) / 2, ry = Math.abs(y2 - y1) / 2;
        if (rx < 1) rx = 1; if (ry < 1) ry = 1;
        double val = Math.pow((x - cx) / rx, 2) + Math.pow((y - cy) / ry, 2);
        return val <= 1.2;
    }

    private boolean isNearPosition(ChartDrawing d, double x, double y,
                                   DrawingRenderer.RenderContext ctx) {
        if (d.getPoints().size() < 2) return false;
        double x1 = DrawingRenderer.timeToX(d.getPoints().get(0).getTime(), ctx);
        double x2 = DrawingRenderer.timeToX(d.getPoints().get(1).getTime(), ctx);
        double left = Math.min(x1, x2), right = Math.max(x1, x2);
        return x >= left - 10 && x <= right + 60 && y >= ctx.priceTop() && y <= ctx.priceBottom();
    }

    private boolean isTradeButtonHit(double x, double y, DrawingRenderer.RenderContext ctx) {
        if (selected == null || !selected.getToolType().isPositionTool()) return false;
        if (selected.getPoints().size() < 2) return false;
        double x2 = DrawingRenderer.timeToX(selected.getPoints().get(1).getTime(), ctx);
        Double entry = selected.getProperties().getEntryPrice();
        if (entry == null) return false;
        double yEntry = DrawingRenderer.priceToY(entry, ctx);
        return x >= x2 + 4 && x <= x2 + 56 && y >= yEntry - 10 && y <= yEntry + 10;
    }

    /**
     * Returns true if the mouse is over the stable delete button for the selected drawing.
     * The delete button is rendered at top-right of the drawing's bounding box.
     */
    private boolean isDeleteButtonHit(double x, double y, DrawingRenderer.RenderContext ctx) {
        if (selected == null || selected.getPoints() == null || selected.getPoints().isEmpty()) return false;
        double[] bb = getDrawingBoundingBox(selected, ctx);
        if (bb == null) return false;
        // Delete button: top-right corner, 20x20 px
        double btnX = bb[2] + 4;
        double btnY = bb[1] - 20;
        return x >= btnX && x <= btnX + 20 && y >= btnY && y <= btnY + 20;
    }

    /**
     * Returns [minX, minY, maxX, maxY] for the drawing's visual bounding box in screen coords.
     */
    public static double[] getDrawingBoundingBox(ChartDrawing d, DrawingRenderer.RenderContext ctx) {
        if (d == null || d.getPoints() == null || d.getPoints().isEmpty()) return null;
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (ChartPoint pt : d.getPoints()) {
            double x = DrawingRenderer.timeToX(pt.getTime(), ctx);
            double y = DrawingRenderer.priceToY(pt.getPrice(), ctx);
            minX = Math.min(minX, x); minY = Math.min(minY, y);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
        }
        // For position tools, expand to include SL/TP lines
        if (d.getToolType().isPositionTool() && d.getProperties() != null) {
            ChartDrawingProperties p = d.getProperties();
            if (p.getEntryPrice() != null) {
                double y = DrawingRenderer.priceToY(p.getEntryPrice(), ctx);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            }
            if (p.getStopLoss() != null) {
                double y = DrawingRenderer.priceToY(p.getStopLoss(), ctx);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            }
            if (p.getTakeProfit() != null) {
                double y = DrawingRenderer.priceToY(p.getTakeProfit(), ctx);
                minY = Math.min(minY, y); maxY = Math.max(maxY, y);
            }
            maxX += 60; // account for +Trade button area
        }
        // For text/note, add box dimensions
        if (d.getToolType() == ChartDrawingToolType.TEXT_LABEL
                || d.getToolType() == ChartDrawingToolType.CALLOUT
                || d.getToolType() == ChartDrawingToolType.NOTE_ICON) {
            String text = d.getProperties() != null && d.getProperties().getText() != null
                    ? d.getProperties().getText() : "Text";
            double fontSize = (d.getProperties() != null && d.getProperties().getFontSize() > 0)
                    ? d.getProperties().getFontSize() : 12;
            double bw = Math.max(60, text.length() * (fontSize * 0.65) + 16);
            double bh = d.getToolType() == ChartDrawingToolType.NOTE_ICON ? 60 : fontSize + 12;
            maxX = Math.max(maxX, minX + bw);
            maxY = Math.max(maxY, minY + bh);
        }
        return new double[]{minX, minY, maxX, maxY};
    }

    private static boolean isTextTool(ChartDrawingToolType t) {
        return t == ChartDrawingToolType.TEXT_LABEL
                || t == ChartDrawingToolType.NOTE_ICON
                || t == ChartDrawingToolType.CALLOUT;
    }

    /**
     * Hit-tests text resize handles. Returns TEXT_RESIZE_W, TEXT_RESIZE_H,
     * TEXT_RESIZE_WH, or -1 if not near a handle.
     */
    private int hitTextResizeHandle(ChartDrawing d, double x, double y,
                                    DrawingRenderer.RenderContext ctx) {
        double[] bb = getDrawingBoundingBox(d, ctx);
        if (bb == null) return -1;
        double maxX = bb[2], maxY = bb[3], minX = bb[0], minY = bb[1];
        double midY = (minY + maxY) / 2;
        double midX = (minX + maxX) / 2;
        double H = HIT_THRESHOLD;

        // Bottom-right corner (resize WH)
        if (Math.hypot(x - maxX, y - maxY) < H) return TEXT_RESIZE_WH;
        // Right mid (resize W)
        if (Math.hypot(x - maxX, y - midY) < H) return TEXT_RESIZE_W;
        // Bottom mid (resize H)
        if (Math.hypot(x - midX, y - maxY) < H) return TEXT_RESIZE_H;
        return -1;
    }

    /**
     * Resizes a text box by moving the resize handle to (mouseX, mouseY).
     */
    private void resizeTextBox(ChartDrawing d, double mouseX, double mouseY,
                               DrawingRenderer.RenderContext ctx) {
        if (d.getPoints().isEmpty() || d.getProperties() == null) return;
        double anchorX = DrawingRenderer.timeToX(d.getPoints().getFirst().getTime(), ctx);
        double anchorY = DrawingRenderer.priceToY(d.getPoints().getFirst().getPrice(), ctx);
        ChartDrawingProperties props = d.getProperties();

        if (dragAnchorIndex == TEXT_RESIZE_W || dragAnchorIndex == TEXT_RESIZE_WH) {
            double newW = Math.max(40, mouseX - anchorX);
            props.setBoxWidth(newW);
        }
        if (dragAnchorIndex == TEXT_RESIZE_H || dragAnchorIndex == TEXT_RESIZE_WH) {
            // For note: anchorY is top; for text_label: anchorY is baseline
            double newH = Math.max(20, mouseY - anchorY);
            props.setBoxHeight(newH);
        }
    }

    /**
     * Hit-tests the position tool's individual price lines (Entry, SL, TP).
     * Returns POS_ENTRY_DRAG, POS_SL_DRAG, POS_TP_DRAG or -1 if not hit.
     */
    private int hitPositionLineAnchor(ChartDrawing d, double x, double y,
                                      DrawingRenderer.RenderContext ctx) {
        if (d.getProperties() == null || d.getPoints().size() < 2) return -1;
        double x1 = DrawingRenderer.timeToX(d.getPoints().get(0).getTime(), ctx);
        double x2 = DrawingRenderer.timeToX(d.getPoints().get(1).getTime(), ctx);
        double left = Math.min(x1, x2), right = Math.max(x1, x2);
        // Only in the horizontal extent of the position drawing
        if (x < left - 10 || x > right + 10) return -1;

        ChartDrawingProperties props = d.getProperties();
        if (props.getEntryPrice() != null) {
            double yEntry = DrawingRenderer.priceToY(props.getEntryPrice(), ctx);
            if (Math.abs(y - yEntry) < HIT_THRESHOLD * 1.5) return POS_ENTRY_DRAG;
        }
        if (props.getStopLoss() != null) {
            double ySl = DrawingRenderer.priceToY(props.getStopLoss(), ctx);
            if (Math.abs(y - ySl) < HIT_THRESHOLD * 1.5) return POS_SL_DRAG;
        }
        if (props.getTakeProfit() != null) {
            double yTp = DrawingRenderer.priceToY(props.getTakeProfit(), ctx);
            if (Math.abs(y - yTp) < HIT_THRESHOLD * 1.5) return POS_TP_DRAG;
        }
        return -1;
    }

    private int hitAnchor(ChartDrawing d, double x, double y, DrawingRenderer.RenderContext ctx) {
        if (d == null || d.getPoints() == null) return -1;
        for (int i = 0; i < d.getPoints().size(); i++) {
            double px = DrawingRenderer.timeToX(d.getPoints().get(i).getTime(), ctx);
            double py = DrawingRenderer.priceToY(d.getPoints().get(i).getPrice(), ctx);
            if (Math.hypot(x - px, y - py) < HIT_THRESHOLD) return i;
        }
        return -1;
    }

    // ── Context menu ─────────────────────────────────────────────────────────

    private void showContextMenu(double screenX, double screenY, ChartDrawing d) {
        if (contextMenu != null) contextMenu.hide();
        contextMenu = new ContextMenu();
        contextMenu.setStyle("-fx-background-color:#1c2128; -fx-border-color:#30363d;");

        MenuItem del = new MenuItem("🗑 Delete");
        del.setOnAction(e -> deleteSelected());
        MenuItem dup = new MenuItem("⧉ Duplicate");
        dup.setOnAction(e -> duplicateSelected());
        MenuItem lock = new MenuItem(d.isLocked() ? "🔓 Unlock" : "🔒 Lock");
        lock.setOnAction(e -> toggleLockSelected());
        MenuItem undoItem = new MenuItem("↩ Undo  Ctrl+Z");
        undoItem.setDisable(!history.canUndo());
        undoItem.setOnAction(e -> undo());
        MenuItem redoItem = new MenuItem("↪ Redo  Ctrl+Y");
        redoItem.setDisable(!history.canRedo());
        redoItem.setOnAction(e -> redo());

        contextMenu.getItems().addAll(del, dup, lock, new SeparatorMenuItem(), undoItem, redoItem);

        if (isAnnotationTool(d.getToolType())) {
            MenuItem editText = new MenuItem("✏ Edit Text");
            editText.setOnAction(e -> startTextEditing(d, host.currentRenderContext()));
            contextMenu.getItems().add(0, editText);
            contextMenu.getItems().add(1, new SeparatorMenuItem());
        }

        if (d.getToolType().isPositionTool()) {
            MenuItem trade = new MenuItem("📈 Create Trade from Drawing");
            trade.setOnAction(e -> { if (onCreateTradeFromDrawing != null) onCreateTradeFromDrawing.accept(d); });
            MenuItem instant = new MenuItem("💾 Instant Save Trade");
            instant.setOnAction(e -> { if (onInstantSaveTrade != null) onInstantSaveTrade.accept(d); });
            contextMenu.getItems().addAll(new SeparatorMenuItem(), trade, instant);
        }
        contextMenu.show(host.getWindow(), screenX, screenY);
    }

    // ── Misc helpers ──────────────────────────────────────────────────────────

    private void notifySelection() {
        if (onSelectionChanged != null) onSelectionChanged.run();
    }

    private static double distToSegment(double px, double py, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double lenSq = dx * dx + dy * dy;
        if (lenSq < 1e-10) return Math.hypot(px - x1, py - y1);
        double t = Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / lenSq));
        return Math.hypot(px - (x1 + t * dx), py - (y1 + t * dy));
    }

    private static ChartDrawing cloneDrawing(ChartDrawing src) {
        if (src.getPoints() == null || src.getPoints().isEmpty()) {
            ChartDrawingService.hydrate(src);
        }
        ChartDrawingProperties srcProps = src.getProperties();
        ChartDrawingProperties copyProps = ChartDrawingProperties.builder()
                .color(srcProps.getColor())
                .lineWidth(srcProps.getLineWidth())
                .fillOpacity(srcProps.getFillOpacity())
                .extendLeft(srcProps.isExtendLeft())
                .extendRight(srcProps.isExtendRight())
                .entryPrice(srcProps.getEntryPrice())
                .stopLoss(srcProps.getStopLoss())
                .takeProfit(srcProps.getTakeProfit())
                .channelWidth(srcProps.getChannelWidth())
                .text(srcProps.getText())
                .fontSize(srcProps.getFontSize())
                .build();
        return ChartDrawing.builder()
                .profile(src.getProfile())
                .symbol(src.getSymbol())
                .timeframe(src.getTimeframe())
                .toolType(src.getToolType())
                .points(new ArrayList<>(src.getPoints()))
                .properties(copyProps)
                .locked(src.isLocked())
                .build();
    }

    private static void offsetCopyPoints(ChartDrawing copy) {
        for (ChartPoint pt : copy.getPoints()) {
            pt.setTime(pt.getTime().plusHours(1));
        }
    }
}
