package com.budgetguardian.service;

import com.budgetguardian.datastructures.Iterator;
import com.budgetguardian.model.Bill;
import com.budgetguardian.model.Debt;
import com.budgetguardian.model.DebtStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * Evaluates application state into hero-banner notifications.
 *
 * <p><b>Design:</b> subscribes to the {@link EventBus} for the events that
 * can change an alert condition (transactions, debts, settings, refills). On
 * any of them it rebuilds the hero banner from scratch — the active-alert set
 * is tiny (≤ 4 rules), so a full recompute is simpler and cheaper than
 * tracking individual entries for staleness, and it can never leave an alert
 * that no longer applies.</p>
 *
 * <p><b>Rules (priority):</b> danger spending 100, debt overdue 90, daily
 * budget 80, bill due 70, refill due 60. The daily reminder (20) is raised by
 * the scheduler, not here, so a rebuild does not erase it — it lives in the
 * separate reminder queue.</p>
 *
 * <p><b>Today</b> is supplied via a {@link Supplier} so tests control the
 * date. <b>Time complexity:</b> evaluate O(d + r) — debts scanned once,
 * refill items scanned once; other checks O(1).</p>
 */
public final class RuleEngine {

    private final DataStore store;
    private final NotificationService notifications;
    private final SettingsService settings;
    private final RefillService refills;
    private final Supplier<LocalDate> today;

    public RuleEngine(DataStore store, EventBus bus, NotificationService notifications,
                      SettingsService settings, RefillService refills, Supplier<LocalDate> today) {
        this.store = store;
        this.notifications = notifications;
        this.settings = settings;
        this.refills = refills;
        this.today = today;
        bus.subscribe(EventType.TRANSACTIONS_CHANGED, t -> evaluate());
        bus.subscribe(EventType.DEBTS_CHANGED, t -> evaluate());
        bus.subscribe(EventType.SETTINGS_CHANGED, t -> evaluate());
        bus.subscribe(EventType.REFILLS_CHANGED, t -> evaluate());
        bus.subscribe(EventType.BILLS_CHANGED, t -> evaluate());
    }

    /**
     * Recomputes every rule and rebuilds the hero banner. Idempotent — safe
     * to call as often as state changes.
     */
    public void evaluate() {
        LocalDate now = today.get();
        LocalDateTime stamp = now.atStartOfDay();
        notifications.clearHero();
        checkDanger(now, stamp);
        checkDebtOverdue(now, stamp);
        checkDailyBudget(now, stamp);
        checkRefill(now, stamp);
        checkBillDue(now, stamp);
    }

    private void checkDanger(LocalDate now, LocalDateTime stamp) {
        long spent = store.dangerWeekTotal(now);
        long limit = settings.dangerWeeklyLimitSatang();
        if (spent > limit) {
            notifications.raiseHero(new Notification(NotificationType.DANGER_SPENDING,
                    "⚠ Danger Spending Limit Exceeded",
                    "Alcohol + gambling this week: " + thb(spent) + " / limit " + thb(limit)
                            + " (over by " + thb(spent - limit) + ")",
                    stamp));
        }
    }

    private void checkDebtOverdue(LocalDate now, LocalDateTime stamp) {
        int overdue = 0;
        Iterator<Debt> it = store.debts().values();
        while (it.hasNext()) {
            Debt debt = it.next();
            if (debt.status() == DebtStatus.OPEN && debt.isOverdue(now)) {
                overdue++;
            }
        }
        if (overdue > 0) {
            notifications.raiseHero(new Notification(NotificationType.DEBT_OVERDUE,
                    "⚠ Debt Overdue",
                    overdue + (overdue == 1 ? " debt is" : " debts are") + " past due",
                    stamp));
        }
    }

    private void checkDailyBudget(LocalDate now, LocalDateTime stamp) {
        long spent = store.dailyTotal(now);
        long budget = settings.dailyBudgetSatang();
        if (spent > budget) {
            notifications.raiseHero(new Notification(NotificationType.DAILY_BUDGET_EXCEEDED,
                    "⚠ Daily Budget Exceeded",
                    "Spent " + thb(spent) + " / limit " + thb(budget)
                            + " (over by " + thb(spent - budget) + ")",
                    stamp));
        }
    }

    private void checkRefill(LocalDate now, LocalDateTime stamp) {
        int overdue = refills.overdueItems(now).size();
        if (overdue > 0) {
            notifications.raiseHero(new Notification(NotificationType.REFILL_DUE,
                    "🔁 Refill Reminder",
                    overdue + (overdue == 1 ? " item is" : " items are") + " overdue for refill",
                    stamp));
        }
    }

    private void checkBillDue(LocalDate now, LocalDateTime stamp) {
        int due = 0;
        Iterator<Bill> it = store.bills().values();
        while (it.hasNext()) {
            if (it.next().isDue(now)) {
                due++;
            }
        }
        if (due > 0) {
            notifications.raiseHero(new Notification(NotificationType.BILL_DUE,
                    "💳 Bill Due",
                    due + (due == 1 ? " bill is" : " bills are") + " due for payment",
                    stamp));
        }
    }

    /** @return satang formatted as THB with two decimals. */
    private static String thb(long satang) {
        return String.format("%.2f THB", satang / 100.0);
    }
}
