package org.pvb.persistenttree.api;

import org.pvb.persistenttree.api.Exceptions.NodeNotFoundException;

/**
 * The primary abstraction for a <b>persistent (fully immutable) N-ary tree</b> whose
 * every mutating operation produces a new logical version of the tree while leaving
 * all prior versions structurally intact and reachable.
 *
 * <h2>What "Persistent" Means</h2>
 * <p>
 * In the context of this library, <em>persistent</em> refers to the functional-programming
 * sense of the word: the data structure preserves its previous versions after modifications.
 * This is achieved through <b>path-copying</b> (also called <em>structural sharing</em>):
 * </p>
 * <ul>
 *   <li>When a node is updated, added, or removed, only the nodes along the path from the
 *       root down to the affected node are copied into new instances.</li>
 *   <li>Every subtree that is not on that path is <em>reused by reference</em> in the new
 *       version, keeping memory overhead proportional to the depth of the change rather
 *       than the total size of the tree.</li>
 *   <li>All previously obtained references to {@link PersistentDataNode} instances remain
 *       valid and continue to reflect the state of the tree at the moment they were
 *       obtained.</li>
 * </ul>
 *
 * <h2>Versioning</h2>
 * <p>
 * Callers can manually snapshot any state of the tree by calling
 * {@link VersionManager#addVersion(Object, PersistentDataNode)} on the {@link VersionManager}
 * returned by {@link #getVersionManager()}. Saved versions can be retrieved at any time
 * via {@link VersionManager#getVersion(Object)}, allowing time-travel-style access to
 * historical tree states.
 * </p>
 *
 * <h2>Component Architecture</h2>
 * <p>
 * A {@code PersistentTree} is composed of two collaborating managers:
 * </p>
 * <ul>
 *   <li>{@link NodeManager} — provides node lookup ({@link NodeManager#getNode(NodeID)})
 *       and predicate-based search ({@link NodeManager#findAll(java.util.function.Predicate)})
 *       over the current version of the tree.</li>
 *   <li>{@link VersionManager} — records and retrieves named snapshots of the tree,
 *       keyed by caller-supplied values of type {@code K}.</li>
 * </ul>
 *
 * <h2>Typical Usage</h2>
 * <pre>{@code
 * // 1. Create a tree via the factory (N-ary is the default type)
 * PersistentTree<String, String> tree =
 *         TreeFactory.createTree("Root", TreeType.N_ARY);
 *
 * // 2. Add children — each call returns the NodeID of the newly created node
 * NodeID child1 = tree.addChild(tree.getRoot().getID(), "Child-1");
 * NodeID child2 = tree.addChild(tree.getRoot().getID(), "Child-2");
 *
 * // 3. Snapshot this version before mutating further
 * tree.getVersionManager().addVersion("v1", tree.getRoot());
 *
 * // 4. Update a node
 * tree.update(child1, "Updated-Child-1");
 *
 * // 5. Retrieve the previous version — child1 still holds "Child-1" here
 * PersistentDataNode<String> v1Root =
 *         tree.getVersionManager().getVersion("v1").orElseThrow();
 *
 * // 6. Traverse the current version depth-first
 * PersistentIterator<String> it = tree.dfsIterator();
 * while (it.hasNext()) {
 *     System.out.println(it.next().getData());
 * }
 * }</pre>
 *
 * <h2>Exception Behaviour</h2>
 * <p>
 * All mutating operations ({@link #update}, {@link #addChild}, {@link #removeNode}) throw
 * {@link NodeNotFoundException} — an unchecked exception — when the target or parent node
 * cannot be found in the current version of the tree. Callers are encouraged to either
 * validate IDs before use or handle this exception at an appropriate boundary.
 * </p>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * The standard implementation ({@link org.pvb.persistenttree.core.PersistentNTree}) is
 * <b>not thread-safe</b>. Although the node objects themselves are immutable and can be
 * shared freely across threads, the {@code root} field inside {@code PersistentNTree} is
 * mutable and written on every mutating operation without synchronization. External
 * locking (or a copy-per-thread strategy) is required for concurrent use.
 * </p>
 *
 * @param <K> the type used as the version key when snapshots are stored in
 *            the {@link VersionManager}; commonly {@link String} or {@link Integer}
 * @param <T> the type of the data payload stored in each tree node
 *
 * @see org.pvb.persistenttree.core.PersistentNTree
 * @see org.pvb.persistenttree.core.TreeFactory
 * @see NodeManager
 * @see VersionManager
 * @see PersistentDataNode
 * @see NodeID
 */
public interface PersistentTree<K, T> {

    /**
     * Returns the {@link NodeManager} that provides node-level query operations
     * over the <em>current version</em> of this tree.
     *
     * <p>The {@link NodeManager} always reflects the most recent root, even after
     * subsequent calls to {@link #update}, {@link #addChild}, or {@link #removeNode}.
     * It is updated automatically as part of every mutating operation.</p>
     *
     * <p>Typical operations exposed by the {@link NodeManager}:</p>
     * <ul>
     *   <li>{@link NodeManager#getNode(NodeID)} — O(n) lookup by ID</li>
     *   <li>{@link NodeManager#findAll(java.util.function.Predicate)} — DFS scan
     *       matching nodes whose data satisfies a predicate</li>
     * </ul>
     *
     * @return the non-null {@link NodeManager} associated with this tree
     */
    NodeManager<T> getNodeManager();

    /**
     * Returns the {@link VersionManager} that manages named snapshots of this tree.
     *
     * <p>The {@link VersionManager} is <em>not</em> automatically populated — callers
     * must explicitly call {@link VersionManager#addVersion(Object, PersistentDataNode)}
     * to record a snapshot. Doing so stores the current root node under the supplied key,
     * allowing the tree state to be retrieved later via
     * {@link VersionManager#getVersion(Object)}.</p>
     *
     * @return the non-null {@link VersionManager} associated with this tree
     */
    VersionManager<K, T> getVersionManager();

    /**
     * Replaces the data payload of the node identified by {@code id} and returns the
     * new root of the updated tree.
     *
     * <h4>Persistence Mechanism</h4>
     * <p>
     * Only the nodes on the path from the root down to the target node are copied.
     * All other subtrees are shared between the old and new version. The internal
     * {@code root} field of the tree is atomically (from a single-thread perspective)
     * replaced with the new root, and the {@link NodeManager} is updated to reflect
     * the new root.
     * </p>
     *
     * <h4>Identifying the Target Node</h4>
     * <p>
     * The {@code id} parameter must be a {@link NodeID} previously obtained from
     * {@link PersistentDataNode#getID()} or returned by {@link #addChild(NodeID, Object)}.
     * The search is performed depth-first over the current version of the tree.
     * If the ID does not match any node, {@link NodeNotFoundException} is thrown and
     * the tree is left unchanged.
     * </p>
     *
     * @param id   the {@link NodeID} of the node whose data should be replaced;
     *             must not be {@code null}
     * @param data the new data to store; may be {@code null} if the type {@code T}
     *             permits it
     * @return the new root {@link PersistentDataNode} of the updated tree version
     * @throws NodeNotFoundException if no node with the given {@code id} exists
     *                               in the current version of the tree
     */
    PersistentDataNode<T> update(NodeID id, T data);

    /**
     * Adds a new child node containing {@code data} to the node identified by
     * {@code parentID}, and returns the {@link NodeID} of the newly created child.
     *
     * <h4>Persistence Mechanism</h4>
     * <p>
     * A new leaf {@link PersistentDataNode} is created with a freshly generated
     * {@link NodeID}. The parent node is then recreated with the new child appended
     * to the end of its children list. All ancestors up to the root are similarly
     * recreated to reflect the updated structure. The internal root of this tree and
     * the associated {@link NodeManager} are both updated to point to the new root.
     * </p>
     *
     * <h4>Child Ordering</h4>
     * <p>
     * Children are maintained in insertion order. The new child is always appended
     * after all existing children of the parent.
     * </p>
     *
     * @param parentID the {@link NodeID} of the node that will become the parent;
     *                 must not be {@code null}
     * @param data     the data payload for the new child node; may be {@code null}
     *                 if {@code T} permits it
     * @return the {@link NodeID} assigned to the newly created child node;
     *         callers should retain this value if they intend to target the node later
     * @throws NodeNotFoundException if no node with the given {@code parentID} exists
     *                               in the current version of the tree
     */
    NodeID addChild(NodeID parentID, T data);

    /**
     * Returns the root node of the <em>current version</em> of this tree.
     *
     * <p>The root is the single node with no parent. Every other node in the tree
     * is reachable from the root by following children. After any mutating operation
     * ({@link #update}, {@link #addChild}, {@link #removeNode}), this method returns
     * the newly produced root of that version.</p>
     *
     * <p>The returned node is immutable and can be safely retained as a reference to
     * the tree state at the time of the call.</p>
     *
     * @return the non-null root {@link PersistentDataNode} of the current tree version
     */
    PersistentDataNode<T> getRoot();

    /**
     * Returns a depth-first pre-order {@link PersistentIterator} starting from the
     * current root of this tree.
     *
     * <p>DFS pre-order visits a node before any of its children, then recurses into
     * children left-to-right (in insertion order). This traversal order is well-suited
     * for producing hierarchical textual representations of the tree.</p>
     *
     * <p>The iterator is a snapshot of the tree structure at the moment it is created.
     * Mutations to the tree after calling this method do not affect an already-created
     * iterator (because all node objects are immutable).</p>
     *
     * <p>The iterator does not support {@link java.util.Iterator#remove()}.</p>
     *
     * @return a new, non-null {@link PersistentIterator} that traverses nodes in
     *         depth-first pre-order starting from the current root
     * @see #dfsIterator(PersistentDataNode)
     */
    PersistentIterator<T> dfsIterator();

    /**
     * Returns a depth-first pre-order {@link PersistentIterator} starting from the
     * specified subtree root.
     *
     * <p>Identical in behaviour to {@link #dfsIterator()} but begins traversal at the
     * provided {@code root} node rather than the current tree root. This is useful for
     * restricting a search or traversal to a particular subtree, or for iterating over
     * a historical version's subtree by passing a node obtained from an older root.</p>
     *
     * @param root the {@link PersistentDataNode} to use as the starting point of the
     *             traversal; must not be {@code null}
     * @return a new, non-null {@link PersistentIterator} that traverses nodes in
     *         depth-first pre-order starting from {@code root}
     */
    PersistentIterator<T> dfsIterator(PersistentDataNode<T> root);

    /**
     * Returns a breadth-first (level-order) {@link PersistentIterator} starting from
     * the current root of this tree.
     *
     * <p>BFS visits all nodes at depth 0 (the root), then all nodes at depth 1
     * (children of root), then depth 2, and so on. Within a given depth, nodes are
     * visited in insertion (left-to-right) order. This traversal is typically used
     * when proximity to the root is meaningful (e.g., finding the shallowest node
     * matching a criterion).</p>
     *
     * <p>The iterator is a snapshot of the tree structure at the moment it is created.
     * Mutations to the tree after calling this method do not affect an already-created
     * iterator.</p>
     *
     * <p>The iterator does not support {@link java.util.Iterator#remove()}.</p>
     *
     * @return a new, non-null {@link PersistentIterator} that traverses nodes in
     *         breadth-first order starting from the current root
     * @see #bfsIterator(PersistentDataNode)
     */
    PersistentIterator<T> bfsIterator();

    /**
     * Returns a breadth-first (level-order) {@link PersistentIterator} starting from
     * the specified subtree root.
     *
     * <p>Identical in behaviour to {@link #bfsIterator()} but begins traversal at the
     * provided {@code root} node rather than the current tree root.</p>
     *
     * @param root the {@link PersistentDataNode} to use as the starting point of the
     *             traversal; must not be {@code null}
     * @return a new, non-null {@link PersistentIterator} that traverses nodes in
     *         breadth-first order starting from {@code root}
     */
    PersistentIterator<T> bfsIterator(PersistentDataNode<T> root);

    /**
     * Removes the node identified by {@code id} — along with its entire subtree —
     * from the current version of the tree, and returns the new root.
     *
     * <h4>Subtree Removal</h4>
     * <p>
     * When a node is removed, all of its descendants are also removed. There is no
     * "reparenting" of children; the entire sub-DAG rooted at the target node is
     * dropped from the new version.
     * </p>
     *
     * <h4>Persistence Mechanism</h4>
     * <p>
     * The removal works by recursively rebuilding the path from the root to the parent
     * of the deleted node. Only ancestors of the removed node are recreated; all other
     * subtrees are shared. The internal root and {@link NodeManager} are updated to
     * reflect the new structure.
     * </p>
     *
     * <h4>Root Removal</h4>
     * <p>
     * Removing the root node results in a tree with a {@code null} root. Callers should
     * guard against this case before calling further operations.
     * </p>
     *
     * @param id the {@link NodeID} of the node to remove; must not be {@code null}
     * @return the new root {@link PersistentDataNode} after deletion; may be
     *         {@code null} if the root itself was removed
     * @throws NodeNotFoundException if no node with the given {@code id} exists
     *                               in the current version of the tree
     */
    PersistentDataNode<T> removeNode(NodeID id);

    /**
     * Removes the node identified by {@code id} — along with its entire subtree —
     * from within the subtree rooted at {@code node}, and returns the updated subtree root.
     *
     * <p>This overload differs from {@link #removeNode(NodeID)} in two important ways:</p>
     * <ol>
     *   <li>The search is limited to the subtree rooted at {@code node}, rather than
     *       the full tree. This allows targeted removal within an arbitrary subtree,
     *       including nodes obtained from historical versions.</li>
     *   <li>The internal tree root and {@link NodeManager} are <b>not</b> updated.
     *       The caller receives the updated subtree root directly and is responsible
     *       for integrating it into a wider structure if needed.</li>
     * </ol>
     *
     * @param id   the {@link NodeID} of the node to remove; must not be {@code null}
     * @param node the root of the subtree to search within; must not be {@code null}
     * @return the updated subtree root after deletion; may be {@code null} if
     *         {@code node} itself was the removed node
     * @throws NodeNotFoundException if no node with the given {@code id} exists
     *                               within the subtree rooted at {@code node}
     */
    PersistentDataNode<T> removeNode(NodeID id, PersistentDataNode<T> node);
}