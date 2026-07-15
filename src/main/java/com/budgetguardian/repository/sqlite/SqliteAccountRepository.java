package com.budgetguardian.repository.sqlite;

import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.model.Account;
import com.budgetguardian.repository.AccountRepository;
import com.budgetguardian.repository.StorageException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JDBC access to the {@code account} table.
 *
 * <p><b>Purpose:</b> hydrates the four fixed accounts into a
 * {@code HashMap<String, Account>} at startup and persists balance changes.
 * Accounts are seeded by the schema script — this repository never inserts
 * or deletes them.</p>
 *
 * <p><b>Time complexity:</b> {@code findAll} O(n) single pass (n = 4);
 * {@code updateBalance} O(1) — primary-key indexed.</p>
 */
public final class SqliteAccountRepository implements AccountRepository {

    private final Connection connection;

    public SqliteAccountRepository(Connection connection) {
        this.connection = connection;
    }

    @Override
    public HashMap<String, Account> findAll() throws StorageException {
        HashMap<String, Account> accounts = new HashMap<>();
        String sql = "SELECT id, name, balance_satang, display_order FROM account ORDER BY display_order";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Account account = new Account(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getLong("balance_satang"),
                        rs.getInt("display_order"));
                accounts.put(account.id(), account);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to load accounts", e);
        }
        return accounts;
    }

    @Override
    public void updateBalance(String accountId, long newBalanceSatang) throws StorageException {
        String sql = "UPDATE account SET balance_satang = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, newBalanceSatang);
            stmt.setString(2, accountId);
            if (stmt.executeUpdate() != 1) {
                throw new StorageException("No such account: " + accountId);
            }
        } catch (SQLException e) {
            throw new StorageException("Failed to update balance of account " + accountId, e);
        }
    }
}
