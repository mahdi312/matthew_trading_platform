package com.mst.matt.tradingplatformapp.ui.chart.drawing;

import com.mst.matt.tradingplatformapp.model.*;
import com.mst.matt.tradingplatformapp.service.ChartDrawingService;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
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
 *   <li>Hover highlight + quick-delete button (×)</li>
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

    private static final double HIT_THRESHOLD      = 8;
    private static final double HOVER_DELETE_RADIUS = 10;

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
    private int dragAnchorIndex = -1;
    @Setter
    private boolean snapEnabled;
    private boolean isDragging;
    private double pressX, pressY;

    /** Drawing the mouse is currently hovering over. */
    @Getter
    private ChartDrawing hovered;
    private double mouseX, mouseY;

    /** Snapshot captured at mouse-press, before a drag/move starts. Used for undo. */
    private DrawingHistoryManager.DrawingSnapshot preDragSnapshot;

    // ── Global overrides ─────────────────────────────────────────────────────
    @Getter
    private boolean showAllDrawings = true;
    @Getter
    private boolean lockAllDrawings = false;
    private boolean showHoverDeleteButton = true;
    private boolean confirmHoverDelete    = false;

    // ── Callbacks ─────────────────────────────────────────────────────────────
    @Setter
    private Consumer<ChartDrawing> onDrawingCreated;
    @Setter
    private Consumer<ChartDrawing> onDrawingUpdated;
    @Setter
    private Consumer<ChartDrawing> onDrawingDeleted;
    @Setter
    private Consumer<ChartDrawing> onCreateTradeFromDrawing;
    @Setter
    private Consumer<ChartDrawing> onInstantSaveTrade;
    @Setter
    private Runnable onSelectionChanged;
    @Setter
    private Consumer<ChartDrawing> onHistoryRestored;
    @Setter
    private Consumer<ChartDrawing> onOpenProperties;   // opens the per-drawing properties dialog

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

        // Hover-delete button hit
        if (showHoverDeleteButton && hovered != null && isHoverDeleteHit(e.getX(), e.getY(), ctx, hovered)) {
            doDeleteDrawing(hovered);
            return true;
        }

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

        // + Trade button hit on selected position drawing
        if (selected != null && selected.getToolType().isPositionTool()
                && isTradeButtonHit(e.getX(), e.getY(), ctx)) {
            if (onCreateTradeFromDrawing != null) onCreateTradeFromDrawing.accept(selected);
            return true;
        }

        if (activeTool == ChartDrawingToolType.SELECT) {
            int anchor = hitAnchor(selected, e.getX(), e.getY(), ctx);
            if (anchor >= 0 && selected != null && !effectiveLock(selected)) {
                dragAnchorIndex = anchor;
                preDragSnapshot = new DrawingHistoryManager.DrawingSnapshot(selected);
                return true;
            }
            ChartDrawing hit = hitTest(e.getX(), e.getY(), ctx);
            selected = hit;
            notifySelection();
            host.requestRender();
            return hit != null;
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
            if (dragAnchorIndex >= 0 && selected != null && !effectiveLock(selected)) {
                ChartPoint pt = pointFromMouse(e.getX(), e.getY(), ctx);
                selected.getPoints().set(dragAnchorIndex, pt);
                updatePositionPricesFromAnchors(selected);
                host.requestRender();
                return true;
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
        if (dragAnchorIndex >= 0 && selected != null && isDragging) {
            dragAnchorIndex = -1;
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
        preDragSnapshot = null;
        return false;
    }

    // ── Render ────────────────────────────────────────────────────────────────

    public void renderDrawings(GraphicsContext gc, DrawingRenderer.RenderContext ctx) {
        if (!showAllDrawings) return;   // global hide

        for (ChartDrawing d : drawings) {
            boolean isSelected = d == selected;
            boolean isHovered  = d == hovered && !isSelected;
            DrawingRenderer.render(gc, d, ctx, isSelected,
                    activeTool == ChartDrawingToolType.SELECT, isHovered);
        }
        if (inProgress != null) {
            DrawingRenderer.render(gc, inProgress, ctx, true, true, false);
        }

        // Hover-delete button overlay
        if (showHoverDeleteButton && hovered != null && !effectiveLock(hovered)
                && activeTool == ChartDrawingToolType.SELECT) {
            renderHoverDeleteButton(gc, ctx, hovered);
        }
    }

    /** Renders a small × button near the hovered drawing's first anchor. */
    private void renderHoverDeleteButton(GraphicsContext gc, DrawingRenderer.RenderContext ctx,
                                         ChartDrawing d) {
        if (d.getPoints() == null || d.getPoints().isEmpty()) return;
        double[] pos = hoverDeletePos(ctx, d);
        double bx = pos[0], by = pos[1];
        double r = HOVER_DELETE_RADIUS;

        gc.setFill(Color.web("#f85149cc"));
        gc.fillOval(bx - r, by - r, r * 2, r * 2);
        gc.setStroke(Color.web("#ffffff"));
        gc.setLineWidth(1.5);
        gc.strokeLine(bx - 4, by - 4, bx + 4, by + 4);
        gc.strokeLine(bx + 4, by - 4, bx - 4, by + 4);
    }

    private double[] hoverDeletePos(DrawingRenderer.RenderContext ctx, ChartDrawing d) {
        double x = DrawingRenderer.timeToX(d.getPoints().getFirst().getTime(), ctx);
        double y = DrawingRenderer.priceToY(d.getPoints().getFirst().getPrice(), ctx);
        return new double[]{x - 12, y - 12};
    }

    private boolean isHoverDeleteHit(double mx, double my, DrawingRenderer.RenderContext ctx,
                                     ChartDrawing d) {
        if (d.getPoints() == null || d.getPoints().isEmpty()) return false;
        double[] pos = hoverDeletePos(ctx, d);
        return Math.hypot(mx - pos[0], my - pos[1]) < HOVER_DELETE_RADIUS + 2;
    }

    // ── Drawing manipulation ──────────────────────────────────────────────────

    public void deleteSelected() {
        if (selected == null) return;
        doDeleteDrawing(selected);
    }

    private void doDeleteDrawing(ChartDrawing d) {
        if (d == null) return;
        if (confirmHoverDelete) {
            // Show a brief confirmation if the user configured it
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
            showMaxDrawingsWarning();
            return;
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

    /**
     * PARALLEL LINES — create a copy of the selected drawing offset by
     * {@code priceDelta} in price units (positive = up).
     */
    public void parallelCopySelected(double priceDelta) {
        if (selected == null) return;
        if (drawings.size() >= ChartDrawingService.MAX_DRAWINGS_PER_CHART) {
            showMaxDrawingsWarning();
            return;
        }
        ChartDrawing copy = cloneDrawing(selected);
        copy.setId(null);
        // Apply price offset to all anchor points
        for (ChartPoint pt : copy.getPoints()) {
            pt.setPrice(pt.getPrice() + priceDelta);
        }
        // Tag the copy's properties
        copy.getProperties().setParallelOffset(priceDelta);
        drawings.add(copy);
        history.recordCreate(copy);
        if (onDrawingCreated != null) onDrawingCreated.accept(copy);
        selected = copy;
        host.requestRender();
    }

    /**
     * MIRROR — create a reflected copy of the selected drawing.
     *
     * @param axis     "VERTICAL" mirrors left↔right around the anchor's X;
     *                 "HORIZONTAL" mirrors up↔down around the anchor's price.
     */
    public void mirrorSelected(String axis) {
        if (selected == null) return;
        if (drawings.size() >= ChartDrawingService.MAX_DRAWINGS_PER_CHART) {
            showMaxDrawingsWarning();
            return;
        }
        if (selected.getPoints() == null || selected.getPoints().isEmpty()) return;

        ChartDrawing copy = cloneDrawing(selected);
        copy.setId(null);
        copy.getProperties().setMirrorAxis(axis);

        if ("VERTICAL".equalsIgnoreCase(axis)) {
            // Mirror time: reflect around the first anchor's time
            long axisEpoch = selected.getPoints().getFirst().getTimeEpoch();
            for (ChartPoint pt : copy.getPoints()) {
                long delta = pt.getTimeEpoch() - axisEpoch;
                pt.setTimeEpoch(axisEpoch - delta);
            }
        } else {
            // HORIZONTAL — mirror price around the first anchor's price
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

    /** Returns true if this drawing is effectively locked (own lock OR global lock). */
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
        ChartDrawingProperties props = ChartDrawingProperties.defaultsFor(tool);
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
            case RECTANGLE, FLAT_CHANNEL -> { return isNearRect(d, x, y, ctx); }
            case TRIANGLE               -> { return isNearPolygon(d, x, y, ctx); }
            case ELLIPSE                -> { return isNearEllipse(d, x, y, ctx); }
            case TEXT_LABEL, NOTE_ICON -> {
                double ax = DrawingRenderer.timeToX(d.getPoints().getFirst().getTime(), ctx);
                double ay = DrawingRenderer.priceToY(d.getPoints().getFirst().getPrice(), ctx);
                return x >= ax && x <= ax + 120 && y >= ay - 25 && y <= ay + 10;
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
                return x >= bx && x <= bx + bw && y >= by && y <= by + 24;
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

        // ── Properties (color, line weight, style, fill opacity) ──────────────
        MenuItem propsItem = new MenuItem("🎨 Properties…");
        propsItem.setOnAction(e -> {
            if (onOpenProperties != null) onOpenProperties.accept(d);
        });

        // ── Parallel copy ─────────────────────────────────────────────────────
        MenuItem parallelItem = new MenuItem("∥ Parallel Copy…");
        parallelItem.setOnAction(e -> showParallelCopyDialog(d));

        // ── Mirror ────────────────────────────────────────────────────────────
        Menu mirrorMenu = new Menu("⇄ Mirror");
        MenuItem mirrorV = new MenuItem("Vertical Axis");
        mirrorV.setOnAction(e -> { selected = d; mirrorSelected("VERTICAL"); });
        MenuItem mirrorH = new MenuItem("Horizontal Axis");
        mirrorH.setOnAction(e -> { selected = d; mirrorSelected("HORIZONTAL"); });
        mirrorMenu.getItems().addAll(mirrorV, mirrorH);

        // ── Standard items ────────────────────────────────────────────────────
        MenuItem del = new MenuItem("🗑 Delete");
        del.setOnAction(e -> doDeleteDrawing(d));
        MenuItem dup = new MenuItem("⧉ Duplicate");
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

        // Annotation text editing
        if (isAnnotationTool(d.getToolType())) {
            MenuItem editText = new MenuItem("✏ Edit Text");
            editText.setOnAction(e -> startTextEditing(d, host.currentRenderContext()));
            contextMenu.getItems().add(0, editText);
            contextMenu.getItems().add(1, new SeparatorMenuItem());
        }

        // Position tools: trade shortcuts
        if (d.getToolType().isPositionTool()) {
            MenuItem trade = new MenuItem("📈 Create Trade from Drawing");
            trade.setOnAction(e -> { if (onCreateTradeFromDrawing != null) onCreateTradeFromDrawing.accept(d); });
            MenuItem instant = new MenuItem("💾 Instant Save Trade");
            instant.setOnAction(e -> { if (onInstantSaveTrade != null) onInstantSaveTrade.accept(d); });
            contextMenu.getItems().addAll(new SeparatorMenuItem(), trade, instant);
        }

        contextMenu.show(host.getWindow(), screenX, screenY);
    }

    /** Shows a small dialog to collect the price-offset for a parallel copy. */
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
            } catch (NumberFormatException ex) {
                // ignore invalid input
            }
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
        // Offset time by 1 candle width (use 1 hour as approximation)
        long oneHour = 3_600_000L;
        for (ChartPoint pt : copy.getPoints()) {
            pt.setTimeEpoch(pt.getTimeEpoch() + oneHour);
        }
    }
}
