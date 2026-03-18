package org.pvb.persistenttree.core;

import org.pvb.persistenttree.api.*;
import org.pvb.persistenttree.api.Exceptions.NodeNotFoundException;
import org.pvb.persistenttree.api.Exceptions.NodePositionOccupiedException;

/**
 * The primary implementation of {@link BinaryTree}, providing a fully persistent binary
 * tree backed by <b>path-copying</b> (structural sharing).
 *
 * <p>A binary tree is a rooted tree in which each node has at most two children,
 * designated <em>left</em> and <em>right</em>. This implementation extends all persistence,
 * versioning, and traversal capabilities of {@link PersistentNTree} while adding
 * binary-specific operations: named child insertion ({@link #addLeftChild},
 * {@link #addRightChild}), direct slot replacement ({@link #setLeft}, {@link #setRight}),
 * and in-order traversal ({@link #inOrderIterator()}).</p>
 *
 * <h2>Persistence via Path-Copying</h2>
 * <p>
 * Every mutating operation recreates only the nodes along the path from the root to the
 * affected node. For example, given the tree:
 * </p>
 * <pre>
 *        A (root)
 *       / \
 *      B   C
 *     /
 *    D
 * </pre>
 * <p>
 * Calling {@code addRightChild(B.getID(), "E")} produces:
 * </p>
 * <pre>
 *        A' (new root)
 *       / \
 *      B'  C         ← C is reused; only A and B are recreated
 *     / \
 *    D   E (new)
 * </pre>
 * <p>
 * The original root {@code A} and node {@code B} are unchanged and still represent
 * the prior version of the tree.
 * </p>
 *
 * <h2>Relation to PersistentNTree</h2>
 * <p>
 * {@code PersistentBinaryTree} does <b>not</b> extend {@link PersistentNTree}; it
 * independently implements {@link BinaryTree} (which itself extends {@link PersistentTree}).
 * The two tree implementations share the same supporting infrastructure:
 * {@link DefaultNodeManager}, {@link DefaultVersionManager}, {@link DfsIterator},
 * and {@link BfsIterator} all operate on {@link PersistentDataNode} and work
 * transparently with {@link PBinaryDataNode}.
 * </p>
 *
 * <h2>Internal State</h2>
 * <ul>
 *   <li>{@link #root} — the root of the current version, typed as
 *       {@link BinaryDataNode} for binary-aware access.</li>
 *   <li>{@link #nodeManager} — a {@link DefaultNodeManager} kept in sync after every
 *       mutation.</li>
 *   <li>{@link #versionManager} — a {@link DefaultVersionManager} for manual snapshots;
 *       not auto-updated.</li>
 * </ul>
 *
 * <h2>Factory Access</h2>
 * <pre>{@code
 * BinaryTree<String, Integer> tree =
 *         (BinaryTree<String, Integer>) TreeFactory.createTree(10, TreeType.BINARY);
 * }</pre>
 *
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li>Update: O(n) — full tree search + path copy</li>
 *   <li>Add left/right child: O(n) — full tree search + path copy</li>
 *   <li>Set left/right: O(n) — full tree search + path copy</li>
 *   <li>Remove node: O(n) — full tree scan + path copy</li>
 *   <li>DFS / BFS / in-order traversal: O(n)</li>
 *   <li>Node lookup ({@link NodeManager#getNode}): O(n)</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is <b>not thread-safe</b>. The mutable fields ({@link #root} and the roots
 * inside the managers) are written without synchronization. External locking is required
 * for concurrent use.
 * </p>
 *
 * @param <K> the version key type used by the {@link VersionManager}
 * @param <T> the type of data stored in each node
 *
 * @see BinaryTree
 * @see PBinaryDataNode
 * @see TreeFactory
 */
public class PersistentBinaryTree<K, T> implements BinaryTree<K, T> {

    /**
     * The root of the current tree version, typed as {@link BinaryDataNode} so that
     * binary-specific accessors ({@link BinaryDataNode#getLeft()},
     * {@link BinaryDataNode#getRight()}) are available without casting throughout
     * the helper methods.
     */
    BinaryDataNode<T> root;

    /**
     * The {@link DefaultNodeManager} that provides node lookup and predicate search
     * over the current version. Kept in sync with {@link #root} via
     * {@link DefaultNodeManager#updateRoot(PersistentDataNode)} after every mutation.
     */
    NodeManager<T> nodeManager;

    /**
     * The {@link DefaultVersionManager} that stores named snapshots. Not updated
     * automatically — callers must call
     * {@link VersionManager#addVersion(Object, PersistentDataNode)} explicitly.
     */
    VersionManager<K, T> versionManager;

    /**
     * Constructs a new {@code PersistentBinaryTree} with a single root node containing
     * the given data.
     *
     * <p>The initial tree consists of exactly one {@link PBinaryDataNode} (the root)
     * with no left or right child. The root is assigned a randomly generated
     * {@link NodeID}. A {@link DefaultNodeManager} and {@link DefaultVersionManager}
     * are also initialised.</p>
     *
     * <p>This constructor is package-private; use
     * {@link TreeFactory#createTree(Object, org.pvb.persistenttree.api.Enums.TreeType)}
     * to create trees from application code.</p>
     *
     * @param data the data payload for the root node; may be {@code null}
     */
    PersistentBinaryTree(T data) {
        this.root = new PBinaryDataNode<>(NodeID.generateUUID(), data);
        this.nodeManager = new DefaultNodeManager<>(root);
        this.versionManager = new DefaultVersionManager<>();
    }

    // -------------------------------------------------------------------------
    // Manager accessors
    // -------------------------------------------------------------------------

    /** {@inheritDoc} */
    @Override
    public NodeManager<T> getNodeManager() {
        return nodeManager;
    }

    /** {@inheritDoc} */
    @Override
    public VersionManager<K, T> getVersionManager() {
        return versionManager;
    }

    /** {@inheritDoc} */
    @Override
    public PersistentDataNode<T> getRoot() {
        return root;
    }

    // -------------------------------------------------------------------------
    // Internal sync helper
    // -------------------------------------------------------------------------

    /**
     * Updates both the internal {@link #root} field and the {@link NodeManager}'s root
     * pointer to the given node.
     *
     * <p>This helper is called at the end of every mutating operation to keep
     * {@link #root} and the {@link DefaultNodeManager} in sync. It centralises the
     * two-line update so it cannot be accidentally omitted in any code path.</p>
     *
     * @param newRoot the new root of the current tree version; may be {@code null}
     *                if the root itself was removed
     */
    private void syncRoot(BinaryDataNode<T> newRoot) {
        this.root = newRoot;
        ((DefaultNodeManager<T>) nodeManager).updateRoot(newRoot);
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    /**
     * Recursively searches for the node with {@code id} and, when found, returns a
     * new {@link PBinaryDataNode} with the same {@link NodeID}, same left/right children,
     * but with {@code newData} as the payload. All ancestors are recreated (path-copying).
     *
     * <h4>Algorithm</h4>
     * <ol>
     *   <li>If {@code node} is {@code null}, return {@code null} (exhausted branch).</li>
     *   <li>If this node's ID matches {@code id}, return a new
     *       {@link PBinaryDataNode} with {@code newData}, same left and right.</li>
     *   <li>Recurse left and right:
     *     <ul>
     *       <li>If neither subtree produced a change, return {@code node} unchanged
     *           (structural sharing).</li>
     *       <li>If the left subtree changed, return a new node with the updated left
     *           and the original right.</li>
     *       <li>If the right subtree changed, return a new node with the original left
     *           and the updated right.</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * @param id      the target {@link NodeID}
     * @param node    the current subtree root; may be {@code null}
     * @param newData the data to assign to the matched node
     * @return the updated subtree root, or the original {@code node} if unchanged
     */
    private BinaryDataNode<T> updateHelper(NodeID id, BinaryDataNode<T> node, T newData) {
        if (node == null) return null;

        if (node.getID().equals(id)) {
            return new PBinaryDataNode<>(id, newData, node.getLeft(), node.getRight());
        }

        BinaryDataNode<T> newLeft  = updateHelper(id, node.getLeft(),  newData);
        BinaryDataNode<T> newRight = updateHelper(id, node.getRight(), newData);

        if (newLeft == node.getLeft() && newRight == node.getRight()) {
            return node; // nothing changed in this subtree
        }
        return new PBinaryDataNode<>(node.getID(), node.getData(), newLeft, newRight);
    }

    /**
     * {@inheritDoc}
     *
     * <h4>Implementation Details</h4>
     * <p>
     * Delegates to {@link #updateHelper(NodeID, BinaryDataNode, Object)}. If the
     * returned root is reference-equal to the current root, the node was not found and
     * {@link NodeNotFoundException} is thrown.
     * </p>
     *
     * @throws NodeNotFoundException if no node with {@code id} exists in the tree
     */
    @Override
    public PersistentDataNode<T> update(NodeID id, T data) {
        BinaryDataNode<T> newRoot = updateHelper(id, this.root, data);
        if (newRoot == this.root) {
            throw new NodeNotFoundException("Node not found with ID: " + id.id());
        }
        syncRoot(newRoot);
        return newRoot;
    }

    // -------------------------------------------------------------------------
    // addChild (generic — fills left first, then right)
    // -------------------------------------------------------------------------

    /**
     * Internal carrier used by {@link #addChildHelper} to propagate the new child's
     * {@link NodeID} and the updated subtree root upward through the recursion.
     */
    private record NewChild<T>(NodeID id, BinaryDataNode<T> node) {}

    /**
     * Recursively searches for the parent with {@code parentID} and adds a new child
     * to the first available slot (left before right).
     *
     * <h4>Algorithm</h4>
     * <ol>
     *   <li>If {@code node} is {@code null}, return {@code null} (not found here).</li>
     *   <li>If this node's ID matches {@code parentID}:
     *     <ul>
     *       <li>If the left slot is empty, place the new child there.</li>
     *       <li>Else if the right slot is empty, place the new child there.</li>
     *       <li>Else throw {@link NodePositionOccupiedException}.</li>
     *     </ul>
     *   </li>
     *   <li>Otherwise, recurse left then right. Return a {@link NewChild} with the
     *       updated subtree if the parent was found in either branch.</li>
     * </ol>
     *
     * @param parentID the target parent {@link NodeID}
     * @param data     the data for the new child node
     * @param node     the current subtree root; may be {@code null}
     * @return a {@link NewChild} with the new child's ID and the updated subtree root,
     *         or {@code null} if {@code parentID} was not found in this subtree
     * @throws NodePositionOccupiedException if both slots of the matched parent are full
     */
    private NewChild<T> addChildHelper(NodeID parentID, T data, BinaryDataNode<T> node) {
        if (node == null) return null;

        if (node.getID().equals(parentID)) {
            NodeID newId = NodeID.generateUUID();
            PBinaryDataNode<T> newChild = new PBinaryDataNode<>(newId, data);

            if (node.getLeft() == null) {
                return new NewChild<>(newId,
                        new PBinaryDataNode<>(node.getID(), node.getData(), newChild, node.getRight()));
            } else if (node.getRight() == null) {
                return new NewChild<>(newId,
                        new PBinaryDataNode<>(node.getID(), node.getData(), node.getLeft(), newChild));
            } else {
                throw new NodePositionOccupiedException(
                        "Both left and right slots are occupied for node: " + parentID.id());
            }
        }

        NewChild<T> leftResult = addChildHelper(parentID, data, node.getLeft());
        if (leftResult != null) {
            return new NewChild<>(leftResult.id(),
                    new PBinaryDataNode<>(node.getID(), node.getData(),
                            leftResult.node(), node.getRight()));
        }

        NewChild<T> rightResult = addChildHelper(parentID, data, node.getRight());
        if (rightResult != null) {
            return new NewChild<>(rightResult.id(),
                    new PBinaryDataNode<>(node.getID(), node.getData(),
                            node.getLeft(), rightResult.node()));
        }

        return null;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Fills the <b>left slot first</b>, then the right slot. Throws
     * {@link NodePositionOccupiedException} if both slots are already occupied.</p>
     *
     * @throws NodeNotFoundException         if no node with {@code parentID} exists
     * @throws NodePositionOccupiedException if both child slots of the parent are full
     */
    @Override
    public NodeID addChild(NodeID parentID, T data) {
        NewChild<T> result = addChildHelper(parentID, data, this.root);
        if (result == null) {
            throw new NodeNotFoundException("Parent node not found with ID: " + parentID.id());
        }
        syncRoot(result.node());
        return result.id();
    }

    // -------------------------------------------------------------------------
    // addLeftChild
    // -------------------------------------------------------------------------

    /**
     * Recursively searches for the parent with {@code parentID} and inserts a new
     * child into the <b>left slot</b> specifically.
     *
     * <h4>Algorithm</h4>
     * <ol>
     *   <li>If {@code node} is {@code null}, return {@code null}.</li>
     *   <li>If this node's ID matches {@code parentID}:
     *     <ul>
     *       <li>If the left slot is already occupied, throw
     *           {@link NodePositionOccupiedException}.</li>
     *       <li>Otherwise create a new child and return the recreated parent with it
     *           in the left slot, preserving the existing right child.</li>
     *     </ul>
     *   </li>
     *   <li>Recurse left then right, rebuilding the path if found in either branch.</li>
     * </ol>
     *
     * @param parentID the target parent {@link NodeID}
     * @param data     the data for the new left child
     * @param node     the current subtree root
     * @return a {@link NewChild} with the ID and updated subtree, or {@code null}
     *         if {@code parentID} was not in this subtree
     * @throws NodePositionOccupiedException if the parent's left slot is occupied
     */
    private NewChild<T> addLeftHelper(NodeID parentID, T data, BinaryDataNode<T> node) {
        if (node == null) return null;

        if (node.getID().equals(parentID)) {
            if (node.getLeft() != null) {
                throw new NodePositionOccupiedException(
                        "Left slot is already occupied for node: " + parentID.id());
            }
            NodeID newId = NodeID.generateUUID();
            PBinaryDataNode<T> newChild = new PBinaryDataNode<>(newId, data);
            return new NewChild<>(newId,
                    new PBinaryDataNode<>(node.getID(), node.getData(), newChild, node.getRight()));
        }

        NewChild<T> leftResult = addLeftHelper(parentID, data, node.getLeft());
        if (leftResult != null) {
            return new NewChild<>(leftResult.id(),
                    new PBinaryDataNode<>(node.getID(), node.getData(),
                            leftResult.node(), node.getRight()));
        }

        NewChild<T> rightResult = addLeftHelper(parentID, data, node.getRight());
        if (rightResult != null) {
            return new NewChild<>(rightResult.id(),
                    new PBinaryDataNode<>(node.getID(), node.getData(),
                            node.getLeft(), rightResult.node()));
        }

        return null;
    }

    /**
     * {@inheritDoc}
     *
     * <h4>Implementation Details</h4>
     * <p>Delegates to {@link #addLeftHelper(NodeID, Object, BinaryDataNode)}.
     * A {@code null} result means the parent was not found.</p>
     *
     * @throws NodeNotFoundException         if no node with {@code parentID} exists
     * @throws NodePositionOccupiedException if the parent's left slot is already occupied
     */
    @Override
    public NodeID addLeftChild(NodeID parentID, T data) {
        NewChild<T> result = addLeftHelper(parentID, data, this.root);
        if (result == null) {
            throw new NodeNotFoundException("Parent node not found with ID: " + parentID.id());
        }
        syncRoot(result.node());
        return result.id();
    }

    // -------------------------------------------------------------------------
    // addRightChild
    // -------------------------------------------------------------------------

    /**
     * Recursively searches for the parent with {@code parentID} and inserts a new
     * child into the <b>right slot</b> specifically.
     *
     * <p>Mirrors {@link #addLeftHelper} but targets the right slot.</p>
     *
     * @param parentID the target parent {@link NodeID}
     * @param data     the data for the new right child
     * @param node     the current subtree root
     * @return a {@link NewChild} with the ID and updated subtree, or {@code null}
     * @throws NodePositionOccupiedException if the parent's right slot is occupied
     */
    private NewChild<T> addRightHelper(NodeID parentID, T data, BinaryDataNode<T> node) {
        if (node == null) return null;

        if (node.getID().equals(parentID)) {
            if (node.getRight() != null) {
                throw new NodePositionOccupiedException(
                        "Right slot is already occupied for node: " + parentID.id());
            }
            NodeID newId = NodeID.generateUUID();
            PBinaryDataNode<T> newChild = new PBinaryDataNode<>(newId, data);
            return new NewChild<>(newId,
                    new PBinaryDataNode<>(node.getID(), node.getData(), node.getLeft(), newChild));
        }

        NewChild<T> leftResult = addRightHelper(parentID, data, node.getLeft());
        if (leftResult != null) {
            return new NewChild<>(leftResult.id(),
                    new PBinaryDataNode<>(node.getID(), node.getData(),
                            leftResult.node(), node.getRight()));
        }

        NewChild<T> rightResult = addRightHelper(parentID, data, node.getRight());
        if (rightResult != null) {
            return new NewChild<>(rightResult.id(),
                    new PBinaryDataNode<>(node.getID(), node.getData(),
                            node.getLeft(), rightResult.node()));
        }

        return null;
    }

    /**
     * {@inheritDoc}
     *
     * <h4>Implementation Details</h4>
     * <p>Delegates to {@link #addRightHelper(NodeID, Object, BinaryDataNode)}.
     * A {@code null} result means the parent was not found.</p>
     *
     * @throws NodeNotFoundException         if no node with {@code parentID} exists
     * @throws NodePositionOccupiedException if the parent's right slot is already occupied
     */
    @Override
    public NodeID addRightChild(NodeID parentID, T data) {
        NewChild<T> result = addRightHelper(parentID, data, this.root);
        if (result == null) {
            throw new NodeNotFoundException("Parent node not found with ID: " + parentID.id());
        }
        syncRoot(result.node());
        return result.id();
    }

    // -------------------------------------------------------------------------
    // setLeft / setRight
    // -------------------------------------------------------------------------

    /**
     * Recursively searches for the node with {@code parentID} and unconditionally
     * replaces its left child with {@code newLeft}.
     *
     * <h4>Algorithm</h4>
     * <ol>
     *   <li>If {@code node} is {@code null}, return {@code null}.</li>
     *   <li>If this node's ID matches {@code parentID}, return a new
     *       {@link PBinaryDataNode} with {@code newLeft} in the left slot and the
     *       existing right child preserved.</li>
     *   <li>Recurse left then right, rebuilding the path if found.</li>
     *   <li>If neither branch matched, return {@code null}.</li>
     * </ol>
     *
     * @param parentID the target parent {@link NodeID}
     * @param newLeft  the replacement for the left slot; may be {@code null}
     * @param node     the current subtree root
     * @return the updated subtree root, or {@code null} if not found
     */
    private BinaryDataNode<T> setLeftHelper(NodeID parentID, BinaryDataNode<T> newLeft,
                                            BinaryDataNode<T> node) {
        if (node == null) return null;

        if (node.getID().equals(parentID)) {
            return new PBinaryDataNode<>(node.getID(), node.getData(), newLeft, node.getRight());
        }

        BinaryDataNode<T> updatedLeft = setLeftHelper(parentID, newLeft, node.getLeft());
        if (updatedLeft != node.getLeft()) {
            return new PBinaryDataNode<>(node.getID(), node.getData(), updatedLeft, node.getRight());
        }

        BinaryDataNode<T> updatedRight = setLeftHelper(parentID, newLeft, node.getRight());
        if (updatedRight != node.getRight()) {
            return new PBinaryDataNode<>(node.getID(), node.getData(), node.getLeft(), updatedRight);
        }

        return null;
    }

    /**
     * {@inheritDoc}
     *
     * <h4>Implementation Details</h4>
     * <p>Delegates to {@link #setLeftHelper(NodeID, BinaryDataNode, BinaryDataNode)}.
     * A {@code null} result means the parent was not found.</p>
     *
     * @throws NodeNotFoundException if no node with {@code parentID} exists in the tree
     */
    @Override
    public PersistentDataNode<T> setLeft(NodeID parentID, BinaryDataNode<T> newLeft) {
        BinaryDataNode<T> newRoot = setLeftHelper(parentID, newLeft, this.root);
        if (newRoot == null) {
            throw new NodeNotFoundException("Node not found with ID: " + parentID.id());
        }
        syncRoot(newRoot);
        return newRoot;
    }

    /**
     * Recursively searches for the node with {@code parentID} and unconditionally
     * replaces its right child with {@code newRight}.
     *
     * <p>Mirrors {@link #setLeftHelper} but targets the right slot.</p>
     *
     * @param parentID the target parent {@link NodeID}
     * @param newRight the replacement for the right slot; may be {@code null}
     * @param node     the current subtree root
     * @return the updated subtree root, or {@code null} if not found
     */
    private BinaryDataNode<T> setRightHelper(NodeID parentID, BinaryDataNode<T> newRight,
                                             BinaryDataNode<T> node) {
        if (node == null) return null;

        if (node.getID().equals(parentID)) {
            return new PBinaryDataNode<>(node.getID(), node.getData(), node.getLeft(), newRight);
        }

        BinaryDataNode<T> updatedLeft = setRightHelper(parentID, newRight, node.getLeft());
        if (updatedLeft != node.getLeft()) {
            return new PBinaryDataNode<>(node.getID(), node.getData(), updatedLeft, node.getRight());
        }

        BinaryDataNode<T> updatedRight = setRightHelper(parentID, newRight, node.getRight());
        if (updatedRight != node.getRight()) {
            return new PBinaryDataNode<>(node.getID(), node.getData(), node.getLeft(), updatedRight);
        }

        return null;
    }

    /**
     * {@inheritDoc}
     *
     * <h4>Implementation Details</h4>
     * <p>Delegates to {@link #setRightHelper(NodeID, BinaryDataNode, BinaryDataNode)}.
     * A {@code null} result means the parent was not found.</p>
     *
     * @throws NodeNotFoundException if no node with {@code parentID} exists in the tree
     */
    @Override
    public PersistentDataNode<T> setRight(NodeID parentID, BinaryDataNode<T> newRight) {
        BinaryDataNode<T> newRoot = setRightHelper(parentID, newRight, this.root);
        if (newRoot == null) {
            throw new NodeNotFoundException("Node not found with ID: " + parentID.id());
        }
        syncRoot(newRoot);
        return newRoot;
    }

    // -------------------------------------------------------------------------
    // removeNode
    // -------------------------------------------------------------------------

    /** Flag set by {@link #removeHelper} when the target node is found and removed. */
    private boolean lastRemoveFound;

    /**
     * Recursively searches for the node with {@code id} and removes it (along with
     * its entire subtree) from the binary tree.
     *
     * <h4>Algorithm</h4>
     * <ol>
     *   <li>If {@code node} is {@code null}, return {@code null}.</li>
     *   <li>If this node's ID matches {@code id}: set {@link #lastRemoveFound} to
     *       {@code true} and return {@code null} (signals removal to the parent).</li>
     *   <li>Recurse into the left child. If the left child was removed, rebuild this
     *       node with a {@code null} left slot.</li>
     *   <li>Recurse into the right child. If the right child was removed, rebuild this
     *       node with a {@code null} right slot.</li>
     *   <li>If nothing changed, return the original {@code node} (structural sharing).</li>
     * </ol>
     *
     * @param id   the {@link NodeID} of the node to remove
     * @param node the current subtree root
     * @return the updated subtree root, or {@code null} if this node was removed
     */
    private BinaryDataNode<T> removeHelper(NodeID id, BinaryDataNode<T> node) {
        if (node == null) return null;

        if (node.getID().equals(id)) {
            lastRemoveFound = true;
            return null;
        }

        BinaryDataNode<T> newLeft  = removeHelper(id, node.getLeft());
        BinaryDataNode<T> newRight = removeHelper(id, node.getRight());

        if (newLeft == node.getLeft() && newRight == node.getRight()) {
            return node; // nothing changed
        }
        return new PBinaryDataNode<>(node.getID(), node.getData(), newLeft, newRight);
    }

    /**
     * {@inheritDoc}
     *
     * <h4>Implementation Details</h4>
     * <p>Resets {@link #lastRemoveFound} to {@code false}, delegates to
     * {@link #removeHelper(NodeID, BinaryDataNode)}, then checks the flag. Throws
     * {@link NodeNotFoundException} if the flag remains {@code false}.</p>
     *
     * @throws NodeNotFoundException if no node with {@code id} exists in the tree
     */
    @Override
    public PersistentDataNode<T> removeNode(NodeID id) {
        lastRemoveFound = false;
        BinaryDataNode<T> newRoot = removeHelper(id, this.root);
        if (!lastRemoveFound) {
            throw new NodeNotFoundException("Node not found with ID: " + id.id());
        }
        syncRoot(newRoot);
        return newRoot;
    }

    /**
     * {@inheritDoc}
     *
     * <h4>Implementation Details</h4>
     * <p>Identical to {@link #removeNode(NodeID)} except the search is limited to the
     * subtree rooted at {@code node}, and the internal tree root and manager are
     * <b>not</b> updated.</p>
     *
     * @throws NodeNotFoundException if no node with {@code id} is found in the subtree
     */
    @Override
    public PersistentDataNode<T> removeNode(NodeID id, PersistentDataNode<T> node) {
        lastRemoveFound = false;
        BinaryDataNode<T> typedNode = (BinaryDataNode<T>) node;
        BinaryDataNode<T> result = removeHelper(id, typedNode);
        if (!lastRemoveFound) {
            throw new NodeNotFoundException("Node not found with ID: " + id.id());
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Iterators
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Creates a new {@link DfsIterator} seeded with the current root. The DFS
     * iterator operates on {@link PersistentDataNode#getChildren()}, which for a
     * {@link PBinaryDataNode} returns {@code [left, right]} (omitting {@code null}
     * slots), producing a correct left-to-right pre-order traversal.</p>
     */
    @Override
    public PersistentIterator<T> dfsIterator() {
        return new DfsIterator<>(root);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Creates a new {@link DfsIterator} seeded with the provided {@code root}.</p>
     */
    @Override
    public PersistentIterator<T> dfsIterator(PersistentDataNode<T> root) {
        return new DfsIterator<>(root);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Creates a new {@link BfsIterator} seeded with the current root.</p>
     */
    @Override
    public PersistentIterator<T> bfsIterator() {
        return new BfsIterator<>(root);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Creates a new {@link BfsIterator} seeded with the provided {@code root}.</p>
     */
    @Override
    public PersistentIterator<T> bfsIterator(PersistentDataNode<T> root) {
        return new BfsIterator<>(root);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Creates a new {@link InOrderIterator} seeded with the current root.</p>
     */
    @Override
    public PersistentIterator<T> inOrderIterator() {
        return new InOrderIterator<>(root);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Creates a new {@link InOrderIterator} seeded with the provided {@code root}.</p>
     */
    @Override
    public PersistentIterator<T> inOrderIterator(BinaryDataNode<T> root) {
        return new InOrderIterator<>(root);
    }
}