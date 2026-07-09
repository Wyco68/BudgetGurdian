package com.budgetguardian.service;

import com.budgetguardian.model.Debt;
import com.budgetguardian.model.DebtDirection;
import com.budgetguardian.model.DebtStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Business-logic tests for {@link DebtService} partial payments. */
class DebtServiceTest extends ServiceTestBase {

    private Debt payable(long satang) {
        return debtService.add(new Debt(0, DebtDirection.PAYABLE, "Alice", satang,
                DAY.plusDays(30), DebtStatus.OPEN, null, NOW));
    }

    private Debt receivable(long satang) {
        return debtService.add(new Debt(0, DebtDirection.RECEIVABLE, "Bob", satang,
                null, DebtStatus.OPEN, null, NOW));
    }

    @Test
    void partialPaymentsAccumulateUntilAutoSettle() {
        Debt debt = payable(100_000);
        debtService.pay(debt.id(), "SCB", 40_000, DAY);
        assertEquals(DebtStatus.OPEN, store.debts().get(debt.id()).status());
        assertEquals(60_000, debtService.remaining(debt.id()));
        assertEquals(-40_000, store.accounts().get("SCB").balanceSatang());

        debtService.pay(debt.id(), "SAVING", 60_000, DAY.plusDays(1));
        Debt settled = store.debts().get(debt.id());
        assertEquals(DebtStatus.SETTLED, settled.status());
        assertEquals(DAY.plusDays(1), settled.settledDate());
        assertEquals(0, debtService.remaining(debt.id()));
        assertEquals(-60_000, store.accounts().get("SAVING").balanceSatang());
        // Database agrees.
        assertEquals(DebtStatus.SETTLED, reload().debts().get(debt.id()).status());
    }

    @Test
    void receivingIncreasesTheAccount() {
        Debt debt = receivable(50_000);
        debtService.pay(debt.id(), "TRUEMONEY", 50_000, DAY);
        assertEquals(50_000, store.accounts().get("TRUEMONEY").balanceSatang());
        assertEquals(DebtStatus.SETTLED, store.debts().get(debt.id()).status());
    }

    @Test
    void overpaymentAndSettledPaymentRejected() {
        Debt debt = payable(10_000);
        assertThrows(BudgetException.class, () -> debtService.pay(debt.id(), "SCB", 10_001, DAY));
        debtService.pay(debt.id(), "SCB", 10_000, DAY);
        assertThrows(BudgetException.class, () -> debtService.pay(debt.id(), "SCB", 1, DAY));
        assertThrows(BudgetException.class, () -> debtService.pay(999, "SCB", 1, DAY));
        assertThrows(BudgetException.class, () -> debtService.pay(debt.id(), "KBANK", 1, DAY));
    }

    @Test
    void undoSettlingPaymentReopensDebt() {
        Debt debt = payable(10_000);
        debtService.pay(debt.id(), "SCB", 10_000, DAY);
        assertEquals(DebtStatus.SETTLED, store.debts().get(debt.id()).status());

        assertTrue(undoService.undo());
        Debt reopened = store.debts().get(debt.id());
        assertEquals(DebtStatus.OPEN, reopened.status());
        assertEquals(10_000, debtService.remaining(debt.id()));
        assertEquals(0, store.accounts().get("SCB").balanceSatang());
        assertEquals(DebtStatus.OPEN, reload().debts().get(debt.id()).status());
    }

    @Test
    void undoNonSettlingPaymentKeepsDebtOpen() {
        Debt debt = payable(100_000);
        debtService.pay(debt.id(), "SCB", 30_000, DAY);
        assertTrue(undoService.undo());
        assertEquals(100_000, debtService.remaining(debt.id()));
        assertEquals(0, store.accounts().get("SCB").balanceSatang());
        assertTrue(store.debtPayments().get(debt.id()).isEmpty());
    }

    @Test
    void undoDebtCreationOnlyWhenPaymentFree() {
        Debt debt = payable(10_000);
        assertTrue(undoService.undo());                    // no payments → fine
        assertTrue(store.debts().isEmpty());
        assertTrue(reload().debts().isEmpty());

        Debt second = payable(10_000);
        debtService.pay(second.id(), "SCB", 5_000, DAY);
        undoService.undo();                                // pops the payment
        debtService.pay(second.id(), "SCB", 5_000, DAY);   // pay again
        // Undo stack top is now the payment, then the debt creation below it.
        undoService.undo();                                // payment undone
        assertTrue(undoService.undo());                    // creation undone — payment-free again
        assertTrue(store.debts().isEmpty());
    }

    @Test
    void overdueDetection() {
        Debt debt = payable(10_000);                       // due DAY+30
        assertTrue(store.debts().get(debt.id()).isOverdue(DAY.plusDays(31)));
        assertTrue(!store.debts().get(debt.id()).isOverdue(DAY.plusDays(30)));
    }
}
