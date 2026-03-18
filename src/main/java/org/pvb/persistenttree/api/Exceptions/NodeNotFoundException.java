package org.pvb.persistenttree.api.Exceptions;

public class NodeNotFoundException extends RuntimeException {
    public NodeNotFoundException(String message) {
        super(message);
    }
}
