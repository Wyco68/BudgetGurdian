package com.budgetguardian.view;

import com.budgetguardian.datastructures.Iterator;
import com.budgetguardian.model.Account;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;
import com.budgetguardian.service.DataStore;
import com.budgetguardian.service.EventType;
import com.budgetguardian.service.ServiceContext;
import com.budgetguardian.util.Money;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.function.Supplier;

/**
 * Dashboard: today's spending vs budget, account balances, monthly stats,
 * category totals and the recent-transactions widget (from the
 * {@code CircularBuffer}).
 *
 * <p>All figures are read from the {@link DataStore}'s O(1) derived totals and
 * structures — no database access. Refreshes on balance and transaction
 * events.</p>
 */
public final class DashboardView implements View {

    private final ServiceContext services;
    private final DataStore store;
    private final Supplier<LocalDate> today;
    private final VBox root = new VBox(16);

    public DashboardView(ServiceContext services, Supplier<LocalDate> today) {
        this.services = services;
        this.store = services.store();
        this.today = today;
        root.getStyleClass().add("view-root");
        root.setPadding(new Insets(20));
        services.bus().subscribe(EventType.TRANSACTIONS_CHANGED, t -> refresh());
        services.bus().subscribe(EventType.BALANCES_CHANGED, t -> refresh());
        refresh();
    }

    @Override
    public String title() {
        return "Dashboard";
    }

    @Override
    public String icon() {
        return "📊";
    }

    @Override
    public Node getNode() {
        return root;
    }

    @Override
    public void refresh() {
        root.getChildren().setAll(
                budgetCard(),
                balancesRow(),
                monthlyStatsRow(),
                sectionLabel("Category Totals — " + YearMonth.from(today.get())),
                categoryTotals(),
                sectionLabel("Recent Transactions"),
                recentTransactions());
    }

    private Node budgetCard() {
        LocalDate day = today.get();
        long spent = store.dailyTotal(day);
        long budget = services.settings().dailyBudgetSatang();
        double ratio = budget > 0 ? Math.min(1.0, (double) spent / budget) : 0;

        Label heading = new Label("Today · " + day.format(UiFormat.DATE));
        heading.getStyleClass().add("card-heading");
        Label amount = new Label(Money.format(spent) + "  /  " + Money.format(budget));
        amount.getStyleClass().add("stat-big");

        ProgressBar bar = new ProgressBar(ratio);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add(spent > budget ? "budget-bar-over" : "budget-bar-ok");

        String note = spent > budget
                ? "Over budget by " + Money.format(spent - budget)
                : "Remaining " + Money.format(budget - spent);
        Label noteLabel = new Label(note);
        noteLabel.getStyleClass().add(spent > budget ? "note-danger" : "note-ok");

        VBox card = new VBox(8, heading, amount, bar, noteLabel);
        card.getStyleClass().addAll("card", "card-wide");
        return card;
    }

    private Node balancesRow() {
        FlowPane row = new FlowPane(12, 12);
        DashboardOrder.forEachAccount(store, account -> row.getChildren().add(balanceCard(account)));
        return row;
    }

    private Node balanceCard(Account account) {
        Label name = new Label(account.name());
        name.getStyleClass().add("card-heading");
        Label balance = new Label(Money.format(account.balanceSatang()));
        balance.getStyleClass().add(account.balanceSatang() < 0 ? "stat-med-neg" : "stat-med");
        VBox card = new VBox(6, name, balance);
        card.getStyleClass().addAll("card", "card-account");
        card.setPrefWidth(180);
        return card;
    }

    private Node monthlyStatsRow() {
        LocalDate day = today.get();
        YearMonth month = YearMonth.from(day);
        long total = 0;
        long highest = 0;
        int daysWithSpend = 0;
        for (int d = 1; d <= month.lengthOfMonth(); d++) {
            long dayTotal = store.dailyTotal(month.atDay(d));
            if (dayTotal > 0) {
                total += dayTotal;
                daysWithSpend++;
                highest = Math.max(highest, dayTotal);
            }
        }
        long average = daysWithSpend > 0 ? total / daysWithSpend : 0;

        FlowPane row = new FlowPane(12, 12);
        row.getChildren().addAll(
                statCard("Monthly Spending", Money.format(total)),
                statCard("Average / Active Day", Money.format(average)),
                statCard("Highest Day", Money.format(highest)));
        return row;
    }

    private Node statCard(String label, String value) {
        Label l = new Label(label);
        l.getStyleClass().add("card-heading");
        Label v = new Label(value);
        v.getStyleClass().add("stat-med");
        VBox card = new VBox(6, l, v);
        card.getStyleClass().addAll("card", "card-stat");
        card.setPrefWidth(220);
        return card;
    }

    private Node categoryTotals() {
        LocalDate day = today.get();
        VBox list = new VBox(6);
        list.getStyleClass().add("card");
        boolean any = false;
        // Deterministic order 1..N by id.
        for (int id = 1; id <= 64; id++) {
            var category = store.categories().get(id);
            if (category == null) {
                continue;
            }
            long total = store.categoryMonthTotal(id, day);
            if (total <= 0) {
                continue;
            }
            any = true;
            Label name = new Label(category.name());
            Label value = new Label(Money.format(total));
            value.getStyleClass().add("mono");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox rowBox = new HBox(8, name, spacer, value);
            rowBox.setAlignment(Pos.CENTER_LEFT);
            if (category.danger()) {
                name.getStyleClass().add("danger-text");
            }
            list.getChildren().add(rowBox);
        }
        if (!any) {
            list.getChildren().add(mutedLabel("No spending yet this month."));
        }
        return list;
    }

    private Node recentTransactions() {
        VBox list = new VBox(4);
        list.getStyleClass().add("card");
        Iterator<Transaction> it = store.recentTransactions().newestFirst();
        if (!it.hasNext()) {
            list.getChildren().add(mutedLabel("No transactions recorded yet."));
            return list;
        }
        while (it.hasNext()) {
            list.getChildren().add(recentRow(it.next()));
        }
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(260);
        scroll.getStyleClass().add("edge-to-edge");
        return scroll;
    }

    private Node recentRow(Transaction txn) {
        String sign = txn.type() == TransactionType.INCOME ? "+" : "−";
        Label left = new Label(txn.date().format(UiFormat.DATE) + "  ·  "
                + UiFormat.categoryName(store, txn.categoryId())
                + (txn.itemName() != null ? " · " + txn.itemName() : ""));
        Label right = new Label(sign + Money.formatPlain(txn.amountSatang()));
        right.getStyleClass().add(txn.type() == TransactionType.INCOME ? "amount-pos" : "amount-neg");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(8, left, spacer, right);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("list-row");
        return row;
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-label");
        return label;
    }

    private Label mutedLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted");
        return label;
    }
}
