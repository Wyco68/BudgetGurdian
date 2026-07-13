package com.budgetguardian.algorithm.sorting;

import com.budgetguardian.datastructures.Comparator;
import com.budgetguardian.datastructures.DynamicArray;

/**
 * Top-down merge sort — stable, O(n log n) worst case.
 *
 * <p><b>Purpose / where used:</b> ordering report rows by date or amount,
 * where <em>stability</em> matters (equal keys keep their original relative
 * order — e.g. two transactions on the same date stay in id order).</p>
 *
 * <p><b>Advantages:</b> guaranteed O(n log n) (no bad pivots), stable.
 * <b>Trade-offs:</b> O(n) auxiliary space for the merge buffer, not in-place.</p>
 *
 * <p><b>Time complexity:</b> O(n log n) best/avg/worst.
 * <b>Space complexity:</b> O(n) auxiliary.</p>
 *
 * @param <T> element type
 */
public final class MergeSort<T> implements SortStrategy<T> {

    @Override
    @SuppressWarnings("unchecked")
    public void sort(DynamicArray<T> data, Comparator<T> comparator) {
        if (data == null || comparator == null) {
            throw new IllegalArgumentException("Data and comparator must not be null");
        }
        int n = data.size();
        if (n < 2) {
            return;
        }
        Object[] buffer = new Object[n];
        sort((DynamicArray<Object>) data, (Comparator<Object>) comparator, buffer, 0, n - 1);
    }

    private void sort(DynamicArray<Object> data, Comparator<Object> comparator,
                      Object[] buffer, int lo, int hi) {
        if (lo >= hi) {
            return;
        }
        int mid = lo + (hi - lo) / 2;
        sort(data, comparator, buffer, lo, mid);
        sort(data, comparator, buffer, mid + 1, hi);
        merge(data, comparator, buffer, lo, mid, hi);
    }

    private void merge(DynamicArray<Object> data, Comparator<Object> comparator,
                       Object[] buffer, int lo, int mid, int hi) {
        for (int k = lo; k <= hi; k++) {
            buffer[k] = data.get(k);
        }
        int i = lo;
        int j = mid + 1;
        for (int k = lo; k <= hi; k++) {
            if (i > mid) {
                data.set(k, buffer[j++]);
            } else if (j > hi) {
                data.set(k, buffer[i++]);
            } else if (comparator.compare(buffer[j], buffer[i]) < 0) {
                data.set(k, buffer[j++]);          // strictly-less keeps left first → stable
            } else {
                data.set(k, buffer[i++]);
            }
        }
    }
}
