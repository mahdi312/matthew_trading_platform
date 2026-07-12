package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.service.price.api.coingecko.*;
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
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;

/**
 * Controller for the DeFi Dashboard tab.
 *
 * Displays global market statistics and DeFi data sourced from CoinGecko:
 * <ul>
 *   <li>Global market cap, BTC dominance, active cryptocurrencies</li>
 *   <li>DeFi market cap, trading volume, DeFi dominance</li>
 *   <li>Exchange rates (fiat/crypto relative to BTC)</li>
 * </ul>
 */
@Component
@FxmlView("/fxml/DeFiDashboardView.fxml")
public class DeFiDashboardController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(DeFiDashboardController.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final NumberFormat      NUM_FMT = NumberFormat.getNumberInstance(Locale.US);

    // ── Global stats ──────────────────────────────────────────────────────────
    @FXML private Label totalMarketCapLabel;
    @FXML private Label marketCapChangeLabel;
    @FXML private Label btcDominanceLabel;
    @FXML private Label activeCryptosLabel;
    @FXML private Label totalVolumeLabel;

    // ── DeFi stats ────────────────────────────────────────────────────────────
    @FXML private Label defiMarketCapLabel;
    @FXML private Label defiVolumeLabel;
    @FXML private Label defiDominanceLabel;
    @FXML private Label topDefiCoinLabel;
    @FXML private Label topDefiCoinDominanceLabel;

    // ── Exchange rates ────────────────────────────────────────────────────────
    @FXML private VBox  exchangeRatesContainer;

    // ── Misc ──────────────────────────────────────────────────────────────────
    @FXML private javafx.scene.control.Button refreshBtn;
    @FXML private ProgressIndicator loadingSpinner;
    @FXML private Label             lastUpdateLabel;
    @FXML private Label             statusLabel;

    @Autowired private CoinGeckoDefiService   defiService;
    @Autowired private CoinGeckoMarketService marketService;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        loadData();
    }

    // ── FXML actions ──────────────────────────────────────────────────────────

    @FXML
    private void onRefresh() {
        loadData();
    }

    // ── Data loading ──────────────────────────────────────────────────────────

    private void loadData() {
        setLoading(true);
        Thread.ofVirtual().name("defi-dashboard-load").start(() -> {
            try {
                // Load global stats
                Optional<CoinGeckoGlobalData> global = defiService.getGlobalData();
                Optional<CoinGeckoDefiData>   defi   = defiService.getDefiData();
                Optional<CoinGeckoExchangeRates> rates = defiService.getExchangeRates();

                Platform.runLater(() -> {
                    global.ifPresent(this::updateGlobalStats);
                    defi.ifPresent(this::updateDefiStats);
                    rates.ifPresent(this::updateExchangeRates);
                    if (global.isEmpty() && defi.isEmpty()) {
                        setStatus("Failed to load data. Check API key or network connection.");
                    } else {
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

    // ── UI update helpers ─────────────────────────────────────────────────────

    private void updateGlobalStats(CoinGeckoGlobalData data) {
        if (data.getTotalMarketCap() != null && data.getTotalMarketCap().containsKey("usd")) {
            totalMarketCapLabel.setText(formatLargeNumber(data.getTotalMarketCap().get("usd")));
        }
        if (data.getTotalVolume() != null && data.getTotalVolume().containsKey("usd")) {
            totalVolumeLabel.setText(formatLargeNumber(data.getTotalVolume().get("usd")));
        }
        if (data.getMarketCapPercentage() != null) {
            BigDecimal btcDom = data.getMarketCapPercentage().getOrDefault("btc", BigDecimal.ZERO);
            btcDominanceLabel.setText(btcDom.setScale(1, RoundingMode.HALF_UP) + "%");
        }
        if (data.getActiveCryptocurrencies() != null) {
            activeCryptosLabel.setText(NUM_FMT.format(data.getActiveCryptocurrencies()));
        }
    }

    private void updateDefiStats(CoinGeckoDefiData data) {
        if (data.getDefiMarketCap() != null)
            defiMarketCapLabel.setText(formatLargeNumber(data.getDefiMarketCap()));
        if (data.getTradingVolume24h() != null)
            defiVolumeLabel.setText(formatLargeNumber(data.getTradingVolume24h()));
        if (data.getDefiDominance() != null)
            defiDominanceLabel.setText(data.getDefiDominance().setScale(2, RoundingMode.HALF_UP) + "%");
        if (data.getTopCoinName() != null)
            topDefiCoinLabel.setText(data.getTopCoinName());
        if (data.getTopCoinDominance() != null)
            topDefiCoinDominanceLabel.setText(
                    data.getTopCoinDominance().setScale(2, RoundingMode.HALF_UP) + "% dominance");
    }

    private void updateExchangeRates(CoinGeckoExchangeRates rates) {
        if (rates == null || rates.getRates() == null) return;
        exchangeRatesContainer.getChildren().clear();

        // Show the 20 most common fiat + top crypto rates
        rates.getRates().entrySet().stream()
                .filter(e -> isCommonRate(e.getKey()))
                .limit(30)
                .forEach(e -> {
                    CoinGeckoRate rate = e.getValue();
                    if (rate == null) return;
                    HBox row = buildRateRow(e.getKey(), rate);
                    exchangeRatesContainer.getChildren().add(row);
                });
    }

    private boolean isCommonRate(String key) {
        // Show major fiat + top crypto
        return Set.of("usd","eur","gbp","jpy","aud","cad","chf","cny","hkd","sgd",
                "btc","eth","bnb","sol","xrp","usdt","usdc","ada","doge","dot",
                "nok","sek","dkk","inr","brl","mxn","krw","zar","rub","try")
                .contains(key.toLowerCase());
    }

    private HBox buildRateRow(String key, CoinGeckoRate rate) {
        HBox row = new HBox();
        row.setStyle("-fx-padding:7 16; -fx-border-color:#30363d; -fx-border-width:0 0 1 0;");

        Label keyLbl  = styledLabel(key.toUpperCase(), true,  "ALWAYS", null);
        Label nameLbl = styledLabel(rate.getName() != null ? rate.getName() : "—", false, "180", null);
        Label valLbl  = styledLabel(
                rate.getValue() != null ? rate.getValue().setScale(4, RoundingMode.HALF_UP).toPlainString() : "—",
                false, "120", "CENTER_RIGHT");
        Label typeLbl = styledLabel(rate.getType() != null ? rate.getType() : "—",
                false, "80", "CENTER_RIGHT");
        typeLbl.setStyle(typeLbl.getStyle() + "; -fx-text-fill:" +
                ("crypto".equalsIgnoreCase(rate.getType()) ? "#f7931a" : "#8b949e") + ";");

        row.getChildren().addAll(keyLbl, nameLbl, valLbl, typeLbl);
        return row;
    }

    private Label styledLabel(String text, boolean hgrow, String prefW, String align) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:12px; -fx-text-fill:#c9d1d9;");
        if (hgrow) javafx.scene.layout.HBox.setHgrow(l, javafx.scene.layout.Priority.ALWAYS);
        if (prefW != null && !"ALWAYS".equals(prefW)) l.setPrefWidth(Double.parseDouble(prefW));
        if ("CENTER_RIGHT".equals(align)) l.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        return l;
    }

    // ── Formatting helpers ────────────────────────────────────────────────────

    private String formatLargeNumber(BigDecimal n) {
        if (n == null) return "—";
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
    }

    private void setStatus(String msg) {
        if (statusLabel != null) statusLabel.setText(msg);
    }

}

