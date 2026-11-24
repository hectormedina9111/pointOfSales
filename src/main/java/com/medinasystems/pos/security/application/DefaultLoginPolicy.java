package com.medinasystems.pos.security.application;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.medinasystems.pos.security.LoginPolicy;

/** Allows every login. The store profile replaces it with a stricter policy from the sync module. */
@Component
public class DefaultLoginPolicy implements LoginPolicy {

    @Override
    public void checkCanLogin(long userId, Set<Long> branchIds) {
        // No extra rule on the central node.
    }
}
