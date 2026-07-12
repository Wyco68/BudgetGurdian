package com.budgetguardian.view;

import com.budgetguardian.datastructures.DynamicArray;
import com.budgetguardian.datastructures.Graph;
import com.budgetguardian.model.Account;
import com.budgetguardian.service.DataStore;
import com.budgetguardian.service.EventType;
import com.budgetguardian.service.ServiceContext;
import com.budgetguardian.util.Money;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.util.function.Supplier;

/**
 * Transfer-network visualization: the four accounts as nodes laid out on a
 * circle, each transfer lane drawn as a directed, weighted edge (total flow
 * labelled). DFS and BFS buttons run the corresponding {@code Graph} traversal
 * from a chosen start account and list the visit order — showcasing the custom
 * graph algorithms.
 */
public final class GraphView implements View {

    private static final double RADIUS = 150;
    private static final double NODE = 30;

    private final ServiceContext services;
    private final DataStore store;
    private final Pane canvas = new Pane();
    private final Label traversalLabel = new Label();
    private final ComboBox<Account> start = new ComboBox<>();
    private final javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(12);

    public GraphView(ServiceContext services, Supplier<java.time.LocalDate> today) {
        this.services = services;
        this.store = services.store();
        root.setPadding(new Insets(20));
        root.getStyleClass().add("view-root");
        canvas.setPrefSize(460, 400);
        canvas.getStyleClass().add("card");
        traversalLabel.getStyleClass().add("mono");
        traversalLabel.setWrapText(true);
        root.getChildren().addAll(header(), canvas, traversalLabel);
        services.bus().subscribe(EventType.TRANSFERS_CHANGED, t -> refresh());
        refresh();
    }

    @Override
    public String title() {
        return "Graph";
    }

    @Override
    public String icon() {
        return "🕸";
    }

    @Override
    public Node getNode() {
        return root;
    }

    @Override
    public void refresh() {
        DynamicArray<Account> accounts = DashboardOrder.orderedAccounts(store);
        Account previous = start.getValue();
        start.getItems().clear();
        for (int i = 0; i < accounts.size(); i++) {
            start.getItems().add(accounts.get(i));
        }
        if (previous != null) {
            start.setValue(store.accounts().get(previous.id()));
        } else if (!accounts.isEmpty()) {
            start.setValue(accounts.get(0));
        }
        drawGraph(accounts);
        traversalLabel.setText("Pick a start account, then run DFS or BFS.");
    }

    private Node header() {
        Label title = new Label("Transfer Network");
        title.getStyleClass().add("section-label");
        start.setConverter(new javafx.util.StringConverter<>() {
            @Override
            public String toString(Account a) {
                return a == null ? "" : a.name();
            }

            @Override
            public Account fromString(String s) {
                return null;
            }
        });
        Button dfs = new Button("Run DFS");
        dfs.getStyleClass().add("primary-button");
        dfs.setOnAction(e -> runTraversal(true));
        Button bfs = new Button("Run BFS");
        bfs.getStyleClass().add("primary-button");
        bfs.setOnAction(e -> runTraversal(false));
        HBox bar = new HBox(10, title, new Label("  Start:"), start, dfs, bfs);
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private void runTraversal(boolean depthFirst) {
        Account from = start.getValue();
        if (from == null) {
            return;
        }
        Graph<String> graph = store.transferGraph();
        DynamicArray<String> order = depthFirst ? graph.dfs(from.id()) : graph.bfs(from.id());
        StringBuilder sb = new StringBuilder(depthFirst ? "DFS from " : "BFS from ");
        sb.append(from.name()).append(":  ");
        for (int i = 0; i < order.size(); i++) {
            sb.append(UiFormat.accountName(store, order.get(i)));
            if (i < order.size() - 1) {
                sb.append("  →  ");
            }
        }
        if (order.size() <= 1) {
            sb.append("(no outgoing transfers)");
        }
        traversalLabel.setText(sb.toString());
    }

    private void drawGraph(DynamicArray<Account> accounts) {
        canvas.getChildren().clear();
        int n = accounts.size();
        if (n == 0) {
            return;
        }
        double cx = 230;
        double cy = 190;
        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            double angle = 2 * Math.PI * i / n - Math.PI / 2;
            xs[i] = cx + RADIUS * Math.cos(angle);
            ys[i] = cy + RADIUS * Math.sin(angle);
        }

        Graph<String> graph = store.transferGraph();
        // One aggregated arrow per ordered pair that has any flow.
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    continue;
                }
                long flow = graph.totalFlow(accounts.get(i).id(), accounts.get(j).id());
                if (flow > 0) {
                    drawEdge(xs[i], ys[i], xs[j], ys[j], flow);
                }
            }
        }
        for (int i = 0; i < n; i++) {
            drawNode(xs[i], ys[i], accounts.get(i).name());
        }
    }

    private void drawEdge(double x1, double y1, double x2, double y2, long flow) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.hypot(dx, dy);
        double ux = dx / len;
        double uy = dy / len;
        // trim to node borders
        double sx = x1 + ux * NODE;
        double sy = y1 + uy * NODE;
        double ex = x2 - ux * NODE;
        double ey = y2 - uy * NODE;

        Line line = new Line(sx, sy, ex, ey);
        line.setStroke(Color.web("#2dd4bf"));
        line.setStrokeWidth(2);

        double ah = 9;
        double angle = Math.atan2(ey - sy, ex - sx);
        Polygon arrow = new Polygon(
                ex, ey,
                ex - ah * Math.cos(angle - Math.PI / 7), ey - ah * Math.sin(angle - Math.PI / 7),
                ex - ah * Math.cos(angle + Math.PI / 7), ey - ah * Math.sin(angle + Math.PI / 7));
        arrow.setFill(Color.web("#2dd4bf"));

        Text label = new Text((sx + ex) / 2, (sy + ey) / 2 - 4, Money.formatPlain(flow));
        label.setFill(Color.web("#8b95a3"));
        label.setFont(Font.font(11));

        canvas.getChildren().addAll(line, arrow, label);
    }

    private void drawNode(double x, double y, String name) {
        Circle circle = new Circle(x, y, NODE);
        circle.setFill(Color.web("#1e242c"));
        circle.setStroke(Color.web("#2dd4bf"));
        circle.setStrokeWidth(2);
        Text text = new Text(name);
        text.setFill(Color.web("#e6e9ee"));
        text.setFont(Font.font(12));
        text.setX(x - text.getLayoutBounds().getWidth() / 2);
        text.setY(y + 4);
        canvas.getChildren().addAll(circle, text);
    }
}
