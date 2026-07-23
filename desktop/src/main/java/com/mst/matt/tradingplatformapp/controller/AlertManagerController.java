package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.client.AlertApiClient;
import com.mst.matt.tradingplatformapp.client.AlertApiClient.AlertResponse;
import com.mst.matt.tradingplatformapp.client.AlertApiClient.CreateAlertRequest;
import com.mst.matt.tradingplatformapp.client.AlertApiClient.UpdateAlertRequest;
import com.mst.matt.tradingplatformapp.model.UserProfile;
import com.mst.matt.tradingplatformapp.model.UserProfile.ProfileAssetFocus;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import net.rgielen.fxweaver.core.FxmlView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URL;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Alert manager — view, create, toggle, and delete price alerts.
 *
 * <h3>Phase 2, Step 12 — trading domain</h3>
 * <p>Alerts are now fetched/created/updated/deleted through {@link AlertApiClient}
 * ({@code /api/alerts/**} on the Gateway → {@code alert-service}) instead of the
 * local {@code AlertService} (JPA) + {@code NotificationService}:</p>
 * <ul>
 *   <li>The table now displays {@link AlertResponse} rows — alerts are scoped
 *       to the logged-in user (via the JWT {@code X-User-Id} header at the
 *       Gateway), not to the local desktop {@code UserProfile}.</li>
 *   <li>{@code alert-service} does not persist per-alert notification-channel
 *       flags (email/Telegram/desktop dispatch is owned solely by
 *       {@code notification-service}); the channel checkboxes remain in the
 *       UI for the user's intent but are not sent to the server.</li>
 *   <li>Symbol autocomplete backed by the local {@code SymbolEntryRepository}
 *       has been removed — {@code newSymbolField} falls back to a plain
 *       {@link TextField}.</li>
 * </ul>
 */
@Component
@FxmlView("/fxml/AlertManagerView.fxml")
public class AlertManagerController implements Initializable {

    @FXML private TableView<AlertResponse>           alertsTable;
    @FXML private TableColumn<AlertResponse,String>  colSymbol, colType,
            colTarget, colStatus,
            colNotify, colTriggered;
    @FXML private TableColumn<AlertResponse,Void>    colActions;

    @FXML private TextField   newSymbolField;
    @FXML private ComboBox<AlertConditionOption> alertTypeCombo;
    @FXML private TextField   targetPriceField;
    @FXML private CheckBox    emailCheck, telegramCheck, desktopCheck, repeatingCheck;
    @FXML private TextField   customMessageField;
    @FXML private Label       channelStatusLabel;

    @Autowired private AlertApiClient alertApiClient;

    private UserProfile activeProfile;
    private static final DateTimeFormatter DTF =
            DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.systemDefault());

    /** Friendly wrapper around {@code alert-service}'s {@code AlertCondition} enum values. */
    private enum AlertConditionOption {
        ABOVE("Price Above"),
        BELOW("Price Below"),
        PERCENT_CHANGE("24h % Change");

        final String label;
        AlertConditionOption(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        alertTypeCombo.getItems().setAll(AlertConditionOption.values());
        alertTypeCombo.setValue(AlertConditionOption.ABOVE);
        setupTable();
    }

    public void setProfile(UserProfile profile) {
        this.activeProfile = profile;
        refreshTable();
    }

    private void setupTable() {
        colSymbol.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getSymbol()));
        colType.setCellValueFactory(c ->
                new SimpleStringProperty(conditionLabel(c.getValue().getCondition())));
        colTarget.setCellValueFactory(c -> {
            AlertResponse a = c.getValue();
            if (a.getTargetValue() == null) return new SimpleStringProperty("—");
            boolean isPercent = "PERCENT_CHANGE".equals(a.getCondition());
            return new SimpleStringProperty(isPercent
                    ? a.getTargetValue() + "%"
                    : "$" + a.getTargetValue());
        });
        colStatus.setCellValueFactory(c ->
                new SimpleStringProperty(statusLabel(c.getValue().getStatus())));
        colStatus.setCellFactory(col -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setStyle(item != null && item.contains("Active")
                        ? "-fx-text-fill:#3fb950;" : "-fx-text-fill:#484f58;");
            }
        });
        // Repeated from AlertResponse.repeating/cooldownSeconds — alert-service has no
        // per-alert notification-channel fields (email/Telegram dispatch lives solely in
        // notification-service), so this column shows the re-arm behaviour instead.
        colNotify.setCellValueFactory(c -> {
            AlertResponse a = c.getValue();
            return new SimpleStringProperty(a.isRepeating()
                    ? "↩ " + Math.max(1, a.getCooldownSeconds() / 60) + "m"
                    : "—");
        });
        colTriggered.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getLastTriggeredAt() != null
                        ? DTF.format(c.getValue().getLastTriggeredAt()) : "—"));

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
                    int idx = getIndex();
                    if (getTableView() == null) return;
                    var items = getTableView().getItems();
                    if (items == null || idx < 0 || idx >= items.size()) return;
                    AlertResponse a = items.get(idx);
                    if (a == null || a.getId() == null) return;
                    toggleAlert(a);
                });
                deleteBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (getTableView() == null) return;
                    var items = getTableView().getItems();
                    if (items == null || idx < 0 || idx >= items.size()) return;
                    AlertResponse a = items.get(idx);
                    if (a == null || a.getId() == null) return;
                    deleteAlert(a);
                });
            }
            @Override protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : box);
            }
        });
    }

    private String conditionLabel(String condition) {
        if (condition == null) return "—";
        try {
            return AlertConditionOption.valueOf(condition).label;
        } catch (IllegalArgumentException e) {
            return condition;
        }
    }

    private String statusLabel(String status) {
        if (status == null) return "⚫ Unknown";
        return switch (status) {
            case "ACTIVE"    -> "🟢 Active";
            case "TRIGGERED" -> "🔔 Triggered";
            case "PAUSED"    -> "⏸ Paused";
            case "CANCELLED" -> "⚫ Cancelled";
            default          -> status;
        };
    }

    private void toggleAlert(AlertResponse a) {
        try {
            UpdateAlertRequest req = new UpdateAlertRequest();
            req.setStatus("ACTIVE".equals(a.getStatus()) ? "PAUSED" : "ACTIVE");
            alertApiClient.updateAlert(a.getId(), req);
            refreshTable();
        } catch (Exception ex) {
            showError("Failed to update alert: " + ex.getMessage());
        }
    }

    private void deleteAlert(AlertResponse a) {
        try {
            alertApiClient.deleteAlert(a.getId());
            refreshTable();
        } catch (Exception ex) {
            showError("Failed to delete alert: " + ex.getMessage());
        }
    }

    @FXML public void onAddAlert() {
        String sym = newSymbolField.getText().trim().toUpperCase();

        if (activeProfile == null) {
            new Alert(Alert.AlertType.WARNING,
                    "No trading profile selected. Choose a profile first.").showAndWait();
            return;
        }
        if (sym.isEmpty()) {
            new Alert(Alert.AlertType.WARNING, "Symbol is required.").showAndWait();
            return;
        }

        AlertConditionOption selectedCondition = alertTypeCombo.getValue();
        if (selectedCondition == null) {
            new Alert(Alert.AlertType.WARNING, "Alert condition is required.").showAndWait();
            return;
        }

        String targetText = targetPriceField.getText().trim();
        if (targetText.isEmpty()) {
            new Alert(Alert.AlertType.WARNING,
                    "Target price / percentage is required.").showAndWait();
            return;
        }

        BigDecimal target;
        try {
            target = new BigDecimal(targetText).abs();
        } catch (NumberFormatException e) {
            new Alert(Alert.AlertType.ERROR, "Invalid target: " + targetText).showAndWait();
            return;
        }

        CreateAlertRequest req = new CreateAlertRequest();
        req.setSymbol(sym);
        req.setAssetClass(resolveAssetClass());
        req.setCondition(selectedCondition.name());
        req.setTargetValue(target);
        req.setMessage(customMessageField.getText().trim());
        req.setRepeating(repeatingCheck != null && repeatingCheck.isSelected());
        // alert-service currently ignores these (see CreateAlertRequest javadoc) —
        // collected so the user's intent isn't silently dropped once the server
        // contract adds per-alert channel support.
        req.setNotifyEmail(emailCheck    != null && emailCheck.isSelected());
        req.setNotifyTelegram(telegramCheck != null && telegramCheck.isSelected());
        req.setNotifyDesktop(desktopCheck  != null && desktopCheck.isSelected());

        try {
            alertApiClient.createAlert(req);
            refreshTable();
            // Clear form
            newSymbolField.clear();
            targetPriceField.clear();
            customMessageField.clear();
        } catch (Exception ex) {
            showError("Failed to create alert: " + ex.getMessage());
        }
    }

    /** Maps the active desktop profile's asset focus to alert-service's AssetClass. */
    private String resolveAssetClass() {
        if (activeProfile == null) return "CRYPTO";
        ProfileAssetFocus focus = activeProfile.getAssetFocus();
        return switch (focus) {
            case CRYPTO -> "CRYPTO";
            case STOCK  -> "STOCK";
            case FOREX  -> "FOREX";
            case MULTI  -> "CRYPTO";
        };
    }

    private void refreshTable() {
        // Fetch on a background thread so the FX thread is never blocked by the
        // blocking WebClient call in AlertApiClient.
        Thread.ofVirtual().start(() -> {
            try {
                List<AlertResponse> alerts = alertApiClient.listAlerts();
                Platform.runLater(() -> {
                    if (alertsTable != null) {
                        alertsTable.getItems().setAll(alerts);
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> showError("Failed to refresh alerts: " + ex.getMessage()));
            }
        });
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            if (channelStatusLabel != null) {
                channelStatusLabel.setText("⚠ " + message);
                channelStatusLabel.setStyle("-fx-text-fill:#f85149; -fx-font-size:11px;"
                        + "-fx-wrap-text:true;");
                channelStatusLabel.setVisible(true);
                channelStatusLabel.setManaged(true);
            } else {
                new Alert(Alert.AlertType.ERROR, message).showAndWait();
            }
        });
    }

}
