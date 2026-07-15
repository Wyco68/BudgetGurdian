package com.budgetguardian.repository;

import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;
import org.junit.jupiter.api.Test;

import com.budgetguardian.repository.StorageException;
import com.budgetguardian.repository.sqlite.SqliteAccountRepository;
import com.budgetguardian.repository.sqlite.SqliteTransactionRepository;
import com.budgetguardian.repository.sqlite.SqliteTransactionRunner;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Atomicity tests for {@link TransactionRunner}. */
class TransactionRunnerTest extends RepositoryTestBase {

    private static final LocalDate DAY = LocalDate.of(2026, 7, 6);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 6, 12, 0);

    private Transaction expense() {
        return new Transaction(0, TransactionType.EXPENSE, "SCB", 1, null, 4_500, "lunch", DAY, NOW);
    }

    @Test
    void commitsOnSuccess() throws Exception {
        TransactionRunner runner = new SqliteTransactionRunner(connection);
        TransactionRepository transactions = new SqliteTransactionRepository(connection);
        AccountRepository accounts = new SqliteAccountRepository(connection);

        long id = runner.run(() -> {
            Transaction saved = transactions.insert(expense());
            accounts.updateBalance("SCB", -4_500);
            return saved.id();
        });

        assertTrue(id > 0);
        assertEquals(1, transactions.findAll().size());
        assertEquals(-4_500, accounts.findAll().get("SCB").balanceSatang());
    }

    @Test
    void rollsBackEverythingOnFailure() throws Exception {
        TransactionRunner runner = new SqliteTransactionRunner(connection);
        TransactionRepository transactions = new SqliteTransactionRepository(connection);
        AccountRepository accounts = new SqliteAccountRepository(connection);

        assertThrows(StorageException.class, () -> runner.run(() -> {
            transactions.insert(expense());                    // would succeed alone
            accounts.updateBalance("KBANK", 1);                // unknown account → throws
            return null;
        }));

        assertTrue(transactions.findAll().isEmpty(), "insert must be rolled back");
        assertEquals(0, accounts.findAll().get("SCB").balanceSatang());
    }

    @Test
    void restoresAutoCommitAfterRun() throws Exception {
        TransactionRunner runner = new SqliteTransactionRunner(connection);
        runner.run(() -> 1);
        assertTrue(connection.getAutoCommit(), "auto-commit must be restored");
        assertThrows(StorageException.class, () -> runner.run(() -> {
            throw new StorageException("boom");
        }));
        assertTrue(connection.getAutoCommit(), "auto-commit must be restored after rollback too");
    }
}
