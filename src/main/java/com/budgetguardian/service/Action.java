package com.budgetguardian.service;

import com.budgetguardian.model.Debt;
import com.budgetguardian.model.DebtPayment;
import com.budgetguardian.model.RefillItem;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.Transfer;

/**
 * One undoable modification — Command pattern with memento snapshots.
 *
 * <p><b>Purpose:</b> every service mutation pushes exactly one {@code Action}
 * onto the {@code Stack<Action>} in {@link DataStore}; Ctrl+Z pops it and
 * {@link UndoService} applies the inverse to both SQLite and memory.
 * Records are immutable snapshots, so undo can never observe half-mutated
 * state. Sealed: the undo dispatch in {@code UndoService} switches
 * exhaustively — adding a new action type fails compilation until handled.</p>
 */
public sealed interface Action {

    /** A transaction was added; undo deletes it. */
    record AddTransaction(Transaction txn) implements Action {
    }

    /** A transaction was deleted; undo restores it with its original id. */
    record DeleteTransaction(Transaction txn) implements Action {
    }

    /** A transaction was edited; undo swaps {@code after} back to {@code before}. */
    record EditTransaction(Transaction before, Transaction after) implements Action {
    }

    /** A transfer was recorded; undo removes it and restores both balances. */
    record AddTransfer(Transfer transfer) implements Action {
    }

    /** A debt was created; undo deletes it (only payment-free debts can be created-undone). */
    record AddDebt(Debt debt) implements Action {
    }

    /**
     * A partial payment was recorded; undo deletes the payment, restores the
     * balance, and reopens the debt if this payment had settled it.
     *
     * @param settledByThisPayment whether the payment flipped the debt to SETTLED
     */
    record AddDebtPayment(DebtPayment payment, boolean settledByThisPayment) implements Action {
    }

    /** An item was confirmed refillable; undo forgets it (next duplicate asks again). */
    record ConfirmRefill(RefillItem item) implements Action {
    }
}
