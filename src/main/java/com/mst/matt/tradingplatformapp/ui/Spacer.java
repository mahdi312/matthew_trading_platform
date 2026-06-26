package com.mst.matt.tradingplatformapp.ui;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * A flexible spacer region that grows to fill available horizontal space in an HBox.
 *
 * <p>Usage in FXML (after importing):
 * <pre>{@code <Spacer HBox.hgrow="ALWAYS"/>}</pre>
 *
 * <p>Usage in Java:
 * <pre>{@code Spacer s = new Spacer(); // auto-sets HBox.hgrow=ALWAYS}</pre>
 */
public class Spacer extends Region {

    public Spacer() {
        setMinWidth(0);
        setPrefWidth(0);
        // Automatically grow to fill remaining HBox space
        HBox.setHgrow(this, Priority.ALWAYS);
    }
}
