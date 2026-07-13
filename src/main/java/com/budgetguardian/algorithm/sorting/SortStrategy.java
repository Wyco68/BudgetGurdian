package com.budgetguardian.algorithm.sorting;

import com.budgetguardian.datastructures.Comparator;
import com.budgetguardian.datastructures.DynamicArray;

/**
 * Strategy interface for in-place sorting of a {@link DynamicArray}.
 *
 * <p><b>Purpose:</b> the Strategy-pattern seam that lets the report layer
 * choose a sort by its properties (stability, worst-case bound, in-place)
 * without the callers knowing the algorithm. All implementations operate on
 * the custom {@code DynamicArray} with a custom {@link Comparator}.</p>
 *
 * @param <T> element type
 */
public interface SortStrategy<T> {

    /**
     * Sorts {@code data} ascending by {@code comparator}, in place.
     *
     * @throws IllegalArgumentException if either argument is null
     */
    void sort(DynamicArray<T> data, Comparator<T> comparator);
}
