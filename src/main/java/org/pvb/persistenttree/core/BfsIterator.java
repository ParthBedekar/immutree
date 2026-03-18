package org.pvb.persistenttree.core;

import org.pvb.persistenttree.api.PersistentDataNode;
import org.pvb.persistenttree.api.PersistentIterator;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * A {@link PersistentIterator} that traverses a persistent tree in
 * <b>breadth-first (level-order)</b> sequence, using a {@link Queue} to track
 * nodes awaiting visitation.
 *
 * <h2>Traversal Order</h2>
 * <p>
 * BFS visits nodes level by level: first the root (depth 0), then all children of
 * the root (depth 1), then all grandchildren (depth 2), and so on. Within a given
 * level, nodes are visited in left-to-right (insertion) order. Given the tree below:
 * </p>
 * <pre>
 *         A
 *        / \
 *       B   C
 *      / \
 *     D   E
 * </pre>
 * <p>The BFS sequence would be: <b>A, B, C, D, E</b>.</p>
 *
 * <h2>Implementation Strategy</h2>
 * <p>
 * A {@link LinkedList} is used as the underlying {@link Queue}. On each call to
 * {@link #next()}, the head node is removed from the queue (via {@link Queue#poll()})
 * and all of its children are enqueued in insertion order (left to right). This ensures
 * that all nodes at the current depth are returned before any node at the next depth.
 * </p>
 *
 * <h2>Snapshot Semantics</h2>
 * <p>
 * Because every {@link PersistentDataNode} is immutable, the iterator captures the
 * tree state at the moment of construction. Subsequent mutations to the
 * {@link org.pvb.persistenttree.core.PersistentNTree} do not affect an in-progress
 * iteration.
 * </p>
 *
 * <h2>Visibility</h2>
 * <p>
 * The constructor is package-private. Instances are obtained through
 * {@link org.pvb.persistenttree.api.PersistentTree#bfsIterator()} or
 * {@link org.pvb.persistenttree.api.PersistentTree#bfsIterator(PersistentDataNode)}.
 * </p>
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>{@link #hasNext()} — O(1)</li>
 *   <li>{@link #next()} — O(k) where k is the number of children of the dequeued node</li>
 *   <li>Full traversal — O(n) where n is the total number of nodes</li>
 * </ul>
 *
 * @param <T> the type of data stored in the nodes being traversed
 *
 * @see DfsIterator
 * @see org.pvb.persistenttree.api.PersistentIterator
 * @see org.pvb.persistenttree.api.PersistentTree#bfsIterator()
 */
public class BfsIterator<T> implements PersistentIterator<T> {

    /**
     * The traversal queue. Nodes are enqueued as they are discovered (when their parent
     * is visited) and dequeued when they are returned by {@link #next()}.
     * Uses {@link LinkedList} for O(1) enqueue ({@link Queue#offer}) and dequeue
     * ({@link Queue#poll}) operations.
     */
    Queue<PersistentDataNode<T>> bfsQueue;

    /**
     * Constructs a new {@code BfsIterator} that will traverse the subtree rooted at
     * the given node in breadth-first order.
     *
     * <p>The root is enqueued immediately if non-null. The first call to {@link #next()}
     * will return {@code root} itself. If {@code root} is {@code null}, the iterator
     * immediately reports {@link #hasNext()} as {@code false}.</p>
     *
     * @param root the starting node of the traversal; may be {@code null} to produce
     *             an empty iterator
     */
    BfsIterator(PersistentDataNode<T> root) {
        bfsQueue = new LinkedList<>();
        if (root != null) bfsQueue.offer(root);
    }

    /**
     * Returns {@code true} if there are more nodes to visit.
     *
     * <p>The queue is non-empty as long as there are nodes that have been discovered
     * (enqueued) but not yet returned. Once the queue is empty, all reachable nodes
     * in the subtree have been visited.</p>
     *
     * @return {@code true} if the iteration has more elements; {@code false} otherwise
     */
    @Override
    public boolean hasNext() {
        return !bfsQueue.isEmpty();
    }

    /**
     * Returns the next {@link PersistentDataNode} in breadth-first (level-order)
     * sequence and advances the iterator.
     *
     * <h4>Procedure</h4>
     * <ol>
     *   <li>Dequeue the head node from the queue via {@link Queue#poll()} — this is
     *       the node being returned.</li>
     *   <li>Enqueue all of the node's direct children in insertion order (left-to-right),
     *       so they will be visited after all other nodes at the current depth.</li>
     *   <li>Return the dequeued node.</li>
     * </ol>
     *
     * @return the next {@link PersistentDataNode} in BFS level-order
     * @throws java.util.NoSuchElementException if the iteration has no more elements
     *         ({@link Queue#poll()} returns {@code null} when empty, and the subsequent
     *         {@code node.getChildren()} call would throw a {@link NullPointerException};
     *         callers should always check {@link #hasNext()} before calling this method)
     */
    @Override
    public PersistentDataNode<T> next() {
        PersistentDataNode<T> node = bfsQueue.poll();
        List<PersistentDataNode<T>> children = new ArrayList<>(node.getChildren());

        for (PersistentDataNode<T> n : children) {
            bfsQueue.offer(n);
        }
        return node;
    }
}