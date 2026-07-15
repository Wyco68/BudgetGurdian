package com.budgetguardian.repository.sqlite;

import com.budgetguardian.repository.StorageException;
import com.budgetguardian.repository.TransactionRunner;

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
 * <p><b>Time complexity:</b> O(1) overhead around the wrapped work.</p>
 */
public final class SqliteTransactionRunner implements TransactionRunner {

    private final Connection connection;

    public SqliteTransactionRunner(Connection connection) {
        this.connection = connection;
    }

    /**
     * Runs {@code work} inside a transaction: commit on success, rollback on
     * any exception (which is rethrown).
     *
     * @throws StorageException from the work or the commit
     */
    @Override
    public <T> T run(Work<T> work) throws StorageException {
        try {
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
        } catch (StorageException e) {
            throw e;
        } catch (SQLException e) {
            throw new StorageException("Transaction failed", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new StorageException("Transaction failed", e);
        }
    }
}
