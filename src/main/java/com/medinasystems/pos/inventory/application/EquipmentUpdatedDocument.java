package com.medinasystems.pos.inventory.application;

import java.time.Instant;
import java.util.Map;

import com.medinasystems.pos.inventory.domain.ProductSerial;

/**
 * An edit of the external code and the characteristics of one unit (event EQUIPMENT_UPDATED). The id is the store's own; the
 * central node finds the unit by product and serial number. Field names are part of the protocol.
 */
public record EquipmentUpdatedDocument(long id, long productId, String serialNumber, long branchId, String externalCode,
                                       Map<String, Object> attributes, String updatedAt, String updatedBy) {

    public static final String EVENT_TYPE = "EQUIPMENT_UPDATED";

    static EquipmentUpdatedDocument of(ProductSerial unit, long branchId, Instant updatedAt, String updatedBy) {
        return new EquipmentUpdatedDocument(unit.getId(), unit.getProductId(), unit.getSerialNumber(), branchId,
                unit.getExternalCode(), unit.getAttributes(), updatedAt.toString(), updatedBy);
    }
}
