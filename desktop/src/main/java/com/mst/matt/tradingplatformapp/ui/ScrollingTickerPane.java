package com.mst.matt.tradingplatformapp.ui;

import javafx.animation.AnimationTimer;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Horizontally scrolling price ticker — items scroll left and loop seamlessly.
 * The track is unmanaged so it never stretches the parent layout width.
 */
public class ScrollingTickerPane extends StackPane {

    private static final double SCROLL_PX_PER_SEC = 50.0;

    private final HBox track = new HBox(28);
    private final Map<String, Label> labels = new LinkedHashMap<>();
    private final List<String> symbolOrder = new ArrayList<>();
    private double scrollX;
    private double loopWidth;
    private AnimationTimer timer;

    public ScrollingTickerPane() {
        getStyleClass().add("ticker-bar");
        setMinHeight(32);
        setMaxHeight(32);
        setMinWidth(0);
        track.setAlignment(Pos.CENTER_LEFT);
        track.setManaged(false);
        setClip(new Rectangle());
        getChildren().add(track);
        widthProperty().addListener((o, a, w) -> {
            updateClip();
            layoutTrack();
        });
        heightProperty().addListener((o, a, h) -> {
            updateClip();
            layoutTrack();
        });
        startScroll();
    }

    public void updateQuote(String symbol, String text, boolean up) {
        String sym = symbol.toUpperCase();
        Label lbl = labels.get(sym);
        if (lbl == null) return;
        lbl.setText(text);
        lbl.getStyleClass().removeAll("ticker-price-up", "ticker-price-down");
        lbl.getStyleClass().add(up ? "ticker-price-up" : "ticker-price-down");
    }

    public void setSymbols(java.util.Collection<String> symbols) {
        labels.clear();
        symbolOrder.clear();
        track.getChildren().clear();
        scrollX = 0;
        track.setTranslateX(0);

        for (String sym : symbols) {
            String key = sym.toUpperCase();
            symbolOrder.add(key);
            Label item = new Label(key + "  —");
            item.getStyleClass().addAll("ticker-item", "ticker-price-up");
            item.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            labels.put(key, item);
            track.getChildren().add(item);
            Label sep = new Label("   │   ");
            sep.setStyle("-fx-text-fill:#30363d; -fx-font-size:11px;");
            track.getChildren().add(sep);
        }
        rebuildLoop();
        layoutTrack();
    }

    private void rebuildLoop() {
        if (track.getChildren().isEmpty()) {
            loopWidth = 0;
            return;
        }
        // Keep only the first set of nodes (one copy of the symbol row)
        int half = symbolOrder.isEmpty() ? 0 : symbolOrder.size() * 2;
        while (track.getChildren().size() > half && half > 0) {
            track.getChildren().remove(track.getChildren().size() - 1);
        }
        track.applyCss();
        track.layout();
        loopWidth = track.getWidth();
        if (loopWidth <= 0) {
            loopWidth = symbolOrder.size() * 160.0;
        }
        // Build a fresh list of cloned label nodes for the second loop copy;
        // we must NOT add the existing nodes (duplicate children error).
        List<javafx.scene.Node> clone = new ArrayList<>();
        for (String key : symbolOrder) {
            Label orig = labels.get(key);
            if (orig == null) continue;
            Label dup = new Label(orig.getText());
            dup.getStyleClass().addAll(orig.getStyleClass());
            dup.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
            clone.add(dup);
            Label sep = new Label("   │   ");
            sep.setStyle("-fx-text-fill:#30363d; -fx-font-size:11px;");
            clone.add(sep);
        }
        track.getChildren().addAll(clone);
        track.applyCss();
        track.layout();
        loopWidth = track.getWidth() / 2.0;
        if (loopWidth <= 0) loopWidth = symbolOrder.size() * 160.0;
    }

    private void layoutTrack() {
        track.resize(track.getWidth(), getHeight());
        track.relocate(0, (getHeight() - track.getHeight()) / 2.0);
        updateClip();
    }

    private void updateClip() {
        Rectangle clip = (Rectangle) getClip();
        clip.setWidth(Math.max(0, getWidth()));
        clip.setHeight(Math.max(0, getHeight()));
    }

    @Override
    protected double computePrefWidth(double height) {
        return 0;
    }

    private void startScroll() {
        if (timer != null) timer.stop();
        long[] last = {System.nanoTime()};
        timer = new AnimationTimer() {
            @Override public void handle(long now) {
                double dt = (now - last[0]) / 1_000_000_000.0;
                last[0] = now;
                double viewW = getWidth();
                if (viewW <= 0 || loopWidth <= viewW || labels.isEmpty()) {
                    track.setTranslateX(0);
                    return;
                }
                scrollX += SCROLL_PX_PER_SEC * dt;
                if (scrollX >= loopWidth) scrollX -= loopWidth;
                track.setTranslateX(-scrollX);
            }
        };
        timer.start();
    }

    public void stop() {
        if (timer != null) timer.stop();
    }
}
