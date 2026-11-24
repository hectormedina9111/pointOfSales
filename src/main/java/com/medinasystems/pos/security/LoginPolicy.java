package com.medinasystems.pos.security;

import java.util.Set;

/**
 * Extra rules for signing in, checked after the password. Other modules may provide the
 * implementation; the default one allows every login. The store profile uses it to refuse users
 * whose branches this store does not serve.
 */
public interface LoginPolicy {

    /**
     * Throws {@link LoginNotAllowedException} when the user may not sign in on this node.
     *
     * @param userId    id of the user who is signing in
     * @param branchIds branches the user can see
     */
    void checkCanLogin(long userId, Set<Long> branchIds);
}
