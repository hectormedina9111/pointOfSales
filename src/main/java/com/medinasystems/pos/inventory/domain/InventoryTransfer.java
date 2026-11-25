package com.medinasystems.pos.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;

/**
 * A transfer of stock from one branch to another, with its lines. It is created in the origin branch and replicated to every
 * store; the destination branch records the receipt on its copy. A transfer is never deleted.
 */
@Entity
@Table(name = "inventory_transfer")
public class InventoryTransfer extends AuditableEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String number;

    @Column(name = "origin_branch_id", nullable = false)
    private long originBranchId;

    @Column(name = "destination_branch_id", nullable = false)
    private long destinationBranchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransferStatus status;

    @Column(length = 500)
    private String notes;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    @Column(name = "sent_by", nullable = false)
    private long sentBy;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "received_by")
    private Long receivedBy;

    @Column(name = "receive_notes", length = 500)
    private String receiveNotes;

    @Column(name = "send_idempotency_key", unique = true, length = 64)
    private String sendIdempotencyKey;

    @Column(name = "receive_idempotency_key", unique = true, length = 64)
    private String receiveIdempotencyKey;

    @OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<InventoryTransferLine> lines = new ArrayList<>();

    protected InventoryTransfer() {
    }

    public InventoryTransfer(String number, long originBranchId, long destinationBranchId, String notes,
                             String sendIdempotencyKey, Instant sentAt, long sentBy) {
        this.number = number;
        this.originBranchId = originBranchId;
        this.destinationBranchId = destinationBranchId;
        this.status = TransferStatus.IN_TRANSIT;
        this.notes = notes;
        this.sendIdempotencyKey = sendIdempotencyKey;
        this.sentAt = sentAt;
        this.sentBy = sentBy;
    }

    public InventoryTransferLine addLine(long productId, BigDecimal quantity, BigDecimal unitCost) {
        InventoryTransferLine line = new InventoryTransferLine(this, productId, quantity, unitCost);
        lines.add(line);
        return line;
    }

    /** Records the receipt: the new status, who received it, when, and the key of the receipt. */
    public void receive(TransferStatus newStatus, Instant at, long by, String notes, String receiveKey) {
        this.status = newStatus;
        this.receivedAt = at;
        this.receivedBy = by;
        this.receiveNotes = notes;
        this.receiveIdempotencyKey = receiveKey;
    }

    public String getNumber() {
        return number;
    }

    public long getOriginBranchId() {
        return originBranchId;
    }

    public long getDestinationBranchId() {
        return destinationBranchId;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public long getSentBy() {
        return sentBy;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Long getReceivedBy() {
        return receivedBy;
    }

    public String getReceiveNotes() {
        return receiveNotes;
    }

    public String getSendIdempotencyKey() {
        return sendIdempotencyKey;
    }

    public String getReceiveIdempotencyKey() {
        return receiveIdempotencyKey;
    }

    public List<InventoryTransferLine> getLines() {
        return lines;
    }
}
