package com.budgetguardian.datastructures;

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behavior tests for {@link PriorityQueue} — binary max-heap for hero banner. */
class PriorityQueueTest {

    private static final Comparator<Integer> NATURAL = Integer::compare;

    /** App-style notification: priority plus insertion sequence for tie-break. */
    private record Alert(int priority, int sequence) {
    }

    @Test
    void startsEmpty() {
        PriorityQueue<Integer> pq = new PriorityQueue<>(NATURAL);
        assertTrue(pq.isEmpty());
        assertEquals(0, pq.size());
    }

    @Test
    void peekReturnsMaximum() {
        PriorityQueue<Integer> pq = new PriorityQueue<>(NATURAL);
        pq.insert(20);
        pq.insert(100);
        pq.insert(60);
        assertEquals(100, pq.peek());
        assertEquals(3, pq.size());   // peek does not remove
    }

    @Test
    void pollDrainsInDescendingOrder() {
        PriorityQueue<Integer> pq = new PriorityQueue<>(NATURAL);
        int[] priorities = {80, 20, 100, 60, 90};
        for (int p : priorities) {
            pq.insert(p);
        }
        assertEquals(100, pq.poll());
        assertEquals(90, pq.poll());
        assertEquals(80, pq.poll());
        assertEquals(60, pq.poll());
        assertEquals(20, pq.poll());
        assertTrue(pq.isEmpty());
    }

    @Test
    void heapPropertyHoldsUnderRandomOperations() {
        PriorityQueue<Integer> pq = new PriorityQueue<>(NATURAL);
        Random random = new Random(1234);
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < 1_000; i++) {
            int value = random.nextInt(1_000_000);
            max = Math.max(max, value);
            pq.insert(value);
        }
        int previous = Integer.MAX_VALUE;
        assertEquals(max, pq.peek());
        while (!pq.isEmpty()) {
            int current = pq.poll();
            assertTrue(current <= previous, "heap order violated");
            previous = current;
        }
    }

    @Test
    void duplicatePrioritiesAllSurface() {
        PriorityQueue<Integer> pq = new PriorityQueue<>(NATURAL);
        pq.insert(50);
        pq.insert(50);
        pq.insert(50);
        assertEquals(50, pq.poll());
        assertEquals(50, pq.poll());
        assertEquals(50, pq.poll());
        assertTrue(pq.isEmpty());
    }

    @Test
    void composedComparatorBreaksTiesByAge() {
        // Hero-banner rule: higher priority wins; equal priority → older first.
        Comparator<Alert> heroOrder = (a, b) -> {
            int byPriority = Integer.compare(a.priority(), b.priority());
            if (byPriority != 0) {
                return byPriority;
            }
            return Integer.compare(b.sequence(), a.sequence());   // lower seq outranks
        };
        PriorityQueue<Alert> pq = new PriorityQueue<>(heroOrder);
        pq.insert(new Alert(80, 1));
        pq.insert(new Alert(80, 2));
        pq.insert(new Alert(100, 3));
        assertEquals(new Alert(100, 3), pq.poll());
        assertEquals(new Alert(80, 1), pq.poll());   // older of the two 80s
        assertEquals(new Alert(80, 2), pq.poll());
    }

    @Test
    void removeArbitraryElementKeepsHeapValid() {
        PriorityQueue<Integer> pq = new PriorityQueue<>(NATURAL);
        for (int p : new int[] {100, 90, 80, 60, 20}) {
            pq.insert(p);
        }
        assertTrue(pq.remove(90));
        assertFalse(pq.remove(999));
        assertEquals(100, pq.poll());
        assertEquals(80, pq.poll());
        assertEquals(60, pq.poll());
        assertEquals(20, pq.poll());
    }

    @Test
    void removeRootBehavesLikePoll() {
        PriorityQueue<Integer> pq = new PriorityQueue<>(NATURAL);
        pq.insert(10);
        pq.insert(30);
        pq.insert(20);
        assertTrue(pq.remove(30));
        assertEquals(20, pq.peek());
    }

    @Test
    void emptyAccessThrows() {
        PriorityQueue<Integer> pq = new PriorityQueue<>(NATURAL);
        assertThrows(NoSuchElementException.class, pq::peek);
        assertThrows(NoSuchElementException.class, pq::poll);
    }

    @Test
    void rejectsNullComparatorAndElement() {
        assertThrows(IllegalArgumentException.class, () -> new PriorityQueue<Integer>(null));
        PriorityQueue<Integer> pq = new PriorityQueue<>(NATURAL);
        assertThrows(IllegalArgumentException.class, () -> pq.insert(null));
    }

    @Test
    void clearEmpties() {
        PriorityQueue<Integer> pq = new PriorityQueue<>(NATURAL);
        pq.insert(1);
        pq.clear();
        assertTrue(pq.isEmpty());
        pq.insert(5);                 // usable after clear
        assertEquals(5, pq.peek());
    }
}
