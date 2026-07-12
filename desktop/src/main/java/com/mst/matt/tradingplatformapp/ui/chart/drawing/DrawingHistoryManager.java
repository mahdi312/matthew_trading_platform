package com.mst.matt.tradingplatformapp.ui.chart.drawing;

import com.mst.matt.tradingplatformapp.model.ChartDrawing;
import com.mst.matt.tradingplatformapp.model.ChartDrawingProperties;
import com.mst.matt.tradingplatformapp.model.ChartPoint;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * Session-based Undo/Redo manager for chart drawing actions.
 *
 * <p>Supported actions (tracked):
 * <ul>
 *   <li>CREATE — adding a new drawing</li>
 *   <li>DELETE — removing a drawing</li>
 *   <li>DUPLICATE — cloning a drawing (treated as a CREATE of the clone)</li>
 *   <li>MOVE — dragging anchors to new positions</li>
 *   <li>MODIFY — editing a drawing property (e.g. dragging a Fib level)</li>
 *   <li>LOCK / UNLOCK — toggling the lock state</li>
 * </ul>
 *
 * <p>NOT tracked (excluded by spec):
 * <ul>
 *   <li>Instant-save trade</li>
 *   <li>Create trade from drawing</li>
 * </ul>
 *
 * <p>The stack is cleared when {@link #clear()} is called (e.g. on chart view close).
 */
public class DrawingHistoryManager {

    /** Max number of undo steps kept in memory. */
    private static final int MAX_STACK_SIZE = 100;

    // ── Action types ─────────────────────────────────────────────────────────

    public enum ActionType {
        CREATE, DELETE, MOVE, MODIFY, LOCK_TOGGLE
    }

    /**
     * One undo/redo entry. Stores a complete snapshot of the drawing's state
     * before (for undo) and after (for redo) the action.
     */
    public static class HistoryEntry {
        public final ActionType type;
        /** Snapshot before the action — used to restore on undo. */
        public final DrawingSnapshot before;
        /** Snapshot after the action — used to restore on redo. */
        public final DrawingSnapshot after;

        public HistoryEntry(ActionType type, DrawingSnapshot before, DrawingSnapshot after) {
            this.type = type;
            this.before = before;
            this.after = after;
        }
    }

    /**
     * Immutable snapshot of a single {@link ChartDrawing}'s state.
     * Used to fully restore a drawing on undo or redo.
     */
    public static class DrawingSnapshot {
        public final ChartDrawing drawing;   // reference (identity — same object)
        public final List<ChartPoint> points;
        public final boolean locked;
        public final boolean present;        // true = the drawing is in the list
        // Properties snapshot
        public final String color;
        public final double lineWidth;
        public final String lineStyle;
        public final double fillOpacity;
        public final Double entryPrice;
        public final Double stopLoss;
        public final Double takeProfit;
        public final Double channelWidth;
        public final String text;

        /** Snapshot from a live drawing (it IS present in the drawings list). */
        public DrawingSnapshot(ChartDrawing d) {
            this.drawing     = d;
            this.points      = deepCopyPoints(d.getPoints());
            this.locked      = d.isLocked();
            this.present     = true;
            ChartDrawingProperties p = d.getProperties();
            this.color        = p != null ? p.getColor() : null;
            this.lineWidth    = p != null ? p.getLineWidth() : 1.5;
            this.lineStyle    = p != null ? p.getLineStyle() : "SOLID";
            this.fillOpacity  = p != null ? p.getFillOpacity() : 0.12;
            this.entryPrice   = p != null ? p.getEntryPrice() : null;
            this.stopLoss     = p != null ? p.getStopLoss() : null;
            this.takeProfit   = p != null ? p.getTakeProfit() : null;
            this.channelWidth = p != null ? p.getChannelWidth() : null;
            this.text         = p != null ? p.getText() : null;
        }

        /** Snapshot representing an absent drawing (not yet created or already deleted). */
        public DrawingSnapshot(ChartDrawing d, boolean present) {
            this.drawing     = d;
            this.points      = d.getPoints() != null ? deepCopyPoints(d.getPoints()) : new ArrayList<>();
            this.locked      = d.isLocked();
            this.present     = present;
            ChartDrawingProperties p = d.getProperties();
            this.color        = p != null ? p.getColor() : null;
            this.lineWidth    = p != null ? p.getLineWidth() : 1.5;
            this.lineStyle    = p != null ? p.getLineStyle() : "SOLID";
            this.fillOpacity  = p != null ? p.getFillOpacity() : 0.12;
            this.entryPrice   = p != null ? p.getEntryPrice() : null;
            this.stopLoss     = p != null ? p.getStopLoss() : null;
            this.takeProfit   = p != null ? p.getTakeProfit() : null;
            this.channelWidth = p != null ? p.getChannelWidth() : null;
            this.text         = p != null ? p.getText() : null;
        }

        private static List<ChartPoint> deepCopyPoints(List<ChartPoint> pts) {
            if (pts == null) return new ArrayList<>();
            List<ChartPoint> copy = new ArrayList<>(pts.size());
            for (ChartPoint pt : pts) {
                copy.add(ChartPoint.ofEpoch(pt.getTimeEpoch(), pt.getPrice()));
            }
            return copy;
        }
    }

    // ── State ────────────────────────────────────────────────────────────────

    private final Deque<HistoryEntry> undoStack = new ArrayDeque<>();
    private final Deque<HistoryEntry> redoStack = new ArrayDeque<>();

    /** Called after undo/redo to signal a state change; triggers re-render + persistence. */
    private Consumer<ChartDrawing> onDrawingRestored;

    public void setOnDrawingRestored(Consumer<ChartDrawing> cb) {
        this.onDrawingRestored = cb;
    }

    // ── Stack management ─────────────────────────────────────────────────────

    /** Records an action, clearing the redo stack. */
    public void record(HistoryEntry entry) {
        undoStack.push(entry);
        redoStack.clear();
        // Trim if too large
        while (undoStack.size() > MAX_STACK_SIZE) {
            // Remove from the bottom of the deque (oldest entry)
            ((ArrayDeque<HistoryEntry>) undoStack).removeLast();
        }
    }

    /** Convenience: record a CREATE action. */
    public void recordCreate(ChartDrawing d) {
        DrawingSnapshot before = new DrawingSnapshot(d, false); // did not exist before
        DrawingSnapshot after  = new DrawingSnapshot(d, true);  // exists after
        record(new HistoryEntry(ActionType.CREATE, before, after));
    }

    /** Convenience: record a DELETE action (call BEFORE removing from list). */
    public void recordDelete(ChartDrawing d) {
        DrawingSnapshot before = new DrawingSnapshot(d, true);  // existed before
        DrawingSnapshot after  = new DrawingSnapshot(d, false); // gone after
        record(new HistoryEntry(ActionType.DELETE, before, after));
    }

    /** Convenience: record a MOVE action. Call with `before` captured before the move. */
    public void recordMove(ChartDrawing d, DrawingSnapshot before) {
        DrawingSnapshot after = new DrawingSnapshot(d);
        record(new HistoryEntry(ActionType.MOVE, before, after));
    }

    /** Convenience: record a MODIFY action. Call with `before` captured before the modification. */
    public void recordModify(ChartDrawing d, DrawingSnapshot before) {
        DrawingSnapshot after = new DrawingSnapshot(d);
        record(new HistoryEntry(ActionType.MODIFY, before, after));
    }

    /** Convenience: record a LOCK_TOGGLE action. */
    public void recordLockToggle(ChartDrawing d) {
        // before = current state (about to be toggled), after = toggled state
        DrawingSnapshot before = new DrawingSnapshot(d);
        // We'll call this before the toggle, so after = inverted locked
        DrawingSnapshot after = new DrawingSnapshot(d, d.isLocked()); // same present
        // We need to flip locked in after — use the raw DrawingSnapshot with constructor tweak
        // Actually: this is called BEFORE the toggle, so d.isLocked() is the OLD state.
        // We capture 'before' as-is. 'after' will be captured after toggle in engine.
        record(new HistoryEntry(ActionType.LOCK_TOGGLE, before, null)); // after filled post-toggle
    }

    public boolean canUndo() { return !undoStack.isEmpty(); }
    public boolean canRedo() { return !redoStack.isEmpty(); }

    /** Clears all history (call when the chart view is closed). */
    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    // ── Undo / Redo ──────────────────────────────────────────────────────────

    /**
     * Applies the undo operation on the provided drawings list.
     * Returns the affected drawing (for selection highlighting), or null.
     */
    public ChartDrawing undo(List<ChartDrawing> drawings) {
        if (undoStack.isEmpty()) return null;
        HistoryEntry entry = undoStack.pop();
        redoStack.push(entry);
        return applySnapshot(entry.before, drawings);
    }

    /**
     * Applies the redo operation on the provided drawings list.
     * Returns the affected drawing (for selection highlighting), or null.
     */
    public ChartDrawing redo(List<ChartDrawing> drawings) {
        if (redoStack.isEmpty()) return null;
        HistoryEntry entry = redoStack.pop();
        undoStack.push(entry);
        DrawingSnapshot snap = entry.after;
        if (snap == null) {
            // Lock toggle redo: re-apply the toggle
            ChartDrawing d = entry.before.drawing;
            d.setLocked(!entry.before.locked);
            if (onDrawingRestored != null) onDrawingRestored.accept(d);
            return d;
        }
        return applySnapshot(snap, drawings);
    }

    /**
     * Restores a drawing to the state described by the snapshot.
     * Adds or removes from the list as needed.
     */
    private ChartDrawing applySnapshot(DrawingSnapshot snap, List<ChartDrawing> drawings) {
        ChartDrawing d = snap.drawing;
        if (snap.present) {
            // Ensure the drawing is in the list
            if (!drawings.contains(d)) drawings.add(d);
            // Restore points
            d.getPoints().clear();
            d.getPoints().addAll(DrawingSnapshot.deepCopyPoints(snap.points));
            // Restore locked
            d.setLocked(snap.locked);
            // Restore properties
            ChartDrawingProperties p = d.getProperties();
            if (p != null) {
                if (snap.color != null) p.setColor(snap.color);
                p.setLineWidth(snap.lineWidth);
                if (snap.lineStyle != null) p.setLineStyle(snap.lineStyle);
                p.setFillOpacity(snap.fillOpacity);
                p.setEntryPrice(snap.entryPrice);
                p.setStopLoss(snap.stopLoss);
                p.setTakeProfit(snap.takeProfit);
                p.setChannelWidth(snap.channelWidth);
                p.setText(snap.text);
            }
        } else {
            // Remove from list
            drawings.remove(d);
        }
        if (onDrawingRestored != null) onDrawingRestored.accept(d);
        return snap.present ? d : null;
    }
}
