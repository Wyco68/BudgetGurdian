package com.budgetguardian.view;

import com.budgetguardian.datastructures.Iterator;
import com.budgetguardian.model.Account;
import com.budgetguardian.model.Category;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;
import com.budgetguardian.service.DataStore;
import com.budgetguardian.service.TransactionService;
import com.budgetguardian.util.Money;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Modal add/edit dialog for a gambling result — the Gambling screen's own
 * entry point, so a win or a loss can be logged in one step instead of
 * remembering the right category and reason wording.
 *
 * <p>A win becomes an ordinary income row whose reason carries
 * {@link TransactionService#GAMBLING_WIN_REASON}; a loss becomes an expense in
 * the {@code Gamble} category. Nothing new is stored — the screen is a
 * projection of the same ledger, so existing "gambling win" income rows are
 * picked up without migration.</p>
 */
public final class GamblingDialog {

    /** Win / loss choice, kept as an enum so the combo can't hold a typo. */
    public enum Result {
        WIN("Win"), LOSS("Loss");

        private final String label;

        Result(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    private final DataStore store;
    private final Supplier<LocalDate> today;

    public GamblingDialog(DataStore store, Supplier<LocalDate> today) {
        this.store = store;
        this.today = today;
    }

    /** Opens the add dialog pre-set to {@code result}. */
    public Optional<Transaction> showCreate(Result result) {
        return show(null, result);
    }

    /** Opens the edit dialog for an existing win or loss row. */
    public Optional<Transaction> showEdit(Transaction existing, Result result) {
        return show(existing, result);
    }

    private Optional<Transaction> show(Transaction existing, Result initial) {
        Dialog<Transaction> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "Record Gambling Result" : "Edit Gambling Result");
        ButtonType saveType = new ButtonType(existing == null ? "Add" : "Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        DialogSupport.applyStylesheet(dialog, GamblingDialog.class);

        ComboBox<Result> result = new ComboBox<>();
        result.getItems().addAll(Result.WIN, Result.LOSS);
        result.setConverter(DialogSupport.converter(Result::label));
        result.setValue(initial);
        result.setMaxWidth(Double.MAX_VALUE);
        // Editing must not turn a win into a loss: that would change the row's
        // type and category, which the edit path does not support.
        result.setDisable(existing != null);

        ComboBox<Account> account = DialogSupport.accountCombo(store);
        account.setMaxWidth(Double.MAX_VALUE);
        TextField amount = new TextField();
        amount.setPromptText("0.00");
        TextField note = new TextField();
        note.setPromptText("note (optional)");
        note.setMaxWidth(Double.MAX_VALUE);
        DatePicker date = new DatePicker(today.get());
        date.setMaxWidth(Double.MAX_VALUE);
        Label error = new Label();
        error.getStyleClass().add("note-danger");

        if (existing != null) {
            account.setValue(store.accounts().get(existing.accountId()));
            amount.setText(Money.formatPlain(existing.amountSatang()));
            note.setText(stripMarker(existing.reason(), initial));
            date.setValue(existing.date());
        }

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        ColumnConstraints labelCol = new ColumnConstraints();
        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        fieldCol.setFillWidth(true);
        grid.getColumnConstraints().addAll(labelCol, fieldCol);
        grid.addRow(0, new Label("Result"), result);
        grid.addRow(1, new Label("Account"), account);
        grid.addRow(2, new Label("Amount (THB)"), DialogSupport.amountFieldWithCalculator(amount));
        grid.addRow(3, new Label("Note"), note);
        grid.addRow(4, new Label("Day"), date);
        grid.add(error, 1, 5);
        dialog.getDialogPane().setContent(grid);

        Transaction[] built = new Transaction[1];
        javafx.scene.Node saveButton = dialog.getDialogPane().lookupButton(saveType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                built[0] = build(existing, result.getValue(), account.getValue(),
                        amount.getText(), note.getText(), date.getValue());
            } catch (RuntimeException e) {
                error.setText(e.getMessage());
                event.consume();
            }
        });

        dialog.setResultConverter(button -> button == saveType ? built[0] : null);
        return dialog.showAndWait();
    }

    private Transaction build(Transaction existing, Result result, Account account,
                              String amountText, String noteText, LocalDate date) {
        if (result == null) {
            throw new IllegalArgumentException("Choose win or loss");
        }
        if (account == null) {
            throw new IllegalArgumentException("Choose an account");
        }
        if (date == null) {
            throw new IllegalArgumentException("Choose the day it happened");
        }
        long satang = Money.parse(amountText);
        if (satang <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
        String note = noteText == null ? "" : noteText.trim();
        long id = existing != null ? existing.id() : 0;
        LocalDateTime createdAt = existing != null ? existing.createdAt() : LocalDateTime.now();

        if (result == Result.WIN) {
            String reason = note.isBlank()
                    ? TransactionService.GAMBLING_WIN_REASON
                    : TransactionService.GAMBLING_WIN_REASON + " — " + note;
            return new Transaction(id, TransactionType.INCOME, account.id(), null, null,
                    satang, reason, date, createdAt);
        }
        return new Transaction(id, TransactionType.EXPENSE, account.id(), gambleCategoryId(), null,
                satang, note, date, createdAt);
    }

    /** @return the id of the seeded {@code Gamble} category. */
    private int gambleCategoryId() {
        Iterator<Category> it = store.categories().values();
        while (it.hasNext()) {
            Category category = it.next();
            if (category.name().equals(TransactionService.GAMBLE_CATEGORY_NAME)) {
                return category.id();
            }
        }
        throw new IllegalStateException("The 'Gamble' category is missing");
    }

    /** @return the free-text part of a win's reason, without the marker prefix. */
    private static String stripMarker(String reason, Result result) {
        if (reason == null || result != Result.WIN) {
            return reason;
        }
        String trimmed = reason.trim();
        if (trimmed.regionMatches(true, 0, TransactionService.GAMBLING_WIN_REASON, 0,
                TransactionService.GAMBLING_WIN_REASON.length())) {
            String rest = trimmed.substring(TransactionService.GAMBLING_WIN_REASON.length()).trim();
            return rest.startsWith("—") ? rest.substring(1).trim() : rest;
        }
        return trimmed;
    }
}
