package com.budgetguardian.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One partial payment on a debt. For payable debts the account balance
 * decreases; for receivable debts it increases.
 *
 * @param id           database id; 0 before first insert
 * @param debtId       owning debt
 * @param accountId    account the money moved on
 * @param amountSatang positive amount in satang
 * @param paymentDate  day of the payment
 * @param createdAt    timestamp the row was recorded
 */
public record DebtPayment(
        long id,
        long debtId,
        String accountId,
        long amountSatang,
        LocalDate paymentDate,
        LocalDateTime createdAt) {

    public DebtPayment {
        if (accountId == null || accountId.isBlank()) {
            throw new IllegalArgumentException("Account id must not be blank");
        }
        if (amountSatang <= 0) {
            throw new IllegalArgumentException("Amount must be positive, got " + amountSatang);
        }
        if (paymentDate == null || createdAt == null) {
            throw new IllegalArgumentException("Dates must not be null");
        }
    }

    /** @return copy with the database-generated id set after insert. */
    public DebtPayment withId(long generatedId) {
        return new DebtPayment(generatedId, debtId, accountId, amountSatang, paymentDate, createdAt);
    }
}
