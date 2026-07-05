package com.budgetguardian.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies schema creation, seeding, idempotency and connection lifecycle
 * of {@link DatabaseManager}.
 */
class DatabaseManagerTest {

    @TempDir
    Path tempDir;

    private Path dbFile() {
        return tempDir.resolve("test-budget.db");
    }

    @Test
    void openCreatesAllTables() throws SQLException {
        try (DatabaseManager db = new DatabaseManager(dbFile())) {
            db.open();
            for (String table : new String[] {
                    "account", "category", "txn", "transfer",
                    "debt", "debt_payment", "refill_item", "setting"}) {
                assertTrue(tableExists(db.getConnection(), table), "missing table: " + table);
            }
        }
    }

    @Test
    void seedsFourAccountsElevenCategoriesAndSettings() throws SQLException {
        try (DatabaseManager db = new DatabaseManager(dbFile())) {
            db.open();
            Connection conn = db.getConnection();
            assertEquals(4, count(conn, "account"));
            assertEquals(11, count(conn, "category"));
            assertEquals(2, scalar(conn, "SELECT COUNT(*) FROM category WHERE is_danger = 1"));
            assertEquals(3, count(conn, "setting"));
            assertEquals(18000, scalar(conn,
                    "SELECT CAST(value AS INTEGER) FROM setting WHERE key = 'daily_budget'"));
        }
    }

    @Test
    void reopeningExistingDatabaseIsIdempotent() throws SQLException {
        Path file = dbFile();
        try (DatabaseManager db = new DatabaseManager(file)) {
            db.open();
        }
        try (DatabaseManager db = new DatabaseManager(file)) {
            db.open();
            assertEquals(4, count(db.getConnection(), "account"));
            assertEquals(11, count(db.getConnection(), "category"));
        }
    }

    @Test
    void foreignKeysAreEnforced() throws SQLException {
        try (DatabaseManager db = new DatabaseManager(dbFile())) {
            db.open();
            try (Statement stmt = db.getConnection().createStatement()) {
                assertThrows(SQLException.class, () -> stmt.executeUpdate(
                        "INSERT INTO txn (type, account_id, amount_satang, reason, txn_date, created_at) "
                        + "VALUES ('EXPENSE', 'NO_SUCH_ACCOUNT', 100, 'x', '2026-07-06', '2026-07-06T12:00:00')"));
            }
        }
    }

    @Test
    void getConnectionBeforeOpenFails() {
        DatabaseManager db = new DatabaseManager(dbFile());
        assertThrows(IllegalStateException.class, db::getConnection);
    }

    @Test
    void openTwiceFails() throws SQLException {
        try (DatabaseManager db = new DatabaseManager(dbFile())) {
            db.open();
            assertThrows(IllegalStateException.class, db::open);
        }
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = '" + table + "'")) {
            return rs.next() && rs.getInt(1) == 1;
        }
    }

    private static int count(Connection conn, String table) throws SQLException {
        return scalar(conn, "SELECT COUNT(*) FROM " + table);
    }

    private static int scalar(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
