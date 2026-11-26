package com.medinasystems.pos.inventory.application;

import java.time.Instant;
import java.util.Map;

import com.medinasystems.pos.inventory.domain.ProductSerial;
import com.medinasystems.pos.inventory.domain.SerialStatus;

/** One unit of equipment, with its product, its characteristics and its external code. */
public record EquipmentView(long id, long productId, String productSku, String productName, Long categoryId,
                            String serialNumber, String externalCode, Map<String, Object> attributes,
                            SerialStatus status, long branchId, String branchName, Instant receivedAt,
                            Instant soldAt) {

    static EquipmentView of(ProductSerial unit, InventoryNames names) {
        return new EquipmentView(unit.getId(), unit.getProductId(), names.productSku(unit.getProductId()),
                names.productName(unit.getProductId()), unit.getCategoryId(), unit.getSerialNumber(),
                unit.getExternalCode(), unit.getAttributes(), unit.getStatus(), unit.getBranchId(),
                names.branchName(unit.getBranchId()), unit.getReceivedAt(), unit.getSoldAt());
    }
}
