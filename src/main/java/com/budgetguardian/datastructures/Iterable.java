package com.budgetguardian.datastructures;

/**
 * Contract for structures that can produce a {@link Iterator}.
 *
 * <p><b>Purpose:</b> custom counterpart of {@code java.lang.Iterable} used
 * across the data-structure package, keeping business code decoupled from the
 * Java Collections Framework.</p>
 *
 * <p><b>Usage:</b> implemented by {@code DynamicArray},
 * {@code DoublyLinkedList}, {@code CircularBuffer} and the view iterators of
 * {@code HashMap} and {@code Graph}.</p>
 *
 * @param <T> element type produced by the iterator
 */
public interface Iterable<T> {

    /** @return a fresh iterator positioned before the first element */
    Iterator<T> iterator();
}
