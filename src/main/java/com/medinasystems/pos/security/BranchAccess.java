package com.medinasystems.pos.security;

import java.util.Set;

/**
 * Public API of the security module for branch visibility. Other modules use it to filter
 * their queries by the branches of the current user.
 */
public interface BranchAccess {

    /** Ids of the branches the user can see. Empty when the user has none. */
    Set<Long> branchIdsOf(long userId);

    /** Makes one more branch visible to the user. */
    void grant(long userId, long branchId);

    /**
     * Id of the user who made the current request.
     *
     * @throws IllegalStateException when the request is not authenticated with a user token
     */
    long currentUserId();
}
