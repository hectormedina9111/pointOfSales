package com.medinasystems.pos.shared;

/**
 * Thrown when a request is well formed but breaks a business rule, for example
 * a duplicated SKU. Rendered as HTTP 422 in RFC 9457 format.
 *
 * @see #getCode() a stable code clients can rely on, such as {@code product.sku.duplicated}
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;
    private final Object[] arguments;

    public BusinessRuleException(String code, String message) {
        this(code, message, new Object[0]);
    }

    /**
     * @param arguments values for the placeholders of the translated message, such as the product that failed
     */
    public BusinessRuleException(String code, String message, Object... arguments) {
        super(message);
        this.code = code;
        this.arguments = arguments.clone();
    }

    public String getCode() {
        return code;
    }

    /** The values for the placeholders of the translated message; empty when there are none. */
    public Object[] getArguments() {
        return arguments.clone();
    }
}
