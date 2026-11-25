package com.medinasystems.pos.security;

import java.util.Optional;

/** Public API of the security module for other modules: who a user is, for the sales tickets. */
public interface UserDirectory {

    /** The user's username and full name. Empty when the user does not exist. */
    Optional<UserInfo> find(long userId);
}
