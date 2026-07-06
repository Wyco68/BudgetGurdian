package com.budgetguardian.datastructures;

import java.util.NoSuchElementException;

/**
 * LIFO stack backed by singly linked nodes — custom replacement for
 * {@code java.util.Stack} / {@code ArrayDeque}.
 *
 * <p><b>Purpose:</b> the undo mechanism. Every data modification pushes an
 * inverse {@code Action}; Ctrl+Z pops and applies it. Also drives the
 * iterative DFS in {@code Graph}.</p>
 *
 * <p><b>Why linked nodes instead of an array:</b> push/pop are strict O(1)
 * with no resize pauses, and the stack shrinks its memory automatically as
 * actions are undone — undo depth is unbounded and unpredictable.</p>
 *
 * <p><b>Advantages:</b> strict O(1) push/pop/peek, unbounded.
 * <b>Trade-offs:</b> one node allocation per push, worse cache locality than
 * an array-backed stack.</p>
 *
 * <p><b>Time complexity:</b> push/pop/peek/size/isEmpty O(1); iteration O(n).
 * <b>Space complexity:</b> O(n), one reference overhead per element.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * Stack<Action> undo = new Stack<>();
 * undo.push(action);
 * if (!undo.isEmpty()) undo.pop().revert();
 * }</pre>
 *
 * @param <T> element type
 */
public class Stack<T> implements Iterable<T> {

    private static final class Node<T> {
        final T value;
        final Node<T> below;

        Node(T value, Node<T> below) {
            this.value = value;
            this.below = below;
        }
    }

    private Node<T> top;
    private int size;

    /**
     * Pushes onto the top. O(1).
     *
     * @throws IllegalArgumentException if {@code element} is null
     */
    public void push(T element) {
        if (element == null) {
            throw new IllegalArgumentException("Null elements are not allowed");
        }
        top = new Node<>(element, top);
        size++;
    }

    /**
     * Removes and returns the top element. O(1).
     *
     * @throws NoSuchElementException if empty
     */
    public T pop() {
        if (top == null) {
            throw new NoSuchElementException("Stack is empty");
        }
        T value = top.value;
        top = top.below;
        size--;
        return value;
    }

    /**
     * @return the top element without removing it. O(1).
     * @throws NoSuchElementException if empty
     */
    public T peek() {
        if (top == null) {
            throw new NoSuchElementException("Stack is empty");
        }
        return top.value;
    }

    /** @return element count. O(1). */
    public int size() {
        return size;
    }

    /** @return {@code true} if no elements. O(1). */
    public boolean isEmpty() {
        return size == 0;
    }

    /** Removes all elements. O(1) — drops the chain for GC. */
    public void clear() {
        top = null;
        size = 0;
    }

    /** @return iterator from top to bottom (undo-history display order). */
    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private Node<T> cursor = top;

            @Override
            public boolean hasNext() {
                return cursor != null;
            }

            @Override
            public T next() {
                if (cursor == null) {
                    throw new NoSuchElementException();
                }
                T value = cursor.value;
                cursor = cursor.below;
                return value;
            }
        };
    }
}
