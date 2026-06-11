package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.model.AppUser.Role;
import com.mst.matt.tradingplatformapp.service.auth.AuthService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import net.rgielen.fxweaver.core.FxmlView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Registration screen controller.
 * New self-registered users always get REGULAR_USER role.
 */
@Component
@FxmlView("/fxml/RegisterView.fxml")
public class RegisterController implements Initializable {

    @FXML private TextField     usernameField;
    @FXML private TextField     displayNameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label         errorLabel;

    @Autowired private AuthService authService;

    /** Called after successful registration — switches to login screen. */
    private Runnable onRegistered;

    /** Called when user clicks "Back to Sign In". */
    private Runnable onBackToLogin;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        hideError();
    }

    public void setOnRegistered(Runnable r) { this.onRegistered = r; }
    public void setOnBackToLogin(Runnable r) { this.onBackToLogin = r; }

    @FXML
    public void onRegister() {
        String username    = trim(usernameField.getText());
        String displayName = trim(displayNameField.getText());
        String password    = passwordField.getText() == null ? "" : passwordField.getText();
        String confirm     = confirmPasswordField.getText() == null ? "" : confirmPasswordField.getText();

        if (username.isEmpty()) { showError("Username is required."); return; }
        if (username.length() < 3) { showError("Username must be at least 3 characters."); return; }
        if (password.length() < 6) { showError("Password must be at least 6 characters."); return; }
        if (!password.equals(confirm)) { showError("Passwords do not match."); return; }

        Thread.ofVirtual().start(() -> {
            try {
                authService.register(username, password,
                        displayName.isEmpty() ? username : displayName,
                        Role.REGULAR_USER);
                Platform.runLater(() -> {
                    hideError();
                    if (onRegistered != null) onRegistered.run();
                });
            } catch (IllegalArgumentException ex) {
                Platform.runLater(() -> showError(ex.getMessage()));
            }
        });
    }

    @FXML
    public void onBackToLogin() {
        if (onBackToLogin != null) onBackToLogin.run();
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }

    private static String trim(String s) { return s == null ? "" : s.trim(); }

    /** Reset form state. */
    public void reset() {
        if (usernameField      != null) usernameField.clear();
        if (displayNameField   != null) displayNameField.clear();
        if (passwordField      != null) passwordField.clear();
        if (confirmPasswordField != null) confirmPasswordField.clear();
        hideError();
    }
}
