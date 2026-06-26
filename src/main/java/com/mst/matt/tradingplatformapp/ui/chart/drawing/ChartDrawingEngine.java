package com.mst.matt.tradingplatformapp.ui.chart.drawing;

import com.mst.matt.tradingplatformapp.model.*;
import com.mst.matt.tradingplatformapp.service.ChartDrawingService;
import javafx.scene.canvas.GraphicsContext;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.paint.Color;
import javafx.stage.Window;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manages drawing tool interaction state: creation, selection, editing,
 * context menu, undo/redo, and annotation text editing.
 *
 * <p>Supports:
 * <ul>
 *   <li>Per-drawing property editing (colour, line weight, line style, fill opacity)</li>
 *   <li>Parallel-copy: create an offset copy of the selected line</li>
 *   <li>Mirror: reflect a drawing across a vertical or horizontal axis</li>
 *   <li>Whole-body shape dragging in Select mode</li>
 *   <li>Position tool: individual SL/TP/Entry line drag + whole-shape move</li>
 *   <li>Stable selection-based delete button (stays visible while drawing is selected)</li>
 *   <li>Text/Note box resize handles when selected</li>
 *   <li>Right-click → Properties | Copy | Mirror | Delete | Lock | Undo/Redo</li>
 *   <li>Global show-all / lock-all overrides</li>
 *   <li>Drawing count guard (max 200 per chart)</li>
 * </ul>
 */
public class ChartDrawingEngine {

    public interface Host {
        void requestRender();
        DrawingRenderer.RenderContext currentRenderContext();
        List<OhlcvBar> getBars();
        Window getWindow();
    }

    // ── Drag mode enum ────────────────────────────────────────────────────────
    private enum DragMode {
        NONE,
        ANCHOR,           // dragging a specific anchor point
        WHOLE_SHAPE,      // dragging entire shape
        POSITION_ENTRY,   // dragging Entry line of a position tool
        POSITION_SL,      // dragging SL line
        POSITION_TP,      // dragging TP line
        POSITION_WHOLE,   // dragging entire position block
        TEXT_RESIZE_W,    // resizing text/note box – west edge (left)
        TEXT_RESIZE_E,    // resizing text/note box – east edge (right)
        TEXT_RESIZE_S,    // resizing text/note box – south edge (bottom)
        TEXT_RESIZE_SE    // resizing text/note box – corner SE
    }

    private static final double HIT_THRESHOLD       = 8;
    private static final double HOVER_DELETE_RADIUS  = 10;
    private static final double RESIZE_HANDLE_SIZE   = 7;
    // Pixel tolerance for position-line drag detection
    private static final double POS_LINE_HIT         = 6;

    private final Host host;
    @Getter
    private final List<ChartDrawing> drawings = new ArrayList<>();
    @Getter
    private final DrawingHistoryManager history = new DrawingHistoryManager();

    @Getter
    private ChartDrawingToolType activeTool = ChartDrawingToolType.SELECT;
    @Getter
    private ChartDrawing selected;
    private ChartDrawing inProgress;

    // ── Drag state ────────────────────────────────────────────────────────────
    private int      dragAnchorIndex = -1;
    private DragMode dragMode        = DragMode.NONE;
    private double   dragStartX, dragStartY;         // mouse position at press
    private double[] dragStartPrices;                // snapshot of all anchor prices at press
    private long[]   dragStartTimes;                 // snapshot of all anchor times at press
    private double   dragStartEntry, dragStartSL, dragStartTP; // position price snapshot
    private double   dragStartTextW, dragStartTextH;            // text box size snapshot

    @Setter
    private boolean snapEnabled;
    private boolean isDragging;
    private double  pressX, pressY;

    /** Drawing the mouse is currently hovering over. */
    @Getter
    private ChartDrawing hovered;
    private double mouseX, mouseY;

    /** Snapshot captured at mouse-press, before a drag/move starts. Used for undo. */
    private DrawingHistoryManager.DrawingSnapshot preDragSnapshot;

    // ── Global overrides ─────────────────────────────────────────────────────
    @Getter
    private boolean showAllDrawings      = true;
    @Getter
    private boolean lockAllDrawings      = false;
    private boolean showHoverDeleteButton = true;
    private boolean confirmHoverDelete    = false;

    /** Current per-profile drawing settings used for defaulting new drawings. */
    @Getter
    private GlobalDrawingSettings currentDrawingSettings = new GlobalDrawingSettings();

    // ── Callbacks ─────────────────────────────────────────────────────────────
    @Setter private Consumer<ChartDrawing> onDrawingCreated;
    @Setter private Consumer<ChartDrawing> onDrawingUpdated;
    @Setter private Consumer<ChartDrawing> onDrawingDeleted;
    @Setter private Consumer<ChartDrawing> onCreateTradeFromDrawing;
    @Setter private Consumer<ChartDrawing> onInstantSaveTrade;
    @Setter private Runnable               onSelectionChanged;
    @Setter private Consumer<ChartDrawing> onHistoryRestored;
    @Setter private Consumer<ChartDrawing> onOpenProperties;

    private ContextMenu contextMenu;

    public ChartDrawingEngine(Host host) {
        this.host = host;
        history.setOnDrawingRestored(d -> {
            if (onHistoryRestored != null) onHistoryRestored.accept(d);
            host.requestRender();
        });
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void setDrawings(List<ChartDrawing> list) {
        drawings.clear();
        if (list != null) drawings.addAll(list);
        if (selected != null && !drawings.contains(selected)) selected = null;
    }

    public void setActiveTool(ChartDrawingToolType tool) {
        this.activeTool = tool != null ? tool : ChartDrawingToolType.SELECT;
        inProgress = null;
        if (tool != ChartDrawingToolType.SELECT) selected = null;
    }

    // ── Global drawing settings ───────────────────────────────────────────────

    public void applyGlobalSettings(GlobalDrawingSettings s) {
        if (s == null) return;
        this.currentDrawingSettings = s;
        this.showAllDrawings       = s.isShowAllDrawings();
        this.lockAllDrawings       = s.isLockAllDrawings();
        this.showHoverDeleteButton = s.isShowHoverDeleteButton();
        this.confirmHoverDelete    = s.isConfirmHoverDelete();
        host.requestRender();
    }

    /** Directly triggers a re-render from external code. */
    public void requestRender() { host.requestRender(); }

    public void setShowAllDrawings(boolean v) { showAllDrawings = v; host.requestRender(); }
    public void setLockAllDrawings(boolean v) { lockAllDrawings = v; host.requestRender(); }

    public boolean isDrawingMode() {
        return activeTool != ChartDrawingToolType.SELECT;
    }

    // ── Undo / Redo ───────────────────────────────────────────────────────────

    public void undo() {
        ChartDrawing d = history.undo(drawings);
        selected = d;
        notifySelection();
        host.requestRender();
        if (d != null && onDrawingUpdated != null) onDrawingUpdated.accept(d);
    }

    public void redo() {
        ChartDrawing d = history.redo(drawings);
        selected = d;
        notifySelection();
        host.requestRender();
        if (d != null && onDrawingUpdated != null) onDrawingUpdated.accept(d);
    }

    // ── Mouse event handlers ──────────────────────────────────────────────────

    public boolean handleMouseMoved(MouseEvent e, DrawingRenderer.RenderContext ctx) {
        if (ctx == null) return false;
        mouseX = e.getX();
        mouseY = e.getY();
        ChartDrawing prev = hovered;
        hovered = activeTool == ChartDrawingToolType.SELECT
                ? hitTest(mouseX, mouseY, ctx) : null;
        if (hovered != prev) host.requestRender();
        return false;
    }

    public boolean handleMousePressed(MouseEvent e, DrawingRenderer.RenderContext ctx) {
        if (ctx == null || host.getBars() == null || host.getBars().isEmpty()) return false;
        pressX = e.getX();
        pressY = e.getY();
        isDragging = false;
        dragMode   = DragMode.NONE;

        // ── Stable selection delete button hit (always visible when selected) ──
        if (selected != null && isSelectionDeleteHit(e.getX(), e.getY(), ctx, selected)) {
            doDeleteDrawing(selected);
            return true;
        }

        // ── Hover-delete button hit (only if no selection or different drawing) ──
        if (showHoverDeleteButton && hovered != null && hovered != selected
                && isHoverDeleteHit(e.getX(), e.getY(), ctx, hovered)) {
            doDeleteDrawing(hovered);
            return true;
        }

        // ── Double-click: annotation text editing ──────────────────────────────
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
            // Right-click on empty area deselects
            if (selected != null) {
                selected = null;
                notifySelection();
                host.requestRender();
            }
            return false;
        }

        if (e.getButton() != MouseButton.PRIMARY) return false;

        // ── + Trade button hit on selected position drawing ─────────────────────
        if (selected != null && selected.getToolType().isPositionTool()
                && isTradeButtonHit(e.getX(), e.getY(), ctx)) {
            if (onCreateTradeFromDrawing != null) onCreateTradeFromDrawing.accept(selected);
            return true;
        }

        if (activeTool == ChartDrawingToolType.SELECT) {
            // ── Text/Note resize handles ──────────────────────────────────────
            if (selected != null && isTextResizeTool(selected.getToolType())
                    && !effectiveLock(selected)) {
                DragMode rm = hitTextResizeHandle(selected, e.getX(), e.getY(), ctx);
                if (rm != DragMode.NONE) {
                    dragMode = rm;
                    dragStartX = e.getX(); dragStartY = e.getY();
                    dragStartTextW = selected.getProperties().getTextBoxWidth();
                    dragStartTextH = selected.getProperties().getTextBoxHeight();
                    preDragSnapshot = new DrawingHistoryManager.DrawingSnapshot(selected);
                    return true;
                }
            }

            // ── Position tool line-specific dragging ──────────────────────────
            if (selected != null && selected.getToolType().isPositionTool()
                    && !effectiveLock(selected)) {
                DragMode pm = hitPositionLine(selected, e.getX(), e.getY(), ctx);
                if (pm != DragMode.NONE) {
                    dragMode = pm;
                    dragStartX = e.getX(); dragStartY = e.getY();
                    ChartDrawingProperties p = selected.getProperties();
                    dragStartEntry = p.getEntryPrice() != null ? p.getEntryPrice() : 0;
                    dragStartSL    = p.getStopLoss()   != null ? p.getStopLoss()   : 0;
                    dragStartTP    = p.getTakeProfit()  != null ? p.getTakeProfit() : 0;
                    // Also save time anchors for whole-shape move
                    dragStartTimes  = selected.getPoints().stream().mapToLong(ChartPoint::getTimeEpoch).toArray();
                    dragStartPrices = selected.getPoints().stream().mapToDouble(ChartPoint::getPrice).toArray();
                    preDragSnapshot = new DrawingHistoryManager.DrawingSnapshot(selected);
                    return true;
                }
            }

            // ── Regular anchor drag ────────────────────────────────────────────
            int anchor = hitAnchor(selected, e.getX(), e.getY(), ctx);
            if (anchor >= 0 && selected != null && !effectiveLock(selected)) {
                dragAnchorIndex = anchor;
                dragMode = DragMode.ANCHOR;
                preDragSnapshot = new DrawingHistoryManager.DrawingSnapshot(selected);
                return true;
            }

            // ── Hit-test for selection or whole-body drag ──────────────────────
            ChartDrawing hit = hitTest(e.getX(), e.getY(), ctx);
            if (hit != null && !effectiveLock(hit)) {
                if (hit == selected) {
                    // Same drawing clicked – prepare for whole-shape drag
                    dragMode    = DragMode.WHOLE_SHAPE;
                    dragStartX  = e.getX(); dragStartY = e.getY();
                    dragStartTimes  = hit.getPoints().stream().mapToLong(ChartPoint::getTimeEpoch).toArray();
                    dragStartPrices = hit.getPoints().stream().mapToDouble(ChartPoint::getPrice).toArray();
                    preDragSnapshot = new DrawingHistoryManager.DrawingSnapshot(hit);
                } else {
                    selected = hit;
                    notifySelection();
                    host.requestRender();
                }
                return true;
            }

            // ── Click on empty area – deselect ────────────────────────────────
            if (selected != null) {
                selected = null;
                dragMode = DragMode.NONE;
                notifySelection();
                host.requestRender();
            }
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
        mouseX = e.getX();
        mouseY = e.getY();

        if (activeTool == ChartDrawingToolType.SELECT) {

            // ── Text resize drag ───────────────────────────────────────────────
            if (dragMode == DragMode.TEXT_RESIZE_E || dragMode == DragMode.TEXT_RESIZE_W
                    || dragMode == DragMode.TEXT_RESIZE_S || dragMode == DragMode.TEXT_RESIZE_SE) {
                if (selected != null && !effectiveLock(selected)) {
                    applyTextResize(selected, e.getX(), e.getY());
                    host.requestRender();
                    return true;
                }
            }

            // ── Position line drag ─────────────────────────────────────────────
            if ((dragMode == DragMode.POSITION_ENTRY || dragMode == DragMode.POSITION_SL
                    || dragMode == DragMode.POSITION_TP || dragMode == DragMode.POSITION_WHOLE)
                    && selected != null && !effectiveLock(selected)) {
                applyPositionDrag(selected, e.getX(), e.getY(), ctx);
                host.requestRender();
                return true;
            }

            // ── Anchor drag ────────────────────────────────────────────────────
            if (dragMode == DragMode.ANCHOR && dragAnchorIndex >= 0
                    && selected != null && !effectiveLock(selected)) {
                ChartPoint pt = pointFromMouse(e.getX(), e.getY(), ctx);
                selected.getPoints().set(dragAnchorIndex, pt);
                updatePositionPricesFromAnchors(selected);
                host.requestRender();
                return true;
            }

            // ── Whole-shape drag ───────────────────────────────────────────────
            if (dragMode == DragMode.WHOLE_SHAPE && selected != null
                    && !effectiveLock(selected) && dragStartTimes != null) {
                applyWholeShapeDrag(selected, e.getX(), e.getY(), ctx);
                host.requestRender();
                return true;
            }

            // No active drag mode in SELECT mode → do NOT consume the event.
            // This allows the chart to pan when the user drags on empty space
            // even if a drawing is currently selected.
            return false;
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

        // Handle all drag-end cases
        if (wasDragging && selected != null && dragMode != DragMode.NONE) {
            DragMode endedMode = dragMode;
            dragMode        = DragMode.NONE;
            dragAnchorIndex = -1;
            if (preDragSnapshot != null) {
                history.recordMove(selected, preDragSnapshot);
                preDragSnapshot = null;
            }
            if (onDrawingUpdated != null) onDrawingUpdated.accept(selected);
            dragStartTimes  = null;
            dragStartPrices = null;
            return true;
        }

        if (dragAnchorIndex >= 0 && selected != null && wasDragging) {
            dragAnchorIndex = -1;
            dragMode = DragMode.NONE;
            if (preDragSnapshot != null) {
                history.recordMove(selected, preDragSnapshot);
                preDragSnapshot = null;
            }
            if (onDrawingUpdated != null) onDrawingUpdated.accept(selected);
            return true;
        }

        if (inProgress != null && activeTool != ChartDrawingToolType.SELECT) {
            if (!inProgress.getPoints().isEmpty() && inProgress.getToolType().requiredPoints() > 1) {
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
        dragMode        = DragMode.NONE;
        preDragSnapshot = null;
        return false;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    public void renderDrawings(GraphicsContext gc, DrawingRenderer.RenderContext ctx) {
        if (!showAllDrawings) return;

        for (ChartDrawing d : drawings) {
            boolean isSelected = d == selected;
            boolean isHovered  = d == hovered && !isSelected;
            DrawingRenderer.render(gc, d, ctx, isSelected,
                    activeTool == ChartDrawingToolType.SELECT, isHovered);
        }
        if (inProgress != null) {
            DrawingRenderer.render(gc, inProgress, ctx, true, true, false);
        }

        // ── Stable selection delete button (shown when drawing is selected) ──
        if (selected != null && !effectiveLock(selected)
                && activeTool == ChartDrawingToolType.SELECT) {
            renderSelectionDeleteButton(gc, ctx, selected);
        }

        // ── Hover-delete button (only for non-selected hovered drawings) ──
        if (showHoverDeleteButton && hovered != null && hovered != selected
                && !effectiveLock(hovered)
                && activeTool == ChartDrawingToolType.SELECT) {
            renderHoverDeleteButton(gc, ctx, hovered);
        }

        // ── Text/Note resize handles ──────────────────────────────────────────
        if (selected != null && isTextResizeTool(selected.getToolType())
                && !effectiveLock(selected)
                && activeTool == ChartDrawingToolType.SELECT) {
            renderTextResizeHandles(gc, ctx, selected);
        }
    }

    // ── Stable selection delete button ────────────────────────────────────────

    /**
     * Renders a stable ✕ button at the top-right of the selected drawing's bounding box.
     * Stays visible as long as the drawing is selected.
     */
    private void renderSelectionDeleteButton(GraphicsContext gc,
                                             DrawingRenderer.RenderContext ctx, ChartDrawing d) {
        double[] pos = selectionDeletePos(ctx, d);
        if (pos == null) return;
        double bx = pos[0], by = pos[1];
        double r  = HOVER_DELETE_RADIUS + 2;  // slightly larger than hover delete

        gc.setFill(Color.web("#f85149ee"));
        gc.fillOval(bx - r, by - r, r * 2, r * 2);
        gc.setStroke(Color.web("#ffffff"));
        gc.setLineWidth(2.0);
        gc.strokeLine(bx - 4, by - 4, bx + 4, by + 4);
        gc.strokeLine(bx + 4, by - 4, bx - 4, by + 4);
    }

    /** Returns the [x, y] screen position for the stable selection-delete button. */
    private double[] selectionDeletePos(DrawingRenderer.RenderContext ctx, ChartDrawing d) {
        if (d.getPoints() == null || d.getPoints().isEmpty()) return null;
        // Use bounding box top-right corner
        double maxX = Double.MIN_VALUE, minY = Double.MAX_VALUE;
        for (ChartPoint pt : d.getPoints()) {
            double sx = DrawingRenderer.timeToX(pt.getTime(), ctx);
            double sy = DrawingRenderer.priceToY(pt.getPrice(), ctx);
            if (sx > maxX) maxX = sx;
            if (sy < minY) minY = sy;
        }
        // For position tools, also consider the TP line
        if (d.getToolType().isPositionTool() && d.getProperties() != null) {
            if (d.getPoints().size() >= 2) {
                double rx = DrawingRenderer.timeToX(d.getPoints().get(1).getTime(), ctx);
                if (rx > maxX) maxX = rx;
            }
            Double tp = d.getProperties().getTakeProfit();
            if (tp != null) {
                double sy = DrawingRenderer.priceToY(tp, ctx);
                if (sy < minY) minY = sy;
            }
            Double sl = d.getProperties().getStopLoss();
            if (sl != null) {
                double sy = DrawingRenderer.priceToY(sl, ctx);
                if (sy < minY) minY = sy;
            }
        }
        // Clamp to chart area
        maxX = Math.min(maxX, ctx.right() - 12);
        minY = Math.max(minY, ctx.priceTop() + 12);
        return new double[]{maxX + 14, minY - 6};
    }

    private boolean isSelectionDeleteHit(double mx, double my,
                                         DrawingRenderer.RenderContext ctx, ChartDrawing d) {
        double[] pos = selectionDeletePos(ctx, d);
        if (pos == null) return false;
        return Math.hypot(mx - pos[0], my - pos[1]) < HOVER_DELETE_RADIUS + 4;
    }

    // ── Hover delete button ───────────────────────────────────────────────────

    /**
     * Renders a small × button at the SAME location as the selection delete button
     * (top-right of bounding box) for consistency.  The button is slightly smaller
     * than the selection one so the user can visually distinguish the two states.
     */
    private void renderHoverDeleteButton(GraphicsContext gc, DrawingRenderer.RenderContext ctx,
                                         ChartDrawing d) {
        if (d.getPoints() == null || d.getPoints().isEmpty()) return;
        // Re-use the same bounding-box position as the selection delete button
        double[] pos = selectionDeletePos(ctx, d);
        if (pos == null) return;
        double bx = pos[0], by = pos[1];
        double r  = HOVER_DELETE_RADIUS;  // same radius, slightly less opaque

        gc.setFill(Color.web("#f85149cc"));
        gc.fillOval(bx - r, by - r, r * 2, r * 2);
        gc.setStroke(Color.web("#ffffff"));
        gc.setLineWidth(1.5);
        gc.strokeLine(bx - 4, by - 4, bx + 4, by + 4);
        gc.strokeLine(bx + 4, by - 4, bx - 4, by + 4);
    }

    /**
     * Returns the hover-delete button position — identical to the selection-delete
     * position so the button never "jumps" when the user clicks to select.
     */
    private double[] hoverDeletePos(DrawingRenderer.RenderContext ctx, ChartDrawing d) {
        // Delegate to the same bounding-box calculation used for the selection delete
        return selectionDeletePos(ctx, d);
    }

    private boolean isHoverDeleteHit(double mx, double my, DrawingRenderer.RenderContext ctx,
                                     ChartDrawing d) {
        if (d.getPoints() == null || d.getPoints().isEmpty()) return false;
        double[] pos = hoverDeletePos(ctx, d);
        if (pos == null) return false;
        return Math.hypot(mx - pos[0], my - pos[1]) < HOVER_DELETE_RADIUS + 4;
    }

    // ── Text / Note resize handles ────────────────────────────────────────────

    private static boolean isTextResizeTool(ChartDrawingToolType t) {
        return t == ChartDrawingToolType.TEXT_LABEL
                || t == ChartDrawingToolType.NOTE_ICON
                || t == ChartDrawingToolType.CALLOUT;
    }

    /**
     * Renders resize handles at the edges/corner of a text or note box.
     * Handles: right (E), bottom (S), bottom-right (SE).
     */
    private void renderTextResizeHandles(GraphicsContext gc,
                                          DrawingRenderer.RenderContext ctx, ChartDrawing d) {
        if (d.getPoints() == null || d.getPoints().isEmpty()) return;
        double ax = DrawingRenderer.timeToX(d.getPoints().getFirst().getTime(), ctx);
        double ay = DrawingRenderer.priceToY(d.getPoints().getFirst().getPrice(), ctx);
        double w  = getTextBoxWidth(d);
        double h  = getTextBoxHeight(d);

        // For TEXT_LABEL the box starts at (ax, ay - h + 4)
        // For NOTE_ICON the box starts at (ax, ay)
        double boxX, boxY;
        if (d.getToolType() == ChartDrawingToolType.NOTE_ICON) {
            boxX = ax; boxY = ay;
        } else if (d.getToolType() == ChartDrawingToolType.CALLOUT && d.getPoints().size() >= 2) {
            boxX = DrawingRenderer.timeToX(d.getPoints().get(1).getTime(), ctx);
            boxY = DrawingRenderer.priceToY(d.getPoints().get(1).getPrice(), ctx);
        } else {
            boxX = ax; boxY = ay - h + 4;
        }

        double hs = RESIZE_HANDLE_SIZE;
        // East handle (right edge centre)
        double ex = boxX + w, ey = boxY + h / 2;
        // South handle (bottom centre)
        double sx = boxX + w / 2, sy = boxY + h;
        // SE handle (bottom-right corner)
        double sex = boxX + w, sey = boxY + h;

        gc.setFill(Color.web("#388bfd"));
        gc.setStroke(Color.web("#ffffff"));
        gc.setLineWidth(1.0);
        for (double[] handle : new double[][]{{ex, ey}, {sx, sy}, {sex, sey}}) {
            gc.fillRect(handle[0] - hs / 2, handle[1] - hs / 2, hs, hs);
            gc.strokeRect(handle[0] - hs / 2, handle[1] - hs / 2, hs, hs);
        }
    }

    private DragMode hitTextResizeHandle(ChartDrawing d, double mx, double my,
                                          DrawingRenderer.RenderContext ctx) {
        if (d.getPoints() == null || d.getPoints().isEmpty()) return DragMode.NONE;
        double ax = DrawingRenderer.timeToX(d.getPoints().getFirst().getTime(), ctx);
        double ay = DrawingRenderer.priceToY(d.getPoints().getFirst().getPrice(), ctx);
        double w  = getTextBoxWidth(d);
        double h  = getTextBoxHeight(d);

        double boxX, boxY;
        if (d.getToolType() == ChartDrawingToolType.NOTE_ICON) {
            boxX = ax; boxY = ay;
        } else if (d.getToolType() == ChartDrawingToolType.CALLOUT && d.getPoints().size() >= 2) {
            boxX = DrawingRenderer.timeToX(d.getPoints().get(1).getTime(), ctx);
            boxY = DrawingRenderer.priceToY(d.getPoints().get(1).getPrice(), ctx);
        } else {
            boxX = ax; boxY = ay - h + 4;
        }

        double tol = RESIZE_HANDLE_SIZE;
        double ex = boxX + w, ey = boxY + h / 2;
        double sx = boxX + w / 2, sy = boxY + h;
        double sex = boxX + w, sey = boxY + h;

        if (Math.abs(mx - sex) < tol && Math.abs(my - sey) < tol) return DragMode.TEXT_RESIZE_SE;
        if (Math.abs(mx - ex)  < tol && Math.abs(my - ey)  < tol) return DragMode.TEXT_RESIZE_E;
        if (Math.abs(mx - sx)  < tol && Math.abs(my - sy)  < tol) return DragMode.TEXT_RESIZE_S;
        return DragMode.NONE;
    }

    private void applyTextResize(ChartDrawing d, double mx, double my) {
        double dw = mx - dragStartX;
        double dh = my - dragStartY;
        double minW = 40, minH = 20;
        ChartDrawingProperties p = d.getProperties();
        switch (dragMode) {
            case TEXT_RESIZE_E -> p.setTextBoxWidth(Math.max(minW, dragStartTextW + dw));
            case TEXT_RESIZE_S -> p.setTextBoxHeight(Math.max(minH, dragStartTextH + dh));
            case TEXT_RESIZE_SE -> {
                p.setTextBoxWidth(Math.max(minW, dragStartTextW + dw));
                p.setTextBoxHeight(Math.max(minH, dragStartTextH + dh));
            }
            default -> {}
        }
    }

    private double getTextBoxWidth(ChartDrawing d) {
        double stored = d.getProperties().getTextBoxWidth();
        if (stored > 0) return stored;
        // Default widths — NOTE_ICON uses a wider default for readability
        if (d.getToolType() == ChartDrawingToolType.NOTE_ICON) return 120;
        String text = d.getProperties().getText();
        double fs = d.getProperties().getFontSize() > 0 ? d.getProperties().getFontSize() : 12;
        // Wrap long text: cap auto-width at 240 px
        int len = (text != null ? text.length() : 4);
        return Math.min(240, Math.max(80, len * (fs * 0.62) + 16));
    }

    private double getTextBoxHeight(ChartDrawing d) {
        double stored = d.getProperties().getTextBoxHeight();
        if (stored > 0) return stored;
        // NOTE_ICON: taller default to show a meaningful preview
        if (d.getToolType() == ChartDrawingToolType.NOTE_ICON) return 80;
        // TEXT_LABEL: compute height based on wrapped lines
        String text = d.getProperties().getText();
        double fs = d.getProperties().getFontSize() > 0 ? d.getProperties().getFontSize() : 12;
        double boxW = getTextBoxWidth(d);
        double charW = fs * 0.62;
        int charsPerLine = Math.max(1, (int)((boxW - 16) / charW));
        int lineCount = (int) Math.ceil((double)(text != null ? text.length() : 4) / charsPerLine);
        // Also count explicit newlines
        if (text != null) lineCount += (int) text.chars().filter(c -> c == '\n').count();
        lineCount = Math.max(1, lineCount);
        return Math.max(fs + 12, lineCount * (fs + 4) + 10);
    }

    // ── Position tool line drag ───────────────────────────────────────────────

    /**
     * Detects which part of a position drawing was clicked for dragging.
     * Returns the appropriate DragMode.
     */
    private DragMode hitPositionLine(ChartDrawing d, double mx, double my,
                                     DrawingRenderer.RenderContext ctx) {
        if (d.getPoints().size() < 2 || d.getProperties() == null) return DragMode.NONE;
        double x1 = DrawingRenderer.timeToX(d.getPoints().get(0).getTime(), ctx);
        double x2 = DrawingRenderer.timeToX(d.getPoints().get(1).getTime(), ctx);
        double left = Math.min(x1, x2), right = Math.max(x1, x2) + 60;
        if (mx < left - 10 || mx > right) return DragMode.NONE;

        ChartDrawingProperties p = d.getProperties();
        double tol = POS_LINE_HIT;

        // Check individual lines
        if (p.getEntryPrice() != null) {
            double yEntry = DrawingRenderer.priceToY(p.getEntryPrice(), ctx);
            if (Math.abs(my - yEntry) < tol && mx >= left && mx <= right)
                return DragMode.POSITION_ENTRY;
        }
        if (p.getStopLoss() != null) {
            double ySL = DrawingRenderer.priceToY(p.getStopLoss(), ctx);
            if (Math.abs(my - ySL) < tol && mx >= left && mx <= right)
                return DragMode.POSITION_SL;
        }
        if (p.getTakeProfit() != null) {
            double yTP = DrawingRenderer.priceToY(p.getTakeProfit(), ctx);
            if (Math.abs(my - yTP) < tol && mx >= left && mx <= right)
                return DragMode.POSITION_TP;
        }

        // Click in the body area → whole-shape drag
        if (mx >= left && mx <= right) return DragMode.POSITION_WHOLE;
        return DragMode.NONE;
    }

    private void applyPositionDrag(ChartDrawing d, double mx, double my,
                                    DrawingRenderer.RenderContext ctx) {
        double priceDelta = yToPrice(my, ctx) - yToPrice(dragStartY, ctx);
        double timeDeltaMs = xToTimeEpoch(mx, ctx) - xToTimeEpoch(dragStartX, ctx);

        ChartDrawingProperties p = d.getProperties();
        switch (dragMode) {
            case POSITION_ENTRY -> {
                if (p.getEntryPrice() != null)
                    p.setEntryPrice(dragStartEntry + priceDelta);
            }
            case POSITION_SL -> {
                if (p.getStopLoss() != null)
                    p.setStopLoss(dragStartSL + priceDelta);
            }
            case POSITION_TP -> {
                if (p.getTakeProfit() != null)
                    p.setTakeProfit(dragStartTP + priceDelta);
            }
            case POSITION_WHOLE -> {
                // Move all three prices + time anchors together
                if (p.getEntryPrice() != null) p.setEntryPrice(dragStartEntry + priceDelta);
                if (p.getStopLoss()   != null) p.setStopLoss(dragStartSL + priceDelta);
                if (p.getTakeProfit() != null) p.setTakeProfit(dragStartTP + priceDelta);
                // Shift time anchors
                List<ChartPoint> pts = d.getPoints();
                for (int i = 0; i < pts.size() && i < dragStartTimes.length; i++) {
                    pts.get(i).setTimeEpoch(dragStartTimes[i] + (long) timeDeltaMs);
                }
            }
            default -> {}
        }
    }

    // ── Whole-shape drag ──────────────────────────────────────────────────────

    private void applyWholeShapeDrag(ChartDrawing d, double mx, double my,
                                      DrawingRenderer.RenderContext ctx) {
        double priceDelta  = yToPrice(my, ctx) - yToPrice(dragStartY, ctx);
        long   timeDeltaMs = (long)(xToTimeEpoch(mx, ctx) - xToTimeEpoch(dragStartX, ctx));

        List<ChartPoint> pts = d.getPoints();
        for (int i = 0; i < pts.size() && i < dragStartTimes.length; i++) {
            pts.get(i).setTimeEpoch(dragStartTimes[i] + timeDeltaMs);
            pts.get(i).setPrice(dragStartPrices[i] + priceDelta);
        }

        // Also shift position prices if relevant
        if (d.getToolType().isPositionTool() && d.getProperties() != null) {
            ChartDrawingProperties p = d.getProperties();
            if (p.getEntryPrice() != null) p.setEntryPrice(dragStartEntry + priceDelta);
            if (p.getStopLoss()   != null) p.setStopLoss(dragStartSL    + priceDelta);
            if (p.getTakeProfit() != null) p.setTakeProfit(dragStartTP  + priceDelta);
        }
    }

    // ── Coordinate helpers (price/time from screen coords) ───────────────────

    private double yToPrice(double y, DrawingRenderer.RenderContext ctx) {
        double range = Math.max(ctx.maxPrice() - ctx.minPrice(), 1e-10);
        return ctx.minPrice() + range * (1.0 - (y - ctx.priceTop()) / ctx.priceH());
    }

    private double xToTimeEpoch(double x, DrawingRenderer.RenderContext ctx) {
        double barW = ctx.plotWidth() / Math.max(1, ctx.visibleBars());
        int idx = (int)((x - ctx.left()) / barW) + ctx.startBarIndex();
        idx = Math.max(0, Math.min(idx, host.getBars().size() - 1));
        return host.getBars().get(idx).getOpenTime()
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant().toEpochMilli();
    }

    // ── Drawing manipulation ──────────────────────────────────────────────────

    public void deleteSelected() {
        if (selected == null) return;
        doDeleteDrawing(selected);
    }

    private void doDeleteDrawing(ChartDrawing d) {
        if (d == null) return;
        if (confirmHoverDelete) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                    "Delete this drawing?", ButtonType.YES, ButtonType.NO);
            alert.setTitle("Delete Drawing");
            alert.setHeaderText(null);
            if (host.getWindow() != null) {
                try { alert.initOwner(host.getWindow()); } catch (Exception ignored) {}
            }
            alert.showAndWait().filter(b -> b == ButtonType.YES).ifPresent(b -> performDelete(d));
        } else {
            performDelete(d);
        }
    }

    private void performDelete(ChartDrawing d) {
        history.recordDelete(d);
        drawings.remove(d);
        if (onDrawingDeleted != null) onDrawingDeleted.accept(d);
        if (selected == d) { selected = null; notifySelection(); }
        if (hovered  == d) hovered = null;
        host.requestRender();
    }

    public void duplicateSelected() {
        if (selected == null) return;
        if (drawings.size() >= ChartDrawingService.MAX_DRAWINGS_PER_CHART) {
            showMaxDrawingsWarning(); return;
        }
        ChartDrawing copy = cloneDrawing(selected);
        copy.setId(null);
        offsetCopyPoints(copy);
        drawings.add(copy);
        history.recordCreate(copy);
        if (onDrawingCreated != null) onDrawingCreated.accept(copy);
        selected = copy;
        host.requestRender();
    }

    public void parallelCopySelected(double priceDelta) {
        if (selected == null) return;
        if (drawings.size() >= ChartDrawingService.MAX_DRAWINGS_PER_CHART) {
            showMaxDrawingsWarning(); return;
        }
        ChartDrawing copy = cloneDrawing(selected);
        copy.setId(null);
        for (ChartPoint pt : copy.getPoints()) {
            pt.setPrice(pt.getPrice() + priceDelta);
        }
        copy.getProperties().setParallelOffset(priceDelta);
        drawings.add(copy);
        history.recordCreate(copy);
        if (onDrawingCreated != null) onDrawingCreated.accept(copy);
        selected = copy;
        host.requestRender();
    }

    public void mirrorSelected(String axis) {
        if (selected == null) return;
        if (drawings.size() >= ChartDrawingService.MAX_DRAWINGS_PER_CHART) {
            showMaxDrawingsWarning(); return;
        }
        if (selected.getPoints() == null || selected.getPoints().isEmpty()) return;

        ChartDrawing copy = cloneDrawing(selected);
        copy.setId(null);
        copy.getProperties().setMirrorAxis(axis);

        if ("VERTICAL".equalsIgnoreCase(axis)) {
            long axisEpoch = selected.getPoints().getFirst().getTimeEpoch();
            for (ChartPoint pt : copy.getPoints()) {
                long delta = pt.getTimeEpoch() - axisEpoch;
                pt.setTimeEpoch(axisEpoch - delta);
            }
        } else {
            double axisPrice = selected.getPoints().getFirst().getPrice();
            for (ChartPoint pt : copy.getPoints()) {
                double delta = pt.getPrice() - axisPrice;
                pt.setPrice(axisPrice - delta);
            }
        }

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

    public void instantSaveSelectedPosition() {
        if (selected != null && selected.getToolType().isPositionTool()
                && onInstantSaveTrade != null) {
            onInstantSaveTrade.accept(selected);
        }
    }

    private boolean effectiveLock(ChartDrawing d) {
        return lockAllDrawings || (d != null && d.isLocked());
    }

    // ── Annotation text editing ───────────────────────────────────────────────

    private void startTextEditing(ChartDrawing d, DrawingRenderer.RenderContext ctx) {
        if (d == null || d.getProperties() == null) return;
        DrawingHistoryManager.DrawingSnapshot before = new DrawingHistoryManager.DrawingSnapshot(d);
        switch (d.getToolType()) {
            case NOTE_ICON -> openNoteEditDialog(d, before);
            case TEXT_LABEL, CALLOUT -> openInlineTextEditor(d, ctx, before);
            default -> {}
        }
    }

    /**
     * Opens a full-featured multi-line text editor for a Note drawing.
     * The dialog shows the complete text in a scrollable TextArea, supports
     * word-wrap, and lets the user resize the note's bounding box.
     */
    private void openNoteEditDialog(ChartDrawing d, DrawingHistoryManager.DrawingSnapshot before) {
        String current = d.getProperties().getText() != null ? d.getProperties().getText() : "";

        Stage stage = new Stage();
        stage.setTitle("✏ Edit Note");
        stage.setResizable(true);
        if (host.getWindow() != null) {
            try { stage.initOwner(host.getWindow()); } catch (Exception ignored) {}
            stage.initModality(Modality.APPLICATION_MODAL);
        }

        // ── Text area (multi-line, wrapping) ───────────────────────────────────
        TextArea textArea = new TextArea(current);
        textArea.setWrapText(true);
        textArea.setPrefRowCount(8);
        textArea.setPrefColumnCount(40);
        textArea.setStyle("-fx-control-inner-background:#161b22; -fx-text-fill:#e6edf3;"
                + "-fx-background-color:#161b22; -fx-border-color:#30363d;"
                + "-fx-font-size:13px; -fx-padding:8;");

        // ── Char counter ───────────────────────────────────────────────────────
        Label counter = new Label("0 chars");
        counter.setStyle("-fx-text-fill:#8b949e; -fx-font-size:11px;");
        textArea.textProperty().addListener((obs, o, n) ->
                counter.setText((n == null ? 0 : n.length()) + " chars"));
        counter.setText(current.length() + " chars");

        // ── Buttons ────────────────────────────────────────────────────────────
        Button saveBtn   = new Button("💾 Save");
        Button cancelBtn = new Button("✕ Cancel");
        saveBtn.setStyle("-fx-background-color:#2a623d; -fx-text-fill:#e6edf3;"
                + "-fx-border-color:#3fb950; -fx-border-radius:4; -fx-background-radius:4;"
                + "-fx-padding:6 16; -fx-cursor:hand;");
        cancelBtn.setStyle("-fx-background-color:#21262d; -fx-text-fill:#8b949e;"
                + "-fx-border-color:#30363d; -fx-border-radius:4; -fx-background-radius:4;"
                + "-fx-padding:6 16; -fx-cursor:hand;");

        saveBtn.setOnAction(e -> {
            d.getProperties().setText(textArea.getText());
            history.recordModify(d, before);
            if (onDrawingUpdated != null) onDrawingUpdated.accept(d);
            host.requestRender();
            stage.close();
        });
        cancelBtn.setOnAction(e -> stage.close());

        // ── Layout ─────────────────────────────────────────────────────────────
        HBox btnBar = new HBox(8, counter, new Region(), saveBtn, cancelBtn);
        HBox.setHgrow(btnBar.getChildren().get(1), Priority.ALWAYS);
        btnBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        btnBar.setPadding(new Insets(8, 0, 0, 0));

        Label hint = new Label("Tip: Shift+Enter for new line. Text wraps to fit the note box.");
        hint.setStyle("-fx-text-fill:#8b949e; -fx-font-size:10px;");
        hint.setWrapText(true);

        VBox root = new VBox(8, textArea, hint, btnBar);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color:#0d1117;");
        VBox.setVgrow(textArea, Priority.ALWAYS);

        Scene scene = new Scene(root, 460, 280);
        scene.setFill(javafx.scene.paint.Color.web("#0d1117"));
        // Keyboard shortcut: Ctrl+Enter = Save
        scene.setOnKeyPressed(ev -> {
            if (ev.isControlDown()
                    && ev.getCode() == javafx.scene.input.KeyCode.ENTER) {
                saveBtn.fire();
            } else if (ev.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                cancelBtn.fire();
            }
        });

        stage.setScene(scene);
        stage.showAndWait();
    }

    private void openInlineTextEditor(ChartDrawing d, DrawingRenderer.RenderContext ctx,
                                       DrawingHistoryManager.DrawingSnapshot before) {
        String current = d.getProperties().getText() != null ? d.getProperties().getText() : "";
        d.getProperties().setEditing(true);
        host.requestRender();

        Stage stage = new Stage();
        stage.setTitle("✏ Edit " + d.getToolType().displayName());
        stage.setResizable(true);
        if (host.getWindow() != null) {
            try { stage.initOwner(host.getWindow()); } catch (Exception ignored) {}
            stage.initModality(Modality.APPLICATION_MODAL);
        }

        // Multi-line TextArea with word-wrap
        TextArea textArea = new TextArea(current);
        textArea.setWrapText(true);
        textArea.setPrefRowCount(5);
        textArea.setPrefColumnCount(35);
        textArea.setStyle("-fx-control-inner-background:#161b22; -fx-text-fill:#e6edf3;"
                + "-fx-background-color:#161b22; -fx-border-color:#30363d;"
                + "-fx-font-size:13px; -fx-padding:8;");

        Button saveBtn   = new Button("💾 Save");
        Button cancelBtn = new Button("✕ Cancel");
        saveBtn.setStyle("-fx-background-color:#2a623d; -fx-text-fill:#e6edf3;"
                + "-fx-border-color:#3fb950; -fx-border-radius:4; -fx-background-radius:4;"
                + "-fx-padding:6 16; -fx-cursor:hand;");
        cancelBtn.setStyle("-fx-background-color:#21262d; -fx-text-fill:#8b949e;"
                + "-fx-border-color:#30363d; -fx-border-radius:4; -fx-background-radius:4;"
                + "-fx-padding:6 16; -fx-cursor:hand;");

        saveBtn.setOnAction(e -> {
            String newText = textArea.getText();
            d.getProperties().setText(newText.isBlank() ? "Text" : newText);
            d.getProperties().setEditing(false);
            history.recordModify(d, before);
            if (onDrawingUpdated != null) onDrawingUpdated.accept(d);
            host.requestRender();
            stage.close();
        });
        cancelBtn.setOnAction(e -> {
            d.getProperties().setEditing(false);
            host.requestRender();
            stage.close();
        });
        stage.setOnHidden(e -> {
            d.getProperties().setEditing(false);
            host.requestRender();
        });

        HBox btnBar = new HBox(8, new Region(), saveBtn, cancelBtn);
        HBox.setHgrow(btnBar.getChildren().get(0), Priority.ALWAYS);
        btnBar.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        btnBar.setPadding(new Insets(8, 0, 0, 0));

        Label hint = new Label("Ctrl+Enter to save · Esc to cancel · Shift+Enter for new line");
        hint.setStyle("-fx-text-fill:#8b949e; -fx-font-size:10px;");

        VBox root = new VBox(8, textArea, hint, btnBar);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color:#0d1117;");
        VBox.setVgrow(textArea, Priority.ALWAYS);

        Scene scene = new Scene(root, 380, 220);
        scene.setFill(javafx.scene.paint.Color.web("#0d1117"));
        scene.setOnKeyPressed(ev -> {
            if (ev.isControlDown()
                    && ev.getCode() == javafx.scene.input.KeyCode.ENTER) {
                saveBtn.fire();
            } else if (ev.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                cancelBtn.fire();
            }
        });

        stage.setScene(scene);
        stage.showAndWait();
    }

    private static boolean isAnnotationTool(ChartDrawingToolType t) {
        return t == ChartDrawingToolType.TEXT_LABEL
                || t == ChartDrawingToolType.CALLOUT
                || t == ChartDrawingToolType.NOTE_ICON;
    }

    // ── Finalize drawing ──────────────────────────────────────────────────────

    private void finalizeInProgress() {
        if (inProgress == null) return;
        if (drawings.size() >= ChartDrawingService.MAX_DRAWINGS_PER_CHART) {
            showMaxDrawingsWarning();
            inProgress = null;
            return;
        }
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
        // Issue 7.2: pass current per-profile settings so defaults reflect the profile
        ChartDrawingProperties props = ChartDrawingProperties.defaultsFor(tool, currentDrawingSettings);
        if (tool.isPositionTool()) {
            double entry  = pt.getPrice();
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
            double p2    = d.getPoints().get(1).getPrice();
            double entry = props.getEntryPrice();
            if (d.getToolType() == ChartDrawingToolType.LONG_POSITION) {
                if (p2 < entry) props.setStopLoss(p2);
                else            props.setTakeProfit(p2);
            } else {
                if (p2 > entry) props.setStopLoss(p2);
                else            props.setTakeProfit(p2);
            }
        }
    }

    // ── Coordinate helpers ────────────────────────────────────────────────────

    private ChartPoint pointFromMouse(double x, double y, DrawingRenderer.RenderContext ctx) {
        int barIdx = DrawingCoordinateMapper.xToBarIndex(x, ctx.left(), ctx.plotWidth(),
                ctx.startBarIndex(), ctx.visibleBars());
        LocalDateTime time = DrawingCoordinateMapper.barIndexToTime(host.getBars(), barIdx);
        double price = DrawingCoordinateMapper.yToPrice(y, ctx.priceTop(), ctx.priceH(),
                ctx.maxPrice(), ctx.minPrice());
        ChartPoint pt = ChartPoint.of(time, price);
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

    private boolean isNearDrawing(ChartDrawing d, double x, double y,
                                   DrawingRenderer.RenderContext ctx) {
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
            case RECTANGLE, FLAT_CHANNEL -> { return isNearRect(d, x, y, ctx); }
            case TRIANGLE               -> { return isNearPolygon(d, x, y, ctx); }
            case ELLIPSE                -> { return isNearEllipse(d, x, y, ctx); }
            case TEXT_LABEL, NOTE_ICON -> {
                double ax = DrawingRenderer.timeToX(d.getPoints().getFirst().getTime(), ctx);
                double ay = DrawingRenderer.priceToY(d.getPoints().getFirst().getPrice(), ctx);
                double w  = getTextBoxWidth(d), h = getTextBoxHeight(d);
                // TEXT_LABEL box starts at (ax, ay-h+4)
                double boxY = d.getToolType() == ChartDrawingToolType.NOTE_ICON ? ay : ay - h + 4;
                return x >= ax && x <= ax + w && y >= boxY && y <= boxY + h;
            }
            case CALLOUT -> {
                if (d.getPoints().size() < 2) {
                    double ax = DrawingRenderer.timeToX(d.getPoints().getFirst().getTime(), ctx);
                    double ay = DrawingRenderer.priceToY(d.getPoints().getFirst().getPrice(), ctx);
                    double w = getTextBoxWidth(d), h = getTextBoxHeight(d);
                    return x >= ax && x <= ax + w && y >= ay - 30 && y <= ay + h;
                }
                double bx = DrawingRenderer.timeToX(d.getPoints().get(1).getTime(), ctx);
                double by = DrawingRenderer.priceToY(d.getPoints().get(1).getPrice(), ctx);
                double w  = getTextBoxWidth(d), h = getTextBoxHeight(d);
                return x >= bx && x <= bx + w && y >= by && y <= by + h;
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

    private boolean isNearRect(ChartDrawing d, double x, double y,
                                DrawingRenderer.RenderContext ctx) {
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

    private boolean isNearPolygon(ChartDrawing d, double x, double y,
                                   DrawingRenderer.RenderContext ctx) {
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

    private boolean isNearEllipse(ChartDrawing d, double x, double y,
                                   DrawingRenderer.RenderContext ctx) {
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

    private int hitAnchor(ChartDrawing d, double x, double y, DrawingRenderer.RenderContext ctx) {
        if (d == null || d.getPoints() == null) return -1;
        for (int i = 0; i < d.getPoints().size(); i++) {
            double px = DrawingRenderer.timeToX(d.getPoints().get(i).getTime(), ctx);
            double py = DrawingRenderer.priceToY(d.getPoints().get(i).getPrice(), ctx);
            if (Math.hypot(x - px, y - py) < HIT_THRESHOLD) return i;
        }
        return -1;
    }

    // ── Context menu ──────────────────────────────────────────────────────────

    private void showContextMenu(double screenX, double screenY, ChartDrawing d) {
        if (contextMenu != null) contextMenu.hide();
        contextMenu = new ContextMenu();
        contextMenu.setStyle("-fx-background-color:#1c2128; -fx-border-color:#30363d;");

        MenuItem propsItem = new MenuItem("🎨 Properties…");
        propsItem.setOnAction(e -> {
            if (onOpenProperties != null) onOpenProperties.accept(d);
        });

        MenuItem parallelItem = new MenuItem("∥ Parallel Copy…");
        parallelItem.setOnAction(e -> showParallelCopyDialog(d));

        Menu mirrorMenu = new Menu("⇄ Mirror");
        MenuItem mirrorV = new MenuItem("Vertical Axis");
        mirrorV.setOnAction(e -> { selected = d; mirrorSelected("VERTICAL"); });
        MenuItem mirrorH = new MenuItem("Horizontal Axis");
        mirrorH.setOnAction(e -> { selected = d; mirrorSelected("HORIZONTAL"); });
        mirrorMenu.getItems().addAll(mirrorV, mirrorH);

        MenuItem del  = new MenuItem("🗑 Delete");
        del.setOnAction(e -> doDeleteDrawing(d));
        MenuItem dup  = new MenuItem("⧉ Duplicate");
        dup.setOnAction(e -> { selected = d; duplicateSelected(); });
        MenuItem lock = new MenuItem(effectiveLock(d) ? "🔓 Unlock" : "🔒 Lock");
        lock.setOnAction(e -> { selected = d; toggleLockSelected(); });
        MenuItem undoItem = new MenuItem("↩ Undo  Ctrl+Z");
        undoItem.setDisable(!history.canUndo());
        undoItem.setOnAction(e -> undo());
        MenuItem redoItem = new MenuItem("↪ Redo  Ctrl+Y");
        redoItem.setDisable(!history.canRedo());
        redoItem.setOnAction(e -> redo());

        contextMenu.getItems().addAll(
                propsItem, new SeparatorMenuItem(),
                parallelItem, mirrorMenu,
                new SeparatorMenuItem(),
                del, dup, lock,
                new SeparatorMenuItem(),
                undoItem, redoItem);

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

    private void showParallelCopyDialog(ChartDrawing d) {
        TextInputDialog dlg = new TextInputDialog("0");
        dlg.setTitle("Parallel Copy");
        dlg.setHeaderText("Enter price offset for the parallel copy");
        dlg.setContentText("Offset (price units):");
        if (host.getWindow() != null) {
            try { dlg.initOwner(host.getWindow()); } catch (Exception ignored) {}
        }
        dlg.showAndWait().ifPresent(txt -> {
            try {
                double offset = Double.parseDouble(txt.trim());
                selected = d;
                parallelCopySelected(offset);
            } catch (NumberFormatException ex) { /* ignore */ }
        });
    }

    private void showMaxDrawingsWarning() {
        Alert a = new Alert(Alert.AlertType.WARNING,
                "Maximum drawing limit (" + ChartDrawingService.MAX_DRAWINGS_PER_CHART
                        + ") reached. Please delete some drawings first.",
                ButtonType.OK);
        a.setTitle("Drawing Limit");
        a.setHeaderText(null);
        if (host.getWindow() != null) {
            try { a.initOwner(host.getWindow()); } catch (Exception ignored) {}
        }
        a.showAndWait();
    }

    // ── Misc helpers ──────────────────────────────────────────────────────────

    private void notifySelection() {
        if (onSelectionChanged != null) onSelectionChanged.run();
    }

    private static double distToSegment(double px, double py,
                                         double x1, double y1,
                                         double x2, double y2) {
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
                .lineStyle(srcProps.getLineStyle())
                .fillOpacity(srcProps.getFillOpacity())
                .extendLeft(srcProps.isExtendLeft())
                .extendRight(srcProps.isExtendRight())
                .entryPrice(srcProps.getEntryPrice())
                .stopLoss(srcProps.getStopLoss())
                .takeProfit(srcProps.getTakeProfit())
                .channelWidth(srcProps.getChannelWidth())
                .text(srcProps.getText())
                .fontSize(srcProps.getFontSize())
                .textBoxWidth(srcProps.getTextBoxWidth())
                .textBoxHeight(srcProps.getTextBoxHeight())
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
        long oneHour = 3_600_000L;
        for (ChartPoint pt : copy.getPoints()) {
            pt.setTimeEpoch(pt.getTimeEpoch() + oneHour);
        }
    }
}
