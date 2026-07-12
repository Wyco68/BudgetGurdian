package com.budgetguardian.view;

import com.budgetguardian.datastructures.DynamicArray;
import com.budgetguardian.model.Account;
import com.budgetguardian.model.Debt;
import com.budgetguardian.model.DebtDirection;
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
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Modal dialog to record a partial payment (or receipt) on a debt. Asks which
 * account the money moves on and how much, defaulting the amount to the
 * remaining balance. Returns the chosen {@code (account, amount, date)} as a
 * {@link Result}; the caller invokes {@code DebtService.pay}.
 */
public final class PaymentDialog {

    /**
     * @param accountId   account the money moves on
     * @param amountSatang payment amount in satang
     * @param date        day of the payment
     */
    public record Result(String accountId, long amountSatang, LocalDate date) {
    }

    private final DataStore store;
    private final Supplier<LocalDate> today;

    public PaymentDialog(DataStore store, Supplier<LocalDate> today) {
        this.store = store;
        this.today = today;
    }

    /**
     * @param debt      the debt being paid
     * @param remaining current remaining satang (used as default and cap)
     */
    public Optional<Result> show(Debt debt, long remaining) {
        Dialog<Result> dialog = new Dialog<>();
        boolean receive = debt.direction() == DebtDirection.RECEIVABLE;
        dialog.setTitle(receive ? "Receive Payment" : "Make Payment");
        ButtonType saveType = new ButtonType(receive ? "Receive" : "Pay", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().getStylesheets().add(
                PaymentDialog.class.getResource("/css/styles.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("app-shell");

        ComboBox<Account> account = new ComboBox<>();
        DynamicArray<Account> accounts = DashboardOrder.orderedAccounts(store);
        for (int i = 0; i < accounts.size(); i++) {
            account.getItems().add(accounts.get(i));
        }
        account.setConverter(new StringConverter<>() {
            @Override
            public String toString(Account a) {
                return a == null ? "" : a.name();
            }

            @Override
            public Account fromString(String s) {
                return null;
            }
        });

        TextField amount = new TextField(Money.formatPlain(remaining));
        DatePicker date = new DatePicker(today.get());
        Label info = new Label((receive ? "To receive: " : "To pay: ") + debt.person()
                + "  ·  remaining " + Money.format(remaining));
        info.getStyleClass().add("muted");
        Label error = new Label();
        error.getStyleClass().add("note-danger");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.add(info, 0, 0, 2, 1);
        grid.addRow(1, new Label(receive ? "Into account" : "From account"), account);
        grid.addRow(2, new Label("Amount (THB)"), amount);
        grid.addRow(3, new Label("Date"), date);
        grid.add(error, 1, 4);
        dialog.getDialogPane().setContent(grid);

        Result[] built = new Result[1];
        javafx.scene.Node saveButton = dialog.getDialogPane().lookupButton(saveType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                if (account.getValue() == null) {
                    throw new IllegalArgumentException("Choose an account");
                }
                if (date.getValue() == null) {
                    throw new IllegalArgumentException("Choose a date");
                }
                long satang = Money.parse(amount.getText());
                if (satang > remaining) {
                    throw new IllegalArgumentException("Amount exceeds remaining " + Money.format(remaining));
                }
                built[0] = new Result(account.getValue().id(), satang, date.getValue());
            } catch (RuntimeException e) {
                error.setText(e.getMessage());
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button == saveType ? built[0] : null);
        return dialog.showAndWait();
    }
}
