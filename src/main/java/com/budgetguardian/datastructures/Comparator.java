package com.budgetguardian.datastructures;

/**
 * Ordering strategy for elements that have no natural order, or need an order
 * different from their natural one.
 *
 * <p><b>Purpose:</b> custom counterpart of {@code java.util.Comparator}. It is
 * the seam of the Strategy pattern used by {@code PriorityQueue} and the
 * sorting algorithms in {@code com.budgetguardian.algorithm.sorting}: callers
 * inject the ordering, the structure/algorithm stays generic.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * Comparator<Notification> byPriority =
 *         (a, b) -> Integer.compare(a.priority(), b.priority());
 * PriorityQueue<Notification> banner = new PriorityQueue<>(byPriority);
 * }</pre>
 *
 * @param <T> type being compared
 */
@FunctionalInterface
public interface Comparator<T> {

    /**
     * @return negative if {@code a < b}, zero if equal, positive if {@code a > b}
     */
    int compare(T a, T b);
}
