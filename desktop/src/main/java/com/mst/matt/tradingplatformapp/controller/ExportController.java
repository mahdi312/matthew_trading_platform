package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.model.UserProfile;
import com.mst.matt.tradingplatformapp.model.fundamental.FundamentalsReport;
import com.mst.matt.tradingplatformapp.service.export.ExcelExportService;
import com.mst.matt.tradingplatformapp.service.fundamental.FundamentalRouter;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import net.rgielen.fxweaver.core.FxmlView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Export screen controller.
 */
@Component
@FxmlView("/fxml/ExportView.fxml")
public class ExportController {

    @FXML private VBox exportRoot;
    /** T-22: optional inclusion of a fundamentals sheet. */
    @FXML private CheckBox includeFundamentalsCheck;
    @FXML private TextField fundamentalsSymbolField;

    @Autowired private ExcelExportService excelExportService;
    @Autowired private FundamentalRouter fundamentalRouter;

    private UserProfile activeProfile;

    public void setProfile(UserProfile profile) {
        this.activeProfile = profile;
    }

    @FXML public void onExportExcel() {
        if (activeProfile == null) {
            new Alert(Alert.AlertType.WARNING,
                    "No active profile selected.").showAndWait();
            return;
        }

        FileChooser fc = new FileChooser();
        fc.setTitle("Save Excel Report");
        fc.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx"));
        fc.setInitialFileName("TradingReport_"
                + activeProfile.getName().replace(" ", "_") + "_"
                + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
                + ".xlsx");

        var owner = exportRoot != null && exportRoot.getScene() != null
                ? exportRoot.getScene().getWindow()
                : null;
        File file = fc.showSaveDialog(owner);

        if (file != null) {
            // T-22: optionally fetch a fundamentals snapshot to embed as Sheet 5.
            final FundamentalsReport snapshot = maybeFetchFundamentals();
            Thread.ofVirtual().start(() -> {
                try {
                    excelExportService.export(activeProfile,
                            file.getAbsolutePath(), snapshot);
                    javafx.application.Platform.runLater(() ->
                            new Alert(Alert.AlertType.INFORMATION,
                                    "✅ Report exported successfully!\n" + file.getAbsolutePath())
                                    .showAndWait()
                    );
                } catch (Exception e) {
                    javafx.application.Platform.runLater(() ->
                            new Alert(Alert.AlertType.ERROR,
                                    "Export failed: " + e.getMessage()).showAndWait()
                    );
                }
            });
        }
    }

    /**
     * T-22 helper. Returns a {@link FundamentalsReport} when the user ticks the
     * "Include Sheet 5" option and a symbol is available; otherwise {@code null}.
     */
    private FundamentalsReport maybeFetchFundamentals() {
        if (includeFundamentalsCheck == null || !includeFundamentalsCheck.isSelected())
            return null;
        String sym = fundamentalsSymbolField != null
                && fundamentalsSymbolField.getText() != null
                && !fundamentalsSymbolField.getText().isBlank()
                ? fundamentalsSymbolField.getText().trim().toUpperCase()
                : (activeProfile.getDefaultSymbol() != null
                        ? activeProfile.getDefaultSymbol()
                        : activeProfile.getAssetFocus().defaultSymbol());
        try {
            Optional<FundamentalsReport> report = fundamentalRouter.fetch(sym, activeProfile);
            return report.orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
