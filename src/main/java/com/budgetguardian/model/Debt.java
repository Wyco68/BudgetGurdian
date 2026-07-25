package com.budgetguardian.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A debt the user must pay or expects to receive. Supports partial payments:
 * settlement happens automatically when payments sum to {@link #amountSatang()}.
 *
 * <p>Stored in a {@code HashMap<Long, Debt>} for O(1) lookup; payment history
 * lives in a {@code DoublyLinkedList<DebtPayment>} per debt. Overdue open
 * debts raise a priority-90 hero alert.</p>
 *
 * @param id           database id; 0 before first insert
 * @param direction    payable (user owes) or receivable (user is owed)
 * @param person       counterparty name, required
 * @param amountSatang positive total amount in satang
 * @param occurredDate day the debt was incurred; a receivable's repayments
 *                     offset this day's spending total. Optional (may be null
 *                     for legacy rows), then no offset applies
 * @param dueDate      optional due date; overdue check uses it when present
 * @param status       open or settled
 * @param settledDate  day the debt became settled; null while open
 * @param createdAt    timestamp the row was recorded
 */
public record Debt(
        long id,
        DebtDirection direction,
        String person,
        long amountSatang,
        LocalDate occurredDate,
        LocalDate dueDate,
        DebtStatus status,
        LocalDate settledDate,
        LocalDateTime createdAt) {

    public Debt {
        if (direction == null) {
            throw new IllegalArgumentException("Debt direction must not be null");
        }
        if (person == null || person.isBlank()) {
            throw new IllegalArgumentException("Person must not be blank");
        }
        if (amountSatang <= 0) {
            throw new IllegalArgumentException("Amount must be positive, got " + amountSatang);
        }
        if (status == null) {
            throw new IllegalArgumentException("Status must not be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Created-at must not be null");
        }
        if (status == DebtStatus.SETTLED && settledDate == null) {
            throw new IllegalArgumentException("Settled debts need a settled date");
        }
        if (status == DebtStatus.OPEN && settledDate != null) {
            throw new IllegalArgumentException("Open debts must not have a settled date");
        }
    }

    /** @return copy with the database-generated id set after insert. */
    public Debt withId(long generatedId) {
        return new Debt(generatedId, direction, person, amountSatang, occurredDate, dueDate, status, settledDate, createdAt);
    }

    /** @return copy marked settled on the given day. */
    public Debt settled(LocalDate onDate) {
        return new Debt(id, direction, person, amountSatang, occurredDate, dueDate, DebtStatus.SETTLED, onDate, createdAt);
    }

    /** @return copy reopened (undo of a settling payment). */
    public Debt reopened() {
        return new Debt(id, direction, person, amountSatang, occurredDate, dueDate, DebtStatus.OPEN, null, createdAt);
    }

    /** @return whether this debt is open and past its due date on {@code today}. O(1). */
    public boolean isOverdue(LocalDate today) {
        return status == DebtStatus.OPEN && dueDate != null && dueDate.isBefore(today);
    }
}
