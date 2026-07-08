package com.budgetguardian.model;

/**
 * Kind of money movement recorded in the ledger.
 *
 * <p>Transfers are deliberately <em>not</em> a transaction type — they move
 * money between accounts without being income or expense, and live in their
 * own {@link Transfer} model and SQL table.</p>
 */
public enum TransactionType {
    /** Money spent from an account; has a category and optional item name. */
    EXPENSE,
    /** Money received into an account; reason required, no category. */
    INCOME,
    /** Money taken out of an account (e.g. cash out); reason required, no category. */
    WITHDRAWAL
}
