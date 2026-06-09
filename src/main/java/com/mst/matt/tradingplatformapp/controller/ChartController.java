package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.model.*;
import com.mst.matt.tradingplatformapp.repository.UserProfileRepository;
import com.mst.matt.tradingplatformapp.service.OhlcvStorageService;
import com.mst.matt.tradingplatformapp.service.ProfilePersistenceService;
import com.mst.matt.tradingplatformapp.service.analysis.AnalysisService;
import com.mst.matt.tradingplatformapp.service.analysis.AnalysisService.AnalysisResult;
import com.mst.matt.tradingplatformapp.service.analysis.IndicatorComputeService;
import com.mst.matt.tradingplatformapp.service.analysis.SignalScoringService.Recommendation;
import com.mst.matt.tradingplatformapp.service.analysis.SignalScoringService.SignalResult;
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
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

@Component
@FxmlView("/fxml/ChartView.fxml")
public class ChartController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(ChartController.class);

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
    @FXML private Label  signalLabel, confidenceLabel, bestBuyLabel,
            bestSellLabel, bullBearLabel, currentPriceChartLabel;

    // ── Spring beans ──────────────────────────────────────────
    @Autowired private AnalysisService          analysisService;
    @Autowired private IndicatorComputeService  indicatorComputeService;
    @Autowired private OhlcvStorageService      ohlcvStorageService;
    @Autowired private PriceRouter              priceRouter;
    @Autowired private PriceProviderRegistry    providerRegistry;
    @Autowired private UserProfileRepository    profileRepository;
    @Autowired private ProfilePersistenceService profilePersistence;
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

    /**
     * Active indicator list — one entry per indicator instance the user has added.
     * Persisted in memory; survives chart refreshes.
     */
    private final List<IndicatorDefinition> activeIndicators = new ArrayList<>();

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        currentBars = defaultBars;

        chart = new CandlestickChartCanvas();
        chart.widthProperty().bind(chartPane.widthProperty());
        chart.heightProperty().bind(chartPane.heightProperty());
        chartPane.getChildren().add(chart);

        if (signalBar != null)
            signalBar.managedProperty().bind(signalBar.visibleProperty());

        chartProviderCombo.setCellFactory(lv -> providerLabelCell());
        chartProviderCombo.setButtonCell(providerLabelCell());

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
        barsCombo.setValue(defaultBars);
        barsCombo.valueProperty().addListener((o, a, n) -> {
            if (n != null) { currentBars = n; loadChart(); }
        });

        // Timeframe toggle group — wire all buttons
        ToggleGroup tfGroup = new ToggleGroup();
        for (ToggleButton btn : allTimeframeButtons()) {
            if (btn != null) btn.setToggleGroup(tfGroup);
        }
        if (tf1h != null) { tf1h.setSelected(true); styleTimeframeButtons(tf1h); }
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
        applyViewMode();
        if (symbolInput.getText() == null || symbolInput.getText().isBlank())
            symbolInput.setText(currentSymbol);
        else
            currentSymbol = symbolInput.getText().trim().toUpperCase();
        if (symbolCombo.getValue() == null) symbolCombo.setValue(currentSymbol);
        refreshProviderCombo();
        loadChart();
    }

    public void setProfile(UserProfile profile) {
        this.activeProfile = profile;
        analysisService.setActiveProfile(profile);
        priceRouter.setActiveProfile(profile);
        if (profile != null && profile.getDefaultSymbol() != null
                && !profile.getDefaultSymbol().isBlank()) {
            currentSymbol = profile.getDefaultSymbol();
            symbolInput.setText(currentSymbol);
            symbolCombo.setValue(currentSymbol);
        }
        refreshProviderCombo();
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
        if (activeProfile == null) return;
        Thread.ofVirtual().start(() -> {
            try {
                AnalysisResult result = loadAnalysisWithRetry();
                var quoteOpt = priceRouter.getQuote(currentSymbol, activeProfile);
                String provider = PriceRouter.getLastProviderName();

                // Compute all active IndicatorDefinitions on the bar series
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
        currentTimeframe = timeframeFor(src);
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
        for (ToggleButton btn : allTimeframeButtons()) {
            if (btn == null) continue;
            btn.setStyle(btn == active ? activeStyle() : inactiveStyle());
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
            // Recompute indicators when list changes
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
        bullBearLabel.setText("🟢 " + signal.getBullishCount()
                + "  ⚪ " + signal.getNeutralCount()
                + "  🔴 " + signal.getBearishCount());
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
