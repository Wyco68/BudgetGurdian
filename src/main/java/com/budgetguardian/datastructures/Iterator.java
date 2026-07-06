package com.budgetguardian.datastructures;

/**
 * Minimal iteration contract for all custom data structures.
 *
 * <p><b>Purpose:</b> replaces {@code java.util.Iterator} so the data-structure
 * package has zero dependency on the Java Collections Framework. Every
 * structure in this package exposes traversal exclusively through this
 * interface.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * Iterator<Transaction> it = transactions.iterator();
 * while (it.hasNext()) {
 *     process(it.next());
 * }
 * }</pre>
 *
 * <p><b>Time complexity:</b> implementations must provide O(1)
 * {@link #hasNext()} and amortized O(1) {@link #next()}.</p>
 *
 * @param <T> element type produced by this iterator
 */
public interface Iterator<T> {

    /** @return {@code true} if another element is available */
    boolean hasNext();

    /**
     * @return the next element
     * @throws java.util.NoSuchElementException if no element remains
     */
    T next();
}
