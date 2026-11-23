package com.medinasystems.pos.shared;

/**
 * Thrown when a request is well formed but breaks a business rule, for example
 * a duplicated SKU. Rendered as HTTP 422 in RFC 9457 format.
 *
 * @see #getCode() a stable code clients can rely on, such as {@code product.sku.duplicated}
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;

    public BusinessRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
