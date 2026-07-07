package com.budgetguardian.datastructures;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behavior tests for {@link Graph} — DFS, BFS, reachability, flow totals. */
class GraphTest {

    /** Builds the app's transfer network: SCB → SAVING → TRUEMONEY → SCB, plus SCHOLARSHIP → SAVING. */
    private Graph<String> transferNetwork() {
        Graph<String> g = new Graph<>();
        g.addEdge("SCB", "SAVING", 50_000, "atm");
        g.addEdge("SAVING", "TRUEMONEY", 20_000, "topup");
        g.addEdge("TRUEMONEY", "SCB", 5_000, "refund");
        g.addEdge("SCHOLARSHIP", "SAVING", 100_000, "stipend");
        return g;
    }

    @Test
    void startsEmpty() {
        Graph<String> g = new Graph<>();
        assertEquals(0, g.vertexCount());
        assertEquals(0, g.edgeCount());
    }

    @Test
    void addVertexIsIdempotent() {
        Graph<String> g = new Graph<>();
        assertTrue(g.addVertex("SCB"));
        assertFalse(g.addVertex("SCB"));
        assertEquals(1, g.vertexCount());
    }

    @Test
    void addEdgeCreatesEndpoints() {
        Graph<String> g = new Graph<>();
        g.addEdge("A", "B", 100, "x");
        assertTrue(g.hasVertex("A"));
        assertTrue(g.hasVertex("B"));
        assertEquals(2, g.vertexCount());
        assertEquals(1, g.edgeCount());
    }

    @Test
    void parallelEdgesAreKept() {
        Graph<String> g = new Graph<>();
        g.addEdge("A", "B", 100, "first");
        g.addEdge("A", "B", 200, "second");
        assertEquals(2, g.edgeCount());
        assertEquals(2, g.edgesFrom("A").size());
        assertEquals(300, g.totalFlow("A", "B"));
    }

    @Test
    void edgesFromUnknownVertexIsEmpty() {
        Graph<String> g = new Graph<>();
        assertTrue(g.edgesFrom("ghost").isEmpty());
    }

    @Test
    void bfsVisitsLevelByLevel() {
        Graph<String> g = transferNetwork();
        DynamicArray<String> order = g.bfs("SCB");
        assertEquals(3, order.size());              // SCHOLARSHIP unreachable from SCB
        assertEquals("SCB", order.get(0));
        assertEquals("SAVING", order.get(1));
        assertEquals("TRUEMONEY", order.get(2));
    }

    @Test
    void dfsVisitsDepthFirstInInsertionOrder() {
        Graph<String> g = new Graph<>();
        g.addEdge("A", "B", 1, "");
        g.addEdge("A", "C", 1, "");
        g.addEdge("B", "D", 1, "");
        DynamicArray<String> order = g.dfs("A");
        assertEquals(4, order.size());
        assertEquals("A", order.get(0));
        assertEquals("B", order.get(1));            // first inserted neighbor first
        assertEquals("D", order.get(2));            // depth before breadth
        assertEquals("C", order.get(3));
    }

    @Test
    void traversalFromUnknownStartIsEmpty() {
        Graph<String> g = transferNetwork();
        assertTrue(g.dfs("ghost").isEmpty());
        assertTrue(g.bfs("ghost").isEmpty());
    }

    @Test
    void traversalHandlesCycles() {
        Graph<String> g = transferNetwork();        // SCB → SAVING → TRUEMONEY → SCB cycle
        assertEquals(3, g.dfs("SCB").size());       // terminates, no revisits
        assertEquals(3, g.bfs("SCB").size());
    }

    @Test
    void hasPathFollowsDirection() {
        Graph<String> g = transferNetwork();
        assertTrue(g.hasPath("SCHOLARSHIP", "SCB"));   // via SAVING → TRUEMONEY → SCB
        assertFalse(g.hasPath("SCB", "SCHOLARSHIP"));  // no edge into SCHOLARSHIP
        assertTrue(g.hasPath("SCB", "SCB"));           // trivially reachable (start)
        assertFalse(g.hasPath("ghost", "SCB"));
        assertFalse(g.hasPath("SCB", "ghost"));
    }

    @Test
    void totalFlowSumsOnlyDirectLane() {
        Graph<String> g = transferNetwork();
        assertEquals(50_000, g.totalFlow("SCB", "SAVING"));
        assertEquals(0, g.totalFlow("SAVING", "SCB"));   // direction matters
        assertEquals(0, g.totalFlow("ghost", "SCB"));
    }

    @Test
    void verticesIteratorInInsertionOrder() {
        Graph<String> g = transferNetwork();
        Iterator<String> it = g.vertices();
        assertEquals("SCB", it.next());
        assertEquals("SAVING", it.next());
        assertEquals("TRUEMONEY", it.next());
        assertEquals("SCHOLARSHIP", it.next());
        assertFalse(it.hasNext());
    }

    @Test
    void rejectsNullVerticesAndEndpoints() {
        Graph<String> g = new Graph<>();
        assertThrows(IllegalArgumentException.class, () -> g.addVertex(null));
        assertThrows(IllegalArgumentException.class, () -> g.addEdge(null, "B", 1, ""));
        assertThrows(IllegalArgumentException.class, () -> g.addEdge("A", null, 1, ""));
    }

    @Test
    void edgeRecordExposesFields() {
        Graph<String> g = new Graph<>();
        g.addEdge("A", "B", 12_345, "reason");
        Graph.Edge<String> edge = g.edgesFrom("A").getFirst();
        assertEquals("A", edge.from());
        assertEquals("B", edge.to());
        assertEquals(12_345, edge.weight());
        assertEquals("reason", edge.label());
    }
}
