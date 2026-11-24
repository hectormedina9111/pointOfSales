package com.medinasystems.pos.sync.application;

import java.time.Instant;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.sync.infrastructure.StoreIdentityRepository;

/** Store side: records the progress and the outcome of each sync cycle in the local identity row. */
@Service
@Profile("store")
@Transactional
public class StoreSyncState {

    private final StoreIdentityRepository identities;

    public StoreSyncState(StoreIdentityRepository identities) {
        this.identities = identities;
    }

    public void completeSnapshot(long version, Instant now) {
        identities.findFirstByOrderByIdAsc().ifPresent(identity -> identity.completeSnapshot(version, now));
    }

    public void recordSuccess(Instant now) {
        identities.findFirstByOrderByIdAsc().ifPresent(identity -> identity.recordSyncSuccess(now));
    }

    public void recordError(Instant now, String message) {
        identities.findFirstByOrderByIdAsc().ifPresent(identity -> identity.recordSyncError(now, message));
    }
}
