package com.budgetguardian.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A recurring bill. {@code payday} (1-31) is the day of the month it's due;
 * null for a one-off/non-monthly bill that never triggers a reminder.
 * Paying a bill logs a normal Bill-category {@link Transaction} and bumps
 * {@link #lastPaidDate()} — there is no separate payment ledger.
 *
 * @param id             database id; 0 before first insert
 * @param name           bill name, e.g. "Internet"
 * @param amountSatang   default amount in satang, positive
 * @param payday         day of month (1-31) it's due, or null if not recurring
 * @param lastPaidDate   most recent payment date, or null if never paid
 * @param createdAt      timestamp the bill was created
 */
public record Bill(long id, String name, long amountSatang, Integer payday,
                   LocalDate lastPaidDate, LocalDateTime createdAt) {

    public Bill {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Bill name must not be blank");
        }
        if (amountSatang <= 0) {
            throw new IllegalArgumentException("Amount must be positive, got " + amountSatang);
        }
        if (payday != null && (payday < 1 || payday > 31)) {
            throw new IllegalArgumentException("Payday must be 1-31, got " + payday);
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Created-at must not be null");
        }
    }

    /** @return whether this bill recurs monthly (has a payday). */
    public boolean isRecurring() {
        return payday != null;
    }

    /**
     * @return whether the bill is due and unpaid: recurring, today's day of
     *         month has reached the payday, and it hasn't been paid this
     *         calendar month yet
     */
    public boolean isDue(LocalDate today) {
        if (payday == null || today.getDayOfMonth() < payday) {
            return false;
        }
        return lastPaidDate == null
                || lastPaidDate.getYear() != today.getYear()
                || lastPaidDate.getMonthValue() != today.getMonthValue();
    }

    /** @return copy with the database-generated id set after insert. */
    public Bill withId(long generatedId) {
        return new Bill(generatedId, name, amountSatang, payday, lastPaidDate, createdAt);
    }

    /** @return copy recording a payment on {@code paidDate}. */
    public Bill withLastPaidDate(LocalDate paidDate) {
        return new Bill(id, name, amountSatang, payday, paidDate, createdAt);
    }
}
