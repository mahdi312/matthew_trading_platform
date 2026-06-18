package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.model.UserProfile;
import com.mst.matt.tradingplatformapp.model.fundamental.FundamentalsReport;
import com.mst.matt.tradingplatformapp.model.fundamental.YearlyFinancialRow;
import com.mst.matt.tradingplatformapp.repository.SymbolEntryRepository;
import com.mst.matt.tradingplatformapp.repository.UserProfileRepository;
import com.mst.matt.tradingplatformapp.service.ProfilePersistenceService;
import com.mst.matt.tradingplatformapp.service.fundamental.FundamentalDataProvider;
import com.mst.matt.tradingplatformapp.service.fundamental.FundamentalRouter;
import com.mst.matt.tradingplatformapp.service.price.AssetClassDetector;
import com.mst.matt.tradingplatformapp.service.price.AssetClassDetector.AssetClass;
import com.mst.matt.tradingplatformapp.ui.AutocompleteSymbolField;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import net.rgielen.fxweaver.core.FxmlView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Yearly profit & fundamentals review — stocks, forex, crypto symbols.
 * Provider preference comes from the active {@link UserProfile}.
 */
@Component
@FxmlView("/fxml/YearlyProfitView.fxml")
public class YearlyProfitController implements Initializable {

    @FXML private VBox root;
    @FXML private TextField symbolField;
    @FXML private ComboBox<FundamentalDataProvider> providerCombo;
    /** Autocomplete symbol field injected programmatically. */
    private AutocompleteSymbolField autocompleteField;
    @FXML private Label companyLabel;
    @FXML private Label metaLabel;
    @FXML private Label summaryLabel;
    @FXML private Label statusLabel;
    @FXML private TableView<YearlyFinancialRow> yearlyTable;
    @FXML private TableColumn<YearlyFinancialRow, String> colYear;
    @FXML private TableColumn<YearlyFinancialRow, String> colRevenue;
    @FXML private TableColumn<YearlyFinancialRow, String> colGross;
    @FXML private TableColumn<YearlyFinancialRow, String> colOperating;
    @FXML private TableColumn<YearlyFinancialRow, String> colNet;
    @FXML private TableColumn<YearlyFinancialRow, String> colEbitda;
    @FXML private ListView<String> earningsList;

    @Autowired private FundamentalRouter fundamentalRouter;
    @Autowired private UserProfileRepository profileRepository;
    /** P1 (LOG-FIX): async writer to keep JavaFX thread off JPA commits. */
    @Autowired private ProfilePersistenceService profilePersistence;
    @Autowired private SymbolEntryRepository symbolEntryRepository;

    private UserProfile activeProfile;
    private String currentSymbol = "AAPL";

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        providerCombo.setItems(FXCollections.observableArrayList(
                fundamentalRouter.enabledProviders()));
        providerCombo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(FundamentalDataProvider p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : p.getLabel());
            }
        });
        providerCombo.setButtonCell(providerCombo.getCellFactory().call(null));

        colYear.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getFiscalYear()));
        colRevenue.setCellValueFactory(c ->
                new SimpleStringProperty(fmt(c.getValue().getTotalRevenue())));
        colGross.setCellValueFactory(c ->
                new SimpleStringProperty(fmt(c.getValue().getGrossProfit())));
        colOperating.setCellValueFactory(c ->
                new SimpleStringProperty(fmt(c.getValue().getOperatingIncome())));
        colNet.setCellValueFactory(c ->
                new SimpleStringProperty(fmt(c.getValue().getNetIncome())));
        colEbitda.setCellValueFactory(c ->
                new SimpleStringProperty(fmt(c.getValue().getEbitda())));

        symbolField.setText(currentSymbol);

        // ── Wire AutocompleteSymbolField ──────────────────────────────────
        // Replace the plain symbolField TextField in the header with an
        // AutocompleteSymbolField that searches SymbolEntry DB as the user types.
        // Re-create on each FXML reload (singleton bean: initialize() is called
        // again after logout/re-login when fxWeaver re-loads the FXML).
        autocompleteField = new AutocompleteSymbolField(symbolEntryRepository);
        autocompleteField.setPrefWidth(160);
        autocompleteField.setPromptText("AAPL, EURUSD…");
        autocompleteField.setText(currentSymbol);
        autocompleteField.setOnAction(e -> onLoad());
        autocompleteField.setOnSymbolSelected(sym -> {
            currentSymbol = sym.trim().toUpperCase();
            if (symbolField != null) symbolField.setText(currentSymbol);
            onLoad();
        });
        // Keep plain field in sync
        autocompleteField.textProperty().addListener((o, a, text) -> {
            if (text != null && !text.isBlank() && symbolField != null)
                symbolField.setText(text.trim().toUpperCase());
        });
        if (symbolField != null && symbolField.getParent() instanceof HBox headerBox) {
            int idx = headerBox.getChildren().indexOf(symbolField);
            if (idx >= 0) {
                headerBox.getChildren().set(idx, autocompleteField);
            }
        }
    }

    public void setProfile(UserProfile profile) {
        this.activeProfile = profile;
        if (profile == null) return;
        if (profile.getDefaultSymbol() != null && !profile.getDefaultSymbol().isBlank()) {
            currentSymbol = profile.getDefaultSymbol();
            symbolField.setText(currentSymbol);
            if (autocompleteField != null) autocompleteField.setSymbol(currentSymbol);
        } else {
            // P2 (LOG-FIX): null-safe fallback so legacy rows with assetFocus=null
            // can't NPE on profile load.
            UserProfile.ProfileAssetFocus focus = profile.getAssetFocus() != null
                    ? profile.getAssetFocus()
                    : UserProfile.ProfileAssetFocus.MULTI;
            currentSymbol = focus.defaultSymbol();
            symbolField.setText(currentSymbol);
            if (autocompleteField != null) autocompleteField.setSymbol(currentSymbol);
        }
        FundamentalDataProvider pref = FundamentalDataProvider.fromString(
                profile.getFundamentalProvider());
        providerCombo.setValue(pref);
    }

    public void prepareView() {
        if (activeProfile != null && symbolField.getText() != null
                && !symbolField.getText().isBlank()) {
            onLoad();
        }
    }

    @FXML public void onLoad() {
        if (activeProfile == null) {
            statusLabel.setText("Select a profile first.");
            return;
        }
        String sym = symbolField.getText().trim().toUpperCase();
        if (sym.isEmpty()) {
            statusLabel.setText("Enter a symbol.");
            return;
        }
        currentSymbol = sym;

        // T-10: fundamentals providers only support equities. Show a clear message for
        // crypto / forex / commodity / index symbols instead of a silent empty table.
        AssetClass detected = AssetClassDetector.detect(sym);
        if (detected != AssetClass.STOCK) {
            String pretty = detected.name().toLowerCase();
            showUnsupportedAssetMessage(sym, pretty);
            return;
        }

        FundamentalDataProvider selected = providerCombo.getValue();
        if (selected != null && selected != FundamentalDataProvider.AUTO) {
            activeProfile.setFundamentalProvider(selected.name());
            // P1 (LOG-FIX): non-blocking save.
            profilePersistence.saveAsync(activeProfile);
        }

        statusLabel.setText("Loading " + sym + "…");
        Thread.ofVirtual().start(() -> {
            var reportOpt = fundamentalRouter.fetch(sym, activeProfile);
            Platform.runLater(() -> displayReport(reportOpt.orElse(null), sym));
        });
    }

    /** T-10 helper: friendly empty state for unsupported (non-stock) symbols. */
    private void showUnsupportedAssetMessage(String sym, String assetClass) {
        companyLabel.setText(sym);
        metaLabel.setText("—");
        summaryLabel.setText("Fundamentals are not available for " + assetClass + " symbols. "
                + "Try a stock ticker such as AAPL, MSFT, or GOOG — or change the default "
                + "symbol in Profile Settings.");
        yearlyTable.setItems(FXCollections.observableArrayList());
        yearlyTable.setPlaceholder(new Label("No fundamentals for " + assetClass + " symbols."));
        earningsList.setItems(FXCollections.observableArrayList());
        statusLabel.setText("Unsupported asset class for fundamentals: " + assetClass);
    }

    @FXML public void onProviderChanged() {
        if (activeProfile == null) return;
        FundamentalDataProvider p = providerCombo.getValue();
        if (p == null) return;
        activeProfile.setFundamentalProvider(p.name());
        // P1 (LOG-FIX): non-blocking save.
        profilePersistence.saveAsync(activeProfile);
        statusLabel.setText("Fundamental provider: " + p.getLabel());
    }

    private void displayReport(FundamentalsReport report, String sym) {
        if (report == null) {
            companyLabel.setText(sym);
            metaLabel.setText("—");
            summaryLabel.setText("No fundamentals data. Try another provider or symbol.");
            yearlyTable.setItems(FXCollections.observableArrayList());
            earningsList.setItems(FXCollections.observableArrayList());
            statusLabel.setText("No data returned.");
            return;
        }
        companyLabel.setText(report.getCompanyName() != null
                ? report.getCompanyName() : report.getSymbol());
        metaLabel.setText(String.join(" · ",
                nz(report.getAssetTypeLabel()),
                nz(report.getSector()),
                nz(report.getIndustry()),
                nz(report.getCountry()),
                "via " + nz(report.getProviderUsed())));
        summaryLabel.setText(report.getSummaryText() != null
                ? report.getSummaryText() : "");
        yearlyTable.setItems(FXCollections.observableArrayList(report.getYearlyRows()));
        earningsList.setItems(FXCollections.observableArrayList(
                report.getEarningsNotes() != null ? report.getEarningsNotes() : List.of()));
        statusLabel.setText("Loaded " + report.getYearlyRows().size() + " fiscal years.");
    }

    private static String fmt(BigDecimal v) {
        if (v == null || v.compareTo(BigDecimal.ZERO) == 0) return "—";
        double d = v.doubleValue();
        if (Math.abs(d) >= 1_000_000_000) return String.format("%.2fB", d / 1e9);
        if (Math.abs(d) >= 1_000_000)     return String.format("%.2fM", d / 1e6);
        if (Math.abs(d) >= 1_000)         return String.format("%.2fK", d / 1e3);
        return v.toPlainString();
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "—" : s;
    }
}
