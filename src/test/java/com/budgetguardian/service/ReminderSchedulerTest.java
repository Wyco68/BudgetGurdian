package com.budgetguardian.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Daily-reminder timing rules of {@link ReminderScheduler}. */
class ReminderSchedulerTest extends ServiceTestBase {

    private NotificationService notifications;
    private LocalDateTime clock;
    private ReminderScheduler scheduler;

    private void schedulerAt(LocalDateTime now) {
        this.clock = now;
        if (notifications == null) {
            notifications = new NotificationService(bus);
            // synchronous executor: run the marshalled work immediately
            scheduler = new ReminderScheduler(store, notifications, settingsService,
                    () -> clock, Runnable::run);
        }
    }

    @Test
    void firesAfterEightPmWhenNoExpenseToday() {
        schedulerAt(LocalDateTime.of(2026, 7, 6, 20, 0));
        scheduler.tick();
        assertNotNull(notifications.reminders().peek());
        assertEquals(NotificationType.DAILY_REMINDER, notifications.reminders().peek().type());
    }

    @Test
    void silentBeforeEightPm() {
        schedulerAt(LocalDateTime.of(2026, 7, 6, 19, 59));
        scheduler.tick();
        assertEquals(0, notifications.reminders().size());
    }

    @Test
    void silentWhenAnExpenseExistsToday() {
        transactionService.add(expense("SCB", DAILY_SPENDING, null, 100, java.time.LocalDate.of(2026, 7, 6)));
        schedulerAt(LocalDateTime.of(2026, 7, 6, 21, 0));
        scheduler.tick();
        assertEquals(0, notifications.reminders().size());
    }

    @Test
    void firesOnlyOncePerDay() {
        schedulerAt(LocalDateTime.of(2026, 7, 6, 20, 0));
        scheduler.tick();
        clock = LocalDateTime.of(2026, 7, 6, 20, 1);
        scheduler.tick();
        clock = LocalDateTime.of(2026, 7, 6, 23, 0);
        scheduler.tick();
        assertEquals(1, notifications.reminders().size());
    }

    @Test
    void firesAgainNextDay() {
        schedulerAt(LocalDateTime.of(2026, 7, 6, 20, 0));
        scheduler.tick();
        clock = LocalDateTime.of(2026, 7, 7, 20, 0);
        scheduler.tick();
        assertEquals(2, notifications.reminders().size());
    }

    @Test
    void incomeTodayDoesNotSuppressReminder() {
        // Only an EXPENSE counts as "recorded today's expenses".
        transactionService.add(income("SCB", 100_000, java.time.LocalDate.of(2026, 7, 6)));
        schedulerAt(LocalDateTime.of(2026, 7, 6, 20, 0));
        scheduler.tick();
        assertEquals(1, notifications.reminders().size());
    }

    @Test
    void osNotifyCallbackFiresOnceWithTheSameReminder() {
        java.util.List<Notification> osNotified = new java.util.ArrayList<>();
        clock = LocalDateTime.of(2026, 7, 6, 20, 0);
        NotificationService osNotifications = new NotificationService(bus);
        ReminderScheduler osScheduler = new ReminderScheduler(store, osNotifications, settingsService,
                () -> clock, Runnable::run, osNotified::add);

        osScheduler.tick();
        clock = LocalDateTime.of(2026, 7, 6, 20, 1);
        osScheduler.tick();

        assertEquals(1, osNotified.size());
        assertEquals(NotificationType.DAILY_REMINDER, osNotified.get(0).type());
        assertEquals(osNotifications.reminders().peek(), osNotified.get(0));
    }
}
