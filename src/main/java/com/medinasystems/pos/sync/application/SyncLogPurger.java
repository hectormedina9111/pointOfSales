package com.medinasystems.pos.sync.application;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.medinasystems.pos.sync.infrastructure.SyncLogPurgeStore;

/**
 * Central node only: deletes old rows of the change log that every active store already has. A row is removed
 * only when its version is at or below the lowest version downloaded by the active stores AND it is older than
 * 30 days. A store that falls behind the purged part gets SNAPSHOT_REQUIRED on its next request and loads a full
 * snapshot again. With no active stores, nothing is deleted.
 */
@Service
@Profile("central")
public class SyncLogPurger {

    private static final Logger log = LoggerFactory.getLogger(SyncLogPurger.class);

    private final SyncLogPurgeStore store;

    public SyncLogPurger(SyncLogPurgeStore store) {
        this.store = store;
    }

    @Scheduled(cron = "${pos.sync.purge-cron:0 0 3 * * *}")
    @Transactional
    public void purge() {
        Optional<Long> bound = store.minActiveDownloadVersion();
        if (bound.isEmpty()) {
            log.info("Sync log purge: no active stores, nothing deleted");
            return;
        }
        int deleted = store.purge(bound.get());
        log.info("Sync log purge: {} changes deleted, up to version {}", deleted, bound.get());
    }
}
