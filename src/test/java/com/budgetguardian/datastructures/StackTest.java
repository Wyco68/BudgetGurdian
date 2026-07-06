package com.budgetguardian.datastructures;

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behavior tests for {@link Stack} — LIFO discipline for the undo mechanism. */
class StackTest {

    @Test
    void startsEmpty() {
        Stack<String> stack = new Stack<>();
        assertTrue(stack.isEmpty());
        assertEquals(0, stack.size());
    }

    @Test
    void lifoOrder() {
        Stack<Integer> stack = new Stack<>();
        stack.push(1);
        stack.push(2);
        stack.push(3);
        assertEquals(3, stack.pop());
        assertEquals(2, stack.pop());
        assertEquals(1, stack.pop());
        assertTrue(stack.isEmpty());
    }

    @Test
    void peekDoesNotRemove() {
        Stack<String> stack = new Stack<>();
        stack.push("top");
        assertEquals("top", stack.peek());
        assertEquals(1, stack.size());
        assertEquals("top", stack.pop());
    }

    @Test
    void emptyAccessThrows() {
        Stack<String> stack = new Stack<>();
        assertThrows(NoSuchElementException.class, stack::pop);
        assertThrows(NoSuchElementException.class, stack::peek);
    }

    @Test
    void rejectsNull() {
        Stack<String> stack = new Stack<>();
        assertThrows(IllegalArgumentException.class, () -> stack.push(null));
    }

    @Test
    void clearEmpties() {
        Stack<Integer> stack = new Stack<>();
        stack.push(1);
        stack.push(2);
        stack.clear();
        assertTrue(stack.isEmpty());
        assertThrows(NoSuchElementException.class, stack::pop);
    }

    @Test
    void iteratorWalksTopToBottom() {
        Stack<Integer> stack = new Stack<>();
        stack.push(1);
        stack.push(2);
        stack.push(3);
        Iterator<Integer> it = stack.iterator();
        assertEquals(3, it.next());
        assertEquals(2, it.next());
        assertEquals(1, it.next());
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::next);
        assertEquals(3, stack.size());   // iteration does not consume
    }

    @Test
    void survivesManyOperations() {
        Stack<Integer> stack = new Stack<>();
        for (int i = 0; i < 10_000; i++) {
            stack.push(i);
        }
        assertEquals(10_000, stack.size());
        for (int i = 9_999; i >= 0; i--) {
            assertEquals(i, stack.pop());
        }
        assertTrue(stack.isEmpty());
    }
}
