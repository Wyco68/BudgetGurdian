package com.budgetguardian.view;

import com.budgetguardian.datastructures.DynamicArray;
import com.budgetguardian.datastructures.Iterator;
import com.budgetguardian.model.Bill;
import com.budgetguardian.service.EventType;
import com.budgetguardian.service.ServiceContext;
import com.budgetguardian.util.Money;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.function.Supplier;

/**
 * Bills screen: a card list of recurring bills with a "＋ New Bill" button
 * and, per bill, a Pay button and its recurrence/due status. Reads bills
 * from the {@code DataStore}.
 */
public final class BillsView implements View {

    private final ServiceContext services;
    private final Supplier<LocalDate> today;
    private final VBox root = new VBox(12);
    private final VBox list = new VBox(10);

    public BillsView(ServiceContext services, Supplier<LocalDate> today) {
        this.services = services;
        this.today = today;
        root.setPadding(new Insets(20));
        root.getStyleClass().add("view-root");
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        root.getChildren().addAll(header(), scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        services.bus().subscribe(EventType.BILLS_CHANGED, t -> refresh());
        refresh();
    }

    @Override
    public String title() {
        return "Bills";
    }

    @Override
    public String icon() {
        return "🧾";
    }

    @Override
    public Node getNode() {
        return root;
    }

    @Override
    public void refresh() {
        list.getChildren().clear();
        DynamicArray<Bill> sorted = sortedById();
        if (sorted.isEmpty()) {
            Label empty = new Label("No bills yet.");
            empty.getStyleClass().add("muted");
            list.getChildren().add(empty);
            return;
        }
        for (int i = 0; i < sorted.size(); i++) {
            list.getChildren().add(billCard(sorted.get(i)));
        }
    }

    private Node header() {
        Label title = new Label("Bills");
        title.getStyleClass().add("page-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button add = new Button("＋ New Bill");
        add.getStyleClass().add("primary-button");
        add.setOnAction(e -> new BillDialog().show().ifPresent(services.bills()::add));
        HBox bar = new HBox(8, title, spacer, add);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private Node billCard(Bill bill) {
        Label name = new Label(bill.name());
        name.getStyleClass().add("card-heading");
        Label amount = new Label(Money.format(bill.amountSatang()));
        amount.getStyleClass().add("stat-med");

        VBox card = new VBox(6, name, amount);
        card.getStyleClass().addAll("card", "card-account");

        if (bill.isRecurring()) {
            Label recurrence = new Label("Due on day " + bill.payday() + " of each month");
            recurrence.getStyleClass().add(bill.isDue(today.get()) ? "note-danger" : "muted");
            card.getChildren().add(recurrence);
        } else {
            Label oneOff = new Label("One-off");
            oneOff.getStyleClass().add("muted");
            card.getChildren().add(oneOff);
        }
        Label lastPaid = new Label(bill.lastPaidDate() != null
                ? "Last paid " + bill.lastPaidDate().format(UiFormat.DATE)
                : "Never paid");
        lastPaid.getStyleClass().add("muted");
        card.getChildren().add(lastPaid);

        HBox actions = new HBox(8);
        Button pay = new Button("Pay");
        pay.getStyleClass().add("primary-button");
        pay.setOnAction(e -> new BillPaymentDialog(services.store(), today).show(bill)
                .ifPresent(r -> services.bills().pay(bill.id(), r.accountId(), r.amountSatang(), r.date(), r.reason())));
        Button delete = new Button("Delete");
        delete.getStyleClass().add("danger-button");
        delete.setOnAction(e -> services.bills().delete(bill.id()));
        actions.getChildren().addAll(pay, delete);
        card.getChildren().add(actions);
        return card;
    }

    /** Bills sorted by id for stable display order. O(b^2) over the (small) bill count. */
    private DynamicArray<Bill> sortedById() {
        DynamicArray<Bill> sorted = new DynamicArray<>();
        Iterator<Bill> it = services.store().bills().values();
        while (it.hasNext()) {
            Bill bill = it.next();
            int pos = 0;
            while (pos < sorted.size() && sorted.get(pos).id() <= bill.id()) {
                pos++;
            }
            sorted.insert(pos, bill);
        }
        return sorted;
    }
}
