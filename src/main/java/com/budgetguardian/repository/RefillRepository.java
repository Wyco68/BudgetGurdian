package com.budgetguardian.repository;

import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.model.RefillItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * JDBC access to the {@code refill_item} table.
 *
 * <p><b>Purpose:</b> hydrates confirmed refillable items into a
 * {@code HashMap<String, RefillItem>} keyed by normalized name. Only items
 * the user answered "Yes" for exist here — a declined item leaves no row, so
 * the next duplicate purchase asks again.</p>
 *
 * <p><b>Time complexity:</b> {@code findAll} O(n) single pass;
 * upsert/delete O(1) statements (primary-key indexed).</p>
 */
public final class RefillRepository {

    private final Connection connection;

    public RefillRepository(Connection connection) {
        this.connection = connection;
    }

    /** @return all refillable items keyed by normalized name. */
    public HashMap<String, RefillItem> findAll() throws SQLException {
        HashMap<String, RefillItem> items = new HashMap<>();
        String sql = "SELECT name, interval_days, last_purchase, purchase_count FROM refill_item";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                RefillItem item = new RefillItem(
                        rs.getString("name"),
                        rs.getDouble("interval_days"),
                        LocalDate.parse(rs.getString("last_purchase")),
                        rs.getInt("purchase_count"));
                items.put(item.name(), item);
            }
        }
        return items;
    }

    /** Inserts or replaces one item (name is the primary key). */
    public void upsert(RefillItem item) throws SQLException {
        String sql = "INSERT INTO refill_item (name, interval_days, last_purchase, purchase_count) "
                + "VALUES (?, ?, ?, ?) ON CONFLICT(name) DO UPDATE SET "
                + "interval_days = excluded.interval_days, last_purchase = excluded.last_purchase, "
                + "purchase_count = excluded.purchase_count";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, item.name());
            stmt.setDouble(2, item.intervalDays());
            stmt.setString(3, item.lastPurchase().toString());
            stmt.setInt(4, item.purchaseCount());
            stmt.executeUpdate();
        }
    }

    /** Deletes one item (undo of a "Yes" confirmation). */
    public void delete(String name) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM refill_item WHERE name = ?")) {
            stmt.setString(1, name);
            if (stmt.executeUpdate() != 1) {
                throw new SQLException("No such refill item: " + name);
            }
        }
    }
}
