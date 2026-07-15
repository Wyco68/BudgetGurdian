package com.budgetguardian.repository.sqlite;

import com.budgetguardian.datastructures.DoublyLinkedList;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;
import com.budgetguardian.repository.StorageException;
import com.budgetguardian.repository.TransactionRepository;

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
public final class SqliteTransactionRepository implements TransactionRepository {

    private final Connection connection;

    public SqliteTransactionRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public DoublyLinkedList<Transaction> findAll() throws StorageException {
        DoublyLinkedList<Transaction> ledger = new DoublyLinkedList<>();
        String sql = "SELECT id, type, account_id, category_id, item_name, amount_satang, reason, "
                + "txn_date, created_at FROM txn ORDER BY txn_date, id";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                ledger.addLast(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to load transactions", e);
        }
        return ledger;
    }

    @Override
    public Transaction insert(Transaction txn) throws StorageException {
        String sql = "INSERT INTO txn (type, account_id, category_id, item_name, amount_satang, reason, "
                + "txn_date, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(stmt, txn);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                keys.next();
                return txn.withId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to insert transaction", e);
        }
    }

    @Override
    public void restore(Transaction txn) throws StorageException {
        String sql = "INSERT INTO txn (id, type, account_id, category_id, item_name, amount_satang, reason, "
                + "txn_date, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, txn.id());
            stmt.setString(2, txn.type().name());
            stmt.setString(3, txn.accountId());
            if (txn.categoryId() != null) {
                stmt.setInt(4, txn.categoryId());
            } else {
                stmt.setNull(4, Types.INTEGER);
            }
            stmt.setString(5, txn.itemName());
            stmt.setLong(6, txn.amountSatang());
            stmt.setString(7, txn.reason());
            stmt.setString(8, txn.date().toString());
            stmt.setString(9, txn.createdAt().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Failed to restore transaction " + txn.id(), e);
        }
    }

    @Override
    public void update(Transaction txn) throws StorageException {
        String sql = "UPDATE txn SET type = ?, account_id = ?, category_id = ?, item_name = ?, "
                + "amount_satang = ?, reason = ?, txn_date = ?, created_at = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            bind(stmt, txn);
            stmt.setLong(9, txn.id());
            if (stmt.executeUpdate() != 1) {
                throw new StorageException("No such transaction: " + txn.id());
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to update transaction " + txn.id(), e);
        }
    }

    @Override
    public void delete(long id) throws StorageException {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM txn WHERE id = ?")) {
            stmt.setLong(1, id);
            if (stmt.executeUpdate() != 1) {
                throw new StorageException("No such transaction: " + id);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to delete transaction " + id, e);
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
