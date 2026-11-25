package com.medinasystems.pos.inventory.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;

/**
 * One product of a transfer. {@code quantity} is the quantity sent; {@code receivedQuantity} stays empty until the destination
 * receives it; {@code unitCost} is the average cost of the origin branch when the transfer left.
 */
@Entity
@Table(name = "inventory_transfer_line")
public class InventoryTransferLine extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transfer_id", nullable = false)
    private InventoryTransfer transfer;

    @Column(name = "product_id", nullable = false)
    private long productId;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "received_quantity")
    private BigDecimal receivedQuantity;

    @Column(name = "unit_cost", nullable = false)
    private BigDecimal unitCost;

    @OneToMany(mappedBy = "line", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<InventoryTransferSerial> serials = new ArrayList<>();

    protected InventoryTransferLine() {
    }

    InventoryTransferLine(InventoryTransfer transfer, long productId, BigDecimal quantity, BigDecimal unitCost) {
        this.transfer = transfer;
        this.productId = productId;
        this.quantity = quantity;
        this.unitCost = unitCost;
    }

    /** One serial number of the line, which the destination may or may not receive. */
    public InventoryTransferSerial addSerial(String serialNumber) {
        InventoryTransferSerial serial = new InventoryTransferSerial(this, serialNumber);
        serials.add(serial);
        return serial;
    }

    public void recordReceived(BigDecimal receivedQuantity) {
        this.receivedQuantity = receivedQuantity;
    }

    public long getProductId() {
        return productId;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getReceivedQuantity() {
        return receivedQuantity;
    }

    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public List<InventoryTransferSerial> getSerials() {
        return serials;
    }
}
