package com.budgetguardian.repository.sqlite;

import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.model.Bill;
import com.budgetguardian.repository.BillRepository;
import com.budgetguardian.repository.StorageException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** JDBC access to the {@code bill} table. */
public final class SqliteBillRepository implements BillRepository {

    private final Connection connection;

    public SqliteBillRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public HashMap<Long, Bill> findAll() throws StorageException {
        HashMap<Long, Bill> bills = new HashMap<>();
        String sql = "SELECT id, name, amount_satang, payday, last_paid_date, created_at FROM bill ORDER BY id";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Bill bill = mapBill(rs);
                bills.put(bill.id(), bill);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to load bills", e);
        }
        return bills;
    }

    @Override
    public Bill insert(Bill bill) throws StorageException {
        String sql = "INSERT INTO bill (name, amount_satang, payday, last_paid_date, created_at) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, bill.name());
            stmt.setLong(2, bill.amountSatang());
            setNullableInt(stmt, 3, bill.payday());
            setNullableDate(stmt, 4, bill.lastPaidDate());
            stmt.setString(5, bill.createdAt().toString());
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                keys.next();
                return bill.withId(keys.getLong(1));
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to insert bill", e);
        }
    }

    @Override
    public void updateLastPaid(Bill bill) throws StorageException {
        String sql = "UPDATE bill SET last_paid_date = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            setNullableDate(stmt, 1, bill.lastPaidDate());
            stmt.setLong(2, bill.id());
            if (stmt.executeUpdate() != 1) {
                throw new StorageException("No such bill: " + bill.id());
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to update bill " + bill.id(), e);
        }
    }

    @Override
    public void delete(long billId) throws StorageException {
        try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM bill WHERE id = ?")) {
            stmt.setLong(1, billId);
            if (stmt.executeUpdate() != 1) {
                throw new StorageException("No such bill: " + billId);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to delete bill " + billId, e);
        }
    }

    private static Bill mapBill(ResultSet rs) throws SQLException {
        int payday = rs.getInt("payday");
        boolean paydayNull = rs.wasNull();
        String lastPaid = rs.getString("last_paid_date");
        return new Bill(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getLong("amount_satang"),
                paydayNull ? null : payday,
                lastPaid != null ? LocalDate.parse(lastPaid) : null,
                LocalDateTime.parse(rs.getString("created_at")));
    }

    private static void setNullableInt(PreparedStatement stmt, int index, Integer value) throws SQLException {
        if (value != null) {
            stmt.setInt(index, value);
        } else {
            stmt.setNull(index, Types.INTEGER);
        }
    }

    private static void setNullableDate(PreparedStatement stmt, int index, LocalDate date) throws SQLException {
        if (date != null) {
            stmt.setString(index, date.toString());
        } else {
            stmt.setNull(index, Types.VARCHAR);
        }
    }
}
