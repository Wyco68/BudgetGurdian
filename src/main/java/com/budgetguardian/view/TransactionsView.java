package com.budgetguardian.view;

import com.budgetguardian.datastructures.Iterator;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;
import com.budgetguardian.service.EventType;
import com.budgetguardian.service.ServiceContext;
import com.budgetguardian.util.Money;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Transactions screen: a newest-first table with add / edit / delete.
 *
 * <p>The table is fed an {@code ObservableList} rebuilt from the ledger's
 * {@code descendingIterator} on every relevant event — the
 * {@code DoublyLinkedList} remains the source of truth; the observable list is
 * a pure UI-boundary projection (the same interop JavaFX requires for
 * {@code TableView}, kept read-only and one-directional). After an expense
 * with an item name is added, repeat-purchase tracking runs silently — no
 * confirmation prompt.</p>
 */
public final class TransactionsView implements View {

    private final ServiceContext services;
    private final Supplier<LocalDate> today;
    private final VBox root = new VBox(12);
    private final ObservableList<Transaction> rows = FXCollections.observableArrayList();
    private final TableView<Transaction> table = new TableView<>(rows);

    public TransactionsView(ServiceContext services, Supplier<LocalDate> today) {
        this.services = services;
        this.today = today;
        root.setPadding(new Insets(20));
        root.getStyleClass().add("view-root");
        buildTable();
        root.getChildren().addAll(header(), table);
        VBox.setVgrow(table, Priority.ALWAYS);
        services.bus().subscribe(EventType.TRANSACTIONS_CHANGED, t -> refresh());
        refresh();
    }

    @Override
    public String title() {
        return "Transactions";
    }

    @Override
    public String icon() {
        return "🧾";
    }

    @Override
    public Node getNode() {
        return root;
    }

    @Override
    public void refresh() {
        rows.clear();
        Iterator<Transaction> it = services.store().ledger().descendingIterator();
        while (it.hasNext()) {
            rows.add(it.next());
        }
    }

    /** Opens the add-expense dialog; wired to the toolbar button and the Ctrl+N shortcut. */
    public void openAddDialog() {
        Optional<Transaction> created = new ExpenseDialog(services.store(), today::get).showCreate();
        created.ifPresent(txn -> {
            Transaction saved = services.transactions().add(txn);
            services.refills().track(saved);
        });
    }

    /** Opens the add-income dialog. */
    public void openAddIncomeDialog() {
        Optional<Transaction> created = new IncomeDialog(services.store(), today::get).showCreate();
        created.ifPresent(services.transactions()::add);
    }

    /** Opens the add-withdrawal dialog. */
    public void openAddWithdrawalDialog() {
        Optional<Transaction> created = new WithdrawalDialog(services.store(), today::get).showCreate();
        created.ifPresent(services.transactions()::add);
    }

    private Node header() {
        Label title = new Label("Transactions");
        title.getStyleClass().add("page-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addExpense = new Button("＋ Expense");
        addExpense.getStyleClass().add("primary-button");
        addExpense.setOnAction(e -> openAddDialog());
        Button addIncome = new Button("＋ Income");
        addIncome.setOnAction(e -> openAddIncomeDialog());
        Button addWithdrawal = new Button("＋ Withdrawal");
        addWithdrawal.setOnAction(e -> openAddWithdrawalDialog());
        Button edit = new Button("Edit");
        edit.setOnAction(e -> editSelected());
        Button delete = new Button("Delete");
        delete.getStyleClass().add("danger-button");
        delete.setOnAction(e -> deleteSelected());

        HBox bar = new HBox(8, title, spacer, addExpense, addIncome, addWithdrawal, edit, delete);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void buildTable() {
        TableColumn<Transaction, String> date = new TableColumn<>("Date");
        date.setCellValueFactory(c -> readOnly(c.getValue().date().format(UiFormat.DATE)));
        TableColumn<Transaction, String> type = new TableColumn<>("Type");
        type.setCellValueFactory(c -> readOnly(c.getValue().type().name()));
        TableColumn<Transaction, String> account = new TableColumn<>("Account");
        account.setCellValueFactory(c -> readOnly(UiFormat.accountName(services.store(), c.getValue().accountId())));
        TableColumn<Transaction, String> category = new TableColumn<>("Category");
        category.setCellValueFactory(c -> readOnly(UiFormat.categoryName(services.store(), c.getValue().categoryId())));
        TableColumn<Transaction, String> item = new TableColumn<>("Item");
        item.setCellValueFactory(c -> readOnly(c.getValue().itemName() == null ? "—" : c.getValue().itemName()));
        TableColumn<Transaction, String> reason = new TableColumn<>("Reason");
        reason.setCellValueFactory(c -> readOnly(c.getValue().reason()));
        TableColumn<Transaction, String> amount = new TableColumn<>("Amount");
        amount.setCellValueFactory(c -> {
            Transaction t = c.getValue();
            String sign = t.type() == TransactionType.INCOME ? "+" : "−";
            return readOnly(sign + Money.formatPlain(t.amountSatang()));
        });

        table.getColumns().add(date);
        table.getColumns().add(type);
        table.getColumns().add(account);
        table.getColumns().add(category);
        table.getColumns().add(item);
        table.getColumns().add(reason);
        table.getColumns().add(amount);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
        table.getStyleClass().add("data-table");
        table.setPlaceholder(new Label("No transactions yet — press Ctrl+N to add one."));
    }

    private void editSelected() {
        Transaction selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        Optional<Transaction> edited = switch (selected.type()) {
            case EXPENSE -> new ExpenseDialog(services.store(), today::get).showEdit(selected);
            case INCOME -> new IncomeDialog(services.store(), today::get).showEdit(selected);
            case WITHDRAWAL -> new WithdrawalDialog(services.store(), today::get).showEdit(selected);
        };
        edited.ifPresent(services.transactions()::edit);
    }

    private void deleteSelected() {
        Transaction selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete this transaction? You can undo with Ctrl+Z.", ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(button -> {
            if (button == ButtonType.OK) {
                services.transactions().delete(selected.id());
            }
        });
    }

    private static javafx.beans.value.ObservableValue<String> readOnly(String value) {
        return new javafx.beans.property.SimpleStringProperty(value);
    }
}
