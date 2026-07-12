package com.mst.matt.tradingplatformapp.ui.chart;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;

/**
 * Collapsible wrapper for {@link DrawingToolbar} with drag offset, auto-hide,
 * and vertical scrolling when the tool list exceeds the chart height.
 */
public class DrawingToolbarPane extends StackPane {

    private static final double TOP_MARGIN = 8;

    private final DrawingToolbar toolbar;
    private final ScrollPane scrollPane;
    private final Button collapseBtn;
    private StackPane chartStackHost;
    private double dragStartX, dragStartY;
    private double startTx, startTy;

    public DrawingToolbarPane(DrawingToolbar toolbar) {
        this.toolbar = toolbar;

        scrollPane = new ScrollPane(toolbar);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPannable(true);
        scrollPane.setPrefWidth(56);
        scrollPane.setMaxWidth(64);
        scrollPane.setMinHeight(120);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        collapseBtn = new Button("◀");
        collapseBtn.setMinSize(18, 18);
        collapseBtn.setMaxSize(18, 18);
        collapseBtn.setStyle("-fx-background-color:#21262d; -fx-text-fill:#8b949e;"
                + "-fx-font-size:8px; -fx-background-radius:3; -fx-cursor:hand; -fx-padding:0;");
        collapseBtn.setTooltip(new Tooltip("Collapse / expand drawing tools"));
        collapseBtn.setOnAction(e -> toggleCollapse());

        getChildren().addAll(scrollPane, collapseBtn);
        StackPane.setAlignment(collapseBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(collapseBtn, new Insets(1, 1, 0, 0));

        setPickOnBounds(false);
        setMaxWidth(64);

        scrollPane.setOnMousePressed(this::startDrag);
        scrollPane.setOnMouseDragged(this::drag);
    }

    public DrawingToolbar getToolbar() { return toolbar; }

    public void attachTo(StackPane chartStack) {
        this.chartStackHost = chartStack;
        StackPane.setAlignment(this, Pos.TOP_LEFT);
        StackPane.setMargin(this, new Insets(TOP_MARGIN, 0, 0, 4));
        if (!chartStack.getChildren().contains(this)) {
            chartStack.getChildren().add(1, this);
        }
        chartStack.heightProperty().addListener((o, a, b) -> updateScrollMaxHeight(b.doubleValue()));
        updateScrollMaxHeight(chartStack.getHeight());
    }

    public void onChartMouseEntered() {
        setOpacity(1);
    }

    public void onChartMouseExited() {
        if (!toolbar.isCollapsed()) setOpacity(0.3);
    }

    private void updateScrollMaxHeight(double stackHeight) {
        if (stackHeight <= 0) return;
        double maxH = Math.max(120, stackHeight - TOP_MARGIN - 8);
        scrollPane.setMaxHeight(maxH);
    }

    private void toggleCollapse() {
        toolbar.setCollapsed(!toolbar.isCollapsed());
        collapseBtn.setText(toolbar.isCollapsed() ? "▶" : "◀");
        scrollPane.setVisible(!toolbar.isCollapsed());
        scrollPane.setManaged(!toolbar.isCollapsed());
        setOpacity(1);
        if (chartStackHost != null) {
            updateScrollMaxHeight(chartStackHost.getHeight());
        }
    }

    private void startDrag(MouseEvent e) {
        dragStartX = e.getSceneX();
        dragStartY = e.getSceneY();
        startTx = getTranslateX();
        startTy = getTranslateY();
        e.consume();
    }

    private void drag(MouseEvent e) {
        if (chartStackHost == null) {
            setTranslateX(startTx + e.getSceneX() - dragStartX);
            setTranslateY(startTy + e.getSceneY() - dragStartY);
            e.consume();
            return;
        }
        double nextX = startTx + e.getSceneX() - dragStartX;
        double nextY = startTy + e.getSceneY() - dragStartY;
        double maxX = Math.max(0, chartStackHost.getWidth() - getWidth());
        double maxY = Math.max(0, chartStackHost.getHeight() - getHeight());
        setTranslateX(Math.max(0, Math.min(maxX, nextX)));
        setTranslateY(Math.max(0, Math.min(maxY, nextY)));
        e.consume();
    }
}
