package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.model.UserProfile;
import com.mst.matt.tradingplatformapp.model.UserProfile.ProfileAssetFocus;
import com.mst.matt.tradingplatformapp.model.AppUser;
import com.mst.matt.tradingplatformapp.model.IndicatorConfig;
import com.mst.matt.tradingplatformapp.repository.IndicatorConfigRepository;
import com.mst.matt.tradingplatformapp.repository.UserProfileRepository;
import com.mst.matt.tradingplatformapp.service.analysis.AnalysisService;
import com.mst.matt.tradingplatformapp.service.alert.AlertService;
import com.mst.matt.tradingplatformapp.service.auth.AuthService;
import com.mst.matt.tradingplatformapp.service.WatchlistDefaults;
import com.mst.matt.tradingplatformapp.service.price.LiveTickerService;
import com.mst.matt.tradingplatformapp.service.price.PriceQuote;
import com.mst.matt.tradingplatformapp.service.price.PriceRouter;
import com.mst.matt.tradingplatformapp.ui.ScrollingTickerPane;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Popup;
import net.rgielen.fxweaver.core.FxWeaver;
import net.rgielen.fxweaver.core.FxmlView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.ResourceBundle;

@Component
@FxmlView("/fxml/MainDashboard.fxml")
public class MainDashboardController implements Initializable {

    @FXML private ComboBox<UserProfile> profileSelector;
    @FXML private StackPane tickerContainer;
    @FXML private VBox sidebar;
    @FXML private Button watchlistMenuBtn;
    @FXML private StackPane contentArea;
    @FXML private Label  statusLabel;
    @FXML private Label  lastUpdateLabel;
    @FXML private Label  alertCountBadge;
    @FXML private Label  versionLabel;
    @FXML private Button alertBellBtn;
    @FXML private Button navDashboard, navChart, navTrades,
            navAnalysis, navAlerts, navMixer,
            navPortfolio, navFundamentals, navExport, navSettings;

    @Autowired private FxWeaver              fxWeaver;
    @Autowired private UserProfileRepository profileRepository;
    @Autowired private IndicatorConfigRepository indicatorConfigRepository;
    @Autowired private LiveTickerService     liveTickerService;
    @Autowired private AlertService          alertService;
    @Autowired private AnalysisService       analysisService;
    @Autowired private PriceRouter           priceRouter;
    @Autowired private AuthService           authService;

    private Parent dashboardView, chartView, tradeEntryView,
            alertsView, mixerView, exportView, fundamentalsView, settingsView, adminView;

    /** Callback invoked on logout — provided by StageInitializer. */
    private Runnable onLogout;

    private DashboardController              dashboardCtrl;
    private ChartController                  chartCtrl;
    private TradeEntryController             tradeEntryCtrl;
    private AlertManagerController           alertsCtrl;
    private IndicatorMixerController         mixerCtrl;
    private ExportController                 exportCtrl;
    private YearlyProfitController           yearlyProfitCtrl;
    private ProfileSettingsController        profileSettingsCtrl;
    private AdminUserManagementController    adminCtrl;

    private UserProfile activeProfile;
    private ScrollingTickerPane scrollingTicker;
    private Popup watchlistPopup;
    private PauseTransition sidebarCollapseTimer;

    /** Called by StageInitializer after login; wire logout callback. */
    public void setOnLogout(Runnable r) { this.onLogout = r; }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupNavTooltips();
        setupProfileSelector();
        loadOrCreateDefaultProfiles();
        setupScrollingTicker();
        setupLiveTicker();
        updateLastUpdateLabel();
        Platform.runLater(() -> {
            setSidebarExpanded(false);
            applyRoleNavVisibility();
        });
    }

    /**
     * Feature 4.2: Show/hide nav buttons based on the current user's tab permissions.
     * Called on initialize and after login.
     */
    private void applyRoleNavVisibility() {
        setNavVisible(navChart,        authService.canSeeTab("CHART"));
        setNavVisible(navAnalysis,     authService.canSeeTab("ANALYZE"));
        setNavVisible(navPortfolio,    authService.canSeeTab("PORTFOLIO"));
        setNavVisible(navTrades,       authService.canSeeTab("TRADE_JOURNAL"));
        setNavVisible(navMixer,        authService.canSeeTab("INDICATOR_MIXER"));
        setNavVisible(navAlerts,       authService.canSeeTab("ALERTS"));
        setNavVisible(navExport,       authService.canSeeTab("EXPORT"));
        setNavVisible(navFundamentals, authService.canSeeTab("FUNDAMENTALS"));
        // Settings always visible; admin panel shown only to ADMIN
        if (navSettings != null) { navSettings.setVisible(true); navSettings.setManaged(true); }
    }

    private void setNavVisible(Button btn, boolean visible) {
        if (btn == null) return;
        btn.setVisible(visible);
        btn.setManaged(visible);
    }

    private void setupNavTooltips() {
        setNavTip(navDashboard, "Dashboard");
        setNavTip(navChart, "Live Chart");
        setNavTip(navTrades, "Trade Journal");
        setNavTip(navAnalysis, "Analysis");
        setNavTip(navAlerts, "Alerts");
        setNavTip(navMixer, "Indicator Mixer");
        setNavTip(navPortfolio, "Portfolio");
        setNavTip(navFundamentals, "Yearly Profit");
        setNavTip(navExport, "Export Excel");
        setNavTip(navSettings, "Settings");
        if (watchlistMenuBtn != null) {
            watchlistMenuBtn.setTooltip(new Tooltip("Watchlist"));
        }
    }

    private static final double SIDEBAR_WIDTH_EXPANDED = 220;
    private static final double SIDEBAR_WIDTH_COLLAPSED = 56;
    private static final double NAV_BTN_WIDTH_EXPANDED = 188;
    private static final double NAV_BTN_WIDTH_COLLAPSED = 44;

    private void setNavTip(Button btn, String label) {
        if (btn == null) return;
        // Preserve the emoji/icon from the FXML text attribute before overwriting it
        String icon = btn.getText() == null ? "" : btn.getText().trim();
        Label iconLabel = new Label(icon);
        iconLabel.getStyleClass().add("nav-icon");
        // Ensure the icon label itself is always visible (non-managed = ok, but keep visible)
        iconLabel.setVisible(true);
        iconLabel.setStyle("-fx-font-size:18px;");
        btn.setGraphic(iconLabel);
        btn.setText(label);
        // Store both label and icon for later use by expandNavLabels
        btn.getProperties().put("navLabel", label);
        btn.getProperties().put("navIcon", icon);
        Tooltip tip = new Tooltip(label);
        tip.setShowDelay(Duration.millis(200));
        tip.setHideDelay(Duration.seconds(4));
        btn.setTooltip(tip);
        btn.setUserData(label);
    }

    @FXML private void onSidebarEnter() {
        if (sidebarCollapseTimer != null) sidebarCollapseTimer.stop();
        setSidebarExpanded(true);
    }

    @FXML private void onSidebarExit() {
        if (sidebarCollapseTimer != null) sidebarCollapseTimer.stop();
        sidebarCollapseTimer = new PauseTransition(Duration.seconds(2));
        sidebarCollapseTimer.setOnFinished(e -> Platform.runLater(() -> setSidebarExpanded(false)));
        sidebarCollapseTimer.play();
    }

    private void setSidebarExpanded(boolean expanded) {
        if (sidebar == null) return;
        if (expanded) {
            sidebar.getStyleClass().remove("sidebar-collapsed");
            sidebar.getStyleClass().add("sidebar-expanded");
        } else {
            sidebar.getStyleClass().remove("sidebar-expanded");
            if (!sidebar.getStyleClass().contains("sidebar-collapsed")) {
                sidebar.getStyleClass().add("sidebar-collapsed");
            }
        }
        double sideW = expanded ? SIDEBAR_WIDTH_EXPANDED : SIDEBAR_WIDTH_COLLAPSED;
        double btnW  = expanded ? NAV_BTN_WIDTH_EXPANDED : NAV_BTN_WIDTH_COLLAPSED;
        sidebar.setPrefWidth(sideW);
        sidebar.setMinWidth(sideW);
        sidebar.setMaxWidth(sideW);
        expandNavLabels(expanded, btnW);
        sidebar.requestLayout();
    }

    private void expandNavLabels(boolean expanded, double btnWidth) {
        for (Button b : List.of(navDashboard, navChart, navTrades, navAnalysis,
                navAlerts, navMixer, navPortfolio, navFundamentals, navExport, navSettings)) {
            if (b == null) continue;
            String label = (String) b.getProperties().get("navLabel");
            if (label == null) label = (String) b.getUserData();
            String icon  = (String) b.getProperties().get("navIcon");

            if (expanded) {
                // Show icon (graphic) + text label side by side
                b.setText(label != null ? label : "");
                b.setContentDisplay(ContentDisplay.LEFT);
                b.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                b.setGraphicTextGap(8);
            } else {
                // Collapsed: show ONLY the icon graphic, no text
                b.setText("");
                b.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                b.setAlignment(javafx.geometry.Pos.CENTER);
                // Ensure graphic label still has icon text
                if (b.getGraphic() instanceof Label iconLabel) {
                    if (icon != null && !icon.isBlank()) {
                        iconLabel.setText(icon);
                    }
                    iconLabel.setVisible(true);
                }
            }

            b.setPrefWidth(btnWidth);
            b.setMinWidth(btnWidth);
            b.setMaxWidth(btnWidth);
        }
    }

    private void setupScrollingTicker() {
        scrollingTicker = new ScrollingTickerPane();
        StackPane.setAlignment(scrollingTicker, javafx.geometry.Pos.CENTER_LEFT);
        scrollingTicker.prefWidthProperty().bind(tickerContainer.widthProperty());
        scrollingTicker.maxWidthProperty().bind(tickerContainer.widthProperty());
        tickerContainer.getChildren().add(scrollingTicker);
    }

    private void setupProfileSelector() {
        profileSelector.setCellFactory(lv -> profileCell());
        profileSelector.setButtonCell(profileCell());
        profileSelector.valueProperty().addListener((obs, old, newP) -> {
            if (newP != null) switchProfile(newP);
        });
    }

    private ListCell<UserProfile> profileCell() {
        return new ListCell<>() {
            @Override protected void updateItem(UserProfile p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) { setText(null); return; }
                setText("● " + p.getName());
                setStyle("-fx-text-fill:#e6edf3;");
            }
        };
    }

    private void loadOrCreateDefaultProfiles() {
        List<UserProfile> profiles =
                profileRepository.findAllByOrderByLastAccessedAtDesc();
        if (profiles.isEmpty()) {
            profiles = List.of(
                    createProfile("Crypto Portfolio", "#3fb950", ProfileAssetFocus.CRYPTO),
                    createProfile("Stocks Journal",   "#388bfd", ProfileAssetFocus.STOCK),
                    createProfile("Forex Trading",    "#bc8cff", ProfileAssetFocus.FOREX)
            );
        }
        profileSelector.setItems(FXCollections.observableArrayList(profiles));
        UserProfile active = profiles.stream()
                .filter(UserProfile::isActive).findFirst()
                .orElse(profiles.get(0));
        profileSelector.setValue(active);
    }

    private UserProfile createProfile(String name, String color, ProfileAssetFocus focus) {
        UserProfile p = UserProfile.builder()
                .name(name).avatarColor(color).active(false)
                .assetFocus(focus)
                .defaultSymbol(focus.defaultSymbol())
                .watchlist(WatchlistDefaults.csvForFocus(focus))
                .chartProvider("AUTO")
                .fundamentalProvider("AUTO")
                .createdAt(LocalDateTime.now())
                .lastAccessedAt(LocalDateTime.now()).build();
        p = profileRepository.save(p);
        IndicatorConfig cfg = IndicatorConfig.fromProfile(
                IndicatorConfig.IndicatorProfile.SWING_TRADING, p);
        indicatorConfigRepository.save(cfg);
        return p;
    }

    private UserProfile createProfile(String name, String color) {
        return createProfile(name, color, ProfileAssetFocus.MULTI);
    }

    private void switchProfile(UserProfile profile) {
        profileRepository.findAll().forEach(p -> {
            boolean selected = p.getId().equals(profile.getId());
            p.setActive(selected);
            if (selected) p.setLastAccessedAt(LocalDateTime.now());
            profileRepository.save(p);
        });
        activeProfile = profile;
        analysisService.setActiveProfile(profile);
        priceRouter.setActiveProfile(profile);

        if (liveTickerService != null) {
            liveTickerService.applyProfileWatchlist(profile);
            if (scrollingTicker != null) {
                scrollingTicker.setSymbols(liveTickerService.allSymbols());
            }
        }

        if (dashboardCtrl != null)  dashboardCtrl.loadProfile(profile);
        if (chartCtrl      != null) chartCtrl.setProfile(profile);
        if (tradeEntryCtrl != null) tradeEntryCtrl.setProfile(profile);
        if (alertsCtrl     != null) alertsCtrl.setProfile(profile);
        if (mixerCtrl      != null) mixerCtrl.setProfile(profile);
        if (exportCtrl     != null) exportCtrl.setProfile(profile);
        if (yearlyProfitCtrl != null) yearlyProfitCtrl.setProfile(profile);
        if (profileSettingsCtrl != null) profileSettingsCtrl.setProfile(profile);

        refreshAlertBadge();
        updateStatusBar("Profile: " + profile.getName());
        Platform.runLater(this::onNavDashboard);
    }

    private void refreshAlertBadge() {
        if (activeProfile == null) return;
        long count = alertService.getAlertsForProfile(activeProfile)
                .stream().filter(a -> a.isTriggered() && a.isActive())
                .count();
        Platform.runLater(() -> {
            alertCountBadge.setText(String.valueOf(count));
            alertCountBadge.setVisible(count > 0);
        });
    }

    private void setupLiveTicker() {
        liveTickerService.addTickerListener(this::onTickerUpdate);
    }

    private void onTickerUpdate(PriceQuote quote) {
        Platform.runLater(() -> {
            if (scrollingTicker == null || quote.getSymbol() == null
                    || quote.getPrice() == null) return;
            if (activeProfile != null && !liveTickerService.allSymbols()
                    .contains(quote.getSymbol().toUpperCase())) {
                return;
            }
            String sym = quote.getSymbol();
            String price = "$" + quote.getPrice()
                    .setScale(quote.getPrice().intValue() >= 10 ? 2 : 4,
                            RoundingMode.HALF_UP).toPlainString();
            String change = quote.getChangePct24h() != null
                    ? (quote.isUp() ? " ▲ +" : " ▼ ")
                    + quote.getChangePct24h()
                    .setScale(2, RoundingMode.HALF_UP).toPlainString() + "%"
                    : "";
            scrollingTicker.updateQuote(sym, sym + "  " + price + change, quote.isUp());
            updateLastUpdateLabel();
        });
    }

    @FXML public void onToggleWatchlistMenu() {
        if (watchlistMenuBtn == null) return;
        // Toggle: close if already showing
        if (watchlistPopup != null && watchlistPopup.isShowing()) {
            watchlistPopup.hide();
            watchlistPopup = null;
            return;
        }

        // ── Build watchlist popup ────────────────────────────────────
        VBox box = new VBox(10);
        box.setStyle("-fx-background-color:#161b22; -fx-padding:14; -fx-border-color:#30363d;"
                + "-fx-border-width:1; -fx-background-radius:8; -fx-border-radius:8;");
        box.setPrefWidth(340);

        // Header
        Label title = new Label("📋 Watchlist — " +
                (activeProfile != null ? activeProfile.getName() : "No profile"));
        title.setStyle("-fx-font-weight:bold; -fx-font-size:14px; -fx-text-fill:#e6edf3;");

        Label hint = new Label("Profile watchlist (comma-separated). Changes saved automatically.");
        hint.setStyle("-fx-font-size:11px; -fx-text-fill:#8b949e;");
        hint.setWrapText(true);

        // Get current watchlist CSV
        String currentWl = (activeProfile != null && activeProfile.getWatchlist() != null
                && !activeProfile.getWatchlist().isBlank())
                ? activeProfile.getWatchlist()
                : (activeProfile != null
                    ? WatchlistDefaults.csvForFocus(activeProfile.getAssetFocus()) : "");

        // Add-symbol row
        HBox addRow = new HBox(6);
        TextField addField = new TextField();
        addField.setPromptText("Add symbol (e.g. NVDA, BTCUSDT)");
        addField.setStyle("-fx-background-color:#21262d; -fx-text-fill:#e6edf3; -fx-border-color:#30363d;");
        HBox.setHgrow(addField, Priority.ALWAYS);
        Button addBtn = new Button("+ Add");
        addBtn.getStyleClass().add("btn-primary");
        addRow.getChildren().addAll(addField, addBtn);

        // Current watchlist as chip-like labels
        FlowPane chips = new FlowPane(6, 6);
        chips.setStyle("-fx-padding:4;");

        // Editable text area (full CSV edit)
        TextArea editor = new TextArea(currentWl);
        editor.setPrefRowCount(3);
        editor.setWrapText(true);
        editor.setStyle("-fx-background-color:#21262d; -fx-text-fill:#e6edf3; -fx-border-color:#30363d; -fx-font-size:11px;");

        // Rebuild chips from editor text
        Runnable rebuildChips = () -> {
            chips.getChildren().clear();
            String csv = editor.getText() == null ? "" : editor.getText();
            String[] syms = csv.split("[,\\s]+");
            for (String s : syms) {
                String sym = s.trim().toUpperCase();
                if (sym.isEmpty()) continue;
                Label chip = new Label(sym + " ✕");
                chip.setStyle("-fx-background-color:#21262d; -fx-text-fill:#e6edf3;"
                        + "-fx-border-color:#30363d; -fx-border-radius:4; -fx-background-radius:4;"
                        + "-fx-padding:3 8; -fx-cursor:hand; -fx-font-size:11px;");
                chip.setOnMouseClicked(ev -> {
                    // Remove this symbol from the CSV
                    String cur = editor.getText() == null ? "" : editor.getText();
                    String updated = Arrays.stream(cur.split("[,\\s]+"))
                            .map(String::trim)
                            .filter(x -> !x.equalsIgnoreCase(sym))
                            .collect(Collectors.joining(", "));
                    editor.setText(updated);
                });
                chips.getChildren().add(chip);
            }
        };
        rebuildChips.run();
        editor.textProperty().addListener((o, a, b) -> rebuildChips.run());

        // Add button handler
        addBtn.setOnAction(e -> {
            String newSym = addField.getText() == null ? "" : addField.getText().trim().toUpperCase();
            if (newSym.isEmpty()) return;
            String cur = editor.getText() == null ? "" : editor.getText().trim();
            // Avoid duplicates
            boolean alreadyIn = Arrays.stream(cur.split("[,\\s]+"))
                    .anyMatch(x -> x.trim().equalsIgnoreCase(newSym));
            if (!alreadyIn) {
                editor.setText(cur.isEmpty() ? newSym : cur + ", " + newSym);
            }
            addField.clear();
        });
        addField.setOnAction(e -> addBtn.fire());

        // Default presets for this profile
        Label presetsLabel = new Label("Quick add for this profile:");
        presetsLabel.setStyle("-fx-font-size:11px; -fx-text-fill:#8b949e;");
        FlowPane presets = new FlowPane(6, 4);
        if (activeProfile != null) {
            List<String> defaults = WatchlistDefaults.forFocus(activeProfile.getAssetFocus());
            for (String sym : defaults) {
                Button preset = new Button(sym);
                preset.setStyle("-fx-background-color:#21262d; -fx-text-fill:#8b949e;"
                        + "-fx-border-color:#30363d; -fx-border-radius:4; -fx-background-radius:4;"
                        + "-fx-padding:2 8; -fx-cursor:hand; -fx-font-size:10px;");
                preset.setOnAction(e -> {
                    String cur = editor.getText() == null ? "" : editor.getText().trim();
                    boolean already = Arrays.stream(cur.split("[,\\s]+"))
                            .anyMatch(x -> x.trim().equalsIgnoreCase(sym));
                    if (!already) {
                        editor.setText(cur.isEmpty() ? sym : cur + ", " + sym);
                    }
                });
                presets.getChildren().add(preset);
            }
        }

        // Apply button
        Button applyBtn = new Button("✔ Apply & Close");
        applyBtn.getStyleClass().add("btn-primary");
        applyBtn.setMaxWidth(Double.MAX_VALUE);
        applyBtn.setOnAction(e -> {
            if (activeProfile == null) return;
            String csv = editor.getText() == null ? "" : editor.getText().trim();
            // Normalize
            String normalized = Arrays.stream(csv.split("[,\\s]+"))
                    .map(String::trim)
                    .filter(x -> !x.isEmpty())
                    .map(String::toUpperCase)
                    .distinct()
                    .collect(Collectors.joining(", "));
            activeProfile.setWatchlist(normalized.isEmpty() ? null : normalized);
            profileRepository.save(activeProfile);
            liveTickerService.applyProfileWatchlist(activeProfile);
            if (scrollingTicker != null)
                scrollingTicker.setSymbols(liveTickerService.allSymbols());
            if (watchlistPopup != null) {
                watchlistPopup.hide();
                watchlistPopup = null;
            }
        });

        Separator sep1 = new Separator();
        sep1.setStyle("-fx-background-color:#30363d;");
        Separator sep2 = new Separator();
        sep2.setStyle("-fx-background-color:#30363d;");

        box.getChildren().addAll(title, hint, addRow, chips, sep1,
                presetsLabel, presets, sep2, editor, applyBtn);

        watchlistPopup = new Popup();
        watchlistPopup.getContent().add(box);
        watchlistPopup.setAutoHide(true);
        watchlistPopup.show(watchlistMenuBtn,
                watchlistMenuBtn.localToScreen(0, watchlistMenuBtn.getHeight() + 4).getX(),
                watchlistMenuBtn.localToScreen(0, watchlistMenuBtn.getHeight() + 4).getY());
    }

    private void ensureDashboardLoaded() {
        if (dashboardView != null) return;
        var wc = fxWeaver.load(DashboardController.class);
        dashboardView = asParent(wc.getView().orElseThrow());
        dashboardCtrl = wc.getController();
        dashboardCtrl.setOnNewTradeCallback(this::onNavTradeEntry);
        dashboardCtrl.setOnViewAllCallback(this::onNavTrades);
        dashboardCtrl.setOnEditTradeCallback(trade -> {
            setActiveNav(navTrades);
            if (tradeEntryView == null) {
                var wc2 = fxWeaver.load(TradeEntryController.class);
                tradeEntryView = asParent(wc2.getView().orElseThrow());
                tradeEntryCtrl = wc2.getController();
                tradeEntryCtrl.setOnSaveCallback(saved -> {
                    if (dashboardCtrl != null && activeProfile != null)
                        dashboardCtrl.loadProfile(activeProfile);
                    onNavDashboard();
                });
            }
            if (activeProfile != null) tradeEntryCtrl.setProfile(activeProfile);
            tradeEntryCtrl.setEditingTrade(trade);
            showView(tradeEntryView);
        });
    }

    @FXML public void onNavDashboard() {
        setActiveNav(navDashboard);
        ensureDashboardLoaded();
        dashboardCtrl.setViewMode(DashboardController.ViewMode.DASHBOARD);
        if (activeProfile != null) dashboardCtrl.loadProfile(activeProfile);
        showView(dashboardView);
    }

    @FXML public void onNavChart() {
        setActiveNav(navChart);
        ensureChartLoaded();
        if (activeProfile != null) chartCtrl.setProfile(activeProfile);
        chartCtrl.setViewMode(ChartController.ViewMode.CHART);
        chartCtrl.prepareView();
        showView(chartView);
    }

    private void ensureChartLoaded() {
        if (chartView != null) return;
        var wc = fxWeaver.load(ChartController.class);
        chartView = asParent(wc.getView().orElseThrow());
        chartCtrl = wc.getController();
    }

    @FXML public void onNavTrades() {
        setActiveNav(navTrades);
        ensureDashboardLoaded();
        dashboardCtrl.setViewMode(DashboardController.ViewMode.JOURNAL);
        if (activeProfile != null) dashboardCtrl.loadProfile(activeProfile);
        showView(dashboardView);
    }

    private void onNavTradeEntry() {
        setActiveNav(navTrades);
        if (tradeEntryView == null) {
            var wc = fxWeaver.load(TradeEntryController.class);
            tradeEntryView = asParent(wc.getView().orElseThrow());
            tradeEntryCtrl = wc.getController();
            tradeEntryCtrl.setOnSaveCallback(saved -> {
                if (dashboardCtrl != null && activeProfile != null)
                    dashboardCtrl.loadProfile(activeProfile);
                onNavDashboard();
            });
        }
        if (activeProfile != null) tradeEntryCtrl.setProfile(activeProfile);
        tradeEntryCtrl.setEditingTrade(null);
        showView(tradeEntryView);
    }

    @FXML public void onNavAlerts() {
        setActiveNav(navAlerts);
        if (alertsView == null) {
            var wc = fxWeaver.load(AlertManagerController.class);
            alertsView = asParent(wc.getView().orElseThrow());
            alertsCtrl = wc.getController();
        }
        if (activeProfile != null) alertsCtrl.setProfile(activeProfile);
        showView(alertsView);
    }

    @FXML public void onNavMixer() {
        setActiveNav(navMixer);
        if (mixerView == null) {
            var wc = fxWeaver.load(IndicatorMixerController.class);
            mixerView = asParent(wc.getView().orElseThrow());
            mixerCtrl = wc.getController();
        }
        if (activeProfile != null) mixerCtrl.setProfile(activeProfile);
        showView(mixerView);
    }

    @FXML public void onNavExport() {
        setActiveNav(navExport);
        if (exportView == null) {
            var wc = fxWeaver.load(ExportController.class);
            exportView = asParent(wc.getView().orElseThrow());
            exportCtrl = wc.getController();
        }
        if (activeProfile != null) exportCtrl.setProfile(activeProfile);
        showView(exportView);
    }

    @FXML public void onNavFundamentals() {
        setActiveNav(navFundamentals);
        if (fundamentalsView == null) {
            var wc = fxWeaver.load(YearlyProfitController.class);
            fundamentalsView = asParent(wc.getView().orElseThrow());
            yearlyProfitCtrl = wc.getController();
        }
        if (activeProfile != null) yearlyProfitCtrl.setProfile(activeProfile);
        yearlyProfitCtrl.prepareView();
        showView(fundamentalsView);
    }

    @FXML public void onNavAnalysis() {
        ensureChartLoaded();
        if (activeProfile != null) chartCtrl.setProfile(activeProfile);
        chartCtrl.setViewMode(ChartController.ViewMode.ANALYSIS);
        chartCtrl.prepareView();
        setActiveNav(navAnalysis);
        showView(chartView);
    }

    @FXML public void onOpenAlerts() { onNavAlerts(); }

    @FXML public void onOpenSettings() {
        setActiveNav(navSettings);
        if (settingsView == null) {
            var wc = fxWeaver.load(ProfileSettingsController.class);
            settingsView = asParent(wc.getView().orElseThrow());
            profileSettingsCtrl = wc.getController();
        }
        if (activeProfile != null) profileSettingsCtrl.setProfile(activeProfile);
        showView(settingsView);
    }

    /** Feature 4.4: Open admin user management panel (ADMIN only). */
    @FXML public void onOpenAdminPanel() {
        if (!authService.isAdmin()) {
            new Alert(Alert.AlertType.WARNING,
                    "Admin access required.").showAndWait();
            return;
        }
        if (adminView == null) {
            var wc = fxWeaver.load(AdminUserManagementController.class);
            adminView = asParent(wc.getView().orElseThrow());
            adminCtrl = wc.getController();
            adminCtrl.setOnClose(this::onNavDashboard);
        }
        adminCtrl.refresh();
        showView(adminView);
    }

    /** Logout: clear auth session and return to login screen. */
    @FXML public void onLogout() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Sign Out");
        confirm.setHeaderText("Sign out of the Trading Platform?");
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        confirm.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            authService.logout();
            if (onLogout != null) onLogout.run();
        });
    }

    @FXML public void onNavPortfolio() {
        setActiveNav(navPortfolio);
        ensureDashboardLoaded();
        dashboardCtrl.setViewMode(DashboardController.ViewMode.PORTFOLIO);
        if (activeProfile != null) dashboardCtrl.loadProfile(activeProfile);
        showView(dashboardView);
    }

    @FXML public void onNewProfile() {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("New Profile");
        dlg.setHeaderText("Create a new trading profile");
        dlg.setContentText("Profile name:");
        dlg.showAndWait().ifPresent(name -> {
            if (!name.isBlank()) {
                UserProfile p = createProfile(name.trim(), "#388bfd");
                profileSelector.getItems().add(p);
                profileSelector.setValue(p);
            }
        });
    }

    /**
     * T-13: rename the active profile. Renaming is purely cosmetic — trades / alerts /
     * configs remain linked via primary key.
     */
    @FXML public void onRenameProfile() {
        if (activeProfile == null) return;
        TextInputDialog dlg = new TextInputDialog(activeProfile.getName());
        dlg.setTitle("Rename Profile");
        dlg.setHeaderText("Rename \"" + activeProfile.getName() + "\"");
        dlg.setContentText("New name:");
        dlg.showAndWait().ifPresent(newName -> {
            String trimmed = newName == null ? "" : newName.trim();
            if (trimmed.isBlank()) return;
            if (profileRepository.findAll().stream()
                    .anyMatch(p -> !p.getId().equals(activeProfile.getId())
                            && trimmed.equalsIgnoreCase(p.getName()))) {
                new Alert(Alert.AlertType.ERROR,
                        "A profile named \"" + trimmed + "\" already exists.")
                        .showAndWait();
                return;
            }
            activeProfile.setName(trimmed);
            profileRepository.save(activeProfile);
            profileSelector.setItems(FXCollections.observableArrayList(
                    profileRepository.findAllByOrderByLastAccessedAtDesc()));
            profileSelector.setValue(activeProfile);
            updateStatusBar("Profile renamed to " + trimmed);
        });
    }

    /**
     * T-13: safely delete the active profile after confirmation. JPA cascades remove all
     * dependent trades / alerts / indicator configs.
     */
    @FXML public void onDeleteProfile() {
        if (activeProfile == null) return;
        long profileCount = profileRepository.count();
        if (profileCount <= 1) {
            new Alert(Alert.AlertType.WARNING,
                    "Cannot delete the last remaining profile — create another one first.")
                    .showAndWait();
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Profile");
        confirm.setHeaderText("Delete \"" + activeProfile.getName() + "\"?");
        confirm.setContentText("This permanently removes the profile and all of its trades, "
                + "alerts, and indicator configurations. This cannot be undone.");
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        confirm.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            try {
                UserProfile victim = activeProfile;
                profileRepository.delete(victim);
                List<UserProfile> remaining =
                        profileRepository.findAllByOrderByLastAccessedAtDesc();
                profileSelector.setItems(FXCollections.observableArrayList(remaining));
                if (!remaining.isEmpty()) {
                    profileSelector.setValue(remaining.get(0));
                }
                updateStatusBar("Deleted profile \"" + victim.getName() + "\"");
            } catch (Exception ex) {
                new Alert(Alert.AlertType.ERROR,
                        "Failed to delete profile: " + ex.getMessage()).showAndWait();
            }
        });
    }

    private void showView(Parent view) {
        contentArea.getChildren().setAll(view);
        StackPane.setAlignment(view, javafx.geometry.Pos.TOP_LEFT);
        VBox.setVgrow(view, Priority.ALWAYS);
        HBox.setHgrow(view, Priority.ALWAYS);
    }

    private void setActiveNav(Button active) {
        List.of(navDashboard, navChart, navTrades, navAnalysis,
                        navAlerts, navMixer, navPortfolio, navFundamentals,
                        navExport, navSettings)
                .forEach(b -> {
                    if (b == null) return;
                    b.getStyleClass().remove("nav-item-active");
                    if (!b.getStyleClass().contains("nav-item"))
                        b.getStyleClass().add("nav-item");
                });
        if (active != null && !active.getStyleClass().contains("nav-item-active"))
            active.getStyleClass().add("nav-item-active");
    }

    private void updateStatusBar(String s) {
        Platform.runLater(() -> statusLabel.setText("● " + s));
    }

    private void updateLastUpdateLabel() {
        String t = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        Platform.runLater(() ->
                lastUpdateLabel.setText("Last update: " + t));
    }

    public UserProfile getActiveProfile() { return activeProfile; }

    private static Parent asParent(Node node) {
        if (node instanceof Parent parent) return parent;
        throw new IllegalStateException("FXML root must be a Parent, got: "
                + node.getClass().getName());
    }
}
