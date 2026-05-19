package com.mst.matt.tradingplatformapp.config;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import net.rgielen.fxweaver.core.FxWeaver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Listens for StageReadyEvent and configures the primary Stage.
 * Uses FxWeaver so every FXML controller is a Spring-managed bean.
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
            // Load the main dashboard via FxWeaver (Spring-managed controller)
            Parent root = fxWeaver.loadView(
                    com.mst.matt.tradingplatformapp.controller.MainDashboardController.class
            );

            Scene scene = new Scene(root, 1440, 900);

            // Apply global dark theme CSS
            scene.getStylesheets().add(
                    Objects.requireNonNull(
                            getClass().getResource("/css/dark-theme.css")
                    ).toExternalForm()
            );

            stage.setTitle("📈 Trading Intelligence Platform");
            stage.setScene(scene);
            stage.setMinWidth(1200);
            stage.setMinHeight(700);
            stage.show();

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize main stage", e);
        }
    }
}