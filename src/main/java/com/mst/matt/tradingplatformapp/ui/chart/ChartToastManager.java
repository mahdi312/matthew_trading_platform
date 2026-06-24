package com.mst.matt.tradingplatformapp.ui.chart;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * Compact, stacked toast notifications for the chart area (max 3 visible).
 */
public class ChartToastManager {

    private static final int MAX_VISIBLE = 3;
    private static final double AUTO_DISMISS_SEC = 6;

    private final VBox container;
    private final Deque<HBox> toasts = new ArrayDeque<>();
    private boolean enabled = true;
    private Consumer<Void> onViewAllAlerts;

    public ChartToastManager(VBox container) {
        this.container = container;
        container.setAlignment(Pos.TOP_RIGHT);
        container.setSpacing(6);
        container.setPadding(new Insets(10, 10, 0, 0));
        container.setMaxWidth(340);
        container.setPickOnBounds(false);
        container.setMouseTransparent(false);
        container.setVisible(false);
        container.setManaged(false);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            Platform.runLater(this::clearAll);
        }
    }

    public boolean isEnabled() { return enabled; }

    public void setOnViewAllAlerts(Runnable callback) {
        this.onViewAllAlerts = v -> callback.run();
    }

    public void showInfo(String message)    { show(message, "#1a3a1a", "#3fb950", "✔"); }
    public void showError(String message)   { show(message, "#4a1a1a", "#f85149", "⚠"); }
    public void showWarning(String message) { show(message, "#3a2a0a", "#e3b341", "⚠"); }

    public void show(String message, String bgColor, String accentColor, String iconText) {
        if (!enabled) return;
        Platform.runLater(() -> addToast(message, bgColor, accentColor, iconText));
    }

    private void addToast(String message, String bgColor, String accentColor, String iconText) {
        while (toasts.size() >= MAX_VISIBLE) {
            dismiss(toasts.pollFirst());
        }

        HBox toast = new HBox(6);
        toast.setAlignment(Pos.CENTER_LEFT);
        toast.setPadding(new Insets(5, 8, 5, 10));
        toast.setMaxWidth(320);
        toast.setStyle(
                "-fx-background-color:" + bgColor + "cc;"
                + "-fx-border-color:" + accentColor + ";"
                + "-fx-border-width:1;"
                + "-fx-border-radius:6;"
                + "-fx-background-radius:6;"
                + "-fx-effect:dropshadow(gaussian,rgba(0,0,0,0.35),4,0,0,1);");

        Label icon = new Label(iconText);
        icon.setStyle("-fx-text-fill:" + accentColor + "; -fx-font-size:10px;");

        Label msg = new Label(message);
        msg.setStyle("-fx-text-fill:#e6edf3; -fx-font-size:11px;");
        msg.setMaxWidth(220);
        msg.setWrapText(true);
        HBox.setHgrow(msg, Priority.ALWAYS);

        Button close = new Button("✕");
        close.setStyle("-fx-background-color:transparent; -fx-text-fill:#8b949e;"
                + "-fx-cursor:hand; -fx-padding:0 2; -fx-font-size:9px;");
        close.setOnAction(e -> dismiss(toast));

        toast.getChildren().addAll(icon, msg, close);
        container.getChildren().add(toast);
        toasts.addLast(toast);
        container.setVisible(true);
        container.setManaged(true);

        PauseTransition autoHide = new PauseTransition(Duration.seconds(AUTO_DISMISS_SEC));
        autoHide.setOnFinished(e -> dismiss(toast));
        toast.setUserData(autoHide);
        toast.setOnMouseEntered(e -> {
            PauseTransition pt = (PauseTransition) toast.getUserData();
            if (pt != null) pt.pause();
        });
        toast.setOnMouseExited(e -> {
            PauseTransition pt = (PauseTransition) toast.getUserData();
            if (pt != null) pt.play();
        });
        autoHide.play();

        if (toasts.size() >= MAX_VISIBLE && onViewAllAlerts != null
                && container.lookup("#viewAllLink") == null) {
            Hyperlink viewAll = new Hyperlink("View All");
            viewAll.setId("viewAllLink");
            viewAll.setStyle("-fx-text-fill:#58a6ff; -fx-font-size:10px;");
            viewAll.setOnAction(e -> onViewAllAlerts.accept(null));
            container.getChildren().add(viewAll);
        }
    }

    private void dismiss(HBox toast) {
        if (toast == null) return;
        PauseTransition pt = (PauseTransition) toast.getUserData();
        if (pt != null) pt.stop();
        container.getChildren().remove(toast);
        toasts.remove(toast);
        if (toasts.isEmpty()) {
            container.getChildren().removeIf(n -> n instanceof Hyperlink);
            container.setVisible(false);
            container.setManaged(false);
        }
    }

    private void clearAll() {
        new ArrayDeque<>(toasts).forEach(this::dismiss);
        container.getChildren().clear();
        container.setVisible(false);
        container.setManaged(false);
    }
}
