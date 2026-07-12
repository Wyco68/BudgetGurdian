package com.budgetguardian.view;

import com.budgetguardian.datastructures.Iterator;
import com.budgetguardian.model.Debt;
import com.budgetguardian.model.DebtDirection;
import com.budgetguardian.model.DebtStatus;
import com.budgetguardian.service.EventType;
import com.budgetguardian.service.ServiceContext;
import com.budgetguardian.util.Money;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.function.Supplier;

/**
 * Debt screen: two columns — money to pay (payable) and to receive
 * (receivable) — each debt showing a paid/total progress bar and a Pay/Receive
 * button that opens the partial-payment dialog. Overdue open debts are
 * highlighted. Reads debts and payment totals from the {@code DataStore}.
 */
public final class DebtView implements View {

    private final ServiceContext services;
    private final Supplier<LocalDate> today;
    private final VBox root = new VBox(12);
    private final HBox columns = new HBox(16);

    public DebtView(ServiceContext services, Supplier<LocalDate> today) {
        this.services = services;
        this.today = today;
        root.setPadding(new Insets(20));
        root.getStyleClass().add("view-root");
        root.getChildren().addAll(header(), columns);
        VBox.setVgrow(columns, Priority.ALWAYS);
        services.bus().subscribe(EventType.DEBTS_CHANGED, t -> refresh());
        refresh();
    }

    @Override
    public String title() {
        return "Debt";
    }

    @Override
    public String icon() {
        return "💳";
    }

    @Override
    public Node getNode() {
        return root;
    }

    @Override
    public void refresh() {
        columns.getChildren().setAll(
                column("Need to Pay", DebtDirection.PAYABLE),
                column("Need to Receive", DebtDirection.RECEIVABLE));
    }

    private Node header() {
        Label title = new Label("Debts");
        title.getStyleClass().add("section-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button add = new Button("＋ New Debt");
        add.getStyleClass().add("primary-button");
        add.setOnAction(e -> new DebtDialog(today).show().ifPresent(services.debts()::add));
        HBox bar = new HBox(8, title, spacer, add);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private Node column(String heading, DebtDirection direction) {
        VBox list = new VBox(10);
        Label label = new Label(heading);
        label.getStyleClass().add("section-label");
        list.getChildren().add(label);

        boolean any = false;
        // Deterministic order by id via sorted walk.
        DynamicById debts = new DynamicById(services.store());
        for (Debt debt : debts.ordered()) {
            if (debt.direction() != direction) {
                continue;
            }
            any = true;
            list.getChildren().add(debtCard(debt));
        }
        if (!any) {
            Label empty = new Label("Nothing here.");
            empty.getStyleClass().add("muted");
            list.getChildren().add(empty);
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");
        HBox.setHgrow(scroll, Priority.ALWAYS);
        scroll.setPrefWidth(1);
        return scroll;
    }

    private Node debtCard(Debt debt) {
        long paid = services.debts().paidTotal(debt.id());
        long total = debt.amountSatang();
        long remaining = total - paid;
        double ratio = total > 0 ? (double) paid / total : 0;

        Label person = new Label(debt.person());
        person.getStyleClass().add("card-heading");
        Label amounts = new Label(Money.format(paid) + " / " + Money.format(total));
        amounts.getStyleClass().add("stat-med");

        ProgressBar bar = new ProgressBar(ratio);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("budget-bar-ok");

        VBox card = new VBox(6, person, amounts, bar);
        card.getStyleClass().addAll("card", "card-account");

        if (debt.status() == DebtStatus.SETTLED) {
            Label settled = new Label("✔ Settled " + debt.settledDate().format(UiFormat.DATE));
            settled.getStyleClass().add("note-ok");
            card.getChildren().add(settled);
        } else {
            if (debt.isOverdue(today.get())) {
                Label overdue = new Label("⚠ Overdue since " + debt.dueDate().format(UiFormat.DATE));
                overdue.getStyleClass().add("note-danger");
                card.getChildren().add(overdue);
            } else if (debt.dueDate() != null) {
                Label dueLabel = new Label("Due " + debt.dueDate().format(UiFormat.DATE));
                dueLabel.getStyleClass().add("muted");
                card.getChildren().add(dueLabel);
            }
            Button pay = new Button(debt.direction() == DebtDirection.PAYABLE ? "Pay" : "Receive");
            pay.getStyleClass().add("primary-button");
            pay.setOnAction(e -> new PaymentDialog(services.store(), today).show(debt, remaining)
                    .ifPresent(r -> services.debts().pay(debt.id(), r.accountId(), r.amountSatang(), r.date())));
            card.getChildren().add(pay);
        }
        return card;
    }

    /** Small helper: debts sorted by id for stable display order. */
    private static final class DynamicById {
        private final com.budgetguardian.datastructures.DynamicArray<Debt> sorted =
                new com.budgetguardian.datastructures.DynamicArray<>();

        DynamicById(com.budgetguardian.service.DataStore store) {
            Iterator<Debt> it = store.debts().values();
            while (it.hasNext()) {
                Debt debt = it.next();
                int pos = 0;
                while (pos < sorted.size() && sorted.get(pos).id() <= debt.id()) {
                    pos++;
                }
                sorted.insert(pos, debt);
            }
        }

        Iterable<Debt> ordered() {
            java.util.ArrayList<Debt> list = new java.util.ArrayList<>();
            for (int i = 0; i < sorted.size(); i++) {
                list.add(sorted.get(i));
            }
            return list;
        }
    }
}
