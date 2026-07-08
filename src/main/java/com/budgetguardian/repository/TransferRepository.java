package com.budgetguardian.repository;

import com.budgetguardian.datastructures.DoublyLinkedList;
import com.budgetguardian.model.Transfer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JDBC access to the {@code transfer} table.
 *
 * <p><b>Purpose:</b> hydrates transfer history — chronological — into a
 * {@code DoublyLinkedList<Transfer>}; each row also becomes one edge of the
 * transfer {@code Graph} during startup rebuild.</p>
 *
 * <p><b>Time complexity:</b> {@code findAll} O(n) single pass; insert/delete
 * O(1) statements.</p>
 */
public final class TransferRepository {

    private final Connection connection;

    public TransferRepository(Connection connection) {
        this.connection = connection;
    }

    /** @return full transfer history ordered by date then id. */
    public DoublyLinkedList<Transfer> findAll() throws SQLException {
        DoublyLinkedList<Transfer> transfers = new DoublyLinkedList<>();
        String sql = "SELECT id, from_account, to_account, amount_satang, reason, transfer_date, created_at "
                + "FROM transfer ORDER BY transfer_date, id";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                transfers.addLast(new Transfer(
                        rs.getLong("id"),
                        rs.getString("from_account"),
                        rs.getString("to_account"),
                        rs.getLong("amount_satang"),
                        rs.getString("reason"),
                        LocalDate.parse(rs.getString("transfer_date")),
                        LocalDateTime.parse(rs.getString("created_at"))));
            }
        }
        return transfers;
    }

    /**
     * Inserts one transfer.
     *
     * @return the same transfer with its generated id
     */
    public Transfer insert(Transfer transfer) throws SQLException {
        String sql = "INSERT INTO transfer (from_account, to_account, amount_satang, reason, "
                + "transfer_date, created_at) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, transfer.fromAccount());
            stmt.setString(2, transfer.toAccount());
            stmt.setLong(3, transfer.amountSatang());
            stmt.setString(4, transfer.reason());
            stmt.setString(5, transfer.date().toString());
            stmt.setString(6, transfer.createdAt().toString());
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                keys.next();
                return transfer.withId(keys.getLong(1));
            }
        }
    }

    /** Deletes by id (undo of a transfer). */
    public void delete(long id) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM transfer WHERE id = ?")) {
            stmt.setLong(1, id);
            if (stmt.executeUpdate() != 1) {
                throw new SQLException("No such transfer: " + id);
            }
        }
    }
}
