package com.budgetguardian.repository;

import com.budgetguardian.datastructures.DoublyLinkedList;
import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.model.Debt;
import com.budgetguardian.model.DebtDirection;
import com.budgetguardian.model.DebtPayment;
import com.budgetguardian.model.DebtStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * JDBC access to the {@code debt} and {@code debt_payment} tables.
 *
 * <p><b>Purpose:</b> hydrates debts into a {@code HashMap<Long, Debt>} (O(1)
 * lookup) and each debt's partial-payment history into a chronological
 * {@code DoublyLinkedList<DebtPayment>}. Settlement status is derived by the
 * service layer (payments sum ≥ amount) and persisted here.</p>
 *
 * <p><b>Time complexity:</b> {@code findAll}/{@code findAllPayments} O(n)
 * single pass; single-row writes O(1) statements.</p>
 */
public final class DebtRepository {

    private final Connection connection;

    public DebtRepository(Connection connection) {
        this.connection = connection;
    }

    /** @return all debts keyed by id. */
    public HashMap<Long, Debt> findAll() throws SQLException {
        HashMap<Long, Debt> debts = new HashMap<>();
        String sql = "SELECT id, direction, person, amount_satang, due_date, status, settled_date, created_at "
                + "FROM debt ORDER BY id";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Debt debt = mapDebt(rs);
                debts.put(debt.id(), debt);
            }
        }
        return debts;
    }

    /** @return every payment of every debt, chronological (payment date then id). */
    public DoublyLinkedList<DebtPayment> findAllPayments() throws SQLException {
        DoublyLinkedList<DebtPayment> payments = new DoublyLinkedList<>();
        String sql = "SELECT id, debt_id, account_id, amount_satang, payment_date, created_at "
                + "FROM debt_payment ORDER BY payment_date, id";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                payments.addLast(mapPayment(rs));
            }
        }
        return payments;
    }

    /**
     * Inserts one debt.
     *
     * @return the same debt with its generated id
     */
    public Debt insert(Debt debt) throws SQLException {
        String sql = "INSERT INTO debt (direction, person, amount_satang, due_date, status, settled_date, "
                + "created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, debt.direction().name());
            stmt.setString(2, debt.person());
            stmt.setLong(3, debt.amountSatang());
            setNullableDate(stmt, 4, debt.dueDate());
            stmt.setString(5, debt.status().name());
            setNullableDate(stmt, 6, debt.settledDate());
            stmt.setString(7, debt.createdAt().toString());
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                keys.next();
                return debt.withId(keys.getLong(1));
            }
        }
    }

    /** Persists a status flip (OPEN ↔ SETTLED) with its settled date. */
    public void updateStatus(Debt debt) throws SQLException {
        String sql = "UPDATE debt SET status = ?, settled_date = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, debt.status().name());
            setNullableDate(stmt, 2, debt.settledDate());
            stmt.setLong(3, debt.id());
            if (stmt.executeUpdate() != 1) {
                throw new SQLException("No such debt: " + debt.id());
            }
        }
    }

    /** Deletes a debt; its payments cascade at the schema level. */
    public void delete(long debtId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM debt WHERE id = ?")) {
            stmt.setLong(1, debtId);
            if (stmt.executeUpdate() != 1) {
                throw new SQLException("No such debt: " + debtId);
            }
        }
    }

    /**
     * Inserts one partial payment.
     *
     * @return the same payment with its generated id
     */
    public DebtPayment insertPayment(DebtPayment payment) throws SQLException {
        String sql = "INSERT INTO debt_payment (debt_id, account_id, amount_satang, payment_date, created_at) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setLong(1, payment.debtId());
            stmt.setString(2, payment.accountId());
            stmt.setLong(3, payment.amountSatang());
            stmt.setString(4, payment.paymentDate().toString());
            stmt.setString(5, payment.createdAt().toString());
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                keys.next();
                return payment.withId(keys.getLong(1));
            }
        }
    }

    /** Deletes one payment (undo of a partial payment). */
    public void deletePayment(long paymentId) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM debt_payment WHERE id = ?")) {
            stmt.setLong(1, paymentId);
            if (stmt.executeUpdate() != 1) {
                throw new SQLException("No such payment: " + paymentId);
            }
        }
    }

    private static Debt mapDebt(ResultSet rs) throws SQLException {
        String dueDate = rs.getString("due_date");
        String settledDate = rs.getString("settled_date");
        return new Debt(
                rs.getLong("id"),
                DebtDirection.valueOf(rs.getString("direction")),
                rs.getString("person"),
                rs.getLong("amount_satang"),
                dueDate != null ? LocalDate.parse(dueDate) : null,
                DebtStatus.valueOf(rs.getString("status")),
                settledDate != null ? LocalDate.parse(settledDate) : null,
                LocalDateTime.parse(rs.getString("created_at")));
    }

    private static DebtPayment mapPayment(ResultSet rs) throws SQLException {
        return new DebtPayment(
                rs.getLong("id"),
                rs.getLong("debt_id"),
                rs.getString("account_id"),
                rs.getLong("amount_satang"),
                LocalDate.parse(rs.getString("payment_date")),
                LocalDateTime.parse(rs.getString("created_at")));
    }

    private static void setNullableDate(PreparedStatement stmt, int index, LocalDate date)
            throws SQLException {
        if (date != null) {
            stmt.setString(index, date.toString());
        } else {
            stmt.setNull(index, Types.VARCHAR);
        }
    }
}
