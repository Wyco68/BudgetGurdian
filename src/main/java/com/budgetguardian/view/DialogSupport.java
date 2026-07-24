package com.budgetguardian.view;

import com.budgetguardian.datastructures.DynamicArray;
import com.budgetguardian.model.Account;
import com.budgetguardian.service.BudgetException;
import com.budgetguardian.service.CalculatorService;
import com.budgetguardian.service.DataStore;
import com.budgetguardian.util.Money;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.function.Function;

/** Shared field-building helpers for the transaction-family modal dialogs. */
final class DialogSupport {

    private DialogSupport() {
    }

    /** @return an account combo box populated in dashboard order. */
    static ComboBox<Account> accountCombo(DataStore store) {
        ComboBox<Account> combo = new ComboBox<>();
        DynamicArray<Account> accounts = DashboardOrder.orderedAccounts(store);
        for (int i = 0; i < accounts.size(); i++) {
            combo.getItems().add(accounts.get(i));
        }
        combo.setConverter(converter(Account::name));
        return combo;
    }

    /** @return a display-name-only converter (no parse-back; combo boxes are selection-only). */
    static <T> StringConverter<T> converter(Function<T, String> name) {
        return new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : name.apply(value);
            }

            @Override
            public T fromString(String string) {
                return null;
            }
        };
    }

    /** Uniform width for the transaction-family modals, so switching type or
     *  category never resizes the dialog. */
    static final double DIALOG_WIDTH = 460;

    /**
     * Applies the app stylesheet, shell style class and the shared fixed width
     * every transaction-family dialog pane needs — a stable footprint means
     * showing/hiding a field (e.g. the refill dropdown) can't change the
     * modal's size.
     */
    static void applyStylesheet(Dialog<?> dialog, Class<?> owner) {
        dialog.getDialogPane().getStylesheets().add(owner.getResource("/css/styles.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("app-shell");
        dialog.getDialogPane().setPrefWidth(DIALOG_WIDTH);
        dialog.getDialogPane().setMinWidth(DIALOG_WIDTH);
    }

    /**
     * @return {@code amount} paired with a "🧮" button that opens an inline
     *         expression calculator; "Use" writes the result back into
     *         {@code amount}. Replaces the standalone calculator screen —
     *         the calculator only ever exists to fill an amount field.
     */
    static HBox amountFieldWithCalculator(TextField amount) {
        Button calcButton = new Button("🧮");
        calcButton.getStyleClass().add("calc-button");
        PopOver popOver = new PopOver();
        CalculatorService calculator = new CalculatorService();

        calcButton.setOnAction(e -> {
            TextField expression = new TextField();
            expression.setPromptText("e.g. 120 + 45.50");
            Label result = new Label("—");
            result.getStyleClass().add("stat-med");
            Label error = new Label();
            error.getStyleClass().add("note-danger");
            long[] lastSatang = {-1};

            Button equals = new Button("=");
            equals.getStyleClass().add("primary-button");
            Runnable evaluate = () -> {
                try {
                    lastSatang[0] = calculator.evaluateToSatang(expression.getText());
                    result.setText(Money.format(lastSatang[0]));
                    error.setText("");
                } catch (BudgetException ex) {
                    error.setText(ex.getMessage());
                }
            };
            equals.setOnAction(ev -> evaluate.run());
            expression.setOnAction(ev -> evaluate.run());

            Button use = new Button("Use");
            use.getStyleClass().add("button");
            use.setOnAction(ev -> {
                if (lastSatang[0] >= 0) {
                    amount.setText(Money.formatPlain(lastSatang[0]));
                }
                popOver.hide();
            });

            HBox actions = new HBox(8, equals, use);
            VBox content = new VBox(8, expression, result, actions, error);
            content.setPadding(new Insets(12));
            content.setPrefWidth(240);
            content.getStyleClass().add("popover-content");
            popOver.toggleRightOf(calcButton, content);
        });

        HBox row = new HBox(8, amount, calcButton);
        HBox.setHgrow(amount, Priority.ALWAYS);
        amount.setMaxWidth(Double.MAX_VALUE);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }
}
