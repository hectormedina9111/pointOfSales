package com.medinasystems.pos.shared;

/**
 * Thrown when the central node receives a call that only a store may accept, such as a cash movement.
 * Rendered as HTTP 403 in RFC 9457 format.
 */
public class StoreOnlyOperationException extends RuntimeException {

    public StoreOnlyOperationException() {
        super("This operation is accepted only on a store node.");
    }
}
