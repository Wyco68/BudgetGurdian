package com.budgetguardian.repository;

import com.budgetguardian.database.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

/** Shared fixture: each test runs against a fresh temp-file SQLite database. */
abstract class RepositoryTestBase {

    @TempDir
    Path tempDir;

    private DatabaseManager db;
    Connection connection;

    @BeforeEach
    void openDatabase() throws SQLException {
        db = new DatabaseManager(tempDir.resolve("repo-test.db"));
        db.open();
        connection = db.getConnection();
    }

    @AfterEach
    void closeDatabase() throws SQLException {
        db.close();
    }
}
