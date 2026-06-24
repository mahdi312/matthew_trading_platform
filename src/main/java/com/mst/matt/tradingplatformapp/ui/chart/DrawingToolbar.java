package com.mst.matt.tradingplatformapp.ui.chart;

import com.mst.matt.tradingplatformapp.model.ChartDrawingToolType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Compact grouped drawing toolbar — 6 category flyouts + select + delete.
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
            this.icon = icon;
            this.label = label;
            this.tools = tools;
        }
    }

    private static final double BTN = 28;

    private Consumer<ChartDrawingToolType> onToolSelected;
    private Runnable onDelete;

    private final Button selectBtn;
    private final Map<ToolGroup, Button> groupButtons = new EnumMap<>(ToolGroup.class);
    private final Map<ToolGroup, ChartDrawingToolType> lastUsed = new EnumMap<>(ToolGroup.class);
    private ChartDrawingToolType activeTool = ChartDrawingToolType.SELECT;
    private boolean collapsed;

    public DrawingToolbar() {
        setAlignment(Pos.TOP_CENTER);
        setSpacing(3);
        setPadding(new Insets(4, 3, 4, 3));
        setPrefWidth(40);
        setMaxWidth(48);
        applyPanelStyle(false);

        selectBtn = createBtn("↖", "Select / Pan");
        selectBtn.setOnAction(e -> selectTool(ChartDrawingToolType.SELECT, null));
        styleActive(selectBtn, true);
        getChildren().add(selectBtn);

        for (ToolGroup group : ToolGroup.values()) {
            Button btn = createBtn(group.icon, group.label);
            btn.setOnAction(e -> showGroupMenu(group, btn));
            groupButtons.put(group, btn);
            getChildren().add(btn);
        }

        Button deleteBtn = createBtn("🗑", "Delete selected");
        deleteBtn.setStyle(baseBtnStyle() + "-fx-background-color:#3d1f1f;");
        deleteBtn.setOnAction(e -> { if (onDelete != null) onDelete.run(); });
        getChildren().add(deleteBtn);
    }

    public void setOnToolSelected(Consumer<ChartDrawingToolType> cb) { onToolSelected = cb; }
    public void setOnDelete(Runnable r) { onDelete = r; }

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
        for (var e : groupButtons.entrySet()) {
            styleActive(e.getValue(), false);
        }
        if (group != null) {
            lastUsed.put(group, tool);
            styleActive(groupButtons.get(group), true);
            Button gb = groupButtons.get(group);
            gb.setText(tool.icon());
            gb.setTooltip(new Tooltip(tool.displayName()));
        }
        if (onToolSelected != null) onToolSelected.accept(tool);
    }

    private Button createBtn(String text, String tooltip) {
        Button b = new Button(text);
        b.setMinSize(BTN, BTN);
        b.setMaxSize(BTN, BTN);
        b.setStyle(baseBtnStyle());
        b.setTooltip(new Tooltip(tooltip));
        return b;
    }

    private static String baseBtnStyle() {
        return "-fx-background-color:#21262d; -fx-text-fill:#e6edf3;"
                + "-fx-font-size:11px; -fx-background-radius:4; -fx-cursor:hand;"
                + "-fx-padding:2;";
    }

    private void styleActive(Button btn, boolean active) {
        btn.setStyle(active
                ? "-fx-background-color:#388bfd; -fx-text-fill:#ffffff;"
                + "-fx-font-size:11px; -fx-background-radius:4; -fx-cursor:hand; -fx-padding:2;"
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
