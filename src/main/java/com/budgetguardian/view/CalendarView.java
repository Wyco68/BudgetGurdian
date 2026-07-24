package com.budgetguardian.view;

import com.budgetguardian.datastructures.Iterator;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;
import com.budgetguardian.service.DataStore;
import com.budgetguardian.service.EventType;
import com.budgetguardian.service.ServiceContext;
import com.budgetguardian.util.Money;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
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
 * Calendar screen: a Monday-first month grid where each day cell shows
 * DailySpending and all-other-category spending as two separately coloured
 * lines; the cell background is colour-coded against the daily budget (which
 * only counts DailySpending) — green under, red over. Prev/next buttons page
 * months. Reads daily totals from the {@code DataStore} in O(1) per day.
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
        monthLabel.getStyleClass().add("page-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, monthLabel, spacer, todayButton, prev, next);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private Node dayCell(LocalDate date, long budget) {
        long spent = store.dailyTotal(date);
        long other = store.otherDailyTotal(date);
        Label dayNumber = new Label(Integer.toString(date.getDayOfMonth()));
        dayNumber.getStyleClass().add("card-heading");

        VBox cell = new VBox(4, dayNumber);
        if (spent > 0) {
            Label dailyLabel = new Label(Money.formatPlain(spent));
            dailyLabel.getStyleClass().add("cal-daily");
            cell.getChildren().add(dailyLabel);
        }
        if (other > 0) {
            Label otherLabel = new Label(Money.formatPlain(other));
            otherLabel.getStyleClass().add("cal-other");
            cell.getChildren().add(otherLabel);
        }
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
        cell.setOnMouseClicked(e -> showDayTransactions(date));
        return cell;
    }

    /** Opens a modal listing every transaction dated {@code date}, newest first. */
    private void showDayTransactions(LocalDate date) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Transactions — " + date.format(UiFormat.DATE));
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().getStylesheets().add(
                CalendarView.class.getResource("/css/styles.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("app-shell");

        VBox list = new VBox(4);
        list.setPadding(new Insets(12));
        long dayTotal = 0;
        int count = 0;
        Iterator<Transaction> it = store.ledger().descendingIterator();
        while (it.hasNext()) {
            Transaction txn = it.next();
            if (!txn.date().equals(date)) {
                continue;
            }
            list.getChildren().add(transactionRow(txn));
            if (txn.type() == TransactionType.EXPENSE) {
                dayTotal += txn.amountSatang();
            }
            count++;
        }
        if (count == 0) {
            Label empty = new Label("No transactions on this day.");
            empty.getStyleClass().add("muted");
            list.getChildren().add(empty);
        } else {
            Label total = new Label("Total spending: " + Money.format(dayTotal));
            total.getStyleClass().add("section-label");
            list.getChildren().add(total);
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(320);
        scroll.getStyleClass().add("edge-to-edge");
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().setPrefWidth(420);
        dialog.showAndWait();
    }

    private Node transactionRow(Transaction txn) {
        String sign = txn.type() == TransactionType.INCOME ? "+" : "−";
        Label left = new Label(txn.type().name().charAt(0) + " · "
                + UiFormat.categoryName(store, txn.categoryId())
                + (txn.itemName() != null ? " · " + txn.itemName() : "")
                + (txn.reason() != null && !txn.reason().isBlank() ? "  (" + txn.reason() + ")" : ""));
        Label right = new Label(sign + Money.formatPlain(txn.amountSatang()));
        right.getStyleClass().add(txn.type() == TransactionType.INCOME ? "amount-pos" : "amount-neg");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(8, left, spacer, right);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("list-row");
        return row;
    }
}
