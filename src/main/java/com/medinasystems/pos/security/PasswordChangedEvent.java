package com.medinasystems.pos.security;

import java.time.Instant;

/**
 * Published in the same transaction as a password change made on this node. It carries the hash only, so a store
 * can queue it for the central node. {@link #toString()} leaves the hash out, so logs never show it.
 */
public record PasswordChangedEvent(long userId, String passwordHash, Instant changedAt) {

    @Override
    public String toString() {
        return "PasswordChangedEvent[userId=" + userId + ", changedAt=" + changedAt + "]";
    }
}
