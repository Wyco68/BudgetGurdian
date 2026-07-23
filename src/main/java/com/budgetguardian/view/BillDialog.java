package com.budgetguardian.view;

import com.budgetguardian.model.Bill;
import com.budgetguardian.util.Money;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Modal dialog to create a bill: name, default amount and an optional
 * monthly payday. Leaving "recurring" unchecked creates a one-off bill that
 * never raises a due-soon reminder.
 */
public final class BillDialog {

    /** @return a new bill (id 0, never paid), or empty if cancelled. */
    public Optional<Bill> show() {
        Dialog<Bill> dialog = new Dialog<>();
        dialog.setTitle("New Bill");
        ButtonType saveType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        DialogSupport.applyStylesheet(dialog, BillDialog.class);

        TextField name = new TextField();
        name.setPromptText("e.g. Internet");
        TextField amount = new TextField();
        amount.setPromptText("0.00");
        CheckBox recurring = new CheckBox("Recurring monthly");
        Spinner<Integer> payday = new Spinner<>(1, 31, 1);
        payday.setEditable(true);
        payday.setDisable(true);
        recurring.selectedProperty().addListener((obs, old, value) -> payday.setDisable(!value));
        Label error = new Label();
        error.getStyleClass().add("note-danger");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));
        grid.addRow(0, new Label("Name"), name);
        grid.addRow(1, new Label("Amount (THB)"), amount);
        grid.addRow(2, recurring, payday);
        grid.add(error, 1, 3);
        dialog.getDialogPane().setContent(grid);

        Bill[] built = new Bill[1];
        javafx.scene.Node saveButton = dialog.getDialogPane().lookupButton(saveType);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                if (name.getText() == null || name.getText().isBlank()) {
                    throw new IllegalArgumentException("Name is required");
                }
                long satang = Money.parse(amount.getText());
                Integer day = recurring.isSelected() ? payday.getValue() : null;
                built[0] = new Bill(0, name.getText().trim(), satang, day, null, LocalDateTime.now());
            } catch (RuntimeException e) {
                error.setText(e.getMessage());
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button == saveType ? built[0] : null);
        return dialog.showAndWait();
    }
}
