package org.pvb.persistenttree.api.Enums;

/**
 * Enumerates the types of persistent tree structures supported by this library.
 *
 * <p>{@code TreeType} is passed to
 * {@link org.pvb.persistenttree.core.TreeFactory#createTree(Object, TreeType)}
 * to select the concrete implementation that will be instantiated. Each constant
 * maps to exactly one implementation class as described below.</p>
 *
 * <h2>Supported Types</h2>
 * <table border="1" summary="Tree type to implementation mapping">
 *   <tr>
 *     <th>Constant</th>
 *     <th>Implementation Class</th>
 *     <th>Node Type</th>
 *     <th>Max Children per Node</th>
 *   </tr>
 *   <tr>
 *     <td>{@link #N_ARY}</td>
 *     <td>{@link org.pvb.persistenttree.core.PersistentNTree}</td>
 *     <td>{@link org.pvb.persistenttree.core.PDataNode}</td>
 *     <td>Unbounded</td>
 *   </tr>
 *   <tr>
 *     <td>{@link #BINARY}</td>
 *     <td>{@link org.pvb.persistenttree.core.PersistentBinaryTree}</td>
 *     <td>{@link org.pvb.persistenttree.core.PBinaryDataNode}</td>
 *     <td>2 (left and right)</td>
 *   </tr>
 * </table>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Create an N-ary tree (unbounded children)
 * PersistentTree<String, String> nAry =
 *         TreeFactory.createTree("Root", TreeType.N_ARY);
 *
 * // Create a binary tree (left and right children only)
 * BinaryTree<String, Integer> binary =
 *         (BinaryTree<String, Integer>) TreeFactory.createTree(0, TreeType.BINARY);
 * }</pre>
 *
 * @see org.pvb.persistenttree.core.TreeFactory
 * @see org.pvb.persistenttree.api.PersistentTree
 * @see org.pvb.persistenttree.api.BinaryTree
 */
public enum TreeType {

    /**
     * Selects the N-ary persistent tree implementation
     * ({@link org.pvb.persistenttree.core.PersistentNTree}).
     *
     * <p>An N-ary tree imposes no upper bound on the number of children a node may have.
     * Children are maintained in insertion order and accessed via
     * {@link org.pvb.persistenttree.api.PersistentDataNode#getChildren()}.
     * New children are appended to the end of the list via
     * {@link org.pvb.persistenttree.api.PersistentTree#addChild(org.pvb.persistenttree.api.NodeID, Object)}.</p>
     *
     * <p>This is the general-purpose tree type suitable for hierarchical data of any
     * branching factor, such as file-system trees, organisational charts, or parse trees.</p>
     */
    N_ARY,

    /**
     * Selects the persistent binary tree implementation
     * ({@link org.pvb.persistenttree.core.PersistentBinaryTree}).
     *
     * <p>A binary tree constrains each node to at most two children, designated
     * <em>left</em> and <em>right</em>. The returned tree implements
     * {@link org.pvb.persistenttree.api.BinaryTree}, which extends
     * {@link org.pvb.persistenttree.api.PersistentTree} with binary-specific operations:</p>
     * <ul>
     *   <li>{@link org.pvb.persistenttree.api.BinaryTree#addLeftChild(org.pvb.persistenttree.api.NodeID, Object)}</li>
     *   <li>{@link org.pvb.persistenttree.api.BinaryTree#addRightChild(org.pvb.persistenttree.api.NodeID, Object)}</li>
     *   <li>{@link org.pvb.persistenttree.api.BinaryTree#setLeft(org.pvb.persistenttree.api.NodeID, org.pvb.persistenttree.api.BinaryDataNode)}</li>
     *   <li>{@link org.pvb.persistenttree.api.BinaryTree#setRight(org.pvb.persistenttree.api.NodeID, org.pvb.persistenttree.api.BinaryDataNode)}</li>
     *   <li>{@link org.pvb.persistenttree.api.BinaryTree#inOrderIterator()}</li>
     * </ul>
     *
     * <p>The factory return type is {@link org.pvb.persistenttree.api.PersistentTree};
     * callers must cast to {@link org.pvb.persistenttree.api.BinaryTree} to access the
     * binary-specific API:</p>
     * <pre>{@code
     * BinaryTree<String, Integer> tree =
     *         (BinaryTree<String, Integer>) TreeFactory.createTree(0, TreeType.BINARY);
     * }</pre>
     *
     * <p>This type is suited for binary search trees, expression trees, Huffman trees,
     * or any structure where at most two children per node is the correct constraint.</p>
     */
    BINARY
}