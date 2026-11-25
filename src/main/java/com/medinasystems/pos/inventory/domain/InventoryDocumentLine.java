package com.medinasystems.pos.inventory.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;

/**
 * One product of an inventory document. {@code quantity} is the change the line applied, with its sign;
 * {@code countedQuantity} is set only on adjustments; {@code previousQuantity} is the stock before the line.
 */
@Entity
@Table(name = "inventory_document_line")
public class InventoryDocumentLine extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private InventoryDocument document;

    @Column(name = "product_id", nullable = false)
    private long productId;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "counted_quantity")
    private BigDecimal countedQuantity;

    @Column(name = "previous_quantity", nullable = false)
    private BigDecimal previousQuantity;

    @Column(name = "unit_cost")
    private BigDecimal unitCost;

    protected InventoryDocumentLine() {
    }

    InventoryDocumentLine(InventoryDocument document, long productId, BigDecimal quantity,
                          BigDecimal countedQuantity, BigDecimal previousQuantity, BigDecimal unitCost) {
        this.document = document;
        this.productId = productId;
        this.quantity = quantity;
        this.countedQuantity = countedQuantity;
        this.previousQuantity = previousQuantity;
        this.unitCost = unitCost;
    }

    public long getProductId() {
        return productId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getCountedQuantity() {
        return countedQuantity;
    }

    public BigDecimal getPreviousQuantity() {
        return previousQuantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }
}
