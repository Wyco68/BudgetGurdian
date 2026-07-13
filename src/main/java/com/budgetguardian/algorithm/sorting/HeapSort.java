package com.budgetguardian.algorithm.sorting;

import com.budgetguardian.datastructures.Comparator;
import com.budgetguardian.datastructures.DynamicArray;

/**
 * In-place heap sort using a binary max-heap built inside the array.
 *
 * <p><b>Purpose / where used:</b> ranking queries such as "top spending
 * categories" — the same sift-down logic that powers
 * {@link com.budgetguardian.datastructures.PriorityQueue}, applied in place.</p>
 *
 * <p><b>Method:</b> build a max-heap (Floyd's O(n) bottom-up heapify), then
 * repeatedly swap the max to the end and shrink the heap, producing ascending
 * order.</p>
 *
 * <p><b>Advantages:</b> guaranteed O(n log n), in-place (O(1) extra).
 * <b>Trade-offs:</b> not stable; poor cache locality versus quicksort.</p>
 *
 * <p><b>Time complexity:</b> O(n log n) best/avg/worst.
 * <b>Space complexity:</b> O(1) auxiliary.</p>
 *
 * @param <T> element type
 */
public final class HeapSort<T> implements SortStrategy<T> {

    @Override
    @SuppressWarnings("unchecked")
    public void sort(DynamicArray<T> data, Comparator<T> comparator) {
        if (data == null || comparator == null) {
            throw new IllegalArgumentException("Data and comparator must not be null");
        }
        DynamicArray<Object> a = (DynamicArray<Object>) data;
        Comparator<Object> c = (Comparator<Object>) comparator;
        int n = a.size();
        for (int i = n / 2 - 1; i >= 0; i--) {
            siftDown(a, c, i, n);
        }
        for (int end = n - 1; end > 0; end--) {
            swap(a, 0, end);
            siftDown(a, c, 0, end);
        }
    }

    private void siftDown(DynamicArray<Object> a, Comparator<Object> c, int root, int size) {
        while (true) {
            int left = 2 * root + 1;
            int right = left + 1;
            int largest = root;
            if (left < size && c.compare(a.get(left), a.get(largest)) > 0) {
                largest = left;
            }
            if (right < size && c.compare(a.get(right), a.get(largest)) > 0) {
                largest = right;
            }
            if (largest == root) {
                return;
            }
            swap(a, root, largest);
            root = largest;
        }
    }

    private void swap(DynamicArray<Object> a, int i, int j) {
        Object tmp = a.get(i);
        a.set(i, a.get(j));
        a.set(j, tmp);
    }
}
