package org.pvb.persistenttree.core;

import org.pvb.persistenttree.api.PersistentDataNode;
import org.pvb.persistenttree.api.PersistentIterator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

/**
 * A {@link PersistentIterator} that traverses a persistent tree in
 * <b>depth-first pre-order</b>, using an explicit {@link Stack} to avoid
 * recursion-related stack overflow on deep trees.
 *
 * <h2>Traversal Order</h2>
 * <p>
 * <em>Pre-order</em> DFS visits each node <em>before</em> any of its children.
 * Children are visited left-to-right (in insertion order). Given the tree below:
 * </p>
 * <pre>
 *         A
 *        / \
 *       B   C
 *      / \
 *     D   E
 * </pre>
 * <p>The DFS pre-order sequence would be: <b>A, B, D, E, C</b>.</p>
 *
 * <h2>Implementation Strategy</h2>
 * <p>
 * An explicit {@link Stack} is used instead of recursion. To produce left-to-right
 * child visitation, children are pushed onto the stack in <em>reverse</em> order:
 * the rightmost child is pushed first so that the leftmost child sits on top of the
 * stack and is popped (visited) next. The reversal is achieved by copying the children
 * list and calling {@link Collections#reverse(List)} before pushing.
 * </p>
 *
 * <h2>Snapshot Semantics</h2>
 * <p>
 * Because every {@link PersistentDataNode} is immutable, the iterator captures the
 * tree state at the moment of construction. Subsequent mutations to the
 * {@link org.pvb.persistenttree.core.PersistentNTree} do not affect an in-progress
 * iteration. Each node visited is the exact object present in the tree at the time
 * the iterator was created.
 * </p>
 *
 * <h2>Visibility</h2>
 * <p>
 * The constructor is package-private. Instances are obtained through
 * {@link org.pvb.persistenttree.api.PersistentTree#dfsIterator()} or
 * {@link org.pvb.persistenttree.api.PersistentTree#dfsIterator(PersistentDataNode)}.
 * {@link DefaultNodeManager} also instantiates this class directly for its
 * {@code findAll} implementation.
 * </p>
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>{@link #hasNext()} — O(1)</li>
 *   <li>{@link #next()} — O(k) where k is the number of children of the current node
 *       (due to list copy and reverse)</li>
 *   <li>Full traversal — O(n) where n is the total number of nodes</li>
 * </ul>
 *
 * @param <T> the type of data stored in the nodes being traversed
 *
 * @see BfsIterator
 * @see org.pvb.persistenttree.api.PersistentIterator
 * @see org.pvb.persistenttree.api.PersistentTree#dfsIterator()
 */
public class DfsIterator<T> implements PersistentIterator<T> {

    /**
     * The traversal stack. Each element is a node yet to be visited.
     * The stack is seeded with the starting root at construction time.
     */
    Stack<PersistentDataNode<T>> dfsStack;

    /**
     * Constructs a new {@code DfsIterator} that will traverse the subtree rooted at
     * the given node in depth-first pre-order.
     *
     * <p>The root node is pushed onto the stack immediately. The first call to
     * {@link #next()} will return {@code root} itself.</p>
     *
     * @param root the starting node of the traversal; if {@code null}, the iterator
     *             will immediately report {@link #hasNext()} as {@code false}
     */
    DfsIterator(PersistentDataNode<T> root) {
        dfsStack = new Stack<>();
        dfsStack.push(root);
    }

    /**
     * Returns {@code true} if there are more nodes to visit.
     *
     * <p>The stack is non-empty as long as there are unvisited nodes remaining
     * in the traversal. Once the stack is empty, all reachable nodes in the subtree
     * have been returned by {@link #next()}.</p>
     *
     * @return {@code true} if the iteration has more elements; {@code false} otherwise
     */
    @Override
    public boolean hasNext() {
        return !dfsStack.isEmpty();
    }

    /**
     * Returns the next {@link PersistentDataNode} in depth-first pre-order and
     * advances the iterator.
     *
     * <h4>Procedure</h4>
     * <ol>
     *   <li>Pop the top node from the stack — this is the node being returned.</li>
     *   <li>Copy the node's children list into a new {@link ArrayList}.</li>
     *   <li>Reverse the copy so that the leftmost child ends up on top of the stack
     *       (ensuring left-to-right visitation order).</li>
     *   <li>Push all reversed children onto the stack.</li>
     *   <li>Return the popped node.</li>
     * </ol>
     *
     * <p>The children list copy and reversal are necessary because {@link Stack#push}
     * appends to the top, so the last-pushed item is visited first. By reversing,
     * the first child in insertion order becomes the last-pushed (top of stack) and
     * is therefore visited next.</p>
     *
     * @return the next {@link PersistentDataNode} in DFS pre-order
     * @throws java.util.NoSuchElementException if the iteration has no more elements
     */
    @Override
    public PersistentDataNode<T> next() {
        PersistentDataNode<T> node = dfsStack.pop();
        List<PersistentDataNode<T>> list = new ArrayList<>(node.getChildren());
        Collections.reverse(list);
        for (PersistentDataNode<T> n : list) {
            dfsStack.push(n);
        }
        return node;
    }
}