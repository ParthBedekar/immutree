package org.pvb.persistenttree.api;

import java.util.List;
import java.util.function.Predicate;

/**
 * Provides <b>read-only query operations</b> over the nodes of the <em>current version</em>
 * of a {@link PersistentTree}.
 *
 * <p>A {@code NodeManager} is always associated with exactly one tree and always reflects
 * the tree's most recent root. Every call to a mutating tree operation
 * ({@link PersistentTree#update}, {@link PersistentTree#addChild},
 * {@link PersistentTree#removeNode}) causes the tree to update the {@code NodeManager}'s
 * internal root pointer via updateRoot

 * As a result, subsequent calls to {@code NodeManager} methods always operate on the
 * latest version of the tree.</p>
 *
 * <h2>Obtaining a NodeManager</h2>
 * <pre>{@code
 * PersistentTree<String, String> tree = TreeFactory.createTree("Root", TreeType.N_ARY);
 * NodeManager<String> manager = tree.getNodeManager();
 * }</pre>
 *
 * <h2>Node Lookup</h2>
 * <pre>{@code
 * NodeID childId = tree.addChild(tree.getRoot().getID(), "Child");
 * PersistentDataNode<String> child = manager.getNode(childId); // returns the node
 * }</pre>
 *
 * <h2>Predicate Search</h2>
 * <pre>{@code
 * // Find all nodes whose data starts with "Error"
 * List<PersistentDataNode<String>> errorNodes =
 *         manager.findAll(data -> data.startsWith("Error"));
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * The concrete implementation ({@link org.pvb.persistenttree.core.DefaultNodeManager})
 * is not thread-safe. External synchronization is required if the tree is shared across
 * threads.
 * </p>
 *
 * @param <T> the type of data stored in the tree nodes managed by this instance
 *
 * @see org.pvb.persistenttree.core.DefaultNodeManager
 * @see PersistentTree#getNodeManager()
 */
public interface NodeManager<T> {

     /**
      * Returns the {@link PersistentDataNode} with the given {@link NodeID} from the
      * current version of the tree.
      *
      * <p>The lookup is performed via a depth-first traversal starting at the current
      * root. The traversal stops as soon as the target node is found, making the average
      * case faster than a full scan on wide, shallow trees. In the worst case (target is
      * the last leaf visited), the complexity is O(n) where n is the number of nodes in
      * the tree.</p>
      *
      * <p>The returned node is the live instance stored in the tree. Because all nodes
      * are immutable, this reference is safe to retain and share.</p>
      *
      * @param id the {@link NodeID} of the node to find; must not be {@code null}
      * @return the matching {@link PersistentDataNode}; never {@code null}
      * @throws org.pvb.persistenttree.api.Exceptions.NodeNotFoundException if no node
      *         with the given {@code id} exists in the current version of the tree
      */
     PersistentDataNode<T> getNode(NodeID id);

     /**
      * Returns all nodes in the current version of the tree whose data satisfies
      * the given {@link Predicate}, searching the entire tree from the current root.
      *
      * <p>This is a convenience overload of {@link #findAll(Predicate, PersistentDataNode)}
      * that implicitly uses the current root as the starting node. It is equivalent to:</p>
      * <pre>{@code
      * manager.findAll(predicate, tree.getRoot());
      * }</pre>
      *
      * <p>The traversal is performed depth-first (using
      * {@link org.pvb.persistenttree.core.DfsIterator}), visiting every node in the
      * tree exactly once. All nodes for which {@code predicate.test(node.getData())}
      * returns {@code true} are collected into the returned list, in DFS visitation
      * order.</p>
      *
      * <p>If no nodes match, an empty list is returned (never {@code null}).</p>
      *
      * <p><b>Complexity:</b> O(n) — every node is visited exactly once.</p>
      *
      * @param predicate a non-null {@link Predicate} applied to the data of each node;
      *                  nodes for which the predicate returns {@code true} are included
      *                  in the result
      * @return a non-null, possibly empty {@link List} of matching nodes in DFS order
      * @throws NullPointerException if {@code predicate} is {@code null}
      */
     List<PersistentDataNode<T>> findAll(Predicate<T> predicate);

     /**
      * Returns all nodes within the subtree rooted at {@code node} whose data satisfies
      * the given {@link Predicate}.
      *
      * <p>This overload restricts the search to the subtree rooted at {@code node}.
      * This is useful when:</p>
      * <ul>
      *   <li>You already hold a reference to a subtree root and do not want to scan the
      *       entire tree.</li>
      *   <li>You want to search within a historical version by passing an older root.</li>
      * </ul>
      *
      * <p>The traversal is depth-first (using
      * {@link org.pvb.persistenttree.core.DfsIterator}). All nodes for which
      * {@code predicate.test(node.getData())} returns {@code true} are collected into
      * the returned list in DFS visitation order.</p>
      *
      * <p>If no nodes match, an empty list is returned (never {@code null}).</p>
      *
      * <p><b>Complexity:</b> O(k) where k is the number of nodes in the subtree rooted
      * at {@code node}.</p>
      *
      * @param predicate a non-null {@link Predicate} applied to the data of each node;
      *                  nodes for which the predicate returns {@code true} are included
      *                  in the result
      * @param node      the subtree root from which to begin the search;
      *                  must not be {@code null}
      * @return a non-null, possibly empty {@link List} of matching nodes in DFS order
      * @throws NullPointerException if {@code predicate} or {@code node} is {@code null}
      */
     List<PersistentDataNode<T>> findAll(Predicate<T> predicate, PersistentDataNode<T> node);
}