package com.budgetguardian.service;

import com.budgetguardian.model.Debt;
import com.budgetguardian.model.DebtDirection;
import com.budgetguardian.model.DebtStatus;
import com.budgetguardian.model.Transaction;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Rule evaluation driving the hero banner. Extends the wired service stack. */
class RuleEngineTest extends ServiceTestBase {

    private NotificationService notifications;
    private RuleEngine engine;
    private LocalDate today;

    private void engineToday(LocalDate day) {
        this.today = day;
        this.notifications = new NotificationService(bus);
        this.engine = new RuleEngine(store, bus, notifications, settingsService, refillService, () -> today);
        engine.evaluate();
    }

    @Test
    void noAlertsOnEmptyState() {
        engineToday(DAY);
        assertNull(notifications.heroBanner());
    }

    @Test
    void dailyBudgetBreachRaises80() {
        engineToday(DAY);
        transactionService.add(expense("SCB", FOOD, null, 18_001, DAY));   // budget 18000
        assertEquals(NotificationType.DAILY_BUDGET_EXCEEDED, notifications.heroBanner().type());
    }

    @Test
    void exactlyAtBudgetIsNotABreach() {
        engineToday(DAY);
        transactionService.add(expense("SCB", FOOD, null, 18_000, DAY));   // equal, not over
        assertNull(notifications.heroBanner());
    }

    @Test
    void dangerBreachRaises100AndOutranksBudget() {
        engineToday(DAY);
        transactionService.add(expense("SCB", FOOD, null, 50_000, DAY));      // budget breach (80)
        transactionService.add(expense("SCB", ALCOHOL, null, 15_000, DAY));   // danger so far 15000
        assertEquals(NotificationType.DAILY_BUDGET_EXCEEDED, notifications.heroBanner().type());
        transactionService.add(expense("SCB", GAMBLING, null, 6_000, DAY));   // danger 21000 > 20000
        assertEquals(NotificationType.DANGER_SPENDING, notifications.heroBanner().type());
    }

    @Test
    void overdueDebtRaises90() {
        engineToday(DAY.plusDays(40));
        debtService.add(new Debt(0, DebtDirection.PAYABLE, "Alice", 10_000,
                DAY.plusDays(30), DebtStatus.OPEN, null, NOW));               // due before today
        assertEquals(NotificationType.DEBT_OVERDUE, notifications.heroBanner().type());
    }

    @Test
    void settledDebtDoesNotAlert() {
        engineToday(DAY.plusDays(40));
        Debt debt = debtService.add(new Debt(0, DebtDirection.PAYABLE, "Alice", 10_000,
                DAY.plusDays(30), DebtStatus.OPEN, null, NOW));
        debtService.pay(debt.id(), "SCB", 10_000, DAY.plusDays(35));          // settles it
        assertNull(notifications.heroBanner());
    }

    @Test
    void refillOverdueRaises60() {
        transactionService.add(expense("SCB", FOOD, "Bread", 3_000, DAY));
        Transaction second = transactionService.add(expense("SCB", FOOD, "Bread", 3_000, DAY.plusDays(7)));
        refillService.confirm(refillService.detectDuplicate(second), second.date());  // interval 7
        engineToday(DAY.plusDays(20));                                        // well overdue
        assertEquals(NotificationType.REFILL_DUE, notifications.heroBanner().type());
    }

    @Test
    void rebuildClearsResolvedAlerts() {
        engineToday(DAY);
        Transaction over = transactionService.add(expense("SCB", FOOD, null, 30_000, DAY));
        assertEquals(NotificationType.DAILY_BUDGET_EXCEEDED, notifications.heroBanner().type());
        transactionService.delete(over.id());                                // back under budget
        assertNull(notifications.heroBanner());                              // rebuild dropped it
    }
}
