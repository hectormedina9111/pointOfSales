package com.medinasystems.pos.security;

/** Outcome of {@link RemotePasswordChanges#apply}. */
public enum RemotePasswordResult {
    /** The password was replaced. */
    APPLIED,
    /** The stored password is at least as recent: nothing changed. */
    STALE,
    /** No user with that id exists on this node. */
    USER_NOT_FOUND,
    /** The user is not assigned to the branch the store serves. */
    BRANCH_NOT_ALLOWED
}
