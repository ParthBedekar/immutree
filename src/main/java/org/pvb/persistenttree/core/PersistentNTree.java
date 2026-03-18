package org.pvb.persistenttree.core;

import org.pvb.persistenttree.api.*;
import org.pvb.persistenttree.api.Exceptions.NodeNotFoundException;

import java.util.ArrayList;
import java.util.List;

/**
 * The primary implementation of {@link PersistentTree}, providing a fully persistent
 * N-ary tree backed by <b>path-copying</b> (structural sharing).
 *
 * <p>An N-ary tree is a rooted tree in which each node may have any number of children.
 * This implementation supports the core mutating operations (update, add, remove) as
 * well as DFS and BFS traversal. Every mutating operation produces a logically new
 * version of the tree by recreating only the nodes along the path from the root to the
 * affected node, while all unmodified subtrees are reused by reference.</p>
 *
 * <h2>Persistence via Path-Copying</h2>
 * <p>
 * Consider a tree where node {@code D} is to be updated, and its ancestors are
 * {@code root → B → D}. The update proceeds as follows:
 * </p>
 * <ol>
 *   <li>A new {@code PDataNode} for {@code D} is created with the new data and the
 *       same children as the original {@code D}.</li>
 *   <li>A new {@code PDataNode} for {@code B} is created with the same data, but its
 *       children list is a new list where the old {@code D} is replaced with the new
 *       {@code D}.</li>
 *   <li>A new root is created with the same data but with the new {@code B} in its
 *       children list.</li>
 *   <li>All other subtrees (siblings of {@code D}, siblings of {@code B}, etc.) are
 *       reused unchanged.</li>
 * </ol>
 * <p>
 * The old root remains intact. If a caller held a reference to the old root, they
 * can still traverse the prior version of the tree.
 * </p>
 *
 * <h2>Internal State</h2>
 * <p>
 * The class maintains three mutable fields that are updated after every structural
 * modification:
 * </p>
 * <ul>
 *   <li>{@link #root} — the root of the current version.</li>
 *   <li>{@link #nodeManager} — a {@link DefaultNodeManager} that always tracks the
 *       current root.</li>
 *   <li>{@link #versionManager} — a {@link DefaultVersionManager} for manual
 *       snapshotting; <b>not</b> auto-updated by mutations.</li>
 * </ul>
 *
 * <h2>Factory Access</h2>
 * <p>
 * The constructor is package-private. Use {@link TreeFactory#createTree(Object,
 * org.pvb.persistenttree.api.Enums.TreeType)} to instantiate a tree:
 * </p>
 * <pre>{@code
 * PersistentTree<String, String> tree =
 *         TreeFactory.createTree("Root", TreeType.N_ARY);
 * }</pre>
 *
 * <h2>Performance Characteristics</h2>
 * <ul>
 *   <li>Update: O(n) — full path search + path copy</li>
 *   <li>Add child: O(n) — full path search + path copy</li>
 *   <li>Remove node: O(n) — full tree scan + path copy</li>
 *   <li>DFS / BFS traversal: O(n)</li>
 *   <li>Node lookup ({@link NodeManager#getNode}): O(n)</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is <b>not thread-safe</b>. The fields {@link #root}, and the root
 * references inside {@link #nodeManager} and {@link #versionManager}, are all
 * unsynchronized. External locking is required for concurrent use.
 * </p>
 *
 * @param <K> the version key type used by the {@link VersionManager}
 * @param <T> the type of data stored in each tree node
 *
 * @see PersistentTree
 * @see TreeFactory
 * @see PDataNode
 */
public class PersistentNTree<K, T> implements PersistentTree<K, T> {

    /**
     * The root node of the current tree version. Updated in-place (from this object's
     * perspective) after every call to {@link #update}, {@link #addChild}, or
     * {@link #removeNode}.
     */
    PersistentDataNode<T> root;

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
     * Constructs a new {@code PersistentNTree} seeded with a single root node
     * containing the given data.
     *
     * <p>The initial tree consists of exactly one node (the root) with no children.
     * The root is assigned a randomly generated {@link NodeID}. A {@link DefaultNodeManager}
     * and a {@link DefaultVersionManager} are also initialized.</p>
     *
     * <p>This constructor is package-private; use
     * {@link TreeFactory#createTree(Object, org.pvb.persistenttree.api.Enums.TreeType)}
     * to create trees from application code.</p>
     *
     * @param data the data payload for the root node; may be {@code null}
     */
    PersistentNTree(T data) {
        root = new PDataNode<>(NodeID.generateUUID(), data);
        nodeManager = new DefaultNodeManager<>(root);
        versionManager = new DefaultVersionManager<>();
    }

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

    /**
     * Recursively searches for the node with the given {@code id} and, when found,
     * returns a new node with the same {@link NodeID} and children but with
     * {@code newData} as the payload. Ancestors of the modified node are also
     * recreated to reflect the change (path-copying).
     *
     * <h4>Algorithm</h4>
     * <ol>
     *   <li>If {@code root} is {@code null}, return {@code null} (exhausted branch).</li>
     *   <li>If this node's ID matches {@code id}, create and return a new
     *       {@link PDataNode} with {@code newData}, keeping the same ID and children.</li>
     *   <li>Otherwise, recurse into each child:
     *     <ul>
     *       <li>If the recursive call returns the same child reference (no change in
     *           that subtree), add the original child to the new children list.</li>
     *       <li>If a different node is returned (the change was within this subtree),
     *           add the new node and mark {@code updated = true}.</li>
     *     </ul>
     *   </li>
     *   <li>If no child was updated ({@code updated == false}), return the original
     *       {@code root} unchanged (preserves structural sharing at this level).</li>
     *   <li>Otherwise, return a new {@link PDataNode} for this ancestor with the
     *       updated children list.</li>
     * </ol>
     *
     * <p><b>Complexity:</b> O(n) in the worst case.</p>
     *
     * @param id      the target node's {@link NodeID}
     * @param root    the current subtree root to search within
     * @param newData the new data to assign to the target node
     * @return the updated subtree root (a new node if anything changed, the original
     *         node if nothing changed within this subtree)
     */
    private PersistentDataNode<T> updateHelper(NodeID id, PersistentDataNode<T> root, T newData) {
        if (root == null) {
            return null;
        }
        if (root.getID().equals(id)) {
            return new PDataNode<>(id, newData, root.getChildren());
        }

        List<PersistentDataNode<T>> updatedList = new ArrayList<>(0);
        boolean updated = false;

        for (PersistentDataNode<T> n : root.getChildren()) {
            PersistentDataNode<T> node = updateHelper(id, n, newData);

            if (node == n) {
                updatedList.add(n);
            } else {
                updatedList.add(node);
                updated = true;
            }
        }

        if (!updated) {
            return root;
        } else {
            return new PDataNode<>(root.getID(), root.getData(), updatedList);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <h4>Implementation Details</h4>
     * <p>
     * Delegates to {@link #updateHelper(NodeID, PersistentDataNode, Object)}.
     * If the returned root is reference-equal ({@code ==}) to the current root,
     * the node was not found and {@link NodeNotFoundException} is thrown. Otherwise,
     * the internal {@link #root} and the {@link NodeManager}'s root are both updated
     * to the new root.
     * </p>
     *
     * @throws NodeNotFoundException if no node with {@code id} exists in the tree
     */
    @Override
    public PersistentDataNode<T> update(NodeID id, T data) {
        PersistentDataNode<T> newRoot = updateHelper(id, this.root, data);

        if (newRoot == root) {
            throw new NodeNotFoundException("Node not found with ID: " + id.id());
        }

        ((DefaultNodeManager<T>) nodeManager).updateRoot(newRoot);
        this.root = newRoot;
        return newRoot;
    }

    /**
     * Internal carrier for the result of {@link #addChildHelper}, bundling the
     * newly assigned {@link NodeID} with the updated subtree root.
     *
     * <p>Using a record eliminates the need for a mutable out-parameter or a
     * two-element array while keeping the helper method's return type expressive.</p>
     *
     * @param id    the {@link NodeID} assigned to the new child node
     * @param child the updated subtree root that now contains the new child
     */
    private record NewChild<T>(NodeID id, PersistentDataNode<T> child) {}

    /**
     * Recursively searches for the parent node with the given {@code parentID} and,
     * when found, appends a new child node to it. Returns a {@link NewChild} record
     * containing the new child's ID and the updated subtree root, or {@code null}
     * if {@code parentID} was not found within this subtree.
     *
     * <h4>Algorithm</h4>
     * <ol>
     *   <li>If {@code root} is {@code null}, return {@code null}.</li>
     *   <li>If this node's ID matches {@code parentID}:
     *     <ul>
     *       <li>Create a new child {@link PDataNode} with a freshly generated
     *           {@link NodeID} and {@code data}.</li>
     *       <li>Append it to a copy of the current children list.</li>
     *       <li>Return a {@link NewChild} with the new child's ID and a recreated
     *           parent node.</li>
     *     </ul>
     *   </li>
     *   <li>Otherwise, recurse into each child. If a non-null {@link NewChild} is
     *       returned, the parent was found somewhere in that subtree:
     *     <ul>
     *       <li>Replace the old child with {@code result.child()} in the accumulated
     *           children list.</li>
     *       <li>Propagate the new child's ID upward by returning a new
     *           {@link NewChild} with the updated subtree root for this level.</li>
     *     </ul>
     *   </li>
     *   <li>If no child subtree contained the parent, return {@code null}.</li>
     * </ol>
     *
     * @param parentID the {@link NodeID} of the parent to search for
     * @param data     the data for the new child node
     * @param root     the current subtree root to search within
     * @return a {@link NewChild} containing the new child's ID and the updated subtree
     *         root, or {@code null} if {@code parentID} was not found in this subtree
     */
    private NewChild<T> addChildHelper(NodeID parentID, T data, PersistentDataNode<T> root) {
        if (root == null) return null;

        if (root.getID().equals(parentID)) {
            List<PersistentDataNode<T>> list = new ArrayList<>(root.getChildren());

            NodeID newId = NodeID.generateUUID();
            PersistentDataNode<T> newNode = new PDataNode<>(newId, data);

            list.add(newNode);

            return new NewChild<>(newId,
                    new PDataNode<>(parentID, root.getData(), list));
        }

        List<PersistentDataNode<T>> updatedList = new ArrayList<>();
        boolean updated = false;
        NodeID foundId = null;

        for (PersistentDataNode<T> n : root.getChildren()) {
            NewChild<T> result = addChildHelper(parentID, data, n);

            if (result == null) {
                updatedList.add(n);
            } else {
                updatedList.add(result.child());
                foundId = result.id();
                updated = true;
            }
        }

        if (!updated) return null;

        return new NewChild<>(foundId,
                new PDataNode<>(root.getID(), root.getData(), updatedList));
    }

    /**
     * {@inheritDoc}
     *
     * <h4>Implementation Details</h4>
     * <p>
     * Delegates to {@link #addChildHelper(NodeID, Object, PersistentDataNode)}.
     * A {@code null} result means the parent was not found, and
     * {@link NodeNotFoundException} is thrown. Otherwise, the internal {@link #root}
     * and {@link NodeManager} are updated to the new root.
     * </p>
     *
     * @throws NodeNotFoundException if no node with {@code parentID} exists in the tree
     */
    @Override
    public NodeID addChild(NodeID parentID, T data) {
        NewChild<T> result = addChildHelper(parentID, data, this.root);

        if (result == null) {
            throw new NodeNotFoundException("Parent node not found with ID: " + parentID.id());
        }

        this.root = result.child();
        ((DefaultNodeManager<T>) nodeManager).updateRoot(this.root);

        return result.id();
    }

    /**
     * Instance-level flag used by {@link #removeHelper} to communicate upward through
     * the recursive call stack that the target node was successfully located and
     * removed. This avoids the need for a wrapper return type in the remove path.
     *
     * <p><b>Note:</b> This flag makes the remove operation <b>non-reentrant</b>. If
     * the tree were used concurrently (which is not supported), simultaneous calls
     * to {@link #removeNode} could corrupt this flag.
     * </p>
     */
    private boolean lastRemoveFound;

    /**
     * Recursively searches for the node with the given {@code id} and removes it
     * (along with its entire subtree) from the tree. Sets {@link #lastRemoveFound}
     * to {@code true} when the node is found.
     *
     * <h4>Algorithm</h4>
     * <ol>
     *   <li>If {@code node} is {@code null}, return {@code null}.</li>
     *   <li>If this node's ID matches {@code id}:
     *     <ul>
     *       <li>Set {@link #lastRemoveFound} to {@code true}.</li>
     *       <li>Return {@code null} to signal that this node should be dropped from
     *           its parent's children list.</li>
     *     </ul>
     *   </li>
     *   <li>Recurse into each child:
     *     <ul>
     *       <li>If the result is {@code null} and {@link #lastRemoveFound} is true,
     *           the child was the removed node — skip it (do not add to the new
     *           children list) and mark {@code changed = true}.</li>
     *       <li>If the result is {@code null} and {@link #lastRemoveFound} is still
     *           false, the child was legitimately missing (shouldn't happen with a
     *           well-formed tree) — skip it.</li>
     *       <li>If the result is the same reference as the original child, the node
     *           was not in that subtree — keep the original.</li>
     *       <li>If the result is a new reference, a descendant was removed — use the
     *           new node and mark {@code changed = true}.</li>
     *     </ul>
     *   </li>
     *   <li>If nothing changed, return the original {@code node} (preserves structural
     *       sharing).</li>
     *   <li>Otherwise, return a new {@link PDataNode} for this ancestor with the
     *       updated children list.</li>
     * </ol>
     *
     * @param id   the {@link NodeID} of the node to remove
     * @param node the current subtree root to search within
     * @return the updated subtree root, or {@code null} if this node itself was removed
     */
    private PersistentDataNode<T> removeHelper(NodeID id, PersistentDataNode<T> node) {
        if (node == null) return null;

        if (node.getID().equals(id)) {
            lastRemoveFound = true;
            return null;
        }

        List<PersistentDataNode<T>> updatedList = new ArrayList<>();
        boolean changed = false;

        for (PersistentDataNode<T> child : node.getChildren()) {
            PersistentDataNode<T> result = removeHelper(id, child);

            if (result == null) {
                if (lastRemoveFound) {
                    changed = true;
                } else {
                    updatedList.add(child);
                }
            } else {
                updatedList.add(result);
                if (result != child) changed = true;
            }
        }

        if (!changed) return node;

        return new PDataNode<>(node.getID(), node.getData(), updatedList);
    }

    /**
     * {@inheritDoc}
     *
     * <h4>Implementation Details</h4>
     * <p>
     * Resets {@link #lastRemoveFound} to {@code false}, then delegates to
     * {@link #removeHelper(NodeID, PersistentDataNode)}. If {@link #lastRemoveFound}
     * remains {@code false} after the call, the node was not found and
     * {@link NodeNotFoundException} is thrown. Otherwise, the internal {@link #root}
     * and {@link NodeManager} are updated.
     * </p>
     *
     * @throws NodeNotFoundException if no node with {@code id} exists in the tree
     */
    @Override
    public PersistentDataNode<T> removeNode(NodeID id) {
        lastRemoveFound = false;

        PersistentDataNode<T> n = removeHelper(id, this.root);

        if (!lastRemoveFound) {
            throw new NodeNotFoundException("Node not found with ID: " + id.id());
        }

        this.root = n;
        ((DefaultNodeManager<T>) nodeManager).updateRoot(n);

        return n;
    }

    /**
     * {@inheritDoc}
     *
     * <h4>Implementation Details</h4>
     * <p>
     * Identical in mechanics to {@link #removeNode(NodeID)} except that the search is
     * limited to the subtree rooted at {@code node} and the internal {@link #root} and
     * {@link NodeManager} are <b>not</b> updated. The caller receives the updated
     * subtree root and is responsible for any further integration.
     * </p>
     *
     * @throws NodeNotFoundException if no node with {@code id} is found within
     *                               the subtree rooted at {@code node}
     */
    @Override
    public PersistentDataNode<T> removeNode(NodeID id, PersistentDataNode<T> node) {
        lastRemoveFound = false;

        PersistentDataNode<T> n = removeHelper(id, node);

        if (!lastRemoveFound) {
            throw new NodeNotFoundException("Node not found with ID: " + id.id());
        }

        return n;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the root of the most recently produced version of this tree.
     * This is the node produced by the last mutating operation, or the initial
     * root if no mutations have occurred.</p>
     */
    @Override
    public PersistentDataNode<T> getRoot() {
        return root;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Creates a new {@link DfsIterator} seeded with the current root.</p>
     */
    @Override
    public PersistentIterator<T> dfsIterator() {
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
     * <p>Creates a new {@link DfsIterator} seeded with the provided {@code node}.</p>
     */
    @Override
    public PersistentIterator<T> dfsIterator(PersistentDataNode<T> node) {
        return new DfsIterator<>(node);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Creates a new {@link BfsIterator} seeded with the provided {@code node}.</p>
     */
    @Override
    public PersistentIterator<T> bfsIterator(PersistentDataNode<T> node) {
        return new BfsIterator<>(node);
    }
}