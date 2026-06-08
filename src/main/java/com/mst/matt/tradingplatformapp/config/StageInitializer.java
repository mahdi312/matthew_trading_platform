package com.mst.matt.tradingplatformapp.config;

import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
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

            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
            double width  = Math.min(1440, bounds.getWidth() * 0.92);
            double height = Math.min(900,  bounds.getHeight() * 0.90);
            double minW   = Math.min(1200, bounds.getWidth() * 0.75);
            double minH   = Math.min(700,  bounds.getHeight() * 0.70);

            Scene scene = new Scene(root, width, height);

            scene.getStylesheets().add(
                    Objects.requireNonNull(
                            getClass().getResource("/css/dark-theme.css")
                    ).toExternalForm()
            );

            stage.setTitle("📈 Trading Intelligence Platform");
            stage.setScene(scene);
            stage.setMinWidth(minW);
            stage.setMinHeight(minH);
            stage.setX(bounds.getMinX() + (bounds.getWidth() - width) / 2);
            stage.setY(bounds.getMinY() + (bounds.getHeight() - height) / 2);
            stage.show();

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize main stage", e);
        }
    }
}