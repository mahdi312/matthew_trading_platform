package com.mst.matt.tradingplatformapp.ui;

import com.mst.matt.tradingplatformapp.model.SymbolEntry;
import com.mst.matt.tradingplatformapp.model.SymbolEntry.AssetType;
import com.mst.matt.tradingplatformapp.repository.SymbolEntryRepository;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.util.List;
import java.util.function.Consumer;

/**
 * A TextField with an autocomplete popup that queries {@link SymbolEntryRepository}.
 *
 * Usage:
 * <pre>
 *   AutocompleteSymbolField field = new AutocompleteSymbolField(symbolRepo, AssetType.CRYPTO);
 *   field.setOnSymbolSelected(sym -> { currentSymbol = sym; loadChart(); });
 *   // Add field to FXML layout programmatically, or wire up in controller
 * </pre>
 */
public class AutocompleteSymbolField extends TextField {

    private static final int MAX_SUGGESTIONS = 12;
    private static final int MIN_QUERY_LENGTH = 1;

    private final SymbolEntryRepository repo;
    private AssetType                   filterType;  // null = search all
    private final Popup                 popup;
    private final ListView<SymbolEntry> listView;

    private Consumer<String> onSymbolSelected;

    public AutocompleteSymbolField(SymbolEntryRepository repo, AssetType filterType) {
        this.repo       = repo;
        this.filterType = filterType;
        this.popup      = new Popup();
        this.listView   = new ListView<>();

        setupListView();
        setupPopup();
        setupTextListener();
        setupKeyHandler();
        setupFocusHandler();

        setPromptText("Search symbol…");
        setStyle("-fx-background-color:#21262d; -fx-text-fill:#e6edf3;"
                + "-fx-border-color:#30363d; -fx-border-radius:6;"
                + "-fx-background-radius:6; -fx-padding:6 10; -fx-font-size:13px;");
    }

    /** Convenience: no filter type (searches all asset types). */
    public AutocompleteSymbolField(SymbolEntryRepository repo) {
        this(repo, null);
    }

    // ── Public API ─────────────────────────────────────────────

    public void setFilterType(AssetType type) { this.filterType = type; }

    public void setOnSymbolSelected(Consumer<String> cb) { this.onSymbolSelected = cb; }

    /** Force-select a symbol value (bypasses suggestion callback). */
    public void setSymbol(String symbol) {
        setText(symbol);
        popup.hide();
    }

    // ── Setup ──────────────────────────────────────────────────

    private void setupListView() {
        listView.setPrefWidth(340);
        listView.setMaxHeight(220);
        listView.setStyle("-fx-background-color:#1c2128; -fx-border-color:#30363d;"
                + "-fx-border-width:1; -fx-border-radius:0 0 6 6;");

        listView.setCellFactory(lv -> new SymbolListCell());

        listView.setOnMouseClicked(e -> {
            SymbolEntry sel = listView.getSelectionModel().getSelectedItem();
            if (sel != null) acceptSuggestion(sel);
        });
    }

    private void setupPopup() {
        VBox container = new VBox(listView);
        container.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 8, 0, 0, 4);");
        popup.getContent().add(container);
        popup.setAutoHide(true);
        popup.setOnAutoHide(e -> popup.hide());
    }

    private void setupTextListener() {
        textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.length() < MIN_QUERY_LENGTH) {
                popup.hide();
                return;
            }
            // Query on background thread to avoid blocking FX thread
            String q = newVal.trim();
            Thread.ofVirtual().start(() -> {
                try {
                    List<SymbolEntry> results = filterType != null
                            ? repo.search(filterType, q)
                            : repo.searchAll(q);
                    if (results.size() > MAX_SUGGESTIONS)
                        results = results.subList(0, MAX_SUGGESTIONS);
                    final List<SymbolEntry> finalResults = results;
                    Platform.runLater(() -> showSuggestions(finalResults));
                } catch (Exception e) {
                    // Silently ignore DB errors during autocomplete
                }
            });
        });
    }

    private void setupKeyHandler() {
        setOnKeyPressed(e -> {
            if (!popup.isShowing()) return;
            switch (e.getCode()) {
                case DOWN -> {
                    listView.requestFocus();
                    listView.getSelectionModel().select(0);
                    e.consume();
                }
                case ESCAPE -> {
                    popup.hide();
                    e.consume();
                }
                case ENTER -> {
                    SymbolEntry sel = listView.getSelectionModel().getSelectedItem();
                    if (sel != null) { acceptSuggestion(sel); e.consume(); }
                }
                default -> {}
            }
        });

        listView.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                SymbolEntry sel = listView.getSelectionModel().getSelectedItem();
                if (sel != null) acceptSuggestion(sel);
                e.consume();
            } else if (e.getCode() == KeyCode.ESCAPE) {
                popup.hide();
                requestFocus();
                e.consume();
            }
        });
    }

    private void setupFocusHandler() {
        focusedProperty().addListener((obs, wasF, isF) -> {
            if (!isF) popup.hide();
        });
    }

    // ── Suggestion display ─────────────────────────────────────

    private void showSuggestions(List<SymbolEntry> results) {
        if (results.isEmpty()) {
            popup.hide();
            return;
        }
        listView.getItems().setAll(results);
        listView.setPrefWidth(Math.max(getWidth(), 260));

        if (!popup.isShowing()) {
            Bounds bounds = localToScreen(getBoundsInLocal());
            if (bounds != null) {
                popup.show(this,
                        bounds.getMinX(),
                        bounds.getMaxY() + 2);
            }
        }
    }

    private void acceptSuggestion(SymbolEntry entry) {
        popup.hide();
        setText(entry.getSymbol());
        positionCaret(entry.getSymbol().length());
        if (onSymbolSelected != null) onSymbolSelected.accept(entry.getSymbol());
    }

    // ── Custom list cell ───────────────────────────────────────

    private static class SymbolListCell extends javafx.scene.control.ListCell<SymbolEntry> {
        private final Label symbolLabel = new Label();
        private final Label nameLabel   = new Label();
        private final VBox  container   = new VBox(1, symbolLabel, nameLabel);

        SymbolListCell() {
            symbolLabel.setStyle("-fx-text-fill:#e6edf3; -fx-font-size:13px; -fx-font-weight:bold;");
            nameLabel.setStyle("-fx-text-fill:#8b949e; -fx-font-size:11px;");
            container.setStyle("-fx-padding:4 8;");
            setStyle("-fx-background-color:transparent;");
        }

        @Override
        protected void updateItem(SymbolEntry item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                symbolLabel.setText(item.getSymbol());
                nameLabel.setText(item.getName() != null ? item.getName() : "");
                setGraphic(container);
                // Highlight on hover
                setOnMouseEntered(e ->
                        setStyle("-fx-background-color:#21262d;"));
                setOnMouseExited(e ->
                        setStyle("-fx-background-color:transparent;"));
            }
        }
    }
}
