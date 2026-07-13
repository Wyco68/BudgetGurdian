package com.budgetguardian.algorithm;

import com.budgetguardian.algorithm.searching.BinarySearch;
import com.budgetguardian.algorithm.searching.LinearSearch;
import com.budgetguardian.algorithm.searching.SearchStrategy;
import com.budgetguardian.datastructures.Comparator;
import com.budgetguardian.datastructures.DynamicArray;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Correctness of linear and binary search strategies. */
class SearchStrategyTest {

    private static final Comparator<Integer> ASC = Integer::compare;

    private DynamicArray<Integer> of(int... values) {
        DynamicArray<Integer> a = new DynamicArray<>();
        for (int v : values) {
            a.append(v);
        }
        return a;
    }

    @Test
    void linearFindsFirstMatch() {
        SearchStrategy<Integer> search = new LinearSearch<>();
        DynamicArray<Integer> data = of(4, 8, 15, 16, 23, 42);
        assertEquals(2, search.search(data, 15, ASC));
        assertEquals(0, search.search(data, 4, ASC));
        assertEquals(5, search.search(data, 42, ASC));
        assertEquals(SearchStrategy.NOT_FOUND, search.search(data, 99, ASC));
    }

    @Test
    void linearWorksOnUnsortedData() {
        SearchStrategy<Integer> search = new LinearSearch<>();
        DynamicArray<Integer> data = of(42, 4, 23, 8);
        assertEquals(2, search.search(data, 23, ASC));
        assertEquals(SearchStrategy.NOT_FOUND, search.search(data, 7, ASC));
    }

    @Test
    void binaryFindsOnSortedData() {
        SearchStrategy<Integer> search = new BinarySearch<>();
        DynamicArray<Integer> data = of(4, 8, 15, 16, 23, 42);
        for (int i = 0; i < data.size(); i++) {
            assertEquals(i, search.search(data, data.get(i), ASC));
        }
        assertEquals(SearchStrategy.NOT_FOUND, search.search(data, 99, ASC));
        assertEquals(SearchStrategy.NOT_FOUND, search.search(data, 3, ASC));
        assertEquals(SearchStrategy.NOT_FOUND, search.search(data, 20, ASC));
    }

    @Test
    void binaryOnEmptyAndSingle() {
        SearchStrategy<Integer> search = new BinarySearch<>();
        assertEquals(SearchStrategy.NOT_FOUND, search.search(of(), 1, ASC));
        assertEquals(0, search.search(of(7), 7, ASC));
        assertEquals(SearchStrategy.NOT_FOUND, search.search(of(7), 8, ASC));
    }

    @Test
    void binaryMatchesLinearOnLargeSortedData() {
        DynamicArray<Integer> data = new DynamicArray<>();
        for (int i = 0; i < 1_000; i++) {
            data.append(i * 2);                 // even numbers 0..1998
        }
        SearchStrategy<Integer> binary = new BinarySearch<>();
        SearchStrategy<Integer> linear = new LinearSearch<>();
        for (int probe : new int[] {0, 500, 1998, 999, 1001}) {
            int b = binary.search(data, probe, ASC);
            int l = linear.search(data, probe, ASC);
            assertEquals(l, b, "mismatch for probe " + probe);
        }
        assertTrue(binary.search(data, 3, ASC) == SearchStrategy.NOT_FOUND);   // odd absent
    }

    @Test
    void rejectsNulls() {
        SearchStrategy<Integer> search = new BinarySearch<>();
        assertThrows(IllegalArgumentException.class, () -> search.search(null, 1, ASC));
        assertThrows(IllegalArgumentException.class, () -> search.search(of(1), 1, null));
    }
}
