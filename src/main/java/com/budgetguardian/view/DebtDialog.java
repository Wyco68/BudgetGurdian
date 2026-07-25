package com.budgetguardian.view;

import com.budgetguardian.model.Debt;
import com.budgetguardian.model.DebtDirection;
import com.budgetguardian.model.DebtStatus;
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
 * Modal dialog to create a debt (payable or receivable) with an optional due
 * date. Returns a valid open {@link Debt} or nothing.
 */
public final class DebtDialog {

    private final Supplier<LocalDate> today;

    public DebtDialog(Supplier<LocalDate> today) {
        this.today = today;
    }

    /** @return a new open debt, or empty if cancelled. */
    public Optional<Debt> show() {
        Dialog<Debt> dialog = new Dialog<>();
        dialog.setTitle("New Debt");
        ButtonType saveType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        dialog.getDialogPane().getStylesheets().add(
                DebtDialog.class.getResource("/css/styles.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("app-shell");

        ComboBox<DebtDirection> direction = new ComboBox<>();
        direction.getItems().addAll(DebtDirection.PAYABLE, DebtDirection.RECEIVABLE);
        direction.setValue(DebtDirection.PAYABLE);
        TextField person = new TextField();
        person.setPromptText("counterparty");
        TextField amount = new TextField();
        amount.setPromptText("0.00");
        DatePicker occurred = new DatePicker(today.get());
        DatePicker due = new DatePicker();
        due.setPromptText("optional");
        Label error = new Label();
        error.getStyleClass().add("note-danger");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.addRow(0, new Label("Direction"), direction);
        grid.addRow(1, new Label("Person"), person);
        grid.addRow(2, new Label("Amount (THB)"), amount);
        grid.addRow(3, new Label("Date happened"), occurred);
        grid.addRow(4, new Label("Due date"), due);
        grid.add(error, 1, 5);
        dialog.getDialogPane().setContent(grid);

        Debt[] built = new Debt[1];
        javafx.scene.Node saveButton = dialog.getDialogPane().lookupButton(saveType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                if (person.getText() == null || person.getText().isBlank()) {
                    throw new IllegalArgumentException("Person is required");
                }
                long satang = Money.parse(amount.getText());
                built[0] = new Debt(0, direction.getValue(), person.getText().trim(), satang,
                        occurred.getValue(), due.getValue(), DebtStatus.OPEN, null, LocalDateTime.now());
            } catch (RuntimeException e) {
                error.setText(e.getMessage());
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button == saveType ? built[0] : null);
        return dialog.showAndWait();
    }
}
