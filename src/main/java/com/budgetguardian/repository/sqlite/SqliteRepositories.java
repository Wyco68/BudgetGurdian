package com.budgetguardian.repository.sqlite;

import com.budgetguardian.repository.Repositories;

import java.sql.Connection;

/**
 * Factory assembling the full SQLite-backed {@link Repositories} bundle from
 * one live JDBC connection (owned by {@code DatabaseManager}).
 */
public final class SqliteRepositories {

    private SqliteRepositories() {
    }

    /** @return every repository plus the transactional runner, SQLite-backed. */
    public static Repositories create(Connection connection) {
        return new Repositories(
                new SqliteTransactionRunner(connection),
                new SqliteAccountRepository(connection),
                new SqliteCategoryRepository(connection),
                new SqliteTransactionRepository(connection),
                new SqliteTransferRepository(connection),
                new SqliteDebtRepository(connection),
                new SqliteRefillRepository(connection),
                new SqliteSettingsRepository(connection),
                new SqliteBillRepository(connection));
    }
}
