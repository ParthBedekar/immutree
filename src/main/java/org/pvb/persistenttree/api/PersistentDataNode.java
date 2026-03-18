package org.pvb.persistenttree.api;

import java.util.List;

/**
 * Represents a single, <b>immutable node</b> within a {@link PersistentTree}.
 *
 * <p>Every node in the persistent tree implements this interface. A node holds three
 * pieces of information: a unique identity ({@link NodeID}), a typed data payload,
 * and an ordered list of child nodes. Because the tree is persistent, a {@code PersistentDataNode}
 * instance is never mutated in-place — operations that would logically "change" a node
 * instead produce a brand-new node (and a new path of ancestor nodes up to a new root),
 * while the original instance remains completely intact and reachable from prior versions.</p>
 *
 * <h2>Structural Sharing</h2>
 * <p>
 * When a node deep in the tree is modified, only the nodes along the path from the root
 * down to that node are recreated. All other subtrees are shared by reference between
 * the old and new versions. This means the children list returned by {@link #getChildren()}
 * may contain nodes that are also present in older versions of the tree, which is safe
 * precisely because nodes are immutable.
 * </p>
 *
 * <h2>Concrete Implementation</h2>
 * <p>
 * The standard concrete implementation is
 * {@link org.pvb.persistenttree.core.PDataNode}, which stores the children in an
 * {@link java.util.ArrayList} and exposes them through an unmodifiable view.
 * </p>
 *
 * <h2>Custom Implementations</h2>
 * <p>
 * Callers may provide their own implementations of this interface for specialised use-cases
 * (e.g., nodes carrying extra metadata). However, the built-in tree operations
 * ({@link PersistentTree#update}, {@link PersistentTree#addChild}, etc.) will always produce
 * instances of {@link org.pvb.persistenttree.core.PDataNode}, so any custom implementation
 * must be compatible with the existing API contract.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * PersistentDataNode<String> root = tree.getRoot();
 *
 * // Read the data payload
 * String value = root.getData();
 *
 * // Stable identity — same ID across all versions that share this node
 * NodeID id = root.getID();
 *
 * // Traverse children (unmodifiable; safe to iterate)
 * for (PersistentDataNode<String> child : root.getChildren()) {
 *     System.out.println(child.getData());
 * }
 * }</pre>
 *
 * @param <T> the type of data payload stored in this node
 *
 * @see org.pvb.persistenttree.core.PDataNode
 * @see NodeID
 * @see PersistentTree
 */
public interface PersistentDataNode<T> {

     /**
      * Returns the data payload stored in this node.
      *
      * <p>The returned value is the object that was supplied when the node was created
      * (via {@link PersistentTree#addChild(NodeID, Object)}) or last updated
      * (via {@link PersistentTree#update(NodeID, Object)}). The data itself is not
      * defensively copied; if {@code T} is a mutable type, callers should take care
      * not to mutate the returned instance, as doing so would silently affect all
      * versions that share this node.</p>
      *
      * @return the data stored in this node; may be {@code null} if {@code null}
      *         was explicitly stored
      */
     T getData();

     /**
      * Returns the {@link NodeID} that uniquely identifies this node across all
      * versions of the tree.
      *
      * <p>The identity of a node is assigned at creation time and never changes.
      * When a node is updated (its data replaced), the new node that is produced
      * retains the same {@link NodeID}, making it possible to locate the "same conceptual
      * node" across different versions by its ID.</p>
      *
      * @return the non-null {@link NodeID} of this node
      */
     NodeID getID();

     /**
      * Returns an unmodifiable, ordered list of this node's direct children.
      *
      * <p>The ordering of children reflects the order in which they were added via
      * {@link PersistentTree#addChild(NodeID, Object)}. New children are always appended
      * to the end of the list.</p>
      *
      * <p>The returned list is an unmodifiable view (backed by the internal list held
      * in {@link org.pvb.persistenttree.core.PDataNode}). Attempting to mutate the
      * returned list will throw {@link UnsupportedOperationException}.</p>
      *
      * <p>A node with no children returns an empty list, never {@code null}.</p>
      *
      * @return a non-null, unmodifiable {@link List} of direct child nodes, possibly empty
      */
     List<PersistentDataNode<T>> getChildren();
}