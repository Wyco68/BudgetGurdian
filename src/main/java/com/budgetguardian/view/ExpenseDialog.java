package com.budgetguardian.view;

import com.budgetguardian.datastructures.Iterator;
import com.budgetguardian.model.Account;
import com.budgetguardian.model.Category;
import com.budgetguardian.model.RefillItem;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;
import com.budgetguardian.service.DataStore;
import com.budgetguardian.service.RefillService;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Modal add/edit dialog for an expense: the only transaction type that
 * carries a category and an item name. Reason is required only when the
 * chosen category is Extra — every other category leaves it optional. When
 * the Refill category is chosen, the item field becomes a dropdown of
 * already-tracked refill items plus an "Add new item" option that reveals a
 * free-text field.
 */
public final class ExpenseDialog {

    private static final String ADD_NEW_ITEM = "+ Add new item";

    private final DataStore store;
    private final Supplier<LocalDate> today;

    public ExpenseDialog(DataStore store, Supplier<LocalDate> today) {
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
        dialog.setTitle(existing == null ? "New Expense" : "Edit Expense");
        ButtonType saveType = new ButtonType(existing == null ? "Add" : "Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        DialogSupport.applyStylesheet(dialog, ExpenseDialog.class);

        ComboBox<Account> account = DialogSupport.accountCombo(store);

        ComboBox<Category> category = new ComboBox<>();
        for (int id = 1; id <= 64; id++) {
            Category c = store.categories().get(id);
            if (c != null) {
                category.getItems().add(c);
            }
        }
        category.setConverter(DialogSupport.converter(Category::name));

        TextField amount = new TextField();
        amount.setPromptText("0.00");
        TextField item = new TextField();
        item.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(item, Priority.ALWAYS);
        ComboBox<String> refillItemChoice = new ComboBox<>();
        refillItemChoice.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(refillItemChoice, Priority.ALWAYS);
        HBox itemRow = new HBox(8, refillItemChoice, item);
        itemRow.setMaxWidth(Double.MAX_VALUE);
        account.setMaxWidth(Double.MAX_VALUE);
        category.setMaxWidth(Double.MAX_VALUE);
        TextField reason = new TextField();
        reason.setMaxWidth(Double.MAX_VALUE);
        DatePicker date = new DatePicker(today.get());
        date.setMaxWidth(Double.MAX_VALUE);
        Label reasonLabel = new Label("Reason");
        Label error = new Label();
        error.getStyleClass().add("note-danger");

        Runnable updateReasonPrompt = () -> {
            boolean requiresReason = category.getValue() != null
                    && category.getValue().name().equals(TransactionService.EXTRA_CATEGORY_NAME);
            reasonLabel.setText(requiresReason ? "Reason (required)" : "Reason (optional)");
            reason.setPromptText(requiresReason ? "required for Extra" : "optional");
        };

        Runnable populateRefillChoices = () -> {
            String previous = refillItemChoice.getValue();
            refillItemChoice.getItems().clear();
            List<String> names = new ArrayList<>();
            Iterator<RefillItem> it = store.refillItems().values();
            while (it.hasNext()) {
                names.add(it.next().name());
            }
            Collections.sort(names);
            refillItemChoice.getItems().addAll(names);
            refillItemChoice.getItems().add(ADD_NEW_ITEM);
            refillItemChoice.setValue(previous != null && refillItemChoice.getItems().contains(previous)
                    ? previous : ADD_NEW_ITEM);
        };

        Runnable updateItemMode = () -> {
            boolean isRefill = category.getValue() != null
                    && category.getValue().name().equals(TransactionService.REFILL_CATEGORY_NAME);
            refillItemChoice.setVisible(isRefill);
            refillItemChoice.setManaged(isRefill);
            boolean showTextField = !isRefill || ADD_NEW_ITEM.equals(refillItemChoice.getValue());
            item.setVisible(showTextField);
            item.setManaged(showTextField);
            item.setPromptText(isRefill ? "new item name" : "item name (optional)");
        };

        category.valueProperty().addListener((obs, old, value) -> {
            updateReasonPrompt.run();
            if (value != null && value.name().equals(TransactionService.REFILL_CATEGORY_NAME)) {
                populateRefillChoices.run();
            }
            updateItemMode.run();
        });
        refillItemChoice.valueProperty().addListener((obs, old, value) -> updateItemMode.run());

        if (existing != null) {
            account.setValue(store.accounts().get(existing.accountId()));
            Category existingCategory = existing.categoryId() != null ? store.categories().get(existing.categoryId()) : null;
            category.setValue(existingCategory);
            amount.setText(Money.formatPlain(existing.amountSatang()));
            reason.setText(existing.reason());
            date.setValue(existing.date());
            boolean isRefill = existingCategory != null
                    && existingCategory.name().equals(TransactionService.REFILL_CATEGORY_NAME);
            if (isRefill && existing.itemName() != null) {
                populateRefillChoices.run();
                String normalized = RefillService.normalize(existing.itemName());
                if (refillItemChoice.getItems().contains(normalized)) {
                    refillItemChoice.setValue(normalized);
                } else {
                    refillItemChoice.setValue(ADD_NEW_ITEM);
                    item.setText(existing.itemName());
                }
            } else {
                item.setText(existing.itemName());
            }
        } else {
            if (prefillSatang != null) {
                amount.setText(Money.formatPlain(prefillSatang));
            }
            if (preferredAccountId != null) {
                account.setValue(store.accounts().get(preferredAccountId));
            }
            item.setPromptText("item name (optional)");
        }
        updateReasonPrompt.run();
        updateItemMode.run();

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        ColumnConstraints labelCol = new ColumnConstraints();
        ColumnConstraints fieldCol = new ColumnConstraints();
        fieldCol.setHgrow(Priority.ALWAYS);
        fieldCol.setFillWidth(true);
        grid.getColumnConstraints().addAll(labelCol, fieldCol);
        int r = 0;
        grid.addRow(r++, new Label("Account"), account);
        grid.addRow(r++, new Label("Category"), category);
        grid.addRow(r++, new Label("Amount (THB)"), DialogSupport.amountFieldWithCalculator(amount));
        grid.addRow(r++, new Label("Item"), itemRow);
        grid.addRow(r++, reasonLabel, reason);
        grid.addRow(r++, new Label("Date"), date);
        grid.add(error, 1, r);
        dialog.getDialogPane().setContent(grid);

        Transaction[] built = new Transaction[1];
        javafx.scene.Node saveButton = dialog.getDialogPane().lookupButton(saveType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                boolean isRefill = category.getValue() != null
                        && category.getValue().name().equals(TransactionService.REFILL_CATEGORY_NAME);
                String effectiveItem = isRefill && !ADD_NEW_ITEM.equals(refillItemChoice.getValue())
                        && refillItemChoice.getValue() != null
                        ? refillItemChoice.getValue()
                        : item.getText();
                built[0] = build(existing, account.getValue(), category.getValue(),
                        amount.getText(), effectiveItem, reason.getText(), date.getValue());
            } catch (RuntimeException e) {
                error.setText(e.getMessage());
                event.consume();
            }
        });

        dialog.setResultConverter(button -> button == saveType ? built[0] : null);
        return dialog.showAndWait();
    }

    private Transaction build(Transaction existing, Account account, Category category,
                              String amountText, String itemText, String reasonText, LocalDate date) {
        if (account == null) {
            throw new IllegalArgumentException("Choose an account");
        }
        if (category == null) {
            throw new IllegalArgumentException("Choose a category");
        }
        if (date == null) {
            throw new IllegalArgumentException("Choose a date");
        }
        long satang = Money.parse(amountText);
        boolean isExtra = category.name().equals(TransactionService.EXTRA_CATEGORY_NAME);
        String reason = reasonText == null ? "" : reasonText.trim();
        if (isExtra && reason.isBlank()) {
            throw new IllegalArgumentException("Extra expenses require a reason");
        }
        String item = itemText == null || itemText.isBlank() ? null : itemText.trim();
        long id = existing != null ? existing.id() : 0;
        LocalDateTime createdAt = existing != null ? existing.createdAt() : LocalDateTime.now();
        return new Transaction(id, TransactionType.EXPENSE, account.id(), category.id(),
                item, satang, reason, date, createdAt);
    }
}
