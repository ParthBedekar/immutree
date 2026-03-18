package org.pvb.persistenttree.core;

import org.pvb.persistenttree.api.BinaryTree;
import org.pvb.persistenttree.api.PersistentTree;
import org.pvb.persistenttree.api.Enums.TreeType;

/**
 * A static factory class for creating instances of {@link PersistentTree}.
 *
 * <p>{@code TreeFactory} is the <b>sole public entry point</b> for constructing
 * persistent trees. It decouples callers from the concrete implementation classes
 * ({@link PersistentNTree}, {@link PersistentBinaryTree}) and centralises instance
 * creation, making it trivial to add new tree variants without changing existing
 * call sites.</p>
 *
 * <h2>Supported Types</h2>
 * <table border="1" summary="Tree type to implementation mapping">
 *   <tr><th>{@link TreeType}</th><th>Implementation</th><th>Return Type</th></tr>
 *   <tr>
 *     <td>{@link TreeType#N_ARY}</td>
 *     <td>{@link PersistentNTree}</td>
 *     <td>{@link PersistentTree}</td>
 *   </tr>
 *   <tr>
 *     <td>{@link TreeType#BINARY}</td>
 *     <td>{@link PersistentBinaryTree}</td>
 *     <td>{@link BinaryTree} (sub-type of {@link PersistentTree})</td>
 *   </tr>
 * </table>
 *
 * <h2>Usage — N-ary Tree</h2>
 * <pre>{@code
 * PersistentTree<String, String> tree =
 *         TreeFactory.createTree("Root", TreeType.N_ARY);
 *
 * NodeID child = tree.addChild(tree.getRoot().getID(), "Child-1");
 * }</pre>
 *
 * <h2>Usage — Binary Tree</h2>
 * <pre>{@code
 * // The factory always returns PersistentTree; cast to BinaryTree for the
 * // binary-specific API (addLeftChild, addRightChild, setLeft, setRight,
 * // inOrderIterator).
 * BinaryTree<String, Integer> tree =
 *         (BinaryTree<String, Integer>) TreeFactory.createTree(10, TreeType.BINARY);
 *
 * NodeID leftId  = tree.addLeftChild(tree.getRoot().getID(), 5);
 * NodeID rightId = tree.addRightChild(tree.getRoot().getID(), 15);
 *
 * PersistentIterator<Integer> inOrder = tree.inOrderIterator();
 * while (inOrder.hasNext()) {
 *     System.out.println(inOrder.next().getData()); // 5, 10, 15
 * }
 * }</pre>
 *
 * <h2>Design Notes</h2>
 * <p>
 * {@code TreeFactory} is declared {@code final} and has only a private constructor,
 * preventing instantiation and subclassing. All methods are static. The factory method
 * is intentionally typed to return {@link PersistentTree} (the widest useful interface)
 * so that the same method signature serves all tree types. Callers that need a narrower
 * type must cast — this is a deliberate trade-off to keep the factory API surface small.
 * </p>
 *
 * @see PersistentTree
 * @see BinaryTree
 * @see PersistentNTree
 * @see PersistentBinaryTree
 * @see TreeType
 */
public final class TreeFactory {

    /** Prevents instantiation of this utility class. */
    private TreeFactory() {}

    /**
     * Creates and returns a new {@link PersistentTree} of the specified {@code type},
     * initialized with a single root node containing {@code data}.
     *
     * <p>The returned tree contains exactly one node (the root) with no children.
     * The root is assigned a randomly generated {@link org.pvb.persistenttree.api.NodeID}.
     * A {@link DefaultNodeManager} and a {@link DefaultVersionManager} are created
     * internally and associated with the new tree.</p>
     *
     * <h4>Type Selection and Casting</h4>
     * <p>
     * The declared return type is always {@link PersistentTree}. When {@code type} is
     * {@link TreeType#BINARY}, the actual runtime type is {@link PersistentBinaryTree},
     * which implements {@link BinaryTree}. Callers that need binary-specific operations
     * must cast:
     * </p>
     * <pre>{@code
     * BinaryTree<String, Integer> bt =
     *         (BinaryTree<String, Integer>) TreeFactory.createTree(0, TreeType.BINARY);
     * }</pre>
     *
     * <h4>Type-to-Implementation Table</h4>
     * <table border="1" summary="TreeType to implementation">
     *   <tr><th>TreeType</th><th>Runtime class</th></tr>
     *   <tr><td>{@link TreeType#N_ARY}</td><td>{@link PersistentNTree}</td></tr>
     *   <tr><td>{@link TreeType#BINARY}</td><td>{@link PersistentBinaryTree}</td></tr>
     * </table>
     *
     * @param <K>  the version key type for the {@link org.pvb.persistenttree.api.VersionManager}
     * @param <T>  the type of data stored in each node
     * @param data the data payload for the root node; may be {@code null}
     * @param type the {@link TreeType} describing which tree implementation to create;
     *             must not be {@code null}
     * @return a new, non-null {@link PersistentTree} instance; the runtime type is
     *         {@link PersistentBinaryTree} when {@code type} is {@link TreeType#BINARY}
     * @throws IllegalArgumentException if {@code type} is {@code null}
     */
    public static <K, T> PersistentTree<K, T> createTree(T data, TreeType type) {
        if (type == null) {
            throw new IllegalArgumentException("TreeType must not be null");
        }
        return switch (type) {
            case BINARY -> new PersistentBinaryTree<>(data);
            default     -> new PersistentNTree<>(data);
        };
    }
}