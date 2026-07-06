package com.budgetguardian.datastructures;

import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;

/**
 * Doubly linked list — custom replacement for {@code java.util.LinkedList}.
 *
 * <p><b>Purpose:</b> ordered storage with O(1) insertion/removal at both ends
 * and bidirectional traversal. Primary store for the transaction ledger and
 * for debt/transfer histories, and the chain type inside {@code Graph}
 * adjacency lists.</p>
 *
 * <p><b>Why it exists / where used:</b> the ledger is chronological —
 * new entries land at the tail in O(1) — while the UI mostly renders
 * "recent first", which the reverse iterator serves in O(n) without any
 * copying or sorting.</p>
 *
 * <p><b>Advantages:</b> O(1) end operations, no resize copying, cheap reverse
 * traversal. <b>Trade-offs:</b> O(n) random access, extra memory for two
 * pointers per node, poor cache locality versus arrays.</p>
 *
 * <p><b>Time complexity:</b> addFirst/addLast/removeFirst/removeLast/
 * getFirst/getLast O(1); get/insertAt/remove(value)/contains O(n).
 * <b>Space complexity:</b> O(n), 2 references overhead per element.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * DoublyLinkedList<Transaction> ledger = new DoublyLinkedList<>();
 * ledger.addLast(txn);                          // chronological append
 * Iterator<Transaction> recent = ledger.descendingIterator(); // newest first
 * }</pre>
 *
 * <p>Iterators are fail-fast. Null elements are rejected.</p>
 *
 * @param <T> element type
 */
public class DoublyLinkedList<T> implements Iterable<T> {

    private static final class Node<T> {
        T value;
        Node<T> prev;
        Node<T> next;

        Node(T value) {
            this.value = value;
        }
    }

    private Node<T> head;
    private Node<T> tail;
    private int size;
    private int modCount;

    /**
     * Inserts at the front. O(1).
     *
     * @throws IllegalArgumentException if {@code element} is null
     */
    public void addFirst(T element) {
        requireNonNull(element);
        Node<T> node = new Node<>(element);
        if (head == null) {
            head = tail = node;
        } else {
            node.next = head;
            head.prev = node;
            head = node;
        }
        size++;
        modCount++;
    }

    /**
     * Appends at the back. O(1).
     *
     * @throws IllegalArgumentException if {@code element} is null
     */
    public void addLast(T element) {
        requireNonNull(element);
        Node<T> node = new Node<>(element);
        if (tail == null) {
            head = tail = node;
        } else {
            node.prev = tail;
            tail.next = node;
            tail = node;
        }
        size++;
        modCount++;
    }

    /**
     * Inserts at {@code index}. O(n) walk, O(1) relink.
     *
     * @param index position in {@code [0, size]}; {@code size} appends
     * @throws IndexOutOfBoundsException if index outside {@code [0, size]}
     * @throws IllegalArgumentException  if {@code element} is null
     */
    public void insertAt(int index, T element) {
        requireNonNull(element);
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("Index " + index + ", size " + size);
        }
        if (index == 0) {
            addFirst(element);
        } else if (index == size) {
            addLast(element);
        } else {
            Node<T> successor = nodeAt(index);
            Node<T> node = new Node<>(element);
            node.prev = successor.prev;
            node.next = successor;
            successor.prev.next = node;
            successor.prev = node;
            size++;
            modCount++;
        }
    }

    /**
     * Removes and returns the first element. O(1).
     *
     * @throws NoSuchElementException if empty
     */
    public T removeFirst() {
        if (head == null) {
            throw new NoSuchElementException("List is empty");
        }
        return unlink(head);
    }

    /**
     * Removes and returns the last element. O(1).
     *
     * @throws NoSuchElementException if empty
     */
    public T removeLast() {
        if (tail == null) {
            throw new NoSuchElementException("List is empty");
        }
        return unlink(tail);
    }

    /**
     * Removes the first element equal to {@code value}. O(n).
     *
     * @return {@code true} if an element was removed
     */
    public boolean remove(T value) {
        for (Node<T> n = head; n != null; n = n.next) {
            if (n.value.equals(value)) {
                unlink(n);
                return true;
            }
        }
        return false;
    }

    /**
     * @return element at {@code index}. O(n) — walks from the nearer end.
     * @throws IndexOutOfBoundsException if index outside {@code [0, size)}
     */
    public T get(int index) {
        return nodeAt(index).value;
    }

    /**
     * @return first element. O(1).
     * @throws NoSuchElementException if empty
     */
    public T getFirst() {
        if (head == null) {
            throw new NoSuchElementException("List is empty");
        }
        return head.value;
    }

    /**
     * @return last element. O(1).
     * @throws NoSuchElementException if empty
     */
    public T getLast() {
        if (tail == null) {
            throw new NoSuchElementException("List is empty");
        }
        return tail.value;
    }

    /** @return whether an equal element exists. O(n). */
    public boolean contains(T value) {
        for (Node<T> n = head; n != null; n = n.next) {
            if (n.value.equals(value)) {
                return true;
            }
        }
        return false;
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
        modCount++;
    }

    /** @return fail-fast head-to-tail iterator (chronological order). */
    @Override
    public Iterator<T> iterator() {
        final int expectedModCount = modCount;
        return new Iterator<>() {
            private Node<T> cursor = head;

            @Override
            public boolean hasNext() {
                return cursor != null;
            }

            @Override
            public T next() {
                if (modCount != expectedModCount) {
                    throw new ConcurrentModificationException();
                }
                if (cursor == null) {
                    throw new NoSuchElementException();
                }
                T value = cursor.value;
                cursor = cursor.next;
                return value;
            }
        };
    }

    /** @return fail-fast tail-to-head iterator (recent-first views). */
    public Iterator<T> descendingIterator() {
        final int expectedModCount = modCount;
        return new Iterator<>() {
            private Node<T> cursor = tail;

            @Override
            public boolean hasNext() {
                return cursor != null;
            }

            @Override
            public T next() {
                if (modCount != expectedModCount) {
                    throw new ConcurrentModificationException();
                }
                if (cursor == null) {
                    throw new NoSuchElementException();
                }
                T value = cursor.value;
                cursor = cursor.prev;
                return value;
            }
        };
    }

    private T unlink(Node<T> node) {
        if (node.prev == null) {
            head = node.next;
        } else {
            node.prev.next = node.next;
        }
        if (node.next == null) {
            tail = node.prev;
        } else {
            node.next.prev = node.prev;
        }
        size--;
        modCount++;
        return node.value;
    }

    private Node<T> nodeAt(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index " + index + ", size " + size);
        }
        Node<T> cursor;
        if (index < size / 2) {
            cursor = head;
            for (int i = 0; i < index; i++) {
                cursor = cursor.next;
            }
        } else {
            cursor = tail;
            for (int i = size - 1; i > index; i--) {
                cursor = cursor.prev;
            }
        }
        return cursor;
    }

    private static void requireNonNull(Object element) {
        if (element == null) {
            throw new IllegalArgumentException("Null elements are not allowed");
        }
    }
}
