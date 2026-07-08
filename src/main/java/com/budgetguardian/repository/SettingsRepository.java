package com.budgetguardian.repository;

import com.budgetguardian.datastructures.HashMap;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * JDBC access to the {@code setting} key-value table.
 *
 * <p><b>Purpose:</b> hydrates settings (daily budget, danger weekly limit,
 * reminder time) into a {@code HashMap<String, String>} and persists edits
 * from the Settings screen.</p>
 *
 * <p><b>Time complexity:</b> {@code findAll} O(n) single pass;
 * {@code put} O(1) upsert.</p>
 */
public final class SettingsRepository {

    private final Connection connection;

    public SettingsRepository(Connection connection) {
        this.connection = connection;
    }

    /** @return all settings keyed by name. */
    public HashMap<String, String> findAll() throws SQLException {
        HashMap<String, String> settings = new HashMap<>();
        try (PreparedStatement stmt = connection.prepareStatement("SELECT key, value FROM setting");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                settings.put(rs.getString("key"), rs.getString("value"));
            }
        }
        return settings;
    }

    /** Inserts or replaces one setting. */
    public void put(String key, String value) throws SQLException {
        String sql = "INSERT INTO setting (key, value) VALUES (?, ?) "
                + "ON CONFLICT(key) DO UPDATE SET value = excluded.value";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.executeUpdate();
        }
    }
}
