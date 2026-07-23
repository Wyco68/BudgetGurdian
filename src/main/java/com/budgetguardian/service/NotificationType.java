package com.budgetguardian.service;

/**
 * Kinds of alert raised by the {@link RuleEngine} and reminder scheduler.
 *
 * <p>Each carries its fixed hero-banner priority (higher wins): danger
 * spending outranks everything, the daily reminder is lowest.</p>
 */
public enum NotificationType {
    /** Alcohol + gambling weekly spending over the limit. */
    DANGER_SPENDING(100),
    /** An open debt is past its due date. */
    DEBT_OVERDUE(90),
    /** Today's spending exceeded the daily budget. */
    DAILY_BUDGET_EXCEEDED(80),
    /** A recurring bill has reached its payday and hasn't been paid this month. */
    BILL_DUE(70),
    /** A refillable item is overdue for repurchase. */
    REFILL_DUE(60),
    /** Evening nudge to record today's expenses. */
    DAILY_REMINDER(20);

    private final int priority;

    NotificationType(int priority) {
        this.priority = priority;
    }

    /** @return the hero-banner priority; larger is more urgent. */
    public int priority() {
        return priority;
    }
}
