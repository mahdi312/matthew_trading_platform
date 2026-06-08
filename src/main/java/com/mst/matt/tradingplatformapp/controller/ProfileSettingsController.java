package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.model.UserProfile;
import com.mst.matt.tradingplatformapp.model.UserProfile.ProfileAssetFocus;
import com.mst.matt.tradingplatformapp.repository.UserProfileRepository;
import com.mst.matt.tradingplatformapp.service.AppSettingsService;
import com.mst.matt.tradingplatformapp.service.ProfilePersistenceService;
import com.mst.matt.tradingplatformapp.service.WatchlistDefaults;
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
import javafx.scene.layout.VBox;
import net.rgielen.fxweaver.core.FxmlView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-profile market data preferences: asset focus, default symbol, chart & fundamental providers.
 */
@Component
@FxmlView("/fxml/ProfileSettingsView.fxml")
public class ProfileSettingsController {

    @FXML private VBox settingsRoot;
    @FXML private Label profileNameLabel;
    @FXML private ComboBox<ProfileAssetFocus> assetFocusCombo;
    @FXML private TextField defaultSymbolField;
    @FXML private ComboBox<MarketDataProvider> chartProviderCombo;
    @FXML private ComboBox<FundamentalDataProvider> fundamentalProviderCombo;
    @FXML private Label chartProvidersHint;
    @FXML private Label savedLabel;
    /** T-12: watchlist editor. */
    @FXML private TextArea watchlistField;
    @FXML private CheckBox offlineModeCheck;

    @Autowired private UserProfileRepository profileRepository;
    @Autowired private AppSettingsService appSettings;
    /** P1 (LOG-FIX): async writer to keep JavaFX thread off JPA commits. */
    @Autowired private ProfilePersistenceService profilePersistence;
    @Autowired private PriceProviderRegistry priceRegistry;
    @Autowired private FundamentalRouter fundamentalRouter;
    @Autowired private PriceRouter priceRouter;
    @Autowired private LiveTickerService liveTickerService;

    private UserProfile activeProfile;

    @FXML
    public void initialize() {
        assetFocusCombo.setItems(FXCollections.observableArrayList(
                ProfileAssetFocus.values()));
        assetFocusCombo.setCellFactory(lv -> labelCell());
        assetFocusCombo.setButtonCell(labelCell());

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
    }

    public void setProfile(UserProfile profile) {
        this.activeProfile = profile;
        if (profile == null) return;
        // P2 (LOG-FIX): never let a null assetFocus reach the combo or symbol lookup
        // (legacy rows persisted before the nullable=false migration may still be null).
        ProfileAssetFocus focus = profile.getAssetFocus() != null
                ? profile.getAssetFocus()
                : ProfileAssetFocus.MULTI;
        if (profile.getAssetFocus() == null) {
            profile.setAssetFocus(focus);
        }
        profileNameLabel.setText(profile.getName());
        assetFocusCombo.setValue(focus);
        defaultSymbolField.setText(profile.getDefaultSymbol() != null
                ? profile.getDefaultSymbol()
                : focus.defaultSymbol());
        refreshChartProviders();
        chartProviderCombo.setValue(
                MarketDataProvider.fromString(profile.getChartProvider()));
        fundamentalProviderCombo.setValue(
                FundamentalDataProvider.fromString(profile.getFundamentalProvider()));
        // T-12: preload watchlist (may be null → leave blank to fall back to defaults).
        if (watchlistField != null) {
            watchlistField.setText(profile.getWatchlist() == null
                    ? WatchlistDefaults.csvForFocus(focus)
                    : profile.getWatchlist());
        }
        if (offlineModeCheck != null) {
            offlineModeCheck.setSelected(appSettings.isOfflineMode());
        }
        savedLabel.setText("");
    }

    @FXML public void onOfflineModeChanged() {
        if (offlineModeCheck == null) return;
        appSettings.setApiFetchEnabled(!offlineModeCheck.isSelected());
        savedLabel.setText(offlineModeCheck.isSelected()
                ? "Offline mode on — using cached data."
                : "Live API fetching enabled.");
    }

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

    @FXML public void onAssetFocusChanged() {
        refreshChartProviders();
    }

    @FXML public void onSave() {
        if (activeProfile == null) return;
        ProfileAssetFocus focus = assetFocusCombo.getValue();
        if (focus != null) activeProfile.setAssetFocus(focus);
        String sym = defaultSymbolField.getText();
        activeProfile.setDefaultSymbol(sym != null ? sym.trim().toUpperCase() : null);
        MarketDataProvider chart = chartProviderCombo.getValue();
        if (chart != null) activeProfile.setChartProvider(chart.name());
        FundamentalDataProvider fund = fundamentalProviderCombo.getValue();
        if (fund != null) activeProfile.setFundamentalProvider(fund.name());

        // T-12: persist the watchlist string and push it to LiveTickerService.
        if (watchlistField != null) {
            String csv = watchlistField.getText() == null ? ""
                    : watchlistField.getText().trim();
            activeProfile.setWatchlist(csv.isEmpty() ? null : csv.toUpperCase());
            liveTickerService.applyProfileWatchlist(activeProfile);
        }
        if (offlineModeCheck != null) {
            appSettings.setApiFetchEnabled(!offlineModeCheck.isSelected());
        }

        // P1 (LOG-FIX): non-blocking save.
        profilePersistence.saveAsync(activeProfile);
        priceRouter.setActiveProfile(activeProfile);
        savedLabel.setText("Saved.");
    }

    private static <T extends Enum<T>> ListCell<T> labelCell() {
        return new ListCell<>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        };
    }
}
