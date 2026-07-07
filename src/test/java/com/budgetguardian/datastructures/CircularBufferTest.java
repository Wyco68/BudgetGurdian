package com.budgetguardian.datastructures;

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behavior tests for {@link CircularBuffer} — recent-transactions ring. */
class CircularBufferTest {

    @Test
    void startsEmpty() {
        CircularBuffer<String> buffer = new CircularBuffer<>(3);
        assertTrue(buffer.isEmpty());
        assertFalse(buffer.isFull());
        assertEquals(0, buffer.size());
        assertEquals(3, buffer.capacity());
    }

    @Test
    void fillsUpToCapacity() {
        CircularBuffer<Integer> buffer = new CircularBuffer<>(3);
        buffer.add(1);
        buffer.add(2);
        buffer.add(3);
        assertTrue(buffer.isFull());
        assertEquals(3, buffer.size());
        assertEquals(1, buffer.get(0));   // oldest
        assertEquals(3, buffer.get(2));   // newest
    }

    @Test
    void overwritesOldestWhenFull() {
        CircularBuffer<Integer> buffer = new CircularBuffer<>(3);
        for (int i = 1; i <= 5; i++) {
            buffer.add(i);                 // 4 evicts 1, 5 evicts 2
        }
        assertEquals(3, buffer.size());
        assertEquals(3, buffer.get(0));
        assertEquals(4, buffer.get(1));
        assertEquals(5, buffer.get(2));
    }

    @Test
    void wrapsManyTimesWithoutCorruption() {
        CircularBuffer<Integer> buffer = new CircularBuffer<>(20);
        for (int i = 0; i < 1_000; i++) {
            buffer.add(i);
        }
        assertEquals(20, buffer.size());
        for (int i = 0; i < 20; i++) {
            assertEquals(980 + i, buffer.get(i));   // last 20 survive, in order
        }
    }

    @Test
    void iteratorOldestToNewest() {
        CircularBuffer<Integer> buffer = new CircularBuffer<>(3);
        for (int i = 1; i <= 4; i++) {
            buffer.add(i);
        }
        Iterator<Integer> it = buffer.iterator();
        assertEquals(2, it.next());
        assertEquals(3, it.next());
        assertEquals(4, it.next());
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::next);
    }

    @Test
    void newestFirstIteratorForDashboard() {
        CircularBuffer<Integer> buffer = new CircularBuffer<>(3);
        for (int i = 1; i <= 4; i++) {
            buffer.add(i);
        }
        Iterator<Integer> it = buffer.newestFirst();
        assertEquals(4, it.next());
        assertEquals(3, it.next());
        assertEquals(2, it.next());
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::next);
    }

    @Test
    void boundsChecked() {
        CircularBuffer<Integer> buffer = new CircularBuffer<>(3);
        buffer.add(1);
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(1));
    }

    @Test
    void rejectsNullAndBadCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new CircularBuffer<String>(0));
        CircularBuffer<String> buffer = new CircularBuffer<>(1);
        assertThrows(IllegalArgumentException.class, () -> buffer.add(null));
    }

    @Test
    void capacityOneAlwaysKeepsNewest() {
        CircularBuffer<String> buffer = new CircularBuffer<>(1);
        buffer.add("a");
        buffer.add("b");
        assertEquals("b", buffer.get(0));
        assertEquals(1, buffer.size());
    }

    @Test
    void clearResets() {
        CircularBuffer<Integer> buffer = new CircularBuffer<>(3);
        for (int i = 0; i < 5; i++) {
            buffer.add(i);
        }
        buffer.clear();
        assertTrue(buffer.isEmpty());
        buffer.add(9);                     // usable after clear, index restarts
        assertEquals(9, buffer.get(0));
    }
}
