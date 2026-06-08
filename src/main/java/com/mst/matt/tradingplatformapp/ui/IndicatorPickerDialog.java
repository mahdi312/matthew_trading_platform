package com.mst.matt.tradingplatformapp.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Searchable checklist for chart indicator overlays.
 * Shown as a lightweight Popup anchored below the trigger button.
 * Clicking the button again hides the popup (toggle behavior).
 */
public final class IndicatorPickerDialog {

    private IndicatorPickerDialog() {}

    // Track last popup so we can toggle it
    private static Popup lastPopup;
    private static Button lastOwner;

    /**
     * Shows (or hides) the indicator picker popup anchored below {@code owner}.
     *
     * @param owner    The button that triggers the popup.
     * @param current  Map of indicator name → current selected state.
     * @param onApply  Called with the updated map whenever a checkbox changes.
     */
    public static void show(Button owner,
                            Map<String, Boolean> current,
                            Consumer<Map<String, Boolean>> onApply) {

        // Toggle: if a popup is already showing for this button, hide it
        if (lastPopup != null && lastPopup.isShowing() && lastOwner == owner) {
            lastPopup.hide();
            lastPopup = null;
            lastOwner = null;
            return;
        }

        // Close any other open popup first
        if (lastPopup != null && lastPopup.isShowing()) {
            lastPopup.hide();
        }

        // ── Build popup content ─────────────────────────────────────
        Label title = new Label("Chart Indicators");
        title.setStyle("-fx-font-size:15px; -fx-font-weight:bold; -fx-text-fill:#e6edf3;");

        Label subtitle = new Label("Click to toggle overlays on the chart");
        subtitle.setStyle("-fx-font-size:11px; -fx-text-fill:#8b949e;");

        TextField search = new TextField();
        search.setPromptText("Search indicators…");
        search.setStyle("-fx-background-color:#21262d; -fx-text-fill:#e6edf3; -fx-border-color:#30363d;");

        VBox list = new VBox(4);
        list.setPadding(new Insets(4, 0, 4, 0));
        Map<String, CheckBox> boxes = new LinkedHashMap<>();

        current.forEach((name, on) -> {
            CheckBox cb = new CheckBox(name);
            cb.setSelected(on);
            cb.setStyle("-fx-text-fill:#e6edf3; -fx-font-size:13px;");
            // Apply immediately on each toggle — no need for Apply button
            cb.selectedProperty().addListener((obs, wasOn, isOn) -> {
                Map<String, Boolean> out = new LinkedHashMap<>();
                boxes.forEach((k, box) -> out.put(k, box.isSelected()));
                onApply.accept(out);
            });
            boxes.put(name, cb);
            list.getChildren().add(cb);
        });

        // Search filter
        search.textProperty().addListener((o, a, q) -> {
            String needle = q == null ? "" : q.trim().toLowerCase();
            boxes.forEach((name, cb) -> {
                boolean match = needle.isEmpty() || name.toLowerCase().contains(needle);
                cb.setVisible(match);
                cb.setManaged(match);
            });
        });

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(260);
        scroll.setStyle("-fx-background-color:#161b22; -fx-background:#161b22;");

        VBox root = new VBox(8, title, subtitle, search, scroll);
        root.setPadding(new Insets(14));
        root.setPrefWidth(280);
        root.setStyle("-fx-background-color:#161b22; -fx-border-color:#30363d;"
                + "-fx-border-width:1; -fx-background-radius:8; -fx-border-radius:8;");

        // ── Create and show popup ───────────────────────────────────
        Popup popup = new Popup();
        popup.getContent().add(root);
        popup.setAutoHide(true);
        popup.setAutoFix(true);

        if (owner != null && owner.getScene() != null && owner.getScene().getWindow() != null) {
            // Position popup below the button
            double x = owner.localToScreen(0, owner.getHeight() + 4).getX();
            double y = owner.localToScreen(0, owner.getHeight() + 4).getY();
            popup.show(owner.getScene().getWindow(), x, y);
        } else {
            popup.show(getAnyWindow(owner));
        }

        lastPopup = popup;
        lastOwner = owner;
    }

    private static javafx.stage.Window getAnyWindow(Node node) {
        if (node != null && node.getScene() != null) {
            return node.getScene().getWindow();
        }
        return null;
    }
}
