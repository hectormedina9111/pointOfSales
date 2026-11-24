package com.medinasystems.pos.sync.application;

import java.util.Set;

import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.security.LoginNotAllowedException;
import com.medinasystems.pos.security.LoginPolicy;
import com.medinasystems.pos.sync.domain.StoreIdentity;
import com.medinasystems.pos.sync.infrastructure.StoreIdentityRepository;

/**
 * Store side: a user may sign in only if this store has no branch of its own, or the user has that branch
 * in user_branch. Replaces the permissive default policy of the security module.
 */
@Component
@Profile("store")
@Primary
public class StoreLoginPolicy implements LoginPolicy {

    static final String BRANCH_NOT_ALLOWED = "STORE_BRANCH_NOT_ALLOWED";

    private final StoreIdentityRepository identities;

    public StoreLoginPolicy(StoreIdentityRepository identities) {
        this.identities = identities;
    }

    @Override
    @Transactional(readOnly = true)
    public void checkCanLogin(long userId, Set<Long> branchIds) {
        // Before activation there is no identity and no replicated user, so nothing is restricted yet.
        Long storeBranchId = identities.findFirstByOrderByIdAsc()
                .map(StoreIdentity::getBranchId)
                .orElse(null);
        if (storeBranchId == null || branchIds.contains(storeBranchId)) {
            return;
        }
        throw new LoginNotAllowedException(BRANCH_NOT_ALLOWED);
    }
}
