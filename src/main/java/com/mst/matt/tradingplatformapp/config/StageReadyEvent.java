package com.mst.matt.tradingplatformapp.config;

import javafx.stage.Stage;
import org.springframework.context.ApplicationEvent;

/**
 * Custom Spring event fired when the JavaFX primary Stage is ready.
 * StageInitializer listens for this to load the first scene.
 */
public class StageReadyEvent extends ApplicationEvent {

    private final Stage stage;

    public StageReadyEvent(Stage stage) {
        super(stage);
        this.stage = stage;
    }

    public Stage getStage() {
        return stage;
    }
}