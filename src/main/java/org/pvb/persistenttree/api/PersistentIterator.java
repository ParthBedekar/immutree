package org.pvb.persistenttree.api;

import java.util.Iterator;

/**
 * A specialised {@link Iterator} that traverses nodes of a {@link PersistentTree},
 * yielding {@link PersistentDataNode} instances one at a time.
 *
 * <p>{@code PersistentIterator} extends the standard Java {@link Iterator} interface,
 * parameterizing its element type as {@code PersistentDataNode<T>}. This means every
 * call to {@link #next()} returns a full node object (including its {@link NodeID},
 * data payload, and children list), rather than just the data value.</p>
 *
 * <h2>Concrete Implementations</h2>
 * <p>Two implementations are provided out of the box:</p>
 * <ul>
 *   <li>{@link org.pvb.persistenttree.core.DfsIterator} — Depth-first pre-order traversal.
 *       Visits a node before its children, processing children left-to-right
 *       (in insertion order).</li>
 *   <li>{@link org.pvb.persistenttree.core.BfsIterator} — Breadth-first (level-order)
 *       traversal. Visits all nodes at depth {@code d} before visiting any node at
 *       depth {@code d+1}.</li>
 * </ul>
 *
 * <h2>Obtaining an Iterator</h2>
 * <p>Iterators are created via the {@link PersistentTree} facade:</p>
 * <pre>{@code
 * PersistentIterator<String> dfs = tree.dfsIterator();
 * PersistentIterator<String> bfs = tree.bfsIterator();
 *
 * // Or from an arbitrary subtree root:
 * PersistentIterator<String> subtreeDfs = tree.dfsIterator(someNode);
 * }</pre>
 *
 * <h2>Usage Pattern</h2>
 * <pre>{@code
 * PersistentIterator<String> it = tree.dfsIterator();
 * while (it.hasNext()) {
 *     PersistentDataNode<String> node = it.next();
 *     System.out.println(node.getID() + " -> " + node.getData());
 * }
 * }</pre>
 *
 * <h2>Snapshot Semantics</h2>
 * <p>
 * Because every {@link PersistentDataNode} is immutable, an iterator created at time
 * {@code t} will always traverse the exact same set of nodes in the exact same order,
 * even if the tree is mutated after {@code t}. This is a natural consequence of the
 * tree's persistence guarantee and requires no defensive copying by the iterator.
 * </p>
 *
 * <h2>Unsupported Remove</h2>
 * <p>
 * The {@link Iterator#remove()} operation is not supported by any implementation of
 * this interface (mutations must go through the {@link PersistentTree} API to preserve
 * persistence invariants). Calling {@code remove()} will throw
 * {@link UnsupportedOperationException}.
 * </p>
 *
 * @param <T> the type of the data payload stored in the nodes being iterated
 *
 * @see org.pvb.persistenttree.core.DfsIterator
 * @see org.pvb.persistenttree.core.BfsIterator
 * @see PersistentTree#dfsIterator()
 * @see PersistentTree#bfsIterator()
 */
public interface PersistentIterator<T> extends Iterator<PersistentDataNode<T>> {
}