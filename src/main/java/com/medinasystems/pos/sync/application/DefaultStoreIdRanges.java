package com.medinasystems.pos.sync.application;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.sync.StoreIdRanges;
import com.medinasystems.pos.sync.infrastructure.StoreNodeRepository;

/** Answers {@link StoreIdRanges} from the store node table. Used by the central node only. */
@Component
@Profile("central")
public class DefaultStoreIdRanges implements StoreIdRanges {

    private final StoreNodeRepository nodes;

    public DefaultStoreIdRanges(StoreNodeRepository nodes) {
        this.nodes = nodes;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean contains(long storeNodeId, long id) {
        return nodes.findById(storeNodeId)
                .map(node -> id >= node.getIdRangeStart() && id <= node.getIdRangeEnd())
                .orElse(false);
    }
}
