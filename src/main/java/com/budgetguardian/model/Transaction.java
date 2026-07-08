package com.budgetguardian.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One ledger entry: expense, income or withdrawal.
 *
 * <p>Immutable; lives chronologically in the
 * {@code DoublyLinkedList<Transaction>} ledger and in the recent-transactions
 * {@code CircularBuffer}. Amounts are always positive — the {@link #type()}
 * decides the balance direction.</p>
 *
 * @param id           database id; 0 before first insert
 * @param type         expense, income or withdrawal
 * @param accountId    account the money moved on
 * @param categoryId   spending category; null for income/withdrawal
 * @param itemName     purchased item (drives refill detection); null when not applicable
 * @param amountSatang positive amount in satang
 * @param reason       user-entered reason, required
 * @param date         day the transaction happened
 * @param createdAt    timestamp the row was recorded
 */
public record Transaction(
        long id,
        TransactionType type,
        String accountId,
        Integer categoryId,
        String itemName,
        long amountSatang,
        String reason,
        LocalDate date,
        LocalDateTime createdAt) {

    public Transaction {
        if (type == null) {
            throw new IllegalArgumentException("Transaction type must not be null");
        }
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Account id must not be blank");
        }
        if (amountSatang <= 0) {
            throw new IllegalArgumentException("Amount must be positive, got " + amountSatang);
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reason must not be blank");
        }
        if (date == null || createdAt == null) {
            throw new IllegalArgumentException("Dates must not be null");
        }
        if (type == TransactionType.EXPENSE && categoryId == null) {
            throw new IllegalArgumentException("Expenses require a category");
        }
        if (type != TransactionType.EXPENSE && categoryId != null) {
            throw new IllegalArgumentException(type + " must not carry a category");
        }
    }

    /** @return copy with the database-generated id set after insert. */
    public Transaction withId(long generatedId) {
        return new Transaction(generatedId, type, accountId, categoryId, itemName,
                amountSatang, reason, date, createdAt);
    }
}
