package com.budgetguardian.repository.sqlite;

import com.budgetguardian.datastructures.DoublyLinkedList;
import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.model.Debt;
import com.budgetguardian.model.DebtDirection;
import com.budgetguardian.model.DebtPayment;
import com.budgetguardian.model.DebtStatus;
import com.budgetguardian.repository.DebtRepository;
import com.budgetguardian.repository.StorageException;

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
public final class SqliteDebtRepository implements DebtRepository {

    private final Connection connection;

    public SqliteDebtRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public HashMap<Long, Debt> findAll() throws StorageException {
        HashMap<Long, Debt> debts = new HashMap<>();
        String sql = "SELECT id, direction, person, amount_satang, occurred_date, due_date, status, "
                + "settled_date, created_at FROM debt ORDER BY id";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Debt debt = mapDebt(rs);
                debts.put(debt.id(), debt);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to load debts", e);
        }
        return debts;
    }

    @Override
    public DoublyLinkedList<DebtPayment> findAllPayments() throws StorageException {
        DoublyLinkedList<DebtPayment> payments = new DoublyLinkedList<>();
        String sql = "SELECT id, debt_id, account_id, amount_satang, payment_date, created_at "
                + "FROM debt_payment ORDER BY payment_date, id";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                payments.addLast(mapPayment(rs));
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to load debt payments", e);
        }
        return payments;
    }

    @Override
    public Debt insert(Debt debt) throws StorageException {
        String sql = "INSERT INTO debt (direction, person, amount_satang, occurred_date, due_date, status, "
                + "settled_date, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, debt.direction().name());
            stmt.setString(2, debt.person());
            stmt.setLong(3, debt.amountSatang());
            setNullableDate(stmt, 4, debt.occurredDate());
            setNullableDate(stmt, 5, debt.dueDate());
            stmt.setString(6, debt.status().name());
            setNullableDate(stmt, 7, debt.settledDate());
            stmt.setString(8, debt.createdAt().toString());
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                keys.next();
                return debt.withId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to insert debt", e);
        }
    }

    @Override
    public void updateStatus(Debt debt) throws StorageException {
        String sql = "UPDATE debt SET status = ?, settled_date = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, debt.status().name());
            setNullableDate(stmt, 2, debt.settledDate());
            stmt.setLong(3, debt.id());
            if (stmt.executeUpdate() != 1) {
                throw new StorageException("No such debt: " + debt.id());
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to update debt " + debt.id(), e);
        }
    }

    @Override
    public void delete(long debtId) throws StorageException {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM debt WHERE id = ?")) {
            stmt.setLong(1, debtId);
            if (stmt.executeUpdate() != 1) {
                throw new StorageException("No such debt: " + debtId);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to delete debt " + debtId, e);
        }
    }

    @Override
    public DebtPayment insertPayment(DebtPayment payment) throws StorageException {
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
        } catch (SQLException e) {
            throw new StorageException("Failed to insert debt payment", e);
        }
    }

    @Override
    public void deletePayment(long paymentId) throws StorageException {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM debt_payment WHERE id = ?")) {
            stmt.setLong(1, paymentId);
            if (stmt.executeUpdate() != 1) {
                throw new StorageException("No such payment: " + paymentId);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to delete payment " + paymentId, e);
        }
    }

    private static Debt mapDebt(ResultSet rs) throws SQLException {
        String occurredDate = rs.getString("occurred_date");
        String dueDate = rs.getString("due_date");
        String settledDate = rs.getString("settled_date");
        return new Debt(
                rs.getLong("id"),
                DebtDirection.valueOf(rs.getString("direction")),
                rs.getString("person"),
                rs.getLong("amount_satang"),
                occurredDate != null ? LocalDate.parse(occurredDate) : null,
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
