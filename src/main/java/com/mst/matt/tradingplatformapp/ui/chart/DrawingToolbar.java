package com.mst.matt.tradingplatformapp.ui.chart;

import com.mst.matt.tradingplatformapp.model.ChartDrawingToolType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Compact grouped drawing toolbar — 6 category flyouts + utility actions.
 *
 * <p>New actions (v2):
 * <ul>
 *   <li>💾 Save Layout — saves current drawings as a named set</li>
 *   <li>📂 Load Layout — loads a previously saved named set</li>
 *   <li>🗑 Delete Layout — removes a saved layout</li>
 *   <li>⚙ Drawing Settings — opens global drawing settings dialog</li>
 *   <li>📷 Screenshot — captures chart to PNG</li>
 *   <li>👁 Show/Hide All — toggles visibility of all drawings</li>
 *   <li>🔒 Lock/Unlock All — toggles lock on all drawings</li>
 * </ul>
 */
public class DrawingToolbar extends VBox {

    public enum ToolGroup {
        LINE("📐", "Line Tools",
                ChartDrawingToolType.TREND_LINE, ChartDrawingToolType.RAY,
                ChartDrawingToolType.EXTENDED_LINE, ChartDrawingToolType.HORIZONTAL_LINE,
                ChartDrawingToolType.VERTICAL_LINE),
        FIB("🔢", "Fibonacci",
                ChartDrawingToolType.FIB_RETRACEMENT, ChartDrawingToolType.FIB_EXTENSION,
                ChartDrawingToolType.FIB_FAN, ChartDrawingToolType.FIB_TIME_ZONES,
                ChartDrawingToolType.FIB_CHANNEL, ChartDrawingToolType.FIB_SPEED_RESISTANCE),
        POSITION("📈", "Positions",
                ChartDrawingToolType.LONG_POSITION, ChartDrawingToolType.SHORT_POSITION),
        SHAPE("▭", "Shapes",
                ChartDrawingToolType.RECTANGLE, ChartDrawingToolType.TRIANGLE,
                ChartDrawingToolType.ELLIPSE, ChartDrawingToolType.PARALLEL_CHANNEL,
                ChartDrawingToolType.FLAT_CHANNEL),
        ANNOTATE("✏️", "Annotations",
                ChartDrawingToolType.TEXT_LABEL, ChartDrawingToolType.CALLOUT,
                ChartDrawingToolType.ARROW, ChartDrawingToolType.NOTE_ICON,
                ChartDrawingToolType.RULER),
        UTILITY("🛠", "Utility",
                ChartDrawingToolType.PARALLEL_LINES, ChartDrawingToolType.MIRROR);

        final String icon;
        final String label;
        final ChartDrawingToolType[] tools;

        ToolGroup(String icon, String label, ChartDrawingToolType... tools) {
            this.icon = icon; this.label = label; this.tools = tools;
        }
    }

    /**
     * T-5: Minimum button dimension — 44×44px meets Apple HIG / Google Material
     * touch target guidelines so finger taps land reliably on all buttons.
     */
    private static final double BTN = 44;

    // ── Callbacks ─────────────────────────────────────────────────────────────
    private Consumer<ChartDrawingToolType> onToolSelected;
    private Runnable onDelete;
    private Runnable onUndo;
    private Runnable onRedo;
    private Runnable onSaveLayout;
    private Runnable onLoadLayout;
    private Runnable onDeleteLayout;
    private Runnable onDrawingSettings;
    private Runnable onScreenshot;
    private Runnable onToggleShowAll;
    private Runnable onToggleLockAll;

    // ── State ─────────────────────────────────────────────────────────────────
    private final Button selectBtn;
    private final Map<ToolGroup, Button> groupButtons = new EnumMap<>(ToolGroup.class);
    private final Map<ToolGroup, ChartDrawingToolType> lastUsed = new EnumMap<>(ToolGroup.class);
    private ChartDrawingToolType activeTool = ChartDrawingToolType.SELECT;
    private boolean collapsed;

    private Button undoBtn;
    private Button redoBtn;
    private Button showAllBtn;
    private Button lockAllBtn;
    private boolean showAllState = true;
    private boolean lockAllState = false;

    public DrawingToolbar() {
        setAlignment(Pos.TOP_CENTER);
        setSpacing(4);
        setPadding(new Insets(6, 4, 6, 4));
        setPrefWidth(56);
        setMaxWidth(64);
        applyPanelStyle(false);

        // ── Select button ────────────────────────────────────────────────────
        selectBtn = createBtn("↖", "Select / Pan");
        selectBtn.setOnAction(e -> selectTool(ChartDrawingToolType.SELECT, null));
        styleActive(selectBtn, true);
        getChildren().add(selectBtn);

        // ── Tool group buttons ────────────────────────────────────────────────
        for (ToolGroup group : ToolGroup.values()) {
            Button btn = createBtn(group.icon, group.label);
            btn.setOnAction(e -> showGroupMenu(group, btn));
            groupButtons.put(group, btn);
            getChildren().add(btn);
        }

        // ── Separator ─────────────────────────────────────────────────────────
        addSep();

        // ── Undo / Redo ───────────────────────────────────────────────────────
        undoBtn = createBtn("↩", "Undo  (Ctrl+Z)");
        undoBtn.setOnAction(e -> { if (onUndo != null) onUndo.run(); });
        undoBtn.setDisable(true);
        getChildren().add(undoBtn);

        redoBtn = createBtn("↪", "Redo  (Ctrl+Y)");
        redoBtn.setOnAction(e -> { if (onRedo != null) onRedo.run(); });
        redoBtn.setDisable(true);
        getChildren().add(redoBtn);

        // ── Separator ─────────────────────────────────────────────────────────
        addSep();

        // ── Show/Hide All ──────────────────────────────────────────────────────
        showAllBtn = createBtn("👁", "Show / Hide all drawings");
        showAllBtn.setOnAction(e -> {
            showAllState = !showAllState;
            showAllBtn.setStyle(showAllState ? baseBtnStyle() :
                    baseBtnStyle() + "-fx-opacity:0.5;");
            if (onToggleShowAll != null) onToggleShowAll.run();
        });
        getChildren().add(showAllBtn);

        // ── Lock/Unlock All ────────────────────────────────────────────────────
        lockAllBtn = createBtn("🔒", "Lock / Unlock all drawings");
        lockAllBtn.setOnAction(e -> {
            lockAllState = !lockAllState;
            lockAllBtn.setStyle(lockAllState
                    ? baseBtnStyle() + "-fx-background-color:#388bfd20;"
                    : baseBtnStyle());
            if (onToggleLockAll != null) onToggleLockAll.run();
        });
        getChildren().add(lockAllBtn);

        // ── Separator ─────────────────────────────────────────────────────────
        addSep();

        // ── Layout: save / load ────────────────────────────────────────────────
        Button saveLayoutBtn = createBtn("💾", "Save Drawing Layout");
        saveLayoutBtn.setOnAction(e -> { if (onSaveLayout != null) onSaveLayout.run(); });
        getChildren().add(saveLayoutBtn);

        Button loadLayoutBtn = createBtn("📂", "Load Drawing Layout");
        loadLayoutBtn.setOnAction(e -> { if (onLoadLayout != null) onLoadLayout.run(); });
        getChildren().add(loadLayoutBtn);

        Button delLayoutBtn = createBtn("🗂", "Delete Drawing Layout");
        delLayoutBtn.setOnAction(e -> { if (onDeleteLayout != null) onDeleteLayout.run(); });
        getChildren().add(delLayoutBtn);

        // ── Separator ─────────────────────────────────────────────────────────
        addSep();

        // ── Screenshot ────────────────────────────────────────────────────────
        Button screenshotBtn = createBtn("📷", "Capture chart screenshot");
        screenshotBtn.setOnAction(e -> { if (onScreenshot != null) onScreenshot.run(); });
        getChildren().add(screenshotBtn);

        // ── Drawing Settings ──────────────────────────────────────────────────
        Button settingsBtn = createBtn("⚙", "Global Drawing Settings");
        settingsBtn.setOnAction(e -> { if (onDrawingSettings != null) onDrawingSettings.run(); });
        getChildren().add(settingsBtn);

        // ── Delete selected ────────────────────────────────────────────────────
        addSep();
        Button deleteBtn = createBtn("🗑", "Delete selected  (Del)");
        deleteBtn.setStyle(baseBtnStyle() + "-fx-background-color:#3d1f1f;");
        deleteBtn.setOnAction(e -> { if (onDelete != null) onDelete.run(); });
        getChildren().add(deleteBtn);
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setOnToolSelected(Consumer<ChartDrawingToolType> cb) { onToolSelected = cb; }
    public void setOnDelete(Runnable r)          { onDelete = r; }
    public void setOnUndo(Runnable r)            { onUndo = r; }
    public void setOnRedo(Runnable r)            { onRedo = r; }
    public void setOnSaveLayout(Runnable r)      { onSaveLayout = r; }
    public void setOnLoadLayout(Runnable r)      { onLoadLayout = r; }
    public void setOnDeleteLayout(Runnable r)    { onDeleteLayout = r; }
    public void setOnDrawingSettings(Runnable r) { onDrawingSettings = r; }
    public void setOnScreenshot(Runnable r)      { onScreenshot = r; }
    public void setOnToggleShowAll(Runnable r)   { onToggleShowAll = r; }
    public void setOnToggleLockAll(Runnable r)   { onToggleLockAll = r; }

    /** Update undo/redo button enabled state based on history availability. */
    public void updateUndoRedoState(boolean canUndo, boolean canRedo) {
        if (undoBtn != null) undoBtn.setDisable(!canUndo);
        if (redoBtn != null) redoBtn.setDisable(!canRedo);
    }

    public boolean isCollapsed() { return collapsed; }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        applyPanelStyle(collapsed);
        for (var child : getChildren()) {
            if (child != selectBtn && child instanceof Button b && !"🗑".equals(b.getText())) {
                child.setVisible(!collapsed);
                child.setManaged(!collapsed);
            }
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void showGroupMenu(ToolGroup group, Button anchor) {
        ContextMenu menu = new ContextMenu();
        menu.setStyle("-fx-background-color:#1c2128; -fx-border-color:#30363d;");
        for (ChartDrawingToolType tool : group.tools) {
            MenuItem item = new MenuItem(tool.displayName());
            item.setOnAction(e -> selectTool(tool, group));
            menu.getItems().add(item);
        }
        menu.show(anchor, javafx.geometry.Side.RIGHT, 0, 0);
    }

    private void selectTool(ChartDrawingToolType tool, ToolGroup group) {
        activeTool = tool;
        styleActive(selectBtn, tool == ChartDrawingToolType.SELECT);
        for (var e : groupButtons.entrySet()) styleActive(e.getValue(), false);
        if (group != null) {
            lastUsed.put(group, tool);
            styleActive(groupButtons.get(group), true);
            Button gb = groupButtons.get(group);
            gb.setText(tool.icon());
            gb.setTooltip(new Tooltip(tool.displayName()));
        }
        if (onToolSelected != null) onToolSelected.accept(tool);
    }

    private void addSep() {
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color:#30363d; -fx-padding:2 0 2 0;");
        getChildren().add(sep);
    }

    private Button createBtn(String text, String tooltip) {
        Button b = new Button(text);
        b.setMinSize(BTN, BTN); b.setMaxSize(BTN, BTN);
        b.setStyle(baseBtnStyle());
        b.setTooltip(new Tooltip(tooltip));
        return b;
    }

    private static String baseBtnStyle() {
        // T-5: font size bumped to 14px for clear icon rendering inside the 44×44 touch area
        return "-fx-background-color:#21262d; -fx-text-fill:#e6edf3;"
                + "-fx-font-size:14px; -fx-background-radius:6; -fx-cursor:hand;"
                + "-fx-padding:4;";
    }

    private void styleActive(Button btn, boolean active) {
        btn.setStyle(active
                ? "-fx-background-color:#388bfd; -fx-text-fill:#ffffff;"
                + "-fx-font-size:14px; -fx-background-radius:6; -fx-cursor:hand; -fx-padding:4;"
                : baseBtnStyle());
    }

    private void applyPanelStyle(boolean mini) {
        setStyle(mini
                ? "-fx-background-color:#1c2128cc; -fx-background-radius:6;"
                + "-fx-border-color:#30363d; -fx-border-radius:6; -fx-border-width:1;"
                : "-fx-background-color:#1c2128ee; -fx-background-radius:6;"
                + "-fx-border-color:#30363d; -fx-border-radius:6; -fx-border-width:1;");
    }
}
