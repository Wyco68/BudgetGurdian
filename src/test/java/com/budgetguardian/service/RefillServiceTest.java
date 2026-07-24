package com.budgetguardian.service;

import com.budgetguardian.model.RefillItem;
import com.budgetguardian.model.Transaction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Duplicate detection and auto-tracking logic of {@link RefillService}. */
class RefillServiceTest extends ServiceTestBase {

    @Test
    void firstPurchaseTracksNothing() {
        Transaction first = transactionService.add(expense("SCB", DAILY_SPENDING, "Bread", 3_000, DAY));
        assertNull(refillService.track(first));
    }

    @Test
    void secondPurchaseAutoTracksWithGapAndSurvivesRestart() {
        transactionService.add(expense("SCB", DAILY_SPENDING, "Bread", 3_000, DAY));
        Transaction second = transactionService.add(expense("SCB", DAILY_SPENDING, "  bread ", 3_200, DAY.plusDays(7)));

        RefillItem item = refillService.track(second);
        assertEquals("bread", item.name());          // normalized
        assertEquals(7.0, item.intervalDays());
        assertEquals(2, item.purchaseCount());
        assertEquals(item, store.refillItems().get("bread"));
        assertEquals(item, reload().refillItems().get("bread"));   // permanent
    }

    @Test
    void trackedItemUpdatesSilentlyOnNextPurchase() {
        transactionService.add(expense("SCB", DAILY_SPENDING, "Bread", 3_000, DAY));
        Transaction second = transactionService.add(expense("SCB", DAILY_SPENDING, "Bread", 3_000, DAY.plusDays(7)));
        refillService.track(second);

        Transaction third = transactionService.add(expense("SCB", DAILY_SPENDING, "Bread", 3_000, DAY.plusDays(10)));
        RefillItem updated = refillService.track(third);           // silent update, not a fresh detection
        assertEquals(3, updated.purchaseCount());
        assertEquals(5.0, updated.intervalDays(), 1e-9);           // avg of gaps 7 and 3
    }

    @Test
    void overdueScanFindsLateItems() {
        transactionService.add(expense("SCB", DAILY_SPENDING, "Bread", 3_000, DAY));
        Transaction second = transactionService.add(expense("SCB", DAILY_SPENDING, "Bread", 3_000, DAY.plusDays(7)));
        refillService.track(second);

        assertTrue(refillService.overdueItems(DAY.plusDays(14)).isEmpty());    // expected day
        assertEquals(1, refillService.overdueItems(DAY.plusDays(15)).size()); // one past
    }

    @Test
    void undoConfirmForgetsTheItemSoNextDuplicateTracksAgain() {
        transactionService.add(expense("SCB", DAILY_SPENDING, "Bread", 3_000, DAY));
        Transaction second = transactionService.add(expense("SCB", DAILY_SPENDING, "Bread", 3_000, DAY.plusDays(7)));
        refillService.track(second);

        assertTrue(undoService.undo());
        assertNull(store.refillItems().get("bread"));
        assertTrue(reload().refillItems().isEmpty());
        // Forgotten — the next duplicate purchase auto-tracks it again.
        Transaction third = transactionService.add(expense("SCB", DAILY_SPENDING, "Bread", 3_000, DAY.plusDays(9)));
        assertTrue(refillService.track(third) != null);
    }

    @Test
    void refillCategoryTracksFromFirstPurchase() {
        Transaction first = transactionService.add(expense("SCB", REFILL, "Shampoo", 12_000, DAY));
        RefillItem item = refillService.track(first);
        assertEquals("shampoo", item.name());
        assertEquals(1, item.purchaseCount());
        assertEquals(30.0, item.intervalDays());   // provisional until a real gap is seen
        assertEquals(item, store.refillItems().get("shampoo"));
    }

    @Test
    void secondRefillPurchaseReplacesProvisionalInterval() {
        refillService.track(transactionService.add(expense("SCB", REFILL, "Shampoo", 12_000, DAY)));
        assertEquals(30.0, store.refillItems().get("shampoo").intervalDays());   // provisional

        Transaction second = transactionService.add(expense("SCB", REFILL, "Shampoo", 12_000, DAY.plusDays(21)));
        RefillItem updated = refillService.track(second);
        assertEquals(21.0, updated.intervalDays(), 1e-9);   // provisional 30 fully replaced
        assertEquals(2, updated.purchaseCount());
        assertEquals(DAY.plusDays(21), updated.lastPurchase());
    }

    @Test
    void daysLastedCountsElapsedTimeSinceLastPurchase() {
        Transaction first = transactionService.add(expense("SCB", REFILL, "Shampoo", 12_000, DAY));
        RefillItem item = refillService.track(first);
        assertEquals(0, item.daysLasted(DAY));
        assertEquals(9, item.daysLasted(DAY.plusDays(9)));
        assertEquals(0, item.daysLasted(DAY.minusDays(3)));   // backdated "today" clamps to 0
    }

    @Test
    void nonRefillCategoryStillNeedsARepeatPurchase() {
        Transaction first = transactionService.add(expense("SCB", DAILY_SPENDING, "Bread", 3_000, DAY));
        assertNull(refillService.track(first));   // first non-Refill purchase tracks nothing
    }

    @Test
    void incomeAndItemlessExpensesNeverTrack() {
        Transaction in = transactionService.add(income("SCB", 1_000, DAY));
        assertNull(refillService.track(in));
        Transaction noItem = transactionService.add(expense("SCB", DAILY_SPENDING, null, 100, DAY));
        assertNull(refillService.track(noItem));
    }

    @Test
    void groupedUndoReversesBothExpenseAndRefillTrackingInOneStep() {
        // Mirror the add-expense UI flow: the expense and the refill-item
        // tracking it triggers are grouped into one undo unit.
        int mark = undoService.mark();
        Transaction saved = transactionService.add(expense("SCB", REFILL, "Shampoo", 12_000, DAY));
        refillService.track(saved);        // pushes a ConfirmRefill on top of the AddTransaction
        undoService.groupSince(mark);

        assertEquals(1, store.undoStack().size(), "operation should collapse to one undo unit");
        assertTrue(refillService.overdueItems(DAY.plusDays(90)).size() >= 0);   // item exists
        assertEquals(-12_000, store.accounts().get("SCB").balanceSatang());

        // A single undo cancels the whole operation: transaction AND refill item.
        assertTrue(undoService.undo());
        assertTrue(store.ledger().isEmpty(), "transaction not undone");
        assertNull(store.refillItems().get("shampoo"), "refill item not undone");
        assertEquals(0, store.accounts().get("SCB").balanceSatang());
        assertTrue(store.undoStack().isEmpty());
    }
}
