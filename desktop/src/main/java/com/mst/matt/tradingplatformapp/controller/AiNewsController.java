package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.client.AiApiClient;
import com.mst.matt.tradingplatformapp.client.AiApiClient.AiInsight;
import com.mst.matt.tradingplatformapp.client.AiApiClient.AiLlmModel;
import com.mst.matt.tradingplatformapp.client.AiApiClient.NewsItem;
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
 * <p>Phase 2, Step 12 — insights fetched via {@link AiApiClient}
 * ({@code /api/ai/**} on the Gateway → {@code ai-service}).</p>
 */
@Component
@FxmlView("/fxml/AiNewsView.fxml")
public class AiNewsController implements Initializable {

    private static final List<String> POPULAR_SYMBOLS = List.of(
            "BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT",
            "ADAUSDT", "DOGEUSDT", "AVAXUSDT", "DOTUSDT", "MATICUSDT");

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

    @Autowired private AiApiClient aiApiClient;

    private String currentSymbol = "";
    private Popup  autocompletePopup;
    private ListView<String> autocompleteList;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupModelSelector();
        setupAutocomplete();
        if (resultPanel != null) {
            resultPanel.setVisible(false);
            resultPanel.setManaged(false);
        }
        if (loadingSpinner != null) {
            loadingSpinner.setVisible(false);
            loadingSpinner.setManaged(false);
        }
    }

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

    @FXML
    public void onQuickSearch(ActionEvent event) {
        if (event.getSource() instanceof Button btn && btn.getUserData() instanceof String query) {
            if (searchField != null) searchField.setText(query);
            fetchAndDisplay(query.trim().toUpperCase());
        }
    }

    @FXML
    public void onRefresh() {
        onGetInsights();
    }

    private void fetchAndDisplay(String query) {
        setLoading(true);
        clearResults();
        String modelId = selectedModelId();
        Thread.ofVirtual().start(() -> {
            try {
                var insightOpt = aiApiClient.getNewsInsight(query, modelId);
                Platform.runLater(() -> {
                    if (insightOpt.isEmpty()) {
                        clearResults();
                        setStatusError("No insights returned for " + query + ".");
                    } else {
                        displayInsight(insightOpt.get());
                    }
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
        AiLlmModel model = modelSelector.getValue();
        if (model.getId() != null && !model.getId().isBlank()) return model.getId();
        return model.getModelId();
    }

    private void setupModelSelector() {
        if (modelSelector == null) return;

        Thread.ofVirtual().start(() -> {
            List<AiLlmModel> models = aiApiClient.listModels();
            Platform.runLater(() -> {
                modelSelector.setItems(FXCollections.observableArrayList(models));
                modelSelector.setCellFactory(lv -> modelCell());
                modelSelector.setButtonCell(modelCell());
                if (!models.isEmpty()) {
                    modelSelector.getSelectionModel().selectFirst();
                }
                modelSelector.valueProperty().addListener((obs, old, selected) -> {
                    if (selected == null || statusLabel == null) return;
                    if (loadingSpinner == null || !loadingSpinner.isVisible()) {
                        statusLabel.setText("");
                        statusLabel.setStyle("-fx-text-fill:#8b949e; -fx-font-size:11px;");
                    }
                });
            });
        });
    }

    private ListCell<AiLlmModel> modelCell() {
        return new ListCell<>() {
            @Override protected void updateItem(AiLlmModel model, boolean empty) {
                super.updateItem(model, empty);
                if (empty || model == null) {
                    setText(null);
                    setStyle(null);
                    return;
                }
                setText(modelLabel(model));
                setStyle("-fx-text-fill:#e6edf3; -fx-font-size:12px;");
            }
        };
    }

    private static String modelLabel(AiLlmModel model) {
        if (model.getProviderName() != null && model.getDisplayName() != null) {
            return model.getProviderName() + " · " + model.getDisplayName();
        }
        if (model.getDisplayName() != null) return model.getDisplayName();
        if (model.getModelId() != null) return model.getModelId();
        return model.getId() != null ? model.getId() : "—";
    }

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

        if (sentimentBadge != null) {
            String s = insight.getOverallSentiment() != null ? insight.getOverallSentiment() : "NEUTRAL";
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

        if (newsContainer != null) {
            newsContainer.getChildren().clear();
            if (insight.getNews() != null) {
                for (NewsItem item : insight.getNews()) {
                    newsContainer.getChildren().add(buildNewsCard(item));
                }
            }
        }

        if (recommendationText != null) {
            recommendationText.setText(insight.getRecommendation() != null ? insight.getRecommendation() : "");
        }

        if (riskText != null) {
            riskText.setText(insight.getRiskWarning() != null ? insight.getRiskWarning() : "");
        }

        if (generatedAtLabel != null && insight.getGeneratedAt() != null) {
            String modelInfo = insight.getModelLabel() != null && !insight.getModelLabel().isBlank()
                    ? " · " + insight.getModelLabel() : "";
            generatedAtLabel.setText("Generated: "
                    + insight.getGeneratedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    + modelInfo);
        }

        if (insight.getLlmNotice() != null && !insight.getLlmNotice().isBlank()) {
            setStatusError("AI unavailable — " + insight.getLlmNotice()
                    + ". Try another model from the picker.");
        } else if (statusLabel != null && !insight.isAiGenerated()) {
            statusLabel.setText("Using rule-based analysis (no AI key for selected model).");
            statusLabel.setStyle("-fx-text-fill:#d29922; -fx-font-size:11px;");
        } else if (statusLabel != null) {
            statusLabel.setText("");
            statusLabel.setStyle("-fx-text-fill:#8b949e; -fx-font-size:11px;");
        }

        if (resultPanel != null) {
            resultPanel.setVisible(true);
            resultPanel.setManaged(true);
        }
    }

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

        String sentiment = item.getSentiment() != null ? item.getSentiment() : "NEUTRAL";
        Label dot = new Label(sentimentIcon(sentiment));
        dot.setStyle("-fx-font-size:13px;");

        Label headline = new Label(item.getHeadline() != null ? item.getHeadline() : "—");
        headline.setStyle("-fx-text-fill:#e6edf3; -fx-font-size:12px;");
        headline.setWrapText(true);
        headline.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(headline, Priority.ALWAYS);

        VBox right = new VBox(2);
        right.setAlignment(Pos.CENTER_RIGHT);
        Label src = new Label(item.getSource() != null ? item.getSource() : "—");
        src.setStyle("-fx-text-fill:#8b949e; -fx-font-size:10px;");
        Label sentBadge = new Label(sentiment);
        String sentColor = switch (sentiment) {
            case "BULLISH" -> "#3fb950";
            case "BEARISH" -> "#f85149";
            default        -> "#8b949e";
        };
        sentBadge.setStyle("-fx-text-fill:" + sentColor + "; -fx-font-size:10px; -fx-font-weight:bold;");
        right.getChildren().addAll(src, sentBadge);

        card.getChildren().addAll(dot, headline, right);

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

        if (item.getUrl() != null && !item.getUrl().isBlank()) {
            card.setOnMouseClicked(e -> {
                try {
                    java.awt.Desktop.getDesktop().browse(new java.net.URI(item.getUrl()));
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

        List<String> suggestions = POPULAR_SYMBOLS;
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
