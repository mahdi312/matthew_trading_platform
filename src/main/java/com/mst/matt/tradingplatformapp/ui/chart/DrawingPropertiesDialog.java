package com.mst.matt.tradingplatformapp.ui.chart;

import com.mst.matt.tradingplatformapp.model.ChartDrawing;
import com.mst.matt.tradingplatformapp.model.ChartDrawingProperties;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Window;

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
        ChartDrawingProperties props;
        props = ChartDrawingProperties.defaultsFor(drawing.getToolType());
        drawing.setProperties(props);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Drawing Properties – " + drawing.getToolType().displayName());
        dialog.setHeaderText(null);
        if (owner != null) {
            try { dialog.initOwner(owner); } catch (Exception ignored) {}
        }
        dialog.getDialogPane().setStyle(
                "-fx-background-color:#161b22; -fx-border-color:#30363d;");

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10);
        grid.setPadding(new Insets(16));

        int row = 0;

        // ── Color picker ──────────────────────────────────────────────────────
        final String[] colorHolder = {props.getColor()};
        grid.add(label("Color:"), 0, row);
        ColorPicker colorPicker = new ColorPicker(safeColor(props.getColor(), "#58a6ff"));
        colorPicker.setStyle("-fx-background-color:#21262d; -fx-text-fill:#e6edf3; -fx-cursor:hand;");
        colorPicker.setPrefWidth(180);
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
        styleCombo.setPrefWidth(180);
        styleCombo.setCellFactory(lv -> styleCell());
        styleCombo.setButtonCell(styleCell());
        grid.add(styleCombo, 1, row++);

        // ── Fill opacity (shapes / channels only) ──────────────────────────────
        boolean hasFill = hasFillProperty(drawing);
        if (hasFill) {
            grid.add(label("Fill Opacity:"), 0, row);
            Slider opacitySlider = new Slider(0, 1,
                    Math.max(0, Math.min(1, props.getFillOpacity())));
            opacitySlider.setPrefWidth(180);
            opacitySlider.setStyle("-fx-control-inner-background:#21262d;");
            Label opacityLabel = label(String.format("%.0f%%", opacitySlider.getValue() * 100));
            opacitySlider.valueProperty().addListener((o, a, n) ->
                    opacityLabel.setText(String.format("%.0f%%", n.doubleValue() * 100)));
            HBox opRow = new HBox(8, opacitySlider, opacityLabel);
            opRow.setAlignment(Pos.CENTER_LEFT);
            grid.add(opRow, 1, row++);

            // Close on OK
            dialog.getDialogPane().setContent(grid);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            styleDialogButtons(dialog.getDialogPane());
            dialog.showAndWait().ifPresent(bt -> {
                if (bt == ButtonType.OK) {
                    props.setColor(colorHolder[0]);
                    props.setLineWidth(weightSlider.getValue());
                    props.setLineStyle(styleCombo.getValue());
                    props.setFillOpacity(opacitySlider.getValue());
                    if (onApply != null) onApply.accept(drawing);
                }
            });
            return;
        }

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        styleDialogButtons(dialog.getDialogPane());
        dialog.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                props.setColor(colorHolder[0]);
                props.setLineWidth(weightSlider.getValue());
                props.setLineStyle(styleCombo.getValue());
                if (onApply != null) onApply.accept(drawing);
            }
        });
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
