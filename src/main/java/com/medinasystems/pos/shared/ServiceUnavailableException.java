package com.medinasystems.pos.shared;

/**
 * Thrown when an operation needs another node or service that cannot be reached right now. Rendered as HTTP 503 in
 * RFC 9457 format, with the code as a property.
 */
public class ServiceUnavailableException extends RuntimeException {

    private final String code;

    public ServiceUnavailableException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
