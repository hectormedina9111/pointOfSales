package com.medinasystems.pos.sync.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;
import com.medinasystems.pos.shared.BusinessRuleException;

/**
 * A store registered in the central node. The central node is not a row here: it is node 0, with
 * ids 1 to 999 999 999, fixed in the code. Store N gets the ids from N x 1 000 000 000 + 1 up to
 * N x 1 000 000 000 + 999 999 999.
 *
 * <p>Two secrets are kept only as SHA-256 hashes: the one-time activation code and the permanent store
 * token. Revoking a store releases its branch, so a replacement store can be registered for it.
 */
@Entity
@Table(name = "store_node")
public class StoreNode extends AuditableEntity {

    /** Ids available to each store: 999 999 999 plus the first id of the range. */
    public static final long RANGE_SIZE = 1_000_000_000L;
    /** Node numbers are stored as SMALLINT. */
    public static final int MAX_NODE_NUMBER = Short.MAX_VALUE;
    static final Duration ACTIVATION_CODE_TTL = Duration.ofHours(24);

    @Column(name = "node_number", nullable = false, unique = true)
    private short nodeNumber;

    @Column(name = "branch_id", unique = true)
    private Long branchId;

    @Column(name = "id_range_start", nullable = false)
    private long idRangeStart;

    @Column(name = "id_range_end", nullable = false)
    private long idRangeEnd;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private StoreNodeStatus status;

    @Column(name = "activation_code_hash", length = 64, unique = true)
    private String activationCodeHash;

    @Column(name = "activation_expires_at")
    private Instant activationExpiresAt;

    @Column(name = "credential_hash", length = 64, unique = true)
    private String credentialHash;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "credential_revoked_at")
    private Instant credentialRevokedAt;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "last_download_version")
    private Long lastDownloadVersion;

    @Column(name = "last_upload_at")
    private Instant lastUploadAt;

    @Column(name = "app_version", length = 20)
    private String appVersion;

    @Column(name = "pending_upload_count", nullable = false)
    private int pendingUploadCount;

    @Column(name = "oldest_pending_at")
    private Instant oldestPendingAt;

    protected StoreNode() {
    }

    /** A new store, pending activation. The branch is optional. */
    public StoreNode(int nodeNumber, Long branchId) {
        if (nodeNumber < 1 || nodeNumber > MAX_NODE_NUMBER) {
            throw new IllegalArgumentException("Store node number must be between 1 and " + MAX_NODE_NUMBER);
        }
        this.nodeNumber = (short) nodeNumber;
        this.branchId = branchId;
        this.idRangeStart = nodeNumber * RANGE_SIZE + 1;
        this.idRangeEnd = nodeNumber * RANGE_SIZE + RANGE_SIZE - 1;
        this.status = StoreNodeStatus.PENDING;
    }

    /**
     * Creates a new one-time activation code, valid for 24 hours, and returns it in clear. Only its hash is
     * kept, so the code is shown once. A store that is already active can get a new code to reinstall.
     */
    public String generateActivationCode(Clock clock) {
        String code = StoreTokens.newActivationCode();
        prepareActivation(StoreTokens.sha256Hex(code), clock.instant());
        return code;
    }

    /** Stores the hash of an activation code that expires 24 hours after {@code now}. */
    public void prepareActivation(String codeHash, Instant now) {
        if (isRevoked()) {
            throw new BusinessRuleException("STORE_REVOKED", "The store was revoked and cannot be activated again");
        }
        this.activationCodeHash = codeHash;
        this.activationExpiresAt = now.plus(ACTIVATION_CODE_TTL);
    }

    /**
     * Exchanges the activation code for the permanent store token. The code is consumed, so it cannot be used
     * again. Returns the token in clear; only its hash is kept.
     *
     * @param codeHash SHA-256 of the code the store sent
     * @throws BusinessRuleException STORE_REVOKED when the store was revoked, or INVALID_ACTIVATION_CODE when
     *                               the code does not match, is used or has expired
     */
    public String activate(String codeHash, Instant now) {
        if (isRevoked()) {
            throw new BusinessRuleException("STORE_REVOKED", "The store was revoked and cannot be activated again");
        }
        if (codeHash == null || !codeHash.equals(activationCodeHash)
                || activationExpiresAt == null || !now.isBefore(activationExpiresAt)) {
            throw new BusinessRuleException("INVALID_ACTIVATION_CODE", "The activation code is invalid or has expired");
        }
        String token = StoreTokens.newStoreToken();
        this.credentialHash = StoreTokens.sha256Hex(token);
        this.activationCodeHash = null;
        this.activationExpiresAt = null;
        this.activatedAt = now;
        this.status = StoreNodeStatus.ACTIVE;
        return token;
    }

    /**
     * Revokes the store: its credential stops working and its branch is released, so another store can take
     * it. Revoking twice does nothing. The activation hash, if any, stays and is refused as STORE_REVOKED.
     */
    public void revoke(Instant now) {
        if (isRevoked()) {
            return;
        }
        this.credentialRevokedAt = now;
        this.credentialHash = null;
        this.branchId = null;
        this.status = StoreNodeStatus.DISABLED;
    }

    /** True when a request carrying this store's token may be accepted. */
    public boolean canAuthenticate() {
        return status == StoreNodeStatus.ACTIVE && credentialHash != null && !isRevoked();
    }

    public boolean isRevoked() {
        return credentialRevokedAt != null;
    }

    public String getCredentialHash() {
        return credentialHash;
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

    public StoreNodeStatus getStatus() {
        return status;
    }

    public Instant getActivationExpiresAt() {
        return activationExpiresAt;
    }

    public Instant getActivatedAt() {
        return activatedAt;
    }

    public Instant getCredentialRevokedAt() {
        return credentialRevokedAt;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public Long getLastDownloadVersion() {
        return lastDownloadVersion;
    }

    public Instant getLastUploadAt() {
        return lastUploadAt;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public int getPendingUploadCount() {
        return pendingUploadCount;
    }

    public Instant getOldestPendingAt() {
        return oldestPendingAt;
    }
}
