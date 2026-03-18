package org.pvb.persistenttree.api;

/**
 * Extends {@link PersistentDataNode} to represent a node in a <b>persistent binary tree</b>,
 * where each node has at most two named children: a <em>left</em> child and a
 * <em>right</em> child.
 *
 * <p>In a standard {@link PersistentDataNode}, children are held in an ordered, unbounded
 * list accessed via {@link PersistentDataNode#getChildren()}. {@code BinaryDataNode}
 * preserves that contract (the list will contain 0, 1, or 2 entries) while additionally
 * exposing the children by their semantic positions through {@link #getLeft()} and
 * {@link #getRight()}. These two accessors are the primary way to navigate a binary tree;
 * {@link #getChildren()} is available for compatibility with generic tree algorithms
 * (e.g., the shared {@link org.pvb.persistenttree.core.DfsIterator} and
 * {@link org.pvb.persistenttree.core.BfsIterator} implementations).</p>
 *
 * <h2>Child Position Convention</h2>
 * <p>
 * The binary position of a child is always determined by its slot, never by its
 * position within the {@link #getChildren()} list alone:
 * </p>
 * <ul>
 *   <li>The <b>left child</b> occupies index&nbsp;0 of the children list.</li>
 *   <li>The <b>right child</b> occupies index&nbsp;1 of the children list.</li>
 * </ul>
 * <p>
 * A node may have a right child without a left child (i.e., the left slot may be
 * {@code null} while the right slot is occupied). In this case the children list
 * contains exactly one entry at index&nbsp;1, but the implementation uses
 * {@code null} as a sentinel at index&nbsp;0. Callers should always use
 * {@link #getLeft()} and {@link #getRight()} rather than indexing into
 * {@link #getChildren()} directly to avoid confusion.
 * </p>
 *
 * <h2>Immutability</h2>
 * <p>
 * Like all nodes in this library, {@code BinaryDataNode} instances are immutable.
 * A mutating operation (e.g., setting the left child) produces a new
 * {@code BinaryDataNode} instance via the tree API; the original node is unchanged
 * and remains valid for any prior version of the tree.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * BinaryTree<String, Integer> tree =
 *         (BinaryTree<String, Integer>) TreeFactory.createTree(10, TreeType.BINARY);
 *
 * NodeID leftId  = tree.addLeftChild(tree.getRoot().getID(), 5);
 * NodeID rightId = tree.addRightChild(tree.getRoot().getID(), 15);
 *
 * BinaryDataNode<Integer> root = (BinaryDataNode<Integer>) tree.getRoot();
 *
 * System.out.println(root.getLeft().getData());  // 5
 * System.out.println(root.getRight().getData()); // 15
 * }</pre>
 *
 * @param <T> the type of the data payload stored in this node
 *
 * @see PersistentDataNode
 * @see org.pvb.persistenttree.core.PBinaryDataNode
 * @see BinaryTree
 */
public interface BinaryDataNode<T> extends PersistentDataNode<T> {

    /**
     * Returns the left child of this node, or {@code null} if no left child exists.
     *
     * <p>The left child is the node occupying position&nbsp;0 (the first slot) in the
     * binary node. It is the child that would be visited <em>before</em> this node
     * during an in-order traversal.</p>
     *
     * <p>Because nodes are immutable, the returned reference is guaranteed to represent
     * the same subtree for the lifetime of this node object. It may be freely retained
     * and shared across threads without additional synchronization.</p>
     *
     * @return the left child {@link BinaryDataNode}, or {@code null} if this node has
     *         no left child
     */
    BinaryDataNode<T> getLeft();

    /**
     * Returns the right child of this node, or {@code null} if no right child exists.
     *
     * <p>The right child is the node occupying position&nbsp;1 (the second slot) in the
     * binary node. It is the child that would be visited <em>after</em> this node during
     * an in-order traversal.</p>
     *
     * <p>Because nodes are immutable, the returned reference is guaranteed to represent
     * the same subtree for the lifetime of this node object.</p>
     *
     * @return the right child {@link BinaryDataNode}, or {@code null} if this node has
     *         no right child
     */
    BinaryDataNode<T> getRight();
}