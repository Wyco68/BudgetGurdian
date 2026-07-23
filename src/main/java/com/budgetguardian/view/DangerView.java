package com.budgetguardian.view;

import com.budgetguardian.datastructures.DynamicArray;
import com.budgetguardian.service.EventType;
import com.budgetguardian.service.ReportService;
import com.budgetguardian.service.ReportService.WeeklyTotal;
import com.budgetguardian.service.ServiceContext;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

/**
 * Weekly Alcohol-vs-Bill screen: a two-series bar chart, one bar-group per
 * Mon–Sun week over the last {@value #WEEKS} weeks, so danger spending and
 * bill payments can be compared week over week without being folded into a
 * single "danger" total.
 */
public final class DangerView implements View {

    private static final int WEEKS = 8;
    private static final DateTimeFormatter WEEK_LABEL = DateTimeFormatter.ofPattern("dd MMM");

    private final ServiceContext services;
    private final ReportService reports;
    private final Supplier<LocalDate> today;
    private final VBox root = new VBox(14);
    private final VBox content = new VBox(14);

    public DangerView(ServiceContext services, Supplier<LocalDate> today) {
        this.services = services;
        this.reports = services.reports();
        this.today = today;
        root.setPadding(new Insets(20));
        root.getStyleClass().add("view-root");
        Label title = new Label("Alcohol & Bills — Weekly");
        title.getStyleClass().add("page-title");
        root.getChildren().addAll(title, content);
        services.bus().subscribe(EventType.TRANSACTIONS_CHANGED, t -> refresh());
        services.bus().subscribe(EventType.BILLS_CHANGED, t -> refresh());
        refresh();
    }

    @Override
    public String title() {
        return "Danger";
    }

    @Override
    public String icon() {
        return "📉";
    }

    @Override
    public Node getNode() {
        return root;
    }

    @Override
    public void refresh() {
        DynamicArray<WeeklyTotal> alcohol = reports.weeklyCategoryTrend("Alcohol", today.get(), WEEKS);
        DynamicArray<WeeklyTotal> bill = reports.weeklyCategoryTrend("Bill", today.get(), WEEKS);
        content.getChildren().setAll(chart(alcohol, bill));
    }

    private Node chart(DynamicArray<WeeklyTotal> alcohol, DynamicArray<WeeklyTotal> bill) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        y.setLabel("THB");
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setPrefHeight(420);
        chart.setAnimated(false);

        XYChart.Series<String, Number> alcoholSeries = new XYChart.Series<>();
        alcoholSeries.setName("Alcohol");
        XYChart.Series<String, Number> billSeries = new XYChart.Series<>();
        billSeries.setName("Bill");
        for (int i = 0; i < alcohol.size(); i++) {
            String label = alcohol.get(i).weekStart().format(WEEK_LABEL);
            alcoholSeries.getData().add(new XYChart.Data<>(label, alcohol.get(i).totalSatang() / 100.0));
            billSeries.getData().add(new XYChart.Data<>(label, bill.get(i).totalSatang() / 100.0));
        }
        chart.setData(FXCollections.observableArrayList(alcoholSeries, billSeries));
        return chart;
    }
}
