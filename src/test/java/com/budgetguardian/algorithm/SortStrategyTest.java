package com.budgetguardian.algorithm;

import com.budgetguardian.algorithm.sorting.HeapSort;
import com.budgetguardian.algorithm.sorting.MergeSort;
import com.budgetguardian.algorithm.sorting.QuickSort;
import com.budgetguardian.algorithm.sorting.SortStrategy;
import com.budgetguardian.datastructures.Comparator;
import com.budgetguardian.datastructures.DynamicArray;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Correctness of the three sort strategies across shapes and sizes. */
class SortStrategyTest {

    private static final Comparator<Integer> ASC = Integer::compare;

    private DynamicArray<Integer> of(int... values) {
        DynamicArray<Integer> a = new DynamicArray<>();
        for (int v : values) {
            a.append(v);
        }
        return a;
    }

    private void assertSorted(SortStrategy<Integer> strategy, int[] input) {
        DynamicArray<Integer> data = of(input);
        int[] expected = input.clone();
        java.util.Arrays.sort(expected);
        strategy.sort(data, ASC);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], data.get(i), strategy.getClass().getSimpleName() + " index " + i);
        }
    }

    private void checkAllShapes(SortStrategy<Integer> strategy) {
        assertSorted(strategy, new int[] {});
        assertSorted(strategy, new int[] {42});
        assertSorted(strategy, new int[] {5, 4, 3, 2, 1});
        assertSorted(strategy, new int[] {1, 2, 3, 4, 5});
        assertSorted(strategy, new int[] {3, 1, 4, 1, 5, 9, 2, 6, 5, 3, 5});
        assertSorted(strategy, new int[] {7, 7, 7, 7});
        Random random = new Random(99);
        int[] big = new int[1_000];
        for (int i = 0; i < big.length; i++) {
            big[i] = random.nextInt(10_000) - 5_000;
        }
        assertSorted(strategy, big);
    }

    @Test
    void mergeSortAllShapes() {
        checkAllShapes(new MergeSort<>());
    }

    @Test
    void quickSortAllShapes() {
        checkAllShapes(new QuickSort<>());
    }

    @Test
    void heapSortAllShapes() {
        checkAllShapes(new HeapSort<>());
    }

    /** Merge sort must be stable: equal keys keep input order. */
    @Test
    void mergeSortIsStable() {
        record Pair(int key, int seq) {
        }
        DynamicArray<Pair> data = new DynamicArray<>();
        data.append(new Pair(1, 0));
        data.append(new Pair(1, 1));
        data.append(new Pair(0, 2));
        data.append(new Pair(1, 3));
        data.append(new Pair(0, 4));
        new MergeSort<Pair>().sort(data, (a, b) -> Integer.compare(a.key(), b.key()));
        // keys: 0,0,1,1,1 with original seq order preserved within each key
        assertEquals(2, data.get(0).seq());
        assertEquals(4, data.get(1).seq());
        assertEquals(0, data.get(2).seq());
        assertEquals(1, data.get(3).seq());
        assertEquals(3, data.get(4).seq());
    }

    @Test
    void descendingComparatorSortsDescending() {
        DynamicArray<Integer> data = of(1, 3, 2);
        new HeapSort<Integer>().sort(data, (a, b) -> Integer.compare(b, a));
        assertEquals(3, data.get(0));
        assertEquals(2, data.get(1));
        assertEquals(1, data.get(2));
    }

    @Test
    void rejectsNulls() {
        SortStrategy<Integer> s = new QuickSort<>();
        assertThrows(IllegalArgumentException.class, () -> s.sort(null, ASC));
        assertThrows(IllegalArgumentException.class, () -> s.sort(of(1), null));
    }
}
