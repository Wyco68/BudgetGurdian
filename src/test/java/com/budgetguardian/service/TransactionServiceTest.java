package com.budgetguardian.service;

import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Business-logic tests for {@link TransactionService} including undo. */
class TransactionServiceTest extends ServiceTestBase {

    @Test
    void addExpenseUpdatesEverything() {
        Transaction saved = transactionService.add(expense("SCB", DAILY_SPENDING, "bread", 4_500, DAY));

        assertTrue(saved.id() > 0);
        assertEquals(1, store.ledger().size());
        assertEquals(-4_500, store.accounts().get("SCB").balanceSatang());
        assertEquals(4_500, store.dailyTotal(DAY));
        assertEquals(4_500, store.categoryMonthTotal(DAILY_SPENDING, DAY));
        assertEquals(saved, store.recentTransactions().get(0));
        assertEquals(1, store.undoStack().size());
        // Database agrees after simulated restart.
        DataStore reloaded = reload();
        assertEquals(-4_500, reloaded.accounts().get("SCB").balanceSatang());
        assertEquals(4_500, reloaded.dailyTotal(DAY));
    }

    @Test
    void incomeIncreasesBalanceWithoutTouchingSpending() {
        transactionService.add(income("SAVING", 900_000, DAY));
        assertEquals(900_000, store.accounts().get("SAVING").balanceSatang());
        assertEquals(0, store.dailyTotal(DAY));           // income is not spending
    }

    @Test
    void withdrawalDecreasesBalanceWithoutTouchingSpending() {
        transactionService.add(new Transaction(0, TransactionType.WITHDRAWAL,
                "SCB", null, null, 10_000, "cash out", DAY, NOW));
        assertEquals(-10_000, store.accounts().get("SCB").balanceSatang());
        assertEquals(0, store.dailyTotal(DAY));
    }

    @Test
    void dangerCategoriesFeedWeeklyTotalMondayToSunday() {
        transactionService.add(expense("SCB", ALCOHOL, null, 10_000, DAY));            // Monday
        transactionService.add(expense("SCB", GAMBLE, null, 5_000, DAY.plusDays(6))); // Sunday, same week
        transactionService.add(expense("SCB", ALCOHOL, null, 7_000, DAY.plusDays(7))); // next Monday
        transactionService.add(expense("SCB", DAILY_SPENDING, null, 99_000, DAY));               // not danger

        assertEquals(15_000, store.dangerWeekTotal(DAY));                // Mon–Sun combined
        assertEquals(15_000, store.dangerWeekTotal(DAY.plusDays(6)));    // same week, any day
        assertEquals(7_000, store.dangerWeekTotal(DAY.plusDays(7)));     // new week restarts
    }

    @Test
    void undoAddRestoresExactPreviousState() {
        transactionService.add(expense("SCB", DAILY_SPENDING, "bread", 4_500, DAY));
        assertTrue(undoService.undo());

        assertTrue(store.ledger().isEmpty());
        assertEquals(0, store.accounts().get("SCB").balanceSatang());
        assertEquals(0, store.dailyTotal(DAY));
        assertTrue(store.recentTransactions().isEmpty());
        assertTrue(store.undoStack().isEmpty());
        assertTrue(reload().ledger().isEmpty());          // database rolled forward too
    }

    @Test
    void deleteThenUndoRestoresWithOriginalId() {
        Transaction saved = transactionService.add(expense("SCB", DAILY_SPENDING, "bread", 4_500, DAY));
        transactionService.delete(saved.id());
        assertTrue(store.ledger().isEmpty());
        assertEquals(0, store.accounts().get("SCB").balanceSatang());

        assertTrue(undoService.undo());                   // undo the delete
        assertEquals(1, store.ledger().size());
        assertEquals(saved, store.ledger().getFirst());   // same id, full fidelity
        assertEquals(-4_500, store.accounts().get("SCB").balanceSatang());
        assertEquals(saved, reload().ledger().getFirst());
    }

    @Test
    void editAdjustsBalancesAcrossAccounts() {
        Transaction saved = transactionService.add(expense("SCB", DAILY_SPENDING, null, 4_500, DAY));
        Transaction edited = new Transaction(saved.id(), TransactionType.EXPENSE,
                "SAVING", DAILY_SPENDING, null, 6_000, "moved", DAY, NOW);
        transactionService.edit(edited);

        assertEquals(0, store.accounts().get("SCB").balanceSatang());
        assertEquals(-6_000, store.accounts().get("SAVING").balanceSatang());
        assertEquals(6_000, store.dailyTotal(DAY));

        assertTrue(undoService.undo());                   // undo the edit
        assertEquals(-4_500, store.accounts().get("SCB").balanceSatang());
        assertEquals(0, store.accounts().get("SAVING").balanceSatang());
        assertEquals(4_500, store.dailyTotal(DAY));
    }

    @Test
    void editOnSameAccountCombinesDeltas() {
        Transaction saved = transactionService.add(expense("SCB", DAILY_SPENDING, null, 4_500, DAY));
        transactionService.edit(new Transaction(saved.id(), TransactionType.EXPENSE,
                "SCB", DAILY_SPENDING, null, 10_000, "bigger", DAY, NOW));
        assertEquals(-10_000, store.accounts().get("SCB").balanceSatang());
        assertEquals(-10_000, reload().accounts().get("SCB").balanceSatang());
    }

    @Test
    void multipleUndosUnwindInLifoOrder() {
        transactionService.add(expense("SCB", DAILY_SPENDING, null, 100, DAY));
        transactionService.add(expense("SCB", DAILY_SPENDING, null, 200, DAY));
        transactionService.add(income("SCB", 1_000, DAY));
        assertEquals(700, store.accounts().get("SCB").balanceSatang());

        undoService.undo();     // income gone
        assertEquals(-300, store.accounts().get("SCB").balanceSatang());
        undoService.undo();     // 200 expense gone
        assertEquals(-100, store.accounts().get("SCB").balanceSatang());
        undoService.undo();     // 100 expense gone
        assertEquals(0, store.accounts().get("SCB").balanceSatang());
        assertFalse(undoService.undo());                  // stack empty → false
    }

    @Test
    void dailyAndOtherTotalsAreTrackedSeparately() {
        transactionService.add(expense("SCB", DAILY_SPENDING, null, 4_000, DAY));
        transactionService.add(expense("SCB", ALCOHOL, null, 6_000, DAY));
        transactionService.add(expense("SCB", BILL, null, 5_000, DAY));

        assertEquals(4_000, store.dailyTotal(DAY));          // DailySpending only
        assertEquals(11_000, store.otherDailyTotal(DAY));    // alcohol + bill
        assertEquals(4_000, reload().dailyTotal(DAY));
        assertEquals(11_000, reload().otherDailyTotal(DAY));
    }

    @Test
    void extraExpenseRequiresReasonButOthersDoNot() {
        Transaction blankReasonDailySpending = new Transaction(0, TransactionType.EXPENSE,
                "SCB", DAILY_SPENDING, null, 100, "", DAY, NOW);
        transactionService.add(blankReasonDailySpending);   // allowed — not Extra

        Transaction blankReasonExtra = new Transaction(0, TransactionType.EXPENSE,
                "SCB", EXTRA, null, 100, "", DAY, NOW);
        assertThrows(BudgetException.class, () -> transactionService.add(blankReasonExtra));

        Transaction withReasonExtra = new Transaction(0, TransactionType.EXPENSE,
                "SCB", EXTRA, null, 100, "unexpected", DAY, NOW);
        Transaction saved = transactionService.add(withReasonExtra);
        assertEquals("unexpected", saved.reason());
    }

    @Test
    void rejectsUnknownAccountAndCategory() {
        assertThrows(BudgetException.class,
                () -> transactionService.add(expense("KBANK", DAILY_SPENDING, null, 100, DAY)));
        assertThrows(BudgetException.class,
                () -> transactionService.add(expense("SCB", 99, null, 100, DAY)));
        assertThrows(BudgetException.class, () -> transactionService.delete(12345));
    }

    @Test
    void recentBufferKeepsOnlyLastTwenty() {
        for (int i = 1; i <= 25; i++) {
            transactionService.add(expense("SCB", DAILY_SPENDING, null, i, DAY));
        }
        assertEquals(20, store.recentTransactions().size());
        assertEquals(25, store.recentTransactions().get(19).amountSatang());  // newest kept
        assertEquals(6, store.recentTransactions().get(0).amountSatang());    // 1–5 evicted
    }
}
