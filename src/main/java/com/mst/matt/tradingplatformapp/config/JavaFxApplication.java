package com.mst.matt.tradingplatformapp.config;

import com.mst.matt.tradingplatformapp.TradingPlatformAppApplication;
import com.mst.matt.tradingplatformapp.config.StageReadyEvent;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * JavaFX Application lifecycle manager.
 *
 * Boot order:
 * 1. init()  → Spring Boot context starts (all beans created, DB migrated)
 * 2. start() → Spring publishes StageReadyEvent → StageInitializer loads main scene
 * 3. stop()  → Spring context gracefully closed
 *
 * This clean separation means every @Controller / @Service is fully
 * initialized before the first pixel is drawn on screen.
 */
public class JavaFxApplication extends Application {

    private ConfigurableApplicationContext springContext;

    @Override
    public void init() {
        // Build Spring context with JavaFX parameters passed in
        springContext = new SpringApplicationBuilder(TradingPlatformAppApplication.class)
                .run(getParameters().getRaw().toArray(new String[0]));
    }

    @Override
    public void start(Stage primaryStage) {
        // Publish the stage so StageInitializer can set up the scene
        springContext.publishEvent(new StageReadyEvent(primaryStage));
    }

    @Override
    public void stop() {
        springContext.close();
        Platform.exit();
    }
}