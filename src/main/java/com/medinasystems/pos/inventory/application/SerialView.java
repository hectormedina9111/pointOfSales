package com.medinasystems.pos.inventory.application;

import java.time.Instant;
import java.util.Map;

import com.medinasystems.pos.inventory.domain.ProductSerial;
import com.medinasystems.pos.inventory.domain.SerialStatus;

/** One serial unit of a product, as the serial list shows it. */
public record SerialView(long id, long productId, String serialNumber, SerialStatus status, long branchId,
                         String branchName, Instant receivedAt, Instant soldAt, String externalCode,
                         Map<String, Object> attributes) {

    static SerialView of(ProductSerial unit, InventoryNames names) {
        return new SerialView(unit.getId(), unit.getProductId(), unit.getSerialNumber(), unit.getStatus(),
                unit.getBranchId(), names.branchName(unit.getBranchId()), unit.getReceivedAt(), unit.getSoldAt(),
                unit.getExternalCode(), unit.getAttributes());
    }
}
