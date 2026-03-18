# ImmuTree-persistence-tree-engine

A **Java library for persistent (immutable) tree data structures** built on structural sharing (path-copying). Every mutating operation — update, add, remove — produces a new version of the tree while leaving all prior versions intact and reachable. No copies of unchanged nodes are made; unmodified subtrees are shared across versions.

---

## What Does "Persistent" Mean?

In the functional-programming sense, a *persistent* data structure preserves all previous versions of itself after a modification. This library achieves persistence through **path-copying**: only the nodes along the path from the root to the modified node are recreated; every other subtree is reused by reference.

```
Version 1          Version 2 (after updating D)
    A                  A'          ← new
   / \                / \
  B   C             B'   C        ← C reused
 / \               / \
D   E            D'   E           ← E reused, D' is new
```

---

## Features

- Fully persistent N-ary tree (unbounded children per node)
- Fully persistent binary tree (left/right named slots)
- Structural sharing — O(depth) memory per version
- Depth-first (pre-order), breadth-first, and in-order iterators
- Manual version snapshotting and time-travel retrieval
- Predicate-based node search (`findAll`)
- Node lookup by `NodeID` (`getNode`)
- `NodePositionOccupiedException` for binary slot enforcement
- Fully documented Javadoc on every public type and method

---

## Installation

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>org.pvb</groupId>
    <artifactId>persistence-tree-engine</artifactId>
    <version>1.1.0</version>
</dependency>
```

---

## Quick Start

### N-ary Tree

```java
import org.pvb.persistenttree.api.*;
import org.pvb.persistenttree.api.Enums.TreeType;
import org.pvb.persistenttree.core.TreeFactory;

// Create a tree
PersistentTree<String, String> tree =
        TreeFactory.createTree("Root", TreeType.N_ARY);

// Add children
NodeID child1 = tree.addChild(tree.getRoot().getID(), "Child-1");
NodeID child2 = tree.addChild(tree.getRoot().getID(), "Child-2");

// Snapshot current state
tree.getVersionManager().addVersion("v1", tree.getRoot());

// Mutate — old version is untouched
tree.update(child1, "Child-1-Updated");

// Retrieve old version
PersistentDataNode<String> v1 = tree.getVersionManager()
        .getVersion("v1").orElseThrow();

// Traverse current version (DFS)
PersistentIterator<String> it = tree.dfsIterator();
while (it.hasNext()) {
    System.out.println(it.next().getData());
}
```

### Binary Tree

```java
BinaryTree<String, Integer> tree =
        (BinaryTree<String, Integer>)
        TreeFactory.<String, Integer>createTree(10, TreeType.BINARY);

NodeID leftId  = tree.addLeftChild(tree.getRoot().getID(), 5);
NodeID rightId = tree.addRightChild(tree.getRoot().getID(), 15);
tree.addLeftChild(leftId, 3);
tree.addRightChild(leftId, 7);

// In-order traversal → 3, 5, 7, 10, 15
PersistentIterator<Integer> it = tree.inOrderIterator();
while (it.hasNext()) {
    System.out.println(it.next().getData());
}
```

---

## Core API

### `TreeFactory`

The sole entry point for creating trees.

| Method | Description |
|---|---|
| `TreeFactory.createTree(T data, TreeType type)` | Creates a new tree of the given type with one root node |

`TreeType` values: `N_ARY`, `BINARY`

> When creating a `BINARY` tree, cast the result to `BinaryTree<K,T>` to access binary-specific methods.

---

### `PersistentTree<K, T>`

The base interface for all tree types.

| Method | Description |
|---|---|
| `addChild(NodeID parentID, T data)` | Adds a child to the given parent; returns the new child's `NodeID` |
| `update(NodeID id, T data)` | Replaces node data; returns new root |
| `removeNode(NodeID id)` | Removes node and its subtree; returns new root |
| `removeNode(NodeID id, PersistentDataNode<T> subtree)` | Removes within a specific subtree |
| `getRoot()` | Returns the current root node |
| `dfsIterator()` | Depth-first pre-order iterator from root |
| `dfsIterator(PersistentDataNode<T> node)` | DFS from a specific node |
| `bfsIterator()` | Breadth-first iterator from root |
| `bfsIterator(PersistentDataNode<T> node)` | BFS from a specific node |
| `getNodeManager()` | Returns the `NodeManager` for lookup and search |
| `getVersionManager()` | Returns the `VersionManager` for snapshotting |

---

### `BinaryTree<K, T>` *(extends `PersistentTree`)*

Additional methods available when using `TreeType.BINARY`.

| Method | Description |
|---|---|
| `addLeftChild(NodeID parentID, T data)` | Inserts into left slot; throws if occupied |
| `addRightChild(NodeID parentID, T data)` | Inserts into right slot; throws if occupied |
| `setLeft(NodeID parentID, BinaryDataNode<T> node)` | Unconditionally replaces left slot |
| `setRight(NodeID parentID, BinaryDataNode<T> node)` | Unconditionally replaces right slot |
| `inOrderIterator()` | Left → root → right iterator from root |
| `inOrderIterator(BinaryDataNode<T> node)` | In-order from a specific node |

---

### `NodeManager<T>`

Obtained via `tree.getNodeManager()`.

| Method | Description |
|---|---|
| `getNode(NodeID id)` | Finds a node by ID; throws `NodeNotFoundException` if missing |
| `findAll(Predicate<T> predicate)` | Returns all nodes matching the predicate (DFS order) |
| `findAll(Predicate<T> predicate, PersistentDataNode<T> subtree)` | Same, scoped to a subtree |

---

### `VersionManager<K, T>`

Obtained via `tree.getVersionManager()`.

| Method | Description |
|---|---|
| `addVersion(K key, PersistentDataNode<T> root)` | Snapshots the given root under a key |
| `getVersion(K key)` | Returns `Optional<PersistentDataNode<T>>` for the key |
| `getCurrentVersion()` | Returns the most recently added version |
| `getKeys()` | Returns all stored version keys |
| `getVersions()` | Returns all stored root nodes |

---

### `PersistentDataNode<T>`

| Method | Description |
|---|---|
| `getData()` | Returns the node's data payload |
| `getID()` | Returns the node's `NodeID` |
| `getChildren()` | Returns an unmodifiable list of child nodes |

### `BinaryDataNode<T>` *(extends `PersistentDataNode<T>`)*

| Method | Description |
|---|---|
| `getLeft()` | Returns the left child, or `null` |
| `getRight()` | Returns the right child, or `null` |

---

## Exceptions

| Exception | When thrown |
|---|---|
| `NodeNotFoundException` | Target or parent `NodeID` not found in the tree |
| `NodePositionOccupiedException` | Attempting to `addLeftChild`/`addRightChild` on an already-occupied slot |

---

## Traversal Summary

| Iterator | Order | Available on |
|---|---|---|
| `dfsIterator()` | Pre-order (root → left → right) | N-ary & Binary |
| `bfsIterator()` | Level-order | N-ary & Binary |
| `inOrderIterator()` | Left → root → right | Binary only |

---

## Versioning Pattern

```java
// Work
NodeID a = tree.addChild(tree.getRoot().getID(), "A");
tree.getVersionManager().addVersion("after-A", tree.getRoot());

NodeID b = tree.addChild(tree.getRoot().getID(), "B");
tree.getVersionManager().addVersion("after-B", tree.getRoot());

// Time travel
PersistentDataNode<String> old = tree.getVersionManager()
        .getVersion("after-A").orElseThrow();

// 'old' still shows only "A" — "B" does not exist here
```

---

## Thread Safety

Node objects (`PDataNode`, `PBinaryDataNode`) are immutable and safe to share across threads. The tree objects themselves (`PersistentNTree`, `PersistentBinaryTree`) are **not thread-safe** — external synchronization is required if mutating from multiple threads.

---

## Package Structure

```
org.pvb.persistenttree
├── api
│   ├── PersistentTree.java
│   ├── BinaryTree.java
│   ├── PersistentDataNode.java
│   ├── BinaryDataNode.java
│   ├── NodeID.java
│   ├── NodeManager.java
│   ├── VersionManager.java
│   ├── PersistentIterator.java
│   ├── Enums/
│   │   └── TreeType.java
│   └── Exceptions/
│       ├── NodeNotFoundException.java
│       └── NodePositionOccupiedException.java
└── core
    ├── TreeFactory.java
    ├── PersistentNTree.java
    ├── PersistentBinaryTree.java
    ├── PDataNode.java
    ├── PBinaryDataNode.java
    ├── DefaultNodeManager.java
    ├── DefaultVersionManager.java
    ├── DfsIterator.java
    ├── BfsIterator.java
    └── InOrderIterator.java
```

---

## License

MIT License. See [LICENSE](LICENSE) for details.
