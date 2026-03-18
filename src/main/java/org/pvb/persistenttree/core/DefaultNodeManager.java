package org.pvb.persistenttree.core;

import org.pvb.persistenttree.api.Exceptions.NodeNotFoundException;
import org.pvb.persistenttree.api.NodeID;
import org.pvb.persistenttree.api.NodeManager;
import org.pvb.persistenttree.api.PersistentDataNode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The default implementation of {@link NodeManager}, providing depth-first node
 * lookup and predicate-based search over the <em>current version</em> of a
 * {@link org.pvb.persistenttree.core.PersistentNTree}.
 *
 * <p>An instance of {@code DefaultNodeManager} is created by
 * {@link PersistentNTree} at construction time and kept in sync with the tree's
 * evolving root via {@link #updateRoot(PersistentDataNode)}. Every mutating tree
 * operation ({@code update}, {@code addChild}, {@code removeNode}) calls
 * {@link #updateRoot(PersistentDataNode)} with the freshly produced root before
 * returning to the caller, ensuring that subsequent manager queries always reflect
 * the latest tree state.</p>
 *
 * <h2>Root Tracking</h2>
 * <p>
 * The {@code root} field is the only mutable state in this class. It is a plain,
 * unsynchronized reference that is updated on every structural tree modification.
 * This design keeps the hot path allocation-free (no wrapper objects) but means
 * the class is <b>not thread-safe</b>.
 * </p>
 *
 * <h2>Visibility</h2>
 * <p>
 * The constructor is package-private, so {@code DefaultNodeManager} can only be
 * instantiated from within the {@code org.pvb.persistenttree.core} package. Callers
 * access it through the {@link NodeManager} interface via
 * {@link org.pvb.persistenttree.api.PersistentTree#getNodeManager()}.
 * </p>
 *
 * @param <T> the type of data stored in the tree nodes managed by this instance
 *
 * @see NodeManager
 * @see PersistentNTree
 */
public class DefaultNodeManager<T> implements NodeManager<T> {

    /**
     * The root node of the current version of the tree. Updated on every structural
     * modification by {@link #updateRoot(PersistentDataNode)}.
     */
    PersistentDataNode<T> root;

    /**
     * Constructs a {@code DefaultNodeManager} tracking the given initial root.
     *
     * <p>Called once from {@link PersistentNTree#PersistentNTree(Object)} during tree
     * construction. The provided root is the single-node tree containing the seed data.</p>
     *
     * @param root the initial root node; must not be {@code null}
     */
    DefaultNodeManager(PersistentDataNode<T> root) {
        this.root = root;
    }

    /**
     * Recursively searches the subtree rooted at {@code root} for the node whose
     * {@link NodeID} equals {@code id}, using depth-first traversal.
     *
     * <p>The search proceeds as follows:</p>
     * <ol>
     *   <li>If {@code root} is {@code null}, return {@code null} (base case for missing
     *       nodes / exhausted branches).</li>
     *   <li>If the current node's ID matches {@code id}, return the node immediately.</li>
     *   <li>Otherwise, recurse into each child in order. Return the first non-null
     *       result encountered.</li>
     *   <li>If all children are exhausted without a match, return {@code null}.</li>
     * </ol>
     *
     * <p><b>Complexity:</b> O(n) in the worst case, where n is the number of nodes
     * in the subtree. Early return on match can make it faster in practice.</p>
     *
     * @param root the subtree root to search within; may be {@code null}
     * @param id   the target {@link NodeID}; must not be {@code null}
     * @return the matching {@link PersistentDataNode}, or {@code null} if not found
     */
    private PersistentDataNode<T> helper(PersistentDataNode<T> root, NodeID id) {
        if (root == null) {
            return null;
        }
        if (root.getID().equals(id)) {
            return root;
        }
        for (PersistentDataNode<T> node : root.getChildren()) {
            PersistentDataNode<T> result = helper(node, id);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    /**
     * Updates the internal root reference to the given node.
     *
     * <p>This method is called by {@link PersistentNTree} after every structural
     * modification (update, addChild, removeNode). It ensures that subsequent calls
     * to {@link #getNode(NodeID)} and {@link #findAll(Predicate)} operate over the
     * most recent version of the tree.</p>
     *
     * <p>This method is package-private and is not part of the public {@link NodeManager}
     * contract. It must not be called by external code.</p>
     *
     * @param newRoot the new root of the current tree version; may be {@code null}
     *                in the edge case where the root node itself was removed
     */
    void updateRoot(PersistentDataNode<T> newRoot) {
        this.root = newRoot;
    }

    /**
     * {@inheritDoc}
     *
     * <h4>Implementation Details</h4>
     * <p>
     * Delegates to {@link #helper(PersistentDataNode, NodeID)}, which performs a
     * recursive depth-first search starting from the current {@link #root}.
     * If the helper returns {@code null} (node not found), a
     * {@link NodeNotFoundException} is thrown.
     * </p>
     *
     * @throws NodeNotFoundException if no node with the given {@code id} is found
     *                               in the current version of the tree
     */
    @Override
    public PersistentDataNode<T> getNode(NodeID id) {
        PersistentDataNode<T> node = helper(this.root, id);
        if (node == null) {
            throw new NodeNotFoundException("Node not found with ID: " + id.id());
        }
        return node;
    }

    /**
     * {@inheritDoc}
     *
     * <h4>Implementation Details</h4>
     * <p>
     * Delegates directly to {@link #findAll(Predicate, PersistentDataNode)} using
     * the current {@link #root} as the starting node.
     * </p>
     */
    @Override
    public List<PersistentDataNode<T>> findAll(Predicate<T> predicate) {
        return findAll(predicate, this.root);
    }

    /**
     * {@inheritDoc}
     *
     * <h4>Implementation Details</h4>
     * <p>
     * Creates a {@link DfsIterator} anchored at {@code node}, then iterates through
     * every node in the subtree exactly once. For each node, the predicate is evaluated
     * against {@link PersistentDataNode#getData()}. Nodes for which the predicate
     * returns {@code true} are collected into a newly allocated {@link ArrayList} and
     * returned.
     * </p>
     *
     * <p>The result list preserves DFS visitation order: a parent node always appears
     * before its children, and children appear in insertion order.</p>
     */
    @Override
    public List<PersistentDataNode<T>> findAll(Predicate<T> predicate, PersistentDataNode<T> node) {
        DfsIterator<T> dfsIterator = new DfsIterator<>(node);
        List<PersistentDataNode<T>> resultant = new ArrayList<>();
        while (dfsIterator.hasNext()) {
            PersistentDataNode<T> curr = dfsIterator.next();
            boolean found = predicate.test(curr.getData());
            if (found) {
                resultant.add(curr);
            }
        }
        return resultant;
    }
}