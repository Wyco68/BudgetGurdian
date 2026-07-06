package com.budgetguardian.datastructures;

import org.junit.jupiter.api.Test;

import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exhaustive behavior tests for {@link DynamicArray}. */
class DynamicArrayTest {

    @Test
    void startsEmpty() {
        DynamicArray<String> array = new DynamicArray<>();
        assertEquals(0, array.size());
        assertTrue(array.isEmpty());
        assertEquals(DynamicArray.DEFAULT_CAPACITY, array.capacity());
    }

    @Test
    void appendAndGet() {
        DynamicArray<Integer> array = new DynamicArray<>();
        for (int i = 0; i < 5; i++) {
            array.append(i * 10);
        }
        assertEquals(5, array.size());
        assertEquals(0, array.get(0));
        assertEquals(40, array.get(4));
    }

    @Test
    void growsWhenFull() {
        DynamicArray<Integer> array = new DynamicArray<>();
        for (int i = 0; i < DynamicArray.DEFAULT_CAPACITY + 1; i++) {
            array.append(i);
        }
        assertEquals(DynamicArray.DEFAULT_CAPACITY * 2, array.capacity());
        assertEquals(DynamicArray.DEFAULT_CAPACITY + 1, array.size());
        for (int i = 0; i <= DynamicArray.DEFAULT_CAPACITY; i++) {
            assertEquals(i, array.get(i));   // order preserved across grow
        }
    }

    @Test
    void shrinksWhenSparse() {
        DynamicArray<Integer> array = new DynamicArray<>();
        for (int i = 0; i < 64; i++) {
            array.append(i);
        }
        int grown = array.capacity();
        while (array.size() > 8) {
            array.remove(array.size() - 1);
        }
        assertTrue(array.capacity() < grown, "capacity should shrink");
        assertTrue(array.capacity() >= DynamicArray.DEFAULT_CAPACITY);
    }

    @Test
    void neverShrinksBelowDefaultCapacity() {
        DynamicArray<Integer> array = new DynamicArray<>();
        array.append(1);
        array.remove(0);
        assertEquals(DynamicArray.DEFAULT_CAPACITY, array.capacity());
    }

    @Test
    void insertAtFrontMiddleEnd() {
        DynamicArray<String> array = new DynamicArray<>();
        array.append("b");
        array.insert(0, "a");           // front
        array.insert(2, "d");           // end (index == size)
        array.insert(2, "c");           // middle
        assertEquals(4, array.size());
        assertEquals("a", array.get(0));
        assertEquals("b", array.get(1));
        assertEquals("c", array.get(2));
        assertEquals("d", array.get(3));
    }

    @Test
    void removeShiftsLeft() {
        DynamicArray<String> array = new DynamicArray<>();
        array.append("a");
        array.append("b");
        array.append("c");
        assertEquals("b", array.remove(1));
        assertEquals(2, array.size());
        assertEquals("c", array.get(1));
    }

    @Test
    void setReplacesAndReturnsPrevious() {
        DynamicArray<String> array = new DynamicArray<>();
        array.append("old");
        assertEquals("old", array.set(0, "new"));
        assertEquals("new", array.get(0));
    }

    @Test
    void indexOfAndContains() {
        DynamicArray<String> array = new DynamicArray<>();
        array.append("x");
        array.append("y");
        assertEquals(1, array.indexOf("y"));
        assertEquals(-1, array.indexOf("z"));
        assertTrue(array.contains("x"));
        assertFalse(array.contains("z"));
    }

    @Test
    void clearResets() {
        DynamicArray<Integer> array = new DynamicArray<>();
        for (int i = 0; i < 100; i++) {
            array.append(i);
        }
        array.clear();
        assertTrue(array.isEmpty());
        assertEquals(DynamicArray.DEFAULT_CAPACITY, array.capacity());
    }

    @Test
    void boundsChecked() {
        DynamicArray<Integer> array = new DynamicArray<>();
        array.append(1);
        assertThrows(IndexOutOfBoundsException.class, () -> array.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> array.get(1));
        assertThrows(IndexOutOfBoundsException.class, () -> array.remove(1));
        assertThrows(IndexOutOfBoundsException.class, () -> array.insert(3, 9));
        assertThrows(IndexOutOfBoundsException.class, () -> array.set(1, 9));
    }

    @Test
    void rejectsNull() {
        DynamicArray<String> array = new DynamicArray<>();
        assertThrows(IllegalArgumentException.class, () -> array.append(null));
        assertThrows(IllegalArgumentException.class, () -> array.insert(0, null));
        array.append("a");
        assertThrows(IllegalArgumentException.class, () -> array.set(0, null));
    }

    @Test
    void rejectsBadCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new DynamicArray<String>(0));
    }

    @Test
    void iteratorWalksInOrder() {
        DynamicArray<Integer> array = new DynamicArray<>();
        for (int i = 0; i < 10; i++) {
            array.append(i);
        }
        Iterator<Integer> it = array.iterator();
        for (int i = 0; i < 10; i++) {
            assertTrue(it.hasNext());
            assertEquals(i, it.next());
        }
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::next);
    }

    @Test
    void iteratorIsFailFast() {
        DynamicArray<Integer> array = new DynamicArray<>();
        array.append(1);
        array.append(2);
        Iterator<Integer> it = array.iterator();
        array.append(3);
        assertThrows(ConcurrentModificationException.class, it::next);
    }
}
