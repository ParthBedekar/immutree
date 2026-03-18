package org.pvb.persistenttree.core;

import org.pvb.persistenttree.api.NodeID;
import org.pvb.persistenttree.api.PersistentDataNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The standard <b>immutable</b> implementation of {@link PersistentDataNode}, used
 * throughout the persistent tree library as the concrete node type.
 *
 * <p>{@code PDataNode} holds three fields: a unique {@link NodeID}, a typed data payload
 * of type {@code T}, and an ordered list of child nodes. All three fields are set at
 * construction time and are never mutated afterwards. Thread-safety of the node object
 * itself is therefore guaranteed — multiple threads can safely read a {@code PDataNode}
 * concurrently without synchronization.</p>
 *
 * <h2>Immutability and Structural Sharing</h2>
 * <p>
 * Because {@code PDataNode} is immutable, it is safe for multiple versions of the tree
 * to share the same node instances. When the tree performs a modification (e.g., via
 * {@link org.pvb.persistenttree.core.PersistentNTree#update}), only the nodes on the
 * path from the root to the modified node are replaced with new {@code PDataNode}
 * instances. All unmodified subtrees continue to use the existing nodes. This is the
 * core of the library's <em>structural sharing</em> strategy.
 * </p>
 *
 * <h2>Children List</h2>
 * <p>
 * Children are stored in an {@link ArrayList} and exposed through an unmodifiable view
 * via {@link Collections#unmodifiableList(List)}. The constructor that accepts a
 * {@code List<PersistentDataNode<T>>} stores the provided list directly (no defensive
 * copy is made), so callers in the internal implementation must ensure that the list
 * is not mutated after construction.
 * </p>
 *
 * <h2>Constructors</h2>
 * <p>
 * Two constructors are provided:
 * </p>
 * <ul>
 *   <li>{@link #PDataNode(NodeID, Object, List)} — Used when creating a node from an
 *       existing children list (e.g., when recreating an ancestor node during a
 *       structural modification).</li>
 *   <li>{@link #PDataNode(NodeID, Object)} — Used when creating a brand-new leaf node
 *       (e.g., when a child is added via
 *       {@link org.pvb.persistenttree.core.PersistentNTree#addChild}).</li>
 * </ul>
 * <p>
 * Both constructors are package-private to enforce creation through the
 * {@link org.pvb.persistenttree.core.PersistentNTree} API.
 * </p>
 *
 * @param <T> the type of the data payload stored in this node
 *
 * @see PersistentDataNode
 * @see org.pvb.persistenttree.core.PersistentNTree
 */
public class PDataNode<T> implements PersistentDataNode<T> {

    /** The typed data payload stored in this node. Never mutated after construction. */
    private final T data;

    /**
     * Ordered list of direct children. Stored as an {@link ArrayList} internally and
     * exposed as an unmodifiable view by {@link #getChildren()}.
     */
    private final List<PersistentDataNode<T>> children;

    /** Stable, unique identity of this node. Assigned at construction and never changed. */
    private final NodeID id;

    /**
     * Constructs a {@code PDataNode} with a pre-existing list of children.
     *
     * <p>This constructor is used internally when recreating a node along a modification
     * path. For example, when updating a descendant, its parent is recreated with its
     * original {@link NodeID} and data, but with a new children list that contains the
     * updated subtree.</p>
     *
     * <p>The provided {@code updatedList} is stored as-is. The caller is responsible for
     * ensuring it is not mutated after this constructor returns.</p>
     *
     * @param id          the stable {@link NodeID} to assign to this node; must not be {@code null}
     * @param data        the data payload for this node; may be {@code null}
     * @param updatedList the ordered list of direct child nodes; must not be {@code null};
     *                    will not be defensively copied
     */
    public PDataNode(NodeID id, T data, List<PersistentDataNode<T>> updatedList) {
        this.data = data;
        this.id = id;
        this.children = updatedList;
    }

    /**
     * Constructs a new leaf {@code PDataNode} with no children.
     *
     * <p>This constructor is used when a brand-new node is created by
     * {@link org.pvb.persistenttree.core.PersistentNTree#addChild}. The children list
     * is initialized to an empty {@link ArrayList}.</p>
     *
     * @param id   the stable {@link NodeID} to assign to this node; must not be {@code null}
     * @param data the data payload for this node; may be {@code null}
     */
    PDataNode(NodeID id, T data) {
        this.data = data;
        this.id = id;
        this.children = new ArrayList<>();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the data payload stored in this node at construction time. The value
     * is never modified; to change a node's data, a new {@code PDataNode} instance must
     * be created via {@link org.pvb.persistenttree.core.PersistentNTree#update}.</p>
     */
    @Override
    public T getData() {
        return this.data;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the stable {@link NodeID} assigned to this node at construction. The
     * same {@link NodeID} is preserved in any new {@code PDataNode} created to replace
     * this node during a structural modification, allowing callers to track the "same
     * logical node" across versions by its ID.</p>
     */
    @Override
    public NodeID getID() {
        return this.id;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns an unmodifiable view of the internal children list, backed by the
     * {@link ArrayList} created at construction time. The ordering reflects insertion
     * order (new children are always appended). Attempting to add, remove, or replace
     * elements in the returned list will throw {@link UnsupportedOperationException}.</p>
     *
     * <p>An empty list is returned for leaf nodes. The list is never {@code null}.</p>
     */
    @Override
    public List<PersistentDataNode<T>> getChildren() {
        return Collections.unmodifiableList(this.children);
    }
}