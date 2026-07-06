package com.budgetguardian.datastructures;

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behavior tests for {@link Queue} — FIFO discipline for notifications. */
class QueueTest {

    @Test
    void startsEmpty() {
        Queue<String> queue = new Queue<>();
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());
    }

    @Test
    void fifoOrder() {
        Queue<Integer> queue = new Queue<>();
        queue.enqueue(1);
        queue.enqueue(2);
        queue.enqueue(3);
        assertEquals(1, queue.dequeue());
        assertEquals(2, queue.dequeue());
        assertEquals(3, queue.dequeue());
        assertTrue(queue.isEmpty());
    }

    @Test
    void peekDoesNotRemove() {
        Queue<String> queue = new Queue<>();
        queue.enqueue("front");
        queue.enqueue("back");
        assertEquals("front", queue.peek());
        assertEquals(2, queue.size());
    }

    @Test
    void drainingResetsTailCorrectly() {
        Queue<Integer> queue = new Queue<>();
        queue.enqueue(1);
        queue.dequeue();
        queue.enqueue(2);            // must not chain onto stale tail
        assertEquals(2, queue.peek());
        assertEquals(1, queue.size());
    }

    @Test
    void emptyAccessThrows() {
        Queue<String> queue = new Queue<>();
        assertThrows(NoSuchElementException.class, queue::dequeue);
        assertThrows(NoSuchElementException.class, queue::peek);
    }

    @Test
    void rejectsNull() {
        Queue<String> queue = new Queue<>();
        assertThrows(IllegalArgumentException.class, () -> queue.enqueue(null));
    }

    @Test
    void clearEmpties() {
        Queue<Integer> queue = new Queue<>();
        queue.enqueue(1);
        queue.clear();
        assertTrue(queue.isEmpty());
        queue.enqueue(9);            // still usable after clear
        assertEquals(9, queue.dequeue());
    }

    @Test
    void iteratorWalksFrontToBack() {
        Queue<Integer> queue = new Queue<>();
        queue.enqueue(1);
        queue.enqueue(2);
        queue.enqueue(3);
        Iterator<Integer> it = queue.iterator();
        assertEquals(1, it.next());
        assertEquals(2, it.next());
        assertEquals(3, it.next());
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::next);
        assertEquals(3, queue.size());   // iteration does not consume
    }

    @Test
    void survivesManyOperations() {
        Queue<Integer> queue = new Queue<>();
        for (int i = 0; i < 10_000; i++) {
            queue.enqueue(i);
        }
        for (int i = 0; i < 10_000; i++) {
            assertEquals(i, queue.dequeue());
        }
        assertTrue(queue.isEmpty());
    }
}
