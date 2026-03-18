package org.pvb.persistenttree.api.Exceptions;

/**
 * Thrown to indicate that an attempt was made to insert a child node into a slot
 * of a {@link org.pvb.persistenttree.api.BinaryDataNode} that is already occupied.
 *
 * <p>In a binary tree, each node has exactly two named slots: a <em>left</em> slot
 * and a <em>right</em> slot. Each slot may hold at most one child at any given time.
 * This exception is thrown by
 * {@link org.pvb.persistenttree.api.BinaryTree#addLeftChild(org.pvb.persistenttree.api.NodeID, Object)}
 * when the target node's left slot already contains a child, and by
 * {@link org.pvb.persistenttree.api.BinaryTree#addRightChild(org.pvb.persistenttree.api.NodeID, Object)}
 * when the right slot is already occupied.</p>
 *
 * <h2>When this Exception is Thrown</h2>
 * <ul>
 *   <li>Calling {@code addLeftChild} on a node that already has a left child.</li>
 *   <li>Calling {@code addRightChild} on a node that already has a right child.</li>
 *   <li>Calling the generic {@code addChild} on a node that already has both children.</li>
 * </ul>
 *
 * <h2>Resolution</h2>
 * <p>
 * To replace an existing child rather than add to an empty slot, use
 * {@link org.pvb.persistenttree.api.BinaryTree#setLeft(org.pvb.persistenttree.api.NodeID,
 * org.pvb.persistenttree.api.BinaryDataNode)} or
 * {@link org.pvb.persistenttree.api.BinaryTree#setRight(org.pvb.persistenttree.api.NodeID,
 * org.pvb.persistenttree.api.BinaryDataNode)}, both of which unconditionally overwrite
 * the target slot.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * NodeID leftId = tree.addLeftChild(parentId, "Left");
 *
 * // This will throw NodePositionOccupiedException because the left slot is taken:
 * tree.addLeftChild(parentId, "Another Left");
 *
 * // Use setLeft to replace instead:
 * tree.setLeft(parentId, newLeftNode);
 * }</pre>
 *
 * <p>This is an unchecked (runtime) exception. Callers are encouraged to validate
 * slot availability before calling the add methods, or to catch this exception at
 * an appropriate boundary.</p>
 *
 * @see org.pvb.persistenttree.api.BinaryTree#addLeftChild(org.pvb.persistenttree.api.NodeID, Object)
 * @see org.pvb.persistenttree.api.BinaryTree#addRightChild(org.pvb.persistenttree.api.NodeID, Object)
 * @see org.pvb.persistenttree.api.BinaryTree#setLeft(org.pvb.persistenttree.api.NodeID, org.pvb.persistenttree.api.BinaryDataNode)
 * @see org.pvb.persistenttree.api.BinaryTree#setRight(org.pvb.persistenttree.api.NodeID, org.pvb.persistenttree.api.BinaryDataNode)
 */
public class NodePositionOccupiedException extends RuntimeException {

    /**
     * Constructs a new {@code NodePositionOccupiedException} with the given detail message.
     *
     * @param message a human-readable description of which slot was occupied and on
     *                which node; must not be {@code null}
     */
    public NodePositionOccupiedException(String message) {
        super(message);
    }

    /**
     * Constructs a new {@code NodePositionOccupiedException} with the given detail
     * message and cause.
     *
     * @param message a human-readable description of the error; must not be {@code null}
     * @param cause   the underlying cause; may be {@code null}
     */
    public NodePositionOccupiedException(String message, Throwable cause) {
        super(message, cause);
    }
}