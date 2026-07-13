package com.budgetguardian.algorithm.searching;

import com.budgetguardian.datastructures.DynamicArray;

/**
 * Strategy interface for locating an element in a {@link DynamicArray}.
 *
 * <p>Implementations differ in their precondition and cost: linear search
 * needs no ordering (O(n)); binary search needs a sorted array (O(log n)).
 * The report and search layers pick the right one for the data at hand.</p>
 *
 * @param <T> element type
 */
public interface SearchStrategy<T> {

    /** Sentinel for "not found". */
    int NOT_FOUND = -1;

    /**
     * @return index of a matching element, or {@link #NOT_FOUND}
     */
    int search(DynamicArray<T> data, T target, com.budgetguardian.datastructures.Comparator<T> comparator);
}
