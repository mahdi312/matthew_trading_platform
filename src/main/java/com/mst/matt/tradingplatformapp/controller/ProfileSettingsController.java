package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.model.UserProfile;
import com.mst.matt.tradingplatformapp.model.UserProfile.ProfileAssetFocus;
import com.mst.matt.tradingplatformapp.repository.UserProfileRepository;
import com.mst.matt.tradingplatformapp.service.AppSettingsService;
import com.mst.matt.tradingplatformapp.service.ProfilePersistenceService;
import com.mst.matt.tradingplatformapp.service.WatchlistDefaults;
import com.mst.matt.tradingplatformapp.service.auth.AuthService;
import com.mst.matt.tradingplatformapp.service.fundamental.FundamentalDataProvider;
import com.mst.matt.tradingplatformapp.service.fundamental.FundamentalRouter;
import com.mst.matt.tradingplatformapp.service.price.AssetClassDetector.AssetClass;
import com.mst.matt.tradingplatformapp.service.price.LiveTickerService;
import com.mst.matt.tradingplatformapp.service.price.MarketDataProvider;
import com.mst.matt.tradingplatformapp.service.price.PriceProviderRegistry;
import com.mst.matt.tradingplatformapp.service.price.PriceRouter;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import net.rgielen.fxweaver.core.FxmlView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.*;

/**
 * Settings page: per-profile data preferences, timezone, default timeframe,
 * theme toggle, favorite timeframes, offline mode, and watchlist.
 */
@Component
@FxmlView("/fxml/ProfileSettingsView.fxml")
public class ProfileSettingsController {

    private static final List<String> ALL_TIMEFRAMES = List.of(
            "1m","3m","5m","15m","30m","1h","2h","4h","6h","8h","12h","1d","3d","1w","1mo");

    // ── FXML ─────────────────────────────────────────────────
    @FXML private VBox settingsRoot;
    @FXML private Label profileNameLabel;
    @FXML private ComboBox<ProfileAssetFocus> assetFocusCombo;
    @FXML private TextField defaultSymbolField;
    @FXML private ComboBox<MarketDataProvider> chartProviderCombo;
    @FXML private ComboBox<FundamentalDataProvider> fundamentalProviderCombo;
    @FXML private Label chartProvidersHint;
    @FXML private Label savedLabel;
    @FXML private TextArea watchlistField;
    @FXML private CheckBox offlineModeCheck;

    // ── New preference controls ───────────────────────────────
    @FXML private ComboBox<String> timezoneCombo;
    @FXML private Label timezoneHint;
    @FXML private ComboBox<String> defaultTfCombo;
    @FXML private FlowPane favTfPane;
    @FXML private ToggleButton darkThemeBtn;
    @FXML private ToggleButton lightThemeBtn;
    @FXML private ToggleGroup themeGroup;

    // ── Spring services ────────────────────────────────────────
    @Autowired private UserProfileRepository profileRepository;
    @Autowired private AppSettingsService appSettings;
    @Autowired private ProfilePersistenceService profilePersistence;
    @Autowired private PriceProviderRegistry priceRegistry;
    @Autowired private FundamentalRouter fundamentalRouter;
    @Autowired private PriceRouter priceRouter;
    @Autowired private LiveTickerService liveTickerService;
    @Autowired private AuthService authService;

    /** Optional callback: called when timezone changes so ChartController can update. */
    private java.util.function.Consumer<ZoneId> onTimezoneChanged;

    private UserProfile activeProfile;

    // ── Selected favorite timeframes (toggle state) ───────────
    private final Set<String> selectedFavorites = new LinkedHashSet<>();

    @FXML
    public void initialize() {
        // Only accessible when authenticated
        if (settingsRoot != null) {
            settingsRoot.setDisable(false);
        }

        // Asset focus
        assetFocusCombo.setItems(FXCollections.observableArrayList(ProfileAssetFocus.values()));
        assetFocusCombo.setCellFactory(lv -> labelCell());
        assetFocusCombo.setButtonCell(labelCell());

        // Fundamental provider
        fundamentalProviderCombo.setItems(FXCollections.observableArrayList(
                fundamentalRouter.enabledProviders()));
        fundamentalProviderCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(FundamentalDataProvider p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : p.getLabel());
            }
        });
        fundamentalProviderCombo.setButtonCell(
                fundamentalProviderCombo.getCellFactory().call(null));

        // Timezone combo — populate with common zones + system default
        initTimezoneCombo();

        // Default timeframe combo
        defaultTfCombo.setItems(FXCollections.observableArrayList(ALL_TIMEFRAMES));
        defaultTfCombo.setValue(appSettings.getDefaultTimeframe());

        // Theme buttons
        boolean dark = appSettings.isDarkTheme();
        if (darkThemeBtn  != null) darkThemeBtn.setSelected(dark);
        if (lightThemeBtn != null) lightThemeBtn.setSelected(!dark);

        // Favorite timeframes pane
        buildFavoritesPane();
    }

    // ── Timezone ─────────────────────────────────────────────

    private void initTimezoneCombo() {
        if (timezoneCombo == null) return;
        // Build a sorted list of common + all available zone IDs
        List<String> zones = buildTimezoneList();
        timezoneCombo.setItems(FXCollections.observableArrayList(zones));
        String current = appSettings.getTimezoneId();
        if (zones.contains(current)) {
            timezoneCombo.setValue(current);
        } else {
            timezoneCombo.setValue(ZoneId.systemDefault().getId());
        }
        timezoneCombo.valueProperty().addListener((o, a, n) -> {
            if (n != null && timezoneHint != null) {
                try {
                    ZoneId zone = ZoneId.of(n);
                    timezoneHint.setText("Current offset: " + zone.getRules()
                            .getOffset(java.time.Instant.now()));
                } catch (Exception e) {
                    timezoneHint.setText("");
                }
            }
        });
        // Trigger hint for initial value
        if (current != null) {
            try {
                ZoneId zone = ZoneId.of(current);
                if (timezoneHint != null)
                    timezoneHint.setText("Current offset: " + zone.getRules()
                            .getOffset(java.time.Instant.now()));
            } catch (Exception ignored) {}
        }
    }

    private List<String> buildTimezoneList() {
        // Prioritize common zones at the top
        List<String> common = List.of(
                "UTC",
                "America/New_York", "America/Chicago", "America/Denver", "America/Los_Angeles",
                "America/Toronto", "America/Sao_Paulo",
                "Europe/London", "Europe/Paris", "Europe/Berlin", "Europe/Moscow",
                "Asia/Dubai", "Asia/Kolkata", "Asia/Bangkok", "Asia/Shanghai",
                "Asia/Tokyo", "Asia/Seoul", "Asia/Singapore",
                "Australia/Sydney", "Pacific/Auckland"
        );
        Set<String> all = new LinkedHashSet<>(common);
        // Add system default if not already in list
        all.add(ZoneId.systemDefault().getId());
        // Add all available zone IDs (sorted)
        ZoneId.getAvailableZoneIds().stream().sorted().forEach(all::add);
        return new ArrayList<>(all);
    }

    @FXML
    public void onUseSystemTimezone() {
        if (timezoneCombo != null) {
            timezoneCombo.setValue(ZoneId.systemDefault().getId());
        }
    }

    // ── Favorite timeframes pane ──────────────────────────────

    private void buildFavoritesPane() {
        if (favTfPane == null) return;
        favTfPane.getChildren().clear();
        List<String> allowed = authService.allowedTimeframes();
        for (String tf : ALL_TIMEFRAMES) {
            if (!allowed.contains(tf)) continue;
            ToggleButton btn = new ToggleButton(tf.toUpperCase());
            btn.setSelected(selectedFavorites.contains(tf));
            btn.setStyle(favBtnStyle(btn.isSelected()));
            btn.selectedProperty().addListener((o, a, selected) -> {
                if (selected) selectedFavorites.add(tf);
                else selectedFavorites.remove(tf);
                btn.setStyle(favBtnStyle(selected));
            });
            favTfPane.getChildren().add(btn);
        }
    }

    private String favBtnStyle(boolean selected) {
        return selected
                ? "-fx-background-color:#b08800; -fx-text-fill:white;"
                  + "-fx-background-radius:6; -fx-padding:5 12; -fx-cursor:hand;"
                  + "-fx-font-weight:bold;"
                : "-fx-background-color:#21262d; -fx-text-fill:#8b949e;"
                  + "-fx-background-radius:6; -fx-padding:5 12; -fx-cursor:hand;";
    }

    // ── Theme ─────────────────────────────────────────────────

    @FXML
    public void onThemeChanged() {
        // Theme is persisted on save
        updateThemeBtnStyles();
    }

    private void updateThemeBtnStyles() {
        if (darkThemeBtn == null || lightThemeBtn == null) return;
        boolean dark = darkThemeBtn.isSelected();
        darkThemeBtn.setStyle(dark
                ? "-fx-background-color:#1f6feb; -fx-text-fill:white;"
                  + "-fx-background-radius:6; -fx-padding:8 20; -fx-cursor:hand;"
                : "-fx-background-color:#21262d; -fx-text-fill:#8b949e;"
                  + "-fx-background-radius:6; -fx-padding:8 20; -fx-cursor:hand;");
        lightThemeBtn.setStyle(!dark
                ? "-fx-background-color:#1f6feb; -fx-text-fill:white;"
                  + "-fx-background-radius:6; -fx-padding:8 20; -fx-cursor:hand;"
                : "-fx-background-color:#21262d; -fx-text-fill:#8b949e;"
                  + "-fx-background-radius:6; -fx-padding:8 20; -fx-cursor:hand;");
    }

    // ── setProfile / onSave ───────────────────────────────────

    public void setProfile(UserProfile profile) {
        this.activeProfile = profile;
        if (profile == null) return;

        // Guard: only show settings to authenticated users
        if (!authService.isLoggedIn()) {
            if (settingsRoot != null) settingsRoot.setDisable(true);
            return;
        }
        if (settingsRoot != null) settingsRoot.setDisable(false);

        ProfileAssetFocus focus = profile.getAssetFocus() != null
                ? profile.getAssetFocus()
                : ProfileAssetFocus.MULTI;
        if (profile.getAssetFocus() == null) profile.setAssetFocus(focus);

        profileNameLabel.setText(profile.getName() + "  ["
                + authService.currentUser().map(u -> u.getDisplayName()).orElse("—") + "]");
        assetFocusCombo.setValue(focus);
        defaultSymbolField.setText(profile.getDefaultSymbol() != null
                ? profile.getDefaultSymbol()
                : focus.defaultSymbol());
        refreshChartProviders();
        chartProviderCombo.setValue(
                MarketDataProvider.fromString(profile.getChartProvider()));
        fundamentalProviderCombo.setValue(
                FundamentalDataProvider.fromString(profile.getFundamentalProvider()));

        if (watchlistField != null) {
            watchlistField.setText(profile.getWatchlist() == null
                    ? WatchlistDefaults.csvForFocus(focus)
                    : profile.getWatchlist());
        }
        if (offlineModeCheck != null) {
            offlineModeCheck.setSelected(appSettings.isOfflineMode());
        }

        // Timezone
        if (timezoneCombo != null) {
            String tz = appSettings.getTimezoneId();
            if (timezoneCombo.getItems().contains(tz)) timezoneCombo.setValue(tz);
        }

        // Default TF
        if (defaultTfCombo != null) {
            defaultTfCombo.setValue(appSettings.getDefaultTimeframe());
        }

        // Favorites
        selectedFavorites.clear();
        selectedFavorites.addAll(authService.getFavoriteTimeframes());
        buildFavoritesPane();

        // Theme
        boolean dark = appSettings.isDarkTheme();
        if (darkThemeBtn  != null) darkThemeBtn.setSelected(dark);
        if (lightThemeBtn != null) lightThemeBtn.setSelected(!dark);
        updateThemeBtnStyles();

        savedLabel.setText("");
    }

    @FXML
    public void onOfflineModeChanged() {
        if (offlineModeCheck == null) return;
        appSettings.setApiFetchEnabled(!offlineModeCheck.isSelected());
        savedLabel.setText(offlineModeCheck.isSelected()
                ? "Offline mode on — using cached data."
                : "Live API fetching enabled.");
    }

    @FXML
    public void onAssetFocusChanged() {
        refreshChartProviders();
    }

    @FXML
    public void onSave() {
        if (activeProfile == null) return;
        if (!authService.isLoggedIn()) {
            savedLabel.setText("⚠ Not authenticated.");
            return;
        }

        // ── Profile data ──────────────────────────────────────
        ProfileAssetFocus focus = assetFocusCombo.getValue();
        if (focus != null) activeProfile.setAssetFocus(focus);
        String sym = defaultSymbolField.getText();
        activeProfile.setDefaultSymbol(sym != null ? sym.trim().toUpperCase() : null);
        MarketDataProvider chart = chartProviderCombo.getValue();
        if (chart != null) activeProfile.setChartProvider(chart.name());
        FundamentalDataProvider fund = fundamentalProviderCombo.getValue();
        if (fund != null) activeProfile.setFundamentalProvider(fund.name());

        if (watchlistField != null) {
            String csv = watchlistField.getText() == null ? ""
                    : watchlistField.getText().trim();
            activeProfile.setWatchlist(csv.isEmpty() ? null : csv.toUpperCase());
            liveTickerService.applyProfileWatchlist(activeProfile);
        }
        if (offlineModeCheck != null) {
            appSettings.setApiFetchEnabled(!offlineModeCheck.isSelected());
        }

        // ── App settings ─────────────────────────────────────
        // Timezone
        if (timezoneCombo != null && timezoneCombo.getValue() != null) {
            appSettings.setTimezone(timezoneCombo.getValue());
            if (onTimezoneChanged != null) {
                try {
                    onTimezoneChanged.accept(ZoneId.of(timezoneCombo.getValue()));
                } catch (Exception ignored) {}
            }
        }

        // Default timeframe
        if (defaultTfCombo != null && defaultTfCombo.getValue() != null) {
            appSettings.setDefaultTimeframe(defaultTfCombo.getValue());
        }

        // Favorite timeframes
        List<String> favs = new ArrayList<>(selectedFavorites);
        authService.saveFavoriteTimeframes(favs);
        appSettings.setFavoriteTimeframes(String.join(",", favs));

        // Theme
        if (darkThemeBtn != null) {
            appSettings.setTheme(darkThemeBtn.isSelected() ? "dark" : "light");
        }

        // Profile save
        profilePersistence.saveAsync(activeProfile);
        priceRouter.setActiveProfile(activeProfile);
        savedLabel.setText("✔ Saved.");
    }

    // ── Chart provider ────────────────────────────────────────

    private void refreshChartProviders() {
        ProfileAssetFocus focus = assetFocusCombo.getValue();
        AssetClass asset = switch (focus != null ? focus : ProfileAssetFocus.MULTI) {
            case CRYPTO -> AssetClass.CRYPTO;
            case STOCK  -> AssetClass.STOCK;
            case FOREX  -> AssetClass.FOREX;
            case MULTI  -> AssetClass.STOCK;
        };
        List<MarketDataProvider> enabled = new ArrayList<>();
        enabled.add(MarketDataProvider.AUTO);
        enabled.addAll(priceRegistry.enabledProvidersFor(asset));
        chartProviderCombo.setItems(FXCollections.observableArrayList(enabled));
        chartProviderCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(MarketDataProvider p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : p.getLabel());
            }
        });
        chartProviderCombo.setButtonCell(chartProviderCombo.getCellFactory().call(null));
        chartProvidersHint.setText("Enabled for " + asset.name().toLowerCase()
                + ": " + enabled.size() + " sources (keys in application.properties).");
    }

    // ── Callbacks ─────────────────────────────────────────────

    /** Optional callback so parent (MainDashboard) can push timezone change to ChartController. */
    public void setOnTimezoneChanged(java.util.function.Consumer<ZoneId> cb) {
        this.onTimezoneChanged = cb;
    }

    // ── Helpers ───────────────────────────────────────────────

    private static <T extends Enum<T>> ListCell<T> labelCell() {
        return new ListCell<>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        };
    }
}
