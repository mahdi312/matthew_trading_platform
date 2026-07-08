package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.config.StageInitializer;
import com.mst.matt.tradingplatformapp.model.DataFetchMode;
import com.mst.matt.tradingplatformapp.model.SymbolEntry;
import com.mst.matt.tradingplatformapp.model.UserProfile;
import com.mst.matt.tradingplatformapp.model.UserProfile.ProfileAssetFocus;
import com.mst.matt.tradingplatformapp.repository.SymbolEntryRepository;
import com.mst.matt.tradingplatformapp.repository.UserProfileRepository;
import com.mst.matt.tradingplatformapp.service.AppSettingsService;
import com.mst.matt.tradingplatformapp.service.AppSettingsService.AppTheme;
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
import com.mst.matt.tradingplatformapp.ui.AutocompleteSymbolField;
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
    @FXML private ToggleButton amazonGreenThemeBtn;
    @FXML private ToggleButton lightBlueThemeBtn;
    @FXML private ToggleGroup themeGroup;
    @FXML private Label themeStatusLabel;

    // ── Data Fetch Mode radio buttons ─────────────────────────
    @FXML private RadioButton fetchModeFullOnlineRadio;
    @FXML private RadioButton fetchModeOfflineOnFailRadio;
    @FXML private RadioButton fetchModeOfflineOnlyRadio;
    @FXML private ToggleGroup dataFetchModeGroup;

    // ── Sensitivity sliders ───────────────────────────────────
    @FXML private javafx.scene.control.Slider zoomSensitivitySlider;
    @FXML private javafx.scene.control.Label  zoomSensitivityLabel;
    @FXML private javafx.scene.control.Slider panSensitivitySlider;
    @FXML private javafx.scene.control.Label  panSensitivityLabel;

    // ── Ticker fetch controls ─────────────────────────────────
    @FXML private javafx.scene.control.Spinner<Integer> tickerIntervalSpinner;
    @FXML private FlowPane tickerSymbolPane;

    /** Callback to apply changed sensitivity values to the live chart canvas. */
    private java.util.function.Consumer<double[]> onSensitivityChanged;

    // ── Spring services ────────────────────────────────────────
    @Autowired private UserProfileRepository profileRepository;
    @Autowired private AppSettingsService appSettings;
    @Autowired private ProfilePersistenceService profilePersistence;
    @Autowired private PriceProviderRegistry priceRegistry;
    @Autowired private FundamentalRouter fundamentalRouter;
    @Autowired private PriceRouter priceRouter;
    @Autowired private LiveTickerService liveTickerService;
    @Autowired private AuthService authService;
    @Autowired private StageInitializer stageInitializer;
    @Autowired private SymbolEntryRepository symbolEntryRepository;

    /** Autocomplete wrapper around the plain defaultSymbolField TextField. */
    private AutocompleteSymbolField defaultSymbolAutocomplete;

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

        // ── Swap plain defaultSymbolField with autocomplete field ─────────────
        if (defaultSymbolField != null && defaultSymbolField.getParent() instanceof javafx.scene.layout.VBox parent) {
            int idx = parent.getChildren().indexOf(defaultSymbolField);
            if (idx >= 0) {
                defaultSymbolAutocomplete = new AutocompleteSymbolField(symbolEntryRepository);
                defaultSymbolAutocomplete.setMaxWidth(320);
                defaultSymbolAutocomplete.setPromptText("e.g. AAPL, BTCUSDT, EURUSD");
                // Mirror text back to the original field so existing save logic still works
                defaultSymbolAutocomplete.textProperty().addListener((o, a, n) -> {
                    if (defaultSymbolField != null) defaultSymbolField.setText(n);
                });
                parent.getChildren().set(idx, defaultSymbolAutocomplete);
            }
        }

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

        // Theme buttons — set initial selection from saved theme
        applyThemeSelectionToButtons(appSettings.getTheme());

        // Favorite timeframes pane
        buildFavoritesPane();

        // ── Sensitivity sliders ───────────────────────────────
        initSensitivitySliders();

        // ── Ticker interval spinner ───────────────────────────
        initTickerIntervalSpinner();
    }

    private void initSensitivitySliders() {
        if (zoomSensitivitySlider != null) {
            double zoomVal = parseSensitivity(appSettings.getSetting("zoomSensitivity"), 0.4);
            zoomSensitivitySlider.setValue(zoomVal);
            updateSensitivityLabel(zoomSensitivityLabel, zoomVal);
            zoomSensitivitySlider.valueProperty().addListener((o, a, n) -> {
                double v = Math.round(n.doubleValue() * 100.0) / 100.0;
                updateSensitivityLabel(zoomSensitivityLabel, v);
                // Live preview — notify chart immediately
                if (onSensitivityChanged != null) {
                    double panVal = panSensitivitySlider != null
                            ? panSensitivitySlider.getValue() : 0.6;
                    onSensitivityChanged.accept(new double[]{v, panVal});
                }
            });
        }
        if (panSensitivitySlider != null) {
            double panVal = parseSensitivity(appSettings.getSetting("panSensitivity"), 0.6);
            panSensitivitySlider.setValue(panVal);
            updateSensitivityLabel(panSensitivityLabel, panVal);
            panSensitivitySlider.valueProperty().addListener((o, a, n) -> {
                double v = Math.round(n.doubleValue() * 100.0) / 100.0;
                updateSensitivityLabel(panSensitivityLabel, v);
                if (onSensitivityChanged != null) {
                    double zoomVal = zoomSensitivitySlider != null
                            ? zoomSensitivitySlider.getValue() : 0.4;
                    onSensitivityChanged.accept(new double[]{zoomVal, v});
                }
            });
        }
    }

    private void initTickerIntervalSpinner() {
        if (tickerIntervalSpinner == null) return;
        tickerIntervalSpinner.setValueFactory(
                new javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory(5, 300,
                        appSettings.getTickerPollIntervalSeconds()));
    }

    // ── Add-symbol text field (built programmatically) ─────
    private javafx.scene.control.TextField addSymbolField;

    /**
     * Build the symbol toggle chips from the current watchlist + disabled state.
     * Each chip has an enabled toggle AND a small "✕" delete button.
     * At the bottom there is an "+ Add Symbol" row.
     */
    private void buildTickerSymbolPane() {
        if (tickerSymbolPane == null) return;
        tickerSymbolPane.getChildren().clear();

        List<String> symbols = liveTickerService.allSymbols();
        if (symbols.isEmpty()) {
            javafx.scene.control.Label noSyms = new javafx.scene.control.Label(
                    "No symbols in watchlist. Add one below.");
            noSyms.setStyle("-fx-text-fill:#8b949e;");
            tickerSymbolPane.getChildren().add(noSyms);
        } else {
            for (String sym : new ArrayList<>(symbols)) {
                tickerSymbolPane.getChildren().add(buildSymbolChip(sym));
            }
        }

        // ── Add-symbol row ─────────────────────────────────────
        javafx.scene.layout.HBox addRow = new javafx.scene.layout.HBox(6);
        addRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        addSymbolField = new javafx.scene.control.TextField();
        addSymbolField.setPromptText("e.g. SOLUSDT, AMZN, GBPUSD");
        addSymbolField.setPrefWidth(180);
        addSymbolField.setStyle("-fx-background-color:#0d1117; -fx-text-fill:#e6edf3;"
                + "-fx-border-color:#30363d; -fx-border-radius:6;"
                + "-fx-background-radius:6; -fx-padding:4 10;");
        // Allow Enter to trigger add
        addSymbolField.setOnAction(ev -> onAddTickerSymbol());

        javafx.scene.control.Button addBtn = new javafx.scene.control.Button("+ Add");
        addBtn.setStyle("-fx-background-color:#1f6feb; -fx-text-fill:white;"
                + "-fx-background-radius:6; -fx-padding:4 12; -fx-cursor:hand; -fx-font-weight:bold;");
        addBtn.setOnAction(ev -> onAddTickerSymbol());

        // Width forces the add-row to span the full FlowPane on its own "line"
        javafx.scene.layout.HBox.setHgrow(addSymbolField, javafx.scene.layout.Priority.ALWAYS);
        addRow.getChildren().addAll(addSymbolField, addBtn);
        tickerSymbolPane.getChildren().add(addRow);
    }

    /** Build one symbol chip (toggle + delete button). */
    private javafx.scene.layout.HBox buildSymbolChip(String sym) {
        boolean enabled = appSettings.isTickerSymbolEnabled(sym);

        javafx.scene.control.ToggleButton chip = new javafx.scene.control.ToggleButton(sym);
        chip.setSelected(enabled);
        chip.setStyle(tickerChipStyle(enabled));
        chip.selectedProperty().addListener((o, a, sel) -> {
            appSettings.setTickerSymbolEnabled(sym, sel);
            chip.setStyle(tickerChipStyle(sel));
            liveTickerService.applyTickerSymbolSettings();
        });

        // Small delete button
        javafx.scene.control.Button delBtn = new javafx.scene.control.Button("✕");
        delBtn.setStyle("-fx-background-color:transparent; -fx-text-fill:#f85149;"
                + "-fx-cursor:hand; -fx-padding:2 4; -fx-font-size:10px; -fx-border-width:0;");
        delBtn.setOnAction(ev -> {
            liveTickerService.removeFromWatchlist(sym);
            buildTickerSymbolPane(); // rebuild to reflect removal
        });
        delBtn.setTooltip(new javafx.scene.control.Tooltip("Remove " + sym + " from watchlist"));

        javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(2, chip, delBtn);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row.setStyle("-fx-background-color:#161b22; -fx-background-radius:8;"
                + "-fx-border-color:#30363d; -fx-border-radius:8; -fx-border-width:1;"
                + "-fx-padding:2 4 2 2;");
        return row;
    }

    private static String tickerChipStyle(boolean enabled) {
        return enabled
                ? "-fx-background-color:#1a4a1a; -fx-text-fill:#3fb950;"
                  + "-fx-background-radius:6; -fx-padding:4 12; -fx-cursor:hand;"
                  + "-fx-border-color:#3fb950; -fx-border-radius:6; -fx-border-width:1;"
                : "-fx-background-color:#21262d; -fx-text-fill:#8b949e;"
                  + "-fx-background-radius:6; -fx-padding:4 12; -fx-cursor:hand;"
                  + "-fx-border-color:#30363d; -fx-border-radius:6; -fx-border-width:1;";
    }

    /** Called by the Add button or pressing Enter in the add-symbol text field. */
    @FXML
    public void onAddTickerSymbol() {
        if (addSymbolField == null) return;
        String raw = addSymbolField.getText();
        if (raw == null || raw.isBlank()) return;
        // Support multiple symbols separated by commas/spaces
        for (String token : raw.split("[,;\\s]+")) {
            String s = token.trim().toUpperCase();
            if (!s.isEmpty()) {
                liveTickerService.addToWatchlist(s);
                appSettings.setTickerSymbolEnabled(s, true); // enabled by default
            }
        }
        addSymbolField.clear();
        buildTickerSymbolPane(); // rebuild to show new symbols
    }

    @FXML
    public void onRefreshTickerSymbols() {
        buildTickerSymbolPane();
    }

    // ── Ticker interval preset shortcuts ──────────────────────────────────

    @FXML public void onTickerPreset15s() { setTickerInterval(15); }
    @FXML public void onTickerPreset1m()  { setTickerInterval(60); }
    @FXML public void onTickerPreset5m()  { setTickerInterval(300); }
    @FXML public void onTickerPreset1h()  { setTickerInterval(3600); }

    private void setTickerInterval(int seconds) {
        if (tickerIntervalSpinner == null) return;
        tickerIntervalSpinner.getValueFactory().setValue(seconds);
        // Immediately apply (same as hitting Save for just this setting)
        appSettings.setTickerPollIntervalSeconds(seconds);
        liveTickerService.applyPollIntervalSetting();
        if (savedLabel != null) savedLabel.setText("Interval set to " + seconds + " s");
    }

    private void updateSensitivityLabel(javafx.scene.control.Label lbl, double v) {
        if (lbl != null) lbl.setText(String.format("%.2f", v));
    }

    private double parseSensitivity(String s, double def) {
        if (s == null || s.isBlank()) return def;
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return def; }
    }

    public void setOnSensitivityChanged(java.util.function.Consumer<double[]> cb) {
        this.onSensitivityChanged = cb;
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

    /**
     * Called when any theme toggle button is clicked.
     * Determines which button is selected, applies the theme immediately,
     * and persists the setting.
     */
    @FXML
    public void onThemeChanged() {
        String themeId = selectedThemeId();
        // Instant switch — no restart required
        stageInitializer.applyTheme(themeId);
        updateThemeBtnStyles(themeId);
        if (themeStatusLabel != null) {
            themeStatusLabel.setText("Theme applied: " + AppTheme.fromId(themeId).id.replace("_", " "));
        }
    }

    @FXML
    public void onUseSystemTheme() {
        boolean osDark = AppSettingsService.isOsDarkMode();
        String themeId = osDark ? "dark" : "light";
        applyThemeSelectionToButtons(themeId);
        stageInitializer.applyTheme(themeId);
        if (themeStatusLabel != null) {
            themeStatusLabel.setText("System theme applied: " + (osDark ? "Dark" : "Light"));
        }
    }

    /** Returns the theme ID for the currently-selected toggle button. */
    private String selectedThemeId() {
        if (darkThemeBtn       != null && darkThemeBtn.isSelected())       return "dark";
        if (lightThemeBtn      != null && lightThemeBtn.isSelected())      return "light";
        if (amazonGreenThemeBtn != null && amazonGreenThemeBtn.isSelected()) return "amazon_green";
        if (lightBlueThemeBtn  != null && lightBlueThemeBtn.isSelected())  return "light_blue";
        return "dark"; // fallback
    }

    /** Selects the correct toggle button for the given theme ID. */
    private void applyThemeSelectionToButtons(String themeId) {
        AppTheme t = AppTheme.fromId(themeId);
        if (darkThemeBtn       != null) darkThemeBtn.setSelected(t == AppTheme.DARK);
        if (lightThemeBtn      != null) lightThemeBtn.setSelected(t == AppTheme.LIGHT);
        if (amazonGreenThemeBtn != null) amazonGreenThemeBtn.setSelected(t == AppTheme.AMAZON_GREEN);
        if (lightBlueThemeBtn  != null) lightBlueThemeBtn.setSelected(t == AppTheme.LIGHT_BLUE);
        updateThemeBtnStyles(themeId);
    }

    private void updateThemeBtnStyles(String activeThemeId) {
        AppTheme active = AppTheme.fromId(activeThemeId);
        styleThemeBtn(darkThemeBtn,       active == AppTheme.DARK);
        styleThemeBtn(lightThemeBtn,      active == AppTheme.LIGHT);
        styleThemeBtn(amazonGreenThemeBtn, active == AppTheme.AMAZON_GREEN);
        styleThemeBtn(lightBlueThemeBtn,  active == AppTheme.LIGHT_BLUE);
    }

    private void styleThemeBtn(ToggleButton btn, boolean selected) {
        if (btn == null) return;
        btn.setStyle(selected
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
        String sym = profile.getDefaultSymbol() != null
                ? profile.getDefaultSymbol()
                : focus.defaultSymbol();
        defaultSymbolField.setText(sym);
        if (defaultSymbolAutocomplete != null) {
            defaultSymbolAutocomplete.setSymbol(sym);
            // Filter autocomplete suggestions based on asset focus
            defaultSymbolAutocomplete.setFilterType(assetFocusToSymbolType(focus));
        }
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
        // Data Fetch Mode radio buttons
        applyDataFetchModeToUI(appSettings.getDataFetchMode());

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
        applyThemeSelectionToButtons(appSettings.getTheme());

        // Sensitivity sliders
        if (zoomSensitivitySlider != null) {
            double zv = parseSensitivity(appSettings.getSetting("zoomSensitivity"), 0.4);
            zoomSensitivitySlider.setValue(zv);
            updateSensitivityLabel(zoomSensitivityLabel, zv);
        }
        if (panSensitivitySlider != null) {
            double pv = parseSensitivity(appSettings.getSetting("panSensitivity"), 0.6);
            panSensitivitySlider.setValue(pv);
            updateSensitivityLabel(panSensitivityLabel, pv);
        }

        // Ticker interval
        if (tickerIntervalSpinner != null) {
            tickerIntervalSpinner.getValueFactory()
                    .setValue(appSettings.getTickerPollIntervalSeconds());
        }
        // Ticker symbol toggles
        buildTickerSymbolPane();

        savedLabel.setText("");
    }

    @FXML
    public void onOfflineModeChanged() {
        if (offlineModeCheck == null) return;
        // Legacy checkbox: map to DataFetchMode for backward compatibility
        DataFetchMode mode = offlineModeCheck.isSelected()
                ? DataFetchMode.OFFLINE_ONLY : DataFetchMode.OFFLINE_ON_FAIL;
        appSettings.setDataFetchMode(mode);
        applyDataFetchModeToUI(mode);
        savedLabel.setText(offlineModeCheck.isSelected()
                ? "Offline mode on — using cached data."
                : "Live API fetching enabled.");
    }

    /**
     * Called when any of the three Data Fetch Mode radio buttons is clicked.
     * Persists the chosen mode immediately so it takes effect right away.
     */
    @FXML
    public void onDataFetchModeChanged() {
        DataFetchMode mode = selectedDataFetchMode();
        appSettings.setDataFetchMode(mode);
        // Keep legacy offline checkbox in sync
        if (offlineModeCheck != null) {
            offlineModeCheck.setSelected(mode == DataFetchMode.OFFLINE_ONLY);
        }
        savedLabel.setText("Data fetch mode: " + mode.label);
    }

    /** Returns the {@link DataFetchMode} corresponding to the currently-selected radio button. */
    private DataFetchMode selectedDataFetchMode() {
        if (fetchModeOfflineOnlyRadio != null && fetchModeOfflineOnlyRadio.isSelected())
            return DataFetchMode.OFFLINE_ONLY;
        if (fetchModeFullOnlineRadio != null && fetchModeFullOnlineRadio.isSelected())
            return DataFetchMode.FULL_ONLINE;
        return DataFetchMode.OFFLINE_ON_FAIL; // default
    }

    /** Reflects the given {@link DataFetchMode} on the radio buttons (no event fired). */
    private void applyDataFetchModeToUI(DataFetchMode mode) {
        if (mode == null) mode = DataFetchMode.OFFLINE_ON_FAIL;
        if (fetchModeFullOnlineRadio    != null) fetchModeFullOnlineRadio.setSelected(mode == DataFetchMode.FULL_ONLINE);
        if (fetchModeOfflineOnFailRadio != null) fetchModeOfflineOnFailRadio.setSelected(mode == DataFetchMode.OFFLINE_ON_FAIL);
        if (fetchModeOfflineOnlyRadio   != null) fetchModeOfflineOnlyRadio.setSelected(mode == DataFetchMode.OFFLINE_ONLY);
    }

    @FXML
    public void onAssetFocusChanged() {
        ProfileAssetFocus newFocus = assetFocusCombo.getValue();
        if (newFocus == null) return;

        // ── 1. Auto-update Default Symbol ─────────────────────────────────────
        // Always update when the user explicitly changes the asset focus.
        String newDefaultSymbol = newFocus.defaultSymbol();
        if (defaultSymbolField != null) {
            defaultSymbolField.setText(newDefaultSymbol);
        }
        if (defaultSymbolAutocomplete != null) {
            defaultSymbolAutocomplete.setSymbol(newDefaultSymbol);
            defaultSymbolAutocomplete.setFilterType(assetFocusToSymbolType(newFocus));
        }

        // ── 2. Refresh and auto-select Chart Data Provider ────────────────────
        // refreshChartProviders() rebuilds the combo filtered by the new asset class
        // and the current user's plan. After rebuild, pick the first non-AUTO option
        // as the new default provider for this asset type (if available), otherwise AUTO.
        refreshChartProviders();
        if (chartProviderCombo != null && chartProviderCombo.getItems().size() > 1) {
            // Prefer the first provider in the list that is not AUTO
            MarketDataProvider firstReal = chartProviderCombo.getItems().stream()
                    .filter(p -> p != MarketDataProvider.AUTO)
                    .findFirst()
                    .orElse(MarketDataProvider.AUTO);
            chartProviderCombo.setValue(firstReal);
        } else {
            chartProviderCombo.setValue(MarketDataProvider.AUTO);
        }

        // ── 3. Update watchlist hint ───────────────────────────────────────────
        if (watchlistField != null) {
            String current = watchlistField.getText();
            if (current == null || current.isBlank()) {
                watchlistField.setPromptText("e.g. " + WatchlistDefaults.csvForFocus(newFocus));
            }
        }

        // ── 4. Persist the changes immediately so they are saved per-user ──────
        if (activeProfile != null && authService.isLoggedIn()) {
            activeProfile.setAssetFocus(newFocus);
            activeProfile.setDefaultSymbol(newDefaultSymbol);
            MarketDataProvider chosen = chartProviderCombo.getValue();
            if (chosen != null) activeProfile.setChartProvider(chosen.name());
            profilePersistence.saveAsync(activeProfile);
            if (savedLabel != null) {
                savedLabel.setText("Asset focus set to " + newFocus.name()
                        + " · default symbol: " + newDefaultSymbol
                        + " · provider: " + (chosen != null ? chosen.getLabel() : "AUTO"));
            }
        }
    }

    /** Map ProfileAssetFocus to SymbolEntry.AssetType for the autocomplete filter. */
    private static SymbolEntry.AssetType assetFocusToSymbolType(ProfileAssetFocus focus) {
        if (focus == null) return null;
        return switch (focus) {
            case CRYPTO -> SymbolEntry.AssetType.CRYPTO;
            case STOCK  -> SymbolEntry.AssetType.STOCK;
            case FOREX  -> SymbolEntry.AssetType.FOREX;
            case MULTI  -> null; // no filter → search all asset types
        };
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
        if (chart != null) {
            // Guard: reject providers not allowed by this user's plan
            if (!priceRegistry.isProviderAllowedForCurrentUser(chart.name())) {
                chart = MarketDataProvider.AUTO;
                chartProviderCombo.setValue(MarketDataProvider.AUTO);
                savedLabel.setText("⚠ Provider not available for your plan — reset to AUTO.");
            }
            activeProfile.setChartProvider(chart.name());
        }
        FundamentalDataProvider fund = fundamentalProviderCombo.getValue();
        if (fund != null) activeProfile.setFundamentalProvider(fund.name());

        if (watchlistField != null) {
            String csv = watchlistField.getText() == null ? ""
                    : watchlistField.getText().trim();
            // Normalize: upper-case and deduplicate
            String normalized = java.util.Arrays.stream(csv.split("[,;\\s]+"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(String::toUpperCase)
                    .distinct()
                    .collect(java.util.stream.Collectors.joining(", "));
            activeProfile.setWatchlist(normalized.isEmpty() ? null : normalized);
            liveTickerService.applyProfileWatchlist(activeProfile);
            // Rebuild symbol chip pane so it reflects the saved watchlist immediately
            buildTickerSymbolPane();
        }
        // Data Fetch Mode — save the three-way radio selection
        DataFetchMode chosenMode = selectedDataFetchMode();
        appSettings.setDataFetchMode(chosenMode);
        if (offlineModeCheck != null) {
            offlineModeCheck.setSelected(chosenMode == DataFetchMode.OFFLINE_ONLY);
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

        // Theme — persist the currently-selected theme (already applied live via onThemeChanged)
        appSettings.setTheme(selectedThemeId());

        // Sensitivity
        if (zoomSensitivitySlider != null) {
            double zv = Math.round(zoomSensitivitySlider.getValue() * 100.0) / 100.0;
            appSettings.setSetting("zoomSensitivity", String.valueOf(zv));
        }
        if (panSensitivitySlider != null) {
            double pv = Math.round(panSensitivitySlider.getValue() * 100.0) / 100.0;
            appSettings.setSetting("panSensitivity", String.valueOf(pv));
        }
        // Notify chart canvas of the new values
        if (onSensitivityChanged != null) {
            double zv = zoomSensitivitySlider != null ? zoomSensitivitySlider.getValue() : 0.4;
            double pv = panSensitivitySlider  != null ? panSensitivitySlider.getValue()  : 0.6;
            onSensitivityChanged.accept(new double[]{zv, pv});
        }

        // Ticker interval — save and immediately reschedule the poller
        if (tickerIntervalSpinner != null && tickerIntervalSpinner.getValue() != null) {
            appSettings.setTickerPollIntervalSeconds(tickerIntervalSpinner.getValue());
            liveTickerService.applyPollIntervalSetting();
        }
        // Ticker symbol settings are applied immediately via chip toggle listeners;
        // call applyTickerSymbolSettings once more on explicit save for safety.
        liveTickerService.applyTickerSymbolSettings();

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

        // ── Plan-filtered provider list ───────────────────────────────────────
        // enabledProvidersFor already filters by the current user's role/plan.
        List<MarketDataProvider> allowed = new ArrayList<>();
        allowed.add(MarketDataProvider.AUTO);
        allowed.addAll(priceRegistry.enabledProvidersFor(asset));

        // Determine the user's plan label for the hint
        com.mst.matt.tradingplatformapp.model.AppUser.Role userRole =
                authService.currentUser()
                        .map(com.mst.matt.tradingplatformapp.model.AppUser::getRole)
                        .orElse(com.mst.matt.tradingplatformapp.model.AppUser.Role.REGULAR_USER);

        chartProviderCombo.setItems(FXCollections.observableArrayList(allowed));
        chartProviderCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(MarketDataProvider p, boolean empty) {
                super.updateItem(p, empty);
                if (empty || p == null) { setText(null); return; }
                setText(p.getLabel());
            }
        });
        chartProviderCombo.setButtonCell(chartProviderCombo.getCellFactory().call(null));

        if (chartProvidersHint != null) {
            chartProvidersHint.setText(
                    "Plan: " + userRole.label()
                    + " · " + asset.name().charAt(0)
                    + asset.name().substring(1).toLowerCase()
                    + " providers: " + allowed.size()
                    + " available (configure API keys in application.properties).");
        }

        // If the currently-selected provider is no longer allowed, reset to AUTO
        MarketDataProvider current = chartProviderCombo.getValue();
        if (current != null && current != MarketDataProvider.AUTO
                && !allowed.contains(current)) {
            chartProviderCombo.setValue(MarketDataProvider.AUTO);
        }
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
