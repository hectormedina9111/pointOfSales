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
 * An inventory document: an entry, an exit or a count of one branch, with its lines. A document is never deleted; its
 * movements stay in the log. Its number and its idempotency key are unique.
 */
@Entity
@Table(name = "inventory_document")
public class InventoryDocument extends AuditableEntity {

    @Column(nullable = false, unique = true, length = 30)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private InventoryDocumentType type;

    @Column(name = "branch_id", nullable = false)
    private long branchId;

    @Column(length = 200)
    private String reason;

    @Column(length = 500)
    private String notes;

    @Column(name = "idempotency_key", unique = true, length = 64)
    private String idempotencyKey;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @Column(name = "user_id", nullable = false)
    private long userId;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<InventoryDocumentLine> lines = new ArrayList<>();

    protected InventoryDocument() {
    }

    public InventoryDocument(String number, InventoryDocumentType type, long branchId, String reason, String notes,
                             String idempotencyKey, Instant postedAt, long userId) {
        this.number = number;
        this.type = type;
        this.branchId = branchId;
        this.reason = reason;
        this.notes = notes;
        this.idempotencyKey = idempotencyKey;
        this.postedAt = postedAt;
        this.userId = userId;
    }

    public void addLine(long productId, BigDecimal quantity, BigDecimal countedQuantity,
                        BigDecimal previousQuantity, BigDecimal unitCost) {
        lines.add(new InventoryDocumentLine(this, productId, quantity, countedQuantity, previousQuantity, unitCost));
    }

    public String getNumber() {
        return number;
    }

    public InventoryDocumentType getType() {
        return type;
    }

    public long getBranchId() {
        return branchId;
    }

    public String getReason() {
        return reason;
    }

    public String getNotes() {
        return notes;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public long getUserId() {
        return userId;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }

    public List<InventoryDocumentLine> getLines() {
        return lines;
    }
}
