package com.medinasystems.pos.shared;

/**
 * Thrown when a store node receives a call that only the central node may accept.
 * Rendered as HTTP 403 in RFC 9457 format.
 */
public class ReadOnlyNodeException extends RuntimeException {

    public ReadOnlyNodeException() {
        super("This node is a store; master data is changed on the central node.");
    }
}
