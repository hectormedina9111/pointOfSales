package com.medinasystems.pos.security.application;

/** The account is locked after too many failed logins. Rendered as HTTP 423. */
public class AccountLockedException extends RuntimeException {

    public AccountLockedException() {
        super("The account is temporarily locked after too many failed attempts");
    }
}
