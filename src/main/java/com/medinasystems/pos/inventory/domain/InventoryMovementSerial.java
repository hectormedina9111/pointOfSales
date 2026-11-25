package com.medinasystems.pos.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.medinasystems.pos.shared.AuditableEntity;

/** Links a movement of the log with one serial unit that it moved, for tracing. Rows are never changed. */
@Entity
@Table(name = "inventory_movement_serial")
public class InventoryMovementSerial extends AuditableEntity {

    @Column(name = "movement_id", nullable = false)
    private long movementId;

    @Column(name = "serial_id", nullable = false)
    private long serialId;

    protected InventoryMovementSerial() {
    }

    public InventoryMovementSerial(long movementId, long serialId) {
        this.movementId = movementId;
        this.serialId = serialId;
    }

    public long getMovementId() {
        return movementId;
    }

    public long getSerialId() {
        return serialId;
    }
}
