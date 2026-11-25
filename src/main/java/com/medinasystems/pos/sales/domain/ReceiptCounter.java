package com.medinasystems.pos.sales.domain;

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
 * The receipt counter of a cash register, one per kind of document: sales, returns and credit notes each have their own
 * sequence. It lives in the store only and is not replicated. The row is locked while a document is numbered, so two
 * documents of one register never get the same number.
 */
@Entity
@Table(name = "cash_register_counter")
@IdClass(ReceiptCounterKey.class)
@EntityListeners(AuditingEntityListener.class)
public class ReceiptCounter {

    public static final String SALE = "SALE";
    public static final String RETURN = "RETURN";
    public static final String CREDIT_NOTE = "CREDIT_NOTE";

    @Id
    @Column(name = "cash_register_id")
    private Long cashRegisterId;

    @Id
    @Column(name = "document_type", length = 20)
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

    protected ReceiptCounter() {
    }

    public ReceiptCounter(long cashRegisterId, String documentType) {
        this.cashRegisterId = cashRegisterId;
        this.documentType = documentType;
        this.lastNumber = 0;
    }

    /** Takes the next number of the register for this document type: 1, then 2, and so on. */
    public long next() {
        lastNumber = lastNumber + 1;
        return lastNumber;
    }

    public Long getCashRegisterId() {
        return cashRegisterId;
    }

    public String getDocumentType() {
        return documentType;
    }

    public long getLastNumber() {
        return lastNumber;
    }
}
