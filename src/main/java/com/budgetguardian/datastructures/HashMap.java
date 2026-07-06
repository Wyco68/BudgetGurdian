package com.budgetguardian.datastructures;

import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;

/**
 * Hash map with separate chaining — custom replacement for
 * {@code java.util.HashMap}.
 *
 * <p><b>Purpose:</b> average O(1) key-value lookup. Backs every fast-search
 * feature: accounts by id, category totals by (category, month) key, refill
 * items by name, debts by id, and application settings.</p>
 *
 * <p><b>Collision handling:</b> separate chaining — each bucket holds a singly
 * linked chain of entries. Chosen over open addressing because deletion is
 * trivial (no tombstones) and performance degrades gracefully under high load.</p>
 *
 * <p><b>Hashing:</b> the key's {@code hashCode()} is spread with
 * {@code h ^ (h >>> 16)} before masking, mixing high bits into the low bits
 * that pick the bucket — plain masking would ignore high bits entirely and
 * cluster keys whose hashes differ only there.</p>
 *
 * <p><b>Resizing:</b> starts at {@value #DEFAULT_CAPACITY} buckets, doubles
 * when size exceeds capacity × {@value #LOAD_FACTOR} (rehash O(n), keeping
 * expected chain length below 1).</p>
 *
 * <p><b>Advantages:</b> average O(1) put/get/remove. <b>Trade-offs:</b> worst
 * case O(n) when all keys collide; no ordering guarantees; O(n) rehash pauses.</p>
 *
 * <p><b>Time complexity:</b> put/get/remove/containsKey average O(1), worst
 * O(n); iteration O(capacity + size).
 * <b>Space complexity:</b> O(n + capacity).</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * HashMap<String, Account> accounts = new HashMap<>();
 * accounts.put("SCB", scb);
 * Account found = accounts.get("SCB");   // average O(1)
 * }</pre>
 *
 * <p>Null keys and null values are rejected; {@code get} returning null means
 * "absent". Iterators are fail-fast.</p>
 *
 * @param <K> key type — must implement {@code hashCode()}/{@code equals()} correctly
 * @param <V> value type
 */
public class HashMap<K, V> implements Iterable<HashMap.Entry<K, V>> {

    /**
     * One key-value pair. Exposed read-only through the entry iterator.
     *
     * @param <K> key type
     * @param <V> value type
     */
    public static final class Entry<K, V> {
        final K key;
        V value;
        Entry<K, V> next;

        Entry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        /** @return the key */
        public K key() {
            return key;
        }

        /** @return the value current at iteration time */
        public V value() {
            return value;
        }
    }

    /** Initial bucket count; always a power of two so masking works. */
    static final int DEFAULT_CAPACITY = 16;

    /** Resize threshold: grow when {@code size > capacity * LOAD_FACTOR}. */
    static final double LOAD_FACTOR = 0.75;

    private Entry<K, V>[] buckets;
    private int size;
    private int modCount;

    /** Creates an empty map with {@value #DEFAULT_CAPACITY} buckets. */
    @SuppressWarnings("unchecked")
    public HashMap() {
        buckets = (Entry<K, V>[]) new Entry[DEFAULT_CAPACITY];
    }

    /**
     * Associates {@code value} with {@code key}. Average O(1).
     *
     * @return the previous value for the key, or null if the key was absent
     * @throws IllegalArgumentException if key or value is null
     */
    public V put(K key, V value) {
        requireNonNull(key, "key");
        requireNonNull(value, "value");
        int index = bucketIndex(key, buckets.length);
        for (Entry<K, V> e = buckets[index]; e != null; e = e.next) {
            if (e.key.equals(key)) {
                V previous = e.value;
                e.value = value;
                return previous;
            }
        }
        Entry<K, V> entry = new Entry<>(key, value);
        entry.next = buckets[index];
        buckets[index] = entry;
        size++;
        modCount++;
        if (size > buckets.length * LOAD_FACTOR) {
            rehash();
        }
        return null;
    }

    /**
     * @return value for {@code key}, or null if absent. Average O(1).
     * @throws IllegalArgumentException if key is null
     */
    public V get(K key) {
        requireNonNull(key, "key");
        for (Entry<K, V> e = buckets[bucketIndex(key, buckets.length)]; e != null; e = e.next) {
            if (e.key.equals(key)) {
                return e.value;
            }
        }
        return null;
    }

    /**
     * @return value for {@code key}, or {@code fallback} if absent. Average O(1).
     */
    public V getOrDefault(K key, V fallback) {
        V value = get(key);
        return value != null ? value : fallback;
    }

    /**
     * Removes the mapping for {@code key}. Average O(1).
     *
     * @return the removed value, or null if the key was absent
     * @throws IllegalArgumentException if key is null
     */
    public V remove(K key) {
        requireNonNull(key, "key");
        int index = bucketIndex(key, buckets.length);
        Entry<K, V> previous = null;
        for (Entry<K, V> e = buckets[index]; e != null; e = e.next) {
            if (e.key.equals(key)) {
                if (previous == null) {
                    buckets[index] = e.next;
                } else {
                    previous.next = e.next;
                }
                size--;
                modCount++;
                return e.value;
            }
            previous = e;
        }
        return null;
    }

    /** @return whether a mapping exists for {@code key}. Average O(1). */
    public boolean containsKey(K key) {
        return get(key) != null;
    }

    /** @return mapping count. O(1). */
    public int size() {
        return size;
    }

    /** @return {@code true} if no mappings. O(1). */
    public boolean isEmpty() {
        return size == 0;
    }

    /** Removes all mappings and resets to the default bucket count. */
    @SuppressWarnings("unchecked")
    public void clear() {
        buckets = (Entry<K, V>[]) new Entry[DEFAULT_CAPACITY];
        size = 0;
        modCount++;
    }

    /** @return current bucket count (exposed for tests and diagnostics). */
    int capacity() {
        return buckets.length;
    }

    /** @return fail-fast iterator over all entries, bucket order (unordered contract). */
    @Override
    public Iterator<Entry<K, V>> iterator() {
        final int expectedModCount = modCount;
        return new Iterator<>() {
            private int bucket = -1;
            private Entry<K, V> cursor = firstEntry();

            private Entry<K, V> firstEntry() {
                while (++bucket < buckets.length) {
                    if (buckets[bucket] != null) {
                        return buckets[bucket];
                    }
                }
                return null;
            }

            @Override
            public boolean hasNext() {
                return cursor != null;
            }

            @Override
            public Entry<K, V> next() {
                if (modCount != expectedModCount) {
                    throw new ConcurrentModificationException();
                }
                if (cursor == null) {
                    throw new NoSuchElementException();
                }
                Entry<K, V> current = cursor;
                cursor = cursor.next != null ? cursor.next : firstEntry();
                return current;
            }
        };
    }

    /** @return iterator over keys (order matches {@link #iterator()}). */
    public Iterator<K> keys() {
        Iterator<Entry<K, V>> entries = iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return entries.hasNext();
            }

            @Override
            public K next() {
                return entries.next().key();
            }
        };
    }

    /** @return iterator over values (order matches {@link #iterator()}). */
    public Iterator<V> values() {
        Iterator<Entry<K, V>> entries = iterator();
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return entries.hasNext();
            }

            @Override
            public V next() {
                return entries.next().value();
            }
        };
    }

    /** Doubles the bucket array and redistributes every entry. O(n). */
    @SuppressWarnings("unchecked")
    private void rehash() {
        Entry<K, V>[] old = buckets;
        Entry<K, V>[] next = (Entry<K, V>[]) new Entry[old.length * 2];
        for (Entry<K, V> chain : old) {
            while (chain != null) {
                Entry<K, V> node = chain;
                chain = chain.next;
                int index = bucketIndex(node.key, next.length);
                node.next = next[index];
                next[index] = node;
            }
        }
        buckets = next;
    }

    /** Spreads high bits into low bits, then masks to the bucket range. */
    private static int bucketIndex(Object key, int capacity) {
        int h = key.hashCode();
        return (h ^ (h >>> 16)) & (capacity - 1);
    }

    private static void requireNonNull(Object value, String what) {
        if (value == null) {
            throw new IllegalArgumentException("Null " + what + " is not allowed");
        }
    }
}
