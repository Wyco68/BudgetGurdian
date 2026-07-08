package com.budgetguardian.repository;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Wraps multi-statement repository work in one SQL transaction.
 *
 * <p><b>Purpose:</b> service-layer operations often touch several tables
 * (insert expense + update balance; insert transfer + update two balances;
 * insert payment + flip debt status). Either all rows land or none — this
 * class is the only place commit/rollback logic lives.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * long id = runner.run(() -> {
 *     long txnId = transactionRepository.insert(txn);
 *     accountRepository.updateBalance(accountId, newBalance);
 *     return txnId;
 * });
 * }</pre>
 *
 * <p><b>Time complexity:</b> O(1) overhead around the wrapped work.</p>
 */
public final class TransactionRunner {

    /**
     * Unit of SQL work executed atomically.
     *
     * @param <T> result type
     */
    @FunctionalInterface
    public interface SqlWork<T> {
        T execute() throws SQLException;
    }

    private final Connection connection;

    public TransactionRunner(Connection connection) {
        this.connection = connection;
    }

    /**
     * Runs {@code work} inside a transaction: commit on success, rollback on
     * any exception (which is rethrown).
     *
     * @throws SQLException from the work or the commit
     */
    public <T> T run(SqlWork<T> work) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            T result = work.execute();
            connection.commit();
            return result;
        } catch (Exception e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }
}
