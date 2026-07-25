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
        transactionService.add(expense("SCB", DAILY_SPENDING, null, 18_001, DAY));   // budget 18000
        assertEquals(NotificationType.DAILY_BUDGET_EXCEEDED, notifications.heroBanner().type());
    }

    @Test
    void exactlyAtBudgetIsNotABreach() {
        engineToday(DAY);
        transactionService.add(expense("SCB", DAILY_SPENDING, null, 18_000, DAY));   // equal, not over
        assertNull(notifications.heroBanner());
    }

    @Test
    void dangerBreachRaises100AndOutranksBudget() {
        engineToday(DAY);
        transactionService.add(expense("SCB", DAILY_SPENDING, null, 50_000, DAY));      // budget breach (80)
        transactionService.add(expense("SCB", ALCOHOL, null, 15_000, DAY));   // danger so far 15000
        assertEquals(NotificationType.DAILY_BUDGET_EXCEEDED, notifications.heroBanner().type());
        transactionService.add(expense("SCB", GAMBLE, null, 6_000, DAY));   // danger 21000 > 20000
        assertEquals(NotificationType.DANGER_SPENDING, notifications.heroBanner().type());
    }

    @Test
    void overdueDebtRaises90() {
        engineToday(DAY.plusDays(40));
        debtService.add(new Debt(0, DebtDirection.PAYABLE, "Alice", 10_000,
                DAY, DAY.plusDays(30), DebtStatus.OPEN, null, NOW));               // due before today
        assertEquals(NotificationType.DEBT_OVERDUE, notifications.heroBanner().type());
    }

    @Test
    void settledDebtDoesNotAlert() {
        engineToday(DAY.plusDays(40));
        Debt debt = debtService.add(new Debt(0, DebtDirection.PAYABLE, "Alice", 10_000,
                DAY, DAY.plusDays(30), DebtStatus.OPEN, null, NOW));
        debtService.pay(debt.id(), "SCB", 10_000, DAY.plusDays(35));          // settles it
        assertNull(notifications.heroBanner());
    }

    @Test
    void refillOverdueRaises60() {
        transactionService.add(expense("SCB", DAILY_SPENDING, "Bread", 3_000, DAY));
        Transaction second = transactionService.add(expense("SCB", DAILY_SPENDING, "Bread", 3_000, DAY.plusDays(7)));
        refillService.track(second);  // interval 7
        engineToday(DAY.plusDays(20));                                        // well overdue
        assertEquals(NotificationType.REFILL_DUE, notifications.heroBanner().type());
    }

    @Test
    void billDueRaises70() {
        billService.add(new com.budgetguardian.model.Bill(0, "Internet", 6_000, 15, null, NOW));
        engineToday(DAY.withDayOfMonth(16));                                   // past payday, unpaid
        assertEquals(NotificationType.BILL_DUE, notifications.heroBanner().type());
    }

    @Test
    void payingTheBillClearsTheAlertUntilNextMonth() {
        var bill = billService.add(new com.budgetguardian.model.Bill(0, "Internet", 6_000, 15, null, NOW));
        engineToday(DAY.withDayOfMonth(16));
        billService.pay(bill.id(), "SCB", 6_000, DAY.withDayOfMonth(16), "");
        assertNull(notifications.heroBanner());
    }

    @Test
    void rebuildClearsResolvedAlerts() {
        engineToday(DAY);
        Transaction over = transactionService.add(expense("SCB", DAILY_SPENDING, null, 30_000, DAY));
        assertEquals(NotificationType.DAILY_BUDGET_EXCEEDED, notifications.heroBanner().type());
        transactionService.delete(over.id());                                // back under budget
        assertNull(notifications.heroBanner());                              // rebuild dropped it
    }
}
