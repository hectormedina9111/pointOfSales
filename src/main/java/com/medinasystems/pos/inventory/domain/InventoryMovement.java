package com.medinasystems.pos.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.medinasystems.pos.inventory.StockMovementType;
import com.medinasystems.pos.inventory.StockReferenceType;
import com.medinasystems.pos.shared.AuditableEntity;

/**
 * One line of the movement log. Rows are never changed or deleted: the balance of a product is the sum of its quantities.
 */
@Entity
@Table(name = "inventory_movement")
public class InventoryMovement extends AuditableEntity {

    @Column(name = "branch_id", nullable = false)
    private long branchId;

    @Column(name = "product_id", nullable = false)
    private long productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockMovementType type;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "resulting_balance", nullable = false)
    private BigDecimal resultingBalance;

    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost;

    @Column(name = "resulting_average_cost", nullable = false)
    private BigDecimal resultingAverageCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 20)
    private StockReferenceType referenceType;

    @Column(name = "reference_id", nullable = false)
    private long referenceId;

    @Column(name = "moved_at", nullable = false)
    private Instant movedAt;

    @Column(name = "user_id", nullable = false)
    private long userId;

    protected InventoryMovement() {
    }

    public InventoryMovement(long branchId, long productId, StockMovementType type, BigDecimal quantity,
                             BigDecimal resultingBalance, BigDecimal unitCost, BigDecimal resultingAverageCost,
                             StockReferenceType referenceType, long referenceId, Instant movedAt, long userId) {
        this.branchId = branchId;
        this.productId = productId;
        this.type = type;
        this.quantity = quantity;
        this.resultingBalance = resultingBalance;
        this.unitCost = unitCost;
        this.resultingAverageCost = resultingAverageCost;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.movedAt = movedAt;
        this.userId = userId;
    }

    public long getBranchId() {
        return branchId;
    }

    public long getProductId() {
        return productId;
    }

    public StockMovementType getType() {
        return type;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getResultingBalance() {
        return resultingBalance;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public BigDecimal getResultingAverageCost() {
        return resultingAverageCost;
    }

    public StockReferenceType getReferenceType() {
        return referenceType;
    }

    public long getReferenceId() {
        return referenceId;
    }

    public Instant getMovedAt() {
        return movedAt;
    }

    public long getUserId() {
        return userId;
    }
}
