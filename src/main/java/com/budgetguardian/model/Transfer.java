package com.budgetguardian.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Money moved between two accounts. Never an expense, never income — only the
 * two balances change. Each transfer is also one directed edge in the
 * transfer {@code Graph}.
 *
 * @param id           database id; 0 before first insert
 * @param fromAccount  source account id
 * @param toAccount    target account id, different from source
 * @param amountSatang positive amount in satang
 * @param reason       user-entered reason, required (e.g. "ATM withdrawal")
 * @param date         day of the transfer
 * @param createdAt    timestamp the row was recorded
 */
public record Transfer(
        long id,
        String fromAccount,
        String toAccount,
        long amountSatang,
        String reason,
        LocalDate date,
        LocalDateTime createdAt) {

    public Transfer {
        if (fromAccount == null || fromAccount.isBlank()
                || toAccount == null || toAccount.isBlank()) {
            throw new IllegalArgumentException("Transfer endpoints must not be blank");
        }
        if (fromAccount.equals(toAccount)) {
            throw new IllegalArgumentException("Transfer endpoints must differ");
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
    }

    /** @return copy with the database-generated id set after insert. */
    public Transfer withId(long generatedId) {
        return new Transfer(generatedId, fromAccount, toAccount, amountSatang, reason, date, createdAt);
    }
}
