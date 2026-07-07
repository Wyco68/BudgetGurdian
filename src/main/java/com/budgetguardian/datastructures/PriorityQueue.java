package com.budgetguardian.datastructures;

import java.util.NoSuchElementException;

/**
 * Priority queue implemented as a binary max-heap — custom replacement for
 * {@code java.util.PriorityQueue}.
 *
 * <p><b>Purpose:</b> the hero banner. All active alerts are inserted; the UI
 * shows only {@link #peek()} — the single highest-priority notification
 * (danger spending 100 &gt; debt overdue 90 &gt; daily budget 80 &gt; refill 60
 * &gt; daily reminder 20).</p>
 *
 * <p><b>Design:</b> the heap lives in a {@link DynamicArray} — deliberate
 * structure reuse. For node {@code i}: parent {@code (i-1)/2}, children
 * {@code 2i+1} / {@code 2i+2}. The injected {@link Comparator} defines
 * priority; {@code compare(a,b) > 0} means {@code a} outranks {@code b}
 * (max-heap). Callers wanting deterministic ties compose the comparator,
 * e.g. priority descending then timestamp ascending.</p>

 * <p><b>Advantages:</b> O(1) access to the maximum, O(log n) insert/poll,
 * compact array storage. <b>Trade-offs:</b> no efficient search for arbitrary
 * elements (O(n)); iteration order is heap order, not priority order.</p>
 *
 * <p><b>Time complexity:</b> insert O(log n) (+ amortized array growth),
 * peek O(1), poll O(log n), size/isEmpty O(1).
 * <b>Space complexity:</b> O(n).</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * PriorityQueue<Notification> banner = new PriorityQueue<>(
 *         (a, b) -> Integer.compare(a.priority(), b.priority()));
 * banner.insert(dangerAlert);
 * Notification top = banner.peek();   // highest priority, O(1)
 * }</pre>
 *
 * @param <T> element type
 */
public class PriorityQueue<T> {

    private final DynamicArray<T> heap = new DynamicArray<>();
    private final Comparator<T> comparator;

    /**
     * @param comparator ordering strategy; larger = higher priority
     * @throws IllegalArgumentException if {@code comparator} is null
     */
    public PriorityQueue(Comparator<T> comparator) {
        if (comparator == null) {
            throw new IllegalArgumentException("Comparator must not be null");
        }
        this.comparator = comparator;
    }

    /**
     * Inserts an element and restores the heap property by sifting up.
     * O(log n).
     *
     * @throws IllegalArgumentException if {@code element} is null
     */
    public void insert(T element) {
        heap.append(element);          // rejects null
        siftUp(heap.size() - 1);
    }

    /**
     * @return the highest-priority element without removing it. O(1).
     * @throws NoSuchElementException if empty
     */
    public T peek() {
        if (heap.isEmpty()) {
            throw new NoSuchElementException("Priority queue is empty");
        }
        return heap.get(0);
    }

    /**
     * Removes and returns the highest-priority element: the last leaf is moved
     * to the root, then sifted down. O(log n).
     *
     * @throws NoSuchElementException if empty
     */
    public T poll() {
        T max = peek();
        T last = heap.remove(heap.size() - 1);
        if (!heap.isEmpty()) {
            heap.set(0, last);
            siftDown(0);
        }
        return max;
    }

    /**
     * Removes the first element equal to {@code value} (linear scan, then one
     * sift). O(n) search + O(log n) restore — acceptable: the banner queue is
     * tiny and removal happens only on alert dismissal.
     *
     * @return {@code true} if an element was removed
     */
    public boolean remove(T value) {
        int index = heap.indexOf(value);
        if (index < 0) {
            return false;
        }
        T last = heap.remove(heap.size() - 1);
        if (index < heap.size()) {
            heap.set(index, last);
            siftDown(index);
            siftUp(index);
        }
        return true;
    }

    /** @return element count. O(1). */
    public int size() {
        return heap.size();
    }

    /** @return {@code true} if no elements. O(1). */
    public boolean isEmpty() {
        return heap.isEmpty();
    }

    /** Removes all elements. */
    public void clear() {
        heap.clear();
    }

    /** Moves node {@code i} up while it outranks its parent. O(log n). */
    private void siftUp(int i) {
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (comparator.compare(heap.get(i), heap.get(parent)) <= 0) {
                return;
            }
            swap(i, parent);
            i = parent;
        }
    }

    /** Moves node {@code i} down while a child outranks it. O(log n). */
    private void siftDown(int i) {
        int size = heap.size();
        while (true) {
            int left = 2 * i + 1;
            int right = left + 1;
            int largest = i;
            if (left < size && comparator.compare(heap.get(left), heap.get(largest)) > 0) {
                largest = left;
            }
            if (right < size && comparator.compare(heap.get(right), heap.get(largest)) > 0) {
                largest = right;
            }
            if (largest == i) {
                return;
            }
            swap(i, largest);
            i = largest;
        }
    }

    private void swap(int a, int b) {
        T tmp = heap.get(a);
        heap.set(a, heap.get(b));
        heap.set(b, tmp);
    }
}
