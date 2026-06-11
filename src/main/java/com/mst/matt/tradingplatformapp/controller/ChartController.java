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
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
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
import java.util.*;
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
    // Timeframe buttons — all possible timeframes
    @FXML private ToggleButton tf1m, tf3m, tf5m, tf15m, tf30m,
            tf1h, tf2h, tf4h, tf6h, tf8h, tf12h,
            tf1d, tf3d, tf1w, tf1mo;
    @FXML private HBox   analysisToolbar;
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

    private final List<IndicatorDefinition> activeIndicators = new ArrayList<>();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Feature 4.3: cap default bars to role limit
        currentBars = Math.min(defaultBars, authService.maxCandles());

        chart = new CandlestickChartCanvas();
        chart.widthProperty().bind(chartPane.widthProperty());
        chart.heightProperty().bind(chartPane.heightProperty());
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

        barsCombo.setItems(FXCollections.observableArrayList(50, 100, 200, 300, 500, 750, 1000));
        barsCombo.setValue(currentBars);
        barsCombo.valueProperty().addListener((o, a, n) -> {
            if (n != null) {
                // Feature 4.3: enforce role cap
                currentBars = Math.min(n, authService.maxCandles());
                loadChart();
            }
        });

        // Timeframe toggle group + role-based visibility
        ToggleGroup tfGroup = new ToggleGroup();
        for (ToggleButton btn : allTimeframeButtons()) {
            if (btn != null) btn.setToggleGroup(tfGroup);
        }
        applyTimeframeAccess();
        if (tf1h != null && tf1h.isVisible()) { tf1h.setSelected(true); styleTimeframeButtons(tf1h); }
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
        chartProviderCombo.setItems(FXCollections.observableArrayList(options));
        chartProviderCombo.setValue(
                MarketDataProvider.fromString(activeProfile.getChartProvider()));
    }

    // ── Feature 4.1: Timeframe access ────────────────────────

    /**
     * Show only the timeframe buttons allowed for the current role.
     * Also applies favorite ordering (Feature 2).
     */
    private void applyTimeframeAccess() {
        List<String> allowed  = authService.allowedTimeframes();
        List<String> favorites = authService.getFavoriteTimeframes();

        for (ToggleButton btn : allTimeframeButtons()) {
            if (btn == null) continue;
            String tf = timeframeFor(btn);
            boolean canUse = allowed.contains(tf);
            btn.setVisible(canUse);
            btn.setManaged(canUse);
            // Feature 2: highlight favorites with a star tooltip
            if (canUse && favorites.contains(tf)) {
                btn.setStyle(favoriteStyle());
                Tooltip t = new Tooltip("★ Favorite — " + tf.toUpperCase());
                t.setShowDelay(javafx.util.Duration.millis(200));
                btn.setTooltip(t);
            } else if (canUse) {
                btn.setStyle(inactiveStyle());
                Tooltip t = new Tooltip("Click to use " + tf.toUpperCase()
                        + "  |  Right-click to ★ favorite");
                t.setShowDelay(javafx.util.Duration.millis(200));
                btn.setTooltip(t);
                // Right-click context menu to toggle favorite
                ContextMenu cm = new ContextMenu();
                MenuItem starItem = new MenuItem("★ Add to Favorites");
                starItem.setOnAction(ev -> toggleFavorite(tf, btn));
                MenuItem unstarItem = new MenuItem("✕ Remove from Favorites");
                unstarItem.setOnAction(ev -> toggleFavorite(tf, btn));
                cm.getItems().addAll(starItem, unstarItem);
                btn.setContextMenu(cm);
            }
        }

        // Feature 4.3: also cap barsCombo max to role limit
        int maxC = authService.maxCandles();
        if (barsCombo != null) {
            List<Integer> caps = List.of(50, 100, 200, 300, 500, 750, 1000).stream()
                    .filter(v -> v <= maxC).collect(Collectors.toList());
            if (!caps.contains(maxC)) caps = new ArrayList<>(caps);
            barsCombo.setItems(FXCollections.observableArrayList(caps));
            if (currentBars > maxC) {
                currentBars = maxC;
                barsCombo.setValue(maxC);
            }
        }
    }

    /**
     * Feature 2: toggle a timeframe in/out of favorites and persist via AuthService.
     */
    private void toggleFavorite(String tf, ToggleButton btn) {
        List<String> current = new ArrayList<>(authService.getFavoriteTimeframes());
        if (current.contains(tf)) {
            current.remove(tf);
        } else {
            current.add(tf);
        }
        authService.saveFavoriteTimeframes(current);
        // Refresh button styling
        applyTimeframeAccess();
        // Restore active TF selection styling
        for (ToggleButton b : allTimeframeButtons()) {
            if (b != null && b.isSelected()) {
                b.setStyle(activeStyle());
                break;
            }
        }
    }

    // ── Chart loading ─────────────────────────────────────────

    @FXML public void onChartProviderChanged() {
        if (activeProfile == null) return;
        MarketDataProvider chosen = chartProviderCombo.getValue();
        if (chosen == null) return;
        activeProfile.setChartProvider(chosen.name());
        profilePersistence.saveAsync(activeProfile);
        Thread.ofVirtual().start(() -> {
            ohlcvStorageService.refreshBars(currentSymbol, currentTimeframe, currentBars, activeProfile);
            loadChart();
        });
    }

    @FXML public void onLoadChart() {
        String sym = symbolInput.getText().trim().toUpperCase();
        if (!sym.isEmpty()) currentSymbol = sym;
        refreshProviderCombo();
        loadChart();
    }

    private void loadChart() {
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
                log.warn("Chart load failed for {} {}: {}", currentSymbol, currentTimeframe,
                        e.getMessage());
                Platform.runLater(() ->
                        dataSourceLabel.setText("⚠ Data unavailable — retrying…"));
            }
        });
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
        // Feature 4.1: double-check permission (shouldn't be visible if not allowed, but safety net)
        if (!authService.canUseTimeframe(tf)) {
            src.setSelected(false);
            new Alert(Alert.AlertType.INFORMATION,
                    "Timeframe '" + tf + "' is not available for your plan.").showAndWait();
            return;
        }
        currentTimeframe = tf;
        styleTimeframeButtons(src);
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
            buyTip.setShowDelay(javafx.util.Duration.millis(200));
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
            neutTip.setShowDelay(javafx.util.Duration.millis(200));
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
            sellTip.setShowDelay(javafx.util.Duration.millis(200));
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
            legendTip.setShowDelay(javafx.util.Duration.millis(300));
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
