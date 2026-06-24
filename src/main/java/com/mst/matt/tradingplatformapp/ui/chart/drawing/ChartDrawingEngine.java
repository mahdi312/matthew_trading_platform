package com.mst.matt.tradingplatformapp.ui.chart.drawing;

import com.mst.matt.tradingplatformapp.model.*;
import com.mst.matt.tradingplatformapp.service.ChartDrawingService;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Window;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manages drawing tool interaction state: creation, selection, editing, context menu.
 */
public class ChartDrawingEngine {

    public interface Host {
        void requestRender();
        DrawingRenderer.RenderContext currentRenderContext();
        List<OhlcvBar> getBars();
        Window getWindow();
    }

    private static final double HIT_THRESHOLD = 8;

    private final Host host;
    private final List<ChartDrawing> drawings = new ArrayList<>();
    private ChartDrawingToolType activeTool = ChartDrawingToolType.SELECT;
    private ChartDrawing selected;
    private ChartDrawing inProgress;
    private int dragAnchorIndex = -1;
    private boolean snapEnabled;
    private boolean isDragging;
    private double pressX, pressY;

    private Consumer<ChartDrawing> onDrawingCreated;
    private Consumer<ChartDrawing> onDrawingUpdated;
    private Consumer<ChartDrawing> onDrawingDeleted;
    private Consumer<ChartDrawing> onCreateTradeFromDrawing;
    private Consumer<ChartDrawing> onInstantSaveTrade;
    private Runnable onSelectionChanged;

    private ContextMenu contextMenu;

    public ChartDrawingEngine(Host host) {
        this.host = host;
    }

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

    public boolean isDrawingMode() {
        return activeTool != ChartDrawingToolType.SELECT;
    }

    public boolean handleMousePressed(MouseEvent e, DrawingRenderer.RenderContext ctx) {
        if (ctx == null || host.getBars() == null || host.getBars().isEmpty()) return false;
        pressX = e.getX();
        pressY = e.getY();
        isDragging = false;

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
        if (selected != null && selected.getToolType().isPositionTool() && isTradeButtonHit(e.getX(), e.getY(), ctx)) {
            if (onCreateTradeFromDrawing != null) onCreateTradeFromDrawing.accept(selected);
            return true;
        }

        if (activeTool == ChartDrawingToolType.SELECT) {
            int anchor = hitAnchor(selected, e.getX(), e.getY(), ctx);
            if (anchor >= 0 && selected != null && !selected.isLocked()) {
                dragAnchorIndex = anchor;
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

        if (activeTool == ChartDrawingToolType.SELECT) {
            if (dragAnchorIndex >= 0 && selected != null && !selected.isLocked()) {
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
            if (inProgress.getPoints().size() == 1
                    && inProgress.getToolType().requiredPoints() > 1) {
                if (inProgress.getPoints().size() == 1) {
                    if (inProgress.getPoints().size() < 2) inProgress.getPoints().add(pt);
                    else inProgress.getPoints().set(1, pt);
                }
            } else if (inProgress.getPoints().size() >= 1) {
                int last = inProgress.getPoints().size() - 1;
                inProgress.getPoints().set(last, pt);
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
            if (onDrawingUpdated != null) onDrawingUpdated.accept(selected);
            return true;
        }

        if (inProgress != null && activeTool != ChartDrawingToolType.SELECT) {
            if (inProgress.getPoints().size() >= 1 && inProgress.getToolType().requiredPoints() > 1) {
                ChartPoint pt = pointFromMouse(e.getX(), e.getY(), ctx);
                if (inProgress.getPoints().size() < 2) inProgress.getPoints().add(pt);
                else inProgress.getPoints().set(inProgress.getPoints().size() - 1, pt);
                updatePositionPricesFromAnchors(inProgress);
            }
            if (inProgress.getPoints().size() >= inProgress.getToolType().requiredPoints()) {
                finalizeInProgress();
            }
            host.requestRender();
            return true;
        }
        dragAnchorIndex = -1;
        return false;
    }

    public void renderDrawings(GraphicsContext gc, DrawingRenderer.RenderContext ctx) {
        for (ChartDrawing d : drawings) {
            DrawingRenderer.render(gc, d, ctx, d == selected, activeTool == ChartDrawingToolType.SELECT);
        }
        if (inProgress != null) {
            DrawingRenderer.render(gc, inProgress, ctx, true, true);
        }
    }

    public void deleteSelected() {
        if (selected == null) return;
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
        if (onDrawingCreated != null) onDrawingCreated.accept(copy);
        selected = copy;
        host.requestRender();
    }

    public void toggleLockSelected() {
        if (selected == null) return;
        selected.setLocked(!selected.isLocked());
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

    private void finalizeInProgress() {
        if (inProgress == null) return;
        updatePositionPricesFromAnchors(inProgress);
        if (inProgress.getToolType() == ChartDrawingToolType.TEXT_LABEL
                && (inProgress.getProperties().getText() == null
                || inProgress.getProperties().getText().isBlank())) {
            inProgress.getProperties().setText("Note");
        }
        drawings.add(inProgress);
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
        ChartDrawing d = ChartDrawing.builder()
                .toolType(tool)
                .points(new ArrayList<>(List.of(pt)))
                .properties(props)
                .locked(false)
                .build();
        return d;
    }

    private void updatePositionPricesFromAnchors(ChartDrawing d) {
        if (!d.getToolType().isPositionTool() || d.getPoints().size() < 2) return;
        ChartDrawingProperties props = d.getProperties();
        if (props.getEntryPrice() == null && !d.getPoints().isEmpty()) {
            props.setEntryPrice(d.getPoints().getFirst().getPrice());
        }
        // Allow vertical drag of SL/TP via second point price for quick adjustment
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

    private ChartDrawing hitTest(double x, double y, DrawingRenderer.RenderContext ctx) {
        for (int i = drawings.size() - 1; i >= 0; i--) {
            if (isNearDrawing(drawings.get(i), x, y, ctx)) return drawings.get(i);
        }
        return null;
    }

    private boolean isNearDrawing(ChartDrawing d, double x, double y, DrawingRenderer.RenderContext ctx) {
        if (d.getPoints() == null || d.getPoints().isEmpty()) return false;
        if (d.getToolType().isPositionTool()) {
            return isNearPosition(d, x, y, ctx);
        }
        if (d.getToolType() == ChartDrawingToolType.HORIZONTAL_LINE
                || d.getToolType() == ChartDrawingToolType.PROFIT_TARGET_LINE
                || d.getToolType() == ChartDrawingToolType.STOP_LOSS_LINE) {
            double py = DrawingRenderer.priceToY(d.getPoints().getFirst().getPrice(), ctx);
            return Math.abs(y - py) < HIT_THRESHOLD && x >= ctx.left() && x <= ctx.right();
        }
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

    private void showContextMenu(double screenX, double screenY, ChartDrawing d) {
        if (contextMenu != null) contextMenu.hide();
        contextMenu = new ContextMenu();
        MenuItem del = new MenuItem("Delete");
        del.setOnAction(e -> deleteSelected());
        MenuItem dup = new MenuItem("Duplicate");
        dup.setOnAction(e -> duplicateSelected());
        MenuItem lock = new MenuItem(d.isLocked() ? "Unlock" : "Lock");
        lock.setOnAction(e -> toggleLockSelected());
        contextMenu.getItems().addAll(del, dup, lock);
        if (d.getToolType().isPositionTool()) {
            MenuItem trade = new MenuItem("Create Trade from Drawing");
            trade.setOnAction(e -> {
                if (onCreateTradeFromDrawing != null) onCreateTradeFromDrawing.accept(d);
            });
            MenuItem instant = new MenuItem("Instant Save Trade");
            instant.setOnAction(e -> {
                if (onInstantSaveTrade != null) onInstantSaveTrade.accept(d);
            });
            contextMenu.getItems().addAll(trade, instant);
        }
        contextMenu.show(host.getWindow(), screenX, screenY);
    }

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
        return ChartDrawing.builder()
                .profile(src.getProfile())
                .symbol(src.getSymbol())
                .timeframe(src.getTimeframe())
                .toolType(src.getToolType())
                .points(new ArrayList<>(src.getPoints()))
                .properties(src.getProperties())
                .locked(src.isLocked())
                .build();
    }

    private static void offsetCopyPoints(ChartDrawing copy) {
        for (ChartPoint pt : copy.getPoints()) {
            pt.setTime(pt.getTime().plusHours(1));
        }
    }
}
