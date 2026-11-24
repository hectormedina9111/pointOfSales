package com.medinasystems.pos.security;

/**
 * The password is right, but this node does not let the user sign in. The code names the reason,
 * for example {@code STORE_BRANCH_NOT_ALLOWED}. Rendered as HTTP 403 in RFC 9457 format.
 */
public class LoginNotAllowedException extends RuntimeException {

    private final String code;

    public LoginNotAllowedException(String code) {
        super("Sign-in is not allowed on this node: " + code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
