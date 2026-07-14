package com.budgetguardian.view;

import com.budgetguardian.datastructures.DynamicArray;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;
import com.budgetguardian.service.EventType;
import com.budgetguardian.service.ServiceContext;
import com.budgetguardian.util.Money;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * Search screen (Ctrl+F): a live free-text query over the ledger, powered by
 * {@link com.budgetguardian.service.SearchService}'s linear search. Results
 * are newest-first and update as the user types; the result count is shown.
 */
public final class SearchView implements View {

    private final ServiceContext services;
    private final VBox root = new VBox(12);
    private final TextField query = new TextField();
    private final Label count = new Label();
    private final ObservableList<Transaction> rows = FXCollections.observableArrayList();
    private final TableView<Transaction> table = new TableView<>(rows);

    public SearchView(ServiceContext services) {
        this.services = services;
        root.setPadding(new Insets(20));
        root.getStyleClass().add("view-root");
        buildTable();
        query.setPromptText("Search transactions by reason, item, account or category…");
        query.textProperty().addListener((obs, old, text) -> runSearch(text));
        Label title = new Label("Search");
        title.getStyleClass().add("page-title");
        count.getStyleClass().add("muted");
        HBox bar = new HBox(12, title, query, count);
        bar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(query, Priority.ALWAYS);
        root.getChildren().addAll(bar, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        // Keep results fresh if the ledger changes while the query stands.
        services.bus().subscribe(EventType.TRANSACTIONS_CHANGED, t -> runSearch(query.getText()));
    }

    @Override
    public String title() {
        return "Search";
    }

    @Override
    public String icon() {
        return "🔍";
    }

    @Override
    public Node getNode() {
        return root;
    }

    @Override
    public void refresh() {
        runSearch(query.getText());
    }

    /** Focuses the query field — invoked by the Ctrl+F accelerator. */
    public void focusQuery() {
        query.requestFocus();
        query.selectAll();
    }

    private void runSearch(String text) {
        rows.clear();
        if (text == null || text.isBlank()) {
            count.setText("");
            return;
        }
        DynamicArray<Transaction> results = services.search().searchTransactions(text);
        for (int i = 0; i < results.size(); i++) {
            rows.add(results.get(i));
        }
        count.setText(results.size() + (results.size() == 1 ? " match" : " matches"));
    }

    private void buildTable() {
        TableColumn<Transaction, String> date = new TableColumn<>("Date");
        date.setCellValueFactory(c -> ro(c.getValue().date().format(UiFormat.DATE)));
        TableColumn<Transaction, String> type = new TableColumn<>("Type");
        type.setCellValueFactory(c -> ro(c.getValue().type().name()));
        TableColumn<Transaction, String> account = new TableColumn<>("Account");
        account.setCellValueFactory(c -> ro(UiFormat.accountName(services.store(), c.getValue().accountId())));
        TableColumn<Transaction, String> category = new TableColumn<>("Category");
        category.setCellValueFactory(c -> ro(UiFormat.categoryName(services.store(), c.getValue().categoryId())));
        TableColumn<Transaction, String> item = new TableColumn<>("Item");
        item.setCellValueFactory(c -> ro(c.getValue().itemName() == null ? "—" : c.getValue().itemName()));
        TableColumn<Transaction, String> reason = new TableColumn<>("Reason");
        reason.setCellValueFactory(c -> ro(c.getValue().reason()));
        TableColumn<Transaction, String> amount = new TableColumn<>("Amount");
        amount.setCellValueFactory(c -> {
            Transaction t = c.getValue();
            String sign = t.type() == TransactionType.INCOME ? "+" : "−";
            return ro(sign + Money.formatPlain(t.amountSatang()));
        });

        table.getColumns().add(date);
        table.getColumns().add(type);
        table.getColumns().add(account);
        table.getColumns().add(category);
        table.getColumns().add(item);
        table.getColumns().add(reason);
        table.getColumns().add(amount);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_SUBSEQUENT_COLUMNS);
        table.getStyleClass().add("data-table");
        table.setPlaceholder(new Label("Type to search your transactions."));
    }

    private static ObservableValue<String> ro(String value) {
        return new SimpleStringProperty(value);
    }
}
