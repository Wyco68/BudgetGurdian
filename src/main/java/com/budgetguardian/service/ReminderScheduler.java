package com.budgetguardian.service;

import com.budgetguardian.datastructures.Iterator;
import com.budgetguardian.model.Transaction;
import com.budgetguardian.model.TransactionType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Raises the daily 8 PM reminder when today has no recorded expenses.
 *
 * <p><b>Design:</b> the real app runs {@link #tick()} on a daemon thread
 * every 60 seconds. All state mutation is marshalled through an injected
 * executor ({@code Platform::runLater} in production, a synchronous runnable
 * in tests) so structure access stays on the JavaFX thread. The clock is a
 * {@link Supplier} of {@code LocalDateTime}, so tests drive time directly
 * without sleeping.</p>
 *
 * <p><b>Rule:</b> at or after the configured reminder time (default 20:00),
 * if no expense is dated today, enqueue one daily reminder — and only one per
 * calendar day, tracked by {@code lastReminderDate}.</p>
 *
 * <p><b>OS-level notification:</b> the same reminder is also handed to an
 * {@code onOsNotify} callback, invoked directly on the caller's thread (not
 * marshalled through {@code executor}) since it is a plain OS call, not a
 * JavaFX toolkit access. Defaults to a no-op so tests and any caller that
 * doesn't want a system notification are unaffected.</p>
 *
 * <p><b>Time complexity:</b> each tick O(n) worst case scanning the ledger
 * tail for a today-dated expense (stops at the first hit).</p>
 */
public final class ReminderScheduler {

    private final DataStore store;
    private final NotificationService notifications;
    private final SettingsService settings;
    private final Supplier<LocalDateTime> clock;
    private final Consumer<Runnable> executor;
    private final Consumer<Notification> onOsNotify;

    private LocalDate lastReminderDate;

    public ReminderScheduler(DataStore store, NotificationService notifications,
                             SettingsService settings, Supplier<LocalDateTime> clock,
                             Consumer<Runnable> executor) {
        this(store, notifications, settings, clock, executor, notification -> { });
    }

    public ReminderScheduler(DataStore store, NotificationService notifications,
                             SettingsService settings, Supplier<LocalDateTime> clock,
                             Consumer<Runnable> executor, Consumer<Notification> onOsNotify) {
        this.store = store;
        this.notifications = notifications;
        this.settings = settings;
        this.clock = clock;
        this.executor = executor;
        this.onOsNotify = onOsNotify;
    }

    /**
     * One scheduler tick: checks the reminder condition and, if met, raises
     * the reminder via the executor. Safe to call at any frequency.
     */
    public void tick() {
        LocalDateTime now = clock.get();
        LocalDate day = now.toLocalDate();
        if (day.equals(lastReminderDate)) {
            return;                                        // already reminded today
        }
        if (now.toLocalTime().isBefore(settings.reminderTime())) {
            return;                                        // too early
        }
        if (hasExpenseOn(day)) {
            return;                                        // nothing to remind about
        }
        lastReminderDate = day;
        Notification reminder = new Notification(
                NotificationType.DAILY_REMINDER,
                "🔔 Daily Reminder",
                "Have you recorded today's expenses?",
                now);
        executor.accept(() -> notifications.enqueueReminder(reminder));
        onOsNotify.accept(reminder);
    }

    private boolean hasExpenseOn(LocalDate day) {
        Iterator<Transaction> it = store.ledger().descendingIterator();
        while (it.hasNext()) {
            Transaction txn = it.next();
            if (txn.type() == TransactionType.EXPENSE && txn.date().equals(day)) {
                return true;
            }
            if (txn.date().isBefore(day)) {
                return false;    // ledger is chronological; older entries only from here
            }
        }
        return false;
    }
}
