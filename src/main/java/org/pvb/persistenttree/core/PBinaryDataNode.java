package org.pvb.persistenttree.core;

import org.pvb.persistenttree.api.BinaryDataNode;
import org.pvb.persistenttree.api.NodeID;
import org.pvb.persistenttree.api.PersistentDataNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The standard <b>immutable</b> implementation of {@link BinaryDataNode}, used as
 * the concrete node type throughout the persistent binary tree.
 *
 * <p>{@code PBinaryDataNode} extends the data model of {@link PDataNode} by storing
 * left and right children as first-class, named fields rather than as an arbitrary-length
 * list. It still satisfies the {@link BinaryDataNode#getChildren()} contract by exposing
 * the two slots in a fixed-size list ({@code [left, right]}), allowing all generic tree
 * algorithms (DFS, BFS, {@link DefaultNodeManager}) to operate over binary trees without
 * modification.</p>
 *
 * <h2>Child Storage Model</h2>
 * <p>
 * Children are stored in two private final fields: {@link #left} and {@link #right}.
 * Either or both may be {@code null} to represent an absent child. The list returned by
 * {@link #getChildren()} is constructed dynamically on each call:
 * </p>
 * <ul>
 *   <li>If both children are {@code null}: an empty list is returned.</li>
 *   <li>If only the left child is present: a single-element list {@code [left]}.</li>
 *   <li>If only the right child is present: a single-element list {@code [right]}.</li>
 *   <li>If both children are present: a two-element list {@code [left, right]}.</li>
 * </ul>
 * <p>
 * This means generic traversal algorithms visit left before right, preserving natural
 * left-to-right order without requiring a sentinel {@code null} entry in the list.
 * </p>
 *
 * <h2>Immutability and Structural Sharing</h2>
 * <p>
 * Like {@link PDataNode}, all fields are {@code final}. A mutating operation on the tree
 * (e.g., {@link PersistentBinaryTree#addLeftChild}) creates a new {@code PBinaryDataNode}
 * for the affected parent and all ancestors; unchanged subtrees are reused by reference.
 * </p>
 *
 * <h2>Constructors</h2>
 * <ul>
 *   <li>{@link #PBinaryDataNode(NodeID, Object, BinaryDataNode, BinaryDataNode)} —
 *       Used when recreating a node during structural modification (both slots supplied
 *       explicitly).</li>
 *   <li>{@link #PBinaryDataNode(NodeID, Object)} — Used when creating a brand-new leaf
 *       node (both slots start as {@code null}).</li>
 * </ul>
 * <p>Both constructors are package-private; node creation is always initiated through
 * the {@link PersistentBinaryTree} API.</p>
 *
 * @param <T> the type of the data payload stored in this node
 *
 * @see BinaryDataNode
 * @see PersistentBinaryTree
 */
public class PBinaryDataNode<T> implements BinaryDataNode<T> {

    /** The data payload of this node. Set at construction; never mutated. */
    private final T data;

    /** The stable unique identity of this node. Set at construction; never mutated. */
    private final NodeID id;

    /**
     * The left child of this node, occupying the first (index&nbsp;0) binary slot.
     * {@code null} when this node has no left child.
     */
    private final BinaryDataNode<T> left;

    /**
     * The right child of this node, occupying the second (index&nbsp;1) binary slot.
     * {@code null} when this node has no right child.
     */
    private final BinaryDataNode<T> right;

    /**
     * Constructs a {@code PBinaryDataNode} with explicitly supplied left and right children.
     *
     * <p>This constructor is the primary path used during structural modifications inside
     * {@link PersistentBinaryTree}. When a child slot is updated (e.g., via
     * {@link PersistentBinaryTree#addLeftChild} or {@link PersistentBinaryTree#setLeft}),
     * the parent and all its ancestors are recreated with their original {@link NodeID}
     * and data, but with the new child references.</p>
     *
     * <p>Either {@code left} or {@code right} (or both) may be {@code null} to represent
     * an absent child in the respective slot.</p>
     *
     * @param id    the stable {@link NodeID} for this node; must not be {@code null}
     * @param data  the data payload; may be {@code null}
     * @param left  the left child; may be {@code null}
     * @param right the right child; may be {@code null}
     */
    public PBinaryDataNode(NodeID id, T data, BinaryDataNode<T> left, BinaryDataNode<T> right) {
        this.id = id;
        this.data = data;
        this.left = left;
        this.right = right;
    }

    /**
     * Constructs a new leaf {@code PBinaryDataNode} with no children.
     *
     * <p>This constructor is used when a brand-new node is added to the tree via
     * {@link PersistentBinaryTree#addLeftChild} or {@link PersistentBinaryTree#addRightChild}.
     * Both {@link #left} and {@link #right} are initialized to {@code null}.</p>
     *
     * @param id   the stable {@link NodeID} for this node; must not be {@code null}
     * @param data the data payload; may be {@code null}
     */
    PBinaryDataNode(NodeID id, T data) {
        this.id = id;
        this.data = data;
        this.left = null;
        this.right = null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the left child node in the binary sense — the node that would be
     * visited before this node during an in-order traversal. Returns {@code null} if
     * no left child has been set.</p>
     */
    @Override
    public BinaryDataNode<T> getLeft() {
        return left;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the right child node in the binary sense — the node that would be
     * visited after this node during an in-order traversal. Returns {@code null} if
     * no right child has been set.</p>
     */
    @Override
    public BinaryDataNode<T> getRight() {
        return right;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the data payload stored in this node at construction time.</p>
     */
    @Override
    public T getData() {
        return data;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the stable {@link NodeID} assigned to this node at construction.
     * The same ID is preserved across all structural modifications that recreate
     * this node's ancestors, allowing callers to track the "same logical node"
     * across versions.</p>
     */
    @Override
    public NodeID getID() {
        return id;
    }

    /**
     * Returns an unmodifiable list containing the non-{@code null} children of this
     * node in left-first, right-second order.
     *
     * <p>This method bridges the binary node model to the generic
     * {@link PersistentDataNode} contract expected by shared traversal algorithms
     * ({@link DfsIterator}, {@link BfsIterator}, {@link DefaultNodeManager}):</p>
     * <ul>
     *   <li>If both children are absent, returns an empty list.</li>
     *   <li>If only the left child is present, returns a single-element list
     *       {@code [left]}.</li>
     *   <li>If only the right child is present, returns a single-element list
     *       {@code [right]}.</li>
     *   <li>If both children are present, returns a two-element list
     *       {@code [left, right]}.</li>
     * </ul>
     *
     * <p><b>Note:</b> Callers that need to distinguish left from right should use
     * {@link #getLeft()} and {@link #getRight()} rather than indexing this list,
     * because a single-element list does not convey which slot is occupied.</p>
     *
     * @return a non-null, unmodifiable {@link List} of present child nodes,
     *         in left-first order; never contains {@code null} entries
     */
    @Override
    public List<PersistentDataNode<T>> getChildren() {
        List<PersistentDataNode<T>> children = new ArrayList<>(2);
        if (left != null) children.add(left);
        if (right != null) children.add(right);
        return Collections.unmodifiableList(children);
    }
}