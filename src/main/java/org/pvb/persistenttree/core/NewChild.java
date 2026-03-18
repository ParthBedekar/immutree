package org.pvb.persistenttree.core;

import org.pvb.persistenttree.api.NodeID;
import org.pvb.persistenttree.api.PersistentDataNode;

record NewChild<T>(NodeID id, PersistentDataNode<T> child) {
}
