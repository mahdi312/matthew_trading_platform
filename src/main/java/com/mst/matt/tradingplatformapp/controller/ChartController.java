package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.model.*;
import com.mst.matt.tradingplatformapp.model.SymbolEntry.AssetType;
import com.mst.matt.tradingplatformapp.model.Trade.TradeDirection;
import com.mst.matt.tradingplatformapp.repository.SymbolEntryRepository;
import com.mst.matt.tradingplatformapp.repository.UserProfileRepository;
import com.mst.matt.tradingplatformapp.service.ChartDrawingService;
import com.mst.matt.tradingplatformapp.service.OhlcvStorageService;
import com.mst.matt.tradingplatformapp.service.ProfilePersistenceService;
import com.mst.matt.tradingplatformapp.service.analysis.AnalysisService;
import com.mst.matt.tradingplatformapp.service.analysis.AnalysisService.AnalysisResult;
import com.mst.matt.tradingplatformapp.service.analysis.IndicatorComputeService;
import com.mst.matt.tradingplatformapp.service.analysis.SignalScoringService.Recommendation;
import com.mst.matt.tradingplatformapp.service.analysis.SignalScoringService.SignalResult;
import com.mst.matt.tradingplatformapp.service.auth.AuthService;
import com.mst.matt.tradingplatformapp.service.marketdata.ChartLiveSessionService;
import com.mst.matt.tradingplatformapp.service.price.AssetClassDetector;
import com.mst.matt.tradingplatformapp.service.price.AssetClassDetector.AssetClass;
import com.mst.matt.tradingplatformapp.service.price.MarketDataProvider;
import com.mst.matt.tradingplatformapp.service.price.PriceProviderRegistry;
import com.mst.matt.tradingplatformapp.service.price.PriceRouter;
import com.mst.matt.tradingplatformapp.ui.IndicatorPickerDialog;
import com.mst.matt.tradingplatformapp.ui.chart.*;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import net.rgielen.fxweaver.core.FxmlView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;

import java.math.BigDecimal;
import java.net.URL;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

// Screenshot imports (only used if javafx-swing is on classpath)
// javax.imageio.ImageIO and javafx.embed.swing.SwingFXUtils are used inside captureChartScreenshot()

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
    @FXML private BorderPane            chartRoot;
    @FXML private ScrollPane            chartToolbarScroll;
    @FXML private Pane                 chartPane;
    @FXML private StackPane             chartStack;
    @FXML private ComboBox<Integer>    barsCombo;
    @FXML private ComboBox<MarketDataProvider> chartProviderCombo;
    @FXML private Label                dataSourceLabel;
    @FXML private VBox                  notificationOverlay;
    @FXML private Button                chartNotifyBellBtn;
    @FXML private Button                fullscreenBtn;
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
    @Autowired private ChartDrawingService      chartDrawingService;
    @Autowired private com.mst.matt.tradingplatformapp.service.AppSettingsService appSettingsService;
    @Autowired private com.mst.matt.tradingplatformapp.service.marketdata.MarketDataSyncScheduler marketDataSyncScheduler;
    @Autowired private ChartLiveSessionService chartLiveSession;
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

    private DrawingToolbarPane drawingToolbarPane;
    private ChartToastManager toastManager;
    private Timeline chartRefreshTimeline;
    private Runnable onViewAllAlerts;
    private Consumer<TradeDrawingDraft> onCreateTradeFromDrawing;
    private Consumer<TradeDrawingDraft> onInstantSaveTradeFromDrawing;
    private boolean chartSessionActive;

    /** Fullscreen state tracking — single source of truth.
     *  Use {@code isFullscreen} as the authoritative flag; {@code fullscreenActive}
     *  is kept for legacy callers but must stay in sync. */
    private boolean isFullscreen = false;
    /** @deprecated use {@link #isFullscreen} instead */
    private boolean fullscreenActive = false;
    /** Reference to the scene's original content for fullscreen restore */
    private javafx.scene.Parent fullscreenOriginalParent = null;
    private javafx.scene.layout.StackPane fullscreenOverlay = null;
    /** Original parent of chartStack before entering fullscreen */
    private javafx.scene.layout.Pane fullscreenChartStackOriginalParent = null;
    /** Original index of chartStack in its parent before entering fullscreen (-2 = BorderPane center) */
    private int fullscreenChartStackOriginalIndex = -1;

    /** Current global drawing settings (persisted via AppSettingsService). */
    private GlobalDrawingSettings drawingSettings = new GlobalDrawingSettings();

    /** Screenshot callback – receives the PNG file path after capture. */
    private Consumer<String> onScreenshotSaved;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Feature 4.3: cap default bars to role limit
        currentBars = Math.min(defaultBars, authService.maxCandles());

        // Register staleness warning callback (Fix 2)
        ohlcvStorageService.setOnStaleDataWarning((symbolTf, msg) ->
                javafx.application.Platform.runLater(() -> showWarnNotification("⚠ " + msg)));

        chart = new CandlestickChartCanvas();
        chart.widthProperty().bind(chartPane.widthProperty());
        chart.heightProperty().bind(chartPane.heightProperty());
        // Apply user's configured timezone (falls back to system default)
        chart.setUserTimezone(appSettingsService.getUserTimezone());
        chart.setCurrentTimeframe(currentTimeframe);
        // Apply persisted sensitivity settings
        try {
            String zs = appSettingsService.getSetting("zoomSensitivity");
            String ps = appSettingsService.getSetting("panSensitivity");
            if (zs != null && !zs.isBlank()) chart.setZoomSensitivity(Double.parseDouble(zs));
            if (ps != null && !ps.isBlank()) chart.setPanSensitivity(Double.parseDouble(ps));
        } catch (Exception ignored) {}
        chartPane.getChildren().add(chart);
        setupChartLayout();
        setupDrawingToolbar();
        setupNotifications();
        setupChartAutoHide();
        wireDrawingEngine();
        setupKeyboardShortcuts();

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

    /** Pin the symbol toolbar at the top; chart canvas fills only the center region. */
    private void setupChartLayout() {
        if (chartRoot != null) {
            chartRoot.setMinSize(0, 0);
        }
        if (chartToolbarScroll != null) {
            VBox.setVgrow(chartToolbarScroll, Priority.NEVER);
            // fitToWidth="true" in FXML already expands the inner HBox to the
            // full scroll-pane width, so we do NOT bind prefWidth here (that
            // would fight with fitToWidth and create duplicate sizing paths).
            chartToolbarScroll.setMinHeight(44);
            chartToolbarScroll.setPrefHeight(44);
            chartToolbarScroll.setMaxHeight(44);
        }
        if (chartStack != null) {
            chartStack.setMinSize(0, 0);
        }
        if (chartPane != null) {
            chartPane.setMinSize(0, 0);
        }
    }

    private void setupDrawingToolbar() {
        if (chartStack == null) return;
        DrawingToolbar toolbar = new DrawingToolbar();
        drawingToolbarPane = new DrawingToolbarPane(toolbar);
        drawingToolbarPane.attachTo(chartStack);
        toolbar.setOnToolSelected(chart::setActiveDrawingTool);
        toolbar.setOnDelete(() -> chart.getDrawingEngine().deleteSelected());

        // Undo / Redo
        toolbar.setOnUndo(() -> chart.getDrawingEngine().undo());
        toolbar.setOnRedo(() -> chart.getDrawingEngine().redo());

        // Save / Load / Delete Layout
        toolbar.setOnSaveLayout(this::onSaveLayout);
        toolbar.setOnLoadLayout(this::onLoadLayout);
        toolbar.setOnDeleteLayout(this::onDeleteLayout);

        // Global drawing settings
        toolbar.setOnDrawingSettings(this::onOpenDrawingSettings);

        // Show / Hide all drawings
        toolbar.setOnToggleShowAll(() -> {
            var engine = chart.getDrawingEngine();
            engine.setShowAllDrawings(!engine.isShowAllDrawings());
            drawingSettings.setShowAllDrawings(engine.isShowAllDrawings());
            persistDrawingSettings();
            showInfoNotification(engine.isShowAllDrawings()
                    ? "All drawings shown." : "All drawings hidden.");
        });

        // Lock / Unlock all drawings
        toolbar.setOnToggleLockAll(() -> {
            var engine = chart.getDrawingEngine();
            engine.setLockAllDrawings(!engine.isLockAllDrawings());
            drawingSettings.setLockAllDrawings(engine.isLockAllDrawings());
            persistDrawingSettings();
            showInfoNotification(engine.isLockAllDrawings()
                    ? "All drawings locked." : "All drawings unlocked.");
        });

        // Screenshot
        toolbar.setOnScreenshot(this::onCaptureScreenshot);
    }

    public void setOnScreenshotSaved(Consumer<String> cb) { this.onScreenshotSaved = cb; }

    // ── Drawing Layout helpers ────────────────────────────────────────────────

    private void onSaveLayout() {
        if (activeProfile == null) { showWarnNotification("No active profile."); return; }
        var engine = chart.getDrawingEngine();
        List<ChartDrawing> drawings = new ArrayList<>(engine.getDrawings());
        Thread.ofVirtual().start(() -> {
            var layouts = chartDrawingService.listLayouts(activeProfile, currentSymbol, currentTimeframe);
            javafx.application.Platform.runLater(() ->
                    LayoutManagerDialog.showSave(
                            chart.getScene() != null ? chart.getScene().getWindow() : null,
                            layouts,
                            name -> Thread.ofVirtual().start(() -> {
                                chartDrawingService.saveLayout(
                                        activeProfile, currentSymbol, currentTimeframe, name, drawings);
                                javafx.application.Platform.runLater(() ->
                                        showInfoNotification("Layout \"" + name + "\" saved."));
                            })));
        });
    }

    private void onLoadLayout() {
        if (activeProfile == null) { showWarnNotification("No active profile."); return; }
        Thread.ofVirtual().start(() -> {
            var layouts = chartDrawingService.listLayouts(activeProfile, currentSymbol, currentTimeframe);
            javafx.application.Platform.runLater(() ->
                    LayoutManagerDialog.showLoad(
                            chart.getScene() != null ? chart.getScene().getWindow() : null,
                            layouts,
                            name -> Thread.ofVirtual().start(() -> {
                                List<ChartDrawing> loaded = chartDrawingService.loadLayout(
                                        activeProfile, currentSymbol, currentTimeframe, name);
                                javafx.application.Platform.runLater(() -> {
                                    chart.setDrawings(loaded);
                                    showInfoNotification("Layout \"" + name + "\" loaded ("
                                            + loaded.size() + " drawings).");
                                });
                            })));
        });
    }

    private void onDeleteLayout() {
        if (activeProfile == null) { showWarnNotification("No active profile."); return; }
        Thread.ofVirtual().start(() -> {
            var layouts = chartDrawingService.listLayouts(activeProfile, currentSymbol, currentTimeframe);
            javafx.application.Platform.runLater(() ->
                    LayoutManagerDialog.showDelete(
                            chart.getScene() != null ? chart.getScene().getWindow() : null,
                            layouts,
                            name -> Thread.ofVirtual().start(() -> {
                                chartDrawingService.deleteLayout(
                                        activeProfile, currentSymbol, currentTimeframe, name);
                                javafx.application.Platform.runLater(() ->
                                        showInfoNotification("Layout \"" + name + "\" deleted."));
                            })));
        });
    }

    // ── Global Drawing Settings (per-profile) ────────────────────────────────

    private void onOpenDrawingSettings() {
        GlobalDrawingSettingsDialog.show(
                drawingSettings,
                chart.getScene() != null ? chart.getScene().getWindow() : null,
                updated -> {
                    drawingSettings = updated;
                    chart.getDrawingEngine().applyGlobalSettings(updated);
                    persistDrawingSettings();
                    showInfoNotification("Drawing settings saved.");
                });
    }

    /**
     * Persists drawing settings to the active profile's {@code drawingSettingsJson}
     * column so each profile has its own independent settings (Issue 7.1).
     *
     * <p>Falls back to {@code AppSettingsService} if no active profile is set (legacy path).
     */
    private void persistDrawingSettings() {
        try {
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().create();
            String json = gson.toJson(drawingSettings);
            if (activeProfile != null) {
                // ── Per-profile persistence (primary path) ──────────────────────
                activeProfile.setDrawingSettingsJson(json);
                profileRepository.save(activeProfile);
            } else {
                // ── Fallback: machine-level settings ───────────────────────────
                appSettingsService.setSetting("drawingSettings", json);
            }
        } catch (Exception e) {
            log.warn("Failed to persist drawing settings: {}", e.getMessage());
        }
    }

    /**
     * Loads drawing settings for the current profile.
     * If the profile has no saved settings yet, falls back to the machine-level
     * setting in {@code AppSettingsService} for backward compatibility (Issue 7.1).
     */
    private void loadDrawingSettings() {
        try {
            String json = null;
            if (activeProfile != null && activeProfile.getDrawingSettingsJson() != null
                    && !activeProfile.getDrawingSettingsJson().isBlank()) {
                // ── Per-profile settings (primary path) ────────────────────────
                json = activeProfile.getDrawingSettingsJson();
            } else {
                // ── Machine-level fallback (backward compat) ───────────────────
                json = appSettingsService.getSetting("drawingSettings");
            }

            if (json != null && !json.isBlank()) {
                com.google.gson.Gson gson = new com.google.gson.GsonBuilder().create();
                drawingSettings = gson.fromJson(json, GlobalDrawingSettings.class);
                if (drawingSettings == null) drawingSettings = new GlobalDrawingSettings();
            } else {
                drawingSettings = new GlobalDrawingSettings();
            }
            chart.getDrawingEngine().applyGlobalSettings(drawingSettings);
        } catch (Exception e) {
            log.warn("Failed to load drawing settings: {}", e.getMessage());
            drawingSettings = new GlobalDrawingSettings();
        }
    }

    // ── Screenshot ────────────────────────────────────────────────────────────

    /**
     * Captures a PNG screenshot of the chart and saves it under
     * {@code ~/.trading-platform/screenshots/}.
     *
     * @return the absolute path of the saved file, or {@code null} on failure.
     */
    public String captureChartScreenshot() {
        return captureChartScreenshot(null);
    }

    /**
     * Captures a PNG screenshot using a descriptive filename that includes the symbol.
     * Format: {@code YYYY-MM-DD_SYMBOL_timestamp.png}
     *
     * @param symbol chart symbol for the filename, may be {@code null}
     * @return the absolute path of the saved file, or {@code null} on failure.
     */
    public String captureChartScreenshot(String symbol) {
        try {
            javafx.scene.image.WritableImage img = chart.captureScreenshot();
            if (img == null) return null;

            String homeDir = System.getProperty("user.home");
            java.io.File screenshotDir = new java.io.File(homeDir, ".trading-platform/screenshots");
            screenshotDir.mkdirs();

            // Format: YYYY-MM-DD_SYMBOL_timestamp.png
            String date    = java.time.LocalDate.now().toString();
            String symPart = (symbol != null && !symbol.isBlank())
                    ? symbol.replaceAll("[^A-Za-z0-9]", "") + "_"
                    : "";
            String fileName = date + "_" + symPart + System.currentTimeMillis() + ".png";
            java.io.File outFile = new java.io.File(screenshotDir, fileName);

            javax.imageio.ImageIO.write(
                    javafx.embed.swing.SwingFXUtils.fromFXImage(img, null),
                    "PNG", outFile);

            return outFile.getAbsolutePath();
        } catch (Exception ex) {
            log.warn("Screenshot capture failed: {}", ex.getMessage());
            return null;
        }
    }

    private void onCaptureScreenshot() {
        String path = captureChartScreenshot(currentSymbol);
        if (path != null) {
            showInfoNotification("Screenshot saved: " + path);
            if (onScreenshotSaved != null) onScreenshotSaved.accept(path);
        } else {
            showErrorNotification("Screenshot capture failed.");
        }
    }

    private void setupNotifications() {
        if (notificationOverlay == null) return;
        toastManager = new ChartToastManager(notificationOverlay);
        toastManager.setOnViewAllAlerts(() -> {
            if (onViewAllAlerts != null) onViewAllAlerts.run();
        });
    }

    public void setOnViewAllAlerts(Runnable callback) {
        this.onViewAllAlerts = callback;
    }

    @FXML public void onToggleChartNotifications() {
        if (toastManager == null) return;
        boolean next = !toastManager.isEnabled();
        toastManager.setEnabled(next);
        if (chartNotifyBellBtn != null) {
            chartNotifyBellBtn.setStyle(next ? "" : "-fx-opacity:0.5;");
        }
    }

    private void setupChartAutoHide() {
        if (chartStack == null) return;
        chartStack.setOnMouseEntered(e -> {
            if (drawingToolbarPane != null) drawingToolbarPane.onChartMouseEntered();
        });
        chartStack.setOnMouseExited(e -> {
            if (drawingToolbarPane != null) drawingToolbarPane.onChartMouseExited();
        });
    }

    // ── Fullscreen ────────────────────────────────────────────────────────────

    /**
     * Toggles fullscreen mode for the chart.
     * In fullscreen: the chart stack fills the entire stage window (no sidebar/header).
     * A floating toolbar overlay with TF buttons + bars combo remains visible.
     * Press F11 or click the button again to exit.
     */
    @FXML public void onToggleFullscreen() {
        if (isFullscreen) {
            exitFullscreen();
        } else {
            enterFullscreen();
        }
    }

    private void enterFullscreen() {
        // Guard: prevent double-enter which would corrupt the saved parent reference
        if (isFullscreen) return;
        if (chartStack == null || chartStack.getScene() == null) return;
        javafx.stage.Stage stage = (javafx.stage.Stage) chartStack.getScene().getWindow();
        if (stage == null) return;

        // Save original parent BEFORE modifying the scene graph (critical order)
        javafx.scene.Scene scene = chartStack.getScene();
        fullscreenOriginalParent = (javafx.scene.Parent) scene.getRoot();

        isFullscreen = true;
        fullscreenActive = true;
        if (fullscreenBtn != null) {
            fullscreenBtn.setText("✕");
            fullscreenBtn.setTooltip(new Tooltip("Exit fullscreen (F11 or Esc)"));
        }

        // fullscreenOriginalParent was already set above BEFORE this block.
        // Use the already-stored scene reference.
//        scene = stage.getScene();

        // Build a fullscreen overlay StackPane (outer container for key events / focus)
        fullscreenOverlay = new javafx.scene.layout.StackPane();
        fullscreenOverlay.setStyle("-fx-background-color:#0d1117;");

        // Fix 1: Use a VBox inside the overlay so the floating toolbar and the chart canvas
        // are stacked vertically with a clear gap between them — the chart never starts
        // behind/underneath the timeframe bar.
        HBox floatingBar = buildFullscreenFloatingBar();
        floatingBar.setMouseTransparent(false);

        // 10 px spacer between toolbar and chart canvas (Fix 1)
        javafx.scene.layout.Region barGap = new javafx.scene.layout.Region();
        barGap.setMinHeight(10);
        barGap.setMaxHeight(10);
        barGap.setPrefHeight(10);
        barGap.setStyle("-fx-background-color:#0d1117;");

        VBox fullscreenVBox = new VBox();
        fullscreenVBox.setStyle("-fx-background-color:#0d1117;");
        VBox.setVgrow(chartStack, Priority.ALWAYS);

        // Save original chartStack parent + index so we can precisely restore on exit
        if (chartStack.getParent() instanceof BorderPane bp) {
            fullscreenChartStackOriginalParent = bp;
            fullscreenChartStackOriginalIndex  = -2;
            bp.setCenter(null);
        } else if (chartStack.getParent() instanceof javafx.scene.layout.Pane origParent) {
            fullscreenChartStackOriginalParent = origParent;
            fullscreenChartStackOriginalIndex  = origParent.getChildren().indexOf(chartStack);
            origParent.getChildren().remove(chartStack);
        } else {
            fullscreenChartStackOriginalParent = null;
            fullscreenChartStackOriginalIndex  = -1;
        }

        fullscreenVBox.getChildren().addAll(floatingBar, barGap, chartStack);
        fullscreenOverlay.getChildren().add(fullscreenVBox);

        // Re-bind chart canvas to the VBox width and to remaining height
        // (after the toolbar + gap are subtracted).
        chart.widthProperty().unbind();
        chart.heightProperty().unbind();
        chart.widthProperty().bind(fullscreenOverlay.widthProperty());
        // Height is managed by VBox.vgrow=ALWAYS; bind canvas height to chartStack
        chart.heightProperty().bind(chartStack.heightProperty());

        // Replace the scene root with the overlay — this takes chartStack with it
        scene.setRoot(fullscreenOverlay);
        stage.setFullScreen(true);
        stage.setFullScreenExitHint("Press F11 or Esc to exit fullscreen");

        // Register F11/Escape on the new scene root
        fullscreenOverlay.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.F11
                    || e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                exitFullscreen();
                e.consume();
            }
        });
        fullscreenOverlay.requestFocus();
    }

    private void exitFullscreen() {
        // Guard: prevent double-exit which caused the NPE on fullscreenOriginalParent.layout()
        if (!isFullscreen || fullscreenOriginalParent == null) {
            // Already exited or never properly entered — just reset state and return
            isFullscreen = false;
            fullscreenActive = false;
            fullscreenOverlay = null;
            fullscreenOriginalParent = null;
            fullscreenChartStackOriginalParent = null;
            fullscreenChartStackOriginalIndex  = -1;
            return;
        }
        isFullscreen = false;
        fullscreenActive = false;

        if (fullscreenBtn != null) {
            fullscreenBtn.setText("⛶");
            fullscreenBtn.setTooltip(new Tooltip("Toggle fullscreen (F11)"));
        }

        javafx.stage.Stage stage = (fullscreenOverlay != null && fullscreenOverlay.getScene() != null)
                ? (javafx.stage.Stage) fullscreenOverlay.getScene().getWindow()
                : null;
        if (stage != null) stage.setFullScreen(false);

        // Restore original scene root
        if (stage != null) {
            // Remove chartStack from wherever it currently is (fullscreenVBox inside overlay)
            // so it is available to be re-inserted into its original parent.
            if (chartStack != null && chartStack.getParent() != null
                    && chartStack.getParent() instanceof javafx.scene.layout.Pane fsParent) {
                fsParent.getChildren().remove(chartStack);
            } else if (fullscreenOverlay != null) {
                fullscreenOverlay.getChildren().remove(chartStack);
            }

            // Restore scene root (this brings the full layout back into the scene graph)
            stage.getScene().setRoot(fullscreenOriginalParent);

            // Re-insert chartStack into its exact original position
            restoreChartStackToParent();

            // Re-bind chart canvas back to chartPane (not the overlay)
            chart.widthProperty().unbind();
            chart.heightProperty().unbind();
            chart.widthProperty().bind(chartPane.widthProperty());
            chart.heightProperty().bind(chartPane.heightProperty());

            // Capture a local reference before nulling out the field (used in lambda below)
            final javafx.scene.Parent localOrigParent = fullscreenOriginalParent;

            // Force a layout pass + re-render so the chart is visible immediately
            javafx.application.Platform.runLater(() -> {
                if (localOrigParent != null) {
                    localOrigParent.layout();
                    // Ensure the BorderPane top (header bar) is still visible after restoring
                    if (localOrigParent instanceof javafx.scene.layout.BorderPane bp) {
                        javafx.scene.Node topNode = bp.getTop();
                        if (topNode != null) {
                            topNode.setVisible(true);
                            topNode.setManaged(true);
                        }
                    }
                }
//                chart.requestLayout();
                chart.render();
            });
        }
        fullscreenOverlay = null;
        fullscreenOriginalParent = null;
        fullscreenChartStackOriginalParent = null;
        fullscreenChartStackOriginalIndex  = -1;
    }

    private void restoreChartStackToParent() {
        if (chartStack == null) return;

        if (fullscreenChartStackOriginalParent instanceof BorderPane bp) {
            bp.setCenter(chartStack);
            return;
        }

        // ── Primary path: use the saved parent + index (most precise) ──────────
        if (fullscreenChartStackOriginalParent != null) {
            if (!fullscreenChartStackOriginalParent.getChildren().contains(chartStack)) {
                int idx = fullscreenChartStackOriginalIndex;
                int size = fullscreenChartStackOriginalParent.getChildren().size();
                if (idx >= 0 && idx <= size) {
                    fullscreenChartStackOriginalParent.getChildren().add(idx, chartStack);
                } else {
                    fullscreenChartStackOriginalParent.getChildren().add(chartStack);
                }
            }
            return;
        }

        // ── Fallback: walk up from chartPane to find the ChartView BorderPane ───
        Node node = chartPane;
        while (node != null) {
            if (node instanceof BorderPane bp) {
                bp.setCenter(chartStack);
                return;
            }
            node = node.getParent();
        }
    }

    /**
     * Builds the floating toolbar displayed during fullscreen mode.
     * Contains timeframe favorites + bars combo + exit fullscreen button.
     */
    private HBox buildFullscreenFloatingBar() {
        HBox bar = new HBox(8);
        bar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        bar.setMaxHeight(40);
        // Fix 1: The floating bar is placed at TOP_CENTER with a bottom margin (padding)
        // so there is a visible gap between the bar and the chart canvas below it.
        bar.setStyle(
                "-fx-background-color:#1c2128cc;"
                + "-fx-border-color:#30363d;"
                + "-fx-border-width:0 0 1 0;"
                + "-fx-padding:5 12;"
                + "-fx-background-radius:0;"
        );


        // Timeframe favorite buttons (mirrored from favTfBar)
        List<String> allowed   = authService.allowedTimeframes();
        List<String> favorites = authService.getFavoriteTimeframes();
        ToggleGroup tg = new ToggleGroup();
        for (String tf : favorites) {
            if (!allowed.contains(tf)) continue;
            ToggleButton btn = new ToggleButton(tf.toUpperCase());
            btn.setToggleGroup(tg);
            btn.setUserData(tf);
            boolean isActive = tf.equalsIgnoreCase(currentTimeframe);
            btn.setSelected(isActive);
            btn.setStyle(isActive
                    ? "-fx-background-color:#1f6feb;-fx-text-fill:white;-fx-background-radius:6;-fx-padding:4 8;"
                    : "-fx-background-color:#21262d;-fx-text-fill:#e6edf3;-fx-background-radius:6;-fx-padding:4 8;");
            btn.setOnAction(e -> {
                if (!btn.isSelected()) { btn.setSelected(true); return; }
                String selectedTf = (String) btn.getUserData();
                currentTimeframe = selectedTf;
                chart.setCurrentTimeframe(selectedTf);
                // Update button styles in floating bar
                for (Node n : bar.getChildren()) {
                    if (n instanceof ToggleButton tb) {
                        String t = (String) tb.getUserData();
                        if (t != null) {
                            tb.setStyle(t.equalsIgnoreCase(selectedTf)
                                    ? "-fx-background-color:#1f6feb;-fx-text-fill:white;-fx-background-radius:6;-fx-padding:4 8;"
                                    : "-fx-background-color:#21262d;-fx-text-fill:#e6edf3;-fx-background-radius:6;-fx-padding:4 8;");
                        }
                    }
                }
                refreshFavBarStyles();
                loadChart();
            });
            bar.getChildren().add(btn);
        }

        bar.getChildren().add(new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL));

        // Bars combo (read-only display + change)
        Label barsLabel = new Label("Bars:");
        barsLabel.setStyle("-fx-text-fill:#8b949e; -fx-font-size:12px;");
        ComboBox<Integer> floatingBarsCombo = new ComboBox<>();
        floatingBarsCombo.setItems(barsCombo.getItems());
        floatingBarsCombo.setValue(currentBars);
        floatingBarsCombo.setStyle("-fx-background-color:#21262d; -fx-text-fill:#e6edf3;");
        floatingBarsCombo.setPrefWidth(80);
        floatingBarsCombo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : String.valueOf(item));
            }
        });
        floatingBarsCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : String.valueOf(item));
            }
        });
        floatingBarsCombo.valueProperty().addListener((o, a, n) -> {
            if (n != null) {
                currentBars = Math.min(n, authService.maxCandles());
                if (chart != null) chart.setPreferredVisibleBars(currentBars);
                barsCombo.setValue(currentBars);
                loadChartWithTimeout();
            }
        });
        bar.getChildren().addAll(barsLabel, floatingBarsCombo);

        // Spacer
        Pane spacer = new Pane();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bar.getChildren().add(spacer);

        // Exit fullscreen button
        Button exitBtn = new Button("✕ Exit Fullscreen");
        exitBtn.setStyle("-fx-background-color:#30363d;-fx-text-fill:#e6edf3;-fx-background-radius:6;-fx-padding:4 10;-fx-cursor:hand;");
        exitBtn.setOnAction(e -> exitFullscreen());
        bar.getChildren().add(exitBtn);

        return bar;
    }

    private void setupKeyboardShortcuts() {
        // Use addEventHandler (not setOnKeyPressed) to avoid overwriting the
        // undo/redo / delete shortcuts already registered in CandlestickChartCanvas.setupKeyHandlers()
        chart.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            if (e.isConsumed()) return;  // already handled by the canvas
            if (e.isControlDown() || e.isMetaDown()) return; // Ctrl/Cmd combos handled in canvas
            switch (e.getCode()) {
                case T -> chart.setActiveDrawingTool(ChartDrawingToolType.TREND_LINE);
                case F -> chart.setActiveDrawingTool(ChartDrawingToolType.FIB_RETRACEMENT);
                case H -> chart.setActiveDrawingTool(ChartDrawingToolType.HORIZONTAL_LINE);
                case V -> chart.setActiveDrawingTool(ChartDrawingToolType.VERTICAL_LINE);
                case R -> chart.setActiveDrawingTool(ChartDrawingToolType.RECTANGLE);
                case ESCAPE -> {
                    if (isFullscreen) {
                        exitFullscreen();
                    } else {
                        chart.setActiveDrawingTool(ChartDrawingToolType.SELECT);
                    }
                }
                case F11 -> {
                    onToggleFullscreen();
                    e.consume();
                }
                default -> {}
            }
        });
    }

    /** Returns the currently displayed symbol. */
    public String getCurrentSymbol() { return currentSymbol; }

    /** Called when the Live Chart view becomes visible. */
    public void onChartActivated() {
        chartSessionActive = true;
        chartLiveSession.activate(currentSymbol, currentTimeframe);
        notifyChartContext();
        startChartRefreshTimer();
        if (activeProfile != null) {
            loadChart();
        }
    }

    /** Called when the user navigates away from the chart. */
    public void onChartDeactivated() {
        chartSessionActive = false;
        chartLiveSession.deactivate();
        stopChartRefreshTimer();
    }

    private void notifyChartContext() {
        marketDataSyncScheduler.notifyChartContextChanged(currentSymbol, currentTimeframe);
    }

    private void startChartRefreshTimer() {
        stopChartRefreshTimer();
        long pollSec = chartLiveSession.pollInterval().getSeconds();
        chartRefreshTimeline = new Timeline(new KeyFrame(
                Duration.seconds(Math.max(15, pollSec)),
                e -> {
                    if (!chartSessionActive || activeProfile == null) return;
                    if (chartLiveSession.isCacheStale()) {
                        log.debug("Chart auto-refresh {}/{}", currentSymbol, currentTimeframe);
                        loadChart();
                    }
                }));
        chartRefreshTimeline.setCycleCount(Timeline.INDEFINITE);
        chartRefreshTimeline.play();
    }

    private void stopChartRefreshTimer() {
        if (chartRefreshTimeline != null) {
            chartRefreshTimeline.stop();
            chartRefreshTimeline = null;
        }
    }

    private void updateChartSessionContext() {
        if (chartSessionActive) {
            chartLiveSession.updateContext(currentSymbol, currentTimeframe);
            notifyChartContext();
            startChartRefreshTimer();
        }
    }

    private void wireDrawingEngine() {
        var engine = chart.getDrawingEngine();
        engine.setOnDrawingCreated(d -> {
            // Issue 7.2: apply current profile's default settings to the new drawing
            applyDrawingDefaults(d);
            persistDrawing(d);
            refreshUndoRedoState();
        });
        engine.setOnDrawingUpdated(d -> {
            persistDrawing(d);
            refreshUndoRedoState();
        });
        engine.setOnDrawingDeleted(d -> {
            if (d.getId() != null) {
                Thread.ofVirtual().start(() -> chartDrawingService.delete(d.getId()));
            }
            refreshUndoRedoState();
        });
        engine.setOnHistoryRestored(d -> {
            if (d != null) persistDrawing(d);
            refreshUndoRedoState();
        });
        engine.setOnCreateTradeFromDrawing(d -> {
            // Capture screenshot before opening trade form
            String screenshotPath = captureChartScreenshot(currentSymbol);
            TradeDrawingDraft draft = buildTradeDraft(d, screenshotPath);
            if (draft != null && onCreateTradeFromDrawing != null)
                onCreateTradeFromDrawing.accept(draft);
        });
        engine.setOnInstantSaveTrade(d -> {
            // Capture screenshot for instant save
            String screenshotPath = captureChartScreenshot(currentSymbol);
            TradeDrawingDraft draft = buildTradeDraft(d, screenshotPath);
            if (draft != null && onInstantSaveTradeFromDrawing != null)
                onInstantSaveTradeFromDrawing.accept(draft);
        });

        // Per-drawing properties dialog
        engine.setOnOpenProperties(d -> {
            DrawingPropertiesDialog.show(
                    d,
                    chart.getScene() != null ? chart.getScene().getWindow() : null,
                    updated -> {
                        persistDrawing(updated);
                        chart.getDrawingEngine().requestRender();
                    });
        });
    }

    /**
     * Issue 7.2: applies the current profile's default drawing settings to a
     * newly created drawing BEFORE it is persisted.
     *
     * <p>Only sets the property if the drawing is still using the hard-coded
     * per-type default (i.e. the user hasn't already customised it in the
     * current interaction).  Position-tool colours are left unchanged so the
     * green/red entry/SL/TP visual language is preserved.
     */
    private void applyDrawingDefaults(ChartDrawing d) {
        if (d == null || drawingSettings == null) return;
        ChartDrawingProperties props = d.getProperties();
        if (props == null) return;
        ChartDrawingToolType type = d.getToolType();

        // Position tools keep their semantic colours (green entry, red SL, blue TP)
        if (type != null && type.isPositionTool()) return;

        // Choose the appropriate default colour based on tool category
        String defaultColor;
        if (type == ChartDrawingToolType.FIB_RETRACEMENT
                || type == ChartDrawingToolType.FIB_EXTENSION
                || type == ChartDrawingToolType.FIB_FAN
                || type == ChartDrawingToolType.FIB_CHANNEL
                || type == ChartDrawingToolType.FIB_TIME_ZONES
                || type == ChartDrawingToolType.FIB_SPEED_RESISTANCE) {
            defaultColor = drawingSettings.getDefaultFibColor() != null
                    ? drawingSettings.getDefaultFibColor() : "#d29922";
        } else if (type == ChartDrawingToolType.NOTE_ICON) {
            defaultColor = drawingSettings.getDefaultAnnotationColor() != null
                    ? drawingSettings.getDefaultAnnotationColor() : "#d29922";
        } else if (type == ChartDrawingToolType.TEXT_LABEL
                || type == ChartDrawingToolType.CALLOUT) {
            defaultColor = drawingSettings.getDefaultAnnotationColor() != null
                    ? drawingSettings.getDefaultAnnotationColor() : "#e6edf3";
        } else if (type == ChartDrawingToolType.RECTANGLE
                || type == ChartDrawingToolType.ELLIPSE
                || type == ChartDrawingToolType.TRIANGLE_PATTERN
                || type == ChartDrawingToolType.FLAT_CHANNEL
                || type == ChartDrawingToolType.PARALLEL_CHANNEL
                || type == ChartDrawingToolType.XABCD_PATTERN
                || type == ChartDrawingToolType.CYPHER_PATTERN
                || type == ChartDrawingToolType.HEAD_AND_SHOULDERS
                || type == ChartDrawingToolType.ABCD_PATTERN
                || type == ChartDrawingToolType.THREE_DRIVES_PATTERN) {
            defaultColor = drawingSettings.getDefaultShapeColor() != null
                    ? drawingSettings.getDefaultShapeColor() : "#58a6ff";
        } else {
            defaultColor = drawingSettings.getDefaultLineColor() != null
                    ? drawingSettings.getDefaultLineColor() : "#58a6ff";
        }

        // Apply defaults (colour, width, style, fill)
        if (defaultColor != null) props.setColor(defaultColor);
        if (drawingSettings.getDefaultLineWidth() > 0) {
            props.setLineWidth(drawingSettings.getDefaultLineWidth());
        }
        if (drawingSettings.getDefaultLineStyle() != null
                && !drawingSettings.getDefaultLineStyle().isBlank()) {
            props.setLineStyle(drawingSettings.getDefaultLineStyle());
        }
        if (drawingSettings.getDefaultFillOpacity() >= 0) {
            props.setFillOpacity(drawingSettings.getDefaultFillOpacity());
        }
    }

    /** Refreshes the undo/redo button enabled state in the toolbar. */
    private void refreshUndoRedoState() {
        if (drawingToolbarPane == null) return;
        DrawingToolbar toolbar = drawingToolbarPane.getToolbar();
        Platform.runLater(() -> {
            var history = chart.getDrawingEngine().getHistory();
            toolbar.updateUndoRedoState(history.canUndo(), history.canRedo());
        });
    }

    public void setOnCreateTradeFromDrawing(Consumer<TradeDrawingDraft> callback) {
        this.onCreateTradeFromDrawing = callback;
    }

    public void setOnInstantSaveTradeFromDrawing(Consumer<TradeDrawingDraft> callback) {
        this.onInstantSaveTradeFromDrawing = callback;
    }

    private void persistDrawing(ChartDrawing drawing) {
        if (activeProfile == null) return;
        drawing.setProfile(activeProfile);
        drawing.setSymbol(currentSymbol);
        drawing.setTimeframe(currentTimeframe);
        Thread.ofVirtual().start(() -> {
            ChartDrawing saved = chartDrawingService.save(drawing);
            Platform.runLater(() -> {
                if (drawing.getId() == null) drawing.setId(saved.getId());
            });
        });
    }

    private void loadDrawingsForChart() {
        if (activeProfile == null) return;
        Thread.ofVirtual().start(() -> {
            List<ChartDrawing> list = chartDrawingService.loadDrawings(
                    activeProfile, currentSymbol, currentTimeframe);
            Platform.runLater(() -> chart.setDrawings(list));
        });
    }

    private TradeDrawingDraft buildTradeDraft(ChartDrawing d) {
        return buildTradeDraft(d, null);
    }

    private TradeDrawingDraft buildTradeDraft(ChartDrawing d, String screenshotPath) {
        if (d == null || !d.getToolType().isPositionTool()) return null;
        ChartDrawingProperties props = d.getProperties();
        if (props == null || props.getEntryPrice() == null) return null;
        TradeDirection dir = d.getToolType() == ChartDrawingToolType.LONG_POSITION
                ? TradeDirection.LONG : TradeDirection.SHORT;
        Trade.AssetType assetType = mapAssetClassToTradeType(
                AssetClassDetector.detect(currentSymbol));
        return new TradeDrawingDraft(
                currentSymbol,
                dir,
                BigDecimal.valueOf(props.getEntryPrice()),
                props.getStopLoss()   != null ? BigDecimal.valueOf(props.getStopLoss())   : null,
                props.getTakeProfit() != null ? BigDecimal.valueOf(props.getTakeProfit()) : null,
                assetType,
                screenshotPath
        );
    }

    private static Trade.AssetType mapAssetClassToTradeType(AssetClass ac) {
        if (ac == null) return Trade.AssetType.CRYPTO;
        return switch (ac) {
            case CRYPTO -> Trade.AssetType.CRYPTO;
            case STOCK -> Trade.AssetType.STOCK;
            case FOREX -> Trade.AssetType.FOREX;
            case COMMODITY -> Trade.AssetType.COMMODITY;
            case INDEX -> Trade.AssetType.INDEX;
        };
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

    /**
     * Apply zoom and pan sensitivity values from Settings to the chart canvas.
     * @param zoomSensitivity 0.1–1.0 (lower = gentler zoom)
     * @param panSensitivity  0.1–1.0 (lower = slower pan)
     */
    public void applySensitivity(double zoomSensitivity, double panSensitivity) {
        if (chart != null) {
            chart.setZoomSensitivity(zoomSensitivity);
            chart.setPanSensitivity(panSensitivity);
        }
    }

    public void prepareView() {
        applyTimeframeAccess();
        applyViewMode();
        if (symbolInput.getText() == null || symbolInput.getText().isBlank())
            symbolInput.setText(currentSymbol);
        else
            currentSymbol = symbolInput.getText().trim().toUpperCase();
        if (symbolCombo.getValue() == null) symbolCombo.setValue(currentSymbol);
        refreshProviderCombo();
        if (activeProfile != null) {
            onChartActivated();
        }
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
        // Issue 7.1: reload per-profile drawing settings whenever profile switches
        loadDrawingSettings();
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
                syncSymbolComboToCurrentSymbol();  // Feature 3
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
                        syncSymbolComboToCurrentSymbol();  // Feature 3
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
     * Favorites are displayed in canonical timeframe order (1m → 3m → … → 1mo).
     * Shows a hint label if no favorites are selected yet.
     */
    private void rebuildFavoritesBar(List<String> allowed, List<String> favorites) {
        if (favTfBar == null) return;
        favTfBar.getChildren().clear();
        // Reset to compact/natural width so the bar shrinks when TFs are removed.
        // Without this, JavaFX keeps the previous width, leaving an empty gap.
        favTfBar.setPrefWidth(javafx.scene.layout.Region.USE_COMPUTED_SIZE);
        favTfBar.setMinWidth(javafx.scene.layout.Region.USE_COMPUTED_SIZE);
        favTfBar.setMaxWidth(javafx.scene.layout.Region.USE_COMPUTED_SIZE);

        if (favorites.isEmpty()) {
            // No favorites: show a hint label
            Label hint = new Label("★ No favorites — click ☆ TF to add");
            hint.setStyle("-fx-text-fill:#484f58; -fx-font-size:11px; -fx-padding:2 4;");
            favTfBar.getChildren().add(hint);
            return;
        }

        // Sort favorites by their position in the canonical ALL_TIMEFRAMES list so
        // they always appear in logical order (1m → 5m → 1h → 4h → 1D …)
        List<String> sortedFavorites = favorites.stream()
                .sorted(Comparator.comparingInt(tf -> {
                    int idx = ALL_TIMEFRAMES.indexOf(tf.toLowerCase());
                    return idx < 0 ? Integer.MAX_VALUE : idx;
                }))
                .collect(Collectors.toList());

        ToggleGroup tg = new ToggleGroup();
        for (String tf : sortedFavorites) {
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
            // Fix 2: Invalidate the in-memory cache so the new provider fetches fresh data
            ohlcvStorageService.invalidateCache(currentSymbol, currentTimeframe);
            ohlcvStorageService.refreshBars(currentSymbol, currentTimeframe, currentBars, activeProfile);
            loadChart();
        });
    }

    @FXML public void onLoadChart() {
        String sym = symbolInput.getText().trim().toUpperCase();
        if (!sym.isEmpty()) currentSymbol = sym;
        // Notify the sync scheduler so it only keeps this symbol's data fresh
        marketDataSyncScheduler.notifyChartContextChanged(currentSymbol, currentTimeframe);
        updateChartSessionContext();
        refreshProviderCombo();
        // Feature 3: sync the symbol combo box to the loaded symbol immediately so the
        // combo box always reflects what is currently displayed on the chart.
        syncSymbolComboToCurrentSymbol();
        loadChart();
    }

    /**
     * Feature 3: Ensures the symbol combo-box selected value matches {@link #currentSymbol}.
     *
     * <p>If the symbol is not yet in the combo's item list (e.g. a custom symbol typed
     * in the search field), it is inserted at position 0 so it is always visible and
     * selectable by the user.
     */
    private void syncSymbolComboToCurrentSymbol() {
        if (symbolCombo == null || currentSymbol == null || currentSymbol.isBlank()) return;
        // Already in sync — skip to avoid triggering the value-change listener needlessly
        if (currentSymbol.equalsIgnoreCase(symbolCombo.getValue())) return;

        if (!symbolCombo.getItems().contains(currentSymbol)) {
            // Insert the custom symbol at the top of the list so the user can re-select it later
            symbolCombo.getItems().add(0, currentSymbol);
        }
        symbolCombo.setValue(currentSymbol);
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
                    loadDrawingsForChart();

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
                    chartLiveSession.recordLoaded();
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

    private void showErrorNotification(String message) {
        if (toastManager != null) toastManager.showError(message);
        else log.warn("Notification: {}", message);
    }

    private void showInfoNotification(String message) {
        if (toastManager != null) toastManager.showInfo(message);
        else log.info("Notification: {}", message);
    }

    private void showWarnNotification(String message) {
        if (toastManager != null) toastManager.showWarning(message);
        else log.warn("Notification: {}", message);
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
        updateChartSessionContext();
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

    /**
     * T-5: All toolbar buttons use at least 44px height to meet touch-friendly target sizes.
     */
    private String activeStyle() {
        return "-fx-background-color:#1f6feb;-fx-text-fill:white;"
                + "-fx-background-radius:6;-fx-padding:4 10;"
                + "-fx-min-height:44;-fx-cursor:hand;";
    }

    /** Style for favorited timeframes — gold tint; touch-friendly height. */
    private String favoriteStyle() {
        return "-fx-background-color:#b08800;-fx-text-fill:white;"
                + "-fx-background-radius:6;-fx-padding:4 10;"
                + "-fx-min-height:44;-fx-cursor:hand;";
    }

    private String inactiveStyle() {
        return "-fx-background-color:#21262d;-fx-text-fill:#e6edf3;"
                + "-fx-background-radius:6;-fx-padding:4 10;"
                + "-fx-min-height:44;-fx-cursor:hand;";
    }

    private String fmtPrice(double p) {
        if (p >= 1000) return String.format("%.2f", p);
        if (p >= 1)    return String.format("%.4f", p);
        return String.format("%.8f", p);
    }
}
