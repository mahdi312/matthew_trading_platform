package com.mst.matt.tradingplatformapp.ui.chart;

import com.mst.matt.tradingplatformapp.model.GlobalDrawingSettings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Window;

import java.util.function.Consumer;

/**
 * Dialog for Global Drawing Settings:
 * - Show / hide all drawings
 * - Lock all drawings
 * - Hover quick-delete button toggle
 * - Confirm hover-delete toggle
 * - Default colour / line weight / style / fill-opacity per tool group
 */
public class GlobalDrawingSettingsDialog {

    private GlobalDrawingSettingsDialog() {}

    public static void show(GlobalDrawingSettings current, Window owner,
                            Consumer<GlobalDrawingSettings> onApply) {
        if (current == null) current = new GlobalDrawingSettings();
        final GlobalDrawingSettings settings = current;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Global Drawing Settings");
        dialog.setHeaderText(null);
        if (owner != null) {
            try { dialog.initOwner(owner); } catch (Exception ignored) {}
        }
        dialog.getDialogPane().setStyle(
                "-fx-background-color:#161b22; -fx-border-color:#30363d;");

        VBox root = new VBox(14);
        root.setPadding(new Insets(16));
        root.setStyle("-fx-background-color:#161b22;");

        // ── Visibility & interaction ──────────────────────────────────────────
        root.getChildren().add(sectionHeader("Visibility & Interaction"));

        CheckBox showAll = styledCheck("Show all drawings", settings.isShowAllDrawings());
        CheckBox lockAll = styledCheck("Lock all drawings (prevent edits)", settings.isLockAllDrawings());
        CheckBox hoverDelete = styledCheck("Show quick-delete ✕ on hover", settings.isShowHoverDeleteButton());
        CheckBox confirmDelete = styledCheck("Confirm hover-delete", settings.isConfirmHoverDelete());
        root.getChildren().addAll(showAll, lockAll, hoverDelete, confirmDelete);

        // ── Default visual properties ─────────────────────────────────────────
        root.getChildren().add(sectionHeader("Default Visual Properties"));

        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(8);

        final String[] lineColor  = {settings.getDefaultLineColor()};
        final String[] fibColor   = {settings.getDefaultFibColor()};
        final String[] shapeColor = {settings.getDefaultShapeColor()};
        final String[] annotColor = {settings.getDefaultAnnotationColor()};

        int r = 0;
        grid.add(lbl("Line/Ray Color:"),      0, r);
        grid.add(colorPicker(lineColor[0],  v -> lineColor[0]  = v), 1, r++);
        grid.add(lbl("Fibonacci Color:"),     0, r);
        grid.add(colorPicker(fibColor[0],   v -> fibColor[0]   = v), 1, r++);
        grid.add(lbl("Shape Color:"),         0, r);
        grid.add(colorPicker(shapeColor[0], v -> shapeColor[0] = v), 1, r++);
        grid.add(lbl("Annotation Color:"),    0, r);
        grid.add(colorPicker(annotColor[0], v -> annotColor[0] = v), 1, r++);

        grid.add(lbl("Default Line Weight:"), 0, r);
        Slider weightSlider = new Slider(1, 5, Math.max(1, Math.min(5, settings.getDefaultLineWidth())));
        weightSlider.setMajorTickUnit(1); weightSlider.setSnapToTicks(true);
        weightSlider.setShowTickLabels(true); weightSlider.setShowTickMarks(true);
        weightSlider.setPrefWidth(160);
        Label weightLbl = lbl(String.format("%.0f px", weightSlider.getValue()));
        weightSlider.valueProperty().addListener((o, a, n) ->
                weightLbl.setText(String.format("%.0f px", n.doubleValue())));
        HBox weightRow = new HBox(8, weightSlider, weightLbl);
        weightRow.setAlignment(Pos.CENTER_LEFT);
        grid.add(weightRow, 1, r++);

        grid.add(lbl("Default Line Style:"),  0, r);
        ComboBox<String> styleCombo = new ComboBox<>();
        styleCombo.getItems().addAll("SOLID", "DASHED", "DOTTED", "DASH_DOT");
        styleCombo.setValue(settings.getDefaultLineStyle() != null ? settings.getDefaultLineStyle() : "SOLID");
        styleCombo.setStyle("-fx-background-color:#21262d; -fx-text-fill:#e6edf3;");
        grid.add(styleCombo, 1, r++);

        grid.add(lbl("Default Fill Opacity:"), 0, r);
        Slider fillSlider = new Slider(0, 1, Math.max(0, Math.min(1, settings.getDefaultFillOpacity())));
        fillSlider.setPrefWidth(160);
        Label fillLbl = lbl(String.format("%.0f%%", fillSlider.getValue() * 100));
        fillSlider.valueProperty().addListener((o, a, n) ->
                fillLbl.setText(String.format("%.0f%%", n.doubleValue() * 100)));
        HBox fillRow = new HBox(8, fillSlider, fillLbl);
        fillRow.setAlignment(Pos.CENTER_LEFT);
        grid.add(fillRow, 1, r++);

        root.getChildren().add(grid);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color:#161b22; -fx-background:transparent;");
        scroll.setPrefHeight(440);

        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Node okBtn = dialog.getDialogPane().lookupButton(ButtonType.OK);
        if (okBtn != null) okBtn.setStyle("-fx-background-color:#1f6feb; -fx-text-fill:white; -fx-background-radius:6;");
        Node cancelBtn = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancelBtn != null) cancelBtn.setStyle("-fx-background-color:#21262d; -fx-text-fill:#e6edf3; -fx-background-radius:6;");

        dialog.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                settings.setShowAllDrawings(showAll.isSelected());
                settings.setLockAllDrawings(lockAll.isSelected());
                settings.setShowHoverDeleteButton(hoverDelete.isSelected());
                settings.setConfirmHoverDelete(confirmDelete.isSelected());
                settings.setDefaultLineColor(lineColor[0]);
                settings.setDefaultFibColor(fibColor[0]);
                settings.setDefaultShapeColor(shapeColor[0]);
                settings.setDefaultAnnotationColor(annotColor[0]);
                settings.setDefaultLineWidth(weightSlider.getValue());
                settings.setDefaultLineStyle(styleCombo.getValue());
                settings.setDefaultFillOpacity(fillSlider.getValue());
                if (onApply != null) onApply.accept(settings);
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Label sectionHeader(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:#8b949e; -fx-font-size:11px; -fx-font-weight:bold;"
                + "-fx-padding:6 0 2 0; -fx-border-color:transparent transparent #30363d transparent;"
                + "-fx-border-width:0 0 1 0;");
        l.setMaxWidth(Double.MAX_VALUE);
        return l;
    }

    private static Label lbl(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:#e6edf3; -fx-font-size:12px;");
        return l;
    }

    private static CheckBox styledCheck(String text, boolean selected) {
        CheckBox cb = new CheckBox(text);
        cb.setSelected(selected);
        cb.setStyle("-fx-text-fill:#e6edf3; -fx-font-size:12px;");
        return cb;
    }

    @FunctionalInterface
    private interface ColorConsumer { void accept(String hex); }

    private static ColorPicker colorPicker(String hex, ColorConsumer consumer) {
        Color initial;
        try { initial = Color.web(hex != null ? hex : "#58a6ff"); }
        catch (Exception e) { initial = Color.web("#58a6ff"); }
        ColorPicker cp = new ColorPicker(initial);
        cp.setStyle("-fx-background-color:#21262d; -fx-text-fill:#e6edf3; -fx-cursor:hand;");
        cp.setPrefWidth(160);
        cp.setOnAction(ev -> consumer.accept(toHex(cp.getValue())));
        return cp;
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x",
                (int) (c.getRed() * 255),
                (int) (c.getGreen() * 255),
                (int) (c.getBlue() * 255));
    }
}
