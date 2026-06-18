package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.model.*;
import com.mst.matt.tradingplatformapp.model.SymbolEntry.AssetType;
import com.mst.matt.tradingplatformapp.repository.SymbolEntryRepository;
import com.mst.matt.tradingplatformapp.repository.UserProfileRepository;
import com.mst.matt.tradingplatformapp.service.OhlcvStorageService;
import com.mst.matt.tradingplatformapp.service.ProfilePersistenceService;
import com.mst.matt.tradingplatformapp.service.analysis.AnalysisService;
import com.mst.matt.tradingplatformapp.service.analysis.AnalysisService.AnalysisResult;
import com.mst.matt.tradingplatformapp.service.analysis.IndicatorComputeService;
import com.mst.matt.tradingplatformapp.service.analysis.SignalScoringService.Recommendation;
import com.mst.matt.tradingplatformapp.service.analysis.SignalScoringService.SignalResult;
import com.mst.matt.tradingplatformapp.service.auth.AuthService;
import com.mst.matt.tradingplatformapp.service.price.AssetClassDetector;
import com.mst.matt.tradingplatformapp.service.price.AssetClassDetector.AssetClass;
import com.mst.matt.tradingplatformapp.service.price.MarketDataProvider;
import com.mst.matt.tradingplatformapp.service.price.PriceProviderRegistry;
import com.mst.matt.tradingplatformapp.service.price.PriceRouter;
import com.mst.matt.tradingplatformapp.ui.IndicatorPickerDialog;
import com.mst.matt.tradingplatformapp.ui.chart.CandlestickChartCanvas;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import net.rgielen.fxweaver.core.FxmlView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;

import java.net.URL;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Component
@FxmlView("/fxml/ChartView.fxml")
public class ChartController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(ChartController.class);

    /** All 15 canonical timeframes in display order. */
    private static final List<String> ALL_TIMEFRAMES = List.of(
            "1m","3m","5m","15m","30m","1h","2h","4h","6h","8h","12h","1d","3d","1w","1mo");

    public enum ViewMode { CHART, ANALYSIS }

    // ── FXML injections ───────────────────────────────────────
    @FXML private TextField            symbolInput;
    @FXML private ComboBox<String>     symbolCombo;
    @FXML private Pane                 chartPane;
    @FXML private ComboBox<Integer>    barsCombo;
    @FXML private ComboBox<MarketDataProvider> chartProviderCombo;
    @FXML private Label                dataSourceLabel;
    @FXML private StackPane            notificationOverlay;
    // Timeframe buttons — all possible timeframes
    @FXML private ToggleButton tf1m, tf3m, tf5m, tf15m, tf30m,
            tf1h, tf2h, tf4h, tf6h, tf8h, tf12h,
            tf1d, tf3d, tf1w, tf1mo;
    @FXML private HBox   analysisToolbar;
    @FXML private HBox   favTfBar;
    @FXML private Button tfDropdownBtn;
    @FXML private Button indicatorPickerBtn;
    @FXML private Button analyzeBtn;
    @FXML private HBox   signalBar;
    @FXML private HBox   sentimentBox;
    @FXML private Label  signalLabel, confidenceLabel, bestBuyLabel,
            bestSellLabel, bullBearLabel, currentPriceChartLabel;
    @FXML private Label  bullCircle, neutralCircle, bearCircle;

    // ── Spring beans ──────────────────────────────────────────
    @Autowired private AnalysisService          analysisService;
    @Autowired private IndicatorComputeService  indicatorComputeService;
    @Autowired private OhlcvStorageService      ohlcvStorageService;
    @Autowired private PriceRouter              priceRouter;
    @Autowired private PriceProviderRegistry    providerRegistry;
    @Autowired private UserProfileRepository    profileRepository;
    @Autowired private ProfilePersistenceService profilePersistence;
    @Autowired private AuthService              authService;
    @Autowired private SymbolEntryRepository    symbolEntryRepository;
    @Autowired private com.mst.matt.tradingplatformapp.service.AppSettingsService appSettingsService;
    @Autowired private com.mst.matt.tradingplatformapp.service.marketdata.MarketDataSyncScheduler marketDataSyncScheduler;
    @Autowired @org.springframework.context.annotation.Lazy
    private IndicatorMixerController indicatorMixerController;

    @Value("${app.chart.default-bars:200}")
    private int defaultBars;

    // ── State ─────────────────────────────────────────────────
    private CandlestickChartCanvas chart;
    private UserProfile   activeProfile;
    private String        currentSymbol    = "BTCUSDT";
    private String        currentTimeframe = "1h";
    private int           currentBars;
    private ViewMode      viewMode         = ViewMode.CHART;

    /** Guard flag — true while refreshProviderCombo() is programmatically setting the value */
    private boolean updatingProviderCombo = false;

    private final List<IndicatorDefinition> activeIndicators = new ArrayList<>();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Feature 4.3: cap default bars to role limit
        currentBars = Math.min(defaultBars, authService.maxCandles());

        chart = new CandlestickChartCanvas();
        chart.widthProperty().bind(chartPane.widthProperty());
        chart.heightProperty().bind(chartPane.heightProperty());
        // Apply user's configured timezone (falls back to system default)
        chart.setUserTimezone(appSettingsService.getUserTimezone());
        chart.setCurrentTimeframe(currentTimeframe);
        chartPane.getChildren().add(chart);

        if (signalBar != null)
            signalBar.managedProperty().bind(signalBar.visibleProperty());

        chartProviderCombo.setCellFactory(lv -> providerLabelCell());
        chartProviderCombo.setButtonCell(providerLabelCell());

        // Initial symbol list — overridden by setProfile()
        symbolCombo.setItems(FXCollections.observableArrayList(
                "BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT",
                "AAPL", "TSLA", "MSFT", "EURUSD", "GBPUSD"));
        symbolCombo.setValue(currentSymbol);
        symbolInput.setText(currentSymbol);
        symbolCombo.valueProperty().addListener((o, a, sym) -> {
            if (sym != null && !sym.isBlank()) {
                currentSymbol = sym.trim().toUpperCase();
                symbolInput.setText(currentSymbol);
            }
        });
        symbolInput.textProperty().addListener((o, a, text) -> {
            if (text != null && !text.isBlank())
                currentSymbol = text.trim().toUpperCase();
        });
        // Autocomplete for symbolInput
        setupSymbolAutocomplete();

        barsCombo.setItems(FXCollections.observableArrayList(50, 100, 200, 300, 500, 750, 1000));
        barsCombo.setValue(currentBars);
        // Fix: always show the selected integer, never fall back to toString "..."
        ListCell<Integer> barsButtonCell = new ListCell<>() {
            @Override protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : String.valueOf(item));
            }
        };
        barsCombo.setButtonCell(barsButtonCell);
        barsCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : String.valueOf(item));
            }
        });
        barsCombo.valueProperty().addListener((o, a, n) -> {
            if (n != null) {
                // Feature 4.3: enforce role cap
                currentBars = Math.min(n, authService.maxCandles());
                // Propagate to chart canvas so setData() uses the new value
                if (chart != null) chart.setPreferredVisibleBars(currentBars);
                loadChartWithTimeout();
            }
        });
        // Sync initial barsCombo value to chart canvas
        chart.setPreferredVisibleBars(currentBars);

        // Timeframe toggle group (FXML buttons kept for legacy, hidden in UI — favTfBar is shown instead)
        ToggleGroup tfGroup = new ToggleGroup();
        for (ToggleButton btn : allTimeframeButtons()) {
            if (btn != null) btn.setToggleGroup(tfGroup);
        }
        applyTimeframeAccess();
        // Select the default timeframe button (even if hidden) so toggle-group state is consistent
        if (tf1h != null) { tf1h.setSelected(true); }
    }

    // ── ViewMode ──────────────────────────────────────────────

    public void setViewMode(ViewMode mode) {
        this.viewMode = mode == null ? ViewMode.CHART : mode;
        applyViewMode();
    }

    private void applyViewMode() {
        boolean analysis = viewMode == ViewMode.ANALYSIS;
        if (analysisToolbar != null) {
            analysisToolbar.setVisible(analysis);
            analysisToolbar.setManaged(analysis);
        }
        if (analyzeBtn != null) {
            analyzeBtn.setVisible(analysis);
            analyzeBtn.setManaged(analysis);
        }
        if (signalBar != null && !analysis) signalBar.setVisible(false);
        chart.setAnalysisMode(analysis);
    }

    /** Apply updated timezone from settings to the chart canvas. */
    public void applyTimezone(ZoneId zone) {
        if (chart != null) chart.setUserTimezone(zone);
    }

    public void prepareView() {
        // Feature 1: null-guard + always re-apply role access on every navigation
        applyTimeframeAccess();
        applyViewMode();
        if (symbolInput.getText() == null || symbolInput.getText().isBlank())
            symbolInput.setText(currentSymbol);
        else
            currentSymbol = symbolInput.getText().trim().toUpperCase();
        if (symbolCombo.getValue() == null) symbolCombo.setValue(currentSymbol);
        refreshProviderCombo();
        // Feature 1: only load if we actually have a profile set
        if (activeProfile != null) loadChart();
    }

    public void setProfile(UserProfile profile) {
        this.activeProfile = profile;
        analysisService.setActiveProfile(profile);
        priceRouter.setActiveProfile(profile);
        if (profile != null && profile.getDefaultSymbol() != null
                && !profile.getDefaultSymbol().isBlank()) {
            currentSymbol = profile.getDefaultSymbol();
            if (symbolInput != null) symbolInput.setText(currentSymbol);
            if (symbolCombo != null) symbolCombo.setValue(currentSymbol);
        }
        // Feature 5: reload symbol list for this profile's asset focus
        refreshSymbolList(profile);
        refreshProviderCombo();
    }

    // ── Symbol autocomplete ───────────────────────────────────

    /** Javafx Popup for the autocomplete suggestion list. */
    private javafx.stage.Popup autocompletePopup;
    private ListView<String> autocompleteList;

    /**
     * Wires an autocomplete suggestion dropdown to {@link #symbolInput}.
     * Suggestions are filtered from the current {@link #symbolCombo} items as the user types.
     */
    private void setupSymbolAutocomplete() {
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
                setStyle("-fx-text-fill:#e6edf3; -fx-background-color:transparent; -fx-font-size:13px;");
            }
        });
        autocompleteList.setOnMouseClicked(e -> {
            String selected = autocompleteList.getSelectionModel().getSelectedItem();
            if (selected != null) {
                symbolInput.setText(selected);
                currentSymbol = selected;
                hideAutocomplete();
                refreshProviderCombo();
                loadChart();
            }
        });

        autocompletePopup = new javafx.stage.Popup();
        autocompletePopup.getContent().add(autocompleteList);
        autocompletePopup.setAutoHide(true);

        symbolInput.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isBlank()) { hideAutocomplete(); return; }
            String query = newVal.trim().toUpperCase();
            List<String> all = new ArrayList<>(symbolCombo.getItems());
            List<String> matches = all.stream()
                    .filter(s -> s.toUpperCase().contains(query))
                    .sorted((a, b) -> {
                        // Prioritize prefix matches
                        boolean aStart = a.startsWith(query);
                        boolean bStart = b.startsWith(query);
                        if (aStart && !bStart) return -1;
                        if (!aStart && bStart) return 1;
                        return a.compareTo(b);
                    })
                    .limit(12)
                    .collect(Collectors.toList());
            if (matches.isEmpty() || (matches.size() == 1 && matches.get(0).equalsIgnoreCase(query))) {
                hideAutocomplete();
            } else {
                autocompleteList.setItems(FXCollections.observableArrayList(matches));
                autocompleteList.setPrefHeight(Math.min(200, matches.size() * 28 + 4));
                if (!autocompletePopup.isShowing() && symbolInput.getScene() != null) {
                    javafx.geometry.Bounds bounds = symbolInput.localToScreen(symbolInput.getBoundsInLocal());
                    if (bounds != null) {
                        autocompletePopup.show(symbolInput.getScene().getWindow(),
                                bounds.getMinX(), bounds.getMaxY());
                        autocompleteList.setPrefWidth(symbolInput.getWidth());
                    }
                }
            }
        });

        symbolInput.focusedProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal) hideAutocomplete();
        });

        // Arrow key navigation in autocomplete
        symbolInput.setOnKeyPressed(e -> {
            if (!autocompletePopup.isShowing()) return;
            switch (e.getCode()) {
                case DOWN -> {
                    autocompleteList.requestFocus();
                    autocompleteList.getSelectionModel().selectFirst();
                    e.consume();
                }
                case ESCAPE -> { hideAutocomplete(); e.consume(); }
                case ENTER  -> {
                    String selected = autocompleteList.getSelectionModel().getSelectedItem();
                    if (selected != null) {
                        symbolInput.setText(selected);
                        currentSymbol = selected;
                        hideAutocomplete();
                    }
                }
                default -> {}
            }
        });
        autocompleteList.setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case ENTER -> {
                    String selected = autocompleteList.getSelectionModel().getSelectedItem();
                    if (selected != null) {
                        symbolInput.setText(selected);
                        currentSymbol = selected;
                        hideAutocomplete();
                        refreshProviderCombo();
                        loadChart();
                    }
                    e.consume();
                }
                case ESCAPE -> { hideAutocomplete(); symbolInput.requestFocus(); e.consume(); }
                default -> {}
            }
        });
    }

    private void hideAutocomplete() {
        if (autocompletePopup != null && autocompletePopup.isShowing()) {
            autocompletePopup.hide();
        }
    }

    /**
     * Feature 5: populate symbolCombo from SymbolEntry table filtered by profile assetFocus.
     * Falls back to hardcoded defaults if no entries in DB yet.
     */
    private void refreshSymbolList(UserProfile profile) {
        if (profile == null || symbolCombo == null) return;
        Thread.ofVirtual().start(() -> {
            try {
                AssetType assetType = mapFocusToType(profile.getAssetFocus());
                List<String> symbols;
                if (assetType != null) {
                    symbols = symbolEntryRepository.findByAssetTypeOrderBySymbolAsc(assetType)
                            .stream().map(SymbolEntry::getSymbol).collect(Collectors.toList());
                } else {
                    // MULTI focus — show all
                    symbols = symbolEntryRepository.findAll()
                            .stream().map(SymbolEntry::getSymbol).collect(Collectors.toList());
                }
                if (symbols.isEmpty()) {
                    symbols = defaultSymbolsFor(profile.getAssetFocus());
                }
                final List<String> finalSymbols = symbols;
                Platform.runLater(() -> {
                    String current = symbolCombo.getValue();
                    symbolCombo.setItems(FXCollections.observableArrayList(finalSymbols));
                    if (current != null && finalSymbols.contains(current)) {
                        symbolCombo.setValue(current);
                    } else if (!finalSymbols.isEmpty()) {
                        symbolCombo.setValue(finalSymbols.get(0));
                    }
                });
            } catch (Exception e) {
                log.warn("Symbol list refresh failed: {}", e.getMessage());
            }
        });
    }

    private AssetType mapFocusToType(UserProfile.ProfileAssetFocus focus) {
        if (focus == null) return null;
        return switch (focus) {
            case CRYPTO -> AssetType.CRYPTO;
            case STOCK  -> AssetType.STOCK;
            case FOREX  -> AssetType.FOREX;
            default     -> null; // MULTI
        };
    }

    private List<String> defaultSymbolsFor(UserProfile.ProfileAssetFocus focus) {
        if (focus == null) return List.of("BTCUSDT");
        return switch (focus) {
            case CRYPTO -> List.of("BTCUSDT","ETHUSDT","SOLUSDT","BNBUSDT","XRPUSDT","ADAUSDT");
            case STOCK  -> List.of("AAPL","TSLA","MSFT","NVDA","AMZN","GOOGL");
            case FOREX  -> List.of("EURUSD","GBPUSD","USDJPY","AUDUSD","USDCHF","NZDUSD");
            default     -> List.of("BTCUSDT","AAPL","EURUSD");
        };
    }

    private void refreshProviderCombo() {
        if (activeProfile == null) return;
        var focus = activeProfile.getAssetFocus() != null
                ? activeProfile.getAssetFocus()
                : UserProfile.ProfileAssetFocus.MULTI;
        AssetClass asset = AssetClassDetector.fromProfileFocus(focus, currentSymbol);
        List<MarketDataProvider> options = new ArrayList<>();
        options.add(MarketDataProvider.AUTO);
        options.addAll(providerRegistry.enabledProvidersFor(asset));
        // Guard: prevent the programmatic setValue from triggering onChartProviderChanged
        updatingProviderCombo = true;
        try {
            chartProviderCombo.setItems(FXCollections.observableArrayList(options));
            MarketDataProvider saved = MarketDataProvider.fromString(activeProfile.getChartProvider());
            // Only set the saved provider if it exists in the current options list; else AUTO
            chartProviderCombo.setValue(options.contains(saved) ? saved : MarketDataProvider.AUTO);
        } finally {
            updatingProviderCombo = false;
        }
    }

    // ── Feature 4.1 / 6: Timeframe access & Favorites ───────

    /**
     * Rebuilds the favorites bar (favTfBar) from the user's persisted favorites.
     * Also keeps all FXML toggle buttons hidden — they exist only for toggle-group tracking.
     */
    private void applyTimeframeAccess() {
        List<String> allowed   = authService.allowedTimeframes();
        List<String> favorites = authService.getFavoriteTimeframes();

        // Keep FXML buttons hidden — we drive TF selection from favTfBar
        for (ToggleButton btn : allTimeframeButtons()) {
            if (btn == null) continue;
            btn.setVisible(false);
            btn.setManaged(false);
        }

        // Rebuild favorites bar
        rebuildFavoritesBar(allowed, favorites);

        // Cap barsCombo to role limit
        int maxC = authService.maxCandles();
        if (barsCombo != null) {
            List<Integer> caps = List.of(50, 100, 200, 300, 500, 750, 1000).stream()
                    .filter(v -> v <= maxC).collect(Collectors.toList());
            barsCombo.setButtonCell(new ListCell<>() {
                @Override protected void updateItem(Integer item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "" : String.valueOf(item));
                }
            });
            barsCombo.setCellFactory(lv -> new ListCell<>() {
                @Override protected void updateItem(Integer item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "" : String.valueOf(item));
                }
            });
            barsCombo.setItems(FXCollections.observableArrayList(caps));
            if (currentBars > maxC) {
                currentBars = maxC;
                barsCombo.setValue(maxC);
            } else {
                barsCombo.setValue(currentBars);
            }
            // Keep chart canvas in sync
            if (chart != null) chart.setPreferredVisibleBars(currentBars);
        }
    }

    /**
     * Rebuilds the favorites bar with ToggleButtons for each favorited timeframe.
     * Shows nothing if no favorites are selected yet.
     */
    private void rebuildFavoritesBar(List<String> allowed, List<String> favorites) {
        if (favTfBar == null) return;
        favTfBar.getChildren().clear();

        if (favorites.isEmpty()) {
            // No favorites: show a hint label
            Label hint = new Label("★ No favorites — click ☆ TF to add");
            hint.setStyle("-fx-text-fill:#484f58; -fx-font-size:11px; -fx-padding:2 4;");
            favTfBar.getChildren().add(hint);
            return;
        }

        ToggleGroup tg = new ToggleGroup();
        for (String tf : favorites) {
            if (!allowed.contains(tf)) continue;
            ToggleButton btn = new ToggleButton(tf.toUpperCase());
            btn.setToggleGroup(tg);
            btn.setUserData(tf);
            boolean isActive = tf.equalsIgnoreCase(currentTimeframe);
            btn.setSelected(isActive);
            btn.setStyle(isActive ? activeStyle() : favoriteStyle());
            Tooltip tip = new Tooltip("★ " + tf.toUpperCase()
                    + " — Right-click to remove from favorites");
            tip.setShowDelay(Duration.millis(200));
            btn.setTooltip(tip);

            btn.setOnAction(e -> {
                if (!btn.isSelected()) { btn.setSelected(true); return; }
                String selectedTf = (String) btn.getUserData();
                if (!authService.canUseTimeframe(selectedTf)) {
                    btn.setSelected(false);
                    showWarnNotification("Timeframe '" + selectedTf + "' is not available for your plan.");
                    return;
                }
                currentTimeframe = selectedTf;
                chart.setCurrentTimeframe(selectedTf);
                // Update styles in favTfBar
                refreshFavBarStyles();
                loadChart();
            });

            // Right-click to remove from favorites
            ContextMenu cm = new ContextMenu();
            MenuItem removeItem = new MenuItem("✕ Remove from Favorites");
            removeItem.setOnAction(ev -> {
                List<String> cur = new ArrayList<>(authService.getFavoriteTimeframes());
                cur.remove(tf);
                authService.saveFavoriteTimeframes(cur);
                applyTimeframeAccess();
                showInfoNotification("Removed " + tf.toUpperCase() + " from favorites.");
            });
            cm.getItems().add(removeItem);
            btn.setContextMenu(cm);

            favTfBar.getChildren().add(btn);
        }
    }

    /** Re-styles all buttons in the favorites bar based on currentTimeframe. */
    private void refreshFavBarStyles() {
        if (favTfBar == null) return;
        for (javafx.scene.Node node : favTfBar.getChildren()) {
            if (node instanceof ToggleButton btn) {
                String tf = (String) btn.getUserData();
                if (tf == null) continue;
                btn.setStyle(tf.equalsIgnoreCase(currentTimeframe) ? activeStyle() : favoriteStyle());
            }
        }
    }

    /**
     * Opens a popup with all available timeframes so the user can star/unstar them.
     */
    @FXML public void onOpenTfDropdown() {
        if (tfDropdownBtn == null) return;
        // Toggle
        if (tfDropdownPopup != null && tfDropdownPopup.isShowing()) {
            tfDropdownPopup.hide();
            tfDropdownPopup = null;
            return;
        }
        List<String> allowed   = authService.allowedTimeframes();
        List<String> favorites = new ArrayList<>(authService.getFavoriteTimeframes());

        VBox box = new VBox(4);
        box.setPadding(new Insets(12));
        box.setPrefWidth(200);
        box.setStyle("-fx-background-color:#161b22; -fx-border-color:#30363d; -fx-border-width:1;"
                + "-fx-background-radius:8; -fx-border-radius:8;"
                + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.5),10,0,0,3);");

        Label title = new Label("Timeframe Favorites");
        title.setStyle("-fx-text-fill:#8b949e; -fx-font-size:11px; -fx-font-weight:bold; -fx-padding:0 0 6 0;");
        box.getChildren().add(title);

        for (String tf : allowed) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            boolean isFav = favorites.contains(tf);

            Button starBtn = new Button(isFav ? "★" : "☆");
            starBtn.setStyle("-fx-background-color:transparent; -fx-cursor:hand; -fx-font-size:14px;"
                    + "-fx-text-fill:" + (isFav ? "#e3b341" : "#484f58") + ";"
                    + "-fx-padding:0 4;");

            Label tfLabel = new Label(tf.toUpperCase());
            tfLabel.setStyle("-fx-text-fill:" + (isFav ? "#e6edf3" : "#8b949e") + "; -fx-font-size:13px;");
            tfLabel.setPrefWidth(80);

            // Click to toggle
            Runnable toggle = () -> {
                List<String> cur = new ArrayList<>(authService.getFavoriteTimeframes());
                if (cur.contains(tf)) {
                    cur.remove(tf);
                    starBtn.setText("☆");
                    starBtn.setStyle("-fx-background-color:transparent; -fx-cursor:hand; -fx-font-size:14px;"
                            + "-fx-text-fill:#484f58; -fx-padding:0 4;");
                    tfLabel.setStyle("-fx-text-fill:#8b949e; -fx-font-size:13px;");
                    showInfoNotification("Removed " + tf.toUpperCase() + " from favorites.");
                } else {
                    cur.add(tf);
                    starBtn.setText("★");
                    starBtn.setStyle("-fx-background-color:transparent; -fx-cursor:hand; -fx-font-size:14px;"
                            + "-fx-text-fill:#e3b341; -fx-padding:0 4;");
                    tfLabel.setStyle("-fx-text-fill:#e6edf3; -fx-font-size:13px;");
                    showInfoNotification("Added " + tf.toUpperCase() + " to favorites.");
                }
                authService.saveFavoriteTimeframes(cur);
                applyTimeframeAccess();
            };
            starBtn.setOnAction(e -> toggle.run());
            tfLabel.setOnMouseClicked(e -> toggle.run());
            row.getChildren().addAll(starBtn, tfLabel);
            box.getChildren().add(row);
        }

        tfDropdownPopup = new javafx.stage.Popup();
        tfDropdownPopup.getContent().add(box);
        tfDropdownPopup.setAutoHide(true);
        javafx.geometry.Bounds bounds = tfDropdownBtn.localToScreen(tfDropdownBtn.getBoundsInLocal());
        if (bounds != null) {
            tfDropdownPopup.show(tfDropdownBtn.getScene().getWindow(),
                    bounds.getMinX(), bounds.getMaxY() + 4);
        }
    }

    private javafx.stage.Popup tfDropdownPopup;

    /**
     * Toggle a timeframe in/out of favorites.
     */
    private void toggleFavorite(String tf, ToggleButton btn) {
        List<String> current = new ArrayList<>(authService.getFavoriteTimeframes());
        if (current.contains(tf)) {
            current.remove(tf);
            showInfoNotification("Removed " + tf.toUpperCase() + " from favorites.");
        } else {
            current.add(tf);
            showInfoNotification("Added " + tf.toUpperCase() + " to favorites.");
        }
        authService.saveFavoriteTimeframes(current);
        applyTimeframeAccess();
    }

    // ── Chart loading ─────────────────────────────────────────

    @FXML public void onChartProviderChanged() {
        // Ignore programmatic updates (refreshProviderCombo setting value)
        if (updatingProviderCombo) return;
        if (activeProfile == null) return;
        MarketDataProvider chosen = chartProviderCombo.getValue();
        if (chosen == null) return;
        // Persist the user's explicit provider choice
        activeProfile.setChartProvider(chosen.name());
        profilePersistence.saveAsync(activeProfile);
        showInfoNotification("Provider changed to " + chosen.getLabel() + " — refreshing chart…");
        Thread.ofVirtual().start(() -> {
            ohlcvStorageService.refreshBars(currentSymbol, currentTimeframe, currentBars, activeProfile);
            loadChart();
        });
    }

    @FXML public void onLoadChart() {
        String sym = symbolInput.getText().trim().toUpperCase();
        if (!sym.isEmpty()) currentSymbol = sym;
        // Notify the sync scheduler so it only keeps this symbol's data fresh
        marketDataSyncScheduler.notifyActiveSymbolChanged(currentSymbol);
        refreshProviderCombo();
        loadChart();
    }

    /**
     * Load chart with a 10-second timeout. If the chart hasn't updated within that
     * window, show a non-blocking error notification.
     */
    private void loadChartWithTimeout() {
        AtomicBoolean completed = new AtomicBoolean(false);
        // Start the load
        loadChart(completed);
        // Schedule a 10-second timeout check on FX thread
        PauseTransition timeout = new PauseTransition(Duration.seconds(10));
        timeout.setOnFinished(e -> {
            if (!completed.get()) {
                showErrorNotification("Failed to load requested candle count. Please try again.");
            }
        });
        timeout.play();
    }

    private void loadChart() {
        loadChart(null);
    }

    private void loadChart(AtomicBoolean completionFlag) {
        // Feature 1: always guard against null profile
        if (activeProfile == null) {
            log.debug("loadChart() skipped — no active profile");
            return;
        }
        // Feature 4.3: enforce candle cap
        currentBars = Math.min(currentBars, authService.maxCandles());

        Thread.ofVirtual().start(() -> {
            try {
                AnalysisResult result = loadAnalysisWithRetry();
                var quoteOpt = priceRouter.getQuote(currentSymbol, activeProfile);
                String provider = PriceRouter.getLastProviderName();

                if (!activeIndicators.isEmpty() && !result.getBars().isEmpty()) {
                    BarSeries series = analysisService.buildBarSeries(
                            result.getBars(), currentSymbol);
                    indicatorComputeService.computeAll(activeIndicators, series);
                }

                if (completionFlag != null) completionFlag.set(true);

                Platform.runLater(() -> {
                    double lastPrice = result.getBars().isEmpty() ? Double.NaN
                            : result.getBars().get(result.getBars().size() - 1)
                            .getClose().doubleValue();
                    quoteOpt.filter(q -> q.getPrice() != null)
                            .ifPresent(q -> chart.setLastPrice(q.getPrice().doubleValue()));
                    if (Double.isNaN(chart.getLastPrice()) && !Double.isNaN(lastPrice))
                        chart.setLastPrice(lastPrice);

                    chart.setData(result.getBars(), activeIndicators, result.getSrResult());

                    if (viewMode == ViewMode.ANALYSIS && result.getSignal() != null)
                        updateSignalBar(result.getSignal());

                    if (result.getIndicators() != null) {
                        double px = quoteOpt
                                .map(q -> q.getPrice())
                                .filter(p -> p != null)
                                .map(java.math.BigDecimal::doubleValue)
                                .orElse(lastPrice);
                        indicatorMixerController.setIndicatorData(
                                result.getIndicators(), result.getSrResult(), px);
                    }

                    quoteOpt.filter(q -> q.getPrice() != null).ifPresent(q -> {
                        if (viewMode == ViewMode.ANALYSIS && currentPriceChartLabel != null) {
                            currentPriceChartLabel.setText(
                                    "$" + q.getPrice().toPlainString()
                                            + (q.isUp() ? " ▲" : " ▼"));
                            currentPriceChartLabel.setStyle(
                                    "-fx-font-size:18px;-fx-font-weight:bold;-fx-text-fill:"
                                            + (q.isUp() ? "#3fb950" : "#f85149") + ";");
                        }
                    });
                    dataSourceLabel.setText("OHLCV via " + provider);
                    dataSourceLabel.setTooltip(new Tooltip(
                            "Last successful price provider for " + currentSymbol));
                });
            } catch (Exception e) {
                if (completionFlag != null) completionFlag.set(true);
                log.warn("Chart load failed for {} {}: {}", currentSymbol, currentTimeframe,
                        e.getMessage());
                Platform.runLater(() -> {
                    dataSourceLabel.setText("⚠ Data unavailable — retrying…");
                    showErrorNotification("Chart data unavailable for " + currentSymbol
                            + " [" + currentTimeframe + "]. " + e.getMessage());
                });
            }
        });
    }

    /**
     * Shows a non-blocking error notification that auto-hides after 3 seconds.
     * The notification also has an ✕ button for manual dismissal.
     */
    private void showErrorNotification(String message) {
        showNotification(message, "#4a1a1a", "#f85149", "⚠");
    }

    /** Shows a non-blocking success/info notification (green, 3-second auto-hide). */
    private void showInfoNotification(String message) {
        showNotification(message, "#1a3a1a", "#3fb950", "✔");
    }

    /** Shows a non-blocking warning notification (amber, 3-second auto-hide). */
    private void showWarnNotification(String message) {
        showNotification(message, "#3a2a0a", "#e3b341", "⚠");
    }

    /**
     * Core notification builder — non-blocking, closable, auto-hides after 3 seconds.
     * Safe to call from any thread (marshals to FX thread automatically).
     */
    private void showNotification(String message, String bgColor, String accentColor, String iconText) {
        if (notificationOverlay == null) {
            log.warn("Notification [{}]: {}", accentColor, message);
            return;
        }
        // Marshal to FX thread in case called from background thread
        Platform.runLater(() -> {
            // ── Compact pill toast ────────────────────────────────────────────────────
            HBox notification = new HBox(7);
            notification.setAlignment(Pos.CENTER_LEFT);
            notification.setPadding(new Insets(6, 10, 6, 10));
            notification.setMaxWidth(320);
            notification.setStyle(
                    "-fx-background-color:" + bgColor + ";" +
                    "-fx-border-color:" + accentColor + ";" +
                    "-fx-border-width:1;" +
                    "-fx-border-radius:20;" +
                    "-fx-background-radius:20;" +
                    "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.45),6,0,0,2);");

            Label icon = new Label(iconText);
            icon.setStyle("-fx-text-fill:" + accentColor + "; -fx-font-size:11px;");

            Label msgLabel = new Label(message);
            msgLabel.setStyle("-fx-text-fill:#c9d1d9; -fx-font-size:11px;");
            msgLabel.setMaxWidth(220);
            msgLabel.setWrapText(false);
            HBox.setHgrow(msgLabel, Priority.ALWAYS);

            Button closeBtn = new Button("✕");
            closeBtn.setStyle("-fx-background-color:transparent; -fx-text-fill:#6e7681;"
                    + "-fx-cursor:hand; -fx-padding:0 2; -fx-font-size:9px;");
            closeBtn.setOnAction(e -> dismissNotification(notification));
            closeBtn.setOnMouseEntered(e ->
                    closeBtn.setStyle("-fx-background-color:transparent; -fx-text-fill:#c9d1d9;"
                            + "-fx-cursor:hand; -fx-padding:0 2; -fx-font-size:9px;"));
            closeBtn.setOnMouseExited(e ->
                    closeBtn.setStyle("-fx-background-color:transparent; -fx-text-fill:#6e7681;"
                            + "-fx-cursor:hand; -fx-padding:0 2; -fx-font-size:9px;"));

            notification.getChildren().addAll(icon, msgLabel, closeBtn);
            notificationOverlay.getChildren().add(notification);
            notificationOverlay.setVisible(true);
            notificationOverlay.setManaged(true);
            // Position: bottom-right corner
            StackPane.setAlignment(notification, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(notification, new Insets(0, 12, 12, 0));

            // Auto-hide after 3 seconds
            PauseTransition autoHide = new PauseTransition(Duration.seconds(3));
            autoHide.setOnFinished(e -> dismissNotification(notification));
            autoHide.play();
        });
    }

    private void dismissNotification(HBox notification) {
        if (notificationOverlay == null) return;
        notificationOverlay.getChildren().remove(notification);
        if (notificationOverlay.getChildren().isEmpty()) {
            notificationOverlay.setVisible(false);
            notificationOverlay.setManaged(false);
        }
    }

    private AnalysisResult loadAnalysisWithRetry() throws Exception {
        try {
            return analysisService.analyze(currentSymbol, currentTimeframe, currentBars, activeProfile);
        } catch (DataIntegrityViolationException dup) {
            log.debug("Registry race on {} {} — retrying", currentSymbol, currentTimeframe);
            Thread.sleep(150);
            return analysisService.analyze(currentSymbol, currentTimeframe, currentBars, activeProfile);
        }
    }

    @FXML public void onRefresh() {
        if (activeProfile == null) return;
        Thread.ofVirtual().start(() -> {
            ohlcvStorageService.refreshBars(currentSymbol, currentTimeframe, currentBars, activeProfile);
            loadChart();
        });
    }

    @FXML public void onAnalyze() { loadChart(); }

    // ── Timeframe ─────────────────────────────────────────────

    @FXML public void onTimeframe(javafx.event.ActionEvent e) {
        ToggleButton src = (ToggleButton) e.getSource();
        if (!src.isSelected()) { src.setSelected(true); return; }
        String tf = timeframeFor(src);
        if (!authService.canUseTimeframe(tf)) {
            src.setSelected(false);
            showWarnNotification("Timeframe '" + tf + "' is not available for your plan.");
            return;
        }
        currentTimeframe = tf;
        chart.setCurrentTimeframe(tf);
        styleTimeframeButtons(src);
        refreshFavBarStyles();
        loadChart();
    }

    private String timeframeFor(ToggleButton btn) {
        return switch (btn.getText()) {
            case "1D"  -> "1d";
            case "3D"  -> "3d";
            case "1W"  -> "1w";
            case "1Mo" -> "1mo";
            default    -> btn.getText().toLowerCase();
        };
    }

    private void styleTimeframeButtons(ToggleButton active) {
        List<String> favorites = authService.getFavoriteTimeframes();
        for (ToggleButton btn : allTimeframeButtons()) {
            if (btn == null) continue;
            if (btn == active) {
                btn.setStyle(activeStyle());
            } else {
                String tf = timeframeFor(btn);
                btn.setStyle(favorites.contains(tf) ? favoriteStyle() : inactiveStyle());
            }
        }
        // Also refresh favorites bar styles
        refreshFavBarStyles();
    }

    private ToggleButton[] allTimeframeButtons() {
        return new ToggleButton[]{tf1m, tf3m, tf5m, tf15m, tf30m,
                tf1h, tf2h, tf4h, tf6h, tf8h, tf12h,
                tf1d, tf3d, tf1w, tf1mo};
    }

    // ── Indicator Picker ──────────────────────────────────────

    @FXML public void onOpenIndicatorPicker() {
        IndicatorPickerDialog.show(indicatorPickerBtn, activeIndicators, updatedList -> {
            Thread.ofVirtual().start(() -> {
                try {
                    AnalysisResult cached = analysisService.getCached(currentSymbol, currentTimeframe)
                            .orElse(null);
                    if (cached != null && !cached.getBars().isEmpty()) {
                        BarSeries series = analysisService.buildBarSeries(
                                cached.getBars(), currentSymbol);
                        indicatorComputeService.computeAll(activeIndicators, series);
                    }
                } catch (Exception ex) {
                    log.warn("Indicator recompute failed: {}", ex.getMessage());
                }
                Platform.runLater(() -> chart.setIndicators(activeIndicators));
            });
        });
    }

    // ── Signal bar ────────────────────────────────────────────

    private void updateSignalBar(SignalResult signal) {
        if (signalBar == null) return;
        signalBar.setVisible(true);
        Recommendation rec = signal.getRecommendation();
        signalLabel.setText(rec.label);
        signalLabel.setStyle("-fx-font-weight:bold;-fx-font-size:14px;-fx-text-fill:" + rec.textColor + ";");
        confidenceLabel.setText(String.format("Confidence: %.1f%%", signal.getConfidence()));
        bestBuyLabel.setText("$" + fmtPrice(signal.getBestBuyPrice()));
        bestSellLabel.setText("$" + fmtPrice(signal.getBestSellPrice()));

        // ── Color-coded sentiment circles ──────────────────────
        int bull = signal.getBullishCount();
        int neu  = signal.getNeutralCount();
        int bear = signal.getBearishCount();

        // Legacy label (hidden, keep for reference)
        if (bullBearLabel != null)
            bullBearLabel.setText("🟢 " + bull + "  ⚪ " + neu + "  🔴 " + bear);

        // Colored circles with dynamic styling
        if (bullCircle != null) {
            bullCircle.setText(String.valueOf(bull));
            // Intensity: more bullish = more vivid green
            String bgColor  = bull > 0 ? "#238636" : "#1a2b1a";
            String brdColor = bull > 0 ? "#3fb950" : "#30363d";
            bullCircle.setStyle(
                    "-fx-background-color:" + bgColor + ";"
                    + "-fx-text-fill:" + (bull > 0 ? "white" : "#8b949e") + ";"
                    + "-fx-background-radius:50%;"
                    + "-fx-min-width:36px;-fx-min-height:36px;"
                    + "-fx-max-width:36px;-fx-max-height:36px;"
                    + "-fx-alignment:center;-fx-font-weight:bold;-fx-font-size:13px;"
                    + "-fx-border-radius:50%;-fx-border-color:" + brdColor + ";-fx-border-width:2;");
            Tooltip buyTip = new Tooltip(
                    "🟢 Buying Sentiment\n"
                    + bull + " indicator(s) show bullish signals.\n"
                    + "Higher count = stronger buying pressure.");
            buyTip.setShowDelay(Duration.millis(200));
            Tooltip.install(bullCircle, buyTip);
        }

        if (neutralCircle != null) {
            neutralCircle.setText(String.valueOf(neu));
            String bgColor  = neu > 0 ? "#2d333b" : "#161b22";
            String brdColor = neu > 0 ? "#8b949e" : "#30363d";
            neutralCircle.setStyle(
                    "-fx-background-color:" + bgColor + ";"
                    + "-fx-text-fill:" + (neu > 0 ? "#e6edf3" : "#484f58") + ";"
                    + "-fx-background-radius:50%;"
                    + "-fx-min-width:36px;-fx-min-height:36px;"
                    + "-fx-max-width:36px;-fx-max-height:36px;"
                    + "-fx-alignment:center;-fx-font-weight:bold;-fx-font-size:13px;"
                    + "-fx-border-radius:50%;-fx-border-color:" + brdColor + ";-fx-border-width:2;");
            Tooltip neutTip = new Tooltip(
                    "⚪ Neutral Sentiment\n"
                    + neu + " indicator(s) show no clear direction.\n"
                    + "Neutral means sideways or conflicting signals.");
            neutTip.setShowDelay(Duration.millis(200));
            Tooltip.install(neutralCircle, neutTip);
        }

        if (bearCircle != null) {
            bearCircle.setText(String.valueOf(bear));
            String bgColor  = bear > 0 ? "#da3633" : "#2b1a1a";
            String brdColor = bear > 0 ? "#f85149" : "#30363d";
            bearCircle.setStyle(
                    "-fx-background-color:" + bgColor + ";"
                    + "-fx-text-fill:" + (bear > 0 ? "white" : "#8b949e") + ";"
                    + "-fx-background-radius:50%;"
                    + "-fx-min-width:36px;-fx-min-height:36px;"
                    + "-fx-max-width:36px;-fx-max-height:36px;"
                    + "-fx-alignment:center;-fx-font-weight:bold;-fx-font-size:13px;"
                    + "-fx-border-radius:50%;-fx-border-color:" + brdColor + ";-fx-border-width:2;");
            Tooltip sellTip = new Tooltip(
                    "🔴 Selling Sentiment\n"
                    + bear + " indicator(s) show bearish signals.\n"
                    + "Higher count = stronger selling pressure.");
            sellTip.setShowDelay(Duration.millis(200));
            Tooltip.install(bearCircle, sellTip);
        }

        // Legend tooltip on the entire sentiment box
        if (sentimentBox != null) {
            Tooltip legendTip = new Tooltip(
                    "Sentiment Circles Legend\n"
                    + "─────────────────────────\n"
                    + "🟢 Green  = Buying  (bullish indicators)\n"
                    + "⚪ Gray   = Neutral (no clear direction)\n"
                    + "🔴 Red    = Selling (bearish indicators)\n\n"
                    + "The number inside each circle shows how many\n"
                    + "weighted indicators are giving that signal.");
            legendTip.setShowDelay(Duration.millis(300));
            legendTip.setPrefWidth(280);
            legendTip.setWrapText(true);
            Tooltip.install(sentimentBox, legendTip);
        }
    }

    // ── Utilities ─────────────────────────────────────────────

    private static ListCell<MarketDataProvider> providerLabelCell() {
        return new ListCell<>() {
            @Override protected void updateItem(MarketDataProvider p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : p.getLabel());
            }
        };
    }

    private String activeStyle() {
        return "-fx-background-color:#1f6feb;-fx-text-fill:white;"
                + "-fx-background-radius:6;-fx-padding:4 8;-fx-cursor:hand;";
    }

    /** Feature 2: style for favorited timeframes — gold tint. */
    private String favoriteStyle() {
        return "-fx-background-color:#b08800;-fx-text-fill:white;"
                + "-fx-background-radius:6;-fx-padding:4 8;-fx-cursor:hand;";
    }

    private String inactiveStyle() {
        return "-fx-background-color:#21262d;-fx-text-fill:#e6edf3;"
                + "-fx-background-radius:6;-fx-padding:4 8;-fx-cursor:hand;";
    }

    private String fmtPrice(double p) {
        if (p >= 1000) return String.format("%.2f", p);
        if (p >= 1)    return String.format("%.4f", p);
        return String.format("%.8f", p);
    }
}
