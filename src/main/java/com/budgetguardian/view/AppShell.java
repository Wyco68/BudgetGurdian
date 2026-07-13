package com.budgetguardian.view;

import com.budgetguardian.datastructures.DynamicArray;
import com.budgetguardian.service.ServiceContext;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The application shell: a left navigation rail, a top hero-banner strip and
 * a swappable content area (BorderPane).
 *
 * <p>Views are registered once; clicking a rail button swaps the centre node
 * and refreshes that view. The shell owns no business logic — it only routes
 * navigation and hosts the {@link HeroBanner}.</p>
 */
public final class AppShell {

    private final BorderPane root = new BorderPane();
    private final VBox navRail = new VBox(4);
    private final DynamicArray<View> views = new DynamicArray<>();
    private final DynamicArray<Button> navButtons = new DynamicArray<>();

    public AppShell(ServiceContext services) {
        root.getStyleClass().add("app-shell");
        navRail.getStyleClass().add("nav-rail");
        navRail.setPadding(new Insets(16, 8, 16, 8));

        Label brand = new Label("Budget Guardian");
        brand.getStyleClass().add("brand");
        brand.setPadding(new Insets(4, 8, 16, 8));
        navRail.getChildren().add(brand);

        root.setLeft(navRail);

        // Top: a slim toolbar with the notification bell, above the hero banner.
        Region toolbarSpacer = new Region();
        HBox.setHgrow(toolbarSpacer, Priority.ALWAYS);
        HBox toolbar = new HBox(8, toolbarSpacer, new NotificationBell(services).getNode());
        toolbar.getStyleClass().add("top-bar");
        toolbar.setPadding(new Insets(8, 16, 8, 16));
        toolbar.setAlignment(Pos.CENTER_RIGHT);

        VBox top = new VBox(toolbar, new HeroBanner(services).getNode());
        root.setTop(top);
    }

    /** Registers a view and adds its nav-rail button. First registered is shown. */
    public void register(View view) {
        views.append(view);
        Button button = new Button(view.icon() + "  " + view.title());
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        final int index = views.size() - 1;
        button.setOnAction(e -> show(index));
        navButtons.append(button);
        navRail.getChildren().add(button);
        if (views.size() == 1) {
            show(0);
        }
    }

    /** Shows the view at {@code index}, refreshing it and marking its button active. */
    public void show(int index) {
        View view = views.get(index);
        view.refresh();
        root.setCenter(view.getNode());
        for (int i = 0; i < navButtons.size(); i++) {
            Button button = navButtons.get(i);
            button.getStyleClass().remove("nav-button-active");
            if (i == index) {
                button.getStyleClass().add("nav-button-active");
            }
        }
    }

    /** Shows a registered view by reference (no-op if not registered). */
    public void show(View view) {
        for (int i = 0; i < views.size(); i++) {
            if (views.get(i) == view) {
                show(i);
                return;
            }
        }
    }

    /** @return the shell root for the scene. */
    public Region getNode() {
        return root;
    }
}
