package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.service.price.CoinGeckoNftService;
import com.mst.matt.tradingplatformapp.service.price.api.coingecko.CoinGeckoNftCollection;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
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
 * Controller for the NFT Explorer tab — top collections, search, and detail panel.
 */
@Component
@FxmlView("/fxml/NftExplorerView.fxml")
public class NftExplorerController implements Initializable {

    private static final Logger log = LoggerFactory.getLogger(NftExplorerController.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int PAGE_SIZE = 25;

    @FXML private TextField searchField;
    @FXML private Button refreshBtn;
    @FXML private ProgressIndicator loadingSpinner;
    @FXML private Label lastUpdateLabel;
    @FXML private Label statusLabel;
    @FXML private VBox collectionsContainer;
    @FXML private VBox detailPanel;
    @FXML private Label detailNameLabel;
    @FXML private Label detailIdLabel;
    @FXML private Label detailFloorLabel;
    @FXML private Label detailMarketCapLabel;
    @FXML private Label detailVolumeLabel;
    @FXML private Label detailPlatformLabel;
    @FXML private Label pageLabel;
    @FXML private Button prevPageBtn;
    @FXML private Button nextPageBtn;

    @Autowired private CoinGeckoNftService nftService;

    private int currentPage = 1;
    private boolean searchMode = false;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        loadCollections();
    }

    @FXML private void onRefresh() {
        searchMode = false;
        searchField.clear();
        loadCollections();
    }

    @FXML private void onSearch() {
        String query = searchField.getText() == null ? "" : searchField.getText().trim();
        if (query.isBlank()) {
            searchMode = false;
            currentPage = 1;
            loadCollections();
            return;
        }
        searchMode = true;
        setLoading(true);
        Thread.ofVirtual().name("nft-search").start(() -> {
            try {
                Optional<CoinGeckoNftCollection> detail = nftService.getCollectionDetail(query);
                Platform.runLater(() -> {
                    if (detail.isPresent()) {
                        renderCollectionRows(List.of(detail.get()), 1);
                        showDetail(detail.get());
                        setStatus("");
                    } else {
                        collectionsContainer.getChildren().clear();
                        setStatus("Collection not found: " + query);
                    }
                    lastUpdateLabel.setText("Last updated: " + LocalDateTime.now().format(DT_FMT));
                    setLoading(false);
                });
            } catch (Exception e) {
                log.error("NFT search error: {}", e.getMessage(), e);
                Platform.runLater(() -> {
                    setStatus("Search failed: " + e.getMessage());
                    setLoading(false);
                });
            }
        });
    }

    @FXML private void onPrevPage() {
        if (searchMode || currentPage <= 1) return;
        currentPage--;
        loadCollections();
    }

    @FXML private void onNextPage() {
        if (searchMode) return;
        currentPage++;
        loadCollections();
    }

    @FXML private void onCloseDetail() {
        detailPanel.setVisible(false);
        detailPanel.setManaged(false);
    }

    private void loadCollections() {
        setLoading(true);
        final int page = currentPage;
        Thread.ofVirtual().name("nft-list-" + page).start(() -> {
            try {
                List<CoinGeckoNftCollection> collections =
                        nftService.listCollections(PAGE_SIZE, page, null);
                Platform.runLater(() -> {
                    renderCollectionRows(collections, page);
                    pageLabel.setText("Page " + page);
                    prevPageBtn.setDisable(page <= 1);
                    nextPageBtn.setDisable(collections.size() < PAGE_SIZE);
                    if (collections.isEmpty()) {
                        setStatus("No collections returned. Check API key or network.");
                    } else {
                        setStatus("");
                    }
                    lastUpdateLabel.setText("Last updated: " + LocalDateTime.now().format(DT_FMT));
                    setLoading(false);
                });
            } catch (Exception e) {
                log.error("NFT list load error: {}", e.getMessage(), e);
                Platform.runLater(() -> {
                    setStatus("Load failed: " + e.getMessage());
                    setLoading(false);
                });
            }
        });
    }

    private void renderCollectionRows(List<CoinGeckoNftCollection> collections, int page) {
        collectionsContainer.getChildren().clear();
        int rank = (page - 1) * PAGE_SIZE + 1;
        for (CoinGeckoNftCollection c : collections) {
            final int rowRank = rank++;
            HBox row = buildCollectionRow(c, rowRank);
            collectionsContainer.getChildren().add(row);
        }
    }

    private HBox buildCollectionRow(CoinGeckoNftCollection c, int rank) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding:8 16; -fx-border-color:#30363d; -fx-border-width:0 0 1 0; -fx-cursor:hand;");
        row.setOnMouseClicked(e -> showDetail(c));

        Label rankLbl = cellLabel(String.valueOf(rank), 36, false);
        Label nameLbl = cellLabel(c.getName() != null ? c.getName() : "—", 0, true);
        Label symLbl  = cellLabel(c.getSymbol() != null ? c.getSymbol() : "—", 80, false);
        Label floorLbl = cellLabel(formatUsd(c.getFloorPrice()), 140, false);
        floorLbl.setAlignment(Pos.CENTER_RIGHT);
        Label capLbl = cellLabel(formatUsd(c.getMarketCap()), 130, false);
        capLbl.setAlignment(Pos.CENTER_RIGHT);
        Label volLbl = cellLabel(formatUsd(c.getVolume24h()), 120, false);
        volLbl.setAlignment(Pos.CENTER_RIGHT);

        row.getChildren().addAll(rankLbl, nameLbl, symLbl, floorLbl, capLbl, volLbl);
        return row;
    }

    private void showDetail(CoinGeckoNftCollection c) {
        if (c == null) return;
        detailPanel.setVisible(true);
        detailPanel.setManaged(true);
        detailNameLabel.setText(c.getName() != null ? c.getName() : "—");
        detailIdLabel.setText(c.getId() != null ? c.getId() : "—");
        detailFloorLabel.setText(formatUsd(c.getFloorPrice()));
        detailMarketCapLabel.setText(formatUsd(c.getMarketCap()));
        detailVolumeLabel.setText(formatUsd(c.getVolume24h()));
        detailPlatformLabel.setText(c.getAssetPlatformId() != null ? c.getAssetPlatformId() : "—");

        if (c.getId() != null) {
            Thread.ofVirtual().name("nft-detail").start(() ->
                    nftService.getCollectionDetail(c.getId()).ifPresent(full ->
                            Platform.runLater(() -> {
                                detailFloorLabel.setText(formatUsd(full.getFloorPrice()));
                                detailMarketCapLabel.setText(formatUsd(full.getMarketCap()));
                                detailVolumeLabel.setText(formatUsd(full.getVolume24h()));
                            })));
        }
    }

    private static Label cellLabel(String text, double prefWidth, boolean hgrow) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:12px; -fx-text-fill:#c9d1d9;");
        if (prefWidth > 0) l.setPrefWidth(prefWidth);
        if (hgrow) HBox.setHgrow(l, Priority.ALWAYS);
        return l;
    }

    private static String formatUsd(java.util.Map<String, BigDecimal> map) {
        if (map == null || !map.containsKey("usd")) return "—";
        BigDecimal v = map.get("usd");
        if (v == null) return "—";
        double d = v.doubleValue();
        if (d >= 1_000_000_000d) return String.format("$%.2fB", d / 1_000_000_000d);
        if (d >= 1_000_000d)     return String.format("$%.2fM", d / 1_000_000d);
        if (d >= 1_000d)         return String.format("$%.0f", d);
        return "$" + v.setScale(2, RoundingMode.HALF_UP).toPlainString();
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
