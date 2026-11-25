package com.medinasystems.pos.shared;

/**
 * Thrown when a requested resource does not exist, and the reason needs its own code, such as
 * {@code CREDIT_NOTE_NOT_FOUND}. Rendered as HTTP 404 in RFC 9457 format, with the code as a property.
 */
public class BusinessNotFoundException extends RuntimeException {

    private final String code;

    public BusinessNotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
