package com.budgetguardian.repository;

/**
 * Bundle of every repository plus the {@link TransactionRunner} — one object
 * the composition root hands to the service layer.
 *
 * <p><b>Purpose:</b> the service layer depends only on the interfaces in this
 * package; which backing store is used (SQLite file or REST backend) is
 * decided once, where this record is built. Factories:
 * {@code repository.sqlite.SqliteRepositories} and
 * {@code repository.api.ApiRepositories}.</p>
 */
public record Repositories(
        TransactionRunner runner,
        AccountRepository accounts,
        CategoryRepository categories,
        TransactionRepository transactions,
        TransferRepository transfers,
        DebtRepository debts,
        RefillRepository refills,
        SettingsRepository settings) {
}
