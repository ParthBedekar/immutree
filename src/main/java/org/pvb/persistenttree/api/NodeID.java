package org.pvb.persistenttree.api;

import java.util.UUID;

/**
 * A strongly-typed, immutable value object that uniquely identifies a single node
 * within a {@link PersistentTree}.
 *
 * <p>{@code NodeID} wraps a standard {@link UUID} to provide identity for nodes across
 * all versions of a persistent tree. Because the tree is persistent (immutable), a node's
 * identity never changes between versions — only the tree structure around it does.
 * This makes {@code NodeID} the stable handle a caller holds onto to refer to a node
 * across multiple versions.</p>
 *
 * <h2>Identity and Equality</h2>
 * <p>
 * Two {@code NodeID} instances are considered equal if and only if their underlying
 * {@link UUID} values are equal. Because {@code NodeID} is a {@code record}, {@link #equals(Object)}
 * and {@link #hashCode()} are automatically derived from the {@code id} component.
 * </p>
 *
 * <h2>Factory Method</h2>
 * <p>
 * Do not construct {@code NodeID} directly in application code. Instead, use the
 * {@link #generateUUID()} factory method, which is called internally by
 * {@link org.pvb.persistenttree.core.PersistentNTree} whenever a new node is created
 * (e.g., via {@link PersistentTree#addChild(NodeID, Object)}).
 * </p>
 *
 * <h2>Usage Pattern</h2>
 * <pre>{@code
 * // IDs are returned to callers when nodes are added:
 * NodeID childId = tree.addChild(tree.getRoot().getID(), "Child Data");
 *
 * // The ID can then be used to target that node in future operations:
 * tree.update(childId, "Updated Data");
 * tree.removeNode(childId);
 * tree.getNodeManager().getNode(childId);
 * }</pre>
 *
 * <h2>Serialization</h2>
 * <p>
 * The underlying {@link UUID} can be accessed via {@link #id()} for serialization,
 * logging, or persistence to external systems.
 * </p>
 *
 * @param id the underlying {@link UUID} that uniquely identifies a node;
 *           must not be {@code null}
 *
 * @see PersistentDataNode#getID()
 * @see PersistentTree#addChild(NodeID, Object)
 * @see PersistentTree#update(NodeID, Object)
 * @see PersistentTree#removeNode(NodeID)
 */
public record NodeID(UUID id) {

    /**
     * Creates and returns a new {@code NodeID} backed by a randomly generated {@link UUID}.
     *
     * <p>This is the canonical factory method for creating node identities. It delegates
     * to {@link UUID#randomUUID()}, which produces a version-4 (randomly generated) UUID
     * with approximately 2<sup>122</sup> possible values, making collisions negligible in
     * practice.</p>
     *
     * <p>This method is called internally by
     * {@link org.pvb.persistenttree.core.PersistentNTree#addChild(NodeID, Object)}
     * each time a new child node is inserted into the tree. Callers do not typically
     * need to invoke this method directly.</p>
     *
     * @return a new {@code NodeID} with a unique, randomly generated {@link UUID}
     */
    public static NodeID generateUUID(){
        return new NodeID(UUID.randomUUID());
    }
}