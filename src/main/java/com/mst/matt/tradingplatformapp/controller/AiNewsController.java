package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.service.AiNewsService;
import com.mst.matt.tradingplatformapp.service.AiNewsService.AiInsight;
import com.mst.matt.tradingplatformapp.service.AiNewsService.NewsItem;
import com.mst.matt.tradingplatformapp.service.ai.AiLlmModel;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Popup;
import net.rgielen.fxweaver.core.FxmlView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Controller for the AI News & Insights tab.
 *
 * <p>Features:
 * <ul>
 *   <li>Symbol/query search field with autocomplete</li>
 *   <li>AI-generated investment recommendations</li>
 *   <li>News aggregation with sentiment indicators</li>
 *   <li>15-minute cache TTL per symbol</li>
 * </ul>
 */
@Component
@FxmlView("/fxml/AiNewsView.fxml")
public class AiNewsController implements Initializable {

    // ── FXML injections ───────────────────────────────────────────────────────
    @FXML private TextField    searchField;
    @FXML private ComboBox<AiLlmModel> modelSelector;
    @FXML private Button       getInsightsBtn;
    @FXML private ProgressIndicator loadingSpinner;
    @FXML private Label        statusLabel;
    @FXML private VBox         newsContainer;
    @FXML private Label        sentimentLabel;
    @FXML private Label        sentimentBadge;
    @FXML private Label        recommendationLabel;
    @FXML private Label        recommendationText;
    @FXML private Label        riskLabel;
    @FXML private Label        riskText;
    @FXML private Label        generatedAtLabel;
    @FXML private ScrollPane   contentScrollPane;
    @FXML private VBox         resultPanel;

    // ── Spring beans ──────────────────────────────────────────────────────────
    @Autowired private AiNewsService aiNewsService;

    // ── State ─────────────────────────────────────────────────────────────────
    private String currentSymbol = "";
    private Popup  autocompletePopup;
    private ListView<String> autocompleteList;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupModelSelector();
        setupAutocomplete();
        // Hide result panel until first search
        if (resultPanel != null) {
            resultPanel.setVisible(false);
            resultPanel.setManaged(false);
        }
        if (loadingSpinner != null) {
            loadingSpinner.setVisible(false);
            loadingSpinner.setManaged(false);
        }
    }

    /**
     * Called by MainDashboardController when the current chart symbol changes.
     * Pre-fills the search field with the symbol.
     */
    public void setCurrentSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) return;
        this.currentSymbol = symbol.trim().toUpperCase();
        if (searchField != null) {
            searchField.setText(currentSymbol);
        }
    }

    @FXML
    public void onGetInsights() {
        String query = searchField != null ? searchField.getText() : currentSymbol;
        if (query == null || query.isBlank()) {
            query = currentSymbol.isBlank() ? "BTCUSDT" : currentSymbol;
        }
        final String finalQuery = query.trim().toUpperCase();
        fetchAndDisplay(finalQuery);
    }

    /** Handler for quick-search chip buttons. Uses the button's userData as the query. */
    @FXML
    public void onQuickSearch(ActionEvent event) {
        if (event.getSource() instanceof Button btn && btn.getUserData() instanceof String query) {
            if (searchField != null) searchField.setText(query);
            fetchAndDisplay(query.trim().toUpperCase());
        }
    }

    @FXML
    public void onRefresh() {
        String query = searchField != null ? searchField.getText() : currentSymbol;
        if (query != null && !query.isBlank()) {
            aiNewsService.invalidate(query.trim().toUpperCase());
        }
        onGetInsights();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void fetchAndDisplay(String query) {
        setLoading(true);
        clearResults();
        String modelId = selectedModelId();
        Thread.ofVirtual().start(() -> {
            try {
                AiInsight insight = aiNewsService.getInsight(query, modelId);
                Platform.runLater(() -> {
                    displayInsight(insight);
                    setLoading(false);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    clearResults();
                    setStatusError(e.getMessage() != null ? e.getMessage() : "Failed to fetch insights.");
                    setLoading(false);
                });
            }
        });
    }

    private String selectedModelId() {
        if (modelSelector == null || modelSelector.getValue() == null) return null;
        return modelSelector.getValue().id();
    }

    private void setupModelSelector() {
        if (modelSelector == null) return;

        List<AiLlmModel> models = aiNewsService.availableModels();
        modelSelector.setItems(FXCollections.observableArrayList(models));
        modelSelector.setCellFactory(lv -> modelCell());
        modelSelector.setButtonCell(modelCell());

        aiNewsService.defaultModel().ifPresentOrElse(
                modelSelector::setValue,
                () -> {
                    if (!models.isEmpty()) modelSelector.getSelectionModel().selectFirst();
                });

        modelSelector.valueProperty().addListener((obs, old, selected) -> {
            if (selected == null || statusLabel == null) return;
            if (!aiNewsService.configuredModels().contains(selected)) {
                statusLabel.setText("⚠ API key not set for " + selected.providerName()
                        + " — add app.api." + selected.apiKeyProperty() + ".key in application-local.properties");
                statusLabel.setStyle("-fx-text-fill:#d29922; -fx-font-size:11px;");
            } else if (loadingSpinner == null || !loadingSpinner.isVisible()) {
                statusLabel.setText("");
                statusLabel.setStyle("-fx-text-fill:#8b949e; -fx-font-size:11px;");
            }
        });
    }

    private ListCell<AiLlmModel> modelCell() {
        return new ListCell<>() {
            @Override protected void updateItem(AiLlmModel model, boolean empty) {
                super.updateItem(model, empty);
                if (empty || model == null) {
                    setText(null);
                    setStyle(null);
                    setDisable(false);
                    return;
                }
                setText(model.label());
                boolean configured = aiNewsService.configuredModels().contains(model);
                setDisable(false);
                setStyle(configured
                        ? "-fx-text-fill:#e6edf3; -fx-font-size:12px;"
                        : "-fx-text-fill:#6e7681; -fx-font-size:12px;");
            }
        };
    }

    /** Hides stale AI content when a fetch fails or a new fetch starts. */
    private void clearResults() {
        if (resultPanel != null) {
            resultPanel.setVisible(false);
            resultPanel.setManaged(false);
        }
        if (newsContainer != null) newsContainer.getChildren().clear();
        if (recommendationText != null) recommendationText.setText("");
        if (riskText != null) riskText.setText("");
        if (sentimentBadge != null) sentimentBadge.setText("");
        if (generatedAtLabel != null) generatedAtLabel.setText("");
    }

    private void displayInsight(AiInsight insight) {
        if (insight == null) return;

        // ── Sentiment badge ───────────────────────────────────────────────────
        if (sentimentBadge != null) {
            String s = insight.overallSentiment();
            String color = switch (s) {
                case "BULLISH" -> "#3fb950";
                case "BEARISH" -> "#f85149";
                default        -> "#8b949e";
            };
            String icon = switch (s) {
                case "BULLISH" -> "⬆ BULLISH";
                case "BEARISH" -> "⬇ BEARISH";
                default        -> "— NEUTRAL";
            };
            sentimentBadge.setText(icon);
            sentimentBadge.setStyle(
                    "-fx-text-fill:" + color + ";"
                    + "-fx-font-weight:bold; -fx-font-size:13px;"
                    + "-fx-background-color:" + color + "22;"
                    + "-fx-background-radius:12; -fx-padding:3 10;"
                    + "-fx-border-color:" + color + "55; -fx-border-radius:12; -fx-border-width:1;");
        }

        // ── News headlines ─────────────────────────────────────────────────────
        if (newsContainer != null) {
            newsContainer.getChildren().clear();
            for (NewsItem item : insight.news()) {
                newsContainer.getChildren().add(buildNewsCard(item));
            }
        }

        // ── Recommendation ────────────────────────────────────────────────────
        if (recommendationText != null) {
            recommendationText.setText(insight.recommendation());
        }

        // ── Risk warning ──────────────────────────────────────────────────────
        if (riskText != null) {
            riskText.setText(insight.riskWarning());
        }

        // ── Timestamp ─────────────────────────────────────────────────────────
        if (generatedAtLabel != null && insight.generatedAt() != null) {
            String modelInfo = insight.modelLabel() != null && !insight.modelLabel().isBlank()
                    ? " · " + insight.modelLabel() : "";
            generatedAtLabel.setText("Generated: "
                    + insight.generatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    + modelInfo);
        }

        if (insight.llmNotice() != null && !insight.llmNotice().isBlank()) {
            setStatusError("AI unavailable — " + insight.llmNotice()
                    + ". Try OpenAI · GPT-4o Mini or another model from the picker.");
        } else if (statusLabel != null && !insight.aiGenerated()) {
            statusLabel.setText("Using rule-based analysis (no AI key for selected model).");
            statusLabel.setStyle("-fx-text-fill:#d29922; -fx-font-size:11px;");
        } else if (statusLabel != null) {
            statusLabel.setText("");
            statusLabel.setStyle("-fx-text-fill:#8b949e; -fx-font-size:11px;");
        }

        // Show result panel
        if (resultPanel != null) {
            resultPanel.setVisible(true);
            resultPanel.setManaged(true);
        }
    }

    /** Builds a styled card for a single news item. */
    private HBox buildNewsCard(NewsItem item) {
        HBox card = new HBox(10);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(8, 12, 8, 12));
        card.setStyle(
                "-fx-background-color:#1c2128;"
                + "-fx-background-radius:6;"
                + "-fx-border-color:#30363d;"
                + "-fx-border-radius:6;"
                + "-fx-border-width:1;"
                + "-fx-cursor:hand;");

        // Sentiment dot
        Label dot = new Label(sentimentIcon(item.sentiment()));
        dot.setStyle("-fx-font-size:13px;");

        // Headline
        Label headline = new Label(item.headline());
        headline.setStyle("-fx-text-fill:#e6edf3; -fx-font-size:12px;");
        headline.setWrapText(true);
        headline.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(headline, Priority.ALWAYS);

        // Source + sentiment badge
        VBox right = new VBox(2);
        right.setAlignment(Pos.CENTER_RIGHT);
        Label src = new Label(item.source());
        src.setStyle("-fx-text-fill:#8b949e; -fx-font-size:10px;");
        Label sentBadge = new Label(item.sentiment());
        String sentColor = switch (item.sentiment()) {
            case "BULLISH" -> "#3fb950";
            case "BEARISH" -> "#f85149";
            default        -> "#8b949e";
        };
        sentBadge.setStyle("-fx-text-fill:" + sentColor + "; -fx-font-size:10px; -fx-font-weight:bold;");
        right.getChildren().addAll(src, sentBadge);

        card.getChildren().addAll(dot, headline, right);

        // Hover highlight
        card.setOnMouseEntered(e -> card.setStyle(
                "-fx-background-color:#21262d;"
                + "-fx-background-radius:6;"
                + "-fx-border-color:#388bfd;"
                + "-fx-border-radius:6;"
                + "-fx-border-width:1;"
                + "-fx-cursor:hand;"));
        card.setOnMouseExited(e -> card.setStyle(
                "-fx-background-color:#1c2128;"
                + "-fx-background-radius:6;"
                + "-fx-border-color:#30363d;"
                + "-fx-border-radius:6;"
                + "-fx-border-width:1;"
                + "-fx-cursor:hand;"));

        // Open URL on click (if available)
        if (item.url() != null && !item.url().isBlank()) {
            card.setOnMouseClicked(e -> {
                try {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI(item.url()));
                } catch (Exception ignored) {}
            });
        }

        return card;
    }

    private String sentimentIcon(String sentiment) {
        return switch (sentiment) {
            case "BULLISH" -> "⬆";
            case "BEARISH" -> "⬇";
            default        -> "•";
        };
    }

    private void setLoading(boolean loading) {
        if (loadingSpinner != null) {
            loadingSpinner.setVisible(loading);
            loadingSpinner.setManaged(loading);
        }
        if (getInsightsBtn != null) getInsightsBtn.setDisable(loading);
        if (statusLabel != null && loading) statusLabel.setText("Fetching insights…");
    }

    private void setStatusError(String msg) {
        if (statusLabel != null) {
            statusLabel.setText("⚠ " + msg);
            statusLabel.setStyle("-fx-text-fill:#f85149; -fx-font-size:11px;");
        }
    }

    // ── Autocomplete ──────────────────────────────────────────────────────────

    private void setupAutocomplete() {
        if (searchField == null) return;

        autocompleteList = new ListView<>();
        autocompleteList.setStyle(
                "-fx-background-color:#1c2128; -fx-border-color:#30363d;"
                + "-fx-border-width:1; -fx-background-radius:0 0 6 6;");
        autocompleteList.setPrefHeight(160);
        autocompleteList.setMaxHeight(200);
        autocompleteList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) { setText(null); setStyle(null); return; }
                setText(s);
                setStyle("-fx-text-fill:#e6edf3; -fx-background-color:transparent; -fx-font-size:12px;");
            }
        });

        List<String> suggestions = aiNewsService.popularSymbols();
        autocompleteList.setItems(FXCollections.observableArrayList(suggestions));

        autocompleteList.setOnMouseClicked(e -> {
            String sel = autocompleteList.getSelectionModel().getSelectedItem();
            if (sel != null) {
                searchField.setText(sel);
                hideAutocomplete();
                fetchAndDisplay(sel.trim().toUpperCase());
            }
        });

        autocompletePopup = new Popup();
        autocompletePopup.getContent().add(autocompleteList);
        autocompletePopup.setAutoHide(true);

        searchField.textProperty().addListener((obs, old, val) -> {
            if (val == null || val.isBlank()) { hideAutocomplete(); return; }
            String q = val.trim().toUpperCase();
            List<String> matches = suggestions.stream()
                    .filter(s -> s.toUpperCase().contains(q))
                    .limit(10)
                    .collect(Collectors.toList());
            if (matches.isEmpty() || (matches.size() == 1 && matches.get(0).equalsIgnoreCase(q))) {
                hideAutocomplete();
            } else {
                autocompleteList.setItems(FXCollections.observableArrayList(matches));
                autocompleteList.setPrefHeight(Math.min(200, matches.size() * 28 + 4));
                if (!autocompletePopup.isShowing() && searchField.getScene() != null) {
                    javafx.geometry.Bounds b = searchField.localToScreen(searchField.getBoundsInLocal());
                    if (b != null) {
                        autocompletePopup.show(searchField.getScene().getWindow(),
                                b.getMinX(), b.getMaxY());
                        autocompleteList.setPrefWidth(searchField.getWidth());
                    }
                }
            }
        });

        searchField.focusedProperty().addListener((obs, o, n) -> {
            if (!n) hideAutocomplete();
        });

        searchField.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ENTER  -> { hideAutocomplete(); onGetInsights(); }
                case ESCAPE -> hideAutocomplete();
                case DOWN   -> {
                    if (autocompletePopup.isShowing()) {
                        autocompleteList.requestFocus();
                        autocompleteList.getSelectionModel().selectFirst();
                        e.consume();
                    }
                }
                default -> {}
            }
        });
    }

    private void hideAutocomplete() {
        if (autocompletePopup != null && autocompletePopup.isShowing()) {
            autocompletePopup.hide();
        }
    }
}
