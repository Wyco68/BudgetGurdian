package com.budgetguardian.datastructures;

import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;

/**
 * Resizable array — custom replacement for {@code java.util.ArrayList}.
 *
 * <p><b>Purpose:</b> contiguous, index-addressable storage with amortized
 * constant-time append. Backbone of dashboard aggregates, chart series and
 * report rows, and the backing store of {@code PriorityQueue}.</p>
 *
 * <p><b>Why it exists / where used:</b> dashboards and reports need random
 * access ({@code get(i)}) and cheap append while building result sets;
 * a linked structure would cost O(n) per access.</p>
 *
 * <p><b>Advantages:</b> O(1) random access, cache-friendly layout, amortized
 * O(1) append. <b>Trade-offs:</b> O(n) insert/remove in the middle (shift),
 * grow/shrink copies the whole backing array.</p>
 *
 * <p><b>Resizing policy:</b> starts at capacity {@value #DEFAULT_CAPACITY},
 * doubles when full (amortized O(1) append), halves when size drops to a
 * quarter of capacity (never below the default) — the hysteresis between the
 * ×2 grow and ¼ shrink thresholds prevents thrashing at a boundary.</p>
 *
 * <p><b>Time complexity:</b> get/set O(1); append amortized O(1), worst O(n)
 * on grow; insert/remove O(n); indexOf/contains O(n).
 * <b>Space complexity:</b> O(n), at most 4n references directly after a shrink
 * trigger point, usually ≤ 2n.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * DynamicArray<Long> dailyTotals = new DynamicArray<>();
 * dailyTotals.append(4500L);
 * long first = dailyTotals.get(0);
 * }</pre>
 *
 * <p>Iterators are fail-fast: structural modification after iterator creation
 * causes {@link ConcurrentModificationException} on the next call.
 * Null elements are rejected with {@link IllegalArgumentException}.</p>
 *
 * @param <T> element type
 */
public class DynamicArray<T> implements Iterable<T> {

    /** Initial and minimum capacity of the backing array. */
    static final int DEFAULT_CAPACITY = 8;

    private Object[] elements;
    private int size;
    private int modCount;

    /** Creates an empty array with capacity {@value #DEFAULT_CAPACITY}. */
    public DynamicArray() {
        this.elements = new Object[DEFAULT_CAPACITY];
    }

    /**
     * Creates an empty array with the given initial capacity.
     *
     * @param initialCapacity starting capacity, at least 1
     * @throws IllegalArgumentException if {@code initialCapacity < 1}
     */
    public DynamicArray(int initialCapacity) {
        if (initialCapacity < 1) {
            throw new IllegalArgumentException("Capacity must be >= 1, got " + initialCapacity);
        }
        this.elements = new Object[initialCapacity];
    }

    /**
     * Appends to the end. Amortized O(1); O(n) when a grow is triggered.
     *
     * @throws IllegalArgumentException if {@code element} is null
     */
    public void append(T element) {
        requireNonNull(element);
        growIfFull();
        elements[size++] = element;
        modCount++;
    }

    /**
     * Inserts at {@code index}, shifting later elements right. O(n).
     *
     * @param index position in {@code [0, size]}; {@code size} appends
     * @throws IndexOutOfBoundsException if index outside {@code [0, size]}
     * @throws IllegalArgumentException  if {@code element} is null
     */
    public void insert(int index, T element) {
        requireNonNull(element);
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("Index " + index + ", size " + size);
        }
        growIfFull();
        System.arraycopy(elements, index, elements, index + 1, size - index);
        elements[index] = element;
        size++;
        modCount++;
    }

    /**
     * Removes and returns the element at {@code index}, shifting later
     * elements left. O(n).
     *
     * @throws IndexOutOfBoundsException if index outside {@code [0, size)}
     */
    public T remove(int index) {
        T removed = get(index);
        System.arraycopy(elements, index + 1, elements, index, size - index - 1);
        elements[--size] = null;
        modCount++;
        shrinkIfSparse();
        return removed;
    }

    /**
     * @return element at {@code index}. O(1).
     * @throws IndexOutOfBoundsException if index outside {@code [0, size)}
     */
    @SuppressWarnings("unchecked")
    public T get(int index) {
        checkBounds(index);
        return (T) elements[index];
    }

    /**
     * Replaces the element at {@code index} and returns the previous value. O(1).
     *
     * @throws IndexOutOfBoundsException if index outside {@code [0, size)}
     * @throws IllegalArgumentException  if {@code element} is null
     */
    public T set(int index, T element) {
        requireNonNull(element);
        T previous = get(index);
        elements[index] = element;
        return previous;
    }

    /**
     * @return index of the first element equal to {@code value}, or -1. O(n).
     */
    public int indexOf(T value) {
        for (int i = 0; i < size; i++) {
            if (elements[i].equals(value)) {
                return i;
            }
        }
        return -1;
    }

    /** @return whether an equal element exists. O(n). */
    public boolean contains(T value) {
        return indexOf(value) >= 0;
    }

    /** @return element count. O(1). */
    public int size() {
        return size;
    }

    /** @return {@code true} if no elements. O(1). */
    public boolean isEmpty() {
        return size == 0;
    }

    /** Removes all elements and resets capacity to the default. O(1) amortized (drops the old array). */
    public void clear() {
        elements = new Object[DEFAULT_CAPACITY];
        size = 0;
        modCount++;
    }

    /** @return current backing capacity (exposed for tests and diagnostics). */
    int capacity() {
        return elements.length;
    }

    /** @return fail-fast iterator from index 0 to {@code size - 1}. */
    @Override
    public Iterator<T> iterator() {
        final int expectedModCount = modCount;
        return new Iterator<>() {
            private int cursor;

            @Override
            public boolean hasNext() {
                return cursor < size;
            }

            @Override
            public T next() {
                if (modCount != expectedModCount) {
                    throw new ConcurrentModificationException();
                }
                if (cursor >= size) {
                    throw new NoSuchElementException();
                }
                return get(cursor++);
            }
        };
    }

    private void growIfFull() {
        if (size == elements.length) {
            resize(elements.length * 2);
        }
    }

    private void shrinkIfSparse() {
        if (elements.length > DEFAULT_CAPACITY && size <= elements.length / 4) {
            resize(Math.max(DEFAULT_CAPACITY, elements.length / 2));
        }
    }

    private void resize(int newCapacity) {
        Object[] next = new Object[newCapacity];
        System.arraycopy(elements, 0, next, 0, size);
        elements = next;
    }

    private void checkBounds(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index " + index + ", size " + size);
        }
    }

    private static void requireNonNull(Object element) {
        if (element == null) {
            throw new IllegalArgumentException("Null elements are not allowed");
        }
    }
}
