package com.budgetguardian.service;

import com.budgetguardian.model.RefillItem;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.service.RefillService.RefillPrompt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Duplicate detection and reminder logic of {@link RefillService}. */
class RefillServiceTest extends ServiceTestBase {

    @Test
    void firstPurchaseAsksNothing() {
        Transaction first = transactionService.add(expense("SCB", FOOD, "Bread", 3_000, DAY));
        assertNull(refillService.detectDuplicate(first));
    }

    @Test
    void secondPurchasePromptsWithGap() {
        transactionService.add(expense("SCB", FOOD, "Bread", 3_000, DAY));
        Transaction second = transactionService.add(expense("SCB", FOOD, "  bread ", 3_200, DAY.plusDays(7)));

        RefillPrompt prompt = refillService.detectDuplicate(second);
        assertEquals("bread", prompt.itemName());          // normalized
        assertEquals(DAY, prompt.lastPurchase());
        assertEquals(7, prompt.gapDays());
    }

    @Test
    void decliningLeavesNothingSoItAsksAgain() {
        transactionService.add(expense("SCB", FOOD, "Bread", 3_000, DAY));
        Transaction second = transactionService.add(expense("SCB", FOOD, "Bread", 3_000, DAY.plusDays(7)));
        assertTrue(refillService.detectDuplicate(second) != null);
        // User answers "No" → nothing stored. Third purchase must prompt again.
        Transaction third = transactionService.add(expense("SCB", FOOD, "Bread", 3_000, DAY.plusDays(12)));
        RefillPrompt again = refillService.detectDuplicate(third);
        assertTrue(again != null);
        assertEquals(DAY.plusDays(7), again.lastPurchase());   // latest earlier purchase
    }

    @Test
    void confirmStoresPermanentlyAndSurvivesRestart() {
        transactionService.add(expense("SCB", FOOD, "Bread", 3_000, DAY));
        Transaction second = transactionService.add(expense("SCB", FOOD, "Bread", 3_000, DAY.plusDays(7)));
        RefillPrompt prompt = refillService.detectDuplicate(second);

        RefillItem item = refillService.confirm(prompt, second.date());
        assertEquals(7.0, item.intervalDays());
        assertEquals(2, item.purchaseCount());
        assertEquals(item, store.refillItems().get("bread"));
        assertEquals(item, reload().refillItems().get("bread"));   // permanent
    }

    @Test
    void confirmedItemUpdatesSilentlyOnNextPurchase() {
        transactionService.add(expense("SCB", FOOD, "Bread", 3_000, DAY));
        Transaction second = transactionService.add(expense("SCB", FOOD, "Bread", 3_000, DAY.plusDays(7)));
        refillService.confirm(refillService.detectDuplicate(second), second.date());

        Transaction third = transactionService.add(expense("SCB", FOOD, "Bread", 3_000, DAY.plusDays(10)));
        assertNull(refillService.detectDuplicate(third));          // no prompt — already refillable
        RefillItem updated = store.refillItems().get("bread");
        assertEquals(3, updated.purchaseCount());
        assertEquals(5.0, updated.intervalDays(), 1e-9);           // avg of gaps 7 and 3
    }

    @Test
    void overdueScanFindsLateItems() {
        transactionService.add(expense("SCB", FOOD, "Bread", 3_000, DAY));
        Transaction second = transactionService.add(expense("SCB", FOOD, "Bread", 3_000, DAY.plusDays(7)));
        refillService.confirm(refillService.detectDuplicate(second), second.date());

        assertTrue(refillService.overdueItems(DAY.plusDays(14)).isEmpty());    // expected day
        assertEquals(1, refillService.overdueItems(DAY.plusDays(15)).size()); // one past
    }

    @Test
    void undoConfirmForgetsTheItem() {
        transactionService.add(expense("SCB", FOOD, "Bread", 3_000, DAY));
        Transaction second = transactionService.add(expense("SCB", FOOD, "Bread", 3_000, DAY.plusDays(7)));
        refillService.confirm(refillService.detectDuplicate(second), second.date());

        assertTrue(undoService.undo());
        assertNull(store.refillItems().get("bread"));
        assertTrue(reload().refillItems().isEmpty());
        // And the question comes back on the next duplicate.
        Transaction third = transactionService.add(expense("SCB", FOOD, "Bread", 3_000, DAY.plusDays(9)));
        assertTrue(refillService.detectDuplicate(third) != null);
    }

    @Test
    void incomeAndItemlessExpensesNeverPrompt() {
        Transaction in = transactionService.add(income("SCB", 1_000, DAY));
        assertNull(refillService.detectDuplicate(in));
        Transaction noItem = transactionService.add(expense("SCB", FOOD, null, 100, DAY));
        assertNull(refillService.detectDuplicate(noItem));
    }
}
