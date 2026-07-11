package com.budgetguardian.view;

import com.budgetguardian.datastructures.DynamicArray;
import com.budgetguardian.model.Account;
import com.budgetguardian.model.Transfer;
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
import java.util.function.Supplier;

/**
 * Modal dialog to create a transfer between two accounts. Enforces distinct
 * endpoints and a positive amount before returning a valid {@link Transfer}.
 */
public final class TransferDialog {

    private final DataStore store;
    private final Supplier<LocalDate> today;

    public TransferDialog(DataStore store, Supplier<LocalDate> today) {
        this.store = store;
        this.today = today;
    }

    /** @return a new transfer, or empty if cancelled. */
    public Optional<Transfer> show() {
        Dialog<Transfer> dialog = new Dialog<>();
        dialog.setTitle("New Transfer");
        ButtonType saveType = new ButtonType("Transfer", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().getStylesheets().add(
                TransferDialog.class.getResource("/css/styles.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("app-shell");

        ComboBox<Account> from = accountBox();
        ComboBox<Account> to = accountBox();
        TextField amount = new TextField();
        amount.setPromptText("0.00");
        TextField reason = new TextField();
        reason.setPromptText("e.g. ATM withdrawal");
        DatePicker date = new DatePicker(today.get());
        Label error = new Label();
        error.getStyleClass().add("note-danger");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.addRow(0, new Label("From"), from);
        grid.addRow(1, new Label("To"), to);
        grid.addRow(2, new Label("Amount (THB)"), amount);
        grid.addRow(3, new Label("Reason"), reason);
        grid.addRow(4, new Label("Date"), date);
        grid.add(error, 1, 5);
        dialog.getDialogPane().setContent(grid);

        Transfer[] built = new Transfer[1];
        javafx.scene.Node saveButton = dialog.getDialogPane().lookupButton(saveType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                built[0] = build(from.getValue(), to.getValue(), amount.getText(),
                        reason.getText(), date.getValue());
            } catch (RuntimeException e) {
                error.setText(e.getMessage());
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button == saveType ? built[0] : null);
        return dialog.showAndWait();
    }

    private Transfer build(Account from, Account to, String amountText, String reasonText, LocalDate date) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("Choose both accounts");
        }
        if (from.id().equals(to.id())) {
            throw new IllegalArgumentException("From and To must differ");
        }
        if (date == null) {
            throw new IllegalArgumentException("Choose a date");
        }
        if (reasonText == null || reasonText.isBlank()) {
            throw new IllegalArgumentException("Reason is required");
        }
        long satang = Money.parse(amountText);
        return new Transfer(0, from.id(), to.id(), satang, reasonText.trim(), date, LocalDateTime.now());
    }

    private ComboBox<Account> accountBox() {
        ComboBox<Account> box = new ComboBox<>();
        DynamicArray<Account> accounts = DashboardOrder.orderedAccounts(store);
        for (int i = 0; i < accounts.size(); i++) {
            box.getItems().add(accounts.get(i));
        }
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(Account account) {
                return account == null ? "" : account.name();
            }

            @Override
            public Account fromString(String string) {
                return null;
            }
        });
        return box;
    }
}
