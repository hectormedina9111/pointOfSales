package com.medinasystems.pos.sales.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;

/**
 * A quantity of one sale line held by a return, keyed by the return's idempotency key. The central node creates the
 * claim when a store asks for it, and replicates it to every store, so each store knows what other branches returned.
 * Rows are never deleted: a released claim stays, as RELEASED. The ids of the sale and line carry no foreign key, so the
 * claim can arrive before the rows it points to.
 */
@Entity
@Table(name = "sale_return_claim")
public class SaleReturnClaim extends AuditableEntity {

    @Column(name = "claim_key", nullable = false, length = 100)
    private String claimKey;

    @Column(name = "sale_id", nullable = false)
    private Long saleId;

    @Column(name = "sale_line_id", nullable = false)
    private Long saleLineId;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantity;

    @Column(name = "store_node_id", nullable = false)
    private Long storeNodeId;

    @Column(name = "return_sale_id")
    private Long returnSaleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReturnClaimStatus status;

    protected SaleReturnClaim() {
    }

    /** A new claim, reserved for the store that asked. */
    public SaleReturnClaim(String claimKey, long saleId, long saleLineId, BigDecimal quantity, long storeNodeId) {
        this.claimKey = claimKey;
        this.saleId = saleId;
        this.saleLineId = saleLineId;
        this.quantity = quantity;
        this.storeNodeId = storeNodeId;
        this.status = ReturnClaimStatus.RESERVED;
    }

    /** A released claim asked again with its key: the quantity is held again. */
    public void reserve(BigDecimal newQuantity, long newStoreNodeId) {
        this.quantity = newQuantity;
        this.storeNodeId = newStoreNodeId;
        this.status = ReturnClaimStatus.RESERVED;
    }

    /** The return reached the central node: the quantity is now part of the sale's returned quantity. */
    public void confirm(long returnSaleId) {
        this.returnSaleId = returnSaleId;
        this.status = ReturnClaimStatus.CONFIRMED;
    }

    /** The return failed: the quantity is free again. */
    public void release() {
        this.status = ReturnClaimStatus.RELEASED;
    }

    public boolean isReserved() {
        return status == ReturnClaimStatus.RESERVED;
    }

    public boolean isHolding() {
        return status == ReturnClaimStatus.RESERVED || status == ReturnClaimStatus.CONFIRMED;
    }

    public String getClaimKey() {
        return claimKey;
    }

    public Long getSaleId() {
        return saleId;
    }

    public Long getSaleLineId() {
        return saleLineId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public Long getStoreNodeId() {
        return storeNodeId;
    }

    public Long getReturnSaleId() {
        return returnSaleId;
    }

    public ReturnClaimStatus getStatus() {
        return status;
    }
}
