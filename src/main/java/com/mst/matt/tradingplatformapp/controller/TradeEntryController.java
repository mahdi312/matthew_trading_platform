package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.model.*;
import com.mst.matt.tradingplatformapp.model.Trade.*;
import com.mst.matt.tradingplatformapp.service.TradeService;
import com.mst.matt.tradingplatformapp.service.price.PriceRouter;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import net.rgielen.fxweaver.core.FxmlView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;
import java.util.function.Consumer;

/**
 * Controller for the Trade Entry form.
 * Handles input validation, live P&L preview, and price fetching.
 */
@Component
@FxmlView("/fxml/TradeEntry.fxml")
public class TradeEntryController implements Initializable {

    // ── FXML nodes ─────────────────────────────────────────
    @FXML private TextField symbolField;
    @FXML private ComboBox<AssetType> assetTypeCombo;
    @FXML private TextField exchangeField;
    @FXML private ToggleButton longBtn;
    @FXML private ToggleButton shortBtn;
    @FXML private TextField strategyField;
    @FXML private TextField entryPriceField;
    @FXML private TextField exitPriceField;
    @FXML private TextField quantityField;
    @FXML private TextField stopLossField;
    @FXML private TextField takeProfitField;
    @FXML private TextField feeField;
    @FXML private DatePicker entryDatePicker;
    @FXML private TextField entryTimeField;
    @FXML private DatePicker exitDatePicker;
    @FXML private TextArea notesArea;
    @FXML private Label symbolValidation;
    @FXML private Label currentPriceLabel;
    @FXML private Label investedLabel;
    @FXML private Label pnlAmountLabel;
    @FXML private Label pnlPercentLabel;
    @FXML private Label rrLabel;
    @FXML private Label formTitleLabel;

    // ── Spring services ─────────────────────────────────────
    @Autowired private TradeService tradeService;
    @Autowired private PriceRouter  priceRouter;

    private UserProfile currentProfile;
    private Trade       editingTrade;  // non-null if editing existing trade
    private boolean     isLong = true;
    private Consumer<Trade> onSaveCallback;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Populate asset type combo
        assetTypeCombo.getItems().setAll(AssetType.values());
        assetTypeCombo.setValue(AssetType.CRYPTO);

        // Set default date to today
        entryDatePicker.setValue(LocalDate.now());
        entryTimeField.setText(LocalTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm")));

        // Long selected by default
        isLong = true;
        styleDirectionButtons();

        // Add change listeners for live P&L preview
        addPnlListeners();
    }

    // ── Direction Buttons ────────────────────────────────────

    @FXML public void onLongSelected() {
        isLong = true;
        styleDirectionButtons();
        updatePnlPreview();
    }

    @FXML public void onShortSelected() {
        isLong = false;
        styleDirectionButtons();
        updatePnlPreview();
    }

    private void styleDirectionButtons() {
        longBtn.setSelected(isLong);
        shortBtn.setSelected(!isLong);

        if (isLong) {
            longBtn.setStyle("-fx-background-color: #1a4a1a; -fx-text-fill: #3fb950;"
                    + "-fx-background-radius: 6; -fx-padding: 10 24;"
                    + "-fx-cursor: hand; -fx-font-weight: bold;");
            shortBtn.setStyle("-fx-background-color: #21262d; -fx-text-fill: #8b949e;"
                    + "-fx-background-radius: 6; -fx-padding: 10 24; -fx-cursor: hand;");
        } else {
            shortBtn.setStyle("-fx-background-color: #4a1a1a; -fx-text-fill: #f85149;"
                    + "-fx-background-radius: 6; -fx-padding: 10 24;"
                    + "-fx-cursor: hand; -fx-font-weight: bold;");
            longBtn.setStyle("-fx-background-color: #21262d; -fx-text-fill: #8b949e;"
                    + "-fx-background-radius: 6; -fx-padding: 10 24; -fx-cursor: hand;");
        }
    }

    // ── Fetch Live Price ──────────────────────────────────────

    @FXML public void onFetchPrice() {
        String symbol = symbolField.getText().trim().toUpperCase();
        if (symbol.isEmpty()) {
            symbolValidation.setText("⚠ Enter a symbol first");
            return;
        }

        symbolValidation.setText("Fetching...");
        currentPriceLabel.setText("Loading...");

        Thread.ofVirtual().start(() -> {
            priceRouter.getQuote(symbol).ifPresentOrElse(
                    quote -> Platform.runLater(() -> {
                        String price = quote.getPrice().toPlainString();
                        entryPriceField.setText(price);
                        String changeStr = quote.getChangePct24h() != null
                                ? quote.getChangePct24h()
                                .setScale(2, RoundingMode.HALF_UP).toPlainString() + "%"
                                : "N/A";
                        currentPriceLabel.setText("Current: $" + price
                                + "  " + (quote.isUp() ? "▲" : "▼") + " " + changeStr);
                        currentPriceLabel.setStyle(quote.isUp()
                                ? "-fx-text-fill: #3fb950; -fx-font-size:11px;"
                                : "-fx-text-fill: #f85149; -fx-font-size:11px;");
                        String name = quote.getAssetName() != null && !quote.getAssetName().isBlank()
                                ? quote.getAssetName() : symbol;
                        symbolValidation.setText("✓ " + name);
                        symbolValidation.setStyle("-fx-text-fill: #3fb950;");

                        // Auto-detect asset type
                        assetTypeCombo.setValue(quote.getAssetType());
                        if (quote.getExchange() != null)
                            exchangeField.setText(quote.getExchange());
                    }),
                    () -> Platform.runLater(() -> {
                        symbolValidation.setText("⚠ Symbol not found");
                        symbolValidation.setStyle("-fx-text-fill: #f85149;");
                    })
            );
        });
    }

    // ── Live P&L Preview ─────────────────────────────────────

    private void addPnlListeners() {
        entryPriceField.textProperty().addListener((o,a,b) -> updatePnlPreview());
        exitPriceField.textProperty() .addListener((o,a,b) -> updatePnlPreview());
        quantityField.textProperty()  .addListener((o,a,b) -> updatePnlPreview());
        stopLossField.textProperty()  .addListener((o,a,b) -> updatePnlPreview());
        takeProfitField.textProperty().addListener((o,a,b) -> updatePnlPreview());
        feeField.textProperty()       .addListener((o,a,b) -> updatePnlPreview());
    }

    private void updatePnlPreview() {
        try {
            BigDecimal entry    = parseBD(entryPriceField.getText());
            BigDecimal exit     = parseBD(exitPriceField.getText());
            BigDecimal qty      = parseBD(quantityField.getText());
            BigDecimal fee      = parseBD(feeField.getText());
            BigDecimal sl       = parseBD(stopLossField.getText());
            BigDecimal tp       = parseBD(takeProfitField.getText());

            if (entry.compareTo(BigDecimal.ZERO) <= 0
                    || qty.compareTo(BigDecimal.ZERO) <= 0) return;

            BigDecimal invested = entry.multiply(qty);
            investedLabel.setText("$" + format(invested));

            // P&L (only if exit is set)
            if (exit.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal diff  = isLong
                        ? exit.subtract(entry)
                        : entry.subtract(exit);
                BigDecimal pnl   = diff.multiply(qty).subtract(fee);
                BigDecimal pct   = diff.divide(entry, 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

                pnlAmountLabel.setText((pnl.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "")
                        + "$" + format(pnl));
                pnlPercentLabel.setText((pct.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "")
                        + format(pct) + "%");

                String color = pnl.compareTo(BigDecimal.ZERO) >= 0 ? "#3fb950" : "#f85149";
                pnlAmountLabel.setStyle("-fx-font-size:28px; -fx-font-weight:bold;"
                        + "-fx-text-fill: " + color + ";");
                pnlPercentLabel.setStyle("-fx-font-size:28px; -fx-font-weight:bold;"
                        + "-fx-text-fill: " + color + ";");
            }

            // R:R ratio
            if (sl.compareTo(BigDecimal.ZERO) > 0
                    && tp.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal risk   = isLong ? entry.subtract(sl) : sl.subtract(entry);
                BigDecimal reward = isLong ? tp.subtract(entry) : entry.subtract(tp);
                if (risk.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal rr = reward.divide(risk, 2, RoundingMode.HALF_UP);
                    rrLabel.setText("1:" + rr.toPlainString());
                    rrLabel.setStyle("-fx-font-size:28px; -fx-font-weight:bold;"
                            + (rr.compareTo(BigDecimal.valueOf(2)) >= 0
                            ? "-fx-text-fill: #3fb950;"
                            : rr.compareTo(BigDecimal.ONE) >= 0
                            ? "-fx-text-fill: #d29922;"
                            : "-fx-text-fill: #f85149;"));
                }
            }

        } catch (Exception ignored) {}
    }

    // ── Save ────────────────────────────────────────────────

    @FXML public void onSave() {
        if (!validate()) return;
        if (currentProfile == null) {
            new Alert(Alert.AlertType.WARNING,
                    "No trading profile selected. Choose a profile in the header first.")
                    .showAndWait();
            return;
        }

        try {
            Trade trade = (editingTrade != null)
                    ? editingTrade
                    : new Trade();

            trade.setProfile(currentProfile);
            String sym = symbolField.getText().trim().toUpperCase();
            trade.setSymbol(sym);
            trade.setAssetName(sym);
            trade.setAssetType(assetTypeCombo.getValue());
            trade.setDirection(isLong ? TradeDirection.LONG : TradeDirection.SHORT);
            trade.setEntryPrice(parseBD(entryPriceField.getText()));
            trade.setQuantity(parseBD(quantityField.getText()));
            trade.setExchange(exchangeField.getText().trim());
            trade.setStrategy(strategyField.getText().trim());
            trade.setNotes(notesArea.getText().trim());

            String exitText = exitPriceField.getText().trim();
            if (!exitText.isEmpty()) {
                trade.setExitPrice(parseBD(exitText));
                trade.setStatus(TradeStatus.CLOSED);
                trade.setExitTime(exitDatePicker.getValue() != null
                        ? exitDatePicker.getValue().atTime(LocalTime.now())
                        : LocalDateTime.now());
            } else {
                trade.setExitPrice(null);
                trade.setExitTime(null);
                trade.setPnlAmount(null);
                trade.setPnlPercent(null);
                trade.setStatus(TradeStatus.OPEN);
            }

            if (!stopLossField.getText().isBlank())
                trade.setStopLoss(parseBD(stopLossField.getText()));
            else
                trade.setStopLoss(null);
            if (!takeProfitField.getText().isBlank())
                trade.setTakeProfit(parseBD(takeProfitField.getText()));
            else
                trade.setTakeProfit(null);
            if (!feeField.getText().isBlank())
                trade.setFee(parseBD(feeField.getText()));
            else
                trade.setFee(null);

            LocalDate entryDate = entryDatePicker.getValue();
            if (entryDate == null) {
                new Alert(Alert.AlertType.WARNING, "Entry date is required.").showAndWait();
                return;
            }
            LocalTime entryTime = parseTime(entryTimeField.getText());
            trade.setEntryTime(LocalDateTime.of(entryDate, entryTime));

            Trade saved = tradeService.saveTrade(trade);

            if (onSaveCallback != null) onSaveCallback.accept(saved);

            // Show success feedback
            symbolValidation.setText("✅ Trade saved!");
            symbolValidation.setStyle("-fx-text-fill: #3fb950;");

            if (editingTrade == null) clearForm();

        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR,
                    "Failed to save trade: " + e.getMessage()).showAndWait();
        }
    }

    @FXML public void onCancel() {
        if (onSaveCallback != null) onSaveCallback.accept(null);
        clearForm();
    }

    // ── Helpers ─────────────────────────────────────────────

    private boolean validate() {
        boolean valid = true;

        if (symbolField.getText().trim().isEmpty()) {
            symbolValidation.setText("⚠ Symbol is required");
            valid = false;
        }
        if (entryPriceField.getText().trim().isEmpty()
                || parseBD(entryPriceField.getText()).compareTo(BigDecimal.ZERO) <= 0) {
            entryPriceField.setStyle("-fx-border-color: #f85149;");
            valid = false;
        }
        if (quantityField.getText().trim().isEmpty()
                || parseBD(quantityField.getText()).compareTo(BigDecimal.ZERO) <= 0) {
            quantityField.setStyle("-fx-border-color: #f85149;");
            valid = false;
        }
        if (assetTypeCombo.getValue() == null) {
            valid = false;
        }
        if (entryDatePicker.getValue() == null) {
            valid = false;
        }
        return valid;
    }

    private BigDecimal parseBD(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(s.trim()); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private LocalTime parseTime(String s) {
        try { return LocalTime.parse(s.trim(),
                DateTimeFormatter.ofPattern("HH:mm")); }
        catch (Exception e) { return LocalTime.now(); }
    }

    private String format(BigDecimal bd) {
        return bd.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private void clearForm() {
        symbolField.clear(); entryPriceField.clear();
        exitPriceField.clear(); quantityField.clear();
        stopLossField.clear(); takeProfitField.clear();
        feeField.clear(); notesArea.clear();
        strategyField.clear(); exchangeField.clear();
        symbolValidation.setText("");
        symbolValidation.setStyle("-fx-font-size:11px;");
        currentPriceLabel.setText("Current: —");
        currentPriceLabel.setStyle("-fx-text-fill:#388bfd; -fx-font-size:11px;");
        entryDatePicker.setValue(LocalDate.now());
        entryTimeField.setText(LocalTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm")));
        exitDatePicker.setValue(null);
        investedLabel.setText("$0.00");
        pnlAmountLabel.setText("$0.00");
        pnlPercentLabel.setText("0.00%");
        rrLabel.setText("—");
        isLong = true;
        if (formTitleLabel != null) formTitleLabel.setText("📋 New Trade Entry");
        styleDirectionButtons();
    }

    // ── Public API for parent controllers ────────────────────

    public void setProfile(UserProfile profile)      { this.currentProfile = profile; }
    public void setEditingTrade(Trade trade) {
        this.editingTrade = trade;
        if (trade != null) {
            if (formTitleLabel != null) formTitleLabel.setText("✏ Edit Trade");
            populateForm(trade);
        } else {
            clearForm(); // entering "new trade" mode — reset everything
        }
    }
    public void setOnSaveCallback(Consumer<Trade> cb) { this.onSaveCallback = cb; }

    private void populateForm(Trade t) {
        clearForm();
        if (formTitleLabel != null) formTitleLabel.setText("✏ Edit Trade");
        symbolField.setText(t.getSymbol());
        assetTypeCombo.setValue(t.getAssetType());
        exchangeField.setText(t.getExchange() != null ? t.getExchange() : "");
        strategyField.setText(t.getStrategy() != null ? t.getStrategy() : "");
        entryPriceField.setText(t.getEntryPrice().toPlainString());
        if (t.getExitPrice() != null)
            exitPriceField.setText(t.getExitPrice().toPlainString());
        if (t.getExitTime() != null)
            exitDatePicker.setValue(t.getExitTime().toLocalDate());
        quantityField.setText(t.getQuantity().toPlainString());
        if (t.getStopLoss() != null)
            stopLossField.setText(t.getStopLoss().toPlainString());
        if (t.getTakeProfit() != null)
            takeProfitField.setText(t.getTakeProfit().toPlainString());
        if (t.getFee() != null)
            feeField.setText(t.getFee().toPlainString());
        entryDatePicker.setValue(t.getEntryTime().toLocalDate());
        entryTimeField.setText(t.getEntryTime()
                .format(DateTimeFormatter.ofPattern("HH:mm")));
        notesArea.setText(t.getNotes() != null ? t.getNotes() : "");
        isLong = t.getDirection() == TradeDirection.LONG;
        styleDirectionButtons();
        updatePnlPreview();
    }
}
