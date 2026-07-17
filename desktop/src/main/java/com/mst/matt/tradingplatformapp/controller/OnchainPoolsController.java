package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.client.ReferenceDataApiClient;
import com.mst.matt.tradingplatformapp.client.ReferenceDataApiClient.DeFiPool;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Controller for the Onchain Pools tab — DEX pool discovery.
 *
 * <p>Phase 2, Step 12 — pool data fetched via {@link ReferenceDataApiClient}
 * ({@code /api/reference/defi/pools} on the Gateway). Pool OHLCV preview is not
 * yet exposed through the Gateway and remains disabled.</p>
 */
@Component
@FxmlView("/fxml/OnchainPoolsView.fxml")
public class OnchainPoolsController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(OnchainPoolsController.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final List<String> DEFAULT_NETWORKS =
            List.of("eth", "bsc", "polygon_pos", "arbitrum", "base", "solana");

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

    @Autowired private ReferenceDataApiClient referenceDataApiClient;

    private int currentPage = 1;
    private boolean trendingMode = false;
    private String selectedNetwork = "eth";
    private DeFiPool selectedPool;
    private List<DeFiPool> lastLoadedPools = List.of();

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
                List<DeFiPool> pools = referenceDataApiClient.getDefiPools(
                        selectedNetwork, query, false);
                Platform.runLater(() -> {
                    lastLoadedPools = pools;
                    renderPools(pools, false);
                    if (pools.isEmpty()) {
                        setStatus("No pools found for: " + query);
                    } else {
                        setStatus("");
                    }
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
        renderPools(pageSlice(lastLoadedPools), !trendingMode);
        pageLabel.setText("Page " + currentPage);
        prevPageBtn.setDisable(currentPage <= 1);
        nextPageBtn.setDisable(pageSlice(lastLoadedPools).size() < 20);
    }

    @FXML private void onNextPage() {
        int maxPage = Math.max(1, (lastLoadedPools.size() + 19) / 20);
        if (currentPage >= maxPage) return;
        currentPage++;
        renderPools(pageSlice(lastLoadedPools), !trendingMode);
        pageLabel.setText("Page " + currentPage);
        prevPageBtn.setDisable(currentPage <= 1);
        nextPageBtn.setDisable(pageSlice(lastLoadedPools).size() < 20);
    }

    @FXML private void onTimeframeChange() {
        ToggleButton selected = (ToggleButton) tfMinute.getToggleGroup().getSelectedToggle();
        if (selected == null) return;
        styleTimeframeButtons(selected);
    }

    @FXML private void onLoadPoolOhlcv() {
        if (selectedPool == null) {
            setStatus("Select a pool first.");
            return;
        }
        ohlcvContainer.getChildren().clear();
        setStatus("Pool OHLCV is not yet available through the Gateway API.");
    }

    private void loadNetworks() {
        networkSelector.getItems().setAll(DEFAULT_NETWORKS);
        networkSelector.setValue(networkSelector.getItems().contains("eth")
                ? "eth" : networkSelector.getItems().get(0));
        selectedNetwork = networkSelector.getValue();

        networkSelector.valueProperty().addListener((o, a, n) -> {
            if (n != null) {
                selectedNetwork = n;
                currentPage = 1;
                loadPools();
            }
        });

        Thread.ofVirtual().name("onchain-networks").start(() -> {
            try {
                List<DeFiPool> trending = referenceDataApiClient.getDefiPools(null, null, true);
                Set<String> networks = new LinkedHashSet<>(DEFAULT_NETWORKS);
                trending.stream()
                        .map(DeFiPool::getNetworkId)
                        .filter(id -> id != null && !id.isBlank())
                        .forEach(networks::add);
                Platform.runLater(() -> {
                    String previous = networkSelector.getValue();
                    networkSelector.getItems().setAll(networks);
                    if (previous != null && networkSelector.getItems().contains(previous)) {
                        networkSelector.setValue(previous);
                    } else if (!networkSelector.getItems().isEmpty()) {
                        networkSelector.setValue(networkSelector.getItems().get(0));
                    }
                    selectedNetwork = networkSelector.getValue();
                });
            } catch (Exception e) {
                log.warn("Failed to enrich network list: {}", e.getMessage());
            }
        });

        loadPools();
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
                List<DeFiPool> pools = trendingMode
                        ? referenceDataApiClient.getDefiPools(null, null, true)
                        : referenceDataApiClient.getDefiPools(network, null, false);
                Platform.runLater(() -> {
                    lastLoadedPools = pools;
                    currentPage = page;
                    renderPools(pageSlice(pools), !trendingMode);
                    pageLabel.setText("Page " + page);
                    prevPageBtn.setDisable(page <= 1);
                    nextPageBtn.setDisable(pageSlice(pools).size() < 20);
                    if (pools.isEmpty()) {
                        setStatus("Failed to load pools. Check API key or network.");
                    } else {
                        setStatus("");
                    }
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

    private List<DeFiPool> pageSlice(List<DeFiPool> pools) {
        int pageSize = 20;
        int from = (currentPage - 1) * pageSize;
        if (from >= pools.size()) return List.of();
        int to = Math.min(from + pageSize, pools.size());
        return new ArrayList<>(pools.subList(from, to));
    }

    private void renderPools(List<DeFiPool> pools, boolean enablePagination) {
        poolsContainer.getChildren().clear();
        if (pools.isEmpty()) {
            setStatus("No pools in response.");
            nextPageBtn.setDisable(true);
            return;
        }
        pools.stream()
                .sorted(Comparator.comparing(
                        DeFiPool::getLiquidityUsd,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .forEach(pool -> poolsContainer.getChildren().add(buildPoolRow(pool)));
        nextPageBtn.setDisable(!enablePagination || pools.size() < 20);
        if (statusLabel != null && statusLabel.getText().isBlank()) {
            setStatus("");
        }
    }

    private HBox buildPoolRow(DeFiPool pool) {
        String name = pool.getPoolName() != null ? pool.getPoolName() : poolPairLabel(pool);
        String price = pool.getPriceUsd() != null
                ? formatNumber(pool.getPriceUsd()) : "—";
        String liquidity = formatLargeUsd(pool.getLiquidityUsd());

        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding:7 12; -fx-border-color:#30363d; -fx-border-width:0 0 1 0; -fx-cursor:hand;");

        Label nameLbl = cellLabel(name, 0, true);
        Label priceLbl = cellLabel(price, 100, false);
        priceLbl.setAlignment(Pos.CENTER_RIGHT);
        Label liqLbl = cellLabel(liquidity, 100, false);
        liqLbl.setAlignment(Pos.CENTER_RIGHT);
        row.getChildren().addAll(nameLbl, priceLbl, liqLbl);

        row.setOnMouseClicked(e -> selectPool(pool, name));
        return row;
    }

    private void selectPool(DeFiPool pool, String name) {
        selectedPool = pool;
        if (pool.getNetworkId() != null) {
            selectedNetwork = pool.getNetworkId();
            if (networkSelector.getItems().contains(pool.getNetworkId())) {
                networkSelector.setValue(pool.getNetworkId());
            }
        }
        poolNameLabel.setText(name);
        poolAddressLabel.setText(pool.getPoolAddress() != null ? pool.getPoolAddress() : "—");
        poolNetworkLabel.setText(pool.getNetworkName() != null ? pool.getNetworkName()
                : (pool.getNetworkId() != null ? pool.getNetworkId() : "—"));
        poolPriceLabel.setText(pool.getPriceUsd() != null
                ? "$" + formatNumber(pool.getPriceUsd()) : "—");
        poolLiquidityLabel.setText(formatLargeUsd(pool.getLiquidityUsd()));
        poolVolumeLabel.setText(formatLargeUsd(pool.getVolume24hUsd()));
        ohlcvContainer.getChildren().clear();
        setStatus("Pool selected — OHLCV preview requires a future Gateway endpoint.");
    }

    private static String poolPairLabel(DeFiPool pool) {
        if (pool.getBaseTokenSymbol() != null && pool.getQuoteTokenSymbol() != null) {
            return pool.getBaseTokenSymbol() + "/" + pool.getQuoteTokenSymbol();
        }
        return "—";
    }

    private static Label cellLabel(String text, double prefWidth, boolean hgrow) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:11px; -fx-text-fill:#c9d1d9;");
        if (prefWidth > 0) l.setPrefWidth(prefWidth);
        if (hgrow) HBox.setHgrow(l, Priority.ALWAYS);
        return l;
    }

    private static String formatNumber(BigDecimal value) {
        if (value == null) return "—";
        return value.setScale(value.compareTo(BigDecimal.TEN) >= 0 ? 2 : 4, RoundingMode.HALF_UP)
                .toPlainString();
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
