package com.budgetguardian.view;

import com.budgetguardian.datastructures.DynamicArray;
import com.budgetguardian.model.RefillItem;
import com.budgetguardian.service.EventType;
import com.budgetguardian.service.ReportService;
import com.budgetguardian.service.ReportService.CategoryTotal;
import com.budgetguardian.service.ReportService.DebtReport;
import com.budgetguardian.service.ReportService.PeriodSummary;
import com.budgetguardian.service.ServiceContext;
import com.budgetguardian.util.Money;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.function.Supplier;

/**
 * Reports screen: a period selector (week / month / year) driving a spending
 * summary, a category bar chart (data ordered by heap sort in
 * {@link ReportService}), the highest-spending category, and danger, debt and
 * refill summaries.
 *
 * <p>Charts use JavaFX's {@code BarChart}; its {@code ObservableList} series
 * is populated from the report's {@code DynamicArray} — chart interop at the
 * UI boundary only, the same rule the tables follow.</p>
 */
public final class ReportsView implements View {

    private enum Period { WEEK, MONTH, YEAR }

    private final ServiceContext services;
    private final ReportService reports;
    private final Supplier<LocalDate> today;
    private final VBox root = new VBox(14);
    private final VBox content = new VBox(14);
    private Period period = Period.MONTH;

    public ReportsView(ServiceContext services, Supplier<LocalDate> today) {
        this.services = services;
        this.reports = services.reports();
        this.today = today;
        root.setPadding(new Insets(20));
        root.getStyleClass().add("view-root");
        root.getChildren().addAll(header(), content);
        services.bus().subscribe(EventType.TRANSACTIONS_CHANGED, t -> refresh());
        services.bus().subscribe(EventType.DEBTS_CHANGED, t -> refresh());
        refresh();
    }

    @Override
    public String title() {
        return "Reports";
    }

    @Override
    public String icon() {
        return "📈";
    }

    @Override
    public Node getNode() {
        return root;
    }

    @Override
    public void refresh() {
        LocalDate day = today.get();
        PeriodSummary summary = switch (period) {
            case WEEK -> reports.weekly(day);
            case MONTH -> reports.monthly(day);
            case YEAR -> reports.yearly(day);
        };
        DynamicArray<CategoryTotal> categories = reports.categoryTotals(summary.from(), summary.to());

        ScrollPane scroll = new ScrollPane(new VBox(14,
                summaryRow(summary),
                sectionLabel("Spending by Category"),
                categoryChart(categories),
                highestCategory(categories),
                sectionLabel("Danger, Debt & Refills"),
                bottomSummaries(summary)));
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        content.getChildren().setAll(scroll);
    }

    private Node header() {
        Label title = new Label("Reports");
        title.getStyleClass().add("page-title");

        ToggleGroup group = new ToggleGroup();
        HBox toggles = new HBox(8);
        toggles.setAlignment(Pos.CENTER_LEFT);
        for (Period p : Period.values()) {
            ToggleButton button = new ToggleButton(label(p));
            button.setToggleGroup(group);
            button.getStyleClass().add("period-toggle");
            button.setSelected(p == period);
            button.setOnAction(e -> {
                period = p;
                button.setSelected(true);
                refresh();
            });
            toggles.getChildren().add(button);
        }
        HBox bar = new HBox(16, title, toggles);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private Node summaryRow(PeriodSummary s) {
        FlowPane row = new FlowPane(12, 12);
        row.getChildren().addAll(
                statCard("Period", s.from().format(UiFormat.DATE) + " – " + s.to().format(UiFormat.DATE)),
                statCard("Total Spending", Money.format(s.totalSatang())),
                statCard("Transactions", Integer.toString(s.transactionCount())),
                statCard("Average / Day", Money.format(s.averagePerDaySatang())));
        return row;
    }

    private Node categoryChart(DynamicArray<CategoryTotal> categories) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        y.setLabel("THB");
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setLegendVisible(false);
        chart.setPrefHeight(300);
        chart.setAnimated(false);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (int i = 0; i < categories.size(); i++) {
            CategoryTotal c = categories.get(i);
            series.getData().add(new XYChart.Data<>(c.name(), c.totalSatang() / 100.0));
        }
        chart.setData(FXCollections.observableArrayList(series));
        if (categories.isEmpty()) {
            Label empty = new Label("No spending in this period.");
            empty.getStyleClass().add("muted");
            VBox box = new VBox(empty);
            box.getStyleClass().add("card");
            return box;
        }
        return chart;
    }

    private Node highestCategory(DynamicArray<CategoryTotal> categories) {
        VBox card = new VBox(6);
        card.getStyleClass().add("card");
        Label heading = new Label("Highest-Spending Category");
        heading.getStyleClass().add("card-heading");
        if (categories.isEmpty()) {
            card.getChildren().addAll(heading, mutedLabel("—"));
            return card;
        }
        CategoryTotal top = categories.get(0);
        Label value = new Label(top.name() + "  ·  " + Money.format(top.totalSatang()));
        value.getStyleClass().add("stat-med");
        card.getChildren().addAll(heading, value);
        return card;
    }

    private Node bottomSummaries(PeriodSummary s) {
        FlowPane row = new FlowPane(12, 12);

        long danger = reports.dangerTotal(s.from(), s.to());
        long dangerLimit = services.settings().dangerWeeklyLimitSatang();
        VBox dangerCard = labelledCard("Danger Spending (period)",
                Money.format(danger),
                danger > dangerLimit ? "Over weekly limit" : "Within limit",
                danger > dangerLimit);

        DebtReport debt = reports.debtReport();
        VBox debtCard = labelledCard("Outstanding Debt",
                "Pay " + Money.format(debt.outstandingPayableSatang())
                        + " · Receive " + Money.format(debt.outstandingReceivableSatang()),
                debt.openCount() + " open · " + debt.settledCount() + " settled",
                debt.outstandingPayableSatang() > 0);

        DynamicArray<RefillItem> overdue = reports.overdueRefills(today.get());
        VBox refillCard = labelledCard("Refills Overdue",
                Integer.toString(overdue.size()),
                overdue.isEmpty() ? "All up to date" : "Need repurchase",
                !overdue.isEmpty());

        row.getChildren().addAll(dangerCard, debtCard, refillCard);
        return row;
    }

    private VBox labelledCard(String heading, String value, String note, boolean warn) {
        Label h = new Label(heading);
        h.getStyleClass().add("card-heading");
        Label v = new Label(value);
        v.getStyleClass().add("stat-med");
        Label n = new Label(note);
        n.getStyleClass().add(warn ? "note-danger" : "note-ok");
        VBox card = new VBox(6, h, v, n);
        card.getStyleClass().addAll("card", "card-stat");
        card.setPrefWidth(300);
        return card;
    }

    private Node statCard(String label, String value) {
        Label l = new Label(label);
        l.getStyleClass().add("card-heading");
        Label v = new Label(value);
        v.getStyleClass().add("stat-med");
        VBox card = new VBox(6, l, v);
        card.getStyleClass().addAll("card", "card-stat");
        card.setPrefWidth(240);
        return card;
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

    private static String label(Period period) {
        return switch (period) {
            case WEEK -> "This Week";
            case MONTH -> "This Month";
            case YEAR -> "This Year";
        };
    }
}
