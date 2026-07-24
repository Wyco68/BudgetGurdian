package com.budgetguardian.model;

import java.time.LocalDate;

/**
 * A recurring purchase the user confirmed as refillable (answered "Yes").
 * Declined items are never stored — the next duplicate purchase asks again.
 *
 * <p>Keyed by normalized (lowercase, trimmed) item name in a
 * {@code HashMap<String, RefillItem>}. {@code intervalDays} is the running
 * average gap between purchases; when {@code today > lastPurchase + interval}
 * a priority-60 refill reminder is raised.</p>
 *
 * @param name          normalized item name, the map key
 * @param intervalDays  average days between purchases, positive
 * @param lastPurchase  most recent purchase day
 * @param purchaseCount how many times the item was bought, at least 1
 */
public record RefillItem(String name, double intervalDays, LocalDate lastPurchase, int purchaseCount) {

    public RefillItem {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Item name must not be blank");
        }
        if (intervalDays <= 0) {
            throw new IllegalArgumentException("Interval must be positive, got " + intervalDays);
        }
        if (lastPurchase == null) {
            throw new IllegalArgumentException("Last purchase date must not be null");
        }
        if (purchaseCount < 1) {
            throw new IllegalArgumentException("Purchase count must be >= 1, got " + purchaseCount);
        }
    }

    /** @return next expected purchase day: last purchase + rounded interval. O(1). */
    public LocalDate nextExpected() {
        return lastPurchase.plusDays(Math.round(intervalDays));
    }

    /** @return whether the item is overdue for refill on {@code today}. O(1). */
    public boolean isOverdue(LocalDate today) {
        return today.isAfter(nextExpected());
    }

    /**
     * @return days the current stock has lasted so far — elapsed days since
     *         the last purchase. Negative-safe: a backdated "today" yields 0.
     */
    public long daysLasted(LocalDate today) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(lastPurchase, today);
        return Math.max(0, days);
    }

    /**
     * Records another purchase: interval becomes the running average of gaps,
     * count increments, last purchase advances. O(1).
     *
     * <p>Running average: {@code newAvg = (oldAvg * (n-1) + gap) / n} where
     * {@code n} is the new gap count ({@code purchaseCount} after this buy
     * minus 1).</p>
     *
     * @param purchaseDate day of the new purchase, after {@link #lastPurchase()}
     */
    public RefillItem recordPurchase(LocalDate purchaseDate) {
        if (purchaseDate == null || !purchaseDate.isAfter(lastPurchase)) {
            throw new IllegalArgumentException("New purchase must be after " + lastPurchase);
        }
        long gap = java.time.temporal.ChronoUnit.DAYS.between(lastPurchase, purchaseDate);
        int gaps = purchaseCount;                       // gaps recorded after this purchase
        double newInterval = (intervalDays * (gaps - 1) + gap) / gaps;
        return new RefillItem(name, newInterval, purchaseDate, purchaseCount + 1);
    }
}
