package com.budgetguardian.service;

import com.budgetguardian.datastructures.Comparator;
import com.budgetguardian.datastructures.PriorityQueue;
import com.budgetguardian.datastructures.Queue;

import java.util.NoSuchElementException;

/**
 * Owns the two notification structures: the hero-banner
 * {@code PriorityQueue} and the FIFO reminder {@code Queue}.
 *
 * <p><b>Hero banner:</b> all active alerts are inserted; the UI shows only
 * {@link #heroBanner()} — the single highest-priority notification. Ordering
 * comes from a {@link Comparator}: priority descending, then {@code raisedAt}
 * ascending so an older alert of equal priority wins, giving a deterministic
 * banner.</p>
 *
 * <p><b>Reminder queue:</b> the daily 8 PM nudge is delivered FIFO, separate
 * from the priority banner.</p>
 *
 * <p><b>Time complexity:</b> raiseHero O(log n), heroBanner O(1), dismiss
 * O(log n); enqueue/dequeue reminder O(1).</p>
 */
public final class NotificationService {

    /** Priority DESC, then older-first on ties. */
    private static final Comparator<Notification> HERO_ORDER = (a, b) -> {
        int byPriority = Integer.compare(a.priority(), b.priority());
        if (byPriority != 0) {
            return byPriority;
        }
        return b.raisedAt().compareTo(a.raisedAt());   // older raisedAt outranks
    };

    private final EventBus bus;
    private final PriorityQueue<Notification> heroBanners = new PriorityQueue<>(HERO_ORDER);
    private final Queue<Notification> reminders = new Queue<>();

    public NotificationService(EventBus bus) {
        this.bus = bus;
    }

    /** Adds an alert to the banner and publishes {@code NOTIFICATION_RAISED}. O(log n). */
    public void raiseHero(Notification notification) {
        heroBanners.insert(notification);
        bus.publish(EventType.NOTIFICATION_RAISED);
    }

    /** @return the highest-priority active alert, or null if none. O(1). */
    public Notification heroBanner() {
        return heroBanners.isEmpty() ? null : heroBanners.peek();
    }

    /** @return active banner count. O(1). */
    public int activeCount() {
        return heroBanners.size();
    }

    /**
     * Removes and returns the current top banner (user dismissed it),
     * revealing the next highest. O(log n).
     *
     * @throws NoSuchElementException if no banner is active
     */
    public Notification dismiss() {
        if (heroBanners.isEmpty()) {
            throw new NoSuchElementException("No active banner to dismiss");
        }
        Notification dismissed = heroBanners.poll();
        bus.publish(EventType.NOTIFICATION_RAISED);
        return dismissed;
    }

    /** Clears every banner (used before a rule-engine rebuild). O(n). */
    public void clearHero() {
        heroBanners.clear();
    }

    /** Adds a reminder to the FIFO queue and publishes. O(1). */
    public void enqueueReminder(Notification reminder) {
        reminders.enqueue(reminder);
        bus.publish(EventType.NOTIFICATION_RAISED);
    }

    /** @return the reminder queue (peek/dequeue by the UI). */
    public Queue<Notification> reminders() {
        return reminders;
    }
}
