package com.budgetguardian.datastructures;

import java.util.NoSuchElementException;

/**
 * Fixed-capacity ring buffer that overwrites its oldest element when full.
 *
 * <p><b>Purpose:</b> the "recent transactions" dashboard widget — always the
 * last {@code capacity} (20 in the app) transactions, in constant memory,
 * with no shifting and no eviction bookkeeping.</p>
 *
 * <p><b>Design:</b> one array plus a {@code head} index (position of the
 * oldest element) and {@code size}. Writing when full advances {@code head},
 * silently dropping the oldest entry. Logical index {@code i} maps to
 * physical slot {@code (head + i) % capacity}.</p>
 *
 * <p><b>Advantages:</b> O(1) add regardless of fullness, fixed memory,
 * oldest-eviction for free. <b>Trade-offs:</b> capacity fixed at construction;
 * overwritten elements are gone — this is a view, not a store (the full ledger
 * lives in {@code DoublyLinkedList}).</p>
 *
 * <p><b>Time complexity:</b> add/get/size/isFull O(1); iteration O(n).
 * <b>Space complexity:</b> O(capacity), constant for the app's lifetime.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * CircularBuffer<Transaction> recent = new CircularBuffer<>(20);
 * recent.add(txn);                        // O(1), evicts oldest when full
 * Iterator<Transaction> widget = recent.newestFirst();
 * }</pre>
 *
 * @param <T> element type
 */
public class CircularBuffer<T> implements Iterable<T> {

    private final Object[] slots;
    private int head;
    private int size;

    /**
     * @param capacity fixed maximum element count, at least 1
     * @throws IllegalArgumentException if {@code capacity < 1}
     */
    public CircularBuffer(int capacity) {
        if (capacity < 1) {
            throw new IllegalArgumentException("Capacity must be >= 1, got " + capacity);
        }
        this.slots = new Object[capacity];
    }

    /**
     * Appends an element; when full, the oldest element is overwritten. O(1).
     *
     * @throws IllegalArgumentException if {@code element} is null
     */
    public void add(T element) {
        if (element == null) {
            throw new IllegalArgumentException("Null elements are not allowed");
        }
        int tail = (head + size) % slots.length;
        slots[tail] = element;
        if (size == slots.length) {
            head = (head + 1) % slots.length;   // overwrote the oldest
        } else {
            size++;
        }
    }

    /**
     * @param index logical position, 0 = oldest retained element
     * @return element at that position. O(1).
     * @throws IndexOutOfBoundsException if index outside {@code [0, size)}
     */
    @SuppressWarnings("unchecked")
    public T get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index " + index + ", size " + size);
        }
        return (T) slots[(head + index) % slots.length];
    }

    /** @return retained element count, at most capacity. O(1). */
    public int size() {
        return size;
    }

    /** @return fixed capacity. O(1). */
    public int capacity() {
        return slots.length;
    }

    /** @return {@code true} if no elements. O(1). */
    public boolean isEmpty() {
        return size == 0;
    }

    /** @return {@code true} when the next add will evict the oldest element. O(1). */
    public boolean isFull() {
        return size == slots.length;
    }

    /** Removes all elements. O(capacity) — clears slots for GC. */
    public void clear() {
        for (int i = 0; i < slots.length; i++) {
            slots[i] = null;
        }
        head = 0;
        size = 0;
    }

    /** @return iterator from oldest to newest. */
    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private int cursor;

            @Override
            public boolean hasNext() {
                return cursor < size;
            }

            @Override
            public T next() {
                if (cursor >= size) {
                    throw new NoSuchElementException();
                }
                return get(cursor++);
            }
        };
    }

    /** @return iterator from newest to oldest (dashboard display order). */
    public Iterator<T> newestFirst() {
        return new Iterator<>() {
            private int cursor = size - 1;

            @Override
            public boolean hasNext() {
                return cursor >= 0;
            }

            @Override
            public T next() {
                if (cursor < 0) {
                    throw new NoSuchElementException();
                }
                return get(cursor--);
            }
        };
    }
}
