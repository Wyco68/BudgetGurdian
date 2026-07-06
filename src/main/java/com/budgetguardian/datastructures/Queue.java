package com.budgetguardian.datastructures;

import java.util.NoSuchElementException;

/**
 * FIFO queue backed by singly linked nodes — custom replacement for
 * {@code java.util.Queue} implementations.
 *
 * <p><b>Purpose:</b> ordered delivery of notifications, reminders and
 * scheduled events — first raised, first shown. Also drives the BFS frontier
 * in {@code Graph}.</p>
 *
 * <p><b>Design:</b> head and tail references give strict O(1) enqueue (at
 * tail) and dequeue (at head) with no resizing and no wasted capacity.</p>
 *
 * <p><b>Advantages:</b> strict O(1) both ends, unbounded.
 * <b>Trade-offs:</b> node allocation per enqueue, no random access.</p>
 *
 * <p><b>Time complexity:</b> enqueue/dequeue/peek/size/isEmpty O(1);
 * iteration O(n). <b>Space complexity:</b> O(n).</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * Queue<Reminder> reminders = new Queue<>();
 * reminders.enqueue(reminder);
 * Reminder next = reminders.dequeue();   // FIFO
 * }</pre>
 *
 * @param <T> element type
 */
public class Queue<T> implements Iterable<T> {

    private static final class Node<T> {
        final T value;
        Node<T> next;

        Node(T value) {
            this.value = value;
        }
    }

    private Node<T> head;
    private Node<T> tail;
    private int size;

    /**
     * Appends at the back. O(1).
     *
     * @throws IllegalArgumentException if {@code element} is null
     */
    public void enqueue(T element) {
        if (element == null) {
            throw new IllegalArgumentException("Null elements are not allowed");
        }
        Node<T> node = new Node<>(element);
        if (tail == null) {
            head = tail = node;
        } else {
            tail.next = node;
            tail = node;
        }
        size++;
    }

    /**
     * Removes and returns the front element. O(1).
     *
     * @throws NoSuchElementException if empty
     */
    public T dequeue() {
        if (head == null) {
            throw new NoSuchElementException("Queue is empty");
        }
        T value = head.value;
        head = head.next;
        if (head == null) {
            tail = null;
        }
        size--;
        return value;
    }

    /**
     * @return the front element without removing it. O(1).
     * @throws NoSuchElementException if empty
     */
    public T peek() {
        if (head == null) {
            throw new NoSuchElementException("Queue is empty");
        }
        return head.value;
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
        head = tail = null;
        size = 0;
    }

    /** @return iterator from front to back (delivery order). */
    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {
            private Node<T> cursor = head;

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
                cursor = cursor.next;
                return value;
            }
        };
    }
}
