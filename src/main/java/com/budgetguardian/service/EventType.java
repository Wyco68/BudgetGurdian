package com.budgetguardian.service;

/**
 * Events published on the {@link EventBus} after state changes.
 *
 * <p>Controllers subscribe per type and refresh only the views affected —
 * the service layer never references JavaFX.</p>
 */
public enum EventType {
    /** Ledger changed: add/edit/delete/undo of a transaction. */
    TRANSACTIONS_CHANGED,
    /** One or more account balances changed. */
    BALANCES_CHANGED,
    /** Transfer history (and the transfer graph) changed. */
    TRANSFERS_CHANGED,
    /** Debts or their payments changed. */
    DEBTS_CHANGED,
    /** Refillable items changed. */
    REFILLS_CHANGED,
    /** Bills changed (added or paid). */
    BILLS_CHANGED,
    /** A setting value changed. */
    SETTINGS_CHANGED,
    /** A notification was raised (hero banner / reminder queue). */
    NOTIFICATION_RAISED
}
