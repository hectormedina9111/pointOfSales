package com.medinasystems.pos.security.application;

/** Wrong username or password, or an invalid refresh token. Rendered as HTTP 401. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid username or password");
    }

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
