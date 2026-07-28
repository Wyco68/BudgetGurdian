package com.budgetguardian.view;

import com.budgetguardian.datastructures.Iterator;
import com.budgetguardian.model.RefillItem;
import com.budgetguardian.service.EventType;
import com.budgetguardian.service.ServiceContext;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.function.Supplier;

/**
 * Refillable-items screen: every tracked item with its running-average
 * interval, last purchase and how long the current stock has lasted so far.
 * The next-expected (due) date is deliberately not shown — only the derived
 * overdue flag. Read-only projection of the refill {@code HashMap}.
 */
public final class RefillsView implements View {

    private final ServiceContext services;
    private final Supplier<LocalDate> today;
    private final VBox root = new VBox(12);
    private final ObservableList<RefillItem> rows = FXCollections.observableArrayList();
    private final TableView<RefillItem> table = new TableView<>(rows);

    public RefillsView(ServiceContext services, Supplier<LocalDate> today) {
        this.services = services;
        this.today = today;
        root.setPadding(new Insets(20));
        root.getStyleClass().add("view-root");
        Label title = new Label("Refillable Items");
        title.getStyleClass().add("page-title");
        buildTable();
        root.getChildren().addAll(title, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        services.bus().subscribe(EventType.REFILLS_CHANGED, t -> refresh());
        services.bus().subscribe(EventType.TRANSACTIONS_CHANGED, t -> refresh());
        refresh();
    }

    @Override
    public String title() {
        return "Refills";
    }

    @Override
    public String icon() {
        return "🔁";
    }

    @Override
    public Node getNode() {
        return root;
    }

    @Override
    public void refresh() {
        rows.clear();
        Iterator<RefillItem> it = services.store().refillItems().values();
        while (it.hasNext()) {
            rows.add(it.next());
        }
    }

    private void buildTable() {
        TableColumn<RefillItem, String> name = new TableColumn<>("Item");
        name.setCellValueFactory(c -> ro(c.getValue().name()));
        TableColumn<RefillItem, String> interval = new TableColumn<>("Interval (days)");
        interval.setCellValueFactory(c -> ro(String.format("%.1f", c.getValue().intervalDays())));
        TableColumn<RefillItem, String> count = new TableColumn<>("Purchases");
        count.setCellValueFactory(c -> ro(Integer.toString(c.getValue().purchaseCount())));
        TableColumn<RefillItem, String> last = new TableColumn<>("Last Purchase");
        last.setCellValueFactory(c -> ro(c.getValue().lastPurchase().format(UiFormat.DATE)));
        TableColumn<RefillItem, String> lasted = new TableColumn<>("Lasted");
        lasted.setCellValueFactory(c -> {
            long days = c.getValue().daysLasted(today.get());
            return ro(days + (days == 1 ? " day" : " days"));
        });
        TableColumn<RefillItem, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(c -> ro(c.getValue().isOverdue(today.get()) ? "⚠ Overdue" : "OK"));

        table.getColumns().add(name);
        table.getColumns().add(interval);
        table.getColumns().add(count);
        table.getColumns().add(last);
        table.getColumns().add(lasted);
        table.getColumns().add(status);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
        table.getStyleClass().add("data-table");
        table.setPlaceholder(new Label("No refillable items yet — record an expense under the Refill category."));
    }

    private static ObservableValue<String> ro(String value) {
        return new SimpleStringProperty(value);
    }
}
