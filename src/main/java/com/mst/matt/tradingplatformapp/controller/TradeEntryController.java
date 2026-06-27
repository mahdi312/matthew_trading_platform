package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.model.*;
import com.mst.matt.tradingplatformapp.model.Trade.*;
import com.mst.matt.tradingplatformapp.service.BrokerImportService;
import com.mst.matt.tradingplatformapp.service.BrokerImportService.ImportResult;
import com.mst.matt.tradingplatformapp.service.TradeService;
import com.mst.matt.tradingplatformapp.service.price.PriceRouter;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import net.rgielen.fxweaver.core.FxmlView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
    @FXML private TextField leverageField;
    @FXML private Label     leverageLabel;
    @FXML private Label     leveragedPnlLabel;
    @FXML private Label     leveragedPnlHint;
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
    @FXML private Label     formTitleLabel;
    @FXML private Label     importStatusLabel;
    // ── Screenshot field ─────────────────────────────────────
    @FXML private StackPane screenshotPane;
    @FXML private ImageView screenshotImageView;
    @FXML private Label     screenshotPlaceholderLabel;
    @FXML private Label     screenshotPathLabel;
    @FXML private Button    uploadImageBtn;
    @FXML private Button    clearImageBtn;

    // ── Spring services ─────────────────────────────────────
    @Autowired private TradeService       tradeService;
    @Autowired private PriceRouter        priceRouter;
    @Autowired private BrokerImportService brokerImportService;

    private UserProfile currentProfile;
    private Trade       editingTrade;     // non-null if editing existing trade
    private boolean     isLong = true;
    private Consumer<Trade> onSaveCallback;

    /** Currently attached screenshot path (absolute file path), or {@code null} if none. */
    private String currentScreenshotPath;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Populate asset type combo
        assetTypeCombo.getItems().setAll(AssetType.values());
        assetTypeCombo.setValue(AssetType.CRYPTO);
        styleComboBox(assetTypeCombo);

        // Set default date to today
        entryDatePicker.setValue(LocalDate.now());
        entryTimeField.setText(LocalTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm")));
        styleDatePicker(entryDatePicker);
        styleDatePicker(exitDatePicker);

        // Long selected by default
        isLong = true;
        styleDirectionButtons();

        // Add change listeners for live P&L preview
        addPnlListeners();
        styleNotesArea();
    }

    private void styleNotesArea() {
        if (notesArea == null) return;
        notesArea.setStyle("-fx-control-inner-background:#f5f5f5; -fx-background-color:#f5f5f5;"
                + "-fx-text-fill:#1a1a1a; -fx-prompt-text-fill:#999999;"
                + "-fx-border-color:#cccccc; -fx-border-radius:6;"
                + "-fx-background-radius:6; -fx-padding:8; -fx-font-size:13px;");
    }

    /** Apply dark theme programmatically to a ComboBox (ensures button-cell text is visible). */
    private <T> void styleComboBox(ComboBox<T> combo) {
        String darkCell = "-fx-background-color:#0d1117; -fx-text-fill:#e6edf3;"
                + "-fx-padding:4 8; -fx-font-size:13px;";
        combo.setCellFactory(lv -> new ListCell<T>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
                setStyle(empty ? "" : darkCell);
            }
        });
        combo.setButtonCell(new ListCell<T>() {
            @Override protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
                setStyle(darkCell);
                // Force the combo box container dark
                combo.setStyle("-fx-background-color:#0d1117; -fx-text-fill:#e6edf3;"
                        + "-fx-border-color:#30363d; -fx-border-radius:6;"
                        + "-fx-background-radius:6;");
            }
        });
    }

    /** Apply dark background to a DatePicker (the popup inherits from CSS). */
    private void styleDatePicker(DatePicker dp) {
        if (dp == null) return;
        dp.setStyle("-fx-background-color:#0d1117; -fx-text-fill:#e6edf3;"
                + "-fx-border-color:#30363d; -fx-border-radius:6;"
                + "-fx-background-radius:6;");
        // Ensure the text field inside is also dark
        dp.getEditor().setStyle("-fx-background-color:#0d1117; -fx-text-fill:#e6edf3;"
                + "-fx-border-color:transparent; -fx-padding:6 10;");
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
        if (leverageField != null) {
            leverageField.textProperty().addListener((o, a, b) -> {
                updateLeverageLabel();
                updatePnlPreview();
            });
        }
    }

    private void updateLeverageLabel() {
        if (leverageField == null || leverageLabel == null) return;
        BigDecimal lev = parseLeverage();
        leverageLabel.setText("×" + lev.setScale(0, RoundingMode.HALF_UP).toPlainString());
    }

    private BigDecimal parseLeverage() {
        if (leverageField == null) return BigDecimal.ONE;
        BigDecimal lev = parseBD(leverageField.getText());
        return (lev.compareTo(BigDecimal.ONE) < 0) ? BigDecimal.ONE : lev;
    }

    private void updatePnlPreview() {
        try {
            BigDecimal entry    = parseBD(entryPriceField.getText());
            BigDecimal exit     = parseBD(exitPriceField.getText());
            BigDecimal qty      = parseBD(quantityField.getText());
            BigDecimal fee      = parseBD(feeField.getText());
            BigDecimal sl       = parseBD(stopLossField.getText());
            BigDecimal tp       = parseBD(takeProfitField.getText());
            BigDecimal lev      = parseLeverage();

            if (entry.compareTo(BigDecimal.ZERO) <= 0
                    || qty.compareTo(BigDecimal.ZERO) <= 0) return;

            BigDecimal invested = entry.multiply(qty);
            investedLabel.setText("$" + format(invested));

            // P&L (only if exit is set)
            if (exit.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal diff  = isLong
                        ? exit.subtract(entry)
                        : entry.subtract(exit);
                // Base P&L (no leverage)
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

                // Leveraged P&L display
                if (leveragedPnlLabel != null) {
                    BigDecimal levPnl = diff.multiply(qty).multiply(lev).subtract(fee);
                    BigDecimal levPct = diff.divide(entry, 6, RoundingMode.HALF_UP)
                            .multiply(lev).multiply(BigDecimal.valueOf(100));
                    String levColor = levPnl.compareTo(BigDecimal.ZERO) >= 0 ? "#bc8cff" : "#f85149";
                    leveragedPnlLabel.setText(
                            (levPnl.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "")
                            + "$" + format(levPnl)
                            + " (" + (levPct.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "")
                            + format(levPct) + "%)");
                    leveragedPnlLabel.setStyle("-fx-font-size:16px; -fx-font-weight:bold;"
                            + "-fx-text-fill: " + levColor + ";");
                    if (leveragedPnlHint != null) {
                        leveragedPnlHint.setText("×" + lev.setScale(0, RoundingMode.HALF_UP)
                                .toPlainString() + " leverage");
                    }
                }
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
            // Leverage
            if (leverageField != null && !leverageField.getText().isBlank()) {
                BigDecimal lev = parseLeverage();
                trade.setLeverage(lev.compareTo(BigDecimal.ONE) > 0 ? lev : null);
            } else {
                trade.setLeverage(null);
            }

            LocalDate entryDate = entryDatePicker.getValue();
            if (entryDate == null) {
                new Alert(Alert.AlertType.WARNING, "Entry date is required.").showAndWait();
                return;
            }
            LocalTime entryTime = parseTime(entryTimeField.getText());
            trade.setEntryTime(LocalDateTime.of(entryDate, entryTime));

            // Attach screenshot if present
            if (currentScreenshotPath != null && !currentScreenshotPath.isBlank())
                trade.setScreenshotPath(currentScreenshotPath);

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
        if (leverageField != null) leverageField.clear();
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
        pnlAmountLabel.setStyle("-fx-font-size:26px; -fx-font-weight:bold; -fx-text-fill:#8b949e;");
        pnlPercentLabel.setText("0.00%");
        pnlPercentLabel.setStyle("-fx-font-size:26px; -fx-font-weight:bold; -fx-text-fill:#8b949e;");
        rrLabel.setText("—");
        rrLabel.setStyle("-fx-font-size:26px; -fx-font-weight:bold; -fx-text-fill:#388bfd;");
        if (leveragedPnlLabel != null) leveragedPnlLabel.setText("—");
        if (leveragedPnlHint  != null) leveragedPnlHint.setText("×1 leverage");
        if (leverageLabel     != null) leverageLabel.setText("×1");
        isLong = true;
        if (formTitleLabel != null) formTitleLabel.setText("📋 New Trade Entry");
        styleDirectionButtons();
        styleNotesArea();
        // Re-apply dark theme to pickers in case JavaFX reset them
        styleDatePicker(entryDatePicker);
        styleDatePicker(exitDatePicker);
        // Clear screenshot
        currentScreenshotPath = null;
        refreshScreenshotDisplay();
    }

    // ── Screenshot field ─────────────────────────────────────────────────────

    /**
     * Attaches a screenshot to the form (called automatically when opened from +Trade,
     * or manually via upload).
     */
    public void setScreenshotPath(String path) {
        currentScreenshotPath = path;
        refreshScreenshotDisplay();
    }

    @FXML public void onUploadImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Chart Screenshot");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.PNG", "*.JPG"));
        File file = chooser.showOpenDialog(
                symbolField.getScene() != null ? symbolField.getScene().getWindow() : null);
        if (file == null) return;

        try {
            // Copy to screenshots dir with standardised name
            String homeDir = System.getProperty("user.home");
            Path screenshotDir = Path.of(homeDir, ".trading-platform", "screenshots");
            Files.createDirectories(screenshotDir);
            String sym  = symbolField.getText().trim().toUpperCase().replaceAll("[^A-Za-z0-9]", "");
            String date = LocalDate.now().toString();
            String dest = date + "_" + (sym.isBlank() ? "" : sym + "_") + System.currentTimeMillis() + ".png";
            Path target = screenshotDir.resolve(dest);
            Files.copy(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            currentScreenshotPath = target.toAbsolutePath().toString();
        } catch (Exception e) {
            // Fallback: use original path directly
            currentScreenshotPath = file.getAbsolutePath();
        }
        refreshScreenshotDisplay();
    }

    @FXML public void onClearImage() {
        currentScreenshotPath = null;
        refreshScreenshotDisplay();
    }

    private void refreshScreenshotDisplay() {
        if (screenshotImageView == null) return;
        if (currentScreenshotPath != null) {
            try {
                File f = new File(currentScreenshotPath);
                if (f.exists()) {
                    Image img = new Image(f.toURI().toString(), true);
                    screenshotImageView.setImage(img);
                    screenshotImageView.setVisible(true);
                    if (screenshotPlaceholderLabel != null)
                        screenshotPlaceholderLabel.setVisible(false);
                    if (screenshotPathLabel != null)
                        screenshotPathLabel.setText("📄 " + f.getName() + "  🔍 Click to enlarge");
                    // Wire click-to-enlarge on the thumbnail
                    screenshotImageView.setOnMouseClicked(ev -> {
                        if (ev.getButton() == MouseButton.PRIMARY) openScreenshotPreview();
                    });
                    screenshotImageView.setStyle("-fx-cursor: hand;");
                    if (screenshotPane != null) {
                        screenshotPane.setOnMouseClicked(ev -> {
                            if (ev.getButton() == MouseButton.PRIMARY
                                    && screenshotImageView.isVisible()) openScreenshotPreview();
                        });
                        screenshotPane.setStyle("-fx-cursor: hand;");
                    }
                    return;
                }
            } catch (Exception ignored) {}
        }
        // No image – show placeholder
        screenshotImageView.setImage(null);
        screenshotImageView.setVisible(false);
        screenshotImageView.setOnMouseClicked(null);
        screenshotImageView.setStyle("");
        if (screenshotPane != null) {
            screenshotPane.setOnMouseClicked(null);
            screenshotPane.setStyle("");
        }
        if (screenshotPlaceholderLabel != null)
            screenshotPlaceholderLabel.setVisible(true);
        if (screenshotPathLabel != null)
            screenshotPathLabel.setText("");
    }

    /**
     * Opens a resizable pop-up dialog showing the screenshot at a large size.
     * The dialog includes:
     *   • Scroll/pan capability via a ScrollPane
     *   • Zoom in / out buttons (+/- keys also work)
     *   • A Close button
     *   • Mouse-scroll to zoom
     */
    private void openScreenshotPreview() {
        if (currentScreenshotPath == null) return;
        File f = new File(currentScreenshotPath);
        if (!f.exists()) return;

        Image img;
        try {
            img = new Image(f.toURI().toString());
        } catch (Exception ex) {
            return;
        }

        Stage dialog = new Stage(StageStyle.DECORATED);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("📸 Screenshot – " + f.getName());
        dialog.setResizable(true);

        // ── Image view with zoom support ──────────────────────────────────
        ImageView bigView = new ImageView(img);
        bigView.setPreserveRatio(true);
        bigView.setSmooth(true);
        // Start at a sensible initial size (fit within ~800×600)
        double initW = Math.min(img.getWidth(),  800);
        double initH = Math.min(img.getHeight(), 600);
        bigView.setFitWidth(initW);
        bigView.setFitHeight(initH);

        StackPane imgContainer = new StackPane(bigView);
        imgContainer.setStyle("-fx-background-color:#0d1117;");

        ScrollPane scroll = new ScrollPane(imgContainer);
        scroll.setStyle("-fx-background-color:#0d1117; -fx-background:#0d1117;");
        scroll.setPannable(true);
        scroll.setFitToWidth(false);
        scroll.setFitToHeight(false);

        // ── Zoom helpers ──────────────────────────────────────────────────
        final double[] scale = {1.0};   // mutable holder
        Runnable applyZoom = () -> {
            double s = scale[0];
            bigView.setFitWidth(img.getWidth() * s);
            bigView.setFitHeight(img.getHeight() * s);
        };
        Runnable zoomIn  = () -> { scale[0] = Math.min(scale[0] * 1.2, 8.0); applyZoom.run(); };
        Runnable zoomOut = () -> { scale[0] = Math.max(scale[0] / 1.2, 0.1); applyZoom.run(); };
        Runnable fitWin  = () -> {
            double ww = dialog.getScene() != null ? dialog.getScene().getWidth()  - 40 : 800;
            double wh = dialog.getScene() != null ? dialog.getScene().getHeight() - 80 : 560;
            double s  = Math.min(ww / img.getWidth(), wh / img.getHeight());
            scale[0]  = Math.max(0.05, s);
            applyZoom.run();
        };

        // Scroll-wheel zoom
        scroll.addEventFilter(ScrollEvent.SCROLL, ev -> {
            if (ev.isControlDown() || true) {   // always zoom on wheel in preview
                if (ev.getDeltaY() > 0) zoomIn.run(); else zoomOut.run();
                ev.consume();
            }
        });

        // ── Toolbar ────────────────────────────────────────────────────────
        Button zoomInBtn  = toolButton("🔍+", "#21262d");
        Button zoomOutBtn = toolButton("🔍-", "#21262d");
        Button fitBtn     = toolButton("⊡ Fit", "#21262d");
        Button closeBtn   = toolButton("✕ Close", "#4a1a1a");

        zoomInBtn .setOnAction(e -> zoomIn.run());
        zoomOutBtn.setOnAction(e -> zoomOut.run());
        fitBtn    .setOnAction(e -> fitWin.run());
        closeBtn  .setOnAction(e -> dialog.close());

        HBox toolbar = new HBox(8, zoomInBtn, zoomOutBtn, fitBtn,
                new Region(), closeBtn);
        HBox.setHgrow(toolbar.getChildren().get(3), Priority.ALWAYS);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(8, 12, 8, 12));
        toolbar.setStyle("-fx-background-color:#161b22; -fx-border-color:#30363d;"
                + "-fx-border-width:0 0 1 0;");

        VBox root = new VBox(toolbar, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.setStyle("-fx-background-color:#0d1117;");

        javafx.scene.Scene scene = new javafx.scene.Scene(root,
                Math.min(img.getWidth() + 40, 960),
                Math.min(img.getHeight() + 80, 720));
        scene.setFill(Color.web("#0d1117"));

        // Keyboard shortcuts in the preview window
        scene.setOnKeyPressed(ev -> {
            switch (ev.getCode()) {
                case EQUALS, PLUS  -> zoomIn.run();
                case MINUS         -> zoomOut.run();
                case F             -> fitWin.run();
                case ESCAPE        -> dialog.close();
                default            -> {}
            }
        });

        dialog.setScene(scene);

        // Add a drop shadow effect to the image
        bigView.setEffect(new DropShadow(12, Color.web("#000000aa")));

        // After the dialog is shown, fit the image properly
        dialog.setOnShown(ev -> fitWin.run());
        dialog.show();
    }

    /** Helper to create a styled toolbar button for the preview dialog. */
    private static Button toolButton(String text, String bgColor) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color:" + bgColor + "; -fx-text-fill:#e6edf3;"
                + "-fx-border-color:#30363d; -fx-border-radius:4; -fx-background-radius:4;"
                + "-fx-padding:4 10; -fx-cursor:hand; -fx-font-size:12px;");
        btn.setOnMouseEntered(e -> btn.setStyle(btn.getStyle()
                .replace(bgColor, adjustBrightness(bgColor))));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color:" + bgColor
                + "; -fx-text-fill:#e6edf3;"
                + "-fx-border-color:#30363d; -fx-border-radius:4; -fx-background-radius:4;"
                + "-fx-padding:4 10; -fx-cursor:hand; -fx-font-size:12px;"));
        return btn;
    }

    private static String adjustBrightness(String hex) {
        // Lighten the colour slightly for hover feedback
        try {
            Color c = Color.web(hex);
            return String.format("#%02x%02x%02x",
                    (int) Math.min(255, c.getRed()   * 255 + 30),
                    (int) Math.min(255, c.getGreen() * 255 + 30),
                    (int) Math.min(255, c.getBlue()  * 255 + 30));
        } catch (Exception ex) {
            return hex;
        }
    }

    // ── Broker Import ─────────────────────────────────────────

    @FXML public void onImportBroker() {
        if (currentProfile == null) {
            setImportStatus("⚠ Select a profile first", false);
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Broker Trade History");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv", "*.CSV"));
        File file = chooser.showOpenDialog(
                symbolField.getScene() != null ? symbolField.getScene().getWindow() : null);
        if (file == null) return;

        setImportStatus("⏳ Parsing " + file.getName() + "…", true);
        Thread.ofVirtual().start(() -> {
            try {
                ImportResult result = brokerImportService.importCsv(file, currentProfile);
                List<Trade> saved   = new java.util.ArrayList<>();
                for (Trade t : result.trades()) {
                    saved.add(tradeService.saveTrade(t));
                }
                Platform.runLater(() -> {
                    String msg = String.format(
                            "✔ %s: imported %d/%d trades from %s",
                            result.broker().label, saved.size(),
                            result.totalRows(), file.getName());
                    if (!result.skippedRows().isEmpty()) {
                        msg += " (" + result.skippedRows().size() + " skipped)";
                    }
                    setImportStatus(msg, true);
                    // Notify parent to refresh trade journal
                    if (onSaveCallback != null) onSaveCallback.accept(null);
                    // Auto-clear status after 6 s
                    PauseTransition clear = new PauseTransition(Duration.seconds(6));
                    final String finalMsg = msg;
                    clear.setOnFinished(e -> {
                        if (importStatusLabel != null &&
                                finalMsg.equals(importStatusLabel.getText())) {
                            importStatusLabel.setText("");
                        }
                    });
                    clear.play();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> setImportStatus(
                        "⚠ Import failed: " + ex.getMessage(), false));
            }
        });
    }

    private void setImportStatus(String text, boolean ok) {
        if (importStatusLabel == null) return;
        importStatusLabel.setText(text);
        importStatusLabel.setStyle(ok
                ? "-fx-text-fill:#3fb950; -fx-font-size:11px;"
                : "-fx-text-fill:#f85149; -fx-font-size:11px;");
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

    /** Pre-fill the form from a Long/Short position chart drawing. */
    public void initFromDrawing(TradeDrawingDraft draft) {
        if (draft == null) return;
        editingTrade = null;
        clearForm();
        if (formTitleLabel != null) formTitleLabel.setText("📋 New Trade from Chart");
        symbolField.setText(draft.symbol());
        isLong = draft.direction() == TradeDirection.LONG;
        styleDirectionButtons();
        entryPriceField.setText(draft.entryPrice().toPlainString());
        if (draft.stopLoss() != null)
            stopLossField.setText(draft.stopLoss().toPlainString());
        if (draft.takeProfit() != null)
            takeProfitField.setText(draft.takeProfit().toPlainString());
        if (draft.assetType() != null)
            assetTypeCombo.setValue(draft.assetType());
        quantityField.setText("1");
        // Attach auto-captured screenshot if available
        if (draft.screenshotPath() != null) {
            setScreenshotPath(draft.screenshotPath());
        }
        updatePnlPreview();
    }

    /** One-click save from chart position drawing (qty=1, OPEN status). */
    public void instantSaveFromDrawing(TradeDrawingDraft draft) {
        if (draft == null || currentProfile == null) return;
        try {
            Trade trade = new Trade();
            trade.setProfile(currentProfile);
            trade.setSymbol(draft.symbol());
            trade.setAssetName(draft.symbol());
            trade.setAssetType(draft.assetType() != null ? draft.assetType() : AssetType.CRYPTO);
            trade.setDirection(draft.direction());
            trade.setEntryPrice(draft.entryPrice());
            trade.setQuantity(BigDecimal.ONE);
            trade.setStatus(TradeStatus.OPEN);
            trade.setEntryTime(LocalDateTime.now());
            if (draft.stopLoss()   != null) trade.setStopLoss(draft.stopLoss());
            if (draft.takeProfit() != null) trade.setTakeProfit(draft.takeProfit());
            // Persist the auto-captured screenshot path
            if (draft.screenshotPath() != null) trade.setScreenshotPath(draft.screenshotPath());
            Trade saved = tradeService.saveTrade(trade);
            if (onSaveCallback != null) onSaveCallback.accept(saved);
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR,
                    "Instant save failed: " + e.getMessage()).showAndWait();
        }
    }

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
        if (leverageField != null) {
            if (t.getLeverage() != null && t.getLeverage().compareTo(BigDecimal.ONE) > 0)
                leverageField.setText(t.getLeverage().toPlainString());
            else
                leverageField.clear();
        }
        updateLeverageLabel();
        entryDatePicker.setValue(t.getEntryTime().toLocalDate());
        entryTimeField.setText(t.getEntryTime()
                .format(DateTimeFormatter.ofPattern("HH:mm")));
        notesArea.setText(t.getNotes() != null ? t.getNotes() : "");
        isLong = t.getDirection() == TradeDirection.LONG;
        styleDirectionButtons();
        // Restore screenshot if the trade has one
        setScreenshotPath(t.getScreenshotPath());
        updatePnlPreview();
    }
}
