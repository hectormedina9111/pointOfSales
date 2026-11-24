package com.medinasystems.pos.shared;

/**
 * Thrown when a request is well formed but conflicts with the state of the node, for example a store that must
 * start again from a snapshot. Rendered as HTTP 409 in RFC 9457 format, with the code as a property.
 */
public class BusinessConflictException extends RuntimeException {

    private final String code;

    public BusinessConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
