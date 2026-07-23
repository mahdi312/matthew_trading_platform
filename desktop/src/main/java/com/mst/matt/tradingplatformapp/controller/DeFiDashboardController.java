package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.client.ReferenceDataApiClient;
import com.mst.matt.tradingplatformapp.client.ReferenceDataApiClient.DeFiPool;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import net.rgielen.fxweaver.core.FxmlView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Controller for the DeFi Dashboard tab.
 *
 * <p>Phase 2, Step 12 — DeFi pool statistics fetched via {@link ReferenceDataApiClient}
 * ({@code /api/reference/defi/pools} on the Gateway). KPI cards are aggregated from
 * trending pool data; the exchange-rates table lists top pools by liquidity.</p>
 */
@Component
@FxmlView("/fxml/DeFiDashboardView.fxml")
public class DeFiDashboardController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(DeFiDashboardController.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final NumberFormat      NUM_FMT = NumberFormat.getNumberInstance(Locale.US);

    @FXML private Label totalMarketCapLabel;
    @FXML private Label marketCapChangeLabel;
    @FXML private Label btcDominanceLabel;
    @FXML private Label activeCryptosLabel;
    @FXML private Label totalVolumeLabel;

    @FXML private Label defiMarketCapLabel;
    @FXML private Label defiVolumeLabel;
    @FXML private Label defiDominanceLabel;
    @FXML private Label topDefiCoinLabel;
    @FXML private Label topDefiCoinDominanceLabel;

    @FXML private VBox  exchangeRatesContainer;

    @FXML private javafx.scene.control.Button refreshBtn;
    @FXML private ProgressIndicator loadingSpinner;
    @FXML private Label             lastUpdateLabel;
    @FXML private Label             statusLabel;

    @Autowired private ReferenceDataApiClient referenceDataApiClient;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        loadData();
    }

    @FXML
    private void onRefresh() {
        loadData();
    }

    private void loadData() {
        setLoading(true);
        Thread.ofVirtual().name("defi-dashboard-load").start(() -> {
            try {
                List<DeFiPool> pools = referenceDataApiClient.getDefiPools(null, null, true);

                Platform.runLater(() -> {
                    if (pools.isEmpty()) {
                        setStatus("Failed to load data. Check API key or network connection.");
                    } else {
                        updateStatsFromPools(pools);
                        updatePoolTable(pools);
                        setStatus("");
                    }
                    lastUpdateLabel.setText("Last updated: " + LocalDateTime.now().format(DT_FMT));
                    setLoading(false);
                });
            } catch (Exception e) {
                log.error("DeFi dashboard load error: {}", e.getMessage(), e);
                Platform.runLater(() -> {
                    setStatus("Error: " + e.getMessage());
                    setLoading(false);
                });
            }
        });
    }

    private void updateStatsFromPools(List<DeFiPool> pools) {
        BigDecimal totalMarketCap = sum(pools, DeFiPool::getMarketCapUsd);
        BigDecimal totalLiquidity = sum(pools, DeFiPool::getLiquidityUsd);
        BigDecimal totalVolume    = sum(pools, DeFiPool::getVolume24hUsd);

        if (totalMarketCapLabel != null) {
            totalMarketCapLabel.setText(formatLargeNumber(totalMarketCap));
        }
        if (totalVolumeLabel != null) {
            totalVolumeLabel.setText(formatLargeNumber(totalVolume));
        }
        if (activeCryptosLabel != null) {
            activeCryptosLabel.setText(NUM_FMT.format(pools.size()));
        }
        if (defiMarketCapLabel != null) {
            defiMarketCapLabel.setText(formatLargeNumber(totalLiquidity));
        }
        if (defiVolumeLabel != null) {
            defiVolumeLabel.setText(formatLargeNumber(totalVolume));
        }

        long networkCount = pools.stream()
                .map(DeFiPool::getNetworkId)
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .count();
        if (btcDominanceLabel != null) {
            btcDominanceLabel.setText(networkCount > 0 ? networkCount + " nets" : "—");
        }

        if (defiDominanceLabel != null && totalMarketCap.compareTo(BigDecimal.ZERO) > 0
                && totalLiquidity.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal dominance = totalLiquidity
                    .multiply(BigDecimal.valueOf(100))
                    .divide(totalMarketCap, 2, RoundingMode.HALF_UP);
            defiDominanceLabel.setText(dominance.toPlainString() + "%");
        }

        pools.stream()
                .filter(p -> p.getLiquidityUsd() != null)
                .max(Comparator.comparing(DeFiPool::getLiquidityUsd))
                .ifPresent(top -> {
                    if (topDefiCoinLabel != null) {
                        String pair = poolPairLabel(top);
                        topDefiCoinLabel.setText(pair);
                    }
                    if (topDefiCoinDominanceLabel != null && totalLiquidity.compareTo(BigDecimal.ZERO) > 0
                            && top.getLiquidityUsd() != null) {
                        BigDecimal share = top.getLiquidityUsd()
                                .multiply(BigDecimal.valueOf(100))
                                .divide(totalLiquidity, 2, RoundingMode.HALF_UP);
                        topDefiCoinDominanceLabel.setText(share.toPlainString() + "% of pool liquidity");
                    }
                });

        if (marketCapChangeLabel != null) {
            pools.stream()
                    .filter(p -> p.getPriceChangePercent24h() != null)
                    .mapToDouble(p -> p.getPriceChangePercent24h().doubleValue())
                    .average()
                    .ifPresentOrElse(
                            avg -> marketCapChangeLabel.setText(String.format("Avg 24h change: %+.2f%%", avg)),
                            () -> marketCapChangeLabel.setText("24h change: —"));
        }
    }

    private void updatePoolTable(List<DeFiPool> pools) {
        if (exchangeRatesContainer == null) return;
        exchangeRatesContainer.getChildren().clear();

        pools.stream()
                .sorted(Comparator.comparing(
                        DeFiPool::getLiquidityUsd,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(30)
                .forEach(pool -> exchangeRatesContainer.getChildren().add(buildPoolRow(pool)));
    }

    private HBox buildPoolRow(DeFiPool pool) {
        HBox row = new HBox();
        row.setStyle("-fx-padding:7 16; -fx-border-color:#30363d; -fx-border-width:0 0 1 0;");

        String symbol = poolPairLabel(pool);
        Label keyLbl  = styledLabel(symbol, true,  "ALWAYS", null);
        Label nameLbl = styledLabel(pool.getDexName() != null ? pool.getDexName()
                : (pool.getNetworkName() != null ? pool.getNetworkName() : "—"), false, "180", null);
        Label valLbl  = styledLabel(formatLargeNumber(pool.getLiquidityUsd()), false, "120", "CENTER_RIGHT");
        Label typeLbl = styledLabel(pool.getNetworkId() != null ? pool.getNetworkId() : "pool",
                false, "80", "CENTER_RIGHT");
        typeLbl.setStyle(typeLbl.getStyle() + "; -fx-text-fill:#58a6ff;");

        row.getChildren().addAll(keyLbl, nameLbl, valLbl, typeLbl);
        return row;
    }

    private static String poolPairLabel(DeFiPool pool) {
        if (pool.getBaseTokenSymbol() != null && pool.getQuoteTokenSymbol() != null) {
            return pool.getBaseTokenSymbol() + "/" + pool.getQuoteTokenSymbol();
        }
        if (pool.getPoolName() != null) return pool.getPoolName();
        return "—";
    }

    private static BigDecimal sum(List<DeFiPool> pools, java.util.function.Function<DeFiPool, BigDecimal> getter) {
        return pools.stream()
                .map(getter)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Label styledLabel(String text, boolean hgrow, String prefW, String align) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:12px; -fx-text-fill:#c9d1d9;");
        if (hgrow) javafx.scene.layout.HBox.setHgrow(l, javafx.scene.layout.Priority.ALWAYS);
        if (prefW != null && !"ALWAYS".equals(prefW)) l.setPrefWidth(Double.parseDouble(prefW));
        if ("CENTER_RIGHT".equals(align)) l.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        return l;
    }

    private String formatLargeNumber(BigDecimal n) {
        if (n == null || n.compareTo(BigDecimal.ZERO) == 0) return "—";
        double d = n.doubleValue();
        if (d >= 1_000_000_000_000d) return String.format("$%.2fT", d / 1_000_000_000_000d);
        if (d >= 1_000_000_000d)     return String.format("$%.2fB", d / 1_000_000_000d);
        if (d >= 1_000_000d)         return String.format("$%.2fM", d / 1_000_000d);
        return "$" + NUM_FMT.format(d);
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
