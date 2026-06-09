package com.mst.matt.tradingplatformapp.ui;

import com.mst.matt.tradingplatformapp.model.IndicatorDefinition;
import com.mst.matt.tradingplatformapp.model.IndicatorDefinition.PriceSource;
import com.mst.matt.tradingplatformapp.model.IndicatorDefinition.Type;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.*;
import java.util.function.Consumer;

/**
 * Two-level indicator picker:
 *
 * Level 1 – Popup with searchable list of all available indicator types, grouped by category.
 *            Each row has a [+] button to add an instance of that indicator.
 *
 * Level 2 – When [+] is clicked, a modal configuration dialog opens where the user
 *            sets period(s), price source, color, and line weight before confirming.
 *            The indicator is then added to the active list.
 *
 * Active indicators can be removed or re-configured via the active panel.
 */
public final class IndicatorPickerDialog {

    private IndicatorPickerDialog() {}

    // ── Category groupings ─────────────────────────────────────

    private static final Map<String, List<Type>> CATEGORIES = new LinkedHashMap<>();
    static {
        CATEGORIES.put("📈 Moving Averages", List.of(
                Type.EMA, Type.SMA, Type.WMA, Type.DEMA, Type.TEMA,
                Type.HULL_MA, Type.KAMA, Type.ZLEMA, Type.VWAP));
        CATEGORIES.put("📊 Bands & Channels", List.of(
                Type.BOLLINGER, Type.KELTNER, Type.DONCHIAN, Type.PARABOLIC_SAR));
        CATEGORIES.put("⚡ Momentum", List.of(
                Type.RSI, Type.MACD, Type.STOCHASTIC, Type.STOCH_RSI, Type.CCI,
                Type.WILLIAMS_R, Type.ROC, Type.DPO, Type.AROON,
                Type.CMO, Type.FISHER, Type.PPO));
        CATEGORIES.put("📉 Volatility", List.of(
                Type.ATR, Type.ULCER_INDEX));
        CATEGORIES.put("📦 Volume", List.of(
                Type.OBV, Type.MFI, Type.CMF, Type.CHAIKIN_OSC));
        CATEGORIES.put("🎯 Trend Strength", List.of(
                Type.ADX));
        CATEGORIES.put("🌩 Complex", List.of(
                Type.ICHIMOKU, Type.SUPPORT_RESISTANCE));
    }

    // Track popup so we can toggle it
    private static Popup lastPopup;
    private static Button lastOwner;

    // ── Public API ─────────────────────────────────────────────

    /**
     * Shows / hides the indicator picker popup.
     *
     * @param owner      The button that triggered the picker.
     * @param active     The currently active indicator list (will be mutated in place).
     * @param onChange   Callback fired after any add / remove.
     */
    public static void show(Button owner,
                            List<IndicatorDefinition> active,
                            Consumer<List<IndicatorDefinition>> onChange) {

        // Toggle
        if (lastPopup != null && lastPopup.isShowing() && lastOwner == owner) {
            lastPopup.hide();
            lastPopup = null;
            lastOwner = null;
            return;
        }
        if (lastPopup != null && lastPopup.isShowing()) {
            lastPopup.hide();
        }

        // ── Root layout ──────────────────────────────────────
        VBox root = new VBox(0);
        root.setPrefWidth(480);
        root.setMaxHeight(620);
        root.setStyle("""
                -fx-background-color:#161b22;
                -fx-border-color:#30363d;
                -fx-border-width:1;
                -fx-background-radius:10;
                -fx-border-radius:10;
                -fx-effect:dropshadow(gaussian,rgba(0,0,0,0.6),20,0,0,4);
                """);

        // ── Header ───────────────────────────────────────────
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 16, 10, 16));
        header.setStyle("-fx-background-color:#1c2128; -fx-background-radius:10 10 0 0;");
        Label title = styledLabel("📊  Chart Indicators", "#e6edf3", 15, true);
        Label sub   = styledLabel("Add unlimited indicators with custom settings", "#8b949e", 11, false);
        VBox titleBox = new VBox(2, title, sub);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        header.getChildren().add(titleBox);
        root.getChildren().add(header);

        // ── Search ───────────────────────────────────────────
        TextField search = new TextField();
        search.setPromptText("Search indicators…");
        search.setStyle("""
                -fx-background-color:#21262d;
                -fx-text-fill:#e6edf3;
                -fx-border-color:#30363d;
                -fx-border-radius:6;
                -fx-background-radius:6;
                -fx-prompt-text-fill:#484f58;
                -fx-font-size:13;
                """);
        VBox searchBox = new VBox(search);
        searchBox.setPadding(new Insets(8, 14, 6, 14));
        root.getChildren().add(searchBox);

        // ── Active indicators panel ───────────────────────────
        VBox activePanel = new VBox(4);
        activePanel.setPadding(new Insets(6, 14, 6, 14));
        Label activeHdr = styledLabel("ACTIVE", "#484f58", 10, true);
        activePanel.getChildren().add(activeHdr);
        VBox activeList = buildActiveList(active, onChange);
        activePanel.getChildren().add(activeList);

        // Separator
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color:#30363d;");

        // ── Indicator library ─────────────────────────────────
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(340);
        scrollPane.setStyle("-fx-background-color:#161b22; -fx-background:#161b22;");
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        VBox library = new VBox(0);

        // Build category groups
        List<Node> allRows = new ArrayList<>();
        CATEGORIES.forEach((catName, types) -> {
            Label catLabel = styledLabel(catName, "#58a6ff", 11, true);
            catLabel.setPadding(new Insets(10, 14, 4, 14));
            VBox catBox = new VBox(0);
            catBox.getChildren().add(catLabel);
            for (Type type : types) {
                HBox row = buildIndicatorRow(type, active, onChange, scrollPane);
                catBox.getChildren().add(row);
                allRows.add(row);
            }
            library.getChildren().add(catBox);
        });

        scrollPane.setContent(library);

        root.getChildren().addAll(activePanel, sep, scrollPane);

        // ── Search filter ────────────────────────────────────
        search.textProperty().addListener((o, a, q) -> {
            String needle = q == null ? "" : q.trim().toLowerCase();
            for (Node row : allRows) {
                if (row instanceof HBox hb && hb.getUserData() instanceof String name) {
                    boolean match = needle.isEmpty() || name.toLowerCase().contains(needle);
                    hb.setVisible(match);
                    hb.setManaged(match);
                }
            }
        });

        // ── Show popup ───────────────────────────────────────
        Popup popup = new Popup();
        popup.getContent().add(root);
        popup.setAutoHide(true);
        popup.setAutoFix(true);

        if (owner != null && owner.getScene() != null && owner.getScene().getWindow() != null) {
            double x = owner.localToScreen(0, owner.getHeight() + 6).getX();
            double y = owner.localToScreen(0, owner.getHeight() + 6).getY();
            popup.show(owner.getScene().getWindow(), x, y);
        }

        lastPopup = popup;
        lastOwner = owner;
    }

    // ── Active indicators list ────────────────────────────────

    private static VBox buildActiveList(List<IndicatorDefinition> active,
                                        Consumer<List<IndicatorDefinition>> onChange) {
        VBox list = new VBox(3);
        refreshActiveList(list, active, onChange);
        return list;
    }

    static void refreshActiveList(VBox list, List<IndicatorDefinition> active,
                                  Consumer<List<IndicatorDefinition>> onChange) {
        list.getChildren().clear();
        if (active.isEmpty()) {
            list.getChildren().add(styledLabel("No active indicators.", "#484f58", 11, false));
            return;
        }
        for (IndicatorDefinition def : new ArrayList<>(active)) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(3, 0, 3, 0));

            // Color swatch
            Rectangle swatch = new Rectangle(10, 10, Color.web(def.getColor()));
            swatch.setArcWidth(3);
            swatch.setArcHeight(3);

            Label name = styledLabel(def.getLabel(), "#e6edf3", 12, false);
            HBox.setHgrow(name, Priority.ALWAYS);

            // Edit button
            Button edit = iconButton("⚙", "#8b949e");
            edit.setOnAction(e -> openConfigDialog(def, success -> {
                onChange.accept(active);
                refreshActiveList(list, active, onChange);
            }));

            // Remove button
            Button remove = iconButton("✕", "#f85149");
            remove.setOnAction(e -> {
                active.remove(def);
                onChange.accept(active);
                refreshActiveList(list, active, onChange);
            });

            row.getChildren().addAll(swatch, name, edit, remove);
            list.getChildren().add(row);
        }
    }

    // ── Library row ──────────────────────────────────────────

    private static HBox buildIndicatorRow(Type type,
                                          List<IndicatorDefinition> active,
                                          Consumer<List<IndicatorDefinition>> onChange,
                                          ScrollPane parent) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(5, 14, 5, 22));
        row.setUserData(type.displayName + " " + type.shortName);
        row.setStyle("-fx-background-color:transparent;");
        row.setOnMouseEntered(e -> row.setStyle("-fx-background-color:#21262d;"));
        row.setOnMouseExited(e  -> row.setStyle("-fx-background-color:transparent;"));

        VBox nameBox = new VBox(1);
        nameBox.getChildren().addAll(
                styledLabel(type.displayName, "#e6edf3", 13, false),
                styledLabel(type.shortName + " · " + type.defaultPane.name().toLowerCase(),
                        "#484f58", 10, false)
        );
        HBox.setHgrow(nameBox, Priority.ALWAYS);

        Button addBtn = new Button("＋ Add");
        addBtn.setStyle("""
                -fx-background-color:#1f6feb;
                -fx-text-fill:white;
                -fx-font-size:11;
                -fx-background-radius:5;
                -fx-padding:3 10;
                -fx-cursor:hand;
                """);
        addBtn.setOnAction(e -> {
            IndicatorDefinition newDef = new IndicatorDefinition(type);
            openConfigDialog(newDef, success -> {
                if (success) {
                    active.add(newDef);
                    onChange.accept(active);
                    // Refresh active panel in popup
                    if (parent != null && parent.getParent() instanceof VBox root) {
                        rebuildActiveInRoot(root, active, onChange);
                    }
                }
            });
        });

        row.getChildren().addAll(nameBox, addBtn);
        return row;
    }

    private static void rebuildActiveInRoot(VBox root, List<IndicatorDefinition> active,
                                            Consumer<List<IndicatorDefinition>> onChange) {
        // Find and update the activeList VBox inside the active panel
        for (Node n : root.getChildren()) {
            if (n instanceof VBox panel) {
                for (Node child : panel.getChildren()) {
                    if (child instanceof VBox activeList
                            && activeList.getUserData() == null) {
                        refreshActiveList(activeList, active, onChange);
                        return;
                    }
                }
            }
        }
    }

    // ── Config Dialog ─────────────────────────────────────────

    /**
     * Opens a modal config dialog for the indicator definition.
     *
     * @param def       the indicator to configure
     * @param callback  called with true if user clicked OK, false if cancelled
     */
    public static void openConfigDialog(IndicatorDefinition def,
                                        Consumer<Boolean> callback) {
        Stage dialog = new Stage(StageStyle.UNDECORATED);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Configure " + def.getType().displayName);

        VBox root = new VBox(12);
        root.setPadding(new Insets(20));
        root.setPrefWidth(380);
        root.setStyle("""
                -fx-background-color:#1c2128;
                -fx-border-color:#30363d;
                -fx-border-width:1;
                -fx-background-radius:10;
                -fx-border-radius:10;
                """);

        // Title
        Label title = styledLabel("Configure: " + def.getType().displayName, "#e6edf3", 15, true);
        root.getChildren().add(title);

        // ── Period controls ──
        VBox paramsBox = new VBox(8);

        // Main period — shown for most indicators (not SAR, OBV)
        boolean hasPeriod = def.getType() != Type.PARABOLIC_SAR && def.getType() != Type.OBV;
        Spinner<Integer> periodSpinner = null;
        if (hasPeriod) {
            periodSpinner = makeIntSpinner(1, 500, def.getPeriod());
            paramsBox.getChildren().add(labeledControl(periodLabel(def.getType()), periodSpinner));
        }

        // Secondary period
        Spinner<Integer> period2Spinner = null;
        String p2label = period2Label(def.getType());
        if (p2label != null) {
            int p2Val = def.getPeriod2() > 0 ? def.getPeriod2() : 1;
            period2Spinner = makeIntSpinner(1, 500, p2Val);
            paramsBox.getChildren().add(labeledControl(p2label, period2Spinner));
        }

        // Tertiary period
        Spinner<Integer> period3Spinner = null;
        String p3label = period3Label(def.getType());
        if (p3label != null) {
            int p3Val = def.getPeriod3() > 0 ? def.getPeriod3() : 1;
            period3Spinner = makeIntSpinner(1, 500, p3Val);
            paramsBox.getChildren().add(labeledControl(p3label, period3Spinner));
        }

        // Price source (shown for single-source indicators)
        ComboBox<PriceSource> sourceCombo = null;
        if (showsPriceSource(def.getType())) {
            sourceCombo = new ComboBox<>(FXCollections.observableArrayList(PriceSource.values()));
            sourceCombo.setValue(def.getPriceSource() != null ? def.getPriceSource() : PriceSource.CLOSE);
            sourceCombo.setCellFactory(lv -> priceSourceCell());
            sourceCombo.setButtonCell(priceSourceCell());
            styleCombo(sourceCombo);
            paramsBox.getChildren().add(labeledControl("Price Source", sourceCombo));
        }

        // Bollinger stdDev stored as period2 * 10 (e.g. 20 = 2.0)
        Spinner<Double> bbDevSpinner = null;
        if (def.getType() == Type.BOLLINGER) {
            double devVal = def.getPeriod2() > 0 ? def.getPeriod2() / 10.0 : 2.0;
            bbDevSpinner = makeDoubleSpinner(0.5, 5.0, devVal, 0.5);
            paramsBox.getChildren().add(labeledControl("Std Dev Multiplier", bbDevSpinner));
        }

        root.getChildren().add(paramsBox);
        root.getChildren().add(separator());

        // ── Visual controls ──
        VBox visualBox = new VBox(8);
        visualBox.getChildren().add(styledLabel("Visual", "#8b949e", 12, true));

        // Color picker
        ColorPicker colorPicker = new ColorPicker(Color.web(def.getColor()));
        colorPicker.setStyle("-fx-background-color:#21262d; -fx-border-color:#30363d; -fx-background-radius:6;");
        visualBox.getChildren().add(labeledControl("Line Color", colorPicker));

        // Line weight
        Spinner<Double> weightSpinner = makeDoubleSpinner(0.5, 5.0, def.getLineWeight(), 0.5);
        visualBox.getChildren().add(labeledControl("Line Weight (px)", weightSpinner));

        root.getChildren().add(visualBox);
        root.getChildren().add(separator());

        // ── Buttons ──
        HBox btnRow = new HBox(10);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("""
                -fx-background-color:#21262d;
                -fx-text-fill:#e6edf3;
                -fx-border-color:#30363d;
                -fx-border-radius:6;
                -fx-background-radius:6;
                -fx-padding:6 16;
                -fx-cursor:hand;
                """);
        Button okBtn = new Button("Apply");
        okBtn.setStyle("""
                -fx-background-color:#1f6feb;
                -fx-text-fill:white;
                -fx-background-radius:6;
                -fx-padding:6 16;
                -fx-cursor:hand;
                -fx-font-weight:bold;
                """);

        // Store refs for lambda
        final Spinner<Integer> p1Ref = periodSpinner;
        final Spinner<Integer> p2Ref = period2Spinner;
        final Spinner<Integer> p3Ref = period3Spinner;
        final ComboBox<PriceSource> srcRef = sourceCombo;
        final Spinner<Double> devRef = bbDevSpinner;

        cancelBtn.setOnAction(e -> { dialog.close(); callback.accept(false); });
        okBtn.setOnAction(e -> {
            // Apply all settings
            if (p1Ref != null) def.setPeriod(p1Ref.getValue());
            if (def.getType() == Type.BOLLINGER && devRef != null) {
                // Store stdDev as integer * 10
                def.setPeriod2((int)(devRef.getValue() * 10));
            } else if (p2Ref != null) {
                def.setPeriod2(p2Ref.getValue());
            }
            if (p3Ref != null) def.setPeriod3(p3Ref.getValue());
            if (srcRef != null) def.setPriceSource(srcRef.getValue());

            // Color
            Color c = colorPicker.getValue();
            def.setColor(String.format("#%02x%02x%02x",
                    (int)(c.getRed()*255), (int)(c.getGreen()*255), (int)(c.getBlue()*255)));
            def.setLineWeight(weightSpinner.getValue());
            def.autoLabel();

            dialog.close();
            callback.accept(true);
        });

        btnRow.getChildren().addAll(cancelBtn, okBtn);
        root.getChildren().add(btnRow);

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        dialog.setScene(scene);
        dialog.show();
    }

    // ── Parameter label helpers ───────────────────────────────

    private static String periodLabel(Type t) {
        return switch (t) {
            case MACD, PPO   -> "Fast Period";
            case KAMA        -> "Efficiency Ratio Period";
            case KELTNER     -> "EMA Period";
            case VWAP        -> "Period";
            case CHAIKIN_OSC -> "Fast Period";
            default          -> "Period";
        };
    }

    private static String period2Label(Type t) {
        return switch (t) {
            case MACD, PPO   -> "Slow Period";
            case STOCHASTIC  -> "%D Smoothing";
            case STOCH_RSI   -> "%D Smoothing";
            case KELTNER     -> "ATR Period";
            case ICHIMOKU    -> "Kijun Period (26)";
            case CHAIKIN_OSC -> "Slow Period";
            case ADX         -> null; // uses same period
            default          -> null;
        };
    }

    private static String period3Label(Type t) {
        return switch (t) {
            case MACD, PPO -> "Signal Period";
            case ICHIMOKU  -> "Senkou B Period (52)";
            case STOCH_RSI -> "RSI Period";
            default        -> null;
        };
    }

    private static boolean showsPriceSource(Type t) {
        // Volume-based or bar-based indicators don't use a price source selector
        return switch (t) {
            case OBV, MFI, CMF, CHAIKIN_OSC,
                    ADX, ICHIMOKU, SUPPORT_RESISTANCE,
                    STOCHASTIC, PARABOLIC_SAR,
                    KELTNER, ATR -> false;
            default -> true;
        };
    }

    // ── UI builders ──────────────────────────────────────────

    private static HBox labeledControl(String label, Node control) {
        Label lbl = styledLabel(label, "#8b949e", 12, false);
        lbl.setPrefWidth(160);
        HBox row = new HBox(10, lbl, control);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Label styledLabel(String text, String color, int size, boolean bold) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:" + color + "; -fx-font-size:" + size + ";"
                + (bold ? "-fx-font-weight:bold;" : ""));
        return l;
    }

    private static Button iconButton(String icon, String color) {
        Button btn = new Button(icon);
        btn.setStyle("-fx-background-color:transparent; -fx-text-fill:" + color
                + "; -fx-font-size:12; -fx-cursor:hand; -fx-padding:1 5;");
        return btn;
    }

    private static Spinner<Integer> makeIntSpinner(int min, int max, int init) {
        Spinner<Integer> s = new Spinner<>(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, init));
        s.setPrefWidth(110);
        s.setEditable(true);
        s.setStyle("-fx-background-color:#21262d; -fx-border-color:#30363d; -fx-background-radius:6;");
        s.getStyleClass().add("split-arrows-horizontal");
        return s;
    }

    private static Spinner<Double> makeDoubleSpinner(double min, double max, double init, double step) {
        Spinner<Double> s = new Spinner<>(
                new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max, init, step));
        s.setPrefWidth(110);
        s.setEditable(true);
        s.setStyle("-fx-background-color:#21262d; -fx-border-color:#30363d; -fx-background-radius:6;");
        return s;
    }

    private static <T> void styleCombo(ComboBox<T> combo) {
        combo.setStyle("""
                -fx-background-color:#21262d;
                -fx-border-color:#30363d;
                -fx-background-radius:6;
                -fx-font-size:12;
                """);
        combo.setPrefWidth(160);
    }

    private static ListCell<PriceSource> priceSourceCell() {
        return new ListCell<>() {
            @Override protected void updateItem(PriceSource item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label);
                setStyle("-fx-text-fill:#e6edf3;");
            }
        };
    }

    private static Separator separator() {
        Separator sep = new Separator();
        sep.setStyle("-fx-background-color:#30363d;");
        return sep;
    }

    private static javafx.stage.Window getAnyWindow(Node node) {
        if (node != null && node.getScene() != null) return node.getScene().getWindow();
        return null;
    }
}
