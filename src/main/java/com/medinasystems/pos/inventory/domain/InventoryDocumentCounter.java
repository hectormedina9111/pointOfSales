package com.medinasystems.pos.inventory.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * The document counter of a branch, one per kind of document, so entries, exits and counts number separately. The row is
 * locked while a document is numbered, so two documents of one branch never get the same number.
 */
@Entity
@Table(name = "inventory_document_counter")
@IdClass(InventoryDocumentCounterKey.class)
@EntityListeners(AuditingEntityListener.class)
public class InventoryDocumentCounter {

    @Id
    @Column(name = "branch_id")
    private Long branchId;

    @Id
    @Column(name = "type", length = 15)
    private String documentType;

    @Column(name = "last_number", nullable = false)
    private long lastNumber;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 50)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", nullable = false, length = 50)
    private String updatedBy;

    @Version
    private Integer version;

    protected InventoryDocumentCounter() {
    }

    public InventoryDocumentCounter(long branchId, String documentType) {
        this.branchId = branchId;
        this.documentType = documentType;
        this.lastNumber = 0;
    }

    /** Takes the next number of the branch for this document type: 1, then 2, and so on. */
    public long next() {
        lastNumber = lastNumber + 1;
        return lastNumber;
    }

    public long getLastNumber() {
        return lastNumber;
    }
}
