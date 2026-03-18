package org.pvb.persistenttree.api;

import java.util.List;
import java.util.Optional;

/**
 * Manages named <b>snapshots (versions)</b> of a {@link PersistentTree}, enabling
 * time-travel access to historical tree states.
 *
 * <p>A {@code VersionManager} is the mechanism through which callers explicitly record
 * and later retrieve specific states of the tree. Every call to
 * {@link #addVersion(Object, PersistentDataNode)} stores the provided root node under
 * the supplied key. Because nodes are immutable and the tree uses structural sharing,
 * storing a root is a very cheap operation — it simply holds a reference to an already-
 * existing node graph; no copying occurs.</p>
 *
 * <h2>Relationship to the Tree</h2>
 * <p>
 * The {@code VersionManager} is obtained via {@link PersistentTree#getVersionManager()}.
 * It does <b>not</b> automatically record versions; callers must decide <em>when</em>
 * to snapshot. A common pattern is to snapshot after every significant modification:
 * </p>
 * <pre>{@code
 * VersionManager<String, String> vm = tree.getVersionManager();
 *
 * // Add a child, then snapshot as version "v1"
 * tree.addChild(tree.getRoot().getID(), "Child A");
 * vm.addVersion("v1", tree.getRoot());
 *
 * // Make another change, snapshot as "v2"
 * tree.addChild(tree.getRoot().getID(), "Child B");
 * vm.addVersion("v2", tree.getRoot());
 *
 * // Later, retrieve version "v1" — Child B is not present here
 * PersistentDataNode<String> v1 = vm.getVersion("v1").orElseThrow();
 * }</pre>
 *
 * <h2>Key Types</h2>
 * <p>
 * The version key type {@code K} is completely caller-defined. Common choices include
 * {@link String} (for human-readable labels), {@link Integer} (for sequential numbering),
 * or any custom object that implements {@link Object#equals(Object)} and
 * {@link Object#hashCode()} correctly (since keys are stored in a {@link java.util.HashMap}
 * internally). If the same key is used twice, the second call to
 * {@link #addVersion(Object, PersistentDataNode)} silently overwrites the first.
 * </p>
 *
 * <h2>Current Version</h2>
 * <p>
 * {@link #getCurrentVersion()} always returns the root that was most recently passed to
 * {@link #addVersion(Object, PersistentDataNode)}, regardless of the key. It returns
 * {@link Optional#empty()} if no version has been added yet.
 * </p>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * The standard implementation ({@link org.pvb.persistenttree.core.DefaultVersionManager})
 * is not thread-safe. External synchronization is required if versions are added or
 * retrieved concurrently.
 * </p>
 *
 * @param <K> the type of the key used to identify versions
 * @param <T> the type of data stored in the tree nodes being versioned
 *
 * @see org.pvb.persistenttree.core.DefaultVersionManager
 * @see PersistentTree#getVersionManager()
 */
public interface VersionManager<K, T> {

    /**
     * Retrieves the tree root that was stored under the given {@code key}, wrapped in
     * an {@link Optional}.
     *
     * <p>If no version was ever stored under {@code key}, the returned {@code Optional}
     * is empty. This method never throws for a missing key — the {@code Optional} idiom
     * is used to distinguish "not found" from a stored {@code null} root (which, while
     * unusual, is permitted).</p>
     *
     * <p>The returned node (if present) is the exact {@link PersistentDataNode} instance
     * that was passed to {@link #addVersion(Object, PersistentDataNode)} at the time of
     * recording. Because all nodes are immutable, this reference fully represents the
     * tree state as it was at the time of the snapshot.</p>
     *
     * <p><b>Complexity:</b> O(1) — backed by a {@link java.util.HashMap} lookup.</p>
     *
     * @param key the version key to look up; must not be {@code null}
     * @return an {@link Optional} containing the root node stored under {@code key},
     *         or {@link Optional#empty()} if no version exists for that key
     */
    Optional<PersistentDataNode<T>> getVersion(K key);

    /**
     * Stores the given tree root under the specified {@code key}, creating a new
     * named snapshot of the tree state.
     *
     * <p>After this call, {@link #getVersion(Object)} invoked with the same {@code key}
     * will return the provided {@code root}. Additionally, {@link #getCurrentVersion()}
     * will return the same {@code root} until the next call to this method.</p>
     *
     * <p>If a version already exists for {@code key}, it is silently replaced. No
     * history of overwritten entries is maintained.</p>
     *
     * <p>Storing a version is extremely cheap: it stores a single object reference.
     * The persistent tree's structural sharing ensures that no copying of node data
     * occurs at snapshot time.</p>
     *
     * @param key  the key under which to store the snapshot; must not be {@code null}
     * @param root the root node representing the tree state to snapshot;
     *             may be {@code null} (representing an empty tree), though this is unusual
     */
    void addVersion(K key, PersistentDataNode<T> root);

    /**
     * Returns all version keys currently stored in this manager, in no guaranteed order.
     *
     * <p>The returned list is a snapshot of the key set at the time of the call. It is
     * not a live view; additions or removals made after this call are not reflected in
     * the returned list.</p>
     *
     * <p>If no versions have been added, an empty list is returned (never {@code null}).</p>
     *
     * @return a non-null, possibly empty {@link List} of all stored version keys
     */
    List<K> getKeys();

    /**
     * Returns all root nodes currently stored in this manager, in no guaranteed order.
     *
     * <p>The returned list contains the root nodes of all snapshots, in no guaranteed
     * correspondence to the order of {@link #getKeys()}. It is a snapshot at the time
     * of the call.</p>
     *
     * <p>If no versions have been added, an empty list is returned (never {@code null}).</p>
     *
     * @return a non-null, possibly empty {@link List} of all stored version roots
     */
    List<PersistentDataNode<T>> getVersions();

    /**
     * Returns the root node that was most recently stored via
     * {@link #addVersion(Object, PersistentDataNode)}, wrapped in an {@link Optional}.
     *
     * <p>"Most recently stored" refers to the last call to
     * {@link #addVersion(Object, PersistentDataNode)}, regardless of the key used. This
     * provides a convenient handle to the latest known good snapshot without needing
     * to track keys manually.</p>
     *
     * <p>Returns {@link Optional#empty()} if {@link #addVersion(Object, PersistentDataNode)}
     * has never been called on this manager.</p>
     *
     * @return an {@link Optional} containing the most recently stored root node,
     *         or {@link Optional#empty()} if no version has been added yet
     */
    Optional<PersistentDataNode<T>> getCurrentVersion();
}