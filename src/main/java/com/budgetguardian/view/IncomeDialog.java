package com.budgetguardian.view;

import com.budgetguardian.model.Account;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Modal add/edit dialog for income: account, amount, an optional reason and
 * a date — no category, no item name (those are expense-only fields).
 */
public final class IncomeDialog {

    private final DataStore store;
    private final Supplier<LocalDate> today;

    public IncomeDialog(DataStore store, Supplier<LocalDate> today) {
        this.store = store;
        this.today = today;
    }

    public Optional<Transaction> showCreate() {
        return show(null, null, null);
    }

    /** Opens the add dialog pre-filled with a computed amount (and optionally an account), e.g. from the calculator. */
    public Optional<Transaction> showCreate(Long prefillSatang, String preferredAccountId) {
        return show(null, prefillSatang, preferredAccountId);
    }

    public Optional<Transaction> showEdit(Transaction existing) {
        return show(existing, null, null);
    }

    private Optional<Transaction> show(Transaction existing, Long prefillSatang, String preferredAccountId) {
        Dialog<Transaction> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "New Income" : "Edit Income");
        ButtonType saveType = new ButtonType(existing == null ? "Add" : "Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        DialogSupport.applyStylesheet(dialog, IncomeDialog.class);

        ComboBox<Account> account = DialogSupport.accountCombo(store);
        TextField amount = new TextField();
        amount.setPromptText("0.00");
        TextField reason = new TextField();
        reason.setPromptText("reason (optional)");
        DatePicker date = new DatePicker(today.get());
        Label error = new Label();
        error.getStyleClass().add("note-danger");

        if (existing != null) {
            account.setValue(store.accounts().get(existing.accountId()));
            amount.setText(Money.formatPlain(existing.amountSatang()));
            reason.setText(existing.reason());
            date.setValue(existing.date());
        } else {
            if (prefillSatang != null) {
                amount.setText(Money.formatPlain(prefillSatang));
            }
            if (preferredAccountId != null) {
                account.setValue(store.accounts().get(preferredAccountId));
            }
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.addRow(0, new Label("Account"), account);
        grid.addRow(1, new Label("Amount (THB)"), DialogSupport.amountFieldWithCalculator(amount));
        grid.addRow(2, new Label("Reason"), reason);
        grid.addRow(3, new Label("Date"), date);
        grid.add(error, 1, 4);
        dialog.getDialogPane().setContent(grid);

        Transaction[] built = new Transaction[1];
        javafx.scene.Node saveButton = dialog.getDialogPane().lookupButton(saveType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                built[0] = build(existing, account.getValue(), amount.getText(), reason.getText(), date.getValue());
            } catch (RuntimeException e) {
                error.setText(e.getMessage());
                event.consume();
            }
        });

        dialog.setResultConverter(button -> button == saveType ? built[0] : null);
        return dialog.showAndWait();
    }

    private Transaction build(Transaction existing, Account account, String amountText,
                              String reasonText, LocalDate date) {
        if (account == null) {
            throw new IllegalArgumentException("Choose an account");
        }
        if (date == null) {
            throw new IllegalArgumentException("Choose a date");
        }
        long satang = Money.parse(amountText);
        String reason = reasonText == null ? "" : reasonText.trim();
        long id = existing != null ? existing.id() : 0;
        LocalDateTime createdAt = existing != null ? existing.createdAt() : LocalDateTime.now();
        return new Transaction(id, TransactionType.INCOME, account.id(), null, null,
                satang, reason, date, createdAt);
    }
}
