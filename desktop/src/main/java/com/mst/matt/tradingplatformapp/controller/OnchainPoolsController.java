package com.mst.matt.tradingplatformapp.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mst.matt.tradingplatformapp.model.OhlcvBar;
import com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoDexService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import net.rgielen.fxweaver.core.FxmlView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Controller for the Onchain Pools tab — DEX pool discovery and OHLCV preview.
 */
@Component
@FxmlView("/fxml/OnchainPoolsView.fxml")
public class OnchainPoolsController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(OnchainPoolsController.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML private ComboBox<String> networkSelector;
    @FXML private ToggleButton topPoolsBtn;
    @FXML private ToggleButton trendingBtn;
    @FXML private TextField searchField;
    @FXML private Button refreshBtn;
    @FXML private ProgressIndicator loadingSpinner;
    @FXML private Label lastUpdateLabel;
    @FXML private Label statusLabel;
    @FXML private VBox poolsContainer;
    @FXML private VBox ohlcvContainer;
    @FXML private Label poolNameLabel;
    @FXML private Label poolAddressLabel;
    @FXML private Label poolPriceLabel;
    @FXML private Label poolLiquidityLabel;
    @FXML private Label poolVolumeLabel;
    @FXML private Label poolNetworkLabel;
    @FXML private Label pageLabel;
    @FXML private Button prevPageBtn;
    @FXML private Button nextPageBtn;
    @FXML private ToggleButton tfMinute;
    @FXML private ToggleButton tfHour;
    @FXML private ToggleButton tf4Hour;
    @FXML private ToggleButton tfDay;

    @Autowired private CoinGeckoDexService dexService;

    private int currentPage = 1;
    private boolean trendingMode = false;
    private String selectedNetwork = "eth";
    private String selectedPoolAddress;
    private String ohlcvTimeframe = "hour";
    private int ohlcvAggregate = 1;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        ToggleGroup viewGroup = new ToggleGroup();
        topPoolsBtn.setToggleGroup(viewGroup);
        trendingBtn.setToggleGroup(viewGroup);
        ToggleGroup tfGroup = new ToggleGroup();
        tfMinute.setToggleGroup(tfGroup);
        tfHour.setToggleGroup(tfGroup);
        tf4Hour.setToggleGroup(tfGroup);
        tfDay.setToggleGroup(tfGroup);

        loadNetworks();
    }

    @FXML private void onRefresh() {
        currentPage = 1;
        loadPools();
    }

    @FXML private void onViewTopPools() {
        trendingMode = false;
        currentPage = 1;
        loadPools();
    }

    @FXML private void onViewTrending() {
        trendingMode = true;
        currentPage = 1;
        loadPools();
    }

    @FXML private void onSearchPools() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim();
        if (query.isBlank()) return;
        setLoading(true);
        Thread.ofVirtual().name("pool-search").start(() -> {
            try {
                Optional<JsonObject> result = dexService.searchPools(query);
                Platform.runLater(() -> {
                    result.ifPresentOrElse(
                            obj -> renderPoolsFromJson(obj, false),
                            () -> {
                                poolsContainer.getChildren().clear();
                                setStatus("No pools found for: " + query);
                            });
                    lastUpdateLabel.setText("Last updated: " + LocalDateTime.now().format(DT_FMT));
                    setLoading(false);
                });
            } catch (Exception e) {
                log.error("Pool search error: {}", e.getMessage(), e);
                Platform.runLater(() -> {
                    setStatus("Search failed: " + e.getMessage());
                    setLoading(false);
                });
            }
        });
    }

    @FXML private void onPrevPage() {
        if (currentPage <= 1) return;
        currentPage--;
        loadPools();
    }

    @FXML private void onNextPage() {
        currentPage++;
        loadPools();
    }

    @FXML private void onTimeframeChange() {
        ToggleButton selected = (ToggleButton) tfMinute.getToggleGroup().getSelectedToggle();
        if (selected == null || selected.getUserData() == null) return;
        String[] parts = selected.getUserData().toString().split("\\|");
        ohlcvTimeframe = parts[0];
        ohlcvAggregate = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
        styleTimeframeButtons(selected);
    }

    @FXML private void onLoadPoolOhlcv() {
        if (selectedPoolAddress == null || selectedNetwork == null) {
            setStatus("Select a pool first.");
            return;
        }
        setLoading(true);
        Thread.ofVirtual().name("pool-ohlcv").start(() -> {
            try {
                List<OhlcvBar> bars = dexService.getPoolOhlcv(
                        selectedNetwork, selectedPoolAddress, ohlcvTimeframe, ohlcvAggregate, 50);
                Platform.runLater(() -> {
                    renderOhlcv(bars);
                    setStatus(bars.isEmpty()
                            ? "No OHLCV data returned for this pool/timeframe."
                            : "Loaded " + bars.size() + " OHLCV bars.");
                    setLoading(false);
                });
            } catch (Exception e) {
                log.error("Pool OHLCV error: {}", e.getMessage(), e);
                Platform.runLater(() -> {
                    setStatus("OHLCV load failed: " + e.getMessage());
                    setLoading(false);
                });
            }
        });
    }

    private void loadNetworks() {
        Thread.ofVirtual().name("onchain-networks").start(() -> {
            try {
                List<String> ids = dexService.getNetworkIds();
                Platform.runLater(() -> {
                    networkSelector.getItems().setAll(ids.isEmpty()
                            ? List.of("eth", "bsc", "polygon_pos", "arbitrum", "base") : ids);
                    if (!networkSelector.getItems().isEmpty()) {
                        networkSelector.setValue(
                                networkSelector.getItems().contains("eth") ? "eth"
                                        : networkSelector.getItems().get(0));
                        selectedNetwork = networkSelector.getValue();
                    }
                    networkSelector.valueProperty().addListener((o, a, n) -> {
                        if (n != null) {
                            selectedNetwork = n;
                            currentPage = 1;
                            loadPools();
                        }
                    });
                    loadPools();
                });
            } catch (Exception e) {
                log.warn("Failed to load networks: {}", e.getMessage());
                Platform.runLater(this::loadPools);
            }
        });
    }

    private void loadPools() {
        if (selectedNetwork == null && networkSelector.getValue() != null) {
            selectedNetwork = networkSelector.getValue();
        }
        setLoading(true);
        final int page = currentPage;
        final String network = selectedNetwork != null ? selectedNetwork : "eth";
        Thread.ofVirtual().name("pool-list-" + page).start(() -> {
            try {
                Optional<JsonObject> result = trendingMode
                        ? (page == 1
                            ? dexService.getTrendingPoolsGlobal()
                            : dexService.getTrendingPoolsByNetwork(network))
                        : dexService.getTopPools(network, page);
                Platform.runLater(() -> {
                    result.ifPresentOrElse(
                            obj -> renderPoolsFromJson(obj, !trendingMode),
                            () -> {
                                poolsContainer.getChildren().clear();
                                setStatus("Failed to load pools. Check API key or network.");
                            });
                    pageLabel.setText("Page " + page);
                    prevPageBtn.setDisable(page <= 1);
                    lastUpdateLabel.setText("Last updated: " + LocalDateTime.now().format(DT_FMT));
                    setLoading(false);
                });
            } catch (Exception e) {
                log.error("Pool list error: {}", e.getMessage(), e);
                Platform.runLater(() -> {
                    setStatus("Load failed: " + e.getMessage());
                    setLoading(false);
                });
            }
        });
    }

    private void renderPoolsFromJson(JsonObject root, boolean enablePagination) {
        JsonArray data = extractDataArray(root);
        poolsContainer.getChildren().clear();
        if (data == null || data.isEmpty()) {
            setStatus("No pools in response.");
            nextPageBtn.setDisable(true);
            return;
        }
        for (JsonElement el : data) {
            if (!el.isJsonObject()) continue;
            JsonObject pool = el.getAsJsonObject();
            poolsContainer.getChildren().add(buildPoolRow(pool));
        }
        nextPageBtn.setDisable(!enablePagination || data.size() < 20);
        setStatus("");
    }

    private HBox buildPoolRow(JsonObject pool) {
        JsonObject attrs = pool.has("attributes") ? pool.getAsJsonObject("attributes") : pool;
        String name = attrs.has("name") ? attrs.get("name").getAsString()
                : (pool.has("id") ? pool.get("id").getAsString() : "—");
        String address = attrs.has("address") ? attrs.get("address").getAsString() : extractAddressFromId(pool);
        String price = attrs.has("base_token_price_usd")
                ? formatNumber(attrs.get("base_token_price_usd").getAsString()) : "—";
        String liquidity = attrs.has("reserve_in_usd")
                ? formatLargeUsd(new BigDecimal(attrs.get("reserve_in_usd").getAsString())) : "—";

        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding:7 12; -fx-border-color:#30363d; -fx-border-width:0 0 1 0; -fx-cursor:hand;");

        Label nameLbl = cellLabel(name, 0, true);
        Label priceLbl = cellLabel(price, 100, false);
        priceLbl.setAlignment(Pos.CENTER_RIGHT);
        Label liqLbl = cellLabel(liquidity, 100, false);
        liqLbl.setAlignment(Pos.CENTER_RIGHT);
        row.getChildren().addAll(nameLbl, priceLbl, liqLbl);

        row.setOnMouseClicked(e -> selectPool(pool, name, address, attrs));
        return row;
    }

    private void selectPool(JsonObject pool, String name, String address, JsonObject attrs) {
        selectedPoolAddress = address;
        if (selectedNetwork == null && pool.has("id")) {
            String id = pool.get("id").getAsString();
            int idx = id.indexOf('_');
            if (idx > 0) selectedNetwork = id.substring(0, idx);
        }
        poolNameLabel.setText(name);
        poolAddressLabel.setText(address != null ? address : "—");
        poolNetworkLabel.setText(selectedNetwork != null ? selectedNetwork : "—");
        poolPriceLabel.setText(attrs.has("base_token_price_usd")
                ? "$" + formatNumber(attrs.get("base_token_price_usd").getAsString()) : "—");
        poolLiquidityLabel.setText(attrs.has("reserve_in_usd")
                ? formatLargeUsd(new BigDecimal(attrs.get("reserve_in_usd").getAsString())) : "—");
        if (attrs.has("volume_usd") && attrs.get("volume_usd").isJsonObject()) {
            JsonObject vol = attrs.getAsJsonObject("volume_usd");
            if (vol.has("h24")) {
                poolVolumeLabel.setText(formatLargeUsd(new BigDecimal(vol.get("h24").getAsString())));
            }
        }
        ohlcvContainer.getChildren().clear();
        setStatus("Pool selected — click Load OHLCV to fetch candles.");
    }

    private void renderOhlcv(List<OhlcvBar> bars) {
        ohlcvContainer.getChildren().clear();
        for (OhlcvBar bar : bars) {
            HBox row = new HBox();
            row.setStyle("-fx-padding:5 12; -fx-border-color:#30363d; -fx-border-width:0 0 1 0;");
            Label timeLbl = cellLabel(bar.getOpenTime() != null ? bar.getOpenTime().toString() : "—", 140, false);
            Label openLbl = cellLabel(formatPrice(bar.getOpen()), 0, true);
            openLbl.setAlignment(Pos.CENTER_RIGHT);
            Label highLbl = cellLabel(formatPrice(bar.getHigh()), 90, false);
            highLbl.setAlignment(Pos.CENTER_RIGHT);
            Label lowLbl = cellLabel(formatPrice(bar.getLow()), 90, false);
            lowLbl.setAlignment(Pos.CENTER_RIGHT);
            Label closeLbl = cellLabel(formatPrice(bar.getClose()), 90, false);
            closeLbl.setAlignment(Pos.CENTER_RIGHT);
            Label volLbl = cellLabel(formatLargeUsd(bar.getVolume()), 100, false);
            volLbl.setAlignment(Pos.CENTER_RIGHT);
            row.getChildren().addAll(timeLbl, openLbl, highLbl, lowLbl, closeLbl, volLbl);
            ohlcvContainer.getChildren().add(row);
        }
    }

    private static JsonArray extractDataArray(JsonObject root) {
        if (root == null) return null;
        if (root.has("data") && root.get("data").isJsonArray()) {
            return root.getAsJsonArray("data");
        }
        return null;
    }

    private static String extractAddressFromId(JsonObject pool) {
        if (!pool.has("id")) return null;
        String id = pool.get("id").getAsString();
        int idx = id.indexOf('_');
        return idx >= 0 && idx < id.length() - 1 ? id.substring(idx + 1) : id;
    }

    private static Label cellLabel(String text, double prefWidth, boolean hgrow) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:11px; -fx-text-fill:#c9d1d9;");
        if (prefWidth > 0) l.setPrefWidth(prefWidth);
        if (hgrow) HBox.setHgrow(l, Priority.ALWAYS);
        return l;
    }

    private static String formatNumber(String raw) {
        try {
            return new BigDecimal(raw).setScale(4, RoundingMode.HALF_UP).toPlainString();
        } catch (Exception e) {
            return raw;
        }
    }

    private static String formatPrice(BigDecimal v) {
        if (v == null) return "—";
        return v.setScale(v.compareTo(BigDecimal.TEN) >= 0 ? 2 : 4, RoundingMode.HALF_UP).toPlainString();
    }

    private static String formatLargeUsd(BigDecimal n) {
        if (n == null) return "—";
        double d = n.doubleValue();
        if (d >= 1_000_000_000d) return String.format("$%.2fB", d / 1_000_000_000d);
        if (d >= 1_000_000d)     return String.format("$%.2fM", d / 1_000_000d);
        if (d >= 1_000d)         return String.format("$%.0f", d);
        return "$" + n.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private void styleTimeframeButtons(ToggleButton selected) {
        for (ToggleButton tb : List.of(tfMinute, tfHour, tf4Hour, tfDay)) {
            boolean on = tb == selected;
            tb.setStyle(on
                    ? "-fx-background-color:#1f6feb; -fx-text-fill:white; -fx-background-radius:4; -fx-padding:3 8; -fx-font-size:11px;"
                    : "-fx-background-color:#21262d; -fx-text-fill:#8b949e; -fx-background-radius:4; -fx-padding:3 8; -fx-font-size:11px; -fx-border-color:#30363d; -fx-border-radius:4; -fx-border-width:1;");
        }
    }

    private void setLoading(boolean loading) {
        if (loadingSpinner != null) {
            loadingSpinner.setVisible(loading);
            loadingSpinner.setManaged(loading);
        }
        if (refreshBtn != null) refreshBtn.setDisable(loading);
    }

    private void setStatus(String msg) {
        if (statusLabel != null) statusLabel.setText(msg);
    }
}
