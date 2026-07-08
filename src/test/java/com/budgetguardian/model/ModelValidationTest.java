package com.budgetguardian.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards on the domain records: invalid states must be unrepresentable. */
class ModelValidationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 7, 6);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 6, 12, 0);

    @Test
    void expenseRequiresCategoryOthersForbidIt() {
        assertThrows(IllegalArgumentException.class, () -> new Transaction(
                0, TransactionType.EXPENSE, "SCB", null, "bread", 100, "lunch", DAY, NOW));
        assertThrows(IllegalArgumentException.class, () -> new Transaction(
                0, TransactionType.INCOME, "SCB", 1, null, 100, "salary", DAY, NOW));
        assertThrows(IllegalArgumentException.class, () -> new Transaction(
                0, TransactionType.WITHDRAWAL, "SCB", 1, null, 100, "cash", DAY, NOW));
    }

    @Test
    void transactionRejectsNonPositiveAmountAndBlankReason() {
        assertThrows(IllegalArgumentException.class, () -> new Transaction(
                0, TransactionType.EXPENSE, "SCB", 1, null, 0, "x", DAY, NOW));
        assertThrows(IllegalArgumentException.class, () -> new Transaction(
                0, TransactionType.EXPENSE, "SCB", 1, null, -5, "x", DAY, NOW));
        assertThrows(IllegalArgumentException.class, () -> new Transaction(
                0, TransactionType.EXPENSE, "SCB", 1, null, 100, "  ", DAY, NOW));
    }

    @Test
    void transferRejectsSameEndpoints() {
        assertThrows(IllegalArgumentException.class, () -> new Transfer(
                0, "SCB", "SCB", 100, "loop", DAY, NOW));
    }

    @Test
    void debtSettledStateConsistency() {
        assertThrows(IllegalArgumentException.class, () -> new Debt(
                0, DebtDirection.PAYABLE, "Alice", 100, null, DebtStatus.SETTLED, null, NOW));
        assertThrows(IllegalArgumentException.class, () -> new Debt(
                0, DebtDirection.PAYABLE, "Alice", 100, null, DebtStatus.OPEN, DAY, NOW));
        Debt open = new Debt(0, DebtDirection.PAYABLE, "Alice", 100, DAY, DebtStatus.OPEN, null, NOW);
        Debt settled = open.settled(DAY.plusDays(1));
        assertEquals(DebtStatus.SETTLED, settled.status());
        assertEquals(DebtStatus.OPEN, settled.reopened().status());
    }

    @Test
    void debtOverdueOnlyWhenOpenAndPastDue() {
        Debt due = new Debt(1, DebtDirection.PAYABLE, "A", 100, DAY, DebtStatus.OPEN, null, NOW);
        assertTrue(due.isOverdue(DAY.plusDays(1)));
        assertFalse(due.isOverdue(DAY));                       // due today ≠ overdue
        assertFalse(due.settled(DAY).isOverdue(DAY.plusDays(9)));
        Debt noDue = new Debt(2, DebtDirection.PAYABLE, "A", 100, null, DebtStatus.OPEN, null, NOW);
        assertFalse(noDue.isOverdue(DAY.plusYears(1)));
    }

    @Test
    void refillRunningAverageInterval() {
        // Confirmed after 2nd purchase with a 10-day gap.
        RefillItem item = new RefillItem("bread", 10, DAY, 2);
        // 3rd purchase 4 days later: avg of gaps (10, 4) = 7.
        RefillItem after = item.recordPurchase(DAY.plusDays(4));
        assertEquals(7.0, after.intervalDays(), 1e-9);
        assertEquals(3, after.purchaseCount());
        assertEquals(DAY.plusDays(4), after.lastPurchase());
        // 4th purchase 8 days later: avg of (10, 4, 8) = 22/3.
        RefillItem third = after.recordPurchase(DAY.plusDays(12));
        assertEquals(22.0 / 3.0, third.intervalDays(), 1e-9);
        assertEquals(4, third.purchaseCount());
    }

    @Test
    void refillOverdueUsesRoundedInterval() {
        RefillItem item = new RefillItem("bread", 7, DAY, 2);
        assertEquals(DAY.plusDays(7), item.nextExpected());
        assertFalse(item.isOverdue(DAY.plusDays(7)));          // expected day ≠ overdue
        assertTrue(item.isOverdue(DAY.plusDays(8)));
    }

    @Test
    void refillRejectsBackdatedPurchase() {
        RefillItem item = new RefillItem("bread", 7, DAY, 2);
        assertThrows(IllegalArgumentException.class, () -> item.recordPurchase(DAY));
        assertThrows(IllegalArgumentException.class, () -> item.recordPurchase(DAY.minusDays(1)));
    }

    @Test
    void accountWithBalanceKeepsIdentity() {
        Account account = new Account("SCB", "SCB", 1_000, 3);
        Account updated = account.withBalance(2_500);
        assertEquals("SCB", updated.id());
        assertEquals(2_500, updated.balanceSatang());
        assertEquals(1_000, account.balanceSatang());          // original untouched
    }
}
