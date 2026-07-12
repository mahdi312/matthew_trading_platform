package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.model.*;
import com.mst.matt.tradingplatformapp.model.PriceAlert.*;
import com.mst.matt.tradingplatformapp.repository.SymbolEntryRepository;
import com.mst.matt.tradingplatformapp.service.alert.AlertService;
import com.mst.matt.tradingplatformapp.service.alert.NotificationService;
import com.mst.matt.tradingplatformapp.ui.AutocompleteSymbolField;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import net.rgielen.fxweaver.core.FxmlView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Alert manager — view, create, toggle, and delete price alerts.
 */
@Component
@FxmlView("/fxml/AlertManagerView.fxml")
public class AlertManagerController implements Initializable {

    @FXML private TableView<PriceAlert>           alertsTable;
    @FXML private TableColumn<PriceAlert,String>  colSymbol, colType,
            colTarget, colStatus,
            colNotify, colTriggered;
    @FXML private TableColumn<PriceAlert,Void>    colActions;

    @FXML private TextField   newSymbolField;
    @FXML private ComboBox<AlertType>    alertTypeCombo;
    @FXML private TextField   targetPriceField;
    @FXML private CheckBox    emailCheck, telegramCheck, desktopCheck, repeatingCheck;
    @FXML private TextField   customMessageField;
    @FXML private Label       channelStatusLabel;

    @Autowired private AlertService         alertService;
    @Autowired private NotificationService  notificationService;
    @Autowired private SymbolEntryRepository symbolEntryRepository;

    /** Replaces the plain newSymbolField with an autocomplete field at runtime. */
    private AutocompleteSymbolField symbolAutocomplete;

    private UserProfile activeProfile;
    private static final DateTimeFormatter DTF =
            DateTimeFormatter.ofPattern("MM/dd HH:mm");

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        alertTypeCombo.getItems().setAll(AlertType.values());
        alertTypeCombo.setValue(AlertType.PRICE_ABOVE);
        setupTable();
        setupSymbolAutocomplete();
        setupChannelCheckListeners();
    }

    /** Replace the plain newSymbolField with an AutocompleteSymbolField. */
    private void setupSymbolAutocomplete() {
        if (newSymbolField == null) return;
        javafx.scene.Parent parent = newSymbolField.getParent();
        if (!(parent instanceof javafx.scene.layout.HBox hbox)) return;

        int idx = hbox.getChildren().indexOf(newSymbolField);
        if (idx < 0) return;

        symbolAutocomplete = new AutocompleteSymbolField(symbolEntryRepository);
        symbolAutocomplete.setPrefWidth(140);
        symbolAutocomplete.setPromptText("Symbol");
        // Mirror text back to the FXML field (used in onAddAlert)
        symbolAutocomplete.textProperty().addListener((o, a, n) -> {
            if (newSymbolField != null) newSymbolField.setText(n);
        });
        hbox.getChildren().set(idx, symbolAutocomplete);
    }

    /** Wire channel checkbox listeners to show/clear warnings when the user
     *  toggles email or telegram. */
    private void setupChannelCheckListeners() {
        if (emailCheck != null) {
            emailCheck.selectedProperty().addListener((o, a, sel) -> updateChannelWarning());
        }
        if (telegramCheck != null) {
            telegramCheck.selectedProperty().addListener((o, a, sel) -> updateChannelWarning());
        }
        // Show initial state
        updateChannelWarning();
    }

    /** Update the channel status label based on current checkbox state. */
    private void updateChannelWarning() {
        boolean wantEmail    = emailCheck    != null && emailCheck.isSelected();
        boolean wantTelegram = telegramCheck != null && telegramCheck.isSelected();
        String warning = notificationService.buildChannelWarning(wantEmail, wantTelegram);
        if (channelStatusLabel != null) {
            if (warning != null) {
                channelStatusLabel.setText("⚠ " + warning);
                channelStatusLabel.setStyle("-fx-text-fill:#d29922; -fx-font-size:11px;"
                        + "-fx-wrap-text:true;");
                channelStatusLabel.setVisible(true);
                channelStatusLabel.setManaged(true);
            } else {
                channelStatusLabel.setText("");
                channelStatusLabel.setVisible(false);
                channelStatusLabel.setManaged(false);
            }
        }
    }

    public void setProfile(UserProfile profile) {
        this.activeProfile = profile;
        refreshTable();
    }

    private void setupTable() {
        colSymbol.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getSymbol()));
        colType.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getAlertType().name()));
        colTarget.setCellValueFactory(c -> {
            PriceAlert a = c.getValue();
            if (a.getTargetPrice() != null)
                return new SimpleStringProperty("$" + a.getTargetPrice());
            if (a.getPercentageThreshold() != null)
                return new SimpleStringProperty(a.getPercentageThreshold() + "%");
            return new SimpleStringProperty("—");
        });
        colStatus.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().isActive() ? "🟢 Active" : "⚫ Off"));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setStyle(item != null && item.contains("Active")
                        ? "-fx-text-fill:#3fb950;" : "-fx-text-fill:#484f58;");
            }
        });
        colNotify.setCellValueFactory(c -> {
            PriceAlert a = c.getValue();
            StringBuilder sb = new StringBuilder();
            if (a.isNotifyEmail())   sb.append("📧 ");
            if (a.isNotifyTelegram())sb.append("✈ ");
            if (a.isNotifyDesktop()) sb.append("🖥 ");
            return new SimpleStringProperty(sb.toString());
        });
        colTriggered.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getTriggeredAt() != null
                        ? c.getValue().getTriggeredAt().format(DTF) : "—"));

        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button toggleBtn = new Button("⏸");
            private final Button deleteBtn = new Button("🗑");
            private final javafx.scene.layout.HBox box =
                    new javafx.scene.layout.HBox(4, toggleBtn, deleteBtn);
            {
                toggleBtn.setStyle("-fx-background-color:#1f6feb; -fx-text-fill:white;"
                        + "-fx-background-radius:4; -fx-cursor:hand;");
                deleteBtn.setStyle("-fx-background-color:#da3633; -fx-text-fill:white;"
                        + "-fx-background-radius:4; -fx-cursor:hand;");
                toggleBtn.setOnAction(e -> {
                    // Fix #3: guard against stale index after list mutations
                    int idx = getIndex();
                    if (getTableView() == null) return;
                    var items = getTableView().getItems();
                    if (items == null || idx < 0 || idx >= items.size()) return;
                    PriceAlert a = items.get(idx);
                    if (a == null || a.getId() == null) return;
                    alertService.toggleAlert(a.getId(), !a.isActive());
                    refreshTable();
                });
                deleteBtn.setOnAction(e -> {
                    // Fix #3: guard against stale index after list mutations
                    int idx = getIndex();
                    if (getTableView() == null) return;
                    var items = getTableView().getItems();
                    if (items == null || idx < 0 || idx >= items.size()) return;
                    PriceAlert a = items.get(idx);
                    if (a == null || a.getId() == null) return;
                    alertService.deleteAlert(a.getId());
                    refreshTable();
                });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    @FXML public void onAddAlert() {
        // Read symbol from autocomplete field if present, else from original field
        String sym = symbolAutocomplete != null
                ? symbolAutocomplete.getText().trim().toUpperCase()
                : newSymbolField.getText().trim().toUpperCase();

        if (activeProfile == null) {
            new Alert(Alert.AlertType.WARNING,
                    "No trading profile selected. Choose a profile first.").showAndWait();
            return;
        }
        if (sym.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Symbol is required.").showAndWait();
            return;
        }

        AlertType selectedType = alertTypeCombo.getValue();
        if (selectedType == null) {
            new Alert(Alert.AlertType.WARNING, "Alert type is required.").showAndWait();
            return;
        }

        boolean wantEmail    = emailCheck    != null && emailCheck.isSelected();
        boolean wantTelegram = telegramCheck != null && telegramCheck.isSelected();

        // ── Pre-approval: check notification channels are configured ─────────
        String channelWarning = notificationService.buildChannelWarning(wantEmail, wantTelegram);
        if (channelWarning != null) {
            // Show a confirmation dialog — user can continue with unconfigured channels disabled
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Notification Channel Warning");
            confirm.setHeaderText("Some notification channels are not configured:");
            confirm.setContentText(channelWarning
                    + "\n\nThe alert will be saved, but unconfigured channels will be skipped "
                    + "when the alert fires.\n\nContinue?");
            confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
            var result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) return;

            // Disable channels that are not configured
            if (wantEmail    && !notificationService.isEmailConfigured())    wantEmail    = false;
            if (wantTelegram && !notificationService.isTelegramConfigured()) wantTelegram = false;
        }

        PriceAlertBuilder builder = PriceAlert.builder()
                .profile(activeProfile)
                .symbol(sym)
                .alertType(selectedType)
                .active(true)
                .notifyEmail(wantEmail)
                .notifyTelegram(wantTelegram)
                .notifyDesktop(desktopCheck != null && desktopCheck.isSelected())
                .repeating(repeatingCheck   != null && repeatingCheck.isSelected())
                .triggered(false)
                .customMessage(customMessageField.getText().trim());

        String targetText = targetPriceField.getText().trim();
        if (targetText.isEmpty() && requiresThreshold(selectedType)) {
            new Alert(Alert.AlertType.WARNING,
                    "Target price / percentage is required for " + selectedType + ".")
                    .showAndWait();
            return;
        }
        if (!targetText.isEmpty()) {
            try {
                BigDecimal target = new BigDecimal(targetText);
                if (selectedType == AlertType.PCT_CHANGE_24H)
                    builder.percentageThreshold(target.abs());
                else
                    builder.targetPrice(target);
            } catch (NumberFormatException e) {
                new Alert(Alert.AlertType.ERROR,
                        "Invalid target: " + targetText).showAndWait();
                return;
            }
        }

        alertService.createAlert(builder.build());
        refreshTable();
        // Clear form
        if (symbolAutocomplete != null) symbolAutocomplete.clear();
        newSymbolField.clear();
        targetPriceField.clear();
        customMessageField.clear();
        // Clear any channel warning
        updateChannelWarning();
    }

    private boolean requiresThreshold(AlertType type) {
        return switch (type) {
            case PRICE_ABOVE, PRICE_BELOW, PCT_CHANGE_24H,
                    FIBONACCI_LEVEL_TOUCH, VOLUME_SPIKE -> true;
            case INDICATOR_BUY_SIGNAL, INDICATOR_SELL_SIGNAL -> false;
        };
    }

    private void refreshTable() {
        if (activeProfile == null) return;
        // Fetch on a background thread so the FX thread is never blocked by DB I/O.
        // The result is applied back on the FX thread via Platform.runLater, ensuring
        // no Hibernate session is open when the TableView accesses the list items.
        Thread.ofVirtual().start(() -> {
            try {
                List<PriceAlert> alerts = alertService.getAlertsForProfile(activeProfile);
                javafx.application.Platform.runLater(() -> {
                    if (alertsTable != null) {
                        alertsTable.getItems().setAll(alerts);
                    }
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() ->
                        new javafx.scene.control.Alert(
                                javafx.scene.control.Alert.AlertType.ERROR,
                                "Failed to refresh alerts: " + ex.getMessage())
                                .show());
            }
        });
    }
}
