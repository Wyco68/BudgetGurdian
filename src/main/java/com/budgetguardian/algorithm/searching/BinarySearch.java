package com.budgetguardian.algorithm.searching;

import com.budgetguardian.datastructures.Comparator;
import com.budgetguardian.datastructures.DynamicArray;

/**
 * Binary search over a {@link DynamicArray} sorted by the same comparator.
 *
 * <p><b>Purpose / where used:</b> fast membership/index lookup on report data
 * that has already been sorted (e.g. locating a date in a sorted
 * daily-total array).</p>
 *
 * <p><b>Precondition:</b> {@code data} must be sorted ascending by
 * {@code comparator}; results are undefined otherwise.</p>
 *
 * <p><b>Advantages:</b> O(log n) lookups on sorted data. <b>Trade-offs:</b>
 * requires a sorted array and O(1) random access (which {@code DynamicArray}
 * provides).</p>
 *
 * <p><b>Time complexity:</b> O(log n). <b>Space complexity:</b> O(1).</p>
 *
 * @param <T> element type
 */
public final class BinarySearch<T> implements SearchStrategy<T> {

    /**
     * @return an index whose element equals {@code target}, or
     *         {@link #NOT_FOUND}
     */
    @Override
    public int search(DynamicArray<T> data, T target, Comparator<T> comparator) {
        if (data == null || comparator == null) {
            throw new IllegalArgumentException("Data and comparator must not be null");
        }
        int lo = 0;
        int hi = data.size() - 1;
        while (lo <= hi) {
            int mid = lo + (hi - lo) / 2;
            int cmp = comparator.compare(data.get(mid), target);
            if (cmp == 0) {
                return mid;
            }
            if (cmp < 0) {
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return NOT_FOUND;
    }
}
