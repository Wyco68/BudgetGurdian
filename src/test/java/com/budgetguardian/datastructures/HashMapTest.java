package com.budgetguardian.datastructures;

import org.junit.jupiter.api.Test;

import java.util.ConcurrentModificationException;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exhaustive behavior tests for {@link HashMap}, including collision handling. */
class HashMapTest {

    /** Key whose hash is fixed — forces every instance into the same bucket. */
    private record CollidingKey(String id) {
        @Override
        public int hashCode() {
            return 42;
        }
    }

    @Test
    void startsEmpty() {
        HashMap<String, Integer> map = new HashMap<>();
        assertTrue(map.isEmpty());
        assertEquals(0, map.size());
        assertEquals(HashMap.DEFAULT_CAPACITY, map.capacity());
    }

    @Test
    void putGetRoundTrip() {
        HashMap<String, Integer> map = new HashMap<>();
        assertNull(map.put("one", 1));
        assertNull(map.put("two", 2));
        assertEquals(1, map.get("one"));
        assertEquals(2, map.get("two"));
        assertEquals(2, map.size());
    }

    @Test
    void putReplacesAndReturnsPrevious() {
        HashMap<String, Integer> map = new HashMap<>();
        map.put("k", 1);
        assertEquals(1, map.put("k", 2));
        assertEquals(2, map.get("k"));
        assertEquals(1, map.size());
    }

    @Test
    void getAbsentReturnsNull() {
        HashMap<String, Integer> map = new HashMap<>();
        assertNull(map.get("missing"));
        assertEquals(7, map.getOrDefault("missing", 7));
    }

    @Test
    void removeDeletesMapping() {
        HashMap<String, Integer> map = new HashMap<>();
        map.put("k", 1);
        assertEquals(1, map.remove("k"));
        assertNull(map.get("k"));
        assertNull(map.remove("k"));
        assertEquals(0, map.size());
    }

    @Test
    void collisionsChainAndStayRetrievable() {
        HashMap<CollidingKey, Integer> map = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            map.put(new CollidingKey("k" + i), i);
        }
        assertEquals(10, map.size());
        for (int i = 0; i < 10; i++) {
            assertEquals(i, map.get(new CollidingKey("k" + i)));
        }
    }

    @Test
    void collisionRemoveHandlesHeadMiddleTail() {
        HashMap<CollidingKey, Integer> map = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            map.put(new CollidingKey("k" + i), i);
        }
        assertEquals(4, map.remove(new CollidingKey("k4")));   // chain head (LIFO chain)
        assertEquals(2, map.remove(new CollidingKey("k2")));   // middle
        assertEquals(0, map.remove(new CollidingKey("k0")));   // tail
        assertEquals(2, map.size());
        assertEquals(1, map.get(new CollidingKey("k1")));
        assertEquals(3, map.get(new CollidingKey("k3")));
    }

    @Test
    void rehashDoublesCapacityAndPreservesEntries() {
        HashMap<Integer, Integer> map = new HashMap<>();
        int count = 100;
        for (int i = 0; i < count; i++) {
            map.put(i, i * i);
        }
        assertTrue(map.capacity() > HashMap.DEFAULT_CAPACITY, "should have rehashed");
        assertEquals(count, map.size());
        for (int i = 0; i < count; i++) {
            assertEquals(i * i, map.get(i));
        }
    }

    @Test
    void containsKey() {
        HashMap<String, String> map = new HashMap<>();
        map.put("a", "1");
        assertTrue(map.containsKey("a"));
        assertFalse(map.containsKey("b"));
    }

    @Test
    void rejectsNullKeyAndValue() {
        HashMap<String, String> map = new HashMap<>();
        assertThrows(IllegalArgumentException.class, () -> map.put(null, "v"));
        assertThrows(IllegalArgumentException.class, () -> map.put("k", null));
        assertThrows(IllegalArgumentException.class, () -> map.get(null));
        assertThrows(IllegalArgumentException.class, () -> map.remove(null));
    }

    @Test
    void entryIteratorVisitsEverythingOnce() {
        HashMap<Integer, String> map = new HashMap<>();
        for (int i = 0; i < 50; i++) {
            map.put(i, "v" + i);
        }
        boolean[] seen = new boolean[50];
        Iterator<HashMap.Entry<Integer, String>> it = map.iterator();
        int visited = 0;
        while (it.hasNext()) {
            HashMap.Entry<Integer, String> e = it.next();
            assertFalse(seen[e.key()], "duplicate key visited: " + e.key());
            seen[e.key()] = true;
            assertEquals("v" + e.key(), e.value());
            visited++;
        }
        assertEquals(50, visited);
        assertThrows(NoSuchElementException.class, it::next);
    }

    @Test
    void keysAndValuesIterators() {
        HashMap<String, Integer> map = new HashMap<>();
        map.put("a", 1);
        map.put("b", 2);
        int keyCount = 0;
        Iterator<String> keys = map.keys();
        while (keys.hasNext()) {
            assertTrue(map.containsKey(keys.next()));
            keyCount++;
        }
        assertEquals(2, keyCount);
        long sum = 0;
        Iterator<Integer> values = map.values();
        while (values.hasNext()) {
            sum += values.next();
        }
        assertEquals(3, sum);
    }

    @Test
    void iteratorIsFailFast() {
        HashMap<String, Integer> map = new HashMap<>();
        map.put("a", 1);
        map.put("b", 2);
        Iterator<HashMap.Entry<String, Integer>> it = map.iterator();
        map.put("c", 3);
        assertThrows(ConcurrentModificationException.class, it::next);
    }

    @Test
    void clearResets() {
        HashMap<Integer, Integer> map = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            map.put(i, i);
        }
        map.clear();
        assertTrue(map.isEmpty());
        assertEquals(HashMap.DEFAULT_CAPACITY, map.capacity());
        assertNull(map.get(5));
    }
}
