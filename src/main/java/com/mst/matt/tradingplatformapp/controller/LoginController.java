package com.mst.matt.tradingplatformapp.controller;

import com.mst.matt.tradingplatformapp.model.AppUser;
import com.mst.matt.tradingplatformapp.service.auth.AuthService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import net.rgielen.fxweaver.core.FxWeaver;
import net.rgielen.fxweaver.core.FxmlView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * Login screen controller.
 * After successful login, hides the login scene and shows the main dashboard.
 */
@Component
@FxmlView("/fxml/LoginView.fxml")
public class LoginController implements Initializable {

    @FXML private TextField     usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label         errorLabel;

    @Autowired private AuthService authService;
    @Autowired private FxWeaver    fxWeaver;

    /** Called by StageInitializer / parent when login succeeds. */
    private Runnable onLoginSuccess;

    /** Called when "Create new account" is pressed. */
    private Runnable onShowRegister;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        hideError();
    }

    /** Register the callback invoked on successful login. */
    public void setOnLoginSuccess(Runnable r) { this.onLoginSuccess = r; }

    /** Register the callback for switching to registration form. */
    public void setOnShowRegister(Runnable r) { this.onShowRegister = r; }

    @FXML
    public void onLogin() {
        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        String password = passwordField.getText() == null ? "" : passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter username and password.");
            return;
        }

        // Run auth on a background thread to avoid blocking FX thread
        Thread.ofVirtual().start(() -> {
            Optional<AppUser> result = authService.login(username, password);
            Platform.runLater(() -> {
                if (result.isPresent()) {
                    hideError();
                    if (onLoginSuccess != null) onLoginSuccess.run();
                } else {
                    showError("Invalid username or password.");
                    passwordField.clear();
                    passwordField.requestFocus();
                }
            });
        });
    }

    @FXML
    public void onShowRegister() {
        if (onShowRegister != null) onShowRegister.run();
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

    /** Reset the form (called when re-showing the login screen after logout). */
    public void reset() {
        if (usernameField != null) usernameField.clear();
        if (passwordField != null) passwordField.clear();
        hideError();
        Platform.runLater(() -> {
            if (usernameField != null) usernameField.requestFocus();
        });
    }
}
