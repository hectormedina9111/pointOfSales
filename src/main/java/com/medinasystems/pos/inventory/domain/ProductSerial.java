package com.medinasystems.pos.inventory.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;

/**
 * One physical unit of a product that tracks serial numbers. A serial number is unique per product in the whole system:
 * a unit that returns to a branch keeps its row and changes branch and status.
 */
@Entity
@Table(name = "product_serial")
public class ProductSerial extends AuditableEntity {

    @Column(name = "product_id", nullable = false)
    private long productId;

    @Column(name = "serial_number", nullable = false, length = 100)
    private String serialNumber;

    @Column(name = "branch_id", nullable = false)
    private long branchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private SerialStatus status;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "sold_at")
    private Instant soldAt;

    @Column(name = "last_sale_id")
    private Long lastSaleId;

    @Column(name = "entry_movement_id")
    private Long entryMovementId;

    @Column(name = "synced_at")
    private Instant syncedAt;

    protected ProductSerial() {
    }

    /** A unit that enters a branch for the first time. The movement id is set when the movement is written. */
    public ProductSerial(long productId, String serialNumber, long branchId, Instant receivedAt) {
        this.productId = productId;
        this.serialNumber = serialNumber;
        this.branchId = branchId;
        this.status = SerialStatus.AVAILABLE;
        this.receivedAt = receivedAt;
    }

    /** The unit enters a branch (entry, return or positive count). */
    public void enter(long branchId, Instant at, long movementId) {
        this.branchId = branchId;
        this.status = SerialStatus.AVAILABLE;
        this.receivedAt = at;
        this.entryMovementId = movementId;
    }

    /** The unit leaves with a sale; the sale is kept as the last one. */
    public void sell(long saleId, Instant at) {
        this.status = SerialStatus.SOLD;
        this.soldAt = at;
        this.lastSaleId = saleId;
    }

    /** The unit leaves the stock with an exit or a negative count. */
    public void remove() {
        this.status = SerialStatus.REMOVED;
    }

    /** The unit is sent to another branch. It stays in transit until that branch receives it. */
    public void dispatch() {
        this.status = SerialStatus.IN_TRANSIT;
    }

    public long getProductId() {
        return productId;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public long getBranchId() {
        return branchId;
    }

    public SerialStatus getStatus() {
        return status;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getSoldAt() {
        return soldAt;
    }

    public Long getLastSaleId() {
        return lastSaleId;
    }

    public Long getEntryMovementId() {
        return entryMovementId;
    }

    public Instant getSyncedAt() {
        return syncedAt;
    }
}
