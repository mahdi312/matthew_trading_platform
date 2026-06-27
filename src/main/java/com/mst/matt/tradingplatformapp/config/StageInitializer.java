package com.mst.matt.tradingplatformapp.config;

import com.mst.matt.tradingplatformapp.controller.LoginController;
import com.mst.matt.tradingplatformapp.controller.MainDashboardController;
import com.mst.matt.tradingplatformapp.controller.RegisterController;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import net.rgielen.fxweaver.core.FxWeaver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Listens for StageReadyEvent.
 * Shows the Login screen first; once authenticated, swaps in the main dashboard.
 *
 * Fix (logout/re-login): keep stable references to the login/register controllers
 * and views; always re-wire callbacks to the SAME controller instances.
 * A fresh dashboard is loaded on each login to avoid stale state across sessions.
 */
@Component
public class StageInitializer implements ApplicationListener<StageReadyEvent> {

    private final FxWeaver fxWeaver;

    // Stable references to login/register — loaded once, reused across sessions
    private Parent loginView;
    private Parent registerView;
    private LoginController    loginCtrl;
    private RegisterController registerCtrl;

    @Autowired
    public StageInitializer(FxWeaver fxWeaver) {
        this.fxWeaver = fxWeaver;
    }

    @Override
    public void onApplicationEvent(StageReadyEvent event) {
        Stage stage = event.getStage();

        try {
            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
            double width  = Math.min(1440, bounds.getWidth() * 0.92);
            double height = Math.min(900,  bounds.getHeight() * 0.90);
            double minW   = Math.min(1200, bounds.getWidth() * 0.75);
            double minH   = Math.min(700,  bounds.getHeight() * 0.70);

            // ── Login / Register views — loaded ONCE ───────────────────
            var loginWc     = fxWeaver.load(LoginController.class);
            var registerWc  = fxWeaver.load(RegisterController.class);

            loginView    = (Parent) loginWc.getView().orElseThrow();
            registerView = (Parent) registerWc.getView().orElseThrow();
            loginCtrl    = loginWc.getController();
            registerCtrl = registerWc.getController();

            // Container to swap between login / register / dashboard
            StackPane root = new StackPane(loginView);

            Scene scene = new Scene(root, width, height);
            scene.getStylesheets().add(
                    Objects.requireNonNull(
                            getClass().getResource("/css/dark-theme.css")
                    ).toExternalForm()
            );

            stage.setTitle("📈 Trading Intelligence Platform — Sign In");
            stage.setScene(scene);
            stage.setMinWidth(420);
            stage.setMinHeight(520);
            stage.setX(bounds.getMinX() + (bounds.getWidth()  - width)  / 2);
            stage.setY(bounds.getMinY() + (bounds.getHeight() - height) / 2);
            stage.show();

            // ── Wire navigation ────────────────────────────────────────

            // Login → Dashboard (wire once; callback references stable controllers)
            loginCtrl.setOnLoginSuccess(() ->
                    loadMainDashboard(stage, root, scene, width, height, minW, minH));

            // Login → Register
            loginCtrl.setOnShowRegister(() -> {
                registerCtrl.reset();
                root.getChildren().setAll(registerView);
            });

            // Register → back to Login
            registerCtrl.setOnBackToLogin(() -> {
                loginCtrl.reset();
                root.getChildren().setAll(loginView);
            });

            // Register → Login (after success)
            registerCtrl.setOnRegistered(() -> {
                loginCtrl.reset();
                root.getChildren().setAll(loginView);
            });

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize main stage", e);
        }
    }

    // ── Private helpers ────────────────────────────────────────

    private void loadMainDashboard(Stage stage, StackPane root, Scene scene,
                                   double width, double height,
                                   double minW,  double minH) {
        // Always load a FRESH dashboard controller on each login so state
        // (profiles, charts, etc.) is re-initialised for the new user session.
        var dashWc = fxWeaver.load(MainDashboardController.class);
        Parent dashView = (Parent) dashWc.getView().orElseThrow();
        MainDashboardController dashCtrl = dashWc.getController();

        // Give the dashboard a logout callback
        dashCtrl.setOnLogout(() -> showLoginScreen(stage, root, scene, width, height, minW, minH));

        // Switch to full-size dashboard window
        stage.setTitle("📈 Trading Intelligence Platform");
        stage.setMinWidth(minW);
        stage.setMinHeight(minH);
        if (stage.getWidth() < minW)  stage.setWidth(minW);
        if (stage.getHeight() < minH) stage.setHeight(minH);
        root.getChildren().setAll(dashView);
    }

    /**
     * Return to the login screen after logout.
     * Reuses the existing stable loginCtrl / loginView instances so the
     * onLoginSuccess callback (already wired) continues to work correctly.
     */
    private void showLoginScreen(Stage stage, StackPane root, Scene scene,
                                  double width, double height,
                                  double dashMinW, double dashMinH) {
        // Reset the login form (clear fields, hide errors)
        loginCtrl.reset();

        // Re-wire login success so a new dashboard is loaded for the returning user
        loginCtrl.setOnLoginSuccess(() ->
                loadMainDashboard(stage, root, scene, width, height, dashMinW, dashMinH));

        stage.setTitle("📈 Trading Intelligence Platform — Sign In");
        stage.setMinWidth(420);
        stage.setMinHeight(520);

        // Put login view back into the scene
        root.getChildren().setAll(loginView);
        Platform.runLater(loginCtrl::reset);   // ensure fields are cleared on FX thread
    }
}
