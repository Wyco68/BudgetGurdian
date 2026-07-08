package com.budgetguardian.model;

/** Whether the user owes ("need to pay") or is owed ("need to receive"). */
public enum DebtDirection {
    /** The user owes money; paying decreases the chosen account. */
    PAYABLE,
    /** The user is owed money; receiving increases the chosen account. */
    RECEIVABLE
}
