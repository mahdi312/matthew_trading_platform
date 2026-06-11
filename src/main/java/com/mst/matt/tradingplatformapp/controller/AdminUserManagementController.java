package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.model.AppUser;
import com.mst.matt.tradingplatformapp.model.AppUser.Role;
import com.mst.matt.tradingplatformapp.service.auth.AuthService;
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

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Admin panel for managing users and tab permissions.
 * ADMIN only — MainDashboardController gates access via authService.isAdmin().
 */
@Component
@FxmlView("/fxml/AdminUserManagementView.fxml")
public class AdminUserManagementController implements Initializable {

    @FXML private TableView<AppUser>                    usersTable;
    @FXML private TableColumn<AppUser, String>          colUsername;
    @FXML private TableColumn<AppUser, String>          colDisplay;
    @FXML private TableColumn<AppUser, String>          colRole;
    @FXML private TableColumn<AppUser, String>          colActive;
    @FXML private TableColumn<AppUser, String>          colLastLogin;
    @FXML private ComboBox<String>                      permScopeCombo;
    @FXML private VBox                                  tabPermissionsBox;
    @FXML private Label                                 statusLabel;

    @Autowired private AuthService authService;

    private Runnable onClose;

    // Map of tabName -> CheckBox for the currently-scoped permission editor
    private final Map<String, CheckBox> tabCheckBoxes = new LinkedHashMap<>();
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupTable();
        setupPermScopeCombo();
        buildTabCheckboxes();
    }

    public void setOnClose(Runnable r) { this.onClose = r; }

    /** Called by MainDashboardController on every navigation to this panel. */
    public void refresh() {
        loadUsers();
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
    }

    // ── Table setup ────────────────────────────────────────────

    private void setupTable() {
        colUsername.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getUsername()));
        colDisplay.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getDisplayName()));
        colRole.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().getRole().label()));
        colActive.setCellValueFactory(c ->
                new SimpleStringProperty(c.getValue().isActive() ? "✓" : "✗"));
        colLastLogin.setCellValueFactory(c -> {
            var t = c.getValue().getLastLoginAt();
            return new SimpleStringProperty(t == null ? "—" : t.format(DTF));
        });

        // Style active/inactive rows
        usersTable.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(AppUser u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) {
                    setStyle("");
                } else if (!u.isActive()) {
                    setStyle("-fx-opacity:0.5;");
                } else {
                    setStyle("");
                }
            }
        });
    }

    private void loadUsers() {
        Thread.ofVirtual().start(() -> {
            try {
                var users = authService.allUsers();
                Platform.runLater(() ->
                        usersTable.setItems(FXCollections.observableArrayList(users)));
            } catch (SecurityException ex) {
                showStatus("Access denied: " + ex.getMessage(), true);
            }
        });
    }

    // ── Permission scope combo ─────────────────────────────────

    private void setupPermScopeCombo() {
        // Scope options: all roles + "— (per user) —" header + users
        List<String> items = new ArrayList<>();
        for (Role r : Role.values()) items.add("Role: " + r.label());
        permScopeCombo.setItems(FXCollections.observableArrayList(items));
        permScopeCombo.setValue("Role: " + Role.REGULAR_USER.label());
    }

    @FXML
    public void onPermScopeChanged() {
        // Load current permissions for selected scope
        loadPermissionsForScope();
    }

    private void loadPermissionsForScope() {
        String scope = permScopeCombo.getValue();
        if (scope == null) return;
        // Update checkboxes based on scope — for now use default (all visible)
        // Real persistence handled in onSavePermissions
        tabCheckBoxes.forEach((tab, cb) -> cb.setSelected(true));
    }

    // ── Tab permission checkboxes ──────────────────────────────

    private void buildTabCheckboxes() {
        tabPermissionsBox.getChildren().clear();
        tabCheckBoxes.clear();
        for (String tab : AuthService.ALL_TABS) {
            CheckBox cb = new CheckBox(formatTabName(tab));
            cb.setSelected(true);
            cb.setStyle("-fx-text-fill:#e6edf3; -fx-font-size:13px;");
            tabPermissionsBox.getChildren().add(cb);
            tabCheckBoxes.put(tab, cb);
        }
    }

    // ── User actions ───────────────────────────────────────────

    @FXML
    public void onAddUser() {
        // Simple dialog to add user
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Add New User");
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setStyle("-fx-padding:16;");
        TextField usernameF = styledTextField("Username");
        TextField displayF  = styledTextField("Display Name");
        PasswordField passF = new PasswordField();
        passF.setPromptText("Password (min 6 chars)");
        passF.setStyle(fieldStyle());
        ComboBox<Role> roleCombo = new ComboBox<>(
                FXCollections.observableArrayList(Role.values()));
        roleCombo.setValue(Role.REGULAR_USER);
        roleCombo.setCellFactory(lv -> roleCell());
        roleCombo.setButtonCell(roleCell());
        roleCombo.setStyle(fieldStyle());

        content.getChildren().addAll(
                label("Username:"), usernameF,
                label("Display Name:"), displayF,
                label("Password:"), passF,
                label("Role:"), roleCombo);
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().setStyle("-fx-background-color:#161b22;");

        dlg.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            String u = usernameF.getText().trim();
            String d = displayF.getText().trim();
            String p = passF.getText();
            Role r   = roleCombo.getValue();
            if (u.isEmpty() || p.isEmpty()) {
                showStatus("Username and password are required.", true);
                return;
            }
            Thread.ofVirtual().start(() -> {
                try {
                    authService.register(u, p, d.isEmpty() ? u : d, r);
                    Platform.runLater(() -> {
                        showStatus("User '" + u + "' created.", false);
                        loadUsers();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> showStatus("Error: " + ex.getMessage(), true));
                }
            });
        });
    }

    @FXML
    public void onChangeRole() {
        AppUser selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("Select a user first.", true); return; }

        ChoiceDialog<Role> dlg = new ChoiceDialog<>(selected.getRole(),
                Role.values());
        dlg.setTitle("Change Role");
        dlg.setHeaderText("Change role for: " + selected.getUsername());
        dlg.setContentText("New role:");
        dlg.showAndWait().ifPresent(newRole -> {
            Thread.ofVirtual().start(() -> {
                try {
                    authService.changeRole(selected.getId(), newRole);
                    Platform.runLater(() -> {
                        showStatus("Role changed to " + newRole.label() + ".", false);
                        loadUsers();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> showStatus("Error: " + ex.getMessage(), true));
                }
            });
        });
    }

    @FXML
    public void onToggleActive() {
        AppUser selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("Select a user first.", true); return; }
        boolean newActive = !selected.isActive();
        Thread.ofVirtual().start(() -> {
            try {
                authService.setUserActive(selected.getId(), newActive);
                Platform.runLater(() -> {
                    showStatus("User " + (newActive ? "activated" : "deactivated") + ".", false);
                    loadUsers();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> showStatus("Error: " + ex.getMessage(), true));
            }
        });
    }

    @FXML
    public void onDeleteUser() {
        AppUser selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) { showStatus("Select a user first.", true); return; }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete User");
        confirm.setHeaderText("Delete user '" + selected.getUsername() + "'?");
        confirm.setContentText("This cannot be undone.");
        confirm.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        confirm.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            Thread.ofVirtual().start(() -> {
                try {
                    authService.deleteUser(selected.getId());
                    Platform.runLater(() -> {
                        showStatus("User deleted.", false);
                        loadUsers();
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> showStatus("Error: " + ex.getMessage(), true));
                }
            });
        });
    }

    // ── Permissions save ───────────────────────────────────────

    @FXML
    public void onSavePermissions() {
        String scope = permScopeCombo.getValue();
        if (scope == null) { showStatus("Select a scope first.", true); return; }

        Thread.ofVirtual().start(() -> {
            try {
                tabCheckBoxes.forEach((tab, cb) -> {
                    if (scope.startsWith("Role: ")) {
                        String roleLabel = scope.substring(6);
                        Role role = Arrays.stream(Role.values())
                                .filter(r -> r.label().equals(roleLabel))
                                .findFirst().orElse(null);
                        if (role != null) {
                            authService.setTabVisibilityForRole(role, tab, cb.isSelected());
                        }
                    } else {
                        // Per-user: find user by display name in table
                        usersTable.getItems().stream()
                                .filter(u -> u.getDisplayName().equals(scope))
                                .findFirst()
                                .ifPresent(u -> authService.setTabVisibilityForUser(
                                        u.getId(), tab, cb.isSelected()));
                    }
                });
                Platform.runLater(() -> showStatus("Permissions saved.", false));
            } catch (Exception ex) {
                Platform.runLater(() -> showStatus("Error: " + ex.getMessage(), true));
            }
        });
    }

    @FXML
    public void onClose() {
        if (onClose != null) onClose.run();
    }

    // ── Helpers ────────────────────────────────────────────────

    private void showStatus(String msg, boolean error) {
        Platform.runLater(() -> {
            statusLabel.setText(msg);
            statusLabel.setStyle("-fx-padding:8 20; -fx-font-size:12px; -fx-text-fill:"
                    + (error ? "#f85149" : "#3fb950") + ";");
            statusLabel.setVisible(true);
            statusLabel.setManaged(true);
        });
    }

    private static String formatTabName(String tab) {
        return tab.replace("_", " ");
    }

    private static TextField styledTextField(String prompt) {
        TextField f = new TextField();
        f.setPromptText(prompt);
        f.setStyle(fieldStyle());
        return f;
    }

    private static String fieldStyle() {
        return "-fx-background-color:#21262d; -fx-text-fill:#e6edf3; -fx-border-color:#30363d;"
                + "-fx-border-radius:4; -fx-background-radius:4; -fx-padding:6 10;";
    }

    private static Label label(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill:#8b949e; -fx-font-size:12px;");
        return l;
    }

    private static ListCell<Role> roleCell() {
        return new ListCell<>() {
            @Override protected void updateItem(Role r, boolean empty) {
                super.updateItem(r, empty);
                setText(empty || r == null ? null : r.label());
            }
        };
    }
}
