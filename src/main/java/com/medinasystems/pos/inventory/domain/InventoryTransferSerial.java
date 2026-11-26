package com.medinasystems.pos.inventory.domain;

import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    /** The equipment data of the unit as it was sent, so the destination creates the equipment with them. */
    @Column(name = "external_code", length = 50)
    private String externalCode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attributes", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> attributes = Map.of();

    protected InventoryTransferSerial() {
    }

    InventoryTransferSerial(InventoryTransferLine line, String serialNumber, String externalCode,
                            Map<String, Object> attributes) {
        this.line = line;
        this.serialNumber = serialNumber;
        this.received = false;
        this.externalCode = externalCode;
        this.attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
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

    public String getExternalCode() {
        return externalCode;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }
}
