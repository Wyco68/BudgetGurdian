package com.budgetguardian.service;

import com.budgetguardian.model.Bill;
import com.budgetguardian.model.Debt;
import com.budgetguardian.model.DebtDirection;
import com.budgetguardian.model.DebtStatus;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.Transfer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards against unbounded growth of the in-memory structures during normal
 * usage. Every mutation the UI can drive has an inverse (undo); after a long
 * run of mutate-then-undo cycles, every accumulating structure must return to
 * its pristine, empty size — otherwise the app leaks memory the longer it
 * stays open.
 *
 * <p>This complements {@code Main.stop()} (which releases the scheduler, DB
 * connection, tray-icon thread and backend process at shutdown): this test
 * covers the <em>during-usage</em> half — the derived-total maps, the undo
 * stack, the ledger, the transfer graph and the recent-transactions buffer.</p>
 */
class MemoryLeakGuardTest extends ServiceTestBase {

    @Test
    void repeatedAddUndoLeavesNoResidue() {
        // A per-iteration key/row leak surfaces at any count; 800 keeps the
        // real-SQLite round trips fast while making accidental growth obvious.
        for (int i = 0; i < 800; i++) {
            // Vary the date so the daily/month/week map keys differ each
            // iteration: if keys weren't pruned on undo, the four derived-total
            // maps would grow by up to 4 keys per loop and never shrink.
            Transaction txn = transactionService.add(
                    expense("SCB", i % 2 == 0 ? DAILY_SPENDING : ALCOHOL, "item" + i,
                            100 + i, DAY.plusDays(i % 400)));
            undoService.undo();
        }
        assertPristine();
        assertTrue(store.undoStack().isEmpty(), "undo stack leaked actions");
    }

    @Test
    void mixedMutationsAllUndoneLeaveNoResidue() {
        for (int i = 0; i < 300; i++) {
            transactionService.add(expense("SCB", i % 2 == 0 ? ALCOHOL : BILL, null,
                    500, DAY.plusDays(i % 30)));
            transferService.add(new Transfer(0, "SCB", "SAVING", 300, "move", DAY, NOW));
            debtService.add(new Debt(0, DebtDirection.PAYABLE, "X", 1_000,
                    null, DebtStatus.OPEN, null, NOW));
            billService.add(new Bill(0, "Bill" + i, 700, null, null, NOW));

            undoService.undo();   // bill
            undoService.undo();   // debt
            undoService.undo();   // transfer
            undoService.undo();   // expense
        }
        assertPristine();
        assertTrue(store.undoStack().isEmpty(), "undo stack leaked actions");
    }

    private void assertPristine() {
        assertTrue(store.ledger().isEmpty(), "ledger leaked rows");
        assertTrue(store.transfers().isEmpty(), "transfers leaked rows");
        assertTrue(store.debts().isEmpty(), "debts leaked rows");
        assertTrue(store.bills().isEmpty(), "bills leaked rows");
        assertTrue(store.recentTransactions().isEmpty(), "recent buffer leaked rows");
        assertEquals(0, store.derivedTotalKeyCount(), "derived-total maps leaked keys");
        assertEquals(0, store.transferGraph().edgeCount(), "transfer graph leaked edges");
        assertEquals(0, store.accounts().get("SCB").balanceSatang(), "SCB balance drifted");
        assertEquals(0, store.accounts().get("SAVING").balanceSatang(), "SAVING balance drifted");
    }
}
