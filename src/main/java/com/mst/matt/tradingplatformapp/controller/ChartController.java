package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.model.*;
import com.mst.matt.tradingplatformapp.repository.UserProfileRepository;
import com.mst.matt.tradingplatformapp.service.OhlcvStorageService;
import com.mst.matt.tradingplatformapp.service.ProfilePersistenceService;
import com.mst.matt.tradingplatformapp.service.analysis.AnalysisService;
import com.mst.matt.tradingplatformapp.service.analysis.AnalysisService.AnalysisResult;
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
import javafx.scene.layout.Pane;
import net.rgielen.fxweaver.core.FxmlView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

@Component
@FxmlView("/fxml/ChartView.fxml")
public class ChartController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(ChartController.class);

    public enum ViewMode { CHART, ANALYSIS }

    @FXML private TextField  symbolInput;
    @FXML private ComboBox<String> symbolCombo;
    @FXML private Pane       chartPane;
    @FXML private ComboBox<Integer> barsCombo;
    @FXML private ComboBox<MarketDataProvider> chartProviderCombo;
    @FXML private Label dataSourceLabel;
    @FXML private ToggleButton tf1m, tf5m, tf15m, tf1h, tf4h, tf1d, tf1w;
    @FXML private javafx.scene.layout.HBox analysisToolbar;
    @FXML private Button indicatorPickerBtn;
    @FXML private Button analyzeBtn;
    @FXML private CheckBox chkEma, chkBollinger, chkIchimoku, chkSR,
            chkVolume, chkMacd, chkRsi;
    @FXML private javafx.scene.layout.HBox signalBar;
    @FXML private Label signalLabel, confidenceLabel, bestBuyLabel,
            bestSellLabel, bullBearLabel, currentPriceChartLabel;

    @Autowired private AnalysisService       analysisService;
    @Autowired private OhlcvStorageService  ohlcvStorageService;
    @Autowired private PriceRouter           priceRouter;
    @Autowired private PriceProviderRegistry providerRegistry;
    @Autowired private UserProfileRepository profileRepository;
    /** P1 (LOG-FIX): async writer to keep JavaFX thread off JPA commits. */
    @Autowired private ProfilePersistenceService profilePersistence;
    @Autowired @org.springframework.context.annotation.Lazy
    private IndicatorMixerController indicatorMixerController;

    @Value("${app.chart.default-bars:200}")
    private int defaultBars;

    private CandlestickChartCanvas chart;
    private UserProfile activeProfile;
    private String      currentSymbol    = "BTCUSDT";
    private String      currentTimeframe = "1h";
    private int         currentBars;
    private ViewMode    viewMode = ViewMode.CHART;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        currentBars = defaultBars;

        chart = new CandlestickChartCanvas();
        chart.widthProperty().bind(chartPane.widthProperty());
        chart.heightProperty().bind(chartPane.heightProperty());
        chartPane.getChildren().add(chart);
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

        barsCombo.setItems(FXCollections.observableArrayList(
                50, 100, 200, 300, 500, 750, 1000));
        barsCombo.setValue(defaultBars);
        barsCombo.valueProperty().addListener((o, a, n) -> {
            if (n != null) { currentBars = n; loadChart(); }
        });

        ToggleGroup tfGroup = new ToggleGroup();
        for (ToggleButton btn : new ToggleButton[]{tf1m, tf5m, tf15m, tf1h, tf4h, tf1d, tf1w})
            btn.setToggleGroup(tfGroup);
        tf1h.setSelected(true);
        styleTimeframeButtons(tf1h);
    }

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
        // managed follows visible (bound in initialize) — never call setManaged on signalBar
        if (signalBar != null && !analysis) {
            signalBar.setVisible(false);
        }
        chart.setAnalysisMode(analysis);
        if (analysis) {
            if (chkEma != null && !chkEma.isSelected()
                    && chkBollinger != null && !chkBollinger.isSelected()) {
                chkEma.setSelected(true);
                chkVolume.setSelected(true);
            }
        }
        syncOverlayTogglesToChart();
    }

    public void prepareView() {
        applyViewMode();
        if (symbolInput.getText() == null || symbolInput.getText().isBlank())
            symbolInput.setText(currentSymbol);
        else
            currentSymbol = symbolInput.getText().trim().toUpperCase();
        if (symbolCombo.getValue() == null)
            symbolCombo.setValue(currentSymbol);
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
        // P2 (LOG-FIX): null-safe asset class lookup.
        var focus = activeProfile.getAssetFocus() != null
                ? activeProfile.getAssetFocus()
                : com.mst.matt.tradingplatformapp.model.UserProfile.ProfileAssetFocus.MULTI;
        AssetClass asset = AssetClassDetector.fromProfileFocus(focus, currentSymbol);
        List<MarketDataProvider> options = new ArrayList<>();
        options.add(MarketDataProvider.AUTO);
        options.addAll(providerRegistry.enabledProvidersFor(asset));
        chartProviderCombo.setItems(FXCollections.observableArrayList(options));
        chartProviderCombo.setValue(
                MarketDataProvider.fromString(activeProfile.getChartProvider()));
    }

    @FXML public void onChartProviderChanged() {
        if (activeProfile == null) return;
        MarketDataProvider chosen = chartProviderCombo.getValue();
        if (chosen == null) return;
        activeProfile.setChartProvider(chosen.name());
        // P1 (LOG-FIX): never block the JavaFX UI thread on a SQLite commit.
        profilePersistence.saveAsync(activeProfile);
        Thread.ofVirtual().start(() -> {
            ohlcvStorageService.refreshBars(
                    currentSymbol, currentTimeframe, currentBars, activeProfile);
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

                Platform.runLater(() -> {
                    double lastPrice = result.getBars().isEmpty() ? Double.NaN
                            : result.getBars().getLast().getClose().doubleValue();
                    quoteOpt.filter(q -> q.getPrice() != null)
                            .ifPresent(q -> chart.setLastPrice(q.getPrice().doubleValue()));
                    if (Double.isNaN(chart.getLastPrice()) && !Double.isNaN(lastPrice)) {
                        chart.setLastPrice(lastPrice);
                    }

                    chart.setData(result.getBars(),
                            result.getIndicators(),
                            result.getSrResult());

                    if (viewMode == ViewMode.ANALYSIS && result.getSignal() != null) {
                        updateSignalBar(result.getSignal());
                    }
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
                        if (viewMode == ViewMode.ANALYSIS) {
                            currentPriceChartLabel.setText(
                                    "$" + q.getPrice().toPlainString()
                                            + (q.isUp() ? " ▲" : " ▼"));
                            currentPriceChartLabel.setStyle("-fx-font-size:18px;"
                                    + "-fx-font-weight:bold; -fx-text-fill:"
                                    + (q.isUp() ? "#3fb950" : "#f85149") + ";");
                        }
                    });
                    // T-14: surface which API actually supplied the OHLCV chart.
                    dataSourceLabel.setText("OHLCV via " + provider);
                    dataSourceLabel.setTooltip(new Tooltip(
                            "Last successful price provider for " + currentSymbol
                                    + " — see docs/API_PROVIDERS.md for the fallback chain."));
                });
            } catch (Exception e) {
                log.warn("Chart load failed for {} {}: {}", currentSymbol, currentTimeframe,
                        e.getMessage());
                Platform.runLater(() ->
                        new Alert(Alert.AlertType.WARNING, friendlyChartError(e))
                                .showAndWait());
            }
        });
    }

    private AnalysisResult loadAnalysisWithRetry() throws Exception {
        try {
            return analysisService.analyze(
                    currentSymbol, currentTimeframe, currentBars, activeProfile);
        } catch (DataIntegrityViolationException dup) {
            log.debug("Registry race on {} {} — retrying chart load", currentSymbol, currentTimeframe);
            Thread.sleep(150);
            return analysisService.analyze(
                    currentSymbol, currentTimeframe, currentBars, activeProfile);
        }
    }

    private static String friendlyChartError(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) root = root.getCause();
        String msg = root.getMessage() == null ? "" : root.getMessage();
        if (root instanceof SQLException
                || msg.contains("HikariPool")
                || msg.contains("Connection is not available")) {
            return "Database is busy loading market data. Please wait a moment and try Refresh.";
        }
        if (e instanceof DataIntegrityViolationException
                || msg.contains("duplicate key")
                || msg.contains("idx_mdt_symbol_tf_provider")) {
            return "Market data registry is syncing. Please try Refresh in a few seconds.";
        }
        return "Could not load chart data. Check your connection or try Refresh.";
    }

    @FXML public void onRefresh() {
        if (activeProfile == null) return;
        Thread.ofVirtual().start(() -> {
            ohlcvStorageService.refreshBars(
                    currentSymbol, currentTimeframe, currentBars, activeProfile);
            loadChart();
        });
    }

    @FXML public void onAnalyze() { loadChart(); }

    private void updateSignalBar(SignalResult signal) {
        signalBar.setVisible(true);
        Recommendation rec = signal.getRecommendation();
        signalLabel.setText(rec.label);
        signalLabel.setStyle("-fx-font-weight:bold; -fx-font-size:14px;"
                + "-fx-text-fill:" + rec.textColor + ";");
        confidenceLabel.setText(String.format("Confidence: %.1f%%",
                signal.getConfidence()));
        bestBuyLabel.setText("$" + fmtPrice(signal.getBestBuyPrice()));
        bestSellLabel.setText("$" + fmtPrice(signal.getBestSellPrice()));
        bullBearLabel.setText("🟢 " + signal.getBullishCount()
                + "  ⚪ " + signal.getNeutralCount()
                + "  🔴 " + signal.getBearishCount());
    }

    @FXML public void onTimeframe(javafx.event.ActionEvent e) {
        ToggleButton src = (ToggleButton) e.getSource();
        if (!src.isSelected()) {
            src.setSelected(true);
            return;
        }
        currentTimeframe = switch (src.getText()) {
            case "1D" -> "1d";
            case "1W" -> "1w";
            default   -> src.getText().toLowerCase();
        };
        styleTimeframeButtons(src);
        loadChart();
    }

    private void styleTimeframeButtons(ToggleButton active) {
        for (ToggleButton btn : new ToggleButton[]{tf1m, tf5m, tf15m, tf1h, tf4h, tf1d, tf1w}) {
            btn.setStyle(btn == active ? getActiveStyle() : getInactiveStyle());
        }
    }

    @FXML public void onToggleEma()       { chart.toggleEma(); }
    @FXML public void onToggleBollinger() { chart.toggleBollinger(); }
    @FXML public void onToggleIchimoku()  { chart.toggleIchimoku(); }
    @FXML public void onToggleSR()        { chart.toggleSR(); }
    @FXML public void onToggleVolume()    { chart.toggleVolume(); }
    @FXML public void onToggleMacd()      { chart.toggleMacd(); }
    @FXML public void onToggleRsi()       { chart.toggleRsi(); }

    @FXML public void onOpenIndicatorPicker() {
        Map<String, Boolean> state = new LinkedHashMap<>();
        state.put("EMA", chkEma != null && chkEma.isSelected());
        state.put("Bollinger Bands", chkBollinger != null && chkBollinger.isSelected());
        state.put("Ichimoku Cloud", chkIchimoku != null && chkIchimoku.isSelected());
        state.put("Support / Resistance", chkSR != null && chkSR.isSelected());
        state.put("Volume", chkVolume != null && chkVolume.isSelected());
        state.put("MACD", chkMacd != null && chkMacd.isSelected());
        state.put("RSI", chkRsi != null && chkRsi.isSelected());
        state.put("Stochastic", false);
        state.put("ATR", false);
        state.put("CCI", false);
        state.put("VWAP", false);
        IndicatorPickerDialog.show(indicatorPickerBtn, state, selected -> {
            if (chkEma != null) chkEma.setSelected(selected.getOrDefault("EMA", false));
            if (chkBollinger != null) chkBollinger.setSelected(selected.getOrDefault("Bollinger Bands", false));
            if (chkIchimoku != null) chkIchimoku.setSelected(selected.getOrDefault("Ichimoku Cloud", false));
            if (chkSR != null) chkSR.setSelected(selected.getOrDefault("Support / Resistance", false));
            if (chkVolume != null) chkVolume.setSelected(selected.getOrDefault("Volume", false));
            if (chkMacd != null) chkMacd.setSelected(selected.getOrDefault("MACD", false));
            if (chkRsi != null) chkRsi.setSelected(selected.getOrDefault("RSI", false));
            syncOverlayTogglesToChart();
        });
    }

    private void syncOverlayTogglesToChart() {
        if (chkEma != null) chart.setShowEma(chkEma.isSelected());
        if (chkBollinger != null) chart.setShowBollinger(chkBollinger.isSelected());
        if (chkIchimoku != null) chart.setShowIchimoku(chkIchimoku.isSelected());
        if (chkSR != null) chart.setShowSR(chkSR.isSelected());
        if (chkVolume != null) chart.setShowVolume(chkVolume.isSelected());
        if (chkMacd != null) chart.setShowMacd(chkMacd.isSelected());
        if (chkRsi != null) chart.setShowRsi(chkRsi.isSelected());
    }

    private static ListCell<MarketDataProvider> providerLabelCell() {
        return new ListCell<>() {
            @Override protected void updateItem(MarketDataProvider p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : p.getLabel());
            }
        };
    }

    private String getActiveStyle() {
        return "-fx-background-color:#1f6feb; -fx-text-fill:white;"
                + "-fx-background-radius:6; -fx-padding:4 8; -fx-cursor:hand;";
    }
    private String getInactiveStyle() {
        return "-fx-background-color:#21262d; -fx-text-fill:#e6edf3;"
                + "-fx-background-radius:6; -fx-padding:4 8; -fx-cursor:hand;";
    }

    private String fmtPrice(double p) {
        if (p >= 1000) return String.format("%.2f", p);
        if (p >= 1)    return String.format("%.4f", p);
        return String.format("%.8f", p);
    }
}
