package com.budgetguardian.view;

import com.budgetguardian.datastructures.DynamicArray;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.service.EventType;
import com.budgetguardian.service.ReportService.GamblingEntry;
import com.budgetguardian.service.ReportService.GamblingSummary;
import com.budgetguardian.service.ServiceContext;
import com.budgetguardian.util.Money;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Gambling screen: every win and loss with the day it happened, over a
 * running win / loss / net summary.
 *
 * <p>Nothing is stored separately — this is a projection of the ordinary
 * ledger. Wins are income rows whose reason contains "gambling win" (the
 * wording already in use before this screen existed); losses are expenses in
 * the {@code Gamble} category. Recording from here goes through
 * {@link GamblingDialog} so both halves land in the right shape, and the rows
 * stay editable and undoable like any other transaction.</p>
 */
public final class GamblingView implements View {

    private final ServiceContext services;
    private final Supplier<LocalDate> today;
    private final VBox root = new VBox(14);
    private final FlowPane summary = new FlowPane(12, 12);
    private final VBox list = new VBox(6);
    private long selectedId = -1;

    public GamblingView(ServiceContext services, Supplier<LocalDate> today) {
        this.services = services;
        this.today = today;
        root.setPadding(new Insets(20));
        root.getStyleClass().add("view-root");

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("edge-to-edge");

        Label listLabel = new Label("Results");
        listLabel.getStyleClass().add("section-label");
        root.getChildren().addAll(header(), summary, listLabel, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        services.bus().subscribe(EventType.TRANSACTIONS_CHANGED, t -> refresh());
        refresh();
    }

    @Override
    public String title() {
        return "Gambling";
    }

    @Override
    public String icon() {
        return "🎲";
    }

    @Override
    public Node getNode() {
        return root;
    }

    @Override
    public void refresh() {
        DynamicArray<GamblingEntry> entries = services.reports().gamblingLog();
        summary.getChildren().setAll(summaryCards(services.reports().gamblingSummary(entries)));

        list.getChildren().clear();
        for (int i = 0; i < entries.size(); i++) {
            list.getChildren().add(entryRow(entries.get(i)));
        }
        if (entries.isEmpty()) {
            Label empty = new Label("No gambling results yet — record a win or a loss above.");
            empty.getStyleClass().add("muted");
            list.getChildren().add(empty);
        }
    }

    private Node header() {
        Label title = new Label("Gambling");
        title.getStyleClass().add("page-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button addWin = new Button("＋ Win");
        addWin.getStyleClass().add("primary-button");
        addWin.setOnAction(e -> record(GamblingDialog.Result.WIN));
        Button addLoss = new Button("＋ Loss");
        addLoss.getStyleClass().add("danger-button");
        addLoss.setOnAction(e -> record(GamblingDialog.Result.LOSS));
        Button edit = new Button("Edit");
        edit.setOnAction(e -> editSelected());
        Button delete = new Button("Delete");
        delete.setOnAction(e -> deleteSelected());

        HBox bar = new HBox(8, title, spacer, addWin, addLoss, edit, delete);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private Node[] summaryCards(GamblingSummary s) {
        long net = s.netSatang();
        String netText = (net >= 0 ? "+" : "−") + Money.formatPlain(Math.abs(net)) + " THB";
        return new Node[]{
                statCard("Total Won", Money.format(s.wonSatang()),
                        s.winCount() + (s.winCount() == 1 ? " win" : " wins"), "amount-pos"),
                statCard("Total Lost", Money.format(s.lostSatang()),
                        s.lossCount() + (s.lossCount() == 1 ? " loss" : " losses"), "amount-neg"),
                statCard("Net", netText,
                        net >= 0 ? "Ahead overall" : "Down overall",
                        net >= 0 ? "amount-pos" : "amount-neg"),
                statCard("Biggest Single",
                        "▲ " + Money.formatPlain(s.biggestWinSatang())
                                + "   ▼ " + Money.formatPlain(s.biggestLossSatang()),
                        "best win · worst loss", null)
        };
    }

    private Node statCard(String heading, String value, String note, String valueStyle) {
        Label h = new Label(heading);
        h.getStyleClass().add("card-heading");
        Label v = new Label(value);
        v.getStyleClass().add("stat-med");
        if (valueStyle != null) {
            v.getStyleClass().add(valueStyle);
        }
        Label n = new Label(note);
        n.getStyleClass().add("card-heading");
        VBox card = new VBox(6, h, v, n);
        card.getStyleClass().addAll("card", "card-stat");
        card.setPrefWidth(250);
        return card;
    }

    private Node entryRow(GamblingEntry entry) {
        Transaction txn = entry.txn();
        Label day = new Label(entry.date().format(UiFormat.DATE));
        day.getStyleClass().add("card-heading");
        Label detail = new Label((entry.win() ? "Win" : "Loss")
                + "  ·  " + UiFormat.accountName(services.store(), txn.accountId())
                + (txn.reason() != null && !txn.reason().isBlank() ? "  ·  " + txn.reason() : ""));
        VBox left = new VBox(2, day, detail);

        Label amount = new Label((entry.win() ? "+" : "−") + Money.formatPlain(entry.amountSatang()));
        amount.getStyleClass().add(entry.win() ? "amount-pos" : "amount-neg");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox row = new HBox(10, left, spacer, amount);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("list-row");
        if (txn.id() == selectedId) {
            row.getStyleClass().add("list-row-selected");
        }
        row.setOnMouseClicked(e -> {
            selectedId = txn.id();
            if (e.getClickCount() == 2) {
                editSelected();
            } else {
                refresh();
            }
        });
        return row;
    }

    private void record(GamblingDialog.Result result) {
        Optional<Transaction> created = new GamblingDialog(services.store(), today::get).showCreate(result);
        created.ifPresent(services.transactions()::add);
    }

    /** @return the selected entry, or null if nothing is selected any more. */
    private GamblingEntry selected() {
        if (selectedId < 0) {
            return null;
        }
        DynamicArray<GamblingEntry> entries = services.reports().gamblingLog();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).txn().id() == selectedId) {
                return entries.get(i);
            }
        }
        return null;
    }

    private void editSelected() {
        GamblingEntry entry = selected();
        if (entry == null) {
            return;
        }
        GamblingDialog.Result result = entry.win()
                ? GamblingDialog.Result.WIN
                : GamblingDialog.Result.LOSS;
        new GamblingDialog(services.store(), today::get)
                .showEdit(entry.txn(), result)
                .ifPresent(services.transactions()::edit);
    }

    private void deleteSelected() {
        GamblingEntry entry = selected();
        if (entry == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete this gambling result? You can undo with Ctrl+Z.", ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(button -> {
            if (button == ButtonType.OK) {
                services.transactions().delete(entry.txn().id());
                selectedId = -1;
            }
        });
    }
}
