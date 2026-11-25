package com.medinasystems.pos.sync.web;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.medinasystems.pos.sync.application.CentralSyncService;
import com.medinasystems.pos.sync.application.CentralUploadService;
import com.medinasystems.pos.sync.application.SyncProtocol.AckRequest;
import com.medinasystems.pos.sync.application.SyncProtocol.ChangesPage;
import com.medinasystems.pos.sync.application.SyncProtocol.HeartbeatRequest;
import com.medinasystems.pos.sync.application.SyncProtocol.HeartbeatResponse;
import com.medinasystems.pos.sync.application.SyncProtocol.SnapshotPage;
import com.medinasystems.pos.sync.application.SyncProtocol.SnapshotVersion;
import com.medinasystems.pos.sync.application.SyncProtocol.UploadRequest;
import com.medinasystems.pos.sync.application.SyncProtocol.UploadResponse;

/**
 * Replication endpoints that stores call on the central node. Every route needs the store token (the filter in
 * {@link StoreTokenAuthenticationFilter} covers /api/v1/sync/*) and none needs a privilege: EndpointPrivileges
 * exempts them. Central node only: a store never serves them.
 */
@RestController
@RequestMapping("/api/v1/sync")
@Profile("central")
public class SyncReplicationController {

    private final CentralSyncService sync;
    private final CentralUploadService uploadService;

    public SyncReplicationController(CentralSyncService sync, CentralUploadService uploadService) {
        this.sync = sync;
        this.uploadService = uploadService;
    }

    @GetMapping("/snapshot/version")
    public SnapshotVersion snapshotVersion() {
        return sync.snapshotVersion();
    }

    @GetMapping("/snapshot")
    public SnapshotPage snapshot(@RequestParam String table, @RequestParam(defaultValue = "0") long afterId,
                                 @RequestParam(defaultValue = "1000") int limit) {
        return sync.snapshot(table, afterId, limit);
    }

    @GetMapping("/changes")
    public ChangesPage changes(@RequestParam(defaultValue = "0") long sinceVersion,
                               @RequestParam(defaultValue = "500") int limit) {
        return sync.changes(sinceVersion, limit);
    }

    @PostMapping("/ack")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ack(@RequestAttribute(StoreTokenAuthenticationFilter.STORE_NODE_ATTRIBUTE) long storeNodeId,
                    @Valid @RequestBody AckRequest request) {
        sync.acknowledge(storeNodeId, request.version());
    }

    /** Outbox rows sent by a store (for example, password changes made there). Answers one result per item. */
    @PostMapping("/uploads")
    public UploadResponse uploads(@RequestAttribute(StoreTokenAuthenticationFilter.STORE_NODE_ATTRIBUTE) long storeNodeId,
                                  @Valid @RequestBody UploadRequest request) {
        return uploadService.upload(storeNodeId, request.items());
    }

    @PostMapping("/heartbeat")
    public HeartbeatResponse heartbeat(@RequestAttribute(StoreTokenAuthenticationFilter.STORE_NODE_ATTRIBUTE) long storeNodeId,
                                       @Valid @RequestBody HeartbeatRequest request) {
        return sync.heartbeat(storeNodeId, request);
    }
}
