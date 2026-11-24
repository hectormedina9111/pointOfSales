package com.medinasystems.pos.sync.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;

/**
 * Local identity of a store server, saved after activation. There is one row, and it exists only on a
 * store node. The store token is kept in clear because the store needs it to call the central node;
 * this database never leaves the store server.
 */
@Entity
@Table(name = "store_identity")
public class StoreIdentity extends AuditableEntity {

    @Column(name = "store_node_id", nullable = false)
    private long storeNodeId;

    @Column(name = "node_number", nullable = false)
    private short nodeNumber;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "id_range_start", nullable = false)
    private long idRangeStart;

    @Column(name = "id_range_end", nullable = false)
    private long idRangeEnd;

    @Column(name = "central_url", nullable = false, length = 300)
    private String centralUrl;

    @Column(name = "store_token", nullable = false, length = 100)
    private String storeToken;

    @Column(name = "activated_at", nullable = false)
    private Instant activatedAt;

    @Column(name = "last_applied_version", nullable = false)
    private long lastAppliedVersion;

    @Column(name = "snapshot_completed_at")
    private Instant snapshotCompletedAt;

    protected StoreIdentity() {
    }

    public StoreIdentity(long storeNodeId, int nodeNumber, Long branchId, long idRangeStart, long idRangeEnd,
                         String centralUrl, String storeToken, Instant activatedAt) {
        if (idRangeEnd <= idRangeStart) {
            throw new IllegalArgumentException("The id range end must be greater than its start");
        }
        this.storeNodeId = storeNodeId;
        this.nodeNumber = (short) nodeNumber;
        this.branchId = branchId;
        this.idRangeStart = idRangeStart;
        this.idRangeEnd = idRangeEnd;
        this.centralUrl = centralUrl;
        this.storeToken = storeToken;
        this.activatedAt = activatedAt;
        this.lastAppliedVersion = 0L;
    }

    public long getStoreNodeId() {
        return storeNodeId;
    }

    public int getNodeNumber() {
        return nodeNumber;
    }

    public Long getBranchId() {
        return branchId;
    }

    public long getIdRangeStart() {
        return idRangeStart;
    }

    public long getIdRangeEnd() {
        return idRangeEnd;
    }

    public String getCentralUrl() {
        return centralUrl;
    }

    public String getStoreToken() {
        return storeToken;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public long getLastAppliedVersion() {
        return lastAppliedVersion;
    }

    public Instant getSnapshotCompletedAt() {
        return snapshotCompletedAt;
    }
}
