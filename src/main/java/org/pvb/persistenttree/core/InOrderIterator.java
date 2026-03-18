package org.pvb.persistenttree.core;

import org.pvb.persistenttree.api.BinaryDataNode;
import org.pvb.persistenttree.api.PersistentDataNode;
import org.pvb.persistenttree.api.PersistentIterator;

import java.util.NoSuchElementException;
import java.util.Stack;

/**
 * A {@link PersistentIterator} that traverses a persistent binary tree in
 * <b>in-order (left → root → right)</b> sequence, using an explicit {@link Stack}
 * to avoid recursion-related stack overflow on deep trees.
 *
 * <h2>Traversal Order</h2>
 * <p>
 * In-order traversal visits the entire left subtree, then the current node, then the
 * entire right subtree, recursively. For a binary search tree this produces nodes in
 * ascending key order. Given the binary tree below:
 * </p>
 * <pre>
 *        10
 *       /  \
 *      5    15
 *     / \
 *    3   7
 * </pre>
 * <p>The in-order sequence would be: <b>3, 5, 7, 10, 15</b>.</p>
 *
 * <h2>Implementation Strategy</h2>
 * <p>
 * The iterator simulates the recursive call stack with an explicit {@link Stack}.
 * At any point in the traversal the stack holds the "spine" of nodes reachable by
 * repeatedly following {@link BinaryDataNode#getLeft()} — these are the nodes that
 * still need to be visited along with their right subtrees. The algorithm is:
 * </p>
 * <ol>
 *   <li><b>Construction:</b> Push the root and then push every left descendant of the
 *       root onto the stack (drills all the way to the leftmost node). This seeds
 *       the stack so the first call to {@link #next()} correctly returns the leftmost
 *       (smallest) node.</li>
 *   <li><b>{@link #next()}:</b>
 *     <ol type="a">
 *       <li>Pop the top node — this is the node to return.</li>
 *       <li>If it has a right child, push the right child and then push all of the
 *           right child's left descendants. This seeds the next "spine" so subsequent
 *           calls process the right subtree in left-to-right in-order.</li>
 *       <li>Return the popped node.</li>
 *     </ol>
 *   </li>
 * </ol>
 *
 * <h2>Why an Explicit Stack</h2>
 * <p>
 * A naive recursive implementation would risk {@link StackOverflowError} on very deep
 * trees (e.g., a degenerate right-only chain of 10,000 nodes). The explicit stack moves
 * the frame storage from the JVM call stack to the heap, making the iterator safe for
 * trees of arbitrary depth within available heap memory.
 * </p>
 *
 * <h2>Snapshot Semantics</h2>
 * <p>
 * Because every {@link BinaryDataNode} is immutable, the iterator captures the tree
 * state at the moment of construction. Subsequent mutations to the
 * {@link PersistentBinaryTree} do not affect an in-progress iteration.
 * </p>
 *
 * <h2>Visibility</h2>
 * <p>
 * The constructor is package-private. Instances are obtained via
 * {@link org.pvb.persistenttree.api.BinaryTree#inOrderIterator()} or
 * {@link org.pvb.persistenttree.api.BinaryTree#inOrderIterator(BinaryDataNode)}.
 * </p>
 *
 * <h2>Complexity</h2>
 * <ul>
 *   <li>{@link #hasNext()} — O(1)</li>
 *   <li>{@link #next()} — amortised O(1) per call (each node is pushed and popped
 *       at most once during a full traversal)</li>
 *   <li>Full traversal — O(n) where n is the total number of nodes</li>
 * </ul>
 *
 * @param <T> the type of data stored in the nodes being traversed
 *
 * @see DfsIterator
 * @see BfsIterator
 * @see org.pvb.persistenttree.api.BinaryTree#inOrderIterator()
 */
public class InOrderIterator<T> implements PersistentIterator<T> {

    /**
     * The traversal stack. At all times this holds the remaining "left-spine" nodes —
     * nodes that have been discovered but not yet returned, along with their pending
     * right subtrees.
     */
    private final Stack<BinaryDataNode<T>> stack;

    /**
     * Constructs a new {@code InOrderIterator} that will traverse the binary subtree
     * rooted at {@code root} in in-order (left → root → right) sequence.
     *
     * <p>The constructor eagerly pushes the root and all of its left descendants onto
     * the stack, so the first call to {@link #next()} immediately returns the leftmost
     * (in-order first) node without any additional traversal work.</p>
     *
     * <p>If {@code root} is {@code null}, the iterator immediately reports
     * {@link #hasNext()} as {@code false}.</p>
     *
     * @param root the starting node of the traversal; may be {@code null} to produce
     *             an empty iterator
     */
    InOrderIterator(BinaryDataNode<T> root) {
        stack = new Stack<>();
        pushLeftSpine(root);
    }

    /**
     * Pushes the given node and all of its left descendants onto the stack.
     *
     * <p>This helper is called both during construction (to seed the initial spine from
     * the root) and during {@link #next()} (to seed the spine from the right child of
     * the just-returned node). It terminates when it encounters a node with no left
     * child.</p>
     *
     * <p>After this call, the top of the stack holds the leftmost node in the subtree
     * rooted at {@code node} — the node that should be returned next by
     * {@link #next()}.</p>
     *
     * @param node the subtree root from which to push the left spine; may be {@code null}
     *             (a no-op in that case)
     */
    private void pushLeftSpine(BinaryDataNode<T> node) {
        while (node != null) {
            stack.push(node);
            node = node.getLeft();
        }
    }

    /**
     * Returns {@code true} if there are more nodes to visit in in-order sequence.
     *
     * <p>The stack is non-empty as long as there are nodes that have been discovered
     * but not yet returned. Once the stack is empty, the entire subtree has been
     * exhausted.</p>
     *
     * @return {@code true} if the iteration has more elements; {@code false} otherwise
     */
    @Override
    public boolean hasNext() {
        return !stack.isEmpty();
    }

    /**
     * Returns the next {@link PersistentDataNode} in in-order sequence and advances
     * the iterator.
     *
     * <h4>Procedure</h4>
     * <ol>
     *   <li>Pop the top node from the stack — this is the next in-order node.</li>
     *   <li>If the popped node has a right child, call
     *       {@link #pushLeftSpine(BinaryDataNode)} on the right child to seed the
     *       left spine of the right subtree onto the stack.</li>
     *   <li>Return the popped node.</li>
     * </ol>
     *
     * <p>This ensures that after returning a node, the stack's new top is the immediate
     * in-order successor — either the leftmost node of the right subtree, or the closest
     * ancestor not yet visited.</p>
     *
     * @return the next {@link PersistentDataNode} in in-order sequence
     * @throws NoSuchElementException if the iteration has no more elements (i.e.,
     *         {@link #hasNext()} returns {@code false})
     */
    @Override
    public PersistentDataNode<T> next() {
        if (!hasNext()) {
            throw new NoSuchElementException("InOrderIterator exhausted");
        }
        BinaryDataNode<T> node = stack.pop();
        pushLeftSpine(node.getRight());
        return node;
    }
}