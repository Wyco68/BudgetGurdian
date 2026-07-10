package com.budgetguardian.service;

import java.time.LocalDateTime;

/**
 * One alert shown on the hero banner or held in the reminder queue.
 *
 * <p>Immutable value object. {@link #priority()} mirrors the
 * {@link NotificationType} priority so the banner {@code PriorityQueue}
 * orders purely on the notification.</p>
 *
 * @param type     alert kind
 * @param title    short headline, e.g. "⚠ Daily Budget Exceeded"
 * @param message  detail line with the numbers
 * @param raisedAt when the alert was created (tie-breaker: older wins)
 */
public record Notification(NotificationType type, String title, String message, LocalDateTime raisedAt) {

    public Notification {
        if (type == null) {
            throw new IllegalArgumentException("Notification type must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Title must not be blank");
        }
        if (message == null) {
            throw new IllegalArgumentException("Message must not be null");
        }
        if (raisedAt == null) {
            throw new IllegalArgumentException("raisedAt must not be null");
        }
    }

    /** @return this notification's priority (from its type). */
    public int priority() {
        return type.priority();
    }
}
