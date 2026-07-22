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
    void incomeAndItemlessExpensesNeverTrack() {
        Transaction in = transactionService.add(income("SCB", 1_000, DAY));
        assertNull(refillService.track(in));
        Transaction noItem = transactionService.add(expense("SCB", DAILY_SPENDING, null, 100, DAY));
        assertNull(refillService.track(noItem));
    }
}
