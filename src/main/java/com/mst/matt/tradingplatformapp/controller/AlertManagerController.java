package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.model.*;
import com.mst.matt.tradingplatformapp.model.PriceAlert.*;
import com.mst.matt.tradingplatformapp.service.alert.AlertService;
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

    @Autowired private AlertService alertService;

    private UserProfile activeProfile;
    private static final DateTimeFormatter DTF =
            DateTimeFormatter.ofPattern("MM/dd HH:mm");

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        alertTypeCombo.getItems().setAll(AlertType.values());
        alertTypeCombo.setValue(AlertType.PRICE_ABOVE);
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
                    PriceAlert a = getTableView().getItems().get(getIndex());
                    alertService.toggleAlert(a.getId(), !a.isActive());
                    refreshTable();
                });
                deleteBtn.setOnAction(e -> {
                    PriceAlert a = getTableView().getItems().get(getIndex());
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

        AlertType selectedType = alertTypeCombo.getValue();
        if (selectedType == null) {
            new Alert(Alert.AlertType.WARNING, "Alert type is required.").showAndWait();
            return;
        }

        PriceAlert.PriceAlertBuilder builder = PriceAlert.builder()
                .profile(activeProfile)
                .symbol(sym)
                .alertType(selectedType)
                .active(true)
                .notifyEmail(emailCheck.isSelected())
                .notifyTelegram(telegramCheck.isSelected())
                .notifyDesktop(desktopCheck.isSelected())
                .repeating(repeatingCheck.isSelected())
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
        newSymbolField.clear();
        targetPriceField.clear();
        customMessageField.clear();
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
        List<PriceAlert> alerts =
                alertService.getAlertsForProfile(activeProfile);
        alertsTable.getItems().setAll(alerts);
    }
}
