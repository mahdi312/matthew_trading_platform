package com.mst.matt.tradingplatformapp.ui.chart;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;

/**
 * Collapsible wrapper for {@link DrawingToolbar} with drag offset and auto-hide.
 */
public class DrawingToolbarPane extends StackPane {

    private final DrawingToolbar toolbar;
    private final Button collapseBtn;
    private double dragStartX, dragStartY;
    private double startTx, startTy;

    public DrawingToolbarPane(DrawingToolbar toolbar) {
        this.toolbar = toolbar;

        collapseBtn = new Button("◀");
        collapseBtn.setMinSize(18, 18);
        collapseBtn.setMaxSize(18, 18);
        collapseBtn.setStyle("-fx-background-color:#21262d; -fx-text-fill:#8b949e;"
                + "-fx-font-size:8px; -fx-background-radius:3; -fx-cursor:hand; -fx-padding:0;");
        collapseBtn.setTooltip(new Tooltip("Collapse / expand drawing tools"));
        collapseBtn.setOnAction(e -> toggleCollapse());

        getChildren().addAll(toolbar, collapseBtn);
        StackPane.setAlignment(collapseBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(collapseBtn, new Insets(1, 1, 0, 0));

        setPickOnBounds(false);
        setMaxWidth(52);

        toolbar.setOnMousePressed(this::startDrag);
        toolbar.setOnMouseDragged(this::drag);
    }

    public DrawingToolbar getToolbar() { return toolbar; }

    public void attachTo(StackPane chartStack) {
        StackPane.setAlignment(this, Pos.TOP_LEFT);
        StackPane.setMargin(this, new Insets(8, 0, 0, 4));
        if (!chartStack.getChildren().contains(this)) {
            chartStack.getChildren().add(1, this);
        }
    }

    public void onChartMouseEntered() {
        setOpacity(1);
    }

    public void onChartMouseExited() {
        if (!toolbar.isCollapsed()) setOpacity(0.3);
    }

    private void toggleCollapse() {
        toolbar.setCollapsed(!toolbar.isCollapsed());
        collapseBtn.setText(toolbar.isCollapsed() ? "▶" : "◀");
        setOpacity(1);
    }

    private void startDrag(MouseEvent e) {
        dragStartX = e.getSceneX();
        dragStartY = e.getSceneY();
        startTx = getTranslateX();
        startTy = getTranslateY();
        e.consume();
    }

    private void drag(MouseEvent e) {
        setTranslateX(startTx + e.getSceneX() - dragStartX);
        setTranslateY(startTy + e.getSceneY() - dragStartY);
        e.consume();
    }
}
