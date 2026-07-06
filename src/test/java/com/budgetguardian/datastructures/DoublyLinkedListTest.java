package com.budgetguardian.datastructures;

import org.junit.jupiter.api.Test;

import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exhaustive behavior tests for {@link DoublyLinkedList}. */
class DoublyLinkedListTest {

    @Test
    void startsEmpty() {
        DoublyLinkedList<String> list = new DoublyLinkedList<>();
        assertTrue(list.isEmpty());
        assertEquals(0, list.size());
    }

    @Test
    void addFirstAndLast() {
        DoublyLinkedList<String> list = new DoublyLinkedList<>();
        list.addLast("b");
        list.addFirst("a");
        list.addLast("c");
        assertEquals(3, list.size());
        assertEquals("a", list.getFirst());
        assertEquals("c", list.getLast());
        assertEquals("b", list.get(1));
    }

    @Test
    void removeFirstAndLast() {
        DoublyLinkedList<String> list = new DoublyLinkedList<>();
        list.addLast("a");
        list.addLast("b");
        list.addLast("c");
        assertEquals("a", list.removeFirst());
        assertEquals("c", list.removeLast());
        assertEquals(1, list.size());
        assertEquals("b", list.getFirst());
        assertEquals("b", list.getLast());
    }

    @Test
    void removeOnlyElementResetsBothEnds() {
        DoublyLinkedList<String> list = new DoublyLinkedList<>();
        list.addLast("solo");
        list.removeFirst();
        assertTrue(list.isEmpty());
        list.addLast("again");            // list still usable
        assertEquals("again", list.getLast());
    }

    @Test
    void removeByValue() {
        DoublyLinkedList<String> list = new DoublyLinkedList<>();
        list.addLast("a");
        list.addLast("b");
        list.addLast("a");
        assertTrue(list.remove("a"));      // removes first occurrence only
        assertEquals(2, list.size());
        assertEquals("b", list.getFirst());
        assertEquals("a", list.getLast());
        assertFalse(list.remove("zzz"));
    }

    @Test
    void insertAtWalksFromNearerEnd() {
        DoublyLinkedList<Integer> list = new DoublyLinkedList<>();
        for (int i = 0; i < 6; i++) {
            list.addLast(i);
        }
        list.insertAt(0, 100);             // front → [100,0,1,2,3,4,5]
        list.insertAt(7, 200);             // append → [100,0,1,2,3,4,5,200]
        list.insertAt(4, 300);             // middle → [100,0,1,2,300,3,4,5,200]
        assertEquals(100, list.get(0));
        assertEquals(300, list.get(4));
        assertEquals(5, list.get(7));      // reverse walk from tail
        assertEquals(200, list.get(8));
        assertEquals(9, list.size());
    }

    @Test
    void emptyAccessThrows() {
        DoublyLinkedList<String> list = new DoublyLinkedList<>();
        assertThrows(NoSuchElementException.class, list::getFirst);
        assertThrows(NoSuchElementException.class, list::getLast);
        assertThrows(NoSuchElementException.class, list::removeFirst);
        assertThrows(NoSuchElementException.class, list::removeLast);
        assertThrows(IndexOutOfBoundsException.class, () -> list.get(0));
    }

    @Test
    void rejectsNull() {
        DoublyLinkedList<String> list = new DoublyLinkedList<>();
        assertThrows(IllegalArgumentException.class, () -> list.addFirst(null));
        assertThrows(IllegalArgumentException.class, () -> list.addLast(null));
        assertThrows(IllegalArgumentException.class, () -> list.insertAt(0, null));
    }

    @Test
    void forwardIterationIsChronological() {
        DoublyLinkedList<Integer> list = new DoublyLinkedList<>();
        for (int i = 0; i < 5; i++) {
            list.addLast(i);
        }
        Iterator<Integer> it = list.iterator();
        for (int i = 0; i < 5; i++) {
            assertEquals(i, it.next());
        }
        assertFalse(it.hasNext());
    }

    @Test
    void reverseIterationIsRecentFirst() {
        DoublyLinkedList<Integer> list = new DoublyLinkedList<>();
        for (int i = 0; i < 5; i++) {
            list.addLast(i);
        }
        Iterator<Integer> it = list.descendingIterator();
        for (int i = 4; i >= 0; i--) {
            assertEquals(i, it.next());
        }
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::next);
    }

    @Test
    void iteratorsAreFailFast() {
        DoublyLinkedList<Integer> list = new DoublyLinkedList<>();
        list.addLast(1);
        Iterator<Integer> forward = list.iterator();
        Iterator<Integer> backward = list.descendingIterator();
        list.addLast(2);
        assertThrows(ConcurrentModificationException.class, forward::next);
        assertThrows(ConcurrentModificationException.class, backward::next);
    }

    @Test
    void clearEmptiesList() {
        DoublyLinkedList<Integer> list = new DoublyLinkedList<>();
        list.addLast(1);
        list.addLast(2);
        list.clear();
        assertTrue(list.isEmpty());
        assertFalse(list.contains(1));
    }

    @Test
    void containsFindsValues() {
        DoublyLinkedList<String> list = new DoublyLinkedList<>();
        list.addLast("x");
        assertTrue(list.contains("x"));
        assertFalse(list.contains("y"));
    }
}
