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
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
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
     *         calculator; "Use" writes the result back into {@code amount}.
     *         Replaces the standalone calculator screen — the calculator only
     *         ever exists to fill an amount field.
     */
    static HBox amountFieldWithCalculator(TextField amount) {
        Button calcButton = new Button("🧮");
        calcButton.getStyleClass().add("calc-button");
        PopOver popOver = new PopOver();

        calcButton.setOnAction(e -> popOver.toggleRightOf(calcButton, calculatorPad(amount, popOver)));

        HBox row = new HBox(8, amount, calcButton);
        HBox.setHgrow(amount, Priority.ALWAYS);
        amount.setMaxWidth(Double.MAX_VALUE);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    /** Keypad labels that map to a different character in the expression. */
    private static String keyToExpression(String key) {
        return switch (key) {
            case "×" -> "*";
            case "÷" -> "/";
            case "−" -> "-";
            default -> key;
        };
    }

    /**
     * Builds the calculator panel: a black display showing the running
     * expression and its evaluated result, over a phone-style keypad. The
     * expression is still typeable — the keys only append to the same field —
     * so keyboard entry and tapping stay interchangeable.
     */
    private static VBox calculatorPad(TextField amount, PopOver popOver) {
        CalculatorService calculator = new CalculatorService();

        TextField expression = new TextField();
        expression.setPromptText("0");
        expression.getStyleClass().add("calc-display-input");
        Label result = new Label("—");
        result.getStyleClass().add("calc-display-result");
        Label error = new Label();
        error.getStyleClass().add("calc-error");
        long[] lastSatang = {-1};

        Runnable evaluate = () -> {
            try {
                lastSatang[0] = calculator.evaluateToSatang(expression.getText());
                result.setText(Money.format(lastSatang[0]));
                error.setText("");
            } catch (BudgetException | IllegalArgumentException ex) {
                lastSatang[0] = -1;
                result.setText("—");
                error.setText(ex.getMessage());
            }
        };
        // Live preview while typing or tapping; the "=" key just forces it.
        expression.textProperty().addListener((obs, old, text) -> {
            if (text == null || text.isBlank()) {
                lastSatang[0] = -1;
                result.setText("—");
                error.setText("");
            } else {
                evaluate.run();
            }
        });
        expression.setOnAction(ev -> evaluate.run());

        VBox display = new VBox(4, expression, result, error);
        display.getStyleClass().add("calc-display");

        GridPane keys = new GridPane();
        keys.getStyleClass().add("calc-keys");
        keys.setHgap(8);
        keys.setVgap(8);
        String[][] layout = {
                {"C", "(", ")", "÷"},
                {"7", "8", "9", "×"},
                {"4", "5", "6", "−"},
                {"1", "2", "3", "+"},
                {"0", ".", "⌫", "="}
        };
        for (int r = 0; r < layout.length; r++) {
            for (int c = 0; c < layout[r].length; c++) {
                keys.add(key(layout[r][c], expression, evaluate), c, r);
            }
        }
        for (int c = 0; c < 4; c++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(25);
            column.setFillWidth(true);
            keys.getColumnConstraints().add(column);
        }

        Button use = new Button("Use amount");
        use.getStyleClass().add("calc-use");
        use.setMaxWidth(Double.MAX_VALUE);
        use.setOnAction(ev -> {
            if (lastSatang[0] < 0) {
                evaluate.run();
            }
            if (lastSatang[0] >= 0) {
                amount.setText(Money.formatPlain(lastSatang[0]));
                popOver.hide();
            }
        });

        VBox content = new VBox(10, display, keys, use);
        content.setPadding(new Insets(12));
        content.setPrefWidth(268);
        content.getStyleClass().add("calc-pad");
        return content;
    }

    /** One keypad button: digits/operators append, C clears, ⌫ backspaces, = evaluates. */
    private static Button key(String label, TextField expression, Runnable evaluate) {
        Button button = new Button(label);
        button.getStyleClass().add("calc-key");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setFocusTraversable(false);
        switch (label) {
            case "C" -> {
                button.getStyleClass().add("calc-key-clear");
                button.setOnAction(e -> expression.clear());
            }
            case "⌫" -> {
                button.getStyleClass().add("calc-key-op");
                button.setOnAction(e -> {
                    String text = expression.getText();
                    if (text != null && !text.isEmpty()) {
                        expression.setText(text.substring(0, text.length() - 1));
                    }
                });
            }
            case "=" -> {
                button.getStyleClass().add("calc-key-equals");
                button.setOnAction(e -> evaluate.run());
            }
            case "(", ")", "÷", "×", "−", "+" -> {
                button.getStyleClass().add("calc-key-op");
                button.setOnAction(e -> append(expression, keyToExpression(label)));
            }
            default -> button.setOnAction(e -> append(expression, label));
        }
        return button;
    }

    private static void append(TextField expression, String text) {
        expression.setText(expression.getText() == null ? text : expression.getText() + text);
        expression.positionCaret(expression.getText().length());
    }
}
