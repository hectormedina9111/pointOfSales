package com.medinasystems.pos.sync.application;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.shared.AuditEvent;
import com.medinasystems.pos.sync.domain.StoreIdentity;
import com.medinasystems.pos.sync.infrastructure.IdRangeSequenceAdjuster;
import com.medinasystems.pos.sync.infrastructure.StoreIdentityRepository;

/** Store side: keeps the store's identity and moves its id sequences into its range. */
@Service
@Profile("store")
@Transactional
public class StoreActivationService {

    private final StoreIdentityRepository identities;
    private final IdRangeSequenceAdjuster sequences;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public StoreActivationService(StoreIdentityRepository identities, IdRangeSequenceAdjuster sequences,
                                  ApplicationEventPublisher events, Clock clock) {
        this.identities = identities;
        this.sequences = sequences;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isActivated() {
        return identities.count() > 0;
    }

    /** Saves the identity received from the central node and adjusts the sequences. The token is not audited. */
    public void saveActivation(ActivationResult result, String centralUrl) {
        StoreIdentity identity = identities.save(new StoreIdentity(result.storeNodeId(), result.nodeNumber(),
                result.branchId(), result.idRangeStart(), result.idRangeEnd(), centralUrl, result.storeToken(),
                clock.instant()));
        sequences.adjust(identity.getIdRangeStart(), identity.getIdRangeEnd());
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("storeNodeId", identity.getStoreNodeId());
        values.put("nodeNumber", identity.getNodeNumber());
        values.put("branchId", identity.getBranchId());
        values.put("idRangeStart", identity.getIdRangeStart());
        values.put("idRangeEnd", identity.getIdRangeEnd());
        events.publishEvent(AuditEvent.created("STORE_ACTIVATED", "store_identity", identity.getId(), values));
    }

    /** At every startup of an activated store: keeps the sequences inside the range. */
    public void adjustSequences() {
        identities.findFirstByOrderByIdAsc()
                .ifPresent(identity -> sequences.adjust(identity.getIdRangeStart(), identity.getIdRangeEnd()));
    }
}
