package com.budgetguardian.algorithm.searching;

import com.budgetguardian.datastructures.Comparator;
import com.budgetguardian.datastructures.DynamicArray;

/**
 * Linear (sequential) search — no ordering precondition.
 *
 * <p><b>Purpose / where used:</b> finding an element in an unsorted
 * {@code DynamicArray}, and the fallback whenever the data cannot be kept
 * sorted (e.g. the transaction ledger for substring queries).</p>
 *
 * <p><b>Advantages:</b> works on any order, O(1) space, finds the first match.
 * <b>Trade-offs:</b> O(n) time — no better without an index.</p>
 *
 * <p><b>Time complexity:</b> O(n). <b>Space complexity:</b> O(1).</p>
 *
 * @param <T> element type
 */
public final class LinearSearch<T> implements SearchStrategy<T> {

    /**
     * @return index of the first element equal (comparator == 0) to
     *         {@code target}, or {@link #NOT_FOUND}
     */
    @Override
    public int search(DynamicArray<T> data, T target, Comparator<T> comparator) {
        if (data == null || comparator == null) {
            throw new IllegalArgumentException("Data and comparator must not be null");
        }
        for (int i = 0; i < data.size(); i++) {
            if (comparator.compare(data.get(i), target) == 0) {
                return i;
            }
        }
        return NOT_FOUND;
    }
}
