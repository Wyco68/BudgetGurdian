package com.budgetguardian.model;

/** Lifecycle of a debt: open until partial payments sum to the full amount. */
public enum DebtStatus {
    /** Payments so far are below the debt amount. */
    OPEN,
    /** Fully paid or received; keeps its history. */
    SETTLED
}
