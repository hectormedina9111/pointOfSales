package com.medinasystems.pos.sync.application;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.shared.BranchScope;
import com.medinasystems.pos.sync.domain.StoreIdentity;
import com.medinasystems.pos.sync.infrastructure.StoreIdentityRepository;

/**
 * Store side: a store sees only the branch the central node assigned to it, and none until it has one.
 * It is read on each call, because the central node can change the assignment while the store runs.
 */
@Component
@Profile("store")
public class StoreBranchScope implements BranchScope {

    private final StoreIdentityRepository identities;

    public StoreBranchScope(StoreIdentityRepository identities) {
        this.identities = identities;
    }

    @Override
    public boolean allBranches() {
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Long> nodeBranchId() {
        return identities.findFirstByOrderByIdAsc().map(StoreIdentity::getBranchId);
    }
}
