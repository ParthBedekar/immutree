# 🌳 ImmuTree — Persistent Tree Engine for Java

> A high-performance **immutable (persistent) tree library** for Java, built using structural sharing (path-copying).

![Java](https://img.shields.io/badge/Java-17+-orange)
![Build](https://img.shields.io/badge/Build-Maven-blue)
![License](https://img.shields.io/badge/License-MIT-green)
![Status](https://img.shields.io/badge/Status-Active-success)

---

## ✨ Why ImmuTree?

Traditional tree structures mutate in place — once changed, the previous state is gone.

**ImmuTree does the opposite.**

Every modification:
- creates a **new version of the tree**
- preserves **all previous versions**
- avoids unnecessary copying via **structural sharing**

👉 Think Git, but for trees.

---

## 🧠 What “Persistent” Actually Means

A persistent data structure **never overwrites itself**.

Instead of modifying nodes directly, ImmuTree uses **path-copying**:

- Only nodes along the modification path are recreated
- All other subtrees are reused (shared)

```
Version 1              Version 2
    A                      A'
   / \                    / \
  B   C       →          B'  C
 / \                    / \
D   E                  D'  E
```

✔ Minimal memory overhead  
✔ O(depth) updates  
✔ Instant time-travel to any version  

---

## 🚀 Features

- 🌲 Persistent **N-ary tree**
- 🌿 Persistent **Binary tree**
- ♻️ Structural sharing (efficient memory usage)
- 🔁 DFS, BFS, and In-order traversal
- 🕓 Versioning & time-travel
- 🔍 Predicate-based search (`findAll`)
- 🧩 Node lookup via `NodeID`
- ⚠️ Strict binary constraints with exceptions
- 📚 Fully documented public API

---

## 📦 Installation

```xml
<dependency>
    <groupId>io.github.ParthBedekar</groupId>
    <artifactId>immutree</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## ⚡ Quick Start

### 🌲 N-ary Tree

```java
import io.github.ParthBedekar.api.*;
import io.github.ParthBedekar.api.Enums.TreeType;
import io.github.ParthBedekar.core.TreeFactory;

PersistentTree<String, String> tree =
        TreeFactory.createTree("Root", TreeType.N_ARY);

NodeID child1 = tree.addChild(tree.getRoot().getID(), "Child-1");
NodeID child2 = tree.addChild(tree.getRoot().getID(), "Child-2");

tree.getVersionManager().addVersion("v1", tree.getRoot());

tree.update(child1, "Updated");

PersistentDataNode<String> old =
        tree.getVersionManager().getVersion("v1").orElseThrow();
```

---

### 🌿 Binary Tree

```java
BinaryTree<String, Integer> tree =
    (BinaryTree<String, Integer>)
    TreeFactory.<String, Integer>createTree(10, TreeType.BINARY);

NodeID left = tree.addLeftChild(tree.getRoot().getID(), 5);
NodeID right = tree.addRightChild(tree.getRoot().getID(), 15);

tree.addLeftChild(left, 3);
tree.addRightChild(left, 7);
```

---

## 🧩 Core Concepts

### 🏗 TreeFactory

Single entry point for creating trees:

```java
TreeFactory.createTree(data, TreeType.N_ARY);
TreeFactory.createTree(data, TreeType.BINARY);
```

---

### 🌳 PersistentTree

Core interface:

- `addChild(...)`
- `update(...)`
- `removeNode(...)`
- `dfsIterator()`
- `bfsIterator()`
- `getVersionManager()`
- `getNodeManager()`

---

### 🌿 BinaryTree (extends PersistentTree)

Adds:

- `addLeftChild(...)`
- `addRightChild(...)`
- `inOrderIterator()`

---

### 🔍 NodeManager

```java
tree.getNodeManager().getNode(id);
tree.getNodeManager().findAll(predicate);
```

---

### 🕓 VersionManager

```java
tree.getVersionManager().addVersion("v1", root);
tree.getVersionManager().getVersion("v1");
```

---

## 🔁 Traversals

| Type | Order |
|------|------|
| DFS  | Root → Children |
| BFS  | Level-order |
| In-order | Left → Root → Right (Binary only) |

---

## 🧪 Versioning Example

```java
NodeID a = tree.addChild(tree.getRoot().getID(), "A");
tree.getVersionManager().addVersion("v1", tree.getRoot());

tree.addChild(tree.getRoot().getID(), "B");

PersistentDataNode<String> old =
    tree.getVersionManager().getVersion("v1").orElseThrow();
```

---

## ⚠️ Exceptions

- `NodeNotFoundException`
- `NodePositionOccupiedException`

---

## 🧵 Thread Safety

| Component | Thread-safe |
|----------|------------|
| Nodes | ✅ Yes (immutable) |
| Trees | ❌ No |

---

## 📁 Project Structure

```
immutree
├── api
├── core
└── utils
```

---

## 💡 Use Cases

- Undo/Redo systems
- Versioned data models
- Compiler ASTs
- Collaborative editors
- Time-travel debugging

---

## 🛣 Roadmap

- [ ] Kotlin support
- [ ] Serialization support
- [ ] Persistent Graphs
- [ ] Concurrent-safe tree wrapper

---

## 📜 License

MIT License — free to use, modify, and distribute.

---

## 🙌 Contributing

Pull requests are welcome. For major changes, open an issue first.

---

## ⭐ Support

If you found this useful:

👉 Star the repo  
👉 Share it  
👉 Use it in your projects  

---

## 👨‍💻 Author

**Parth Bedekar**  
Building systems that don’t forget their past.
