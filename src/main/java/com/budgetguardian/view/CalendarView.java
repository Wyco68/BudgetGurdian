package com.budgetguardian.view;

import com.budgetguardian.service.DataStore;
import com.budgetguardian.service.EventType;
import com.budgetguardian.service.ServiceContext;
import com.budgetguardian.util.Money;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Calendar screen: a Monday-first month grid where each day cell shows its
 * total spending, colour-coded against the daily budget (green under, red
 * over). Prev/next buttons page months. Reads daily totals from the
 * {@code DataStore} in O(1) per day.
 */
public final class CalendarView implements View {

    private final ServiceContext services;
    private final DataStore store;
    private final Supplier<LocalDate> today;
    private final VBox root = new VBox(12);
    private final Label monthLabel = new Label();
    private final GridPane grid = new GridPane();
    private YearMonth current;

    public CalendarView(ServiceContext services, Supplier<LocalDate> today) {
        this.services = services;
        this.store = services.store();
        this.today = today;
        this.current = YearMonth.from(today.get());
        root.setPadding(new Insets(20));
        root.getStyleClass().add("view-root");
        grid.setHgap(8);
        grid.setVgap(8);
        root.getChildren().addAll(header(), grid);
        services.bus().subscribe(EventType.TRANSACTIONS_CHANGED, t -> refresh());
        refresh();
    }

    @Override
    public String title() {
        return "Calendar";
    }

    @Override
    public String icon() {
        return "📅";
    }

    @Override
    public Node getNode() {
        return root;
    }

    @Override
    public void refresh() {
        monthLabel.setText(current.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault())
                + " " + current.getYear());
        grid.getChildren().clear();
        String[] names = {"Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"};
        for (int c = 0; c < 7; c++) {
            Label head = new Label(names[c]);
            head.getStyleClass().add("card-heading");
            grid.add(head, c, 0);
        }
        long budget = services.settings().dailyBudgetSatang();
        LocalDate first = current.atDay(1);
        int leadingBlanks = first.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue();
        int row = 1;
        int col = leadingBlanks;
        for (int day = 1; day <= current.lengthOfMonth(); day++) {
            grid.add(dayCell(current.atDay(day), budget), col, row);
            col++;
            if (col == 7) {
                col = 0;
                row++;
            }
        }
    }

    private Node header() {
        Button prev = new Button("‹");
        prev.getStyleClass().add("button");
        prev.setOnAction(e -> {
            current = current.minusMonths(1);
            refresh();
        });
        Button next = new Button("›");
        next.getStyleClass().add("button");
        next.setOnAction(e -> {
            current = current.plusMonths(1);
            refresh();
        });
        Button todayButton = new Button("Today");
        todayButton.getStyleClass().add("button");
        todayButton.setOnAction(e -> {
            current = YearMonth.from(today.get());
            refresh();
        });
        monthLabel.getStyleClass().add("section-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, monthLabel, spacer, todayButton, prev, next);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private Node dayCell(LocalDate date, long budget) {
        long spent = store.dailyTotal(date);
        Label dayNumber = new Label(Integer.toString(date.getDayOfMonth()));
        dayNumber.getStyleClass().add("card-heading");
        Label amount = new Label(spent > 0 ? Money.formatPlain(spent) : "");
        amount.getStyleClass().add("mono");

        VBox cell = new VBox(4, dayNumber, amount);
        cell.setPadding(new Insets(8));
        cell.setPrefSize(150, 76);
        cell.getStyleClass().add("cal-cell");
        if (spent > budget) {
            cell.getStyleClass().add("cal-over");
        } else if (spent > 0) {
            cell.getStyleClass().add("cal-under");
        }
        if (date.equals(today.get())) {
            cell.getStyleClass().add("cal-today");
        }
        return cell;
    }
}
