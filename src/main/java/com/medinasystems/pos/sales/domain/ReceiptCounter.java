package com.medinasystems.pos.sales.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * The receipt counter of a cash register. It lives in the store only and is not replicated: each store numbers its own
 * receipts. The row is locked while a sale is charged, so two receipts of one register never get the same number.
 * The key is the cash register id, so the entity does not generate one.
 */
@Entity
@Table(name = "cash_register_counter")
@EntityListeners(AuditingEntityListener.class)
public class ReceiptCounter {

    @Id
    @Column(name = "cash_register_id")
    private Long cashRegisterId;

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

    public ReceiptCounter(long cashRegisterId) {
        this.cashRegisterId = cashRegisterId;
        this.lastNumber = 0;
    }

    /** Takes the next number of the register: 1, then 2, and so on. */
    public long next() {
        lastNumber = lastNumber + 1;
        return lastNumber;
    }

    public Long getCashRegisterId() {
        return cashRegisterId;
    }

    public long getLastNumber() {
        return lastNumber;
    }
}
