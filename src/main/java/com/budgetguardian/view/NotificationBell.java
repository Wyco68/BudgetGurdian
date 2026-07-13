package com.budgetguardian.view;

import com.budgetguardian.datastructures.Iterator;
import com.budgetguardian.service.EventType;
import com.budgetguardian.service.Notification;
import com.budgetguardian.service.NotificationService;
import com.budgetguardian.service.ServiceContext;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Top-bar bell showing the reminder-queue count with a popover listing the
 * FIFO reminder queue and the recent hero-alert history. Wires the
 * {@code Queue} (reminders) and {@code CircularBuffer} (history) structures to
 * the UI. Rebinds on every {@code NOTIFICATION_RAISED} event.
 *
 * <p>Uses a lightweight custom popover (a floating panel) to avoid a
 * third-party control dependency.</p>
 */
public final class NotificationBell {

    private final NotificationService notifications;
    private final Button bell = new Button();
    private final PopOver popover = new PopOver();

    public NotificationBell(ServiceContext services) {
        this.notifications = services.notifications();
        bell.getStyleClass().add("bell-button");
        bell.setOnAction(e -> popover.toggle(bell, buildContent()));
        services.bus().subscribe(EventType.NOTIFICATION_RAISED, t -> refresh());
        refresh();
    }

    /** @return the bell button for the top bar. */
    public Button getNode() {
        return bell;
    }

    private void refresh() {
        int reminders = notifications.reminders().size();
        bell.setText(reminders > 0 ? "🔔 " + reminders : "🔔");
        if (popover.isShowing()) {
            popover.replaceContent(buildContent());
        }
    }

    private Region buildContent() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(14));
        box.getStyleClass().add("popover-content");
        box.setPrefWidth(320);

        box.getChildren().add(heading("Reminders"));
        if (notifications.reminders().isEmpty()) {
            box.getChildren().add(muted("No reminders."));
        } else {
            Iterator<Notification> it = notifications.reminders().iterator();
            while (it.hasNext()) {
                box.getChildren().add(row(it.next()));
            }
            Button clear = new Button("Dismiss all");
            clear.getStyleClass().add("button");
            clear.setOnAction(e -> {
                while (!notifications.reminders().isEmpty()) {
                    notifications.reminders().dequeue();
                }
                refresh();
                popover.replaceContent(buildContent());
            });
            box.getChildren().add(clear);
        }

        box.getChildren().add(heading("Recent Alerts"));
        if (notifications.history().isEmpty()) {
            box.getChildren().add(muted("No alerts yet."));
        } else {
            Iterator<Notification> it = notifications.history().newestFirst();
            while (it.hasNext()) {
                box.getChildren().add(row(it.next()));
            }
        }

        ScrollPane scroll = new ScrollPane(box);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(360);
        scroll.getStyleClass().add("edge-to-edge");
        return scroll;
    }

    private Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("card-heading");
        return label;
    }

    private Label muted(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted");
        return label;
    }

    private Region row(Notification n) {
        Label title = new Label(n.title());
        title.getStyleClass().add("list-title");
        Label message = new Label(n.message());
        message.getStyleClass().add("muted");
        message.setWrapText(true);
        VBox item = new VBox(2, title, message);
        item.getStyleClass().add("list-row");
        HBox.setHgrow(item, Priority.ALWAYS);
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }
}
