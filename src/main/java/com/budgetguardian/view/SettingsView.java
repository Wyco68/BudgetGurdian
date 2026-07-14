package com.budgetguardian.view;

import com.budgetguardian.service.ServiceContext;
import com.budgetguardian.service.SettingsService;
import com.budgetguardian.util.Money;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Settings screen: edit the daily budget, danger weekly limit and reminder
 * time. Values are validated and saved through {@link SettingsService};
 * settings are preferences, not undoable data.
 */
public final class SettingsView implements View {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final ServiceContext services;
    private final VBox root = new VBox(16);
    private final TextField dailyBudget = new TextField();
    private final TextField dangerLimit = new TextField();
    private final TextField reminderTime = new TextField();
    private final Label status = new Label();

    public SettingsView(ServiceContext services) {
        this.services = services;
        root.setPadding(new Insets(20));
        root.getStyleClass().add("view-root");
        Label title = new Label("Settings");
        title.getStyleClass().add("page-title");
        status.getStyleClass().add("note-ok");
        root.getChildren().addAll(title, form());
        refresh();
    }

    @Override
    public String title() {
        return "Settings";
    }

    @Override
    public String icon() {
        return "⚙";
    }

    @Override
    public Node getNode() {
        return root;
    }

    @Override
    public void refresh() {
        dailyBudget.setText(Money.formatPlain(services.settings().dailyBudgetSatang()));
        dangerLimit.setText(Money.formatPlain(services.settings().dangerWeeklyLimitSatang()));
        reminderTime.setText(services.settings().reminderTime().format(HH_MM));
        status.setText("");
    }

    private Node form() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));
        grid.getStyleClass().add("card");
        grid.addRow(0, new Label("Daily Budget (THB)"), dailyBudget);
        grid.addRow(1, new Label("Danger Weekly Limit (THB)"), dangerLimit);
        grid.addRow(2, new Label("Reminder Time (HH:mm)"), reminderTime);

        Button save = new Button("Save");
        save.getStyleClass().add("primary-button");
        save.setOnAction(e -> save());
        grid.add(save, 1, 3);
        grid.add(status, 1, 4);
        return grid;
    }

    private void save() {
        try {
            long budget = Money.parse(dailyBudget.getText());
            long danger = Money.parse(dangerLimit.getText());
            LocalTime time = LocalTime.parse(reminderTime.getText().trim(), HH_MM);
            services.settings().set(SettingsService.DAILY_BUDGET, Long.toString(budget));
            services.settings().set(SettingsService.DANGER_WEEKLY_LIMIT, Long.toString(danger));
            services.settings().set(SettingsService.REMINDER_TIME, time.format(HH_MM));
            status.getStyleClass().setAll("note-ok");
            status.setText("Saved.");
        } catch (RuntimeException ex) {
            status.getStyleClass().setAll("note-danger");
            status.setText("Invalid input: " + ex.getMessage());
        }
    }
}
