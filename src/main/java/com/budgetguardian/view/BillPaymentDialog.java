package com.budgetguardian.view;

import com.budgetguardian.model.Account;
import com.budgetguardian.model.Bill;
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
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Modal dialog to pay a bill: which account, how much (defaulting to the
 * bill's amount) and when. Returns the chosen fields; the caller invokes
 * {@code BillService.pay}.
 */
public final class BillPaymentDialog {

    /**
     * @param accountId    account the money moves on
     * @param amountSatang payment amount in satang
     * @param date         day of the payment
     * @param reason       optional note
     */
    public record Result(String accountId, long amountSatang, LocalDate date, String reason) {
    }

    private final DataStore store;
    private final Supplier<LocalDate> today;

    public BillPaymentDialog(DataStore store, Supplier<LocalDate> today) {
        this.store = store;
        this.today = today;
    }

    public Optional<Result> show(Bill bill) {
        Dialog<Result> dialog = new Dialog<>();
        dialog.setTitle("Pay " + bill.name());
        ButtonType saveType = new ButtonType("Pay", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        DialogSupport.applyStylesheet(dialog, BillPaymentDialog.class);

        ComboBox<Account> account = DialogSupport.accountCombo(store);
        TextField amount = new TextField(Money.formatPlain(bill.amountSatang()));
        TextField reason = new TextField();
        reason.setPromptText("reason (optional)");
        DatePicker date = new DatePicker(today.get());
        Label error = new Label();
        error.getStyleClass().add("note-danger");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.addRow(0, new Label("From account"), account);
        grid.addRow(1, new Label("Amount (THB)"), amount);
        grid.addRow(2, new Label("Reason"), reason);
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
                built[0] = new Result(account.getValue().id(), satang, date.getValue(), reason.getText());
            } catch (RuntimeException e) {
                error.setText(e.getMessage());
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button == saveType ? built[0] : null);
        return dialog.showAndWait();
    }
}
