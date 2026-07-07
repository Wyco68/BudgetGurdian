package com.budgetguardian.datastructures;

/**
 * Directed weighted multigraph with adjacency lists — models the account
 * transfer network.
 *
 * <p><b>Purpose:</b> vertices are accounts (Saving, Scholarship, SCB,
 * TrueMoney); every individual transfer is its own directed edge (parallel
 * edges allowed — hence multigraph), weighted by amount in satang. Powers the
 * transfer-flow visualization, reachability queries and flow totals.</p>
 *
 * <p><b>Design — structure composition:</b> adjacency is a
 * {@link HashMap} from vertex to a {@link DoublyLinkedList} of
 * {@link Edge}s; DFS uses an explicit {@link Stack} (iterative — no
 * recursion, no call-stack limits); BFS uses a {@link Queue}. Four custom
 * structures cooperating inside a fifth.</p>
 *
 * <p><b>Advantages:</b> O(1) vertex/edge insertion, adjacency lookup average
 * O(1), traversals O(V+E). <b>Trade-offs:</b> edge existence check between two
 * given vertices is O(deg); adjacency lists suit sparse graphs like this one
 * (V = 4).</p>
 *
 * <p><b>Time complexity:</b> addVertex/addEdge O(1) average;
 * edgesFrom O(1) average; dfs/bfs/hasPath O(V+E); totalFlow O(deg(from)).
 * <b>Space complexity:</b> O(V+E).</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * Graph<String> transfers = new Graph<>();
 * transfers.addEdge("SCB", "SAVING", 50000, "ATM withdrawal");
 * DynamicArray<String> reachable = transfers.bfs("SCB");
 * }</pre>
 *
 * <p>Traversal order is deterministic: neighbors are visited in edge insertion
 * order (chronological — oldest transfer first).</p>
 *
 * @param <T> vertex type — must implement {@code hashCode()}/{@code equals()}
 */
public class Graph<T> {

    /**
     * One directed weighted edge; for Budget Guardian, one transfer.
     *
     * @param <T>    vertex type
     * @param from   source vertex
     * @param to     target vertex
     * @param weight edge weight (amount in satang)
     * @param label  display label (e.g. transfer reason and date)
     */
    public record Edge<T>(T from, T to, long weight, String label) {
        public Edge {
            if (from == null || to == null) {
                throw new IllegalArgumentException("Edge endpoints must not be null");
            }
        }
    }

    private final HashMap<T, DoublyLinkedList<Edge<T>>> adjacency = new HashMap<>();
    private final DynamicArray<T> vertices = new DynamicArray<>();
    private int edgeCount;

    /**
     * Adds a vertex; no-op if already present. Average O(1).
     *
     * @return {@code true} if the vertex was new
     * @throws IllegalArgumentException if {@code vertex} is null
     */
    public boolean addVertex(T vertex) {
        if (vertex == null) {
            throw new IllegalArgumentException("Null vertices are not allowed");
        }
        if (adjacency.containsKey(vertex)) {
            return false;
        }
        adjacency.put(vertex, new DoublyLinkedList<>());
        vertices.append(vertex);
        return true;
    }

    /**
     * Adds a directed edge, creating missing endpoints automatically.
     * Parallel edges are kept — each call records one more edge. Average O(1).
     *
     * @throws IllegalArgumentException if an endpoint is null
     */
    public void addEdge(T from, T to, long weight, String label) {
        addVertex(from);
        addVertex(to);
        adjacency.get(from).addLast(new Edge<>(from, to, weight, label));
        edgeCount++;
    }

    /**
     * @return outgoing edges of {@code vertex} in insertion order; empty list
     *         if the vertex is unknown. Average O(1).
     */
    public DoublyLinkedList<Edge<T>> edgesFrom(T vertex) {
        DoublyLinkedList<Edge<T>> edges = adjacency.get(vertex);
        return edges != null ? edges : new DoublyLinkedList<>();
    }

    /** @return whether the vertex exists. Average O(1). */
    public boolean hasVertex(T vertex) {
        return adjacency.containsKey(vertex);
    }

    /** @return vertex count. O(1). */
    public int vertexCount() {
        return vertices.size();
    }

    /** @return total edge count including parallel edges. O(1). */
    public int edgeCount() {
        return edgeCount;
    }

    /** @return all vertices in insertion order. */
    public Iterator<T> vertices() {
        return vertices.iterator();
    }

    /**
     * Depth-first traversal from {@code start}, iterative with an explicit
     * {@link Stack}. O(V+E) time, O(V) space.
     *
     * <p>Neighbors are pushed in reverse insertion order so they are
     * <em>visited</em> in insertion order — matching recursive DFS.</p>
     *
     * @return visit order; empty if {@code start} is unknown
     */
    public DynamicArray<T> dfs(T start) {
        DynamicArray<T> order = new DynamicArray<>();
        if (!adjacency.containsKey(start)) {
            return order;
        }
        HashMap<T, Boolean> visited = new HashMap<>();
        Stack<T> stack = new Stack<>();
        stack.push(start);
        while (!stack.isEmpty()) {
            T current = stack.pop();
            if (visited.containsKey(current)) {
                continue;
            }
            visited.put(current, Boolean.TRUE);
            order.append(current);
            Iterator<Edge<T>> edges = adjacency.get(current).descendingIterator();
            while (edges.hasNext()) {
                T neighbor = edges.next().to();
                if (!visited.containsKey(neighbor)) {
                    stack.push(neighbor);
                }
            }
        }
        return order;
    }

    /**
     * Breadth-first traversal from {@code start}, frontier managed by a
     * {@link Queue}. O(V+E) time, O(V) space.
     *
     * @return visit order (level by level); empty if {@code start} is unknown
     */
    public DynamicArray<T> bfs(T start) {
        DynamicArray<T> order = new DynamicArray<>();
        if (!adjacency.containsKey(start)) {
            return order;
        }
        HashMap<T, Boolean> visited = new HashMap<>();
        Queue<T> frontier = new Queue<>();
        visited.put(start, Boolean.TRUE);
        frontier.enqueue(start);
        while (!frontier.isEmpty()) {
            T current = frontier.dequeue();
            order.append(current);
            Iterator<Edge<T>> edges = adjacency.get(current).iterator();
            while (edges.hasNext()) {
                T neighbor = edges.next().to();
                if (!visited.containsKey(neighbor)) {
                    visited.put(neighbor, Boolean.TRUE);
                    frontier.enqueue(neighbor);
                }
            }
        }
        return order;
    }

    /**
     * @return whether {@code to} is reachable from {@code from} following edge
     *         directions. O(V+E) via BFS.
     */
    public boolean hasPath(T from, T to) {
        if (!adjacency.containsKey(from) || !adjacency.containsKey(to)) {
            return false;
        }
        DynamicArray<T> reachable = bfs(from);
        for (int i = 0; i < reachable.size(); i++) {
            if (reachable.get(i).equals(to)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return sum of weights of all direct edges {@code from → to}
     *         (total amount ever transferred on that lane). O(deg(from)).
     */
    public long totalFlow(T from, T to) {
        long total = 0;
        Iterator<Edge<T>> edges = edgesFrom(from).iterator();
        while (edges.hasNext()) {
            Edge<T> edge = edges.next();
            if (edge.to().equals(to)) {
                total += edge.weight();
            }
        }
        return total;
    }
}
