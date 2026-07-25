package com.budgetguardian.view;

import com.budgetguardian.datastructures.Iterator;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;
import com.budgetguardian.service.EventType;
import com.budgetguardian.service.ServiceContext;
import com.budgetguardian.util.Money;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Transactions screen: a newest-first list of transaction cards with add /
 * edit / delete. Each row uses the same clean list-row style the dashboard's
 * recent-transactions widget used, and clicking a row selects it (highlighted)
 * for edit or delete.
 *
 * <p>The list is rebuilt from the ledger's {@code descendingIterator} on every
 * relevant event — the {@code DoublyLinkedList} remains the source of truth.
 * After an expense with an item name is added, repeat-purchase tracking runs
 * silently — no confirmation prompt.</p>
 */
public final class TransactionsView implements View {

    private final ServiceContext services;
    private final Supplier<LocalDate> today;
    private final VBox root = new VBox(12);
    private final VBox list = new VBox(6);
    private long selectedId = -1;

    public TransactionsView(ServiceContext services, Supplier<LocalDate> today) {
        this.services = services;
        this.today = today;
        root.setPadding(new Insets(20));
        root.getStyleClass().add("view-root");
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        root.getChildren().addAll(header(), scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
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
        list.getChildren().clear();
        Iterator<Transaction> it = services.store().ledger().descendingIterator();
        boolean any = false;
        while (it.hasNext()) {
            any = true;
            list.getChildren().add(transactionCard(it.next()));
        }
        if (!any) {
            Label empty = new Label("No transactions yet — press Ctrl+N to add one.");
            empty.getStyleClass().add("muted");
            list.getChildren().add(empty);
        }
    }

    /** Opens the add-expense dialog; wired to the toolbar button and the Ctrl+N shortcut. */
    public void openAddDialog() {
        Optional<Transaction> created = new ExpenseDialog(services.store(), today::get).showCreate();
        created.ifPresent(txn -> {
            // Group the expense and any refill-item tracking it triggers into one
            // undo unit, so a single Ctrl+Z cancels the whole operation.
            int mark = services.undo().mark();
            Transaction saved = services.transactions().add(txn);
            services.refills().track(saved);
            services.undo().groupSince(mark);
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

    private Node transactionCard(Transaction txn) {
        String sign = txn.type() == TransactionType.INCOME ? "+" : "−";
        Label meta = new Label(txn.date().format(UiFormat.DATE) + "  ·  " + txn.type().name()
                + "  ·  " + UiFormat.accountName(services.store(), txn.accountId()));
        meta.getStyleClass().add("card-heading");
        String detail = UiFormat.categoryName(services.store(), txn.categoryId())
                + (txn.itemName() != null ? " · " + txn.itemName() : "")
                + (txn.reason() != null && !txn.reason().isBlank() ? "  (" + txn.reason() + ")" : "");
        Label detailLabel = new Label(detail);
        VBox left = new VBox(2, meta, detailLabel);

        Label right = new Label(sign + Money.formatPlain(txn.amountSatang()));
        right.getStyleClass().add(txn.type() == TransactionType.INCOME ? "amount-pos" : "amount-neg");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(10, left, spacer, right);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("list-row");
        if (txn.id() == selectedId) {
            row.getStyleClass().add("list-row-selected");
        }
        row.setOnMouseClicked(e -> {
            selectedId = txn.id();
            if (e.getClickCount() == 2) {
                editSelected();
            } else {
                refresh();
            }
        });
        return row;
    }

    private Transaction selected() {
        if (selectedId < 0) {
            return null;
        }
        Iterator<Transaction> it = services.store().ledger().iterator();
        while (it.hasNext()) {
            Transaction txn = it.next();
            if (txn.id() == selectedId) {
                return txn;
            }
        }
        return null;
    }

    private void editSelected() {
        Transaction selected = selected();
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
        Transaction selected = selected();
        if (selected == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete this transaction? You can undo with Ctrl+Z.", ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(button -> {
            if (button == ButtonType.OK) {
                services.transactions().delete(selected.id());
                selectedId = -1;
            }
        });
    }
}
