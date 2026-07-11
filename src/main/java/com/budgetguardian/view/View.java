package com.budgetguardian.view;

import javafx.scene.Node;

/**
 * Contract for a top-level screen shown in the shell's content area.
 *
 * <p>Each view builds its own JavaFX node tree, subscribes to the
 * {@link com.budgetguardian.service.EventBus} for the changes it cares about,
 * and reads state from the {@code DataStore}. {@link #refresh()} rebuilds the
 * view's dynamic content and is called on navigation and on relevant events.</p>
 */
public interface View {

    /** @return short title shown in the nav rail and header. */
    String title();

    /** @return an icon glyph (emoji) for the nav rail. */
    String icon();

    /** @return the root node of this view. Stable across refreshes. */
    Node getNode();

    /** Rebuilds dynamic content from the current data-store state. */
    void refresh();
}
