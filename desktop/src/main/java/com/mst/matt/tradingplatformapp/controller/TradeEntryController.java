package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.client.TradeApiClient;
import com.mst.matt.tradingplatformapp.client.TradeApiClient.TradeRequest;
import com.mst.matt.tradingplatformapp.client.TradeApiClient.TradeResponse;
import com.mst.matt.tradingplatformapp.model.*;
import com.mst.matt.tradingplatformapp.model.Trade.*;
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
 *
 * <h3>Phase 2, Step 12 — trading domain</h3>
 * <p>All trade persistence calls are now routed through {@link TradeApiClient}
 * which hits {@code /api/trades/**} on the Gateway → {@code trading-service}.
 * The old {@code TradeService} (JPA), {@code BrokerImportService}, and
 * {@code PriceRouter} injections have been replaced:</p>
 * <ul>
 *   <li>{@code TradeService.saveTrade()} → {@link TradeApiClient#saveTrade}</li>
 *   <li>{@code TradeService.updateTrade()} → {@link TradeApiClient#updateTrade}</li>
 *   <li>Broker CSV import → deferred to later step (UI button kept, disabled)</li>
 *   <li>Price fetch → still uses {@link PriceRouter} (local; migrated later)</li>
 * </ul>
 * <p>All FXML bindings and UI logic are unchanged from the pre-refactor version.</p>
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
    @FXML private Label     slPctLabel;
    @FXML private TextField takeProfitField;
    @FXML private Label     tpPctLabel;
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
    /** Replaces old TradeService (JPA) — hits trading-service via gateway. */
    @Autowired private TradeApiClient  tradeApiClient;
    /** Still used for live price fetch; will be migrated in the market domain step. */
    @Autowired private PriceRouter     priceRouter;

    private UserProfile currentProfile;
    /** Server-assigned id of the trade being edited, or null for a new trade. */
    private Long        editingTradeId;
    private boolean     isLong = true;
    private Consumer<TradeResponse> onSaveCallback;

    /** Currently attached screenshot path (absolute file path), or {@code null} if none. */
    private String currentScreenshotPath;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        assetTypeCombo.getItems().setAll(AssetType.values());
        assetTypeCombo.setValue(AssetType.CRYPTO);
        styleComboBox(assetTypeCombo);

        entryDatePicker.setValue(LocalDate.now());
        entryTimeField.setText(LocalTime.now()
                .format(DateTimeFormatter.ofPattern("HH:mm")));
        styleDatePicker(entryDatePicker);
        styleDatePicker(exitDatePicker);

        isLong = true;
        styleDirectionButtons();
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
                combo.setStyle("-fx-background-color:#0d1117; -fx-text-fill:#e6edf3;"
                        + "-fx-border-color:#30363d; -fx-border-radius:6;"
                        + "-fx-background-radius:6;");
            }
        });
    }

    private void styleDatePicker(DatePicker dp) {
        if (dp == null) return;
        dp.setStyle("-fx-background-color:#0d1117; -fx-text-fill:#e6edf3;"
                + "-fx-border-color:#30363d; -fx-border-radius:6;"
                + "-fx-background-radius:6;");
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

    private void updateSlTpPctLabels(BigDecimal entry, BigDecimal sl, BigDecimal tp) {
        if (entry == null || entry.compareTo(BigDecimal.ZERO) <= 0) {
            if (slPctLabel != null) slPctLabel.setText("");
            if (tpPctLabel != null) tpPctLabel.setText("");
            return;
        }
        if (slPctLabel != null) {
            if (sl != null && sl.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal slPct = isLong
                        ? sl.subtract(entry).divide(entry, 6, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                        : entry.subtract(sl).divide(entry, 6, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100)).negate();
                String sign = slPct.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                slPctLabel.setText("SL: " + sign + format(slPct) + "%");
                boolean wrongSide = slPct.compareTo(BigDecimal.ZERO) > 0;
                slPctLabel.setStyle("-fx-font-size:11px; -fx-font-weight:bold; -fx-text-fill:"
                        + (wrongSide ? "#d29922;" : "#f85149;"));
            } else {
                slPctLabel.setText("");
            }
        }
        if (tpPctLabel != null) {
            if (tp != null && tp.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal tpPct = isLong
                        ? tp.subtract(entry).divide(entry, 6, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                        : entry.subtract(tp).divide(entry, 6, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100));
                String sign = tpPct.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
                tpPctLabel.setText("TP: " + sign + format(tpPct) + "%");
                boolean wrongSide = tpPct.compareTo(BigDecimal.ZERO) < 0;
                tpPctLabel.setStyle("-fx-font-size:11px; -fx-font-weight:bold; -fx-text-fill:"
                        + (wrongSide ? "#d29922;" : "#3fb950;"));
            } else {
                tpPctLabel.setText("");
            }
        }
    }

    private void updatePnlPreview() {
        try {
            BigDecimal entry = parseBD(entryPriceField.getText());
            BigDecimal exit  = parseBD(exitPriceField.getText());
            BigDecimal qty   = parseBD(quantityField.getText());
            BigDecimal fee   = parseBD(feeField.getText());
            BigDecimal sl    = parseBD(stopLossField.getText());
            BigDecimal tp    = parseBD(takeProfitField.getText());
            BigDecimal lev   = parseLeverage();

            if (entry.compareTo(BigDecimal.ZERO) <= 0
                    || qty.compareTo(BigDecimal.ZERO) <= 0) return;

            BigDecimal invested = entry.multiply(qty);
            investedLabel.setText("$" + format(invested));

            if (exit.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal diff = isLong ? exit.subtract(entry) : entry.subtract(exit);
                BigDecimal pnl  = diff.multiply(qty).subtract(fee);
                BigDecimal pct  = diff.divide(entry, 6, RoundingMode.HALF_UP)
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

            updateSlTpPctLabels(entry, sl, tp);

            if (sl.compareTo(BigDecimal.ZERO) > 0 && tp.compareTo(BigDecimal.ZERO) > 0) {
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
            TradeRequest req = buildRequest();

            TradeResponse saved;
            if (editingTradeId != null) {
                saved = tradeApiClient.updateTrade(editingTradeId, req);
            } else {
                saved = tradeApiClient.saveTrade(req);
            }

            if (onSaveCallback != null) onSaveCallback.accept(saved);

            symbolValidation.setText("✅ Trade saved!");
            symbolValidation.setStyle("-fx-text-fill: #3fb950;");

            if (editingTradeId == null) clearForm();

        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR,
                    "Failed to save trade: " + e.getMessage()).showAndWait();
        }
    }

    @FXML public void onCancel() {
        if (onSaveCallback != null) onSaveCallback.accept(null);
        clearForm();
    }

    /** Builds a {@link TradeRequest} from the current form state. */
    private TradeRequest buildRequest() {
        TradeRequest req = new TradeRequest();
        if (currentProfile != null) req.setProfileId(currentProfile.getId());

        String sym = symbolField.getText().trim().toUpperCase();
        req.setSymbol(sym);
        req.setAssetName(sym);
        req.setAssetType(assetTypeCombo.getValue() != null
                ? assetTypeCombo.getValue().name() : "CRYPTO");
        req.setDirection(isLong ? "LONG" : "SHORT");
        req.setEntryPrice(parseBD(entryPriceField.getText()));
        req.setQuantity(parseBD(quantityField.getText()));
        req.setExchange(exchangeField.getText().trim());
        req.setStrategy(strategyField.getText().trim());
        req.setNotes(notesArea.getText().trim());

        String exitText = exitPriceField.getText().trim();
        if (!exitText.isEmpty()) {
            req.setExitPrice(parseBD(exitText));
            req.setStatus("CLOSED");
            req.setExitTime(exitDatePicker.getValue() != null
                    ? exitDatePicker.getValue().atTime(LocalTime.now())
                    : LocalDateTime.now());
        } else {
            req.setStatus("OPEN");
        }

        if (!stopLossField.getText().isBlank())   req.setStopLoss(parseBD(stopLossField.getText()));
        if (!takeProfitField.getText().isBlank())  req.setTakeProfit(parseBD(takeProfitField.getText()));
        if (!feeField.getText().isBlank())         req.setFee(parseBD(feeField.getText()));
        if (leverageField != null && !leverageField.getText().isBlank()) {
            BigDecimal lev = parseLeverage();
            if (lev.compareTo(BigDecimal.ONE) > 0) req.setLeverage(lev);
        }

        LocalDate entryDate = entryDatePicker.getValue();
        LocalTime entryTime = parseTime(entryTimeField.getText());
        req.setEntryTime(LocalDateTime.of(
                entryDate != null ? entryDate : LocalDate.now(), entryTime));

        if (currentScreenshotPath != null && !currentScreenshotPath.isBlank())
            req.setScreenshotPath(currentScreenshotPath);

        return req;
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
        if (assetTypeCombo.getValue() == null) valid = false;
        if (entryDatePicker.getValue() == null) valid = false;
        return valid;
    }

    private BigDecimal parseBD(String s) {
        if (s == null || s.isBlank()) return BigDecimal.ZERO;
        try { return new BigDecimal(s.trim()); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private LocalTime parseTime(String s) {
        try { return LocalTime.parse(s.trim(), DateTimeFormatter.ofPattern("HH:mm")); }
        catch (Exception e) { return LocalTime.now(); }
    }

    private String format(BigDecimal bd) {
        return bd.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private void clearForm() {
        symbolField.clear(); entryPriceField.clear();
        exitPriceField.clear(); quantityField.clear();
        stopLossField.clear(); takeProfitField.clear();
        if (slPctLabel  != null) slPctLabel.setText("");
        if (tpPctLabel  != null) tpPctLabel.setText("");
        feeField.clear(); notesArea.clear();
        strategyField.clear(); exchangeField.clear();
        if (leverageField != null) leverageField.clear();
        symbolValidation.setText("");
        symbolValidation.setStyle("-fx-font-size:11px;");
        currentPriceLabel.setText("Current: —");
        currentPriceLabel.setStyle("-fx-text-fill:#388bfd; -fx-font-size:11px;");
        entryDatePicker.setValue(LocalDate.now());
        entryTimeField.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));
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
        editingTradeId = null;
        if (formTitleLabel != null) formTitleLabel.setText("📋 New Trade Entry");
        styleDirectionButtons();
        styleNotesArea();
        styleDatePicker(entryDatePicker);
        styleDatePicker(exitDatePicker);
        currentScreenshotPath = null;
        refreshScreenshotDisplay();
    }

    // ── Screenshot field ─────────────────────────────────────────────────────

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
                    if (screenshotPlaceholderLabel != null) screenshotPlaceholderLabel.setVisible(false);
                    if (screenshotPathLabel != null)
                        screenshotPathLabel.setText("📄 " + f.getName() + "  🔍 Click to enlarge");
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
        screenshotImageView.setImage(null);
        screenshotImageView.setVisible(false);
        screenshotImageView.setOnMouseClicked(null);
        screenshotImageView.setStyle("");
        if (screenshotPane != null) {
            screenshotPane.setOnMouseClicked(null);
            screenshotPane.setStyle("");
        }
        if (screenshotPlaceholderLabel != null) screenshotPlaceholderLabel.setVisible(true);
        if (screenshotPathLabel != null) screenshotPathLabel.setText("");
    }

    private void openScreenshotPreview() {
        if (currentScreenshotPath == null) return;
        File f = new File(currentScreenshotPath);
        if (!f.exists()) return;
        Image img;
        try { img = new Image(f.toURI().toString()); } catch (Exception ex) { return; }

        Stage dialog = new Stage(StageStyle.DECORATED);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("📸 Screenshot – " + f.getName());
        dialog.setResizable(true);

        ImageView bigView = new ImageView(img);
        bigView.setPreserveRatio(true);
        bigView.setSmooth(true);
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

        final double[] scale = {1.0};
        Runnable applyZoom = () -> { bigView.setFitWidth(img.getWidth() * scale[0]); bigView.setFitHeight(img.getHeight() * scale[0]); };
        Runnable zoomIn  = () -> { scale[0] = Math.min(scale[0] * 1.2, 8.0); applyZoom.run(); };
        Runnable zoomOut = () -> { scale[0] = Math.max(scale[0] / 1.2, 0.1); applyZoom.run(); };
        Runnable fitWin  = () -> {
            double ww = dialog.getScene() != null ? dialog.getScene().getWidth()  - 40 : 800;
            double wh = dialog.getScene() != null ? dialog.getScene().getHeight() - 80 : 560;
            scale[0] = Math.max(0.05, Math.min(ww / img.getWidth(), wh / img.getHeight()));
            applyZoom.run();
        };
        scroll.addEventFilter(ScrollEvent.SCROLL, ev -> { if (ev.getDeltaY() > 0) zoomIn.run(); else zoomOut.run(); ev.consume(); });

        Button zoomInBtn  = toolButton("🔍+", "#21262d");
        Button zoomOutBtn = toolButton("🔍-", "#21262d");
        Button fitBtn     = toolButton("⊡ Fit", "#21262d");
        Button closeBtn   = toolButton("✕ Close", "#4a1a1a");
        zoomInBtn.setOnAction(e -> zoomIn.run());
        zoomOutBtn.setOnAction(e -> zoomOut.run());
        fitBtn.setOnAction(e -> fitWin.run());
        closeBtn.setOnAction(e -> dialog.close());

        HBox toolbar = new HBox(8, zoomInBtn, zoomOutBtn, fitBtn, new Region(), closeBtn);
        HBox.setHgrow(toolbar.getChildren().get(3), Priority.ALWAYS);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(8, 12, 8, 12));
        toolbar.setStyle("-fx-background-color:#161b22; -fx-border-color:#30363d; -fx-border-width:0 0 1 0;");

        VBox root = new VBox(toolbar, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.setStyle("-fx-background-color:#0d1117;");

        javafx.scene.Scene scene = new javafx.scene.Scene(root,
                Math.min(img.getWidth() + 40, 960), Math.min(img.getHeight() + 80, 720));
        scene.setFill(Color.web("#0d1117"));
        scene.setOnKeyPressed(ev -> {
            switch (ev.getCode()) {
                case EQUALS, PLUS -> zoomIn.run();
                case MINUS        -> zoomOut.run();
                case F            -> fitWin.run();
                case ESCAPE       -> dialog.close();
                default           -> {}
            }
        });
        dialog.setScene(scene);
        bigView.setEffect(new DropShadow(12, Color.web("#000000aa")));
        dialog.setOnShown(ev -> fitWin.run());
        dialog.show();
    }

    private static Button toolButton(String text, String bgColor) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color:" + bgColor + "; -fx-text-fill:#e6edf3;"
                + "-fx-border-color:#30363d; -fx-border-radius:4; -fx-background-radius:4;"
                + "-fx-padding:4 10; -fx-cursor:hand; -fx-font-size:12px;");
        btn.setOnMouseEntered(e -> btn.setStyle(btn.getStyle().replace(bgColor, adjustBrightness(bgColor))));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color:" + bgColor + "; -fx-text-fill:#e6edf3;"
                + "-fx-border-color:#30363d; -fx-border-radius:4; -fx-background-radius:4;"
                + "-fx-padding:4 10; -fx-cursor:hand; -fx-font-size:12px;"));
        return btn;
    }

    private static String adjustBrightness(String hex) {
        try {
            Color c = Color.web(hex);
            return String.format("#%02x%02x%02x",
                    (int) Math.min(255, c.getRed()   * 255 + 30),
                    (int) Math.min(255, c.getGreen() * 255 + 30),
                    (int) Math.min(255, c.getBlue()  * 255 + 30));
        } catch (Exception ex) { return hex; }
    }

    // ── Broker Import (deferred — UI stub retained) ───────────────────────────

    /**
     * Broker CSV import is deferred to the trading-domain step when
     * {@code trading-service} exposes a {@code POST /api/trades/import} endpoint.
     * The button is shown disabled in the UI in the meantime.
     */
    @FXML public void onImportBroker() {
        if (importStatusLabel != null) {
            importStatusLabel.setText("ℹ Broker CSV import will be available once trading-service import endpoint is deployed.");
            importStatusLabel.setStyle("-fx-text-fill:#8b949e; -fx-font-size:11px;");
            PauseTransition clear = new PauseTransition(Duration.seconds(6));
            clear.setOnFinished(e -> { if (importStatusLabel != null) importStatusLabel.setText(""); });
            clear.play();
        }
    }

    private void setImportStatus(String text, boolean ok) {
        if (importStatusLabel == null) return;
        importStatusLabel.setText(text);
        importStatusLabel.setStyle(ok
                ? "-fx-text-fill:#3fb950; -fx-font-size:11px;"
                : "-fx-text-fill:#f85149; -fx-font-size:11px;");
    }

    // ── Public API for parent controllers ────────────────────

    public void setProfile(UserProfile profile) { this.currentProfile = profile; }

    /**
     * Sets the trade being edited.
     *
     * @param response non-null = edit mode; null = new trade mode
     */
    public void setEditingTrade(TradeResponse response) {
        if (response != null) {
            this.editingTradeId = response.getId();
            if (formTitleLabel != null) formTitleLabel.setText("✏ Edit Trade");
            populateForm(response);
        } else {
            this.editingTradeId = null;
            clearForm();
        }
    }

    /**
     * Overload kept for backward compatibility: callers that still pass a
     * legacy {@code Trade} model object (e.g., {@code MainDashboardController})
     * will use this until they are refactored in the next step.
     */
    public void setEditingTrade(Trade trade) {
        if (trade == null) {
            setEditingTrade((TradeResponse) null);
            return;
        }
        // Map legacy Trade model → TradeResponse DTO
        TradeResponse r = new TradeResponse();
        r.setId(trade.getId());
        r.setSymbol(trade.getSymbol());
        r.setAssetName(trade.getAssetName());
        r.setAssetType(trade.getAssetType() != null ? trade.getAssetType().name() : "CRYPTO");
        r.setDirection(trade.getDirection() != null ? trade.getDirection().name() : "LONG");
        r.setEntryPrice(trade.getEntryPrice());
        r.setExitPrice(trade.getExitPrice());
        r.setQuantity(trade.getQuantity());
        r.setStopLoss(trade.getStopLoss());
        r.setTakeProfit(trade.getTakeProfit());
        r.setFee(trade.getFee());
        r.setLeverage(trade.getLeverage());
        r.setExchange(trade.getExchange());
        r.setStrategy(trade.getStrategy());
        r.setNotes(trade.getNotes());
        r.setScreenshotPath(trade.getScreenshotPath());
        r.setStatus(trade.getStatus() != null ? trade.getStatus().name() : "OPEN");
        r.setEntryTime(trade.getEntryTime());
        r.setExitTime(trade.getExitTime());
        setEditingTrade(r);
    }

    public void setOnSaveCallback(Consumer<TradeResponse> cb) { this.onSaveCallback = cb; }

    /**
     * Overload for callers that still pass a {@code Consumer<Trade>}.
     * Wraps the callback to map TradeResponse back to a minimal Trade shell.
     */
    @SuppressWarnings("unchecked")
    public void setOnSaveLegacyCallback(Consumer<Trade> cb) {
        if (cb == null) { this.onSaveCallback = null; return; }
        this.onSaveCallback = resp -> {
            if (resp == null) { cb.accept(null); return; }
            Trade t = new Trade();
            t.setId(resp.getId());
            t.setSymbol(resp.getSymbol());
            cb.accept(t);
        };
    }

    /** Pre-fill the form from a Long/Short position chart drawing. */
    public void initFromDrawing(TradeDrawingDraft draft) {
        if (draft == null) return;
        editingTradeId = null;
        clearForm();
        if (formTitleLabel != null) formTitleLabel.setText("📋 New Trade from Chart");
        symbolField.setText(draft.symbol());
        isLong = draft.direction() == TradeDirection.LONG;
        styleDirectionButtons();
        entryPriceField.setText(draft.entryPrice().toPlainString());
        if (draft.stopLoss() != null)  stopLossField.setText(draft.stopLoss().toPlainString());
        if (draft.takeProfit() != null) takeProfitField.setText(draft.takeProfit().toPlainString());
        if (draft.assetType() != null) assetTypeCombo.setValue(draft.assetType());
        quantityField.setText("1");
        if (draft.screenshotPath() != null) setScreenshotPath(draft.screenshotPath());
        updatePnlPreview();
    }

    /** One-click save from chart position drawing (qty=1, OPEN status). */
    public void instantSaveFromDrawing(TradeDrawingDraft draft) {
        if (draft == null || currentProfile == null) return;
        try {
            TradeRequest req = new TradeRequest();
            req.setProfileId(currentProfile.getId());
            req.setSymbol(draft.symbol());
            req.setAssetName(draft.symbol());
            req.setAssetType(draft.assetType() != null ? draft.assetType().name() : "CRYPTO");
            req.setDirection(draft.direction() != null ? draft.direction().name() : "LONG");
            req.setEntryPrice(draft.entryPrice());
            req.setQuantity(BigDecimal.ONE);
            req.setStatus("OPEN");
            req.setEntryTime(LocalDateTime.now());
            if (draft.stopLoss()    != null) req.setStopLoss(draft.stopLoss());
            if (draft.takeProfit()  != null) req.setTakeProfit(draft.takeProfit());
            if (draft.screenshotPath() != null) req.setScreenshotPath(draft.screenshotPath());

            TradeResponse saved = tradeApiClient.saveTrade(req);
            if (onSaveCallback != null) onSaveCallback.accept(saved);
        } catch (Exception e) {
            new Alert(Alert.AlertType.ERROR,
                    "Instant save failed: " + e.getMessage()).showAndWait();
        }
    }

    private void populateForm(TradeResponse t) {
        clearForm();
        if (formTitleLabel != null) formTitleLabel.setText("✏ Edit Trade");
        symbolField.setText(t.getSymbol());
        try { assetTypeCombo.setValue(AssetType.valueOf(t.getAssetType())); }
        catch (Exception ignored) { assetTypeCombo.setValue(AssetType.CRYPTO); }
        exchangeField.setText(t.getExchange() != null ? t.getExchange() : "");
        strategyField.setText(t.getStrategy() != null ? t.getStrategy() : "");
        entryPriceField.setText(t.getEntryPrice() != null ? t.getEntryPrice().toPlainString() : "");
        if (t.getExitPrice() != null)
            exitPriceField.setText(t.getExitPrice().toPlainString());
        if (t.getExitTime() != null)
            exitDatePicker.setValue(t.getExitTime().toLocalDate());
        if (t.getQuantity() != null) quantityField.setText(t.getQuantity().toPlainString());
        if (t.getStopLoss() != null) stopLossField.setText(t.getStopLoss().toPlainString());
        if (t.getTakeProfit() != null) takeProfitField.setText(t.getTakeProfit().toPlainString());
        if (t.getFee() != null) feeField.setText(t.getFee().toPlainString());
        if (leverageField != null && t.getLeverage() != null
                && t.getLeverage().compareTo(BigDecimal.ONE) > 0)
            leverageField.setText(t.getLeverage().toPlainString());
        updateLeverageLabel();
        if (t.getEntryTime() != null) {
            entryDatePicker.setValue(t.getEntryTime().toLocalDate());
            entryTimeField.setText(t.getEntryTime().format(DateTimeFormatter.ofPattern("HH:mm")));
        }
        notesArea.setText(t.getNotes() != null ? t.getNotes() : "");
        isLong = !"SHORT".equalsIgnoreCase(t.getDirection());
        styleDirectionButtons();
        setScreenshotPath(t.getScreenshotPath());
        updatePnlPreview();
    }
}
