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

        // Left column: brand header, the (growing) button list, then a footer
        // pinned to the bottom with keyboard-shortcut hints.
        VBox rail = new VBox();
        rail.getStyleClass().add("nav-rail");
        rail.setPadding(new Insets(16, 12, 14, 12));
        rail.getChildren().addAll(brandHeader(), navRail, railSpacer(), navFooter());
        VBox.setVgrow(navRail, Priority.ALWAYS);

        root.setLeft(rail);

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

    /** Brand block: a teal mark, the product name and a muted tagline. */
    private Region brandHeader() {
        Label mark = new Label("◆");
        mark.getStyleClass().add("brand-mark");
        Label name = new Label("Budget Guardian");
        name.getStyleClass().add("brand");
        HBox titleRow = new HBox(8, mark, name);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Label sub = new Label("Personal finance");
        sub.getStyleClass().add("brand-sub");

        VBox header = new VBox(2, titleRow, sub);
        header.setPadding(new Insets(4, 6, 16, 6));
        return header;
    }

    /** Flexible gap that pushes the footer to the bottom of the rail. */
    private Region railSpacer() {
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    /** Footer with the app's global keyboard shortcuts. */
    private Region navFooter() {
        VBox footer = new VBox(6,
                shortcutRow("Ctrl N", "New transaction"),
                shortcutRow("Ctrl F", "Search"),
                shortcutRow("Ctrl Z", "Undo"));
        footer.getStyleClass().add("nav-footer");
        return footer;
    }

    private HBox shortcutRow(String keys, String label) {
        Label kbd = new Label(keys);
        kbd.getStyleClass().add("kbd");
        Label text = new Label(label);
        HBox row = new HBox(8, kbd, text);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
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
