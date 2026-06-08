package com.mst.matt.tradingplatformapp.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Searchable checklist for chart indicator overlays (Analysis tab). */
public final class IndicatorPickerDialog {

    private IndicatorPickerDialog() {}

    public static void show(Button owner,
                            Map<String, Boolean> current,
                            Consumer<Map<String, Boolean>> onApply) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Chart Indicators");
        dlg.setHeaderText(null);
        dlg.initModality(Modality.NONE);

        Label title = new Label("Chart Indicators");
        title.setStyle("-fx-font-size:16px; -fx-font-weight:bold; -fx-text-fill:#e6edf3;");
        Label subtitle = new Label("Select overlays to display on the chart");
        subtitle.getStyleClass().add("label-muted");

        TextField search = new TextField();
        search.setPromptText("Search indicators…");

        VBox list = new VBox(6);
        Map<String, CheckBox> boxes = new LinkedHashMap<>();
        current.forEach((name, on) -> {
            CheckBox cb = new CheckBox(name);
            cb.setSelected(on);
            boxes.put(name, cb);
            list.getChildren().add(cb);
        });

        search.textProperty().addListener((o, a, q) -> {
            String needle = q == null ? "" : q.trim().toLowerCase();
            boxes.forEach((name, cb) ->
                    cb.setVisible(needle.isEmpty() || name.toLowerCase().contains(needle)));
        });

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(280);

        VBox root = new VBox(10, title, subtitle, search, scroll);
        root.setPadding(new Insets(12));
        root.setPrefWidth(300);
        dlg.getDialogPane().setContent(root);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);
        dlg.setOnShown(e -> {
            if (owner != null && owner.getScene() != null) {
                dlg.initOwner(owner.getScene().getWindow());
            }
        });
        dlg.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.APPLY) {
                Map<String, Boolean> out = new LinkedHashMap<>();
                boxes.forEach((k, cb) -> out.put(k, cb.isSelected()));
                onApply.accept(out);
            }
        });
    }
}
