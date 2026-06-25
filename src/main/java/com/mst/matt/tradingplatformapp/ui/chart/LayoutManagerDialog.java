package com.mst.matt.tradingplatformapp.ui.chart;

import com.mst.matt.tradingplatformapp.model.DrawingLayout;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * Dialog for managing named drawing layouts (Save / Load / Delete / Rename).
 */
public class LayoutManagerDialog {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private LayoutManagerDialog() {}

    // ── Save dialog ───────────────────────────────────────────────────────────

    /**
     * Prompts the user for a layout name and calls {@code onSave} with the chosen name.
     */
    public static void showSave(Window owner, List<DrawingLayout> existing, Consumer<String> onSave) {
        TextInputDialog dlg = new TextInputDialog("");
        dlg.setTitle("Save Drawing Layout");
        dlg.setHeaderText("Enter a name for this drawing layout");
        dlg.setContentText("Layout name:");
        if (owner != null) {
            try { dlg.initOwner(owner); } catch (Exception ignored) {}
        }
        dlg.getDialogPane().setStyle("-fx-background-color:#161b22;");
        dlg.getEditor().setStyle("-fx-background-color:#21262d; -fx-text-fill:#e6edf3;");

        // Suggest existing names as autocomplete options
        if (!existing.isEmpty()) {
            Label hint = new Label("Existing layouts: "
                    + String.join(", ", existing.stream()
                    .map(DrawingLayout::getName).toList()));
            hint.setStyle("-fx-text-fill:#8b949e; -fx-font-size:11px;");
            dlg.getDialogPane().setExpandableContent(hint);
        }

        dlg.showAndWait().ifPresent(name -> {
            if (!name.isBlank()) {
                onSave.accept(name.trim());
            }
        });
    }

    // ── Load dialog ───────────────────────────────────────────────────────────

    /**
     * Shows a list of saved layouts and calls {@code onLoad} with the selected name.
     */
    public static void showLoad(Window owner, List<DrawingLayout> layouts, Consumer<String> onLoad) {
        if (layouts.isEmpty()) {
            Alert a = new Alert(Alert.AlertType.INFORMATION,
                    "No saved layouts for this symbol/timeframe.", ButtonType.OK);
            a.setTitle("Load Layout");
            a.setHeaderText(null);
            if (owner != null) try { a.initOwner(owner); } catch (Exception ignored) {}
            a.getDialogPane().setStyle("-fx-background-color:#161b22;");
            a.showAndWait();
            return;
        }

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Load Drawing Layout");
        dialog.setHeaderText("Select a layout to load:");
        if (owner != null) try { dialog.initOwner(owner); } catch (Exception ignored) {}
        dialog.getDialogPane().setStyle("-fx-background-color:#161b22;");

        ListView<DrawingLayout> list = new ListView<>();
        list.setItems(FXCollections.observableArrayList(layouts));
        list.setStyle("-fx-background-color:#21262d; -fx-border-color:#30363d;");
        list.setPrefHeight(Math.min(300, layouts.size() * 44 + 16));
        list.setCellFactory(lv -> new LayoutCell());

        dialog.getDialogPane().setContent(list);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        styleButtons(dialog.getDialogPane());

        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                DrawingLayout selected = list.getSelectionModel().getSelectedItem();
                return selected != null ? selected.getName() : null;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(name -> {
            if (name != null) onLoad.accept(name);
        });
    }

    // ── Delete dialog ─────────────────────────────────────────────────────────

    /**
     * Shows a list of layouts and lets the user pick one to delete.
     */
    public static void showDelete(Window owner, List<DrawingLayout> layouts,
                                  Consumer<String> onDelete) {
        if (layouts.isEmpty()) {
            Alert a = new Alert(Alert.AlertType.INFORMATION,
                    "No saved layouts for this symbol/timeframe.", ButtonType.OK);
            a.setTitle("Delete Layout"); a.setHeaderText(null);
            if (owner != null) try { a.initOwner(owner); } catch (Exception ignored) {}
            a.getDialogPane().setStyle("-fx-background-color:#161b22;");
            a.showAndWait();
            return;
        }

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Delete Drawing Layout");
        dialog.setHeaderText("Select a layout to delete:");
        if (owner != null) try { dialog.initOwner(owner); } catch (Exception ignored) {}
        dialog.getDialogPane().setStyle("-fx-background-color:#161b22;");

        ListView<DrawingLayout> list = new ListView<>();
        list.setItems(FXCollections.observableArrayList(layouts));
        list.setStyle("-fx-background-color:#21262d; -fx-border-color:#30363d;");
        list.setPrefHeight(Math.min(300, layouts.size() * 44 + 16));
        list.setCellFactory(lv -> new LayoutCell());

        dialog.getDialogPane().setContent(list);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        styleButtons(dialog.getDialogPane());

        dialog.setResultConverter(bt -> {
            if (bt == ButtonType.OK) {
                DrawingLayout sel = list.getSelectionModel().getSelectedItem();
                return sel != null ? sel.getName() : null;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(name -> {
            if (name != null) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Delete layout \"" + name + "\"?", ButtonType.YES, ButtonType.NO);
                confirm.setTitle("Confirm Delete"); confirm.setHeaderText(null);
                if (owner != null) try { confirm.initOwner(owner); } catch (Exception ignored) {}
                confirm.getDialogPane().setStyle("-fx-background-color:#161b22;");
                confirm.showAndWait()
                        .filter(b -> b == ButtonType.YES)
                        .ifPresent(b -> onDelete.accept(name));
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void styleButtons(DialogPane pane) {
        pane.lookupButton(ButtonType.OK)
                .setStyle("-fx-background-color:#1f6feb; -fx-text-fill:white; -fx-background-radius:6;");
        pane.lookupButton(ButtonType.CANCEL)
                .setStyle("-fx-background-color:#21262d; -fx-text-fill:#e6edf3; -fx-background-radius:6;");
    }

    // ── List cell ─────────────────────────────────────────────────────────────

    private static class LayoutCell extends ListCell<DrawingLayout> {
        @Override
        protected void updateItem(DrawingLayout item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) { setGraphic(null); return; }
            VBox box = new VBox(2);
            box.setPadding(new Insets(6, 8, 6, 8));
            Label name = new Label(item.getName());
            name.setStyle("-fx-text-fill:#e6edf3; -fx-font-size:13px; -fx-font-weight:bold;");
            String date = item.getSavedAtEpoch() > 0
                    ? FMT.format(Instant.ofEpochMilli(item.getSavedAtEpoch()))
                    : "—";
            Label meta = new Label(item.getSymbol() + " " + item.getTimeframe() + "  ·  " + date);
            meta.setStyle("-fx-text-fill:#8b949e; -fx-font-size:11px;");
            box.getChildren().addAll(name, meta);
            setGraphic(box);
            setStyle("-fx-background-color:transparent;");
        }
    }
}
