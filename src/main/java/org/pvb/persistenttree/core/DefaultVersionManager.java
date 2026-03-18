package org.pvb.persistenttree.core;

import org.pvb.persistenttree.api.PersistentDataNode;
import org.pvb.persistenttree.api.VersionManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The default implementation of {@link VersionManager}, backed by a {@link HashMap}
 * that maps caller-supplied keys to {@link PersistentDataNode} root snapshots.
 *
 * <p>An instance of {@code DefaultVersionManager} is created by
 * {@link PersistentNTree} at construction time and exposed to callers via
 * {@link org.pvb.persistenttree.api.PersistentTree#getVersionManager()}. It does
 * <b>not</b> record versions automatically — callers must explicitly invoke
 * {@link #addVersion(Object, PersistentDataNode)} to snapshot any desired state.</p>
 *
 * <h2>Storage Model</h2>
 * <p>
 * Versions are stored in a {@link HashMap}. Because every {@link PersistentDataNode}
 * is immutable and the tree uses structural sharing, storing a root is equivalent to
 * holding a reference to an already-existing, frozen node graph. No copying occurs at
 * snapshot time, so version storage is both time- and memory-efficient: the cost is
 * one map entry per snapshot, with additional memory proportional only to the parts
 * of the tree that are <em>unique</em> to that version.
 * </p>
 *
 * <h2>Current Version Tracking</h2>
 * <p>
 * The {@link #current} field tracks the most recently added root. It is updated on
 * every call to {@link #addVersion(Object, PersistentDataNode)} and exposed via
 * {@link #getCurrentVersion()}. It is independent of any particular key: calling
 * {@link #addVersion(Object, PersistentDataNode)} twice with different keys will cause
 * {@link #getCurrentVersion()} to return the root from the second call.
 * </p>
 *
 * <h2>Visibility</h2>
 * <p>
 * The constructor is package-private. Callers obtain an instance through
 * {@link org.pvb.persistenttree.api.PersistentTree#getVersionManager()}.
 * </p>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is <b>not thread-safe</b>. The underlying {@link HashMap} and the
 * {@link #current} pointer are both unsynchronized. External locking is required
 * for concurrent use.
 * </p>
 *
 * @param <K> the type of the key used to identify stored versions
 * @param <T> the type of data stored in the tree nodes being versioned
 *
 * @see VersionManager
 * @see PersistentNTree
 */
public class DefaultVersionManager<K, T> implements VersionManager<K, T> {

    /**
     * The map of version keys to root nodes. A {@link HashMap} is used for O(1)
     * average-case lookup and insertion.
     */
    private final Map<K, PersistentDataNode<T>> versions;

    /**
     * The root node most recently stored via {@link #addVersion(Object, PersistentDataNode)}.
     * {@code null} until the first call to {@link #addVersion}.
     */
    private PersistentDataNode<T> current;

    /**
     * Constructs an empty {@code DefaultVersionManager} with no stored versions.
     *
     * <p>Called once from {@link PersistentNTree} at tree construction time. The
     * internal map starts empty and {@link #current} is {@code null} until
     * {@link #addVersion(Object, PersistentDataNode)} is first called.</p>
     */
    DefaultVersionManager() {
        versions = new HashMap<>();
    }

    /**
     * {@inheritDoc}
     *
     * <h4>Implementation Details</h4>
     * <p>
     * Delegates to {@link HashMap#get(Object)}, wrapping the result in
     * {@link Optional#ofNullable(Object)}. A missing key and a stored {@code null}
     * value are both represented as {@link Optional#empty()} by this method (since
     * {@link Optional#ofNullable} converts {@code null} to empty). In practice, a
     * {@code null} root is unusual but may occur if the root node was removed.
     * </p>
     *
     * <p><b>Complexity:</b> O(1) average case.</p>
     */
    @Override
    public Optional<PersistentDataNode<T>> getVersion(K key) {
        return Optional.ofNullable(versions.get(key));
    }

    /**
     * {@inheritDoc}
     *
     * <h4>Implementation Details</h4>
     * <p>
     * Inserts an entry into the internal {@link HashMap} and updates the {@link #current}
     * pointer. If a version already exists under {@code key}, it is silently overwritten.
     * </p>
     *
     * <p><b>Complexity:</b> O(1) average case.</p>
     */
    @Override
    public void addVersion(K key, PersistentDataNode<T> root) {
        versions.put(key, root);
        current = root;
    }

    /**
     * {@inheritDoc}
     *
     * <h4>Implementation Details</h4>
     * <p>
     * Returns a new immutable {@link List} backed by a stream over the map's key set.
     * The order of keys is unspecified (determined by the {@link HashMap} iteration
     * order). The returned list is a snapshot and will not reflect future
     * {@link #addVersion} calls.
     * </p>
     */
    @Override
    public List<K> getKeys() {
        return versions.keySet().stream().toList();
    }

    /**
     * {@inheritDoc}
     *
     * <h4>Implementation Details</h4>
     * <p>
     * Returns a new immutable {@link List} backed by a stream over the map's values
     * collection. The ordering of the list is unspecified and does not necessarily
     * correspond to insertion order or to the order of {@link #getKeys()}. The returned
     * list is a snapshot and will not reflect future {@link #addVersion} calls.
     * </p>
     */
    @Override
    public List<PersistentDataNode<T>> getVersions() {
        return versions.values().stream().toList();
    }

    /**
     * {@inheritDoc}
     *
     * <h4>Implementation Details</h4>
     * <p>
     * Returns {@link Optional#ofNullable(Object)} wrapping the {@link #current} field,
     * which is updated on every call to {@link #addVersion(Object, PersistentDataNode)}.
     * If {@link #addVersion} has never been called, {@link #current} is {@code null}
     * and this method returns {@link Optional#empty()}.
     * </p>
     */
    @Override
    public Optional<PersistentDataNode<T>> getCurrentVersion() {
        return Optional.ofNullable(current);
    }
}