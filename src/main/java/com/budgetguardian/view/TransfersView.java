package com.budgetguardian.view;

import com.budgetguardian.datastructures.Iterator;
import com.budgetguardian.model.Transfer;
import com.budgetguardian.service.EventType;
import com.budgetguardian.service.ServiceContext;
import com.budgetguardian.util.Money;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.function.Supplier;

/**
 * Transfers screen: a newest-first table of account-to-account transfers with
 * an add dialog. Transfers never touch spending totals — only balances and
 * the transfer graph. Rows are a read-only projection of the transfer
 * {@code DoublyLinkedList}.
 */
public final class TransfersView implements View {

    private final ServiceContext services;
    private final Supplier<LocalDate> today;
    private final VBox root = new VBox(12);
    private final ObservableList<Transfer> rows = FXCollections.observableArrayList();
    private final TableView<Transfer> table = new TableView<>(rows);

    public TransfersView(ServiceContext services, Supplier<LocalDate> today) {
        this.services = services;
        this.today = today;
        root.setPadding(new Insets(20));
        root.getStyleClass().add("view-root");
        buildTable();
        root.getChildren().addAll(header(), table);
        VBox.setVgrow(table, Priority.ALWAYS);
        services.bus().subscribe(EventType.TRANSFERS_CHANGED, t -> refresh());
        refresh();
    }

    @Override
    public String title() {
        return "Transfers";
    }

    @Override
    public String icon() {
        return "🔀";
    }

    @Override
    public Node getNode() {
        return root;
    }

    @Override
    public void refresh() {
        rows.clear();
        Iterator<Transfer> it = services.store().transfers().descendingIterator();
        while (it.hasNext()) {
            rows.add(it.next());
        }
    }

    private Node header() {
        Label title = new Label("Transfers");
        title.getStyleClass().add("page-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button add = new Button("＋ New Transfer");
        add.getStyleClass().add("primary-button");
        add.setOnAction(e -> openAddDialog());
        HBox bar = new HBox(8, title, spacer, add);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void openAddDialog() {
        new TransferDialog(services.store(), today).show()
                .ifPresent(services.transfers()::add);
    }

    private void buildTable() {
        TableColumn<Transfer, String> date = new TableColumn<>("Date");
        date.setCellValueFactory(c -> ro(c.getValue().date().format(UiFormat.DATE)));
        TableColumn<Transfer, String> from = new TableColumn<>("From");
        from.setCellValueFactory(c -> ro(UiFormat.accountName(services.store(), c.getValue().fromAccount())));
        TableColumn<Transfer, String> to = new TableColumn<>("To");
        to.setCellValueFactory(c -> ro(UiFormat.accountName(services.store(), c.getValue().toAccount())));
        TableColumn<Transfer, String> amount = new TableColumn<>("Amount");
        amount.setCellValueFactory(c -> ro(Money.formatPlain(c.getValue().amountSatang())));
        TableColumn<Transfer, String> reason = new TableColumn<>("Reason");
        reason.setCellValueFactory(c -> ro(c.getValue().reason()));

        table.getColumns().add(date);
        table.getColumns().add(from);
        table.getColumns().add(to);
        table.getColumns().add(amount);
        table.getColumns().add(reason);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
        table.getStyleClass().add("data-table");
        table.setPlaceholder(new Label("No transfers yet."));
    }

    private static ObservableValue<String> ro(String value) {
        return new SimpleStringProperty(value);
    }
}
