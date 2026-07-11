package com.budgetguardian.view;

import com.budgetguardian.service.EventType;
import com.budgetguardian.service.Notification;
import com.budgetguardian.service.NotificationService;
import com.budgetguardian.service.ServiceContext;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Top-of-window alert strip. Shows only the single highest-priority
 * notification — {@link NotificationService#heroBanner()} — and colours
 * itself by severity. Dismissing pops the banner, revealing the next.
 *
 * <p>Rebinds on every {@code NOTIFICATION_RAISED} event.</p>
 */
public final class HeroBanner {

    private final NotificationService notifications;
    private final HBox root = new HBox(12);
    private final Label title = new Label();
    private final Label message = new Label();

    public HeroBanner(ServiceContext services) {
        this.notifications = services.notifications();
        root.setAlignment(Pos.CENTER_LEFT);
        root.getStyleClass().add("hero-banner");
        title.getStyleClass().add("hero-title");
        message.getStyleClass().add("hero-message");

        Button dismiss = new Button("Dismiss");
        dismiss.getStyleClass().add("hero-dismiss");
        dismiss.setOnAction(e -> {
            if (notifications.heroBanner() != null) {
                notifications.dismiss();
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        root.getChildren().addAll(title, message, spacer, dismiss);

        services.bus().subscribe(EventType.NOTIFICATION_RAISED, t -> refresh());
        refresh();
    }

    /** @return the banner node (hidden and unmanaged when no alert is active). */
    public Region getNode() {
        return root;
    }

    private void refresh() {
        Notification banner = notifications.heroBanner();
        boolean visible = banner != null;
        root.setVisible(visible);
        root.setManaged(visible);
        root.getStyleClass().removeIf(c -> c.startsWith("hero-p"));
        if (visible) {
            title.setText(banner.title());
            message.setText(banner.message());
            root.getStyleClass().add(severityClass(banner.priority()));
        }
    }

    private static String severityClass(int priority) {
        if (priority >= 100) {
            return "hero-p100";
        }
        if (priority >= 90) {
            return "hero-p90";
        }
        if (priority >= 80) {
            return "hero-p80";
        }
        return "hero-p60";
    }
}
