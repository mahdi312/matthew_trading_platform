package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.client.ReferenceDataApiClient;
import com.mst.matt.tradingplatformapp.client.ReferenceDataApiClient.NftCollection;
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
import java.util.ResourceBundle;

/**
 * Controller for the NFT Explorer tab — top collections, search, and detail panel.
 *
 * <p>Phase 2, Step 12 — data fetched via {@link ReferenceDataApiClient}
 * ({@code /api/reference/nft/collections} on the Gateway).</p>
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

    @Autowired private ReferenceDataApiClient referenceDataApiClient;

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
                List<NftCollection> results =
                        referenceDataApiClient.getNftCollections(PAGE_SIZE, 1, false, query);
                Platform.runLater(() -> {
                    if (!results.isEmpty()) {
                        renderCollectionRows(results, 1);
                        showDetail(results.get(0));
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
                List<NftCollection> collections =
                        referenceDataApiClient.getNftCollections(PAGE_SIZE, page, false, null);
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

    private void renderCollectionRows(List<NftCollection> collections, int page) {
        collectionsContainer.getChildren().clear();
        int rank = (page - 1) * PAGE_SIZE + 1;
        for (NftCollection c : collections) {
            final int rowRank = rank++;
            HBox row = buildCollectionRow(c, rowRank);
            collectionsContainer.getChildren().add(row);
        }
    }

    private HBox buildCollectionRow(NftCollection c, int rank) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-padding:8 16; -fx-border-color:#30363d; -fx-border-width:0 0 1 0; -fx-cursor:hand;");
        row.setOnMouseClicked(e -> showDetail(c));

        String symbol = c.getCollectionSlug() != null ? c.getCollectionSlug()
                : (c.getContractAddress() != null ? abbreviate(c.getContractAddress()) : "—");

        Label rankLbl = cellLabel(String.valueOf(rank), 36, false);
        Label nameLbl = cellLabel(c.getName() != null ? c.getName() : "—", 0, true);
        Label symLbl  = cellLabel(symbol, 80, false);
        Label floorLbl = cellLabel(formatUsd(c.getFloorPriceUsd()), 140, false);
        floorLbl.setAlignment(Pos.CENTER_RIGHT);
        Label capLbl = cellLabel(formatUsd(c.getVolume7d()), 130, false);
        capLbl.setAlignment(Pos.CENTER_RIGHT);
        Label volLbl = cellLabel(formatUsd(c.getVolume24hUsd()), 120, false);
        volLbl.setAlignment(Pos.CENTER_RIGHT);

        row.getChildren().addAll(rankLbl, nameLbl, symLbl, floorLbl, capLbl, volLbl);
        return row;
    }

    private void showDetail(NftCollection c) {
        if (c == null) return;
        detailPanel.setVisible(true);
        detailPanel.setManaged(true);
        detailNameLabel.setText(c.getName() != null ? c.getName() : "—");
        detailIdLabel.setText(c.getCollectionSlug() != null ? c.getCollectionSlug()
                : (c.getContractAddress() != null ? c.getContractAddress() : "—"));
        detailFloorLabel.setText(formatUsd(c.getFloorPriceUsd()));
        detailMarketCapLabel.setText(formatUsd(c.getVolume7d()));
        detailVolumeLabel.setText(formatUsd(c.getVolume24hUsd()));
        detailPlatformLabel.setText(c.getBlockchain() != null ? c.getBlockchain() : "—");
    }

    private static String abbreviate(String address) {
        if (address.length() <= 10) return address;
        return address.substring(0, 6) + "…" + address.substring(address.length() - 4);
    }

    private static Label cellLabel(String text, double prefWidth, boolean hgrow) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size:12px; -fx-text-fill:#c9d1d9;");
        if (prefWidth > 0) l.setPrefWidth(prefWidth);
        if (hgrow) HBox.setHgrow(l, Priority.ALWAYS);
        return l;
    }

    private static String formatUsd(BigDecimal value) {
        if (value == null) return "—";
        double d = value.doubleValue();
        if (d >= 1_000_000_000d) return String.format("$%.2fB", d / 1_000_000_000d);
        if (d >= 1_000_000d)     return String.format("$%.2fM", d / 1_000_000d);
        if (d >= 1_000d)         return String.format("$%.0f", d);
        return "$" + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
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
