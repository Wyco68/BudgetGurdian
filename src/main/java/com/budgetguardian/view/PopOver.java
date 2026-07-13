package com.budgetguardian.view;

import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Popup;

/**
 * Minimal floating panel anchored under a trigger node — a dependency-free
 * stand-in for a rich popover. Backed by a {@link Popup} that auto-hides when
 * focus leaves it.
 */
public final class PopOver {

    private final Popup popup = new Popup();
    private final StackPane container = new StackPane();

    public PopOver() {
        container.getStyleClass().add("popover");
        popup.getContent().add(container);
        popup.setAutoHide(true);
    }

    /** @return whether the popover is currently visible. */
    public boolean isShowing() {
        return popup.isShowing();
    }

    /** Shows {@code content} under {@code anchor}, or hides if already open. */
    public void toggle(Node anchor, Region content) {
        if (popup.isShowing()) {
            popup.hide();
            return;
        }
        replaceContent(content);
        Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        Point2D at = new Point2D(bounds.getMinX(), bounds.getMaxY() + 6);
        popup.show(anchor, at.getX() - 260, at.getY());
    }

    /** Swaps the displayed content (used when the model changes while open). */
    public void replaceContent(Region content) {
        container.getChildren().setAll(content);
    }

    /** Hides the popover. */
    public void hide() {
        popup.hide();
    }
}
