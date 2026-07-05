package com.budgetguardian.database;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the single SQLite connection used by the whole application.
 *
 * <p><b>Purpose:</b> open the database file, apply the idempotent schema
 * script ({@code /db/schema.sql}) with its seed rows (4 accounts,
 * 11 categories, default settings), and hand the live {@link Connection}
 * to the repository layer. This class is the only place JDBC bootstrapping
 * happens; repositories receive the connection, never create one.</p>
 *
 * <p><b>Responsibilities:</b></p>
 * <ul>
 *   <li>Open/close the connection (single long-lived connection — the app is
 *       single-user and all writes happen on one thread).</li>
 *   <li>Enable {@code PRAGMA foreign_keys} per connection (SQLite default is off).</li>
 *   <li>Execute the schema script on every startup; every statement in it is
 *       {@code IF NOT EXISTS} / {@code INSERT OR IGNORE}, so reruns are no-ops.</li>
 *   <li>Stamp {@code PRAGMA user_version} for future migrations.</li>
 * </ul>
 *
 * <p><b>Time complexity:</b> {@code open()} is O(s) in the number of schema
 * statements; all other methods O(1).
 * <b>Space complexity:</b> O(1) beyond the connection handle.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * try (DatabaseManager db = new DatabaseManager(Path.of("budget.db"))) {
 *     db.open();
 *     Connection conn = db.getConnection();
 *     // pass conn to repositories
 * }
 * }</pre>
 */
public final class DatabaseManager implements AutoCloseable {

    /** Classpath location of the idempotent schema script. */
    private static final String SCHEMA_RESOURCE = "/db/schema.sql";

    /** Current schema version, written to {@code PRAGMA user_version}. */
    private static final int SCHEMA_VERSION = 1;

    private final Path databaseFile;
    private Connection connection;

    /**
     * @param databaseFile path of the SQLite file; created on first open
     */
    public DatabaseManager(Path databaseFile) {
        this.databaseFile = databaseFile;
    }

    /**
     * Opens the connection, enables foreign keys, applies the schema script
     * and stamps the schema version. Safe to call once per instance.
     *
     * @throws SQLException          if the database cannot be opened or a
     *                               schema statement fails
     * @throws IllegalStateException if already open
     */
    public void open() throws SQLException {
        if (connection != null) {
            throw new IllegalStateException("Database already open: " + databaseFile);
        }
        connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
        }
        applySchema();
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("PRAGMA user_version = " + SCHEMA_VERSION);
        }
    }

    /**
     * @return the live connection
     * @throws IllegalStateException if {@link #open()} has not been called
     */
    public Connection getConnection() {
        if (connection == null) {
            throw new IllegalStateException("Database not open: " + databaseFile);
        }
        return connection;
    }

    /** Closes the connection; subsequent {@link #getConnection()} calls fail. */
    @Override
    public void close() throws SQLException {
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    /**
     * Reads {@code /db/schema.sql} from the classpath and executes it
     * statement by statement inside one transaction.
     *
     * <p>Comments are stripped first, then statements are split on {@code ';'}.
     * The script contains no string literals with semicolons; keep it that way
     * when editing the schema.</p>
     */
    private void applySchema() throws SQLException {
        String script = stripComments(readSchemaScript());
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement stmt = connection.createStatement()) {
            for (String raw : script.split(";")) {
                String sql = raw.trim();
                if (!sql.isEmpty()) {
                    stmt.execute(sql);
                }
            }
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private String readSchemaScript() throws SQLException {
        try (InputStream in = DatabaseManager.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new SQLException("Schema resource missing from classpath: " + SCHEMA_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SQLException("Failed to read schema resource: " + SCHEMA_RESOURCE, e);
        }
    }

    /** Removes {@code -- line comments} so bare comment blocks don't execute as empty statements. */
    private static String stripComments(String sql) {
        StringBuilder result = new StringBuilder(sql.length());
        for (String line : sql.split("\n")) {
            int comment = line.indexOf("--");
            result.append(comment >= 0 ? line.substring(0, comment) : line).append('\n');
        }
        return result.toString();
    }
}
