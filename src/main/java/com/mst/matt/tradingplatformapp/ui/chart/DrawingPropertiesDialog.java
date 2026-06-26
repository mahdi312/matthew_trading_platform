package com.mst.matt.tradingplatformapp.ui.chart;

import com.mst.matt.tradingplatformapp.model.ChartDrawing;
import com.mst.matt.tradingplatformapp.model.ChartDrawingProperties;
import com.mst.matt.tradingplatformapp.model.ChartDrawingToolType;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Per-drawing properties dialog: colour, line weight, line style, fill opacity.
 *
 * <p>Opened via right-click → Properties… or the "🎨 Properties" context menu item.
 * Changes are applied live to the drawing object; persistence is triggered via
 * the {@code onApply} callback.
 */
public class DrawingPropertiesDialog {

    private static final String[] LINE_STYLES = {"SOLID", "DASHED", "DOTTED", "DASH_DOT"};

    private DrawingPropertiesDialog() {}

    /**
     * Shows the properties dialog for the given drawing.
     *
     * @param drawing  the drawing to edit
     * @param owner    parent window (for modal ownership)
     * @param onApply  called when the user clicks OK with the modified drawing
     */
    public static void show(ChartDrawing drawing, Window owner, Consumer<ChartDrawing> onApply) {
        if (drawing == null) return;
        ChartDrawingProperties props = drawing.getProperties() != null
                ? drawing.getProperties()
                : ChartDrawingProperties.defaultsFor(drawing.getToolType());
        drawing.setProperties(props);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Drawing Properties – " + drawing.getToolType().displayName());
        dialog.setHeaderText(null);
        if (owner != null) {
            try { dialog.initOwner(owner); } catch (Exception ignored) {}
        }
        dialog.getDialogPane().setStyle(
                "-fx-background-color:#161b22; -fx-border-color:#30363d;");
        dialog.getDialogPane().setPrefWidth(520);

        VBox root = new VBox(16);
        root.setPadding(new Insets(16));

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10);

        int row = 0;

        // ── Color picker ──────────────────────────────────────────────────────
        final String[] colorHolder = {props.getColor()};
        grid.add(label("Color:"), 0, row);
        ColorPicker colorPicker = new ColorPicker(safeColor(props.getColor(), "#58a6ff"));
        colorPicker.setStyle("-fx-background-color:#21262d; -fx-text-fill:#e6edf3; -fx-cursor:hand;");
        colorPicker.setPrefWidth(200);
        colorPicker.setOnAction(e -> colorHolder[0] = toHex(colorPicker.getValue()));
        grid.add(colorPicker, 1, row++);

        // ── Line weight ───────────────────────────────────────────────────────
        grid.add(label("Line Weight:"), 0, row);
        Slider weightSlider = new Slider(1, 5, Math.max(1, Math.min(5, props.getLineWidth())));
        weightSlider.setMajorTickUnit(1); weightSlider.setSnapToTicks(true);
        weightSlider.setShowTickLabels(true); weightSlider.setShowTickMarks(true);
        weightSlider.setPrefWidth(180);
        weightSlider.setStyle("-fx-control-inner-background:#21262d;");
        Label weightLabel = label(String.format("%.0f px", weightSlider.getValue()));
        weightSlider.valueProperty().addListener((o, a, n) ->
                weightLabel.setText(String.format("%.0f px", n.doubleValue())));
        HBox weightRow = new HBox(8, weightSlider, weightLabel);
        weightRow.setAlignment(Pos.CENTER_LEFT);
        grid.add(weightRow, 1, row++);

        // ── Line style ────────────────────────────────────────────────────────
        grid.add(label("Line Style:"), 0, row);
        ComboBox<String> styleCombo = new ComboBox<>();
        styleCombo.getItems().addAll(LINE_STYLES);
        styleCombo.setValue(props.getLineStyle() != null ? props.getLineStyle() : "SOLID");
        styleCombo.setStyle("-fx-background-color:#21262d; -fx-text-fill:#e6edf3;");
        styleCombo.setPrefWidth(200);
        styleCombo.setCellFactory(lv -> styleCell());
        styleCombo.setButtonCell(styleCell());
        grid.add(styleCombo, 1, row++);

        // ── Fill opacity (shapes / channels only) ──────────────────────────────
        boolean hasFill = hasFillProperty(drawing);
        Slider opacitySlider = null;
        if (hasFill) {
            grid.add(label("Fill Opacity:"), 0, row);
            opacitySlider = new Slider(0, 1, Math.max(0, Math.min(1, props.getFillOpacity())));
            opacitySlider.setPrefWidth(180);
            opacitySlider.setStyle("-fx-control-inner-background:#21262d;");
            Label opacityLabel = label(String.format("%.0f%%", opacitySlider.getValue() * 100));
            Slider finalOpSlider = opacitySlider;
            opacitySlider.valueProperty().addListener((o, a, n) ->
                    opacityLabel.setText(String.format("%.0f%%", n.doubleValue() * 100)));
            HBox opRow = new HBox(8, opacitySlider, opacityLabel);
            opRow.setAlignment(Pos.CENTER_LEFT);
            grid.add(opRow, 1, row++);
        }

        root.getChildren().add(grid);

        // ── Fibonacci Custom Levels panel ─────────────────────────────────────
        boolean isFibTool = isFibonacciTool(drawing.getToolType());
        final List<Double> fibLevels = new ArrayList<>();
        VBox fibPanel = null;
        if (isFibTool) {
            // Initialise from existing custom levels or built-in defaults
            if (props.getCustomFibLevels() != null && !props.getCustomFibLevels().isEmpty()) {
                fibLevels.addAll(props.getCustomFibLevels());
            } else {
                fibLevels.addAll(defaultFibLevels(drawing.getToolType()));
            }

            fibPanel = buildFibLevelPanel(fibLevels);
            root.getChildren().add(fibPanel);
        }

        dialog.getDialogPane().setContent(root);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        styleDialogButtons(dialog.getDialogPane());

        final Slider finalOpacity = opacitySlider;
        ChartDrawingProperties finalProps = props;
        dialog.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                finalProps.setColor(colorHolder[0]);
                finalProps.setLineWidth(weightSlider.getValue());
                finalProps.setLineStyle(styleCombo.getValue());
                if (hasFill && finalOpacity != null) {
                    finalProps.setFillOpacity(finalOpacity.getValue());
                }
                if (isFibTool) {
                    // Save custom Fib levels (sorted ascending)
                    fibLevels.sort(Double::compareTo);
                    finalProps.setCustomFibLevels(new ArrayList<>(fibLevels));
                }
                if (onApply != null) onApply.accept(drawing);
            }
        });
    }

    // ── Fibonacci level panel ─────────────────────────────────────────────────

    /**
     * Builds the Fibonacci custom-levels editor panel.
     * The list is live — changes to {@code levels} are reflected immediately.
     */
    private static VBox buildFibLevelPanel(List<Double> levels) {
        VBox panel = new VBox(8);
        panel.setStyle(
                "-fx-background-color:#21262d; -fx-background-radius:6;"
                + "-fx-border-color:#30363d; -fx-border-radius:6; -fx-border-width:1;"
                + "-fx-padding:12;");

        Label title = new Label("📐 Fibonacci Levels");
        title.setStyle("-fx-text-fill:#e6edf3; -fx-font-size:13px; -fx-font-weight:bold;");

        Label hint = new Label("Add, remove, or edit levels (0 = 0%, 1 = 100%, 1.618 = 161.8%)");
        hint.setStyle("-fx-text-fill:#8b949e; -fx-font-size:10px;");
        hint.setWrapText(true);

        VBox levelsList = new VBox(4);

        // Rebuild helper — recreates the level rows from the current list
        Runnable[] rebuild = {null};
        rebuild[0] = () -> {
            levelsList.getChildren().clear();
            List<Double> sortedLevels = new ArrayList<>(levels);
            sortedLevels.sort(Double::compareTo);
            levels.clear();
            levels.addAll(sortedLevels);

            for (int i = 0; i < levels.size(); i++) {
                final int idx = i;
                double lv = levels.get(i);

                HBox row = new HBox(6);
                row.setAlignment(Pos.CENTER_LEFT);

                // Level value input
                TextField lvField = new TextField(String.format("%.4f", lv));
                lvField.setPrefWidth(90);
                lvField.setStyle("-fx-background-color:#161b22; -fx-text-fill:#e6edf3;"
                        + "-fx-border-color:#30363d; -fx-border-radius:4; -fx-background-radius:4;");
                lvField.setPromptText("e.g. 0.5");

                // Display percentage label
                Label pctLabel = new Label(String.format("= %.2f%%", lv * 100));
                pctLabel.setStyle("-fx-text-fill:#8b949e; -fx-font-size:11px;");
                pctLabel.setPrefWidth(80);
                lvField.textProperty().addListener((obs, o, n) -> {
                    try {
                        double v = Double.parseDouble(n.trim());
                        pctLabel.setText(String.format("= %.2f%%", v * 100));
                        levels.set(idx, v);
                    } catch (NumberFormatException ignored) {}
                });

                // Remove button
                Button removeBtn = new Button("✕");
                removeBtn.setStyle("-fx-background-color:#da3633; -fx-text-fill:white;"
                        + "-fx-background-radius:4; -fx-padding:2 6; -fx-cursor:hand;"
                        + "-fx-font-size:10px;");
                removeBtn.setTooltip(new Tooltip("Remove this level"));
                removeBtn.setOnAction(e -> {
                    levels.remove(idx);
                    rebuild[0].run();
                });

                row.getChildren().addAll(lvField, pctLabel, removeBtn);
                levelsList.getChildren().add(row);
            }
        };
        rebuild[0].run();

        // Add new level row
        HBox addRow = new HBox(6);
        addRow.setAlignment(Pos.CENTER_LEFT);
        TextField addField = new TextField();
        addField.setPromptText("e.g. 0.854");
        addField.setPrefWidth(90);
        addField.setStyle("-fx-background-color:#161b22; -fx-text-fill:#e6edf3;"
                + "-fx-border-color:#30363d; -fx-border-radius:4; -fx-background-radius:4;");
        Button addBtn = new Button("+ Add Level");
        addBtn.setStyle("-fx-background-color:#1f6feb; -fx-text-fill:white;"
                + "-fx-background-radius:4; -fx-padding:4 10; -fx-cursor:hand; -fx-font-size:11px;");
        Runnable doAdd = () -> {
            try {
                double v = Double.parseDouble(addField.getText().trim());
                if (!levels.contains(v)) {
                    levels.add(v);
                    rebuild[0].run();
                    addField.clear();
                }
            } catch (NumberFormatException ex) {
                addField.setStyle("-fx-background-color:#161b22; -fx-text-fill:#f85149;"
                        + "-fx-border-color:#f85149; -fx-border-radius:4; -fx-background-radius:4;");
            }
        };
        addBtn.setOnAction(e -> doAdd.run());
        addField.setOnAction(e -> doAdd.run());

        // Reset to defaults button
        Button resetBtn = new Button("↺ Reset to Defaults");
        resetBtn.setStyle("-fx-background-color:#21262d; -fx-text-fill:#8b949e;"
                + "-fx-background-radius:4; -fx-padding:4 10; -fx-cursor:hand; -fx-font-size:11px;"
                + "-fx-border-color:#30363d; -fx-border-radius:4; -fx-border-width:1;");
        resetBtn.setOnAction(e -> {
            levels.clear();
            levels.addAll(List.of(0.0, 0.236, 0.382, 0.5, 0.618, 0.786, 1.0));
            rebuild[0].run();
        });

        addRow.getChildren().addAll(addField, addBtn, resetBtn);

        // Scrollable list (max 5 levels visible at once)
        ScrollPane scroll = new ScrollPane(levelsList);
        scroll.setFitToWidth(true);
        scroll.setMaxHeight(160);
        scroll.setStyle("-fx-background-color:#21262d; -fx-background:#21262d;"
                + "-fx-border-color:transparent;");

        panel.getChildren().addAll(title, hint, scroll, addRow);
        return panel;
    }

    /** Returns default Fib levels appropriate for the given tool type. */
    private static List<Double> defaultFibLevels(ChartDrawingToolType type) {
        return switch (type) {
            case FIB_EXTENSION        -> List.of(0.618, 1.0, 1.272, 1.618, 2.618, 4.236);
            case FIB_FAN              -> List.of(0.236, 0.382, 0.5, 0.618, 0.786);
            case FIB_CHANNEL          -> List.of(0.0, 0.382, 0.618, 1.0, 1.618);
            case FIB_SPEED_RESISTANCE -> List.of(0.125, 0.2, 0.333, 0.5, 0.667);
            default                   -> List.of(0.0, 0.236, 0.382, 0.5, 0.618, 0.786, 1.0);
        };
    }

    /** Returns true when the drawing tool is a Fibonacci type. */
    private static boolean isFibonacciTool(ChartDrawingToolType type) {
        if (type == null) return false;
        return switch (type) {
            case FIB_RETRACEMENT, FIB_EXTENSION, FIB_FAN,
                 FIB_CHANNEL, FIB_TIME_ZONES, FIB_SPEED_RESISTANCE -> true;
            default -> false;
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean hasFillProperty(ChartDrawing d) {
        if (d.getToolType() == null) return false;
        return switch (d.getToolType()) {
            case RECTANGLE, TRIANGLE, ELLIPSE, FLAT_CHANNEL,
                 PARALLEL_CHANNEL, LONG_POSITION, SHORT_POSITION -> true;
            default -> false;
        };
    }

    private static Label label(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:#e6edf3; -fx-font-size:12px;");
        return l;
    }

    private static void styleDialogButtons(DialogPane pane) {
        pane.lookupButton(ButtonType.OK)
                .setStyle("-fx-background-color:#1f6feb; -fx-text-fill:white; -fx-background-radius:6;");
        pane.lookupButton(ButtonType.CANCEL)
                .setStyle("-fx-background-color:#21262d; -fx-text-fill:#e6edf3; -fx-background-radius:6;");
    }

    private static ListCell<String> styleCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                setText(null);
                setStyle("-fx-background-color:transparent;");
                Rectangle preview = new Rectangle(80, 3);
                switch (item) {
                    case "DASHED"   -> preview.getStrokeDashArray().setAll(8.0, 5.0);
                    case "DOTTED"   -> preview.getStrokeDashArray().setAll(2.0, 4.0);
                    case "DASH_DOT" -> preview.getStrokeDashArray().setAll(8.0, 4.0, 2.0, 4.0);
                    default         -> preview.getStrokeDashArray().clear(); // SOLID
                }
                preview.setFill(Color.web("#58a6ff"));
                Label lbl = new Label(styleLabel(item));
                lbl.setStyle("-fx-text-fill:#e6edf3; -fx-font-size:11px; -fx-padding:0 0 0 6;");
                HBox box = new HBox(6, preview, lbl);
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
            }
        };
    }

    private static String styleLabel(String s) {
        return switch (s) {
            case "DASHED"   -> "Dashed";
            case "DOTTED"   -> "Dotted";
            case "DASH_DOT" -> "Dash·Dot";
            default          -> "Solid";
        };
    }

    private static Color safeColor(String hex, String fallback) {
        try { if (hex != null) return Color.web(hex); }
        catch (Exception ignored) {}
        return Color.web(fallback);
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x",
                (int) (c.getRed() * 255),
                (int) (c.getGreen() * 255),
                (int) (c.getBlue() * 255));
    }
}
