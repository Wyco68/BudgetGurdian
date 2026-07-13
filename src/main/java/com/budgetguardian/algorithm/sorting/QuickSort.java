package com.budgetguardian.algorithm.sorting;

import com.budgetguardian.datastructures.Comparator;
import com.budgetguardian.datastructures.DynamicArray;

/**
 * In-place quick sort with median-of-three pivot selection.
 *
 * <p><b>Purpose / where used:</b> general-purpose ordering of chart/report
 * data where stability is not required and the low constant factor and
 * in-place operation are attractive.</p>
 *
 * <p><b>Pivot:</b> median-of-three (first, middle, last) reduces the chance of
 * the O(n²) worst case on already-sorted or reverse-sorted input.</p>
 *
 * <p><b>Advantages:</b> in-place (O(log n) stack), fast average case.
 * <b>Trade-offs:</b> not stable; O(n²) worst case on adversarial input.</p>
 *
 * <p><b>Time complexity:</b> O(n log n) average, O(n²) worst.
 * <b>Space complexity:</b> O(log n) recursion.</p>
 *
 * @param <T> element type
 */
public final class QuickSort<T> implements SortStrategy<T> {

    @Override
    @SuppressWarnings("unchecked")
    public void sort(DynamicArray<T> data, Comparator<T> comparator) {
        if (data == null || comparator == null) {
            throw new IllegalArgumentException("Data and comparator must not be null");
        }
        if (data.size() < 2) {
            return;
        }
        sort((DynamicArray<Object>) data, (Comparator<Object>) comparator, 0, data.size() - 1);
    }

    private void sort(DynamicArray<Object> data, Comparator<Object> comparator, int lo, int hi) {
        while (lo < hi) {
            int p = partition(data, comparator, lo, hi);
            // recurse into the smaller side, loop on the larger — bounds stack to O(log n)
            if (p - lo < hi - p) {
                sort(data, comparator, lo, p - 1);
                lo = p + 1;
            } else {
                sort(data, comparator, p + 1, hi);
                hi = p - 1;
            }
        }
    }

    private int partition(DynamicArray<Object> data, Comparator<Object> comparator, int lo, int hi) {
        int mid = lo + (hi - lo) / 2;
        int pivotIndex = medianOfThree(data, comparator, lo, mid, hi);
        swap(data, pivotIndex, hi);                // park pivot at the end
        Object pivot = data.get(hi);
        int store = lo;
        for (int i = lo; i < hi; i++) {
            if (comparator.compare(data.get(i), pivot) < 0) {
                swap(data, i, store++);
            }
        }
        swap(data, store, hi);
        return store;
    }

    private int medianOfThree(DynamicArray<Object> data, Comparator<Object> comparator,
                              int a, int b, int c) {
        Object x = data.get(a);
        Object y = data.get(b);
        Object z = data.get(c);
        if (comparator.compare(x, y) < 0) {
            if (comparator.compare(y, z) < 0) {
                return b;
            }
            return comparator.compare(x, z) < 0 ? c : a;
        }
        if (comparator.compare(x, z) < 0) {
            return a;
        }
        return comparator.compare(y, z) < 0 ? c : b;
    }

    private void swap(DynamicArray<Object> data, int i, int j) {
        Object tmp = data.get(i);
        data.set(i, data.get(j));
        data.set(j, tmp);
    }
}
