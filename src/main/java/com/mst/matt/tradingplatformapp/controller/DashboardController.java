package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.model.*;
import com.mst.matt.tradingplatformapp.service.TradeService;
import com.mst.matt.tradingplatformapp.service.TradeService.PortfolioStats;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import net.rgielen.fxweaver.core.FxmlView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dashboard controller — portfolio stats, equity curve, recent trades table.
 */
@Component
@FxmlView("/fxml/DashboardView.fxml")
public class DashboardController implements Initializable {

    public enum ViewMode { DASHBOARD, JOURNAL, PORTFOLIO }

    // ── Sections (visibility toggled by view mode) ─────────────
    @FXML private HBox statsSection;
    @FXML private HBox middleSection;
    @FXML private VBox tradesSection;
    @FXML private Label tradesSectionTitle;
    @FXML private Button viewAllBtn;

    // ── Stat cards ────────────────────────────────────────────
    @FXML private Label totalPnlLabel, totalPnlPctLabel;
    @FXML private Label winRateLabel, winLossLabel;
    @FXML private Label totalTradesLabel, openTradesLabel;
    @FXML private Label bestTradeLabel, worstTradeLabel;
    @FXML private Label profitFactorLabel, avgWinLossLabel;

    // ── Charts ────────────────────────────────────────────────
    @FXML private Canvas equityCanvas;
    @FXML private VBox   breakdownContainer;

    // ── Trades TabPane ────────────────────────────────────────
    @FXML private TabPane  tradesTabPane;
    @FXML private Tab      myTradesTab;

    // ── Recent Trades Table ───────────────────────────────────
    @FXML private TableView<Trade>       recentTradesTable;
    @FXML private TableColumn<Trade,String> colDate, colSymbol, colType,
            colDir,  colEntry,  colExit,
            colQty,  colPnl,    colPnlPct,
            colStatus;
    @FXML private TableColumn<Trade,Void> colActions;

    @Autowired private TradeService tradeService;

    private UserProfile activeProfile;
    private ViewMode viewMode = ViewMode.DASHBOARD;
    private List<BigDecimal> fullEquityCurve = new ArrayList<>();
    private List<BigDecimal> currentEquityCurve = new ArrayList<>();

    // Parent-supplied callback for editing a specific trade
    private java.util.function.Consumer<Trade> onEditTradeCallback;
    // Parent notified when user wants to open Trade Entry (new) or view all
    private Runnable onNewTradeCallback;
    private Runnable onViewAllCallback;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        setupTradeTable();
        applyViewMode();
        // Canvas auto-resizes: bind width to parent
        equityCanvas.widthProperty().addListener((o,a,b) ->
                drawEquityCurve(currentEquityCurve));
        equityCanvas.heightProperty().addListener((o,a,b) ->
                drawEquityCurve(currentEquityCurve));
    }

    public void loadProfile(UserProfile profile) {
        this.activeProfile = profile;
        refreshAll();
    }

    /** Switch between Dashboard overview, full Trade Journal, or Portfolio summary. */
    public void setViewMode(ViewMode mode) {
        this.viewMode = mode;
        applyViewMode();
        if (activeProfile != null) refreshAll();
    }

    private void applyViewMode() {
        boolean showStats   = viewMode == ViewMode.DASHBOARD || viewMode == ViewMode.PORTFOLIO;
        boolean showMiddle  = showStats;
        boolean showTrades  = viewMode == ViewMode.DASHBOARD || viewMode == ViewMode.JOURNAL;

        statsSection.setVisible(showStats);
        statsSection.setManaged(showStats);
        middleSection.setVisible(showMiddle);
        middleSection.setManaged(showMiddle);
        tradesSection.setVisible(showTrades);
        tradesSection.setManaged(showTrades);

        tradesSectionTitle.setText(
                viewMode == ViewMode.JOURNAL ? "Trade Journal" : "Recent Trades");
        viewAllBtn.setVisible(viewMode == ViewMode.DASHBOARD);
        viewAllBtn.setManaged(viewMode == ViewMode.DASHBOARD);
    }

    // ── Refresh ───────────────────────────────────────────────

    public void refreshAll() {
        if (activeProfile == null) return;

        PortfolioStats stats = tradeService.getStats(activeProfile);
        List<Trade>    recent = tradeService.getTradesForProfile(activeProfile);

        Platform.runLater(() -> {
            updateStatCards(stats);
            fullEquityCurve = new ArrayList<>(stats.getEquityCurve());
            drawEquityCurve(stats.getEquityCurve());
            drawBreakdown(recent);
            int limit = viewMode == ViewMode.JOURNAL
                    ? recent.size()
                    : Math.min(recent.size(), 20);
            populateTable(limit > 0 ? recent.subList(0, limit) : List.of());
            buildBrokerTabs(recent);
        });
    }

    // ── Stat Cards ────────────────────────────────────────────

    private void updateStatCards(PortfolioStats s) {
        boolean profit = s.getTotalPnl().compareTo(BigDecimal.ZERO) >= 0;

        totalPnlLabel.setText((profit ? "+" : "") + "$"
                + fmt(s.getTotalPnl()));
        totalPnlLabel.setStyle("-fx-font-size:28px; -fx-font-weight:bold;"
                + (profit ? "-fx-text-fill:#3fb950;" : "-fx-text-fill:#f85149;"));

        totalPnlPctLabel.setText((profit ? "▲ +" : "▼ ")
                + fmt(s.getTotalPnlPercent()) + "%");
        totalPnlPctLabel.setStyle(profit
                ? "-fx-text-fill:#3fb950;" : "-fx-text-fill:#f85149;");

        winRateLabel.setText(fmt(s.getWinRate()) + "%");
        winLossLabel.setText(s.getWins() + "W / " + s.getLosses() + "L");

        totalTradesLabel.setText(String.valueOf(s.getTotalTrades()));
        openTradesLabel.setText(s.getOpenTrades() + " Open");

        bestTradeLabel.setText("$" + fmt(s.getBestTrade()));
        worstTradeLabel.setText("$" + fmt(s.getWorstTrade()));

        profitFactorLabel.setText(fmt(s.getProfitFactor()));
        avgWinLossLabel.setText("Avg W: $" + fmt(s.getAvgWin())
                + " / L: $" + fmt(s.getAvgLoss()));
    }

    // ── Equity Curve Canvas ───────────────────────────────────

    private void drawEquityCurve(List<BigDecimal> curve) {
        currentEquityCurve = curve == null ? new ArrayList<>() : new ArrayList<>(curve);
        GraphicsContext gc = equityCanvas.getGraphicsContext2D();
        double w = equityCanvas.getWidth();
        double h = equityCanvas.getHeight();
        if (w <= 0 || h <= 0) return;

        gc.clearRect(0, 0, w, h);

        // Background
        gc.setFill(Color.web("#161b22"));
        gc.fillRect(0, 0, w, h);

        if (currentEquityCurve.isEmpty()) return;

        // Grid lines
        gc.setStroke(Color.web("#30363d"));
        gc.setLineWidth(0.5);
        for (int i = 1; i <= 4; i++) {
            double y = h * i / 4.0;
            gc.strokeLine(0, y, w, y);
        }

        double maxVal = currentEquityCurve.stream()
                .mapToDouble(BigDecimal::doubleValue).max().orElse(1);
        double minVal = currentEquityCurve.stream()
                .mapToDouble(BigDecimal::doubleValue).min().orElse(0);
        double range  = Math.max(Math.abs(maxVal - minVal), 1);

        // Zero line
        double zeroY = h - ((0 - minVal) / range) * (h * 0.85) - h * 0.075;
        gc.setStroke(Color.web("#30363d"));
        gc.setLineWidth(1.0);
        gc.strokeLine(0, zeroY, w, zeroY);

        // Build path
        int n = currentEquityCurve.size();
        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            double val = currentEquityCurve.get(i).doubleValue();
            xs[i] = n == 1 ? w / 2.0 : (double) i / (n - 1) * w;
            ys[i] = h - ((val - minVal) / range) * (h * 0.85) - h * 0.075;
        }

        // Fill gradient
        boolean netPositive = currentEquityCurve.get(n - 1)
                .compareTo(BigDecimal.ZERO) >= 0;
        gc.setFill(netPositive
                ? Color.web("#3fb95020") : Color.web("#f8514920"));
        gc.beginPath();
        gc.moveTo(xs[0], h);
        for (int i = 0; i < n; i++) gc.lineTo(xs[i], ys[i]);
        gc.lineTo(xs[n-1], h);
        gc.closePath();
        gc.fill();

        // Equity line
        gc.setStroke(netPositive
                ? Color.web("#3fb950") : Color.web("#f85149"));
        gc.setLineWidth(2.0);
        gc.beginPath();
        gc.moveTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) gc.lineTo(xs[i], ys[i]);
        gc.stroke();

        // Last point dot
        if (n > 0) {
            gc.setFill(netPositive
                    ? Color.web("#3fb950") : Color.web("#f85149"));
            gc.fillOval(xs[n-1]-4, ys[n-1]-4, 8, 8);
        }
    }

    // ── Asset Breakdown ───────────────────────────────────────

    private void drawBreakdown(List<Trade> trades) {
        breakdownContainer.getChildren().clear();
        Map<String, BigDecimal> pnlByType = new LinkedHashMap<>();
        pnlByType.put("CRYPTO", BigDecimal.ZERO);
        pnlByType.put("STOCK",  BigDecimal.ZERO);
        pnlByType.put("FOREX",  BigDecimal.ZERO);

        for (Trade t : trades) {
            if (t.getPnlAmount() == null || t.getStatus() != Trade.TradeStatus.CLOSED)
                continue;
            String key = t.getAssetType().name();
            pnlByType.merge(key, t.getPnlAmount(), BigDecimal::add);
        }

        BigDecimal total = pnlByType.values().stream()
                .map(BigDecimal::abs).reduce(BigDecimal.ZERO, BigDecimal::add);

        String[] colors = {"#388bfd", "#3fb950", "#bc8cff"};
        int idx = 0;
        for (Map.Entry<String, BigDecimal> entry : pnlByType.entrySet()) {
            String type  = entry.getKey();
            BigDecimal pnl = entry.getValue();
            double pct   = total.compareTo(BigDecimal.ZERO) == 0 ? 0
                    : pnl.abs().divide(total, 4, RoundingMode.HALF_UP)
                    .doubleValue() * 100;
            boolean pos  = pnl.compareTo(BigDecimal.ZERO) >= 0;

            VBox row = new VBox(4);
            HBox header = new HBox(8);

            Label nameLbl = new Label(type);
            nameLbl.setStyle("-fx-text-fill:#e6edf3; -fx-font-size:12px;");
            Label pnlLbl  = new Label((pos ? "+" : "") + "$" + fmt(pnl));
            pnlLbl.setStyle("-fx-font-size:12px; -fx-font-weight:bold;"
                    + (pos ? "-fx-text-fill:#3fb950;" : "-fx-text-fill:#f85149;"));
            header.getChildren().addAll(nameLbl,
                    new Pane() {{ HBox.setHgrow(this, Priority.ALWAYS); }},
                    pnlLbl);

            // Progress bar
            ProgressBar bar = new ProgressBar(pct / 100.0);
            bar.setMaxWidth(Double.MAX_VALUE);
            bar.setStyle("-fx-accent: " + colors[idx % 3] + ";");

            Label pctLbl = new Label(String.format("%.1f%%", pct));
            pctLbl.setStyle("-fx-text-fill:#8b949e; -fx-font-size:11px;");

            row.getChildren().addAll(header, bar, pctLbl);
            breakdownContainer.getChildren().add(row);
            idx++;
        }
    }

    // ── Broker Import Tabs ────────────────────────────────────

    /**
     * Builds / refreshes per-broker tabs in the TabPane.
     * For each unique exchange that has at least one trade with "Imported from" in notes,
     * create (or re-populate) a tab named "<BrokerName> Imports" with a read-only TableView.
     */
    private void buildBrokerTabs(List<Trade> allTrades) {
        if (tradesTabPane == null) return;

        // Group imported trades by exchange (non-blank exchange field + notes contain "Imported from")
        Map<String, List<Trade>> byBroker = allTrades.stream()
                .filter(t -> t.getExchange() != null && !t.getExchange().isBlank()
                        && t.getNotes() != null && t.getNotes().contains("Imported from"))
                .collect(Collectors.groupingBy(Trade::getExchange, LinkedHashMap::new, Collectors.toList()));

        // Remove old broker tabs (keep only "My Trades" tab at index 0)
        tradesTabPane.getTabs().removeIf(tab -> tab != myTradesTab);

        // Create a tab per broker
        for (Map.Entry<String, List<Trade>> entry : byBroker.entrySet()) {
            String broker = entry.getKey();
            List<Trade> brokerTrades = entry.getValue();

            Tab tab = new Tab(brokerIcon(broker) + " " + broker + " Imports");
            tab.setClosable(false);
            tab.setStyle("-fx-padding:4 10;");

            VBox content = new VBox(8);
            content.setStyle("-fx-background-color:#161b22; -fx-padding:8;");

            // Summary bar
            long closedCount = brokerTrades.stream()
                    .filter(t -> t.getStatus() == Trade.TradeStatus.CLOSED).count();
            BigDecimal totalPnl = brokerTrades.stream()
                    .filter(t -> t.getPnlAmount() != null)
                    .map(Trade::getPnlAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean net = totalPnl.compareTo(BigDecimal.ZERO) >= 0;

            HBox summary = new HBox(16);
            summary.setStyle("-fx-padding:6 4 4 4;");
            summary.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

            Label countLbl = new Label(brokerTrades.size() + " trades imported");
            countLbl.setStyle("-fx-text-fill:#8b949e; -fx-font-size:12px;");

            Label pnlLbl = new Label("Total P&L: " + (net ? "+" : "") + "$" + fmt(totalPnl));
            pnlLbl.setStyle("-fx-font-size:12px; -fx-font-weight:bold;"
                    + (net ? "-fx-text-fill:#3fb950;" : "-fx-text-fill:#f85149;"));

            Label closedLbl = new Label(closedCount + " closed");
            closedLbl.setStyle("-fx-text-fill:#8b949e; -fx-font-size:12px;");

            summary.getChildren().addAll(countLbl,
                    new Region() {{ HBox.setHgrow(this, Priority.ALWAYS); }},
                    closedLbl, pnlLbl);

            TableView<Trade> table = buildBrokerTableView();
            table.getItems().setAll(brokerTrades);
            VBox.setVgrow(table, Priority.ALWAYS);

            content.getChildren().addAll(summary, table);
            VBox.setVgrow(content, Priority.ALWAYS);

            tab.setContent(content);
            tradesTabPane.getTabs().add(tab);
        }
    }

    private String brokerIcon(String broker) {
        return switch (broker.toUpperCase()) {
            case "BINANCE"                          -> "🟡";
            case "BYBIT"                            -> "🔵";
            case "ETORO"                            -> "🟢";
            case "MT4", "MT5", "MT4/MT5"           -> "📊";
            case "INTERACTIVE BROKERS", "IB"       -> "🏦";
            default                                 -> "📥";
        };
    }

    /** Builds a read-only TableView with the same columns as recentTradesTable. */
    @SuppressWarnings("unchecked")
    private TableView<Trade> buildBrokerTableView() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MM/dd HH:mm");
        TableView<Trade> table = new TableView<>();
        table.setMinHeight(300);
        table.setStyle("-fx-background-color:#161b22; -fx-border-color:#30363d; -fx-border-width:1;");
        table.setPlaceholder(new Label("No imported trades") {{
            setStyle("-fx-text-fill:#8b949e; -fx-font-size:13px;");
        }});

        TableColumn<Trade, String> cDate = col("DATE", 130,
                t -> t.getEntryTime() != null ? t.getEntryTime().format(dtf) : "—");
        TableColumn<Trade, String> cSymbol = col("SYMBOL", 100, Trade::getSymbol);
        TableColumn<Trade, String> cType   = col("TYPE",   70,  t -> t.getAssetType().name());

        TableColumn<Trade, String> cDir = col("DIR", 60, t -> t.getDirection().name());
        cDir.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null) { setText(null); return; }
                setText(v);
                setStyle("LONG".equals(v)
                        ? "-fx-text-fill:#3fb950; -fx-font-weight:bold;"
                        : "-fx-text-fill:#f85149; -fx-font-weight:bold;");
            }
        });

        TableColumn<Trade, String> cEntry = col("ENTRY", 110,
                t -> t.getEntryPrice() != null ? "$" + t.getEntryPrice().toPlainString() : "—");
        TableColumn<Trade, String> cExit  = col("EXIT",  110,
                t -> t.getExitPrice()  != null ? "$" + t.getExitPrice().toPlainString()  : "OPEN");
        TableColumn<Trade, String> cQty   = col("QTY",   90,
                t -> t.getQuantity() != null ? t.getQuantity().toPlainString() : "—");

        TableColumn<Trade, String> cPnl = col("P&L $", 100, t -> {
            BigDecimal p = t.getPnlAmount();
            return p == null ? "—" : (p.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + "$" + fmt(p);
        });
        cPnl.setCellFactory(colPnl.getCellFactory());

        TableColumn<Trade, String> cPnlPct = col("P&L %", 90, t -> {
            BigDecimal p = t.getPnlPercent();
            return p == null ? "—" : (p.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "") + fmt(p) + "%";
        });
        cPnlPct.setCellFactory(colPnl.getCellFactory());

        TableColumn<Trade, String> cStatus = col("STATUS", 80, t -> t.getStatus().name());
        cStatus.setCellFactory(colStatus.getCellFactory());

        table.getColumns().addAll(cDate, cSymbol, cType, cDir,
                cEntry, cExit, cQty, cPnl, cPnlPct, cStatus);

        table.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Trade t, boolean empty) {
                super.updateItem(t, empty);
                getStyleClass().removeAll("row-profit", "row-loss");
                if (!empty && t != null && t.getPnlAmount() != null)
                    getStyleClass().add(t.getPnlAmount().compareTo(BigDecimal.ZERO) >= 0
                            ? "row-profit" : "row-loss");
            }
        });

        return table;
    }

    private TableColumn<Trade, String> col(String title, double pref,
                                            java.util.function.Function<Trade, String> extractor) {
        TableColumn<Trade, String> c = new TableColumn<>(title);
        c.setPrefWidth(pref);
        c.setCellValueFactory(cellData ->
                new SimpleStringProperty(
                        extractor.apply(cellData.getValue())));
        return c;
    }

    // ── Trades Table ──────────────────────────────────────────

    private void setupTradeTable() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("MM/dd HH:mm");

        colDate.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getEntryTime() != null
                        ? c.getValue().getEntryTime().format(dtf) : "—"));

        colSymbol.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getSymbol()));

        colType.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getAssetType().name()));

        colDir.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getDirection().name()));
        colDir.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item);
                setStyle("LONG".equals(item)
                        ? "-fx-text-fill:#3fb950; -fx-font-weight:bold;"
                        : "-fx-text-fill:#f85149; -fx-font-weight:bold;");
            }
        });

        colEntry.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getEntryPrice() != null
                        ? "$" + c.getValue().getEntryPrice().toPlainString() : "—"));

        colExit.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getExitPrice() != null
                        ? "$" + c.getValue().getExitPrice().toPlainString() : "OPEN"));

        colQty.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getQuantity() != null
                        ? c.getValue().getQuantity().toPlainString() : "—"));

        colPnl.setCellValueFactory(c -> {
            BigDecimal pnl = c.getValue().getPnlAmount();
            if (pnl == null) return new SimpleStringProperty("—");
            return new SimpleStringProperty((pnl.compareTo(BigDecimal.ZERO)>=0 ? "+" : "")
                    + "$" + fmt(pnl));
        });
        colPnl.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.equals("—")) {
                    setText(item); setStyle(""); return;
                }
                setText(item);
                setStyle(item.startsWith("+")
                        ? "-fx-text-fill:#3fb950; -fx-font-weight:bold;"
                        : "-fx-text-fill:#f85149; -fx-font-weight:bold;");
            }
        });

        colPnlPct.setCellValueFactory(c -> {
            BigDecimal pct = c.getValue().getPnlPercent();
            if (pct == null) return new SimpleStringProperty("—");
            return new SimpleStringProperty(
                    (pct.compareTo(BigDecimal.ZERO)>=0 ? "+" : "")
                            + fmt(pct) + "%");
        });
        colPnlPct.setCellFactory(colPnl.getCellFactory());

        colStatus.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getStatus().name()));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item);
                setStyle(switch(item) {
                    case "OPEN"      -> "-fx-text-fill:#d29922;";
                    case "CLOSED"    -> "-fx-text-fill:#8b949e;";
                    case "CANCELLED" -> "-fx-text-fill:#484f58;";
                    default          -> "";
                });
            }
        });

        // Actions column — edit / close / delete (T-11 adds the Delete button).
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn   = new Button("✏");
            private final Button closeBtn  = new Button("✓");
            private final Button deleteBtn = new Button("🗑");
            private final HBox   box       = new HBox(4, editBtn, closeBtn, deleteBtn);
            {
                editBtn.setStyle("-fx-background-color:#1f6feb; -fx-text-fill:white;"
                        + "-fx-background-radius:4; -fx-cursor:hand; -fx-padding:2 6;");
                closeBtn.setStyle("-fx-background-color:#238636; -fx-text-fill:white;"
                        + "-fx-background-radius:4; -fx-cursor:hand; -fx-padding:2 6;");
                deleteBtn.setStyle("-fx-background-color:#da3633; -fx-text-fill:white;"
                        + "-fx-background-radius:4; -fx-cursor:hand; -fx-padding:2 6;");
                editBtn.setTooltip(new Tooltip("Edit trade"));
                closeBtn.setTooltip(new Tooltip("Close open trade"));
                deleteBtn.setTooltip(new Tooltip("Delete trade"));

                editBtn.setOnAction(e -> {
                    Trade t = getTableView().getItems().get(getIndex());
                    if (onEditTradeCallback != null) onEditTradeCallback.accept(t);
                });
                closeBtn.setOnAction(e -> {
                    Trade t = getTableView().getItems().get(getIndex());
                    if (t.getStatus() == Trade.TradeStatus.OPEN) {
                        showCloseTradeDialog(t);
                    }
                });
                deleteBtn.setOnAction(e -> {
                    Trade t = getTableView().getItems().get(getIndex());
                    showDeleteTradeDialog(t);
                });
            }
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0
                        || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }
                Trade trade = getTableView().getItems().get(getIndex());
                closeBtn.setDisable(trade.getStatus() != Trade.TradeStatus.OPEN);
                setGraphic(box);
            }
        });

        // Color rows by profit/loss
        recentTradesTable.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(Trade t, boolean empty) {
                super.updateItem(t, empty);
                getStyleClass().removeAll("row-profit","row-loss");
                if (!empty && t != null && t.getPnlAmount() != null) {
                    getStyleClass().add(
                            t.getPnlAmount().compareTo(BigDecimal.ZERO) >= 0
                                    ? "row-profit" : "row-loss");
                }
            }
        });
    }

    private void populateTable(List<Trade> trades) {
        recentTradesTable.getItems().setAll(trades);
    }

    /** T-11: confirm-and-delete dialog. Cascades through TradeService.deleteTrade. */
    private void showDeleteTradeDialog(Trade trade) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Trade");
        confirm.setHeaderText("Delete " + trade.getSymbol()
                + " (" + trade.getDirection() + ")?");
        confirm.setContentText("This permanently removes the trade and its P&L from "
                + "this profile's history. Continue?");
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        confirm.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                try {
                    tradeService.deleteTrade(trade.getId());
                    refreshAll();
                } catch (Exception ex) {
                    new Alert(Alert.AlertType.ERROR,
                            "Failed to delete trade: " + ex.getMessage()).showAndWait();
                }
            }
        });
    }

    private void showCloseTradeDialog(Trade trade) {
        TextInputDialog dialog = new TextInputDialog(
                trade.getEntryPrice().toPlainString());
        dialog.setTitle("Close Trade");
        dialog.setHeaderText("Close: " + trade.getSymbol());
        dialog.setContentText("Exit Price:");
        dialog.showAndWait().ifPresent(priceStr -> {
            try {
                BigDecimal exitPrice = new BigDecimal(priceStr.trim());
                tradeService.closeTrade(trade.getId(), exitPrice);
                refreshAll();
            } catch (Exception e) {
                new Alert(Alert.AlertType.ERROR,
                        "Invalid price: " + e.getMessage()).showAndWait();
            }
        });
    }

    // ── Navigation ─────────────────────────────────────────────
    @FXML public void onNewTrade() { if (onNewTradeCallback != null) onNewTradeCallback.run(); }
    @FXML public void onViewAll()  { if (onViewAllCallback  != null) onViewAllCallback.run(); }
    @FXML public void onFilter1W() { filterEquityCurve(7); }
    @FXML public void onFilter1M() { filterEquityCurve(30); }
    @FXML public void onFilter3M() { filterEquityCurve(90); }
    @FXML public void onFilterAll() { drawEquityCurve(fullEquityCurve); }

    private void filterEquityCurve(int days) {
        int n = Math.min(fullEquityCurve.size(), days);
        if (n > 0)
            drawEquityCurve(fullEquityCurve.subList(
                    fullEquityCurve.size() - n, fullEquityCurve.size()));
    }

    public void setOnNewTradeCallback(Runnable r) { onNewTradeCallback = r; }
    public void setOnViewAllCallback(Runnable r)  { onViewAllCallback  = r; }
    public void setOnEditTradeCallback(java.util.function.Consumer<Trade> c) { onEditTradeCallback = c; }

    private String fmt(BigDecimal bd) {
        if (bd == null) return "0.00";
        return bd.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
