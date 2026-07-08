package com.budgetguardian.repository;

import com.budgetguardian.datastructures.HashMap;
import com.budgetguardian.model.Account;

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
public final class AccountRepository {

    private final Connection connection;

    public AccountRepository(Connection connection) {
        this.connection = connection;
    }

    /** @return all accounts keyed by id, hydrated in one pass. */
    public HashMap<String, Account> findAll() throws SQLException {
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
        }
        return accounts;
    }

    /**
     * Persists a new balance for one account.
     *
     * @throws SQLException if the account id does not exist (0 rows updated)
     */
    public void updateBalance(String accountId, long newBalanceSatang) throws SQLException {
        String sql = "UPDATE account SET balance_satang = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, newBalanceSatang);
            stmt.setString(2, accountId);
            if (stmt.executeUpdate() != 1) {
                throw new SQLException("No such account: " + accountId);
            }
        }
    }
}
