package com.mst.matt.tradingplatformapp.config;

import com.mst.matt.tradingplatformapp.controller.LoginController;
import com.mst.matt.tradingplatformapp.controller.MainDashboardController;
import com.mst.matt.tradingplatformapp.controller.RegisterController;
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
 */
@Component
public class StageInitializer implements ApplicationListener<StageReadyEvent> {

    private final FxWeaver fxWeaver;

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

            // ── Login / Register views ─────────────────────────────────
            var loginWc     = fxWeaver.load(LoginController.class);
            var registerWc  = fxWeaver.load(RegisterController.class);

            Parent loginView    = (Parent) loginWc.getView().orElseThrow();
            Parent registerView = (Parent) registerWc.getView().orElseThrow();

            LoginController    loginCtrl    = loginWc.getController();
            RegisterController registerCtrl = registerWc.getController();

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

            // Login → Dashboard
            loginCtrl.setOnLoginSuccess(() -> {
                loadMainDashboard(stage, root, scene, width, height, minW, minH);
            });

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
        // Load dashboard lazily (only once per session)
        var dashWc = fxWeaver.load(MainDashboardController.class);
        Parent dashView = (Parent) dashWc.getView().orElseThrow();
        MainDashboardController dashCtrl = dashWc.getController();

        // Give the dashboard a logout callback so it can return to the login screen
        dashCtrl.setOnLogout(() -> {
            // Reinstate login scene sizing
            stage.setTitle("📈 Trading Intelligence Platform — Sign In");
            stage.setMinWidth(420);
            stage.setMinHeight(520);
            root.getChildren().setAll(
                    Objects.requireNonNull(fxWeaver.load(LoginController.class).getView().orElseThrow()));
            // Re-wire login controller (already loaded, just get it)
            LoginController lc = fxWeaver.load(LoginController.class).getController();
            lc.reset();
            lc.setOnLoginSuccess(() ->
                    loadMainDashboard(stage, root, scene, width, height, minW, minH));
        });

        // Switch to full-size dashboard window
        stage.setTitle("📈 Trading Intelligence Platform");
        stage.setMinWidth(minW);
        stage.setMinHeight(minH);
        root.getChildren().setAll(dashView);
    }
}
