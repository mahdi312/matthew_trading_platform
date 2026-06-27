package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.model.*;
import com.mst.matt.tradingplatformapp.repository.IndicatorConfigRepository;
import com.mst.matt.tradingplatformapp.repository.SymbolEntryRepository;
import com.mst.matt.tradingplatformapp.service.OhlcvStorageService;
import com.mst.matt.tradingplatformapp.service.analysis.*;
import com.mst.matt.tradingplatformapp.service.price.PriceRouter;
import com.mst.matt.tradingplatformapp.ui.AutocompleteSymbolField;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import net.rgielen.fxweaver.core.FxmlView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

/**
 * Indicator Mixer controller.
 * Provides real-time signal preview as user adjusts indicator weights.
 */
@Component
@FxmlView("/fxml/IndicatorMixerView.fxml")
public class IndicatorMixerController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(IndicatorMixerController.class);

    // Symbol search / select
    @FXML private TextField    mixerSymbolField;
    @FXML private ComboBox<String> mixerSymbolCombo;
    /** Autocomplete field injected programmatically at initialize time. */
    private AutocompleteSymbolField autocompleteField;

    // Preset combo
    @FXML private ComboBox<String> profilePresetCombo;
    @FXML private VBox mixerContainer;

    // MACD
    @FXML private CheckBox macdEnabled;
    @FXML private Slider   macdSlider;
    @FXML private Label    macdWeightLabel;

    // RSI
    @FXML private CheckBox rsiEnabled;
    @FXML private Slider   rsiSlider;
    @FXML private Label    rsiWeightLabel;
    @FXML private Spinner<Integer> rsiPeriod, rsiOverbought, rsiOversold;

    // Ichimoku
    @FXML private CheckBox ichimokuEnabled;
    @FXML private Slider   ichimokuSlider;
    @FXML private Label    ichimokuWeightLabel;
    @FXML private Spinner<Integer> tenkanPeriod, kijunPeriod, senkouPeriod;

    // EMA
    @FXML private CheckBox emaEnabled;
    @FXML private Slider   emaSlider;
    @FXML private Label    emaWeightLabel;
    @FXML private Spinner<Integer> emaFast, emaSlow, goldShort, goldLong;

    // Bollinger
    @FXML private CheckBox bollingerEnabled;
    @FXML private Slider   bbSlider;
    @FXML private Label    bbWeightLabel;
    @FXML private Spinner<Integer> bbPeriod;
    @FXML private Spinner<Double>  bbDev;

    // Fibonacci
    @FXML private CheckBox fibEnabled;
    @FXML private Slider   fibSlider;
    @FXML private Label    fibWeightLabel;
    @FXML private Spinner<Integer> fibLookback;

    // Stochastic
    @FXML private CheckBox stochEnabled;
    @FXML private Slider   stochSlider;
    @FXML private Label    stochWeightLabel;

    // VWAP
    @FXML private CheckBox vwapEnabled;
    @FXML private Slider   vwapSlider;
    @FXML private Label    vwapWeightLabel;

    // CCI
    @FXML private CheckBox cciEnabled;
    @FXML private Slider   cciSlider;
    @FXML private Label    cciWeightLabel;

    // Preview panel
    @FXML private Label      previewSignalLabel, previewConfidence;
    @FXML private ProgressBar previewProgress;
    @FXML private Label       previewBuyLabel, previewSellLabel;
    @FXML private VBox        signalBreakdownBox;

    @Autowired private IndicatorConfigRepository configRepo;
    @Autowired private IndicatorService          indicatorService;
    @Autowired private SupportResistanceService  srService;
    @Autowired private SignalScoringService      scoringService;
    @Autowired private OhlcvStorageService       ohlcvStorage;
    @Autowired private AnalysisService           analysisService;
    @Autowired private PriceRouter               priceRouter;
    @Autowired private SymbolEntryRepository     symbolEntryRepository;

    private UserProfile activeProfile;
    private IndicatorResult lastIndicators;
    private SupportResistanceService.SRResult lastSr;
    private double lastPrice;
    private boolean applyingConfig;
    /** Currently selected symbol in the mixer (may differ from chart symbol). */
    private String mixerSymbol = "BTCUSDT";

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        if (profilePresetCombo != null) {
            profilePresetCombo.setItems(FXCollections.observableArrayList(
                    "Swing Trading", "Scalping", "Day Trading",
                    "Crypto Momentum", "Long Term", "Conservative", "Custom"
            ));
            profilePresetCombo.setValue("Swing Trading");
        }
        if (bbDev != null) {
            bbDev.setValueFactory(
                    new SpinnerValueFactory.DoubleSpinnerValueFactory(1.0, 5.0, 2.0, 0.1));
        }

        // ── Wire autocomplete symbol field ──────────────────────────────────
        // Replace the plain TextField with an AutocompleteSymbolField that
        // queries the SymbolEntry DB as the user types.
        // Re-create on each FXML reload (singleton bean).
        autocompleteField = new AutocompleteSymbolField(symbolEntryRepository);
        autocompleteField.setPrefWidth(200);
        autocompleteField.setText(mixerSymbol);
        autocompleteField.setOnSymbolSelected(sym -> {
            mixerSymbol = sym.trim().toUpperCase();
            reloadMixerIndicators();
        });
        // Keep plain text field in sync (for onMixerSymbolChanged handler)
        autocompleteField.textProperty().addListener((o, a, text) -> {
            if (text != null && !text.isBlank()) {
                mixerSymbol = text.trim().toUpperCase();
                if (mixerSymbolField != null) mixerSymbolField.setText(mixerSymbol);
            }
        });
        if (mixerSymbolField != null && mixerSymbolField.getParent() instanceof HBox headerBox) {
            // Replace the old TextField in the header HBox
            int idx = headerBox.getChildren().indexOf(mixerSymbolField);
            if (idx >= 0) {
                headerBox.getChildren().set(idx, autocompleteField);
            }
        }
        // Also wire the existing field as fallback for onMixerSymbolChanged
        if (mixerSymbolField != null) {
            mixerSymbolField.setText(mixerSymbol);
            mixerSymbolField.textProperty().addListener((o, a, text) -> {
                if (text != null && !text.isBlank())
                    mixerSymbol = text.trim().toUpperCase();
            });
        }
        populateMixerSymbolCombo();
        if (mixerSymbolCombo != null) {
            mixerSymbolCombo.valueProperty().addListener((o, a, sym) -> {
                if (sym != null && !sym.isBlank()) {
                    mixerSymbol = sym.trim().toUpperCase();
                    if (autocompleteField != null) autocompleteField.setSymbol(mixerSymbol);
                    else if (mixerSymbolField != null) mixerSymbolField.setText(mixerSymbol);
                    reloadMixerIndicators();
                }
            });
        }

        // Wire all sliders to update labels and re-score
        wireSlider(macdSlider,     macdWeightLabel,     "Weight: ");
        wireSlider(rsiSlider,      rsiWeightLabel,      "Weight: ");
        wireSlider(ichimokuSlider, ichimokuWeightLabel, "Weight: ");
        wireSlider(emaSlider,      emaWeightLabel,      "Weight: ");
        wireSlider(bbSlider,       bbWeightLabel,       "Weight: ");
        wireSlider(fibSlider,      fibWeightLabel,      "Weight: ");
        wireSlider(stochSlider,    stochWeightLabel,    "Weight: ");
        wireSlider(vwapSlider,     vwapWeightLabel,     "Weight: ");
        wireSlider(cciSlider,      cciWeightLabel,      "Weight: ");

        wireControls();
    }

    // ── Symbol search helpers ──────────────────────────────────

    /**
     * Populate the symbol ComboBox from DB + a default list.
     */
    private void populateMixerSymbolCombo() {
        if (mixerSymbolCombo == null) return;
        Thread.ofVirtual().start(() -> {
            try {
                List<String> symbols = symbolEntryRepository.findAll()
                        .stream().map(SymbolEntry::getSymbol).sorted().collect(Collectors.toList());
                if (symbols.isEmpty()) {
                    symbols = List.of("BTCUSDT","ETHUSDT","SOLUSDT","BNBUSDT",
                            "AAPL","TSLA","MSFT","EURUSD","GBPUSD");
                }
                final List<String> finalSymbols = symbols;
                Platform.runLater(() -> {
                    mixerSymbolCombo.setItems(FXCollections.observableArrayList(finalSymbols));
                    if (finalSymbols.contains(mixerSymbol)) {
                        mixerSymbolCombo.setValue(mixerSymbol);
                    } else if (!finalSymbols.isEmpty()) {
                        mixerSymbolCombo.setValue(finalSymbols.get(0));
                    }
                });
            } catch (Exception ex) {
                log.warn("Could not populate mixer symbol combo: {}", ex.getMessage());
            }
        });
    }

    /**
     * Triggered when the user clicks the refresh button or presses Enter in the symbol field.
     * Reads the symbol from the text field, then loads indicators for that symbol.
     */
    @FXML public void onMixerSymbolChanged() {
        if (mixerSymbolField != null && !mixerSymbolField.getText().isBlank()) {
            mixerSymbol = mixerSymbolField.getText().trim().toUpperCase();
        }
        reloadMixerIndicators();
    }

    /**
     * Triggered when the user selects a symbol from the combo dropdown.
     */
    @FXML public void onMixerSymbolComboChanged() {
        if (mixerSymbolCombo != null && mixerSymbolCombo.getValue() != null) {
            mixerSymbol = mixerSymbolCombo.getValue().trim().toUpperCase();
            if (mixerSymbolField != null) mixerSymbolField.setText(mixerSymbol);
            reloadMixerIndicators();
        }
    }

    /**
     * Loads OHLCV data and re-computes indicators for the current mixerSymbol.
     * Runs analysis in a background thread to keep the UI responsive.
     */
    private void reloadMixerIndicators() {
        if (activeProfile == null || mixerSymbol == null || mixerSymbol.isBlank()) return;
        // Show loading hint
        Platform.runLater(() -> {
            if (previewSignalLabel != null) {
                previewSignalLabel.setText("⏳ Loading " + mixerSymbol + "…");
                previewSignalLabel.setStyle("-fx-font-size:14px; -fx-text-fill:#8b949e;");
            }
        });
        Thread.ofVirtual().start(() -> {
            try {
                AnalysisService.AnalysisResult result =
                        analysisService.analyze(mixerSymbol, "1h", 200, activeProfile);
                double price = result.getBars().isEmpty() ? 0.0
                        : result.getBars().get(result.getBars().size() - 1).getClose().doubleValue();
                var quoteOpt = priceRouter.getQuote(mixerSymbol, activeProfile);
                double livePrice = quoteOpt
                        .filter(q -> q.getPrice() != null)
                        .map(q -> q.getPrice().doubleValue())
                        .orElse(price);
                if (result.getIndicators() != null && result.getSrResult() != null) {
                    setIndicatorData(result.getIndicators(), result.getSrResult(), livePrice);
                } else {
                    Platform.runLater(() -> {
                        if (previewSignalLabel != null) {
                            previewSignalLabel.setText("⚠ No data for " + mixerSymbol);
                            previewSignalLabel.setStyle("-fx-font-size:14px; -fx-text-fill:#d29922;");
                        }
                    });
                }
            } catch (Exception ex) {
                log.warn("Mixer indicator reload failed for {}: {}", mixerSymbol, ex.getMessage());
                Platform.runLater(() -> {
                    if (previewSignalLabel != null) {
                        previewSignalLabel.setText("⚠ Error loading " + mixerSymbol);
                        previewSignalLabel.setStyle("-fx-font-size:14px; -fx-text-fill:#f85149;");
                    }
                });
            }
        });
    }

    public void setProfile(UserProfile profile) {
        this.activeProfile = profile;
        if (profile == null) { loadConfig(); return; }

        // Always update mixer symbol to match the new profile's default symbol.
        // This ensures the symbol field stays in sync whenever the active profile changes.
        String profileSymbol = profile.getDefaultSymbol();
        if (profileSymbol != null && !profileSymbol.isBlank()) {
            String newSym = profileSymbol.toUpperCase();
            if (!newSym.equals(mixerSymbol)) {
                mixerSymbol = newSym;
                // Sync all symbol UI components
                if (autocompleteField != null) {
                    autocompleteField.setSymbol(mixerSymbol);
                } else if (mixerSymbolField != null) {
                    mixerSymbolField.setText(mixerSymbol);
                }
            }
        }

        // Always rebuild the symbol combo for the new profile's asset focus
        // (so the dropdown shows relevant symbols for crypto/stock/forex profiles)
        populateMixerSymbolComboForProfile(profile);

        loadConfig();
        // Reload indicators for the (potentially new) symbol
        reloadMixerIndicators();
    }

    /**
     * Populate the mixer symbol combo filtered for the given profile's asset focus.
     * Falls back to the full list if no focus-specific symbols are available.
     */
    private void populateMixerSymbolComboForProfile(UserProfile profile) {
        if (mixerSymbolCombo == null) return;
        Thread.ofVirtual().start(() -> {
            try {
                List<String> symbols = symbolEntryRepository.findAll()
                        .stream().map(SymbolEntry::getSymbol).sorted()
                        .collect(Collectors.toList());

                // Filter by asset focus when possible
                if (profile != null && profile.getAssetFocus() != null && !symbols.isEmpty()) {
                    String focus = profile.getAssetFocus().name(); // CRYPTO / STOCK / FOREX / MULTI
                    List<String> filtered = symbols.stream().filter(s -> {
                        switch (focus) {
                            case "CRYPTO" -> { return s.endsWith("USDT") || s.endsWith("BTC"); }
                            case "FOREX"  -> { return s.length() == 6 && s.matches("[A-Z]+"); }
                            case "STOCK"  -> { return !s.endsWith("USDT") && !s.endsWith("BTC")
                                    && !(s.length() == 6 && s.matches("[A-Z]+")); }
                            default -> { return true; } // MULTI: show all
                        }
                    }).collect(Collectors.toList());
                    if (!filtered.isEmpty()) symbols = filtered;
                }

                if (symbols.isEmpty()) {
                    symbols = List.of("BTCUSDT","ETHUSDT","SOLUSDT","BNBUSDT",
                            "AAPL","TSLA","MSFT","EURUSD","GBPUSD");
                }
                final List<String> finalSymbols = symbols;
                final String sym = mixerSymbol;
                Platform.runLater(() -> {
                    mixerSymbolCombo.setItems(FXCollections.observableArrayList(finalSymbols));
                    // Select the current mixerSymbol if it's in the list, else select first
                    if (finalSymbols.contains(sym)) {
                        mixerSymbolCombo.setValue(sym);
                    } else if (!finalSymbols.isEmpty()) {
                        mixerSymbolCombo.setValue(finalSymbols.get(0));
                    }
                });
            } catch (Exception ex) {
                log.warn("Could not populate mixer symbol combo for profile: {}", ex.getMessage());
            }
        });
    }

    public void setIndicatorData(IndicatorResult indicators,
                                 SupportResistanceService.SRResult sr,
                                 double price) {
        this.lastIndicators = indicators;
        this.lastSr         = sr;
        this.lastPrice      = price;
        updatePreview();
    }

    // ── Load / Save Config ─────────────────────────────────────

    private void loadConfig() {
        if (activeProfile == null) return;
        Optional<IndicatorConfig> opt = configRepo.findByProfile(activeProfile);
        IndicatorConfig config = opt.orElse(
                IndicatorConfig.fromProfile(
                        IndicatorConfig.IndicatorProfile.SWING_TRADING, activeProfile));
        applyConfigToUI(config);
        updatePreview();
    }

    @FXML public void onSave() {
        if (activeProfile == null) return;
        IndicatorConfig config = buildConfigFromUI();
        configRepo.save(config);
        showSavedToast();
    }

    @FXML public void onReset() { loadConfig(); }

    @FXML public void onPresetSelected() {
        if (applyingConfig || profilePresetCombo == null) return;
        String preset = profilePresetCombo.getValue();
        if (preset == null || activeProfile == null) return;

        IndicatorConfig.IndicatorProfile profile = toIndicatorProfile(preset);

        IndicatorConfig preset_cfg = IndicatorConfig.fromProfile(
                profile, activeProfile);
        applyConfigToUI(preset_cfg);
        updatePreview();
    }

    // ── UI ↔ Config conversion ─────────────────────────────────

    private void applyConfigToUI(IndicatorConfig c) {
        applyingConfig = true;
        try {
            if (profilePresetCombo != null) {
                profilePresetCombo.setValue(toPresetLabel(c.getActiveProfile()));
            }

            macdEnabled.setSelected(c.isMacdEnabled());
            macdSlider.setValue(c.getMacdWeight());

            rsiEnabled.setSelected(c.isRsiEnabled());
            rsiSlider.setValue(c.getRsiWeight());
            setSpinnerValue(rsiPeriod, c.getRsiPeriod());
            setSpinnerValue(rsiOverbought, c.getRsiOverbought());
            setSpinnerValue(rsiOversold, c.getRsiOversold());

            ichimokuEnabled.setSelected(c.isIchimokuEnabled());
            ichimokuSlider.setValue(c.getIchimokuWeight());
            setSpinnerValue(tenkanPeriod, c.getIchimokuTenkanPeriod());
            setSpinnerValue(kijunPeriod, c.getIchimokuKijunPeriod());
            setSpinnerValue(senkouPeriod, c.getIchimokuSenkouPeriod());

            emaEnabled.setSelected(c.isEmaEnabled());
            emaSlider.setValue(c.getEmaWeight());
            setSpinnerValue(emaFast, c.getEmaFastPeriod());
            setSpinnerValue(emaSlow, c.getEmaSlowPeriod());
            setSpinnerValue(goldShort, c.getGoldCrossShortPeriod());
            setSpinnerValue(goldLong, c.getGoldCrossLongPeriod());

            bollingerEnabled.setSelected(c.isBollingerEnabled());
            bbSlider.setValue(c.getBollingerWeight());
            setSpinnerValue(bbPeriod, c.getBollingerPeriod());
            if (c.getBollingerDeviation() > 0) bbDev.getValueFactory()
                    .setValue(c.getBollingerDeviation());

            fibEnabled.setSelected(c.isFibonacciEnabled());
            fibSlider.setValue(c.getFibonacciWeight());
            setSpinnerValue(fibLookback, c.getFibonacciLookback());

            stochEnabled.setSelected(c.isStochasticEnabled());
            stochSlider.setValue(c.getStochasticWeight());

            vwapEnabled.setSelected(c.isVwapEnabled());
            vwapSlider.setValue(c.getVwapWeight());

            cciEnabled.setSelected(c.isCciEnabled());
            cciSlider.setValue(c.getCciWeight());
        } finally {
            applyingConfig = false;
        }
    }

    private IndicatorConfig buildConfigFromUI() {
        Optional<IndicatorConfig> opt = activeProfile != null
                ? configRepo.findByProfile(activeProfile) : Optional.empty();
        IndicatorConfig c = opt.orElse(new IndicatorConfig());
        c.setProfile(activeProfile);
        c.setActiveProfile(toIndicatorProfile(
                profilePresetCombo != null ? profilePresetCombo.getValue() : "Swing Trading"));

        c.setMacdEnabled(macdEnabled.isSelected());
        c.setMacdWeight((int)macdSlider.getValue());

        c.setRsiEnabled(rsiEnabled.isSelected());
        c.setRsiWeight((int)rsiSlider.getValue());
        c.setRsiPeriod(rsiPeriod.getValue());
        c.setRsiOverbought(rsiOverbought.getValue());
        c.setRsiOversold(rsiOversold.getValue());

        c.setIchimokuEnabled(ichimokuEnabled.isSelected());
        c.setIchimokuWeight((int)ichimokuSlider.getValue());
        c.setIchimokuTenkanPeriod(tenkanPeriod.getValue());
        c.setIchimokuKijunPeriod(kijunPeriod.getValue());
        c.setIchimokuSenkouPeriod(senkouPeriod.getValue());

        c.setEmaEnabled(emaEnabled.isSelected());
        c.setEmaWeight((int)emaSlider.getValue());
        c.setEmaFastPeriod(emaFast.getValue());
        c.setEmaSlowPeriod(emaSlow.getValue());
        c.setGoldCrossShortPeriod(goldShort.getValue());
        c.setGoldCrossLongPeriod(goldLong.getValue());

        c.setBollingerEnabled(bollingerEnabled.isSelected());
        c.setBollingerWeight((int)bbSlider.getValue());
        c.setBollingerPeriod(bbPeriod.getValue());
        c.setBollingerDeviation(bbDev.getValue());

        c.setFibonacciEnabled(fibEnabled.isSelected());
        c.setFibonacciWeight((int)fibSlider.getValue());
        c.setFibonacciLookback(fibLookback.getValue());

        c.setStochasticEnabled(stochEnabled.isSelected());
        c.setStochasticWeight((int)stochSlider.getValue());

        c.setVwapEnabled(vwapEnabled.isSelected());
        c.setVwapWeight((int)vwapSlider.getValue());

        c.setCciEnabled(cciEnabled.isSelected());
        c.setCciWeight((int)cciSlider.getValue());

        return c;
    }

    private void wireSlider(Slider slider, Label label, String prefix) {
        slider.valueProperty().addListener((o, a, n) -> {
            label.setText(prefix + n.intValue());
            updatePreview();
        });
    }

    private void wireControls() {
        for (CheckBox cb : new CheckBox[]{macdEnabled, rsiEnabled, ichimokuEnabled,
                emaEnabled, bollingerEnabled, fibEnabled, stochEnabled, vwapEnabled,
                cciEnabled}) {
            cb.selectedProperty().addListener((o, a, n) -> updatePreview());
        }

        for (Spinner<?> spinner : new Spinner<?>[]{rsiPeriod, rsiOverbought,
                rsiOversold, tenkanPeriod, kijunPeriod, senkouPeriod, emaFast,
                emaSlow, goldShort, goldLong, bbPeriod, bbDev, fibLookback}) {
            spinner.valueProperty().addListener((o, a, n) -> updatePreview());
        }
    }

    private void setSpinnerValue(Spinner<Integer> spinner, int value) {
        if (value > 0) spinner.getValueFactory().setValue(value);
    }

    private IndicatorConfig.IndicatorProfile toIndicatorProfile(String preset) {
        if (preset == null) return IndicatorConfig.IndicatorProfile.SWING_TRADING;
        return switch (preset) {
            case "Scalping"          -> IndicatorConfig.IndicatorProfile.SCALPING;
            case "Crypto Momentum"   -> IndicatorConfig.IndicatorProfile.CRYPTO_MOMENTUM;
            case "Day Trading"       -> IndicatorConfig.IndicatorProfile.DAY_TRADING;
            case "Long Term"         -> IndicatorConfig.IndicatorProfile.LONG_TERM;
            case "Conservative"      -> IndicatorConfig.IndicatorProfile.CONSERVATIVE;
            case "Custom"            -> IndicatorConfig.IndicatorProfile.CUSTOM;
            default                  -> IndicatorConfig.IndicatorProfile.SWING_TRADING;
        };
    }

    private String toPresetLabel(IndicatorConfig.IndicatorProfile profile) {
        if (profile == null) return "Swing Trading";
        return switch (profile) {
            case SCALPING        -> "Scalping";
            case DAY_TRADING     -> "Day Trading";
            case CRYPTO_MOMENTUM -> "Crypto Momentum";
            case LONG_TERM       -> "Long Term";
            case CONSERVATIVE    -> "Conservative";
            case CUSTOM          -> "Custom";
            case SWING_TRADING   -> "Swing Trading";
        };
    }

    private void updatePreview() {
        if (applyingConfig || lastIndicators == null || lastSr == null
                || previewSignalLabel == null) return;

        IndicatorConfig config = buildConfigFromUI();
        SignalScoringService.SignalResult result =
                scoringService.score(lastIndicators, lastSr, config, lastPrice);

        Platform.runLater(() -> {
            SignalScoringService.Recommendation rec = result.getRecommendation();
            previewSignalLabel.setText(rec.label);
            previewSignalLabel.setStyle("-fx-font-size:20px; -fx-font-weight:bold;"
                    + "-fx-text-fill:" + rec.textColor + ";");
            previewConfidence.setText(String.format("Confidence: %.1f%%",
                    result.getConfidence()));
            previewProgress.setProgress(result.getConfidence() / 100.0);
            previewBuyLabel.setText("$" + String.format("%.4f",
                    result.getBestBuyPrice()));
            previewSellLabel.setText("$" + String.format("%.4f",
                    result.getBestSellPrice()));

            // Signal breakdown
            signalBreakdownBox.getChildren().clear();
            Label hdr = new Label("Signal Breakdown");
            hdr.setStyle("-fx-text-fill:#8b949e;");
            signalBreakdownBox.getChildren().add(hdr);

            for (SignalScoringService.WeightedSignal ws
                    : result.getIndividualSignals()) {
                HBox row = new HBox(8);
                Label name = new Label(ws.name());
                name.setStyle("-fx-text-fill:#e6edf3; -fx-min-width:130px;");
                Label sig  = new Label(ws.signal() > 0 ? "🟢 BULL"
                        : ws.signal() < 0 ? "🔴 BEAR" : "⚪ NEUT");
                sig.setStyle(ws.signal() > 0 ? "-fx-text-fill:#3fb950;"
                        : ws.signal() < 0 ? "-fx-text-fill:#f85149;"
                        : "-fx-text-fill:#8b949e;");
                Label w = new Label("×" + ws.weight());
                w.setStyle("-fx-text-fill:#484f58;");
                row.getChildren().addAll(name, sig, w);
                signalBreakdownBox.getChildren().add(row);
            }
        });
    }

    private void showSavedToast() {
        previewSignalLabel.setText("✅ Configuration Saved!");
        previewSignalLabel.setStyle("-fx-font-size:14px; -fx-text-fill:#3fb950;");
    }
}
