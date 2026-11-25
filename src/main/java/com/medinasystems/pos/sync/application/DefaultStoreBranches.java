package com.medinasystems.pos.sync.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.sync.StoreBranches;
import com.medinasystems.pos.sync.infrastructure.StoreNodeRepository;

/** Answers {@link StoreBranches} from the store node table. */
@Component
public class DefaultStoreBranches implements StoreBranches {

    private final StoreNodeRepository nodes;

    public DefaultStoreBranches(StoreNodeRepository nodes) {
        this.nodes = nodes;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isServedByStore(long branchId) {
        return nodes.existsByBranchId(branchId);
    }
}
