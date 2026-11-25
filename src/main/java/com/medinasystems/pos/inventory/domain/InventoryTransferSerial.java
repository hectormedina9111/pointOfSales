package com.medinasystems.pos.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;

/** A serial number sent in a transfer line, and whether the destination received that unit. */
@Entity
@Table(name = "inventory_transfer_line_serial")
public class InventoryTransferSerial extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "line_id", nullable = false)
    private InventoryTransferLine line;

    @Column(name = "serial_number", nullable = false, length = 100)
    private String serialNumber;

    @Column(nullable = false)
    private boolean received;

    protected InventoryTransferSerial() {
    }

    InventoryTransferSerial(InventoryTransferLine line, String serialNumber) {
        this.line = line;
        this.serialNumber = serialNumber;
        this.received = false;
    }

    public void markReceived(boolean received) {
        this.received = received;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public boolean isReceived() {
        return received;
    }
}
