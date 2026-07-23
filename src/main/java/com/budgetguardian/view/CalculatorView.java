package com.budgetguardian.view;

import com.budgetguardian.datastructures.DynamicArray;
import com.budgetguardian.model.Account;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.service.BudgetException;
import com.budgetguardian.service.ServiceContext;
import com.budgetguardian.util.Money;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Calculator screen with two independent tools that both feed the same
 * "save as a transaction" hand-off:
 * <ul>
 *   <li>a free-form THB expression evaluator ({@code + - * / ( )})</li>
 *   <li>a quick calc between two accounts' live balances (+/−), letting the
 *       user pick which account the eventual transaction applies to</li>
 * </ul>
 * Neither tool writes to the store directly — saving opens the matching
 * Phase-5 dialog pre-filled with the computed amount, so all validation and
 * persistence stays in one place.
 */
public final class CalculatorView implements View {

    private final ServiceContext services;
    private final Supplier<LocalDate> today;
    private final VBox root = new VBox(20);

    private final TextField expression = new TextField();
    private final Label expressionResult = new Label("—");
    private final ComboBox<Account> accountA = new ComboBox<>();
    private final ComboBox<Account> accountB = new ComboBox<>();
    private final ComboBox<Account> preferredAccount = new ComboBox<>();
    private final Label quickResult = new Label("—");
    private final Label error = new Label();

    private long lastResultSatang;
    private String lastPreferredAccountId;

    public CalculatorView(ServiceContext services, Supplier<LocalDate> today) {
        this.services = services;
        this.today = today;
        root.setPadding(new Insets(20));
        root.getStyleClass().add("view-root");
        error.getStyleClass().add("note-danger");

        Label title = new Label("Calculator");
        title.getStyleClass().add("page-title");
        root.getChildren().addAll(title, expressionSection(), quickCalcSection(), error, saveBar());
        refresh();
    }

    @Override
    public String title() {
        return "Calculator";
    }

    @Override
    public String icon() {
        return "🧮";
    }

    @Override
    public Node getNode() {
        return root;
    }

    @Override
    public void refresh() {
        Account previousA = accountA.getValue();
        Account previousB = accountB.getValue();
        Account previousPreferred = preferredAccount.getValue();
        DynamicArray<Account> accounts = DashboardOrder.orderedAccounts(services.store());
        accountA.getItems().clear();
        accountB.getItems().clear();
        preferredAccount.getItems().clear();
        for (int i = 0; i < accounts.size(); i++) {
            accountA.getItems().add(accounts.get(i));
            accountB.getItems().add(accounts.get(i));
            preferredAccount.getItems().add(accounts.get(i));
        }
        if (accounts.isEmpty()) {
            return;
        }
        accountA.setValue(previousA != null ? services.store().accounts().get(previousA.id()) : accounts.get(0));
        accountB.setValue(previousB != null ? services.store().accounts().get(previousB.id())
                : accounts.size() > 1 ? accounts.get(1) : accounts.get(0));
        preferredAccount.setValue(previousPreferred != null
                ? services.store().accounts().get(previousPreferred.id()) : accounts.get(0));
    }

    private Node expressionSection() {
        Label heading = new Label("Expression");
        heading.getStyleClass().add("section-label");

        expression.setPromptText("e.g. 120 + 45.50 * 2");
        Button equals = new Button("=");
        equals.getStyleClass().add("primary-button");
        equals.setOnAction(e -> evaluateExpression());
        expressionResult.getStyleClass().add("stat-med");

        HBox row = new HBox(10, expression, equals, expressionResult);
        row.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(8, heading, row);
        card.getStyleClass().add("card");
        return card;
    }

    private Node quickCalcSection() {
        Label heading = new Label("Quick Account Calc");
        heading.getStyleClass().add("section-label");

        accountA.setConverter(DialogSupport.converter(Account::name));
        accountB.setConverter(DialogSupport.converter(Account::name));
        preferredAccount.setConverter(DialogSupport.converter(Account::name));

        ToggleGroup group = new ToggleGroup();
        ToggleButton plus = new ToggleButton("+");
        plus.setToggleGroup(group);
        plus.setSelected(true);
        ToggleButton minus = new ToggleButton("−");
        minus.setToggleGroup(group);
        HBox ops = new HBox(4, plus, minus);

        Button compute = new Button("Compute");
        compute.getStyleClass().add("primary-button");
        compute.setOnAction(e -> evaluateQuickCalc(plus.isSelected()));
        quickResult.getStyleClass().add("stat-med");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, new Label("Account A"), accountA);
        grid.addRow(1, new Label("Operation"), ops);
        grid.addRow(2, new Label("Account B"), accountB);
        grid.addRow(3, new Label("Save into account"), preferredAccount);
        grid.addRow(4, compute, quickResult);

        VBox card = new VBox(8, heading, grid);
        card.getStyleClass().add("card");
        return card;
    }

    private Node saveBar() {
        Label heading = new Label("Save last result as");
        heading.getStyleClass().add("section-label");
        Button asExpense = new Button("Expense");
        asExpense.setOnAction(e -> saveAsExpense());
        Button asIncome = new Button("Income");
        asIncome.setOnAction(e -> saveAsIncome());
        Button asWithdrawal = new Button("Withdrawal");
        asWithdrawal.setOnAction(e -> saveAsWithdrawal());
        HBox row = new HBox(8, asExpense, asIncome, asWithdrawal);
        row.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(8, heading, row);
        card.getStyleClass().add("card");
        return card;
    }

    private void evaluateExpression() {
        try {
            lastResultSatang = services.calculator().evaluateToSatang(expression.getText());
            lastPreferredAccountId = null;
            expressionResult.setText(Money.format(lastResultSatang));
            error.setText("");
        } catch (BudgetException e) {
            error.setText(e.getMessage());
        }
    }

    private void evaluateQuickCalc(boolean add) {
        Account a = accountA.getValue();
        Account b = accountB.getValue();
        if (a == null || b == null) {
            error.setText("Choose both accounts");
            return;
        }
        long result = add ? a.balanceSatang() + b.balanceSatang() : a.balanceSatang() - b.balanceSatang();
        lastResultSatang = Math.abs(result);
        lastPreferredAccountId = preferredAccount.getValue() != null ? preferredAccount.getValue().id() : null;
        quickResult.setText(Money.format(lastResultSatang) + (result < 0 ? "  (absolute value)" : ""));
        error.setText("");
    }

    private void saveAsExpense() {
        if (!haveResult()) {
            return;
        }
        Optional<Transaction> created = new ExpenseDialog(services.store(), today::get)
                .showCreate(lastResultSatang, lastPreferredAccountId);
        created.ifPresent(txn -> {
            Transaction saved = services.transactions().add(txn);
            services.refills().track(saved);
        });
    }

    private void saveAsIncome() {
        if (!haveResult()) {
            return;
        }
        new IncomeDialog(services.store(), today::get)
                .showCreate(lastResultSatang, lastPreferredAccountId)
                .ifPresent(services.transactions()::add);
    }

    private void saveAsWithdrawal() {
        if (!haveResult()) {
            return;
        }
        new WithdrawalDialog(services.store(), today::get)
                .showCreate(lastResultSatang, lastPreferredAccountId)
                .ifPresent(services.transactions()::add);
    }

    private boolean haveResult() {
        if (lastResultSatang <= 0) {
            error.setText("Compute a result first");
            return false;
        }
        return true;
    }
}
