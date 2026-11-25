package com.medinasystems.pos.security;

import java.time.Instant;

/**
 * Public API of the security module for a password changed on another node. A store uploads its change; the
 * central node applies it here, keeping the most recent change of each user.
 */
public interface RemotePasswordChanges {

    /**
     * Applies a password changed on a store, when it is the most recent change of the user.
     *
     * @param passwordHash     BCrypt hash of the new password; the clear password never travels
     * @param changedAt        when the store applied the change
     * @param requiredBranchId branch the store serves: the user must be assigned to it. Null skips the check.
     * @return what happened; STALE means a newer password is already stored and will reach the store by replication
     */
    RemotePasswordResult apply(long userId, String passwordHash, Instant changedAt, Long requiredBranchId);
}
