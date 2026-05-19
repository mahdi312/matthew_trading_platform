package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.model.UserProfile;
import com.mst.matt.tradingplatformapp.model.UserProfile.ProfileAssetFocus;
import com.mst.matt.tradingplatformapp.model.IndicatorConfig;
import com.mst.matt.tradingplatformapp.repository.IndicatorConfigRepository;
import com.mst.matt.tradingplatformapp.repository.UserProfileRepository;
import com.mst.matt.tradingplatformapp.service.analysis.AnalysisService;
import com.mst.matt.tradingplatformapp.service.alert.AlertService;
import com.mst.matt.tradingplatformapp.service.price.LiveTickerService;
import com.mst.matt.tradingplatformapp.service.price.PriceQuote;
import com.mst.matt.tradingplatformapp.service.price.PriceRouter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import net.rgielen.fxweaver.core.FxWeaver;
import net.rgielen.fxweaver.core.FxmlView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

@Component
@FxmlView("/fxml/MainDashboard.fxml")
public class MainDashboardController implements Initializable {

    @FXML private ComboBox<UserProfile> profileSelector;
    @FXML private HBox   tickerBar;
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

    private Parent dashboardView, chartView, tradeEntryView,
            alertsView, mixerView, exportView, fundamentalsView, settingsView;

    private DashboardController         dashboardCtrl;
    private ChartController             chartCtrl;
    private TradeEntryController        tradeEntryCtrl;
    private AlertManagerController      alertsCtrl;
    private IndicatorMixerController    mixerCtrl;
    private ExportController            exportCtrl;
    private YearlyProfitController      yearlyProfitCtrl;
    private ProfileSettingsController   profileSettingsCtrl;

    private UserProfile activeProfile;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupProfileSelector();
        loadOrCreateDefaultProfiles();
        setupLiveTicker();
        updateLastUpdateLabel();
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
            String sym = quote.getSymbol();
            if (quote.getPrice() == null) return;
            String price = "$" + quote.getPrice()
                    .setScale(quote.getPrice().intValue() >= 10 ? 2 : 4,
                            RoundingMode.HALF_UP).toPlainString();
            String change = quote.getChangePct24h() != null
                    ? (quote.isUp() ? "▲ +" : "▼ ")
                    + quote.getChangePct24h()
                    .setScale(2, RoundingMode.HALF_UP).toPlainString() + "%"
                    : "";

            tickerBar.getChildren().stream()
                    .filter(n -> n instanceof Label)
                    .map(n -> (Label) n)
                    .filter(l -> l.getText().startsWith(sym))
                    .findFirst()
                    .ifPresentOrElse(
                            l -> {
                                l.setText(sym + "  " + price + "  " + change);
                                l.getStyleClass().removeAll(
                                        "ticker-price-up","ticker-price-down");
                                l.getStyleClass().add(
                                        quote.isUp() ? "ticker-price-up"
                                                : "ticker-price-down");
                            },
                            () -> {
                                Label lbl = new Label(sym + "  " + price + "  " + change);
                                lbl.getStyleClass().addAll("ticker-item",
                                        quote.isUp() ? "ticker-price-up"
                                                : "ticker-price-down");
                                Label sep = new Label(" │ ");
                                sep.setStyle("-fx-text-fill:#30363d;");
                                tickerBar.getChildren().addAll(lbl, sep);
                            }
                    );
            updateLastUpdateLabel();
        });
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
