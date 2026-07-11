package com.budgetguardian.view;

import com.budgetguardian.datastructures.DynamicArray;
import com.budgetguardian.model.Account;
import com.budgetguardian.model.Category;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;
import com.budgetguardian.service.DataStore;
import com.budgetguardian.util.Money;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Modal add/edit dialog for a transaction. Builds a valid {@link Transaction}
 * or nothing — all field validation happens here so services receive
 * well-formed input. Category is enabled only for expenses (income and
 * withdrawal carry none), enforcing the model rule at the UI too.
 */
public final class TransactionDialog {

    private final DataStore store;
    private final Supplier today;

    /** Minimal date supplier to avoid a java.util import in the signature. */
    @FunctionalInterface
    public interface Supplier {
        LocalDate get();
    }

    public TransactionDialog(DataStore store, Supplier today) {
        this.store = store;
        this.today = today;
    }

    /** Opens the dialog to create a new transaction. */
    public Optional<Transaction> showCreate() {
        return show(null);
    }

    /** Opens the dialog pre-filled to edit {@code existing}. */
    public Optional<Transaction> showEdit(Transaction existing) {
        return show(existing);
    }

    private Optional<Transaction> show(Transaction existing) {
        Dialog<Transaction> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "New Transaction" : "Edit Transaction");
        ButtonType saveType = new ButtonType(existing == null ? "Add" : "Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().getStylesheets().add(
                TransactionDialog.class.getResource("/css/styles.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("app-shell");

        ComboBox<TransactionType> type = new ComboBox<>();
        type.getItems().addAll(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.WITHDRAWAL);

        ComboBox<Account> account = new ComboBox<>();
        DynamicArray<Account> accounts = DashboardOrder.orderedAccounts(store);
        for (int i = 0; i < accounts.size(); i++) {
            account.getItems().add(accounts.get(i));
        }
        account.setConverter(accountConverter());

        ComboBox<Category> category = new ComboBox<>();
        for (int id = 1; id <= 64; id++) {
            Category c = store.categories().get(id);
            if (c != null) {
                category.getItems().add(c);
            }
        }
        category.setConverter(categoryConverter());

        TextField amount = new TextField();
        amount.setPromptText("0.00");
        TextField item = new TextField();
        item.setPromptText("item name (optional)");
        TextField reason = new TextField();
        reason.setPromptText("reason");
        DatePicker date = new DatePicker(today.get());
        Label error = new Label();
        error.getStyleClass().add("note-danger");

        type.valueProperty().addListener((obs, old, value) ->
                category.setDisable(value != TransactionType.EXPENSE));

        if (existing != null) {
            type.setValue(existing.type());
            account.setValue(store.accounts().get(existing.accountId()));
            category.setValue(existing.categoryId() != null ? store.categories().get(existing.categoryId()) : null);
            amount.setText(Money.formatPlain(existing.amountSatang()));
            item.setText(existing.itemName());
            reason.setText(existing.reason());
            date.setValue(existing.date());
        } else {
            type.setValue(TransactionType.EXPENSE);
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        int r = 0;
        grid.addRow(r++, new Label("Type"), type);
        grid.addRow(r++, new Label("Account"), account);
        grid.addRow(r++, new Label("Category"), category);
        grid.addRow(r++, new Label("Amount (THB)"), amount);
        grid.addRow(r++, new Label("Item"), item);
        grid.addRow(r++, new Label("Reason"), reason);
        grid.addRow(r++, new Label("Date"), date);
        grid.add(error, 1, r);
        dialog.getDialogPane().setContent(grid);

        // Holds the built transaction across the event filter and result converter.
        Transaction[] built = new Transaction[1];

        // Validate on the Save button; consume the event (dialog stays open) on failure.
        javafx.scene.Node saveButton = dialog.getDialogPane().lookupButton(saveType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                built[0] = build(existing, type.getValue(), account.getValue(), category.getValue(),
                        amount.getText(), item.getText(), reason.getText(), date.getValue());
            } catch (RuntimeException e) {
                error.setText(e.getMessage());
                event.consume();
            }
        });

        dialog.setResultConverter(button -> button == saveType ? built[0] : null);
        return dialog.showAndWait();
    }

    private Transaction build(Transaction existing, TransactionType type, Account account,
                              Category category, String amountText, String itemText,
                              String reasonText, LocalDate date) {
        if (type == null) {
            throw new IllegalArgumentException("Choose a type");
        }
        if (account == null) {
            throw new IllegalArgumentException("Choose an account");
        }
        if (date == null) {
            throw new IllegalArgumentException("Choose a date");
        }
        long satang = Money.parse(amountText);
        Integer categoryId = type == TransactionType.EXPENSE
                ? requireCategory(category)
                : null;
        String item = itemText == null || itemText.isBlank() ? null : itemText.trim();
        long id = existing != null ? existing.id() : 0;
        LocalDateTime createdAt = existing != null ? existing.createdAt() : LocalDateTime.now();
        return new Transaction(id, type, account.id(), categoryId,
                type == TransactionType.EXPENSE ? item : null,
                satang, reasonText == null ? "" : reasonText.trim(), date, createdAt);
    }

    private static Integer requireCategory(Category category) {
        if (category == null) {
            throw new IllegalArgumentException("Choose a category for an expense");
        }
        return category.id();
    }

    private StringConverter<Account> accountConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Account account) {
                return account == null ? "" : account.name();
            }

            @Override
            public Account fromString(String string) {
                return null;
            }
        };
    }

    private StringConverter<Category> categoryConverter() {
        return new StringConverter<>() {
            @Override
            public String toString(Category category) {
                return category == null ? "" : category.name();
            }

            @Override
            public Category fromString(String string) {
                return null;
            }
        };
    }
}
