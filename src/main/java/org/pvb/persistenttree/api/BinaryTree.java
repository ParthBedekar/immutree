package org.pvb.persistenttree.api;

import org.pvb.persistenttree.api.Exceptions.NodeNotFoundException;
import org.pvb.persistenttree.api.Exceptions.NodePositionOccupiedException;

/**
 * Extends {@link PersistentTree} with operations specific to a <b>persistent binary tree</b>,
 * where every node holds at most two children: a designated <em>left</em> child and a
 * designated <em>right</em> child.
 *
 * <p>All persistence, structural-sharing, and versioning guarantees defined in
 * {@link PersistentTree} apply equally to this interface. Every mutating operation
 * returns a new root or node ID while leaving all prior node references intact.</p>
 *
 * <h2>Binary Constraint</h2>
 * <p>
 * Unlike {@link PersistentTree#addChild(NodeID, Object)}, which appends children freely,
 * this interface provides {@link #addLeftChild(NodeID, Object)} and
 * {@link #addRightChild(NodeID, Object)}, each of which targets an explicit slot.
 * Attempting to fill an already-occupied slot throws
 * {@link NodePositionOccupiedException}. To replace an existing child, use
 * {@link #setLeft(NodeID, BinaryDataNode)} or {@link #setRight(NodeID, BinaryDataNode)}.
 * </p>
 *
 * <h2>General {@code addChild} Behaviour</h2>
 * <p>
 * The inherited {@link PersistentTree#addChild(NodeID, Object)} method fills the
 * <em>left slot first</em>; if the left slot is already occupied it fills the right slot.
 * If both slots are occupied, {@link NodePositionOccupiedException} is thrown. This
 * allows generic tree-building code that is unaware of binary semantics to still
 * function correctly when handed a {@code BinaryTree}.
 * </p>
 *
 * <h2>In-Order Traversal</h2>
 * <p>
 * In addition to the DFS and BFS iterators inherited from {@link PersistentTree}, this
 * interface provides {@link #inOrderIterator()} and {@link #inOrderIterator(BinaryDataNode)},
 * which visit nodes in <em>left → root → right</em> order. This traversal is not
 * meaningful for general N-ary trees and is therefore specific to this interface.
 * </p>
 *
 * <h2>Typical Usage</h2>
 * <pre>{@code
 * BinaryTree<String, Integer> tree =
 *         (BinaryTree<String, Integer>) TreeFactory.createTree(10, TreeType.BINARY);
 *
 * // Add children into named slots
 * NodeID leftId  = tree.addLeftChild(tree.getRoot().getID(), 5);
 * NodeID rightId = tree.addRightChild(tree.getRoot().getID(), 15);
 *
 * // Snapshot this version
 * tree.getVersionManager().addVersion("v1", tree.getRoot());
 *
 * // Update a node
 * tree.update(leftId, 7);
 *
 * // In-order traversal (5 → 10 → 15 before update; 7 → 10 → 15 after)
 * PersistentIterator<Integer> it = tree.inOrderIterator();
 * while (it.hasNext()) {
 *     System.out.println(it.next().getData());
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * The standard implementation ({@link org.pvb.persistenttree.core.PersistentBinaryTree})
 * is <b>not thread-safe</b>. External synchronization is required for concurrent use.
 * </p>
 *
 * @param <K> the type used as the version key in the {@link VersionManager}
 * @param <T> the type of the data payload stored in each tree node
 *
 * @see PersistentTree
 * @see BinaryDataNode
 * @see org.pvb.persistenttree.core.PersistentBinaryTree
 * @see org.pvb.persistenttree.core.TreeFactory
 */
public interface BinaryTree<K, T> extends PersistentTree<K, T> {

    /**
     * Adds a new child node containing {@code data} to the <b>left slot</b> of the
     * node identified by {@code parentID}, and returns the {@link NodeID} of the
     * newly created child.
     *
     * <h4>Persistence Mechanism</h4>
     * <p>
     * A new leaf {@link BinaryDataNode} is created with a freshly generated
     * {@link NodeID} and placed into the left position of the parent. All ancestor
     * nodes up to the root are recreated via path-copying. The internal root and
     * {@link NodeManager} are updated to reflect the new version.
     * </p>
     *
     * @param parentID the {@link NodeID} of the node that will become the parent;
     *                 must not be {@code null}
     * @param data     the data payload for the new left child; may be {@code null}
     * @return the {@link NodeID} of the newly created left child
     * @throws NodeNotFoundException         if no node with {@code parentID} exists
     *                                        in the current version of the tree
     * @throws NodePositionOccupiedException if the parent's left slot is already occupied
     */
    NodeID addLeftChild(NodeID parentID, T data);

    /**
     * Adds a new child node containing {@code data} to the <b>right slot</b> of the
     * node identified by {@code parentID}, and returns the {@link NodeID} of the
     * newly created child.
     *
     * <h4>Persistence Mechanism</h4>
     * <p>
     * A new leaf {@link BinaryDataNode} is created with a freshly generated
     * {@link NodeID} and placed into the right position of the parent. All ancestor
     * nodes up to the root are recreated via path-copying. The internal root and
     * {@link NodeManager} are updated to reflect the new version.
     * </p>
     *
     * @param parentID the {@link NodeID} of the node that will become the parent;
     *                 must not be {@code null}
     * @param data     the data payload for the new right child; may be {@code null}
     * @return the {@link NodeID} of the newly created right child
     * @throws NodeNotFoundException         if no node with {@code parentID} exists
     *                                        in the current version of the tree
     * @throws NodePositionOccupiedException if the parent's right slot is already occupied
     */
    NodeID addRightChild(NodeID parentID, T data);

    /**
     * Replaces the <b>left child</b> of the node identified by {@code parentID} with
     * the provided {@code newLeft} subtree, and returns the new tree root.
     *
     * <p>Unlike {@link #addLeftChild(NodeID, Object)}, this method unconditionally
     * overwrites whatever is currently in the left slot (including {@code null} → node
     * and node → {@code null}). Passing {@code null} as {@code newLeft} effectively
     * removes the left child and its entire subtree.</p>
     *
     * <h4>Persistence Mechanism</h4>
     * <p>
     * The parent node is recreated with the supplied {@code newLeft} in its left slot
     * and its existing right child preserved. All ancestor nodes up to the root are
     * recreated. The internal root and {@link NodeManager} are updated.
     * </p>
     *
     * @param parentID the {@link NodeID} of the node whose left child should be replaced;
     *                 must not be {@code null}
     * @param newLeft  the new left subtree; may be {@code null} to clear the left child
     * @return the new root {@link PersistentDataNode} of the updated tree version
     * @throws NodeNotFoundException if no node with {@code parentID} exists in the tree
     */
    PersistentDataNode<T> setLeft(NodeID parentID, BinaryDataNode<T> newLeft);

    /**
     * Replaces the <b>right child</b> of the node identified by {@code parentID} with
     * the provided {@code newRight} subtree, and returns the new tree root.
     *
     * <p>Like {@link #setLeft(NodeID, BinaryDataNode)}, this method unconditionally
     * overwrites the right slot. Passing {@code null} as {@code newRight} removes the
     * right child and its entire subtree.</p>
     *
     * <h4>Persistence Mechanism</h4>
     * <p>
     * The parent node is recreated with its existing left child preserved and the
     * supplied {@code newRight} in its right slot. All ancestor nodes up to the root
     * are recreated. The internal root and {@link NodeManager} are updated.
     * </p>
     *
     * @param parentID the {@link NodeID} of the node whose right child should be replaced;
     *                 must not be {@code null}
     * @param newRight the new right subtree; may be {@code null} to clear the right child
     * @return the new root {@link PersistentDataNode} of the updated tree version
     * @throws NodeNotFoundException if no node with {@code parentID} exists in the tree
     */
    PersistentDataNode<T> setRight(NodeID parentID, BinaryDataNode<T> newRight);

    /**
     * Returns an in-order (left → root → right) {@link PersistentIterator} starting
     * from the current root of this tree.
     *
     * <p>In-order traversal visits the left subtree completely, then the current node,
     * then the right subtree. For a binary search tree this produces nodes in
     * ascending key order. Given the binary tree below:</p>
     * <pre>
     *        10
     *       /  \
     *      5    15
     *     / \
     *    3   7
     * </pre>
     * <p>The in-order sequence would be: <b>3, 5, 7, 10, 15</b>.</p>
     *
     * <p>The iterator captures a snapshot of the tree structure at the moment it is
     * created. Subsequent mutations to the tree do not affect an already-created
     * iterator.</p>
     *
     * <p>The iterator does not support {@link java.util.Iterator#remove()}.</p>
     *
     * @return a new, non-null {@link PersistentIterator} that traverses nodes in
     *         in-order (left → root → right) starting from the current root
     * @see #inOrderIterator(BinaryDataNode)
     */
    PersistentIterator<T> inOrderIterator();

    /**
     * Returns an in-order (left → root → right) {@link PersistentIterator} starting
     * from the specified subtree root.
     *
     * <p>Identical in behaviour to {@link #inOrderIterator()} but begins traversal at
     * the provided {@code root} node rather than the current tree root. This allows
     * in-order iteration of an arbitrary subtree or a historical version.</p>
     *
     * @param root the {@link BinaryDataNode} to use as the starting point of the
     *             traversal; must not be {@code null}
     * @return a new, non-null {@link PersistentIterator} that traverses nodes in
     *         in-order starting from {@code root}
     */
    PersistentIterator<T> inOrderIterator(BinaryDataNode<T> root);
}