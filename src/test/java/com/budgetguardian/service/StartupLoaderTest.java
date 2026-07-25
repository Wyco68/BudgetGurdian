package com.budgetguardian.service;

import com.budgetguardian.model.Debt;
import com.budgetguardian.model.DebtDirection;
import com.budgetguardian.model.DebtStatus;
import com.budgetguardian.model.Transfer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Startup hydration: everything written must come back rebuilt. */
class StartupLoaderTest extends ServiceTestBase {

    @Test
    void freshDatabaseLoadsSeedsAndEmptyState() {
        assertEquals(4, store.accounts().size());
        assertEquals(6, store.categories().size());
        assertEquals(3, store.settings().size());
        assertTrue(store.ledger().isEmpty());
        assertTrue(store.undoStack().isEmpty());
        assertEquals(4, store.transferGraph().vertexCount());   // isolated vertices
        assertEquals(0, store.transferGraph().edgeCount());
    }

    @Test
    void reloadRebuildsAllDerivedState() {
        transactionService.add(income("SCB", 100_000, DAY));
        for (int i = 1; i <= 22; i++) {
            transactionService.add(expense("SCB", DAILY_SPENDING, "item" + i, 1_000, DAY));
        }
        transactionService.add(expense("SCB", ALCOHOL, null, 9_000, DAY));
        transferService.add(new Transfer(0, "SCB", "SAVING", 5_000, "move", DAY, NOW));
        Debt debt = debtService.add(new Debt(0, DebtDirection.PAYABLE, "Alice", 50_000,
                null, null, DebtStatus.OPEN, null, NOW));
        debtService.pay(debt.id(), "SAVING", 20_000, DAY);

        DataStore reloaded = reload();

        assertEquals(24, reloaded.ledger().size());
        assertEquals(22_000, reloaded.dailyTotal(DAY));               // DailySpending only, not Alcohol
        assertEquals(22_000, reloaded.categoryMonthTotal(DAILY_SPENDING, DAY));
        assertEquals(9_000, reloaded.dangerWeekTotal(DAY));
        assertEquals(20, reloaded.recentTransactions().size());       // buffer capped
        assertEquals(1, reloaded.transferGraph().edgeCount());
        assertEquals(5_000, reloaded.transferGraph().totalFlow("SCB", "SAVING"));
        assertEquals(1, reloaded.debtPayments().get(debt.id()).size());
        assertEquals(30_000, reloaded.debts().get(debt.id()).amountSatang() - 20_000);
        // Balances: +100000 −22000 −9000 −5000(out) = 64000 SCB; +5000 −20000 = −15000 SAVING
        assertEquals(64_000, reloaded.accounts().get("SCB").balanceSatang());
        assertEquals(-15_000, reloaded.accounts().get("SAVING").balanceSatang());
        assertTrue(reloaded.undoStack().isEmpty());                   // undo is session-only
    }
}
