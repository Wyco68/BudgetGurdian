package com.budgetguardian.repository;

import com.budgetguardian.datastructures.DoublyLinkedList;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JDBC access to the {@code txn} table.
 *
 * <p><b>Purpose:</b> hydrates the full ledger — chronologically ordered —
 * into a {@code DoublyLinkedList<Transaction>} at startup and persists every
 * mutation (insert on record, delete on undo, update on edit).</p>
 *
 * <p><b>Time complexity:</b> {@code findAll} O(n) single pass, appending at
 * the list tail in O(1) each; insert/update/delete O(log n) inside SQLite
 * (B-tree by primary key), O(1) statements here.</p>
 */
public final class TransactionRepository {

    private final Connection connection;

    public TransactionRepository(Connection connection) {
        this.connection = connection;
    }

    /** @return full ledger ordered by date then id (stable chronology). */
    public DoublyLinkedList<Transaction> findAll() throws SQLException {
        DoublyLinkedList<Transaction> ledger = new DoublyLinkedList<>();
        String sql = "SELECT id, type, account_id, category_id, item_name, amount_satang, reason, "
                + "txn_date, created_at FROM txn ORDER BY txn_date, id";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                ledger.addLast(mapRow(rs));
            }
        }
        return ledger;
    }

    /**
     * Inserts one transaction.
     *
     * @return the same transaction with its generated id
     */
    public Transaction insert(Transaction txn) throws SQLException {
        String sql = "INSERT INTO txn (type, account_id, category_id, item_name, amount_satang, reason, "
                + "txn_date, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(stmt, txn);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                keys.next();
                return txn.withId(keys.getLong(1));
            }
        }
    }

    /** Rewrites all columns of an existing transaction (edit feature). */
    public void update(Transaction txn) throws SQLException {
        String sql = "UPDATE txn SET type = ?, account_id = ?, category_id = ?, item_name = ?, "
                + "amount_satang = ?, reason = ?, txn_date = ?, created_at = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            bind(stmt, txn);
            stmt.setLong(9, txn.id());
            if (stmt.executeUpdate() != 1) {
                throw new SQLException("No such transaction: " + txn.id());
            }
        }
    }

    /** Deletes by id (undo of an add). */
    public void delete(long id) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM txn WHERE id = ?")) {
            stmt.setLong(1, id);
            if (stmt.executeUpdate() != 1) {
                throw new SQLException("No such transaction: " + id);
            }
        }
    }

    private static void bind(PreparedStatement stmt, Transaction txn) throws SQLException {
        stmt.setString(1, txn.type().name());
        stmt.setString(2, txn.accountId());
        if (txn.categoryId() != null) {
            stmt.setInt(3, txn.categoryId());
        } else {
            stmt.setNull(3, Types.INTEGER);
        }
        stmt.setString(4, txn.itemName());
        stmt.setLong(5, txn.amountSatang());
        stmt.setString(6, txn.reason());
        stmt.setString(7, txn.date().toString());
        stmt.setString(8, txn.createdAt().toString());
    }

    private static Transaction mapRow(ResultSet rs) throws SQLException {
        int categoryId = rs.getInt("category_id");
        boolean categoryNull = rs.wasNull();
        return new Transaction(
                rs.getLong("id"),
                TransactionType.valueOf(rs.getString("type")),
                rs.getString("account_id"),
                categoryNull ? null : categoryId,
                rs.getString("item_name"),
                rs.getLong("amount_satang"),
                rs.getString("reason"),
                LocalDate.parse(rs.getString("txn_date")),
                LocalDateTime.parse(rs.getString("created_at")));
    }
}
