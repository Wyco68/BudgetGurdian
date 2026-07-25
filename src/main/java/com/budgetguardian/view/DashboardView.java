package com.budgetguardian.view;

import com.budgetguardian.model.Account;
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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Dashboard: today's spending vs budget, account balances, monthly stats,
 * category totals and the recent-transactions widget (from the
 * {@code CircularBuffer}).
 *
 * <p>Account cards are clickable — clicking toggles it into a selection set,
 * and a "Selected Total" card shows the combined balance of every selected
 * account, letting you quickly compare or sum a subset of accounts.</p>
 *
 * <p>All figures are read from the {@link DataStore}'s O(1) derived totals and
 * structures — no database access. Refreshes on balance and transaction
 * events.</p>
 */
public final class DashboardView implements View {

    /** Pseudo-account ids for the debt summary cards, selectable into the combined total. */
    private static final String RECEIVABLE_ID = "__RECEIVABLE__";
    private static final String PAYABLE_ID = "__PAYABLE__";

    private final ServiceContext services;
    private final DataStore store;
    private final Supplier<LocalDate> today;
    private final VBox root = new VBox(16);
    private final ScrollPane scroller = new ScrollPane(root);
    private final Set<String> selectedAccountIds = new LinkedHashSet<>();

    public DashboardView(ServiceContext services, Supplier<LocalDate> today) {
        this.services = services;
        this.store = services.store();
        this.today = today;
        root.getStyleClass().add("view-root");
        root.setPadding(new Insets(20));
        scroller.setFitToWidth(true);
        scroller.getStyleClass().addAll("view-root", "edge-to-edge");
        services.bus().subscribe(EventType.TRANSACTIONS_CHANGED, t -> refresh());
        services.bus().subscribe(EventType.BALANCES_CHANGED, t -> refresh());
        services.bus().subscribe(EventType.DEBTS_CHANGED, t -> refresh());
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
        return scroller;
    }

    @Override
    public void refresh() {
        root.getChildren().setAll(
                pageHeader(),
                budgetCard(),
                sectionLabel("Accounts"),
                balancesRow());
        if (!selectedAccountIds.isEmpty()) {
            // Own row: showing the combined total never reflows the account cards.
            root.getChildren().add(selectedTotalCard());
        }
        root.getChildren().addAll(
                sectionLabel("This Month"),
                monthlyStatsRow(),
                sectionLabel("Category Totals — " + YearMonth.from(today.get())),
                categoryTotals());
    }

    private Node pageHeader() {
        Label title = new Label("Dashboard");
        title.getStyleClass().add("page-title");
        Label sub = new Label(today.get().format(UiFormat.DATE));
        sub.getStyleClass().add("view-subtitle");
        return new VBox(2, title, sub);
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
        var debt = services.reports().debtReport();
        row.getChildren().add(debtCard("Receivable", RECEIVABLE_ID, debt.outstandingReceivableSatang()));
        row.getChildren().add(debtCard("Payable", PAYABLE_ID, debt.outstandingPayableSatang()));
        return row;
    }

    /** A selectable outstanding-debt summary card, behaving like an account tile. */
    private Node debtCard(String label, String pseudoId, long amountSatang) {
        Label name = new Label(label);
        name.getStyleClass().add("card-heading");
        Label value = new Label(Money.format(amountSatang));
        value.getStyleClass().add("stat-med");
        VBox card = new VBox(6, name, value);
        card.getStyleClass().addAll("card", "card-account", "selectable");
        if (selectedAccountIds.contains(pseudoId)) {
            card.getStyleClass().add("card-account-selected");
        }
        card.setPrefWidth(180);
        card.setOnMouseClicked(e -> {
            if (!selectedAccountIds.remove(pseudoId)) {
                selectedAccountIds.add(pseudoId);
            }
            refresh();
        });
        return card;
    }

    private Node balanceCard(Account account) {
        Label name = new Label(account.name());
        name.getStyleClass().add("card-heading");
        Label balance = new Label(Money.format(account.balanceSatang()));
        balance.getStyleClass().add(account.balanceSatang() < 0 ? "stat-med-neg" : "stat-med");
        VBox card = new VBox(6, name, balance);
        card.getStyleClass().addAll("card", "card-account", "selectable");
        if (selectedAccountIds.contains(account.id())) {
            card.getStyleClass().add("card-account-selected");
        }
        card.setPrefWidth(180);
        card.setOnMouseClicked(e -> {
            if (!selectedAccountIds.remove(account.id())) {
                selectedAccountIds.add(account.id());
            }
            refresh();
        });
        return card;
    }

    /** Combined balance of every clicked account/debt card, with a clear-selection hint. */
    private Node selectedTotalCard() {
        long total = 0;
        StringBuilder names = new StringBuilder();
        var debt = services.reports().debtReport();
        for (String id : selectedAccountIds) {
            long value;
            String label;
            if (RECEIVABLE_ID.equals(id)) {
                value = debt.outstandingReceivableSatang();
                label = "Receivable";
            } else if (PAYABLE_ID.equals(id)) {
                value = debt.outstandingPayableSatang();
                label = "Payable";
            } else {
                Account account = store.accounts().get(id);
                if (account == null) {
                    continue;
                }
                value = account.balanceSatang();
                label = account.name();
            }
            total += value;
            if (names.length() > 0) {
                names.append(" + ");
            }
            names.append(label);
        }
        Label heading = new Label("Selected Total (" + selectedAccountIds.size() + ")");
        heading.getStyleClass().add("card-heading");
        Label value = new Label(Money.format(total));
        value.getStyleClass().add(total < 0 ? "stat-med-neg" : "stat-med");
        Label breakdown = new Label(names.toString());
        breakdown.getStyleClass().add("muted");
        breakdown.setWrapText(true);

        VBox card = new VBox(6, heading, value, breakdown);
        card.getStyleClass().addAll("card-total", "selectable");
        card.setPrefWidth(220);
        card.setOnMouseClicked(e -> {
            selectedAccountIds.clear();
            refresh();
        });
        return card;
    }

    private Node monthlyStatsRow() {
        LocalDate day = today.get();
        YearMonth month = YearMonth.from(day);
        long total = 0;
        long highest = 0;
        LocalDate highestDay = null;
        int daysWithSpend = 0;
        for (int d = 1; d <= month.lengthOfMonth(); d++) {
            LocalDate date = month.atDay(d);
            long dayTotal = store.dailyTotal(date);
            if (dayTotal > 0) {
                total += dayTotal;
                daysWithSpend++;
                if (dayTotal > highest) {
                    highest = dayTotal;
                    highestDay = date;
                }
            }
        }
        long average = daysWithSpend > 0 ? total / daysWithSpend : 0;

        FlowPane row = new FlowPane(12, 12);
        row.getChildren().addAll(
                statCard("Monthly Spending", Money.format(total)),
                statCard("Average / Active Day", Money.format(average)),
                statCard("Highest Day",
                        highestDay == null ? Money.format(0)
                                : Money.format(highest) + "  ·  " + highestDay.format(UiFormat.DATE)));
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
